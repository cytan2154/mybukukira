package com.example.export

import com.example.accounting.AccountingEngine
import com.example.localization.BukuKiraStrings
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
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
    lang: LanguageCode
  ): Result<Unit> = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { out ->
      writePdfStream(out, workspace, stats, lang)
    } ?: error("Unable to open output stream for PDF.")
  }

  fun writePdfStream(
    outputStream: OutputStream,
    workspace: BusinessWorkspace,
    stats: FinancialStats,
    lang: LanguageCode
  ) {
    val labels = BukuKiraStrings.statementLabels(lang)
    val doc = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val marginX = 40f
    val rightX = pageWidth - 40f

    val titlePaint = Paint().apply {
      color = Color.parseColor("#047857")
      textSize = 16f
      typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val subPaint = Paint().apply {
      color = Color.parseColor("#334155")
      textSize = 10f
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
      textSize = 10f
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

    var pageNumber = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas: Canvas = page.canvas
    var y = 44f

    fun startNewPage(headerTitle: String) {
      doc.finishPage(page)
      pageNumber++
      page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
      canvas = page.canvas
      y = 44f
      canvas.drawText(headerTitle, marginX, y, titlePaint)
      y += 16f
      canvas.drawText("${workspace.businessName} (${workspace.businessRegNo}) • FY ${stats.selectedYear}", marginX, y, subPaint)
      y += 14f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 18f
    }

    fun ensureSpace(needed: Float, continuationHeader: String) {
      if (y + needed > pageHeight - 52f) {
        startNewPage(continuationHeader)
      }
    }

    fun drawBusinessHeader(reportTitle: String) {
      canvas.drawText(workspace.businessName.ifBlank { "BukuKira" }, marginX, y, titlePaint)
      y += 16f
      val infoLine = buildString {
        if (workspace.businessRegNo.isNotBlank()) append(workspace.businessRegNo).append(" • ")
        if (workspace.ownerName.isNotBlank()) append(workspace.ownerName).append(" • ")
        append("FY ${stats.selectedYear}")
      }
      canvas.drawText(infoLine, marginX, y, subPaint)
      y += 14f
      if (workspace.businessAddress.isNotBlank()) {
        canvas.drawText(workspace.businessAddress.take(85), marginX, y, subPaint)
        y += 14f
      }
      canvas.drawText(reportTitle, marginX, y + 4f, sectionPaint)
      y += 14f
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 16f
    }

    fun drawStatementSection(type: StatementType, heading: String) {
      drawBusinessHeader(heading)
      var lastSection = ""
      stats.rows.filter { it.statementType == type || (type == StatementType.BALANCE_SHEET && it.statementType == StatementType.CHECK) }.forEach { r ->
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
        canvas.drawText(r.label.take(54), marginX + 6f, y, labelPaint)
        canvas.drawText(BukuKiraStrings.formatCurrency(workspace.currency, r.amount), rightX, y, amtPaint)
        y += 14f
        if (r.note.isNotBlank()) {
          ensureSpace(14f, heading)
          canvas.drawText(r.note.take(82), marginX + 10f, y, subPaint)
          y += 12f
        }
      }
      y += 10f
      ensureSpace(36f, heading)
      canvas.drawLine(marginX, y, rightX, y, linePaint)
      y += 14f
      drawWrappedText(canvas, labels.disclaimer, marginX, y, rightX - marginX, 12f, subPaint)
    }

    // Page 1+: Profit & Loss
    drawStatementSection(StatementType.PNL, "${labels.pnlTitle} — ${stats.selectedYear}")

    // Next Page: Balance Sheet
    startNewPage("${labels.bsTitle} — ${stats.selectedYear}")
    y -= 16f
    drawStatementSection(StatementType.BALANCE_SHEET, "${labels.bsTitle} — ${stats.selectedYear}")

    // Next Page: General Ledger (Lejar Am)
    if (stats.ledgers.isNotEmpty()) {
      startNewPage("${labels.ledgerTitle} — ${stats.selectedYear}")
      stats.ledgers.forEach { (accName, entries) ->
        ensureSpace(44f, "${labels.ledgerTitle} — ${stats.selectedYear}")
        canvas.drawText(accName, marginX, y, sectionPaint)
        y += 14f
        canvas.drawText("Date", marginX, y, subPaint)
        canvas.drawText("Description", marginX + 68f, y, subPaint)
        canvas.drawText("Debit", rightX - 90f, y, amountPaint)
        canvas.drawText("Credit", rightX, y, amountPaint)
        y += 6f
        canvas.drawLine(marginX, y, rightX, y, linePaint)
        y += 13f

        var drSum = 0.0
        var crSum = 0.0
        entries.forEach { e ->
          ensureSpace(16f, "${labels.ledgerTitle} — ${stats.selectedYear}")
          drSum += e.debit
          crSum += e.credit
          canvas.drawText(e.date, marginX, y, rowPaint)
          canvas.drawText(e.description.take(36), marginX + 68f, y, rowPaint)
          if (e.debit > 0) {
            canvas.drawText(String.format(Locale.US, "%.2f", e.debit), rightX - 90f, y, amountPaint)
          }
          if (e.credit > 0) {
            canvas.drawText(String.format(Locale.US, "%.2f", e.credit), rightX, y, amountPaint)
          }
          y += 14f
        }
        ensureSpace(22f, "${labels.ledgerTitle} — ${stats.selectedYear}")
        canvas.drawLine(marginX, y - 6f, rightX, y - 6f, linePaint)
        canvas.drawText("Total", marginX + 68f, y + 6f, totalPaint)
        canvas.drawText(String.format(Locale.US, "%.2f", drSum), rightX - 90f, y + 6f, totalAmountPaint)
        canvas.drawText(String.format(Locale.US, "%.2f", crSum), rightX, y + 6f, totalAmountPaint)
        y += 22f
      }
    }

    doc.finishPage(page)
    doc.writeTo(outputStream)
    doc.close()
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
      if (paint.measureText(candidate) > maxWidth) {
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
}
