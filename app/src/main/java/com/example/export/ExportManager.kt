package com.example.export

import com.example.accounting.AccountingEngine
import com.example.localization.BukuKiraStrings
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.OwnerPrivateInfo
import com.example.model.PaymentMethod
import com.example.model.StatementType
import com.example.model.TransactionType
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.min

data class BackupPayload(
  val businessName: String?,
  val businessRegNo: String?,
  val businessType: String?,
  val businessAddress: String?,
  val ownerName: String?,
  val currency: String?,
  val financialYear: String?,
  val closingStockByYear: Map<String, Double>,
  val beginningBalances: BeginningBalances?,
  val transactions: List<BookkeepingTransaction>
)

internal data class BalanceSheetPdfLayout(
  val headerHeight: Float,
  val baseBodyHeight: Float,
  val scale: Float,
  val fittedTotalHeight: Float,
  val maxAvailableHeight: Float,
  val rowCount: Int,
  val sectionCount: Int
)

object ExportManager {

  fun buildJsonBackup(
    workspace: BusinessWorkspace,
    transactions: List<BookkeepingTransaction>
  ): String {
    val root = JSONObject()
    root.put("backupVersion", 2)
    root.put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

    val settings = JSONObject().apply {
      put("businessName", workspace.businessName)
      put("businessRegNo", workspace.businessRegNo)
      put("businessType", workspace.businessType)
      put("businessAddress", workspace.businessAddress)
      put("ownerName", workspace.ownerName)
      put("currency", workspace.currency)
      put("financialYear", workspace.financialYear)
      val stockObj = JSONObject()
      workspace.closingStockByYear.forEach { (k, v) -> stockObj.put(k, v) }
      put("closingStockByYear", stockObj)
      val bbObj = JSONObject()
      workspace.beginningBalances.toFirestoreMap().forEach { (k, v) -> bbObj.put(k, v) }
      put("beginningBalances", bbObj)
    }
    root.put("settings", settings)

    val txArray = JSONArray()
    transactions.sortedBy { it.date }.forEach { t ->
      val obj = JSONObject().apply {
        put("id", t.id)
        put("date", t.date)
        put("type", t.type.wireValue)
        put("categoryId", t.categoryId)
        put("method", t.method.wireValue)
        put("amount", t.amount)
        put("note", t.note)
        t.partyName?.let { put("partyName", it) }
        t.invoiceNo?.let { put("invoiceNo", it) }
        t.disposalAssetType?.let { put("disposalAssetType", it.wireValue) }
        t.disposalAssetCost?.let { put("disposalAssetCost", it) }
      }
      txArray.put(obj)
    }
    root.put("transactions", txArray)
    return root.toString(2)
  }

  /**
   * Parses both v2 Android JSON backups and legacy BukuKira HTML JSON backups (`appState` or `{ settings, transactions }`).
   */
  fun parseJsonBackup(
    rawJson: String,
    businessId: String,
    uid: String,
    memberUids: List<String>
  ): BackupPayload {
    val root = JSONObject(rawJson)
    val settings = root.optJSONObject("settings") ?: root
    val stockObj = settings.optJSONObject("closingStockByYear")
    val stockMap = linkedMapOf<String, Double>()
    if (stockObj != null) {
      stockObj.keys().forEach { k ->
        val v = stockObj.optDouble(k, Double.NaN)
        if (!v.isNaN() && v >= 0) stockMap[k] = v
      }
    }

    val bbObj = settings.optJSONObject("beginningBalances")
    val bb = if (bbObj != null) {
      val map = linkedMapOf<String, Any?>()
      bbObj.keys().forEach { k -> map[k] = bbObj.optDouble(k, 0.0) }
      BeginningBalances.fromMap(map)
    } else null

    val txArr = root.optJSONArray("transactions") ?: JSONArray()
    val list = mutableListOf<BookkeepingTransaction>()
    for (i in 0 until txArr.length()) {
      val item = txArr.optJSONObject(i) ?: continue
      val date = item.optString("date", "").take(10)
      if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(date)) continue
      val rawId = item.optString("id", "").replace(Regex("[^a-zA-Z0-9_\\-]"), "")
      val txId = if (rawId.isNotEmpty()) rawId.take(128) else "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
      val type = TransactionType.fromWire(item.optString("type", "IN"))
      val method = PaymentMethod.fromWire(item.optString("method", "CASH"))
      val amount = item.optDouble("amount", 0.0).let { if (it.isFinite() && it >= 0) it else 0.0 }
      val note = item.optString("note", "").take(500)
      val categoryId = item.optString("categoryId", "exp_others").take(60).ifBlank { "exp_others" }
      val partyName = item.optString("partyName", "").trim().takeIf { it.isNotEmpty() }?.take(120)
      val invoiceNo = item.optString("invoiceNo", "").trim().takeIf { it.isNotEmpty() }?.take(60)
      val disposalType = AssetDisposalType.fromWire(item.optString("disposalAssetType", ""))
      val disposalCost = item.optDouble("disposalAssetCost", Double.NaN).takeIf { !it.isNaN() && it >= 0 }

      list.add(
        BookkeepingTransaction(
          id = txId,
          businessId = businessId,
          createdByUid = uid,
          memberUids = memberUids,
          date = date,
          type = type,
          categoryId = categoryId,
          method = method,
          amount = amount,
          note = note,
          partyName = partyName,
          invoiceNo = invoiceNo,
          disposalAssetType = disposalType,
          disposalAssetCost = disposalCost
        )
      )
    }

    return BackupPayload(
      businessName = settings.optString("businessName", "").trim().takeIf { it.isNotEmpty() },
      businessRegNo = settings.optString("businessRegNo", "").trim().takeIf { it.isNotEmpty() },
      businessType = settings.optString("businessType", "").trim().takeIf { it.isNotEmpty() },
      businessAddress = settings.optString("businessAddress", "").trim().takeIf { it.isNotEmpty() },
      ownerName = settings.optString("ownerName", "").trim().takeIf { it.isNotEmpty() },
      currency = settings.optString("currency", "").trim().takeIf { it in listOf("RM", "SGD", "USD", "Rp") },
      financialYear = settings.optString("financialYear", "").trim().takeIf { Regex("^[0-9]{4}$").matches(it) },
      closingStockByYear = stockMap,
      beginningBalances = bb,
      transactions = list
    )
  }

  fun buildCsvReport(
    workspace: BusinessWorkspace,
    transactions: List<BookkeepingTransaction>,
    stats: FinancialStats,
    lang: LanguageCode
  ): String {
    val labels = BukuKiraStrings.statementLabels(lang)
    fun csvCell(value: String): String {
      val safe = value.replace("\"", "\"\"")
      return "\"$safe\""
    }

    return buildString {
      // UTF-8 BOM for Microsoft Excel compatibility
      append('\uFEFF')
      appendLine("${csvCell("BukuKira Financial Export")},${csvCell(workspace.businessName)}")
      appendLine("${csvCell("Registration No")},${csvCell(workspace.businessRegNo)}")
      appendLine("${csvCell("Financial Year")},${csvCell(stats.selectedYear)}")
      appendLine("${csvCell("Currency")},${csvCell(workspace.currency)}")
      appendLine()

      appendLine(listOf("Date", "Type", "Category", "Method", "Party", "Invoice", "Amount", "Note").joinToString(",") { csvCell(it) })
      transactions.filter { it.date.startsWith(stats.selectedYear) }.sortedBy { it.date }.forEach { t ->
        appendLine(
          listOf(
            t.date,
            t.type.wireValue,
            t.categoryId,
            t.method.wireValue,
            t.partyName.orEmpty(),
            t.invoiceNo.orEmpty(),
            String.format(Locale.US, "%.2f", t.amount),
            t.note
          ).joinToString(",") { csvCell(it) }
        )
      }
      appendLine()
      appendLine("${csvCell(labels.pnlTitle)} / ${csvCell(labels.bsTitle)}")
      appendLine(listOf("Statement", "Section", "Account", "Amount (${workspace.currency})", "Note").joinToString(",") { csvCell(it) })
      stats.rows.forEach { r ->
        appendLine(
          listOf(
            r.statementType.name,
            r.section,
            r.label,
            String.format(Locale.US, "%.2f", r.amount),
            r.note
          ).joinToString(",") { csvCell(it) }
        )
      }
      appendLine()
      appendLine(csvCell(labels.disclaimer))
    }
  }

  /**
   * Generates a multi-page A4 PDF (595 x 842 pt) containing Income Statement, Balance Sheet,
   * and General Ledger (Lejar Am) using native android.graphics.pdf.PdfDocument.
   */
  fun writePdfToUri(
    context: Context,
    uri: Uri,
    workspace: BusinessWorkspace,
    stats: FinancialStats,
    lang: LanguageCode,
    ownerPrivateInfo: OwnerPrivateInfo? = null
  ): Result<Unit> = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { out ->
      writePdfStream(out, workspace, stats, lang, ownerPrivateInfo)
    } ?: error("Unable to open output stream for PDF.")
  }

  fun writePdfStream(
    outputStream: OutputStream,
    workspace: BusinessWorkspace,
    stats: FinancialStats,
    lang: LanguageCode,
    ownerPrivateInfo: OwnerPrivateInfo? = null
  ) {
    val labels = BukuKiraStrings.statementLabels(lang)
    val doc = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val marginX = 36f
    val rightX = pageWidth - 36f
    val contentWidth = rightX - marginX

    val titlePaint = Paint().apply {
      color = Color.parseColor("#047857")
      textSize = 16f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val subPaint = Paint().apply {
      color = Color.parseColor("#334155")
      textSize = 9.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val sectionPaint = Paint().apply {
      color = Color.parseColor("#0F172A")
      textSize = 10.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val rowPaint = Paint().apply {
      color = Color.parseColor("#1E293B")
      textSize = 9.5f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    val totalPaint = Paint().apply {
      color = Color.parseColor("#047857")
      textSize = 9.8f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val amountPaint = Paint(rowPaint).apply {
      textAlign = Paint.Align.RIGHT
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    val totalAmountPaint = Paint(totalPaint).apply {
      textAlign = Paint.Align.RIGHT
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val linePaint = Paint().apply {
      color = Color.parseColor("#CBD5E1")
      strokeWidth = 0.8f
    }
    val darkLinePaint = Paint().apply {
      color = Color.parseColor("#0F172A")
      strokeWidth = 1.4f
    }

    var pageNumber = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas: Canvas = page.canvas
    var y = 40f

    fun startBlankPage() {
      doc.finishPage(page)
      pageNumber++
      page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
      canvas = page.canvas
      y = 32f
    }

    fun startNewPage(headerTitle: String) {
      doc.finishPage(page)
      pageNumber++
      page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
      canvas = page.canvas
      y = 40f
      canvas.drawText(headerTitle, marginX, y, titlePaint)
      y += 15f
      canvas.drawText("${workspace.businessName.ifBlank { "BukuKira" }} (${workspace.businessRegNo}) • FY ${stats.selectedYear}", marginX, y, subPaint)
      y += 12f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 16f
    }

    fun ensureSpace(needed: Float, continuationHeader: String) {
      if (y + needed > pageHeight - 48f) {
        startNewPage(continuationHeader)
      }
    }

    fun drawBusinessHeader(reportTitle: String) {
      canvas.drawText(workspace.businessName.ifBlank { "BukuKira" }, marginX, y, titlePaint)
      y += 15f
      val infoLine = buildString {
        if (workspace.businessRegNo.isNotBlank()) append(workspace.businessRegNo).append(" • ")
        if (workspace.ownerName.isNotBlank()) append(workspace.ownerName).append(" • ")
        append("FY ${stats.selectedYear}")
      }
      canvas.drawText(infoLine, marginX, y, subPaint)
      y += 13f
      if (workspace.businessAddress.isNotBlank()) {
        canvas.drawText(fitTextToWidth(workspace.businessAddress, contentWidth, subPaint), marginX, y, subPaint)
        y += 13f
      }
      canvas.drawText(reportTitle, marginX, y + 3f, sectionPaint)
      y += 12f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 14f
    }

    val ownerDisplayName = workspace.ownerName.trim().ifEmpty { "-" }
    val ownerIdText = ownerPrivateInfo?.ownerIdNumber?.trim().orEmpty()

    fun drawOwnerSignatureBlock(scaleFactor: Float) {
      val s = scaleFactor
      val sigLabelPaint = Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = (8.8f * s).coerceAtLeast(6.6f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      val sigNamePaint = Paint().apply {
        color = Color.parseColor("#0F172A")
        textSize = (9.5f * s).coerceAtLeast(7.0f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
      }
      val sigMutedPaint = Paint().apply {
        color = Color.parseColor("#475569")
        textSize = (8.5f * s).coerceAtLeast(6.4f)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
      }
      val sigMutedRightPaint = Paint(sigMutedPaint).apply {
        textAlign = Paint.Align.RIGHT
      }
      val sigRulePaint = Paint().apply {
        color = Color.parseColor("#94A3B8")
        strokeWidth = 0.9f
      }

      y += 10f * s
      val sigLineWidth = 145f * s
      val maxLeftWidth = (contentWidth - sigLineWidth - 20f * s).coerceAtLeast(160f)

      canvas.drawText(fitTextToWidth(labels.preparedCertifiedBy, maxLeftWidth, sigLabelPaint), marginX, y + 9f * s, sigLabelPaint)
      canvas.drawText(fitTextToWidth(ownerDisplayName, maxLeftWidth, sigNamePaint), marginX, y + 20.5f * s, sigNamePaint)
      if (ownerIdText.isNotEmpty()) {
        canvas.drawText(
          fitTextToWidth("${labels.ownerIdNoLabel} $ownerIdText", maxLeftWidth, sigMutedPaint),
          marginX,
          y + 31.5f * s,
          sigMutedPaint
        )
      }

      val ruleY = y + 19f * s
      canvas.drawLine(rightX - sigLineWidth, ruleY, rightX, ruleY, sigRulePaint)
      canvas.drawText(labels.ownerSignatureLabel, rightX, ruleY + 11.5f * s, sigMutedRightPaint)

      y += (if (ownerIdText.isNotEmpty()) 36f else 33f) * s
    }

    fun drawStatementSection(type: StatementType, heading: String) {
      drawBusinessHeader(heading)
      var lastSection = ""
      stats.rows.filter { it.statementType == type }.forEach { r ->
        if (r.section != lastSection) {
          ensureSpace(34f, heading)
          y += 6f
          canvas.drawText(r.section, marginX, y, sectionPaint)
          y += 14f
          lastSection = r.section
        }
        ensureSpace(20f, heading)
        val labelPaint = if (r.isTotal) totalPaint else rowPaint
        val amtPaint = if (r.isTotal) totalAmountPaint else amountPaint
        canvas.drawText(fitTextToWidth(r.label, contentWidth - 115f, labelPaint), marginX + 6f, y, labelPaint)
        canvas.drawText(BukuKiraStrings.formatCurrency(workspace.currency, r.amount), rightX, y, amtPaint)
        y += 14f
        if (r.note.isNotBlank()) {
          ensureSpace(14f, heading)
          canvas.drawText(fitTextToWidth(r.note, contentWidth - 16f, subPaint), marginX + 10f, y, subPaint)
          y += 12f
        }
      }
      ensureSpace(88f, heading)
      drawOwnerSignatureBlock(1f)
      y += 8f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 14f
      drawWrappedText(canvas, labels.disclaimer, marginX, y, contentWidth, 12f, subPaint)
    }

    fun drawSinglePageBalanceSheet(heading: String) {
      startBlankPage()
      val bsRows = stats.rows.filter { it.statementType == StatementType.BALANCE_SHEET }
      val bsSubPaint = Paint(subPaint).apply { textSize = 8.5f }
      val bsSectionPaint = Paint(sectionPaint).apply { textSize = 9.5f }
      val bsRowPaint = Paint(rowPaint).apply { textSize = 8.8f }
      val bsTotalPaint = Paint(totalPaint).apply { textSize = 9f }
      val bsAmtPaint = Paint(amountPaint).apply { textSize = 8.8f }
      val bsTotalAmtPaint = Paint(totalAmountPaint).apply { textSize = 9f }
      val bsDisclaimerPaint = Paint(subPaint).apply { textSize = 7.8f }

      val hasAddress = workspace.businessAddress.isNotBlank()
      val disclaimerLines = countWrappedLines(labels.disclaimer, contentWidth, bsDisclaimerPaint)
      val layout = computeBalanceSheetLayout(
        rows = bsRows,
        hasAddress = hasAddress,
        disclaimerLineCount = disclaimerLines,
        hasOwnerId = ownerIdText.isNotEmpty(),
        pageHeight = pageHeight.toFloat()
      )
      val scale = layout.scale

      if (scale < 1f) {
        bsRowPaint.textSize = (8.8f * scale).coerceAtLeast(6.8f)
        bsTotalPaint.textSize = (9f * scale).coerceAtLeast(7f)
        bsSectionPaint.textSize = (9.5f * scale).coerceAtLeast(7.2f)
        bsAmtPaint.textSize = bsRowPaint.textSize
        bsTotalAmtPaint.textSize = bsTotalPaint.textSize
        bsSubPaint.textSize = (8.5f * scale).coerceAtLeast(6.5f)
        bsDisclaimerPaint.textSize = (7.8f * scale).coerceAtLeast(6.3f)
      }

      val bsTitlePaint = Paint(titlePaint).apply { textSize = 14.5f }
      canvas.drawText(workspace.businessName.ifBlank { "BukuKira" }, marginX, y, bsTitlePaint)
      y += 14f
      val infoLine = buildString {
        if (workspace.businessRegNo.isNotBlank()) append(workspace.businessRegNo).append(" • ")
        if (workspace.ownerName.isNotBlank()) append(workspace.ownerName).append(" • ")
        append("FY ${stats.selectedYear}")
      }
      canvas.drawText(infoLine, marginX, y, bsSubPaint)
      y += 11f
      if (hasAddress) {
        canvas.drawText(fitTextToWidth(workspace.businessAddress, contentWidth, bsSubPaint), marginX, y, bsSubPaint)
        y += 11f
      }
      canvas.drawText(heading, marginX, y + 2f, bsSectionPaint)
      y += 10f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 11f * scale

      val secGapBefore = 4f * scale
      val secStepAfter = 12f * scale
      val rowStep = 13f * scale
      val noteStep = 10.5f * scale
      val discLineH = 10f * scale

      var drawnSection = ""
      bsRows.forEach { r ->
        if (r.section != drawnSection) {
          y += secGapBefore
          canvas.drawText(r.section, marginX, y, bsSectionPaint)
          y += secStepAfter
          drawnSection = r.section
        }
        val lp = if (r.isTotal) bsTotalPaint else bsRowPaint
        val ap = if (r.isTotal) bsTotalAmtPaint else bsAmtPaint
        canvas.drawText(fitTextToWidth(r.label, contentWidth - 115f, lp), marginX + 6f, y, lp)
        canvas.drawText(BukuKiraStrings.formatCurrency(workspace.currency, r.amount), rightX, y, ap)
        y += rowStep
        if (r.note.isNotBlank()) {
          canvas.drawText(fitTextToWidth(r.note, contentWidth - 16f, bsSubPaint), marginX + 10f, y, bsSubPaint)
          y += noteStep
        }
      }

      drawOwnerSignatureBlock(scale)

      y += 6f * scale
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 10f * scale
      drawWrappedText(canvas, labels.disclaimer, marginX, y, contentWidth, discLineH, bsDisclaimerPaint)
    }

    // Page 1+: Profit & Loss
    drawStatementSection(StatementType.PNL, "${labels.pnlTitle} — ${stats.selectedYear}")

    // Forced Single Page: Balance Sheet
    drawSinglePageBalanceSheet("${labels.bsTitle} — ${stats.selectedYear}")

    // Next Page(s): 3-Column General Ledger (Date | Details | Debit | Credit | Balance)
    if (stats.ledgers.isNotEmpty()) {
      val ledgerHeading = "${labels.ledgerTitle} — ${stats.selectedYear}"
      startNewPage(ledgerHeading)

      val colDateX = marginX
      val colDetailsX = marginX + 64f
      val colDebitRightX = rightX - 156f
      val colCreditRightX = rightX - 78f
      val colBalanceRightX = rightX
      val detailsMaxWidth = (colDebitRightX - 74f) - colDetailsX

      val colHeaderPaint = Paint(sectionPaint).apply { textSize = 9.2f }
      val colHeaderRightPaint = Paint(colHeaderPaint).apply { textAlign = Paint.Align.RIGHT }
      val ledgerRowPaint = Paint(rowPaint).apply { textSize = 9f }
      val ledgerAmtPaint = Paint(amountPaint).apply { textSize = 9f }
      val ledgerTotalPaint = Paint(totalPaint).apply { textSize = 9.2f }
      val ledgerTotalAmtPaint = Paint(totalAmountPaint).apply { textSize = 9.2f }

      stats.ledgers.forEach { (accName, entries) ->
        ensureSpace(56f, ledgerHeading)
        canvas.drawText("${labels.ledgerAccountPrefix}: $accName", marginX, y, sectionPaint)
        y += 5f
        canvas.drawLine(marginX, y, rightX, y, darkLinePaint)
        y += 14f

        canvas.drawText(labels.ledgerColDate, colDateX, y, colHeaderPaint)
        canvas.drawText(labels.ledgerColDetails, colDetailsX, y, colHeaderPaint)
        canvas.drawText(labels.ledgerColDebit, colDebitRightX, y, colHeaderRightPaint)
        canvas.drawText(labels.ledgerColCredit, colCreditRightX, y, colHeaderRightPaint)
        canvas.drawText(labels.ledgerColBalance, colBalanceRightX, y, colHeaderRightPaint)
        y += 5f
        canvas.drawLine(marginX, y, rightX, y, linePaint)
        y += 13f

        var drSum = 0.0
        var crSum = 0.0
        entries.forEach { e ->
          ensureSpace(16f, ledgerHeading)
          drSum += e.debit
          crSum += e.credit
          canvas.drawText(e.date, colDateX, y, ledgerRowPaint)
          canvas.drawText(fitTextToWidth(e.description, detailsMaxWidth, ledgerRowPaint), colDetailsX, y, ledgerRowPaint)
          canvas.drawText(
            if (e.debit > 0) BukuKiraStrings.formatAmount(e.debit) else "-",
            colDebitRightX,
            y,
            ledgerAmtPaint
          )
          canvas.drawText(
            if (e.credit > 0) BukuKiraStrings.formatAmount(e.credit) else "-",
            colCreditRightX,
            y,
            ledgerAmtPaint
          )
          canvas.drawText(
            BukuKiraStrings.formatAmount(e.balance),
            colBalanceRightX,
            y,
            ledgerAmtPaint
          )
          y += 14f
        }
        ensureSpace(24f, ledgerHeading)
        val closingBalance = entries.lastOrNull()?.balance ?: 0.0
        canvas.drawLine(marginX, y - 5f, rightX, y - 5f, linePaint)
        canvas.drawText(labels.ledgerTotal, colDetailsX, y + 6f, ledgerTotalPaint)
        canvas.drawText(BukuKiraStrings.formatAmount(drSum), colDebitRightX, y + 6f, ledgerTotalAmtPaint)
        canvas.drawText(BukuKiraStrings.formatAmount(crSum), colCreditRightX, y + 6f, ledgerTotalAmtPaint)
        canvas.drawText(BukuKiraStrings.formatAmount(closingBalance), colBalanceRightX, y + 6f, ledgerTotalAmtPaint)
        y += 22f
      }
      ensureSpace(34f, ledgerHeading)
      y += 4f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 12f
      drawWrappedText(canvas, labels.disclaimer, marginX, y, contentWidth, 11.5f, subPaint)
    }

    doc.finishPage(page)
    doc.writeTo(outputStream)
    doc.close()
  }

  private fun fitTextToWidth(text: String, maxWidth: Float, paint: Paint): String {
    if (text.isEmpty() || maxWidth <= 0f) return ""
    if (paint.measureText(text) <= maxWidth) return text
    val ellipsis = "…"
    val avail = (maxWidth - paint.measureText(ellipsis)).coerceAtLeast(0f)
    val count = paint.breakText(text, true, avail, null)
    return if (count <= 0) ellipsis else text.substring(0, count).trimEnd() + ellipsis
  }

  private fun countWrappedLines(text: String, maxWidth: Float, paint: Paint): Int {
    if (text.isBlank()) return 0
    val words = text.split(" ")
    var line = StringBuilder()
    var count = 0
    for (w in words) {
      val candidate = if (line.isEmpty()) w else "$line $w"
      if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
        count++
        line = StringBuilder(w)
      } else {
        line = StringBuilder(candidate)
      }
    }
    if (line.isNotEmpty()) count++
    return count.coerceAtLeast(1)
  }

  private fun drawWrappedText(
    canvas: Canvas,
    text: String,
    x: Float,
    startY: Float,
    maxWidth: Float,
    lineHeight: Float,
    paint: Paint
  ) {
    val words = text.split(" ")
    var line = StringBuilder()
    var y = startY
    for (w in words) {
      val candidate = if (line.isEmpty()) w else "$line $w"
      if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
        canvas.drawText(line.toString(), x, y, paint)
        line = StringBuilder(w)
        y += lineHeight
      } else {
        line = StringBuilder(candidate)
      }
    }
    if (line.isNotEmpty()) {
      canvas.drawText(line.toString(), x, y, paint)
    }
  }

  internal fun computeBalanceSheetLayout(
    rows: List<com.example.model.StatementRow>,
    hasAddress: Boolean,
    disclaimerLineCount: Int,
    hasOwnerId: Boolean = false,
    pageHeight: Float = 842f
  ): BalanceSheetPdfLayout {
    var sectionCount = 0
    var noteCount = 0
    var lastSec = ""
    rows.forEach { r ->
      if (r.section != lastSec) {
        sectionCount++
        lastSec = r.section
      }
      if (r.note.isNotBlank()) noteCount++
    }
    val headerHeight = 14f + 11f + (if (hasAddress) 11f else 0f) + 12f + 11f
    val signatureHeight = 10f + if (hasOwnerId) 36f else 33f
    val baseBodyHeight =
      sectionCount * 16f +
        rows.size * 13f +
        noteCount * 10.5f +
        signatureHeight +
        16f +
        disclaimerLineCount * 10f
    val maxAvailableHeight = pageHeight - 32f - 24f
    val availableBodyHeight = (maxAvailableHeight - headerHeight).coerceAtLeast(180f)
    val scale = min(1f, availableBodyHeight / baseBodyHeight)
    val fittedTotalHeight = headerHeight + baseBodyHeight * scale
    return BalanceSheetPdfLayout(
      headerHeight = headerHeight,
      baseBodyHeight = baseBodyHeight,
      scale = scale,
      fittedTotalHeight = fittedTotalHeight,
      maxAvailableHeight = maxAvailableHeight,
      rowCount = rows.size,
      sectionCount = sectionCount
    )
  }
}
