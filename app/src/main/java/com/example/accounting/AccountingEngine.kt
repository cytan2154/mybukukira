package com.example.accounting

import com.example.localization.BukuKiraStrings
import com.example.model.AccountingCatalog
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.LedgerEntry
import com.example.model.PaymentMethod
import com.example.model.StatementClassification
import com.example.model.StatementRow
import com.example.model.StatementType
import com.example.model.TransactionType
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure Kotlin on-device accounting engine matching BukuKira Apps Script (`bkMasterGetStatements_`) 1:1.
 * Computes Profit & Loss, Balance Sheet, Balance Check, and General Ledger (Lejar Am) over local transactions.
 */
object AccountingEngine {

  private data class ParsedDisposal(
    val assetType: AssetDisposalType,
    val cost: Double,
    val userNote: String
  )

  private val legacyDisposalRegex =
    Regex("^BK_ASSET_DISPOSAL\\|([^|]+)\\|([0-9]+(?:\\.[0-9]+)?)\\|(.*)$")

  private fun parseDisposal(t: BookkeepingTransaction): ParsedDisposal? {
    val typedType = t.disposalAssetType
    val typedCost = t.disposalAssetCost
    if (typedType != null && typedCost != null && typedCost.isFinite() && typedCost > 0.0) {
      return ParsedDisposal(typedType, typedCost, t.note)
    }
    val m = legacyDisposalRegex.matchEntire(t.note) ?: return null
    val parsedType = AssetDisposalType.fromWire(m.groupValues[1]) ?: AssetDisposalType.EQUIPMENT
    val cost = m.groupValues[2].toDoubleOrNull() ?: return null
    if (!cost.isFinite() || cost <= 0.0) return null
    return ParsedDisposal(parsedType, cost, m.groupValues[3])
  }

  private fun formatDisposalNote(t: BookkeepingTransaction, d: ParsedDisposal?, lang: LanguageCode): String {
    if (d != null) {
      val typeLabel = when (d.assetType) {
        AssetDisposalType.EQUIPMENT -> BukuKiraStrings.translateAccount("Loji dan jentera", lang)
        AssetDisposalType.LAND_BUILDING -> BukuKiraStrings.translateAccount("Tanah dan bangunan", lang)
        AssetDisposalType.VEHICLES -> BukuKiraStrings.translateAccount("Kenderaan", lang)
        AssetDisposalType.OTHER_NON_CURRENT -> BukuKiraStrings.translateAccount("Aset bukan semasa lain", lang)
        AssetDisposalType.INVESTMENTS -> BukuKiraStrings.translateAccount("Pelaburan", lang)
      }
      val costText = String.format(java.util.Locale.US, "%.2f", d.cost)
      val base = "Kos RM $costText ($typeLabel)"
      return if (d.userNote.isNotBlank()) "$base - ${d.userNote}" else base
    }
    return t.note
  }

  fun getOpeningStock(
    year: Int,
    closingStockByYear: Map<String, Double>,
    beginningBalances: BeginningBalances
  ): Double {
    var latestYear: Int? = null
    closingStockByYear.keys.forEach { k ->
      val ky = k.toIntOrNull()
      val currentLatest = latestYear
      if (ky != null && ky < year && (currentLatest == null || ky > currentLatest)) {
        latestYear = ky
      }
    }
    return if (latestYear != null) {
      max(0.0, closingStockByYear[latestYear.toString()] ?: 0.0)
    } else {
      max(0.0, beginningBalances.openingStock)
    }
  }

  fun getClosingStock(
    year: Int,
    closingStockByYear: Map<String, Double>,
    beginningBalances: BeginningBalances
  ): Double {
    val yearKey = year.toString()
    return if (closingStockByYear.containsKey(yearKey)) {
      max(0.0, closingStockByYear[yearKey] ?: 0.0)
    } else {
      getOpeningStock(year, closingStockByYear, beginningBalances)
    }
  }

  fun computeStatements(
    transactions: List<BookkeepingTransaction>,
    beginningBalances: BeginningBalances,
    closingStockByYear: Map<String, Double>,
    financialYear: String,
    lang: LanguageCode = LanguageCode.MS
  ): FinancialStats {
    val year = financialYear.toIntOrNull()?.takeIf { it in 1900..2200 } ?: 2026
    val tL = { key: String -> BukuKiraStrings.translateAccount(key, lang) }
    val labels = BukuKiraStrings.statementLabels(lang)

    var revenue = 0.0
    var otherIncome = 0.0
    var purchases = 0.0
    var expenses = 0.0
    var drawings = 0.0
    var priorProfit = 0.0

    val revenueByCategory = linkedMapOf<String, Double>()
    val otherIncomeByCategory = linkedMapOf<String, Double>()
    val expensesByCategory = linkedMapOf<String, Double>()

    var disposalGainLoss = 0.0
    var hasDisposalRecord = false

    var cash = beginningBalances.cash
    var bank = beginningBalances.bank
    var land = beginningBalances.landBuilding
    var equipment = beginningBalances.equipment
    var vehicles = beginningBalances.vehicles
    var otherNC = beginningBalances.otherNonCurrent
    var investments = beginningBalances.investments
    var debtors = beginningBalances.tradeDebtors
    var creditors = beginningBalances.tradeCreditors
    var otherCreditors = beginningBalances.otherCreditors
    var otherDebtors = beginningBalances.otherDebtors
    var otherCurrent = beginningBalances.otherCurrent
    var loans = beginningBalances.loans
    var capital = beginningBalances.capital

    val bf = linkedMapOf(
      "Tunai" to cash,
      "Bank" to bank,
      "Penghutang perdagangan" to debtors,
      "Pemiutang dagangan" to creditors,
      "Pemiutang lain" to otherCreditors,
      "Tanah dan bangunan" to land,
      "Loji dan jentera" to equipment,
      "Kenderaan" to vehicles,
      "Aset bukan semasa lain" to otherNC,
      "Pelaburan" to investments,
      "Pinjaman dan overdraf" to loans,
      "Akaun modal" to capital
    )

    val sortedTxs = transactions.sortedWith(compareBy<BookkeepingTransaction> { it.date }.thenBy { it.id })

    for (t in sortedTxs) {
      val ty = t.date.take(4).toIntOrNull() ?: continue
      val amt = if (t.amount.isFinite()) t.amount else 0.0
      val (accLabelMs, st) = AccountingCatalog.accountMetaFor(t.categoryId)
      val isIn = t.type == TransactionType.IN
      val isOut = t.type == TransactionType.OUT
      val isCreditSale = t.type == TransactionType.CREDIT_SALE
      val isCreditPurchase = t.type == TransactionType.CREDIT_PURCHASE
      val isCreditNote = t.type == TransactionType.CREDIT_NOTE
      val isDebitNote = t.type == TransactionType.DEBIT_NOTE
      val isStockReturn = st == StatementClassification.PNL_COGS || st == StatementClassification.PNL_PURCHASE_RETURN
      val isCash = t.method == PaymentMethod.CASH

      if (ty == year) {
        when (st) {
          StatementClassification.PNL_REVENUE -> {
            revenue += amt
            revenueByCategory[accLabelMs] = (revenueByCategory[accLabelMs] ?: 0.0) + amt
          }
          StatementClassification.PNL_OTHER_INCOME -> {
            otherIncome += amt
            otherIncomeByCategory[accLabelMs] = (otherIncomeByCategory[accLabelMs] ?: 0.0) + amt
          }
          StatementClassification.BS_ASSET_DISPOSAL -> {
            val d = parseDisposal(t)
            if (d != null) {
              val diff = amt - d.cost
              disposalGainLoss += diff
              hasDisposalRecord = true
              otherIncome += diff
            }
          }
          StatementClassification.PNL_SALES_RETURN -> {
            revenue -= amt
            revenueByCategory[accLabelMs] = (revenueByCategory[accLabelMs] ?: 0.0) - amt
          }
          StatementClassification.PNL_COGS -> purchases += amt
          StatementClassification.PNL_PURCHASE_RETURN -> purchases -= amt
          StatementClassification.PNL_EXPENSE -> {
            expenses += amt
            expensesByCategory[accLabelMs] = (expensesByCategory[accLabelMs] ?: 0.0) + amt
          }
          StatementClassification.BS_EQUITY_DRAW -> drawings += amt
          StatementClassification.BS_EQUITY_DRAW_RETURN -> drawings -= amt
          else -> Unit
        }
      } else if (ty < year) {
        when (st) {
          StatementClassification.PNL_REVENUE, StatementClassification.PNL_OTHER_INCOME -> priorProfit += amt
          StatementClassification.BS_ASSET_DISPOSAL -> {
            val d = parseDisposal(t)
            priorProfit += if (d != null) (amt - d.cost) else amt
          }
          StatementClassification.PNL_SALES_RETURN,
          StatementClassification.PNL_COGS,
          StatementClassification.PNL_EXPENSE,
          StatementClassification.BS_EQUITY_DRAW -> priorProfit -= amt
          StatementClassification.PNL_PURCHASE_RETURN,
          StatementClassification.BS_EQUITY_DRAW_RETURN -> priorProfit += amt
          else -> Unit
        }

        if (st == StatementClassification.BS_TRANSFER) {
          if (t.categoryId == "transfer_bank_to_cash") {
            bf["Bank"] = (bf["Bank"] ?: 0.0) - amt
            bf["Tunai"] = (bf["Tunai"] ?: 0.0) + amt
          } else if (t.categoryId == "transfer_cash_to_bank") {
            bf["Tunai"] = (bf["Tunai"] ?: 0.0) - amt
            bf["Bank"] = (bf["Bank"] ?: 0.0) + amt
          }
        } else if (isIn) {
          if (isCash) bf["Tunai"] = (bf["Tunai"] ?: 0.0) + amt else bf["Bank"] = (bf["Bank"] ?: 0.0) + amt
        } else if (isOut) {
          if (isCash) bf["Tunai"] = (bf["Tunai"] ?: 0.0) - amt else bf["Bank"] = (bf["Bank"] ?: 0.0) - amt
        }

        when (st) {
          StatementClassification.BS_LAND_BUILDING_ADD -> bf["Tanah dan bangunan"] = (bf["Tanah dan bangunan"] ?: 0.0) + amt
          StatementClassification.BS_EQUIPMENT_ADD -> bf["Loji dan jentera"] = (bf["Loji dan jentera"] ?: 0.0) + amt
          StatementClassification.BS_VEHICLES_ADD -> bf["Kenderaan"] = (bf["Kenderaan"] ?: 0.0) + amt
          StatementClassification.BS_OTHER_NONCURRENT_ADD -> bf["Aset bukan semasa lain"] = (bf["Aset bukan semasa lain"] ?: 0.0) + amt
          StatementClassification.BS_INVESTMENT_ADD -> bf["Pelaburan"] = (bf["Pelaburan"] ?: 0.0) + amt
          StatementClassification.BS_TRADE_DEBTORS_COLLECT -> bf["Penghutang perdagangan"] = (bf["Penghutang perdagangan"] ?: 0.0) - amt
          StatementClassification.BS_TRADE_CREDITORS_PAY -> bf["Pemiutang dagangan"] = (bf["Pemiutang dagangan"] ?: 0.0) - amt
          StatementClassification.BS_OTHER_CREDITORS_PAY -> bf["Pemiutang lain"] = (bf["Pemiutang lain"] ?: 0.0) - amt
          StatementClassification.BS_LIABILITY_ADD -> bf["Pinjaman dan overdraf"] = (bf["Pinjaman dan overdraf"] ?: 0.0) + amt
          StatementClassification.BS_LIABILITY_PAY -> bf["Pinjaman dan overdraf"] = (bf["Pinjaman dan overdraf"] ?: 0.0) - amt
          StatementClassification.BS_EQUITY_ADD -> bf["Akaun modal"] = (bf["Akaun modal"] ?: 0.0) + amt
          StatementClassification.BS_ASSET_DISPOSAL -> {
            val d = parseDisposal(t)
            if (d != null) {
              when (d.assetType) {
                AssetDisposalType.LAND_BUILDING -> bf["Tanah dan bangunan"] = (bf["Tanah dan bangunan"] ?: 0.0) - d.cost
                AssetDisposalType.VEHICLES -> bf["Kenderaan"] = (bf["Kenderaan"] ?: 0.0) - d.cost
                AssetDisposalType.OTHER_NON_CURRENT -> bf["Aset bukan semasa lain"] = (bf["Aset bukan semasa lain"] ?: 0.0) - d.cost
                AssetDisposalType.INVESTMENTS -> bf["Pelaburan"] = (bf["Pelaburan"] ?: 0.0) - d.cost
                AssetDisposalType.EQUIPMENT -> bf["Loji dan jentera"] = (bf["Loji dan jentera"] ?: 0.0) - d.cost
              }
            }
          }
          else -> Unit
        }
        if (isCreditSale) bf["Penghutang perdagangan"] = (bf["Penghutang perdagangan"] ?: 0.0) + amt
        if (isCreditPurchase) {
          if (st == StatementClassification.PNL_COGS) {
            bf["Pemiutang dagangan"] = (bf["Pemiutang dagangan"] ?: 0.0) + amt
          } else {
            bf["Pemiutang lain"] = (bf["Pemiutang lain"] ?: 0.0) + amt
          }
        }
        if (isCreditNote) bf["Penghutang perdagangan"] = (bf["Penghutang perdagangan"] ?: 0.0) - amt
        if (isDebitNote) {
          if (isStockReturn) {
            bf["Pemiutang dagangan"] = (bf["Pemiutang dagangan"] ?: 0.0) - amt
          } else {
            bf["Pemiutang lain"] = (bf["Pemiutang lain"] ?: 0.0) - amt
          }
        }
      }

      if (ty <= year) {
        if (st == StatementClassification.BS_TRANSFER) {
          if (t.categoryId == "transfer_bank_to_cash") {
            bank -= amt
            cash += amt
          } else if (t.categoryId == "transfer_cash_to_bank") {
            cash -= amt
            bank += amt
          }
        } else if (isIn) {
          if (isCash) cash += amt else bank += amt
        } else if (isOut) {
          if (isCash) cash -= amt else bank -= amt
        }

        when (st) {
          StatementClassification.BS_LAND_BUILDING_ADD -> land += amt
          StatementClassification.BS_EQUIPMENT_ADD -> equipment += amt
          StatementClassification.BS_VEHICLES_ADD -> vehicles += amt
          StatementClassification.BS_OTHER_NONCURRENT_ADD -> otherNC += amt
          StatementClassification.BS_INVESTMENT_ADD -> investments += amt
          StatementClassification.BS_TRADE_DEBTORS_COLLECT -> debtors -= amt
          StatementClassification.BS_TRADE_CREDITORS_PAY -> creditors -= amt
          StatementClassification.BS_OTHER_CREDITORS_PAY -> otherCreditors -= amt
          StatementClassification.BS_OTHER_DEBTORS_ADD -> otherDebtors += amt
          StatementClassification.BS_OTHER_CURRENT_ADD -> otherCurrent += amt
          StatementClassification.BS_LIABILITY_ADD -> loans += amt
          StatementClassification.BS_LIABILITY_PAY -> loans -= amt
          StatementClassification.BS_EQUITY_ADD -> capital += amt
          StatementClassification.BS_ASSET_DISPOSAL -> {
            val d = parseDisposal(t)
            if (d != null) {
              when (d.assetType) {
                AssetDisposalType.LAND_BUILDING -> land -= d.cost
                AssetDisposalType.VEHICLES -> vehicles -= d.cost
                AssetDisposalType.OTHER_NON_CURRENT -> otherNC -= d.cost
                AssetDisposalType.INVESTMENTS -> investments -= d.cost
                AssetDisposalType.EQUIPMENT -> equipment -= d.cost
              }
            }
          }
          else -> Unit
        }
        if (isCreditSale) debtors += amt
        if (isCreditPurchase) {
          if (st == StatementClassification.PNL_COGS) creditors += amt else otherCreditors += amt
        }
        if (isCreditNote) debtors -= amt
        if (isDebitNote) {
          if (isStockReturn) creditors -= amt else otherCreditors -= amt
        }
      }
    }

    // Build General Ledger (Lejar Am)
    val ledgers = linkedMapOf<String, MutableList<LedgerEntry>>()
    fun addLedger(accMs: String, date: String, desc: String, dr: Double, cr: Double) {
      val normalized = when (accMs) {
        "Beli Aset Bukan Semasa Lain" -> "Aset bukan semasa lain"
        "Beli Loji / Mesin / Peralatan" -> "Loji dan jentera"
        "Beli Kenderaan Perniagaan" -> "Kenderaan"
        "Beli Tanah / Bangunan" -> "Tanah dan bangunan"
        else -> accMs
      }
      val translatedAcc = tL(normalized)
      val entries = ledgers.getOrPut(translatedAcc) { mutableListOf() }
      entries.add(LedgerEntry(date = date, description = desc, debit = dr, credit = cr))
    }

    val debitNormalBf = setOf(
      "Tunai", "Bank", "Penghutang perdagangan", "Tanah dan bangunan",
      "Loji dan jentera", "Kenderaan", "Aset bukan semasa lain", "Pelaburan"
    )
    bf.forEach { (acc, value) ->
      if (abs(value) > 0.0001) {
        val isDebitNormal = acc in debitNormalBf
        if (isDebitNormal) {
          addLedger(
            accMs = acc,
            date = "$year-01-01",
            desc = tL("Baki Bawa Hadapan"),
            dr = if (value > 0) value else 0.0,
            cr = if (value < 0) -value else 0.0
          )
        } else {
          addLedger(
            accMs = acc,
            date = "$year-01-01",
            desc = tL("Baki Bawa Hadapan"),
            dr = if (value < 0) -value else 0.0,
            cr = if (value > 0) value else 0.0
          )
        }
      }
    }

    for (t in sortedTxs.filter { it.date.take(4).toIntOrNull() == year }) {
      val amt = if (t.amount.isFinite()) t.amount else 0.0
      val (accLabelMs, st) = AccountingCatalog.accountMetaFor(t.categoryId)
      val isCreditSale = t.type == TransactionType.CREDIT_SALE
      val isCreditPurchase = t.type == TransactionType.CREDIT_PURCHASE
      val isCreditNote = t.type == TransactionType.CREDIT_NOTE
      val isDebitNote = t.type == TransactionType.DEBIT_NOTE
      val isIn = t.type == TransactionType.IN || isCreditSale
      val isOut = t.type == TransactionType.OUT || isCreditPurchase
      val isCash = t.method == PaymentMethod.CASH

      val displayNote = if (st == StatementClassification.BS_ASSET_DISPOSAL) {
        formatDisposalNote(t, parseDisposal(t), lang)
      } else {
        t.note
      }

      var drAcc = ""
      var crAcc = ""

      when {
        st == StatementClassification.BS_TRANSFER -> {
          if (t.categoryId == "transfer_bank_to_cash") {
            drAcc = "Tunai"; crAcc = "Bank"
          } else {
            drAcc = "Bank"; crAcc = "Tunai"
          }
        }
        isCreditNote -> {
          drAcc = accLabelMs; crAcc = "Penghutang perdagangan"
        }
        isDebitNote -> {
          drAcc = if (st == StatementClassification.PNL_COGS || st == StatementClassification.PNL_PURCHASE_RETURN) {
            "Pemiutang dagangan"
          } else {
            "Pemiutang lain"
          }
          crAcc = accLabelMs
        }
        st == StatementClassification.BS_TRADE_DEBTORS_COLLECT -> {
          drAcc = if (isCash) "Tunai" else "Bank"; crAcc = "Penghutang perdagangan"
        }
        st == StatementClassification.BS_TRADE_CREDITORS_PAY -> {
          drAcc = "Pemiutang dagangan"; crAcc = if (isCash) "Tunai" else "Bank"
        }
        st == StatementClassification.BS_OTHER_CREDITORS_PAY -> {
          drAcc = "Pemiutang lain"; crAcc = if (isCash) "Tunai" else "Bank"
        }
        isIn -> {
          crAcc = accLabelMs
          drAcc = if (isCreditSale) "Penghutang perdagangan" else if (isCash) "Tunai" else "Bank"
        }
        isOut -> {
          drAcc = accLabelMs
          crAcc = if (isCreditPurchase) {
            if (st == StatementClassification.PNL_COGS) "Pemiutang dagangan" else "Pemiutang lain"
          } else {
            if (isCash) "Tunai" else "Bank"
          }
        }
      }

      if (drAcc.isNotEmpty() && crAcc.isNotEmpty()) {
        val descForDr = buildString {
          append(tL(crAcc))
          if (displayNote.isNotBlank()) append(" - ").append(displayNote)
        }
        val descForCr = buildString {
          append(tL(drAcc))
          if (displayNote.isNotBlank()) append(" - ").append(displayNote)
        }
        addLedger(drAcc, t.date, descForDr, amt, 0.0)
        addLedger(crAcc, t.date, descForCr, 0.0, amt)
      }
    }

    if (hasDisposalRecord) {
      otherIncomeByCategory["Untung / (Rugi) bersih pelupusan aset"] = disposalGainLoss
    }

    val opening = getOpeningStock(year, closingStockByYear, beginningBalances)
    val closing = getClosingStock(year, closingStockByYear, beginningBalances)
    val netCOGS = opening + purchases - closing
    val gross = revenue - netCOGS
    val netProfit = gross + otherIncome - expenses

    val firstStock = max(0.0, beginningBalances.openingStock)
    val brought = beginningBalances.broughtForward + priorProfit + (opening - firstStock)
    val carried = brought + netProfit - drawings

    val nonCurrent = land + equipment + vehicles + otherNC
    val current = closing + debtors + otherDebtors + cash + bank + otherCurrent
    val totalAssets = nonCurrent + investments + current
    val totalLiabilities = loans + creditors + otherCreditors
    val totalEquity = capital + carried
    val totalLE = totalLiabilities + totalEquity
    val diff = totalAssets - totalLE

    // Statement rows (localized)
    val rows = mutableListOf<StatementRow>()
    fun addRow(type: StatementType, section: String, label: String, amount: Double, note: String = "", isTotal: Boolean = false) {
      rows.add(StatementRow(type, section, label, amount, note, isTotal))
    }

    revenueByCategory.keys.sorted().forEach { k ->
      addRow(StatementType.PNL, labels.secRevenue, tL(k), revenueByCategory[k] ?: 0.0)
    }
    addRow(StatementType.PNL, labels.secRevenue, labels.totalRevenue, revenue, isTotal = true)

    otherIncomeByCategory.keys.sorted().forEach { k ->
      val note = if (k.contains("pelupusan", ignoreCase = true)) labels.disposalNote else ""
      addRow(StatementType.PNL, labels.secOtherIncome, tL(k), otherIncomeByCategory[k] ?: 0.0, note)
    }
    addRow(StatementType.PNL, labels.secOtherIncome, labels.totalOtherIncome, otherIncome, isTotal = true)

    addRow(StatementType.PNL, labels.secCogs, labels.openingStock, opening)
    addRow(StatementType.PNL, labels.secCogs, labels.addPurchases, purchases)
    addRow(StatementType.PNL, labels.secCogs, labels.lessClosingStock, -abs(closing))
    addRow(StatementType.PNL, labels.secCogs, labels.netCogs, netCOGS, isTotal = true)
    addRow(StatementType.PNL, labels.secSummaryPnl, labels.grossProfit, gross, isTotal = true)

    AccountingCatalog.officialExpenseLabelsMs.forEach { k ->
      addRow(StatementType.PNL, labels.secOpEx, tL(k), expensesByCategory[k] ?: 0.0)
    }
    expensesByCategory.keys.filter { it !in AccountingCatalog.officialExpenseLabelsMs }.sorted().forEach { k ->
      addRow(StatementType.PNL, labels.secExtraOpEx, tL(k), expensesByCategory[k] ?: 0.0)
    }
    addRow(StatementType.PNL, labels.secOpEx, labels.totalOpEx, expenses, isTotal = true)

    addRow(StatementType.PNL, labels.secSummaryPnl, labels.addOtherIncome, otherIncome)
    addRow(StatementType.PNL, labels.secSummaryPnl, labels.lessOpEx, -expenses)
    addRow(StatementType.PNL, labels.secSummaryPnl, labels.netProfit, netProfit, isTotal = true)

    // Balance Sheet rows
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, tL("Tanah dan bangunan"), land)
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, tL("Loji dan jentera"), equipment)
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, tL("Kenderaan"), vehicles)
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, tL("Aset bukan semasa lain"), otherNC)
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, labels.totalNonCurrentAssets, nonCurrent, isTotal = true)
    addRow(StatementType.BALANCE_SHEET, labels.secNonCurrentAssets, tL("Pelaburan"), investments)

    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, labels.closingStockAsset, closing)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, tL("Penghutang perdagangan"), debtors)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, tL("Penghutang lain"), otherDebtors)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, tL("Tunai"), cash)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, tL("Bank"), bank)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, labels.otherCurrentAsset, otherCurrent)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, labels.totalCurrentAssets, current, isTotal = true)
    addRow(StatementType.BALANCE_SHEET, labels.secCurrentAssets, labels.totalAssets, totalAssets, isTotal = true)

    addRow(StatementType.BALANCE_SHEET, labels.secLiabilities, tL("Pinjaman dan overdraf"), loans)
    addRow(StatementType.BALANCE_SHEET, labels.secLiabilities, tL("Pemiutang dagangan"), creditors)
    addRow(StatementType.BALANCE_SHEET, labels.secLiabilities, tL("Pemiutang lain"), otherCreditors)
    addRow(StatementType.BALANCE_SHEET, labels.secLiabilities, labels.totalLiabilities, totalLiabilities, isTotal = true)

    addRow(StatementType.BALANCE_SHEET, labels.secEquity, tL("Akaun modal"), capital)
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.broughtForward, brought)
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.currentYearProfit, netProfit)
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.drawingsNet, -abs(drawings))
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.carriedForward, carried)
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.totalEquity, totalEquity, isTotal = true)
    addRow(StatementType.BALANCE_SHEET, labels.secEquity, labels.totalLiabilitiesAndEquity, totalLE, isTotal = true)

    val isBalanced = abs(diff) < 0.01
    addRow(
      StatementType.CHECK,
      labels.secCheck,
      labels.balanceDiff,
      diff,
      note = if (isBalanced) labels.balanced else labels.unbalanced,
      isTotal = true
    )

    return FinancialStats(
      selectedYear = year.toString(),
      cashBalance = cash,
      bankBalance = bank,
      totalMoneyNow = cash + bank,
      totalRevenue = revenue,
      revenueByCategory = revenueByCategory,
      totalOtherIncome = otherIncome,
      otherIncomeByCategory = otherIncomeByCategory,
      totalPurchasesCogs = purchases,
      openingStock = opening,
      closingStock = closing,
      netCogs = netCOGS,
      grossProfit = gross,
      totalOperatingExpenses = expenses,
      expensesByCategory = expensesByCategory,
      totalExpenses = netCOGS + expenses,
      netProfit = netProfit,
      landBuilding = land,
      equipmentAsset = equipment,
      vehicles = vehicles,
      otherNonCurrent = otherNC,
      investments = investments,
      tradeDebtors = debtors,
      otherDebtors = otherDebtors,
      otherCurrent = otherCurrent,
      loansLiability = loans,
      tradeCreditors = creditors,
      otherCreditors = otherCreditors,
      ownerCapital = capital,
      ownerDrawings = drawings,
      broughtForward = brought,
      carriedForward = carried,
      nonCurrentAssets = nonCurrent,
      currentAssets = current,
      totalAssets = totalAssets,
      totalLiabilities = totalLiabilities,
      totalEquity = totalEquity,
      totalLiabilitiesAndEquity = totalLE,
      balanceDifference = diff,
      isBalanced = isBalanced,
      ledgers = ledgers.mapValues { it.value.toList() },
      rows = rows
    )
  }
}
