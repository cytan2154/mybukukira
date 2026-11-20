package com.example

import com.example.accounting.AccountingEngine
import com.example.model.AccountingCatalog
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.LanguageCode
import com.example.model.PaymentMethod
import com.example.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountingEngineTest {

  @Test
  fun sampleBusinessData_2026_matchesBukuKiraReferenceAndBalances() {
    val txs = AccountingCatalog.sampleTransactions("biz_1", "owner_1", listOf("owner_1"))
    val stats = AccountingEngine.computeStatements(
      transactions = txs,
      beginningBalances = BeginningBalances(),
      closingStockByYear = mapOf("2026" to 650.0),
      financialYear = "2026",
      lang = LanguageCode.MS
    )

    assertEquals(8450.0, stats.totalRevenue, 0.001)
    assertEquals(2400.0, stats.totalPurchasesCogs, 0.001)
    assertEquals(0.0, stats.openingStock, 0.001)
    assertEquals(650.0, stats.closingStock, 0.001)
    assertEquals(1750.0, stats.netCogs, 0.001)
    assertEquals(6700.0, stats.grossProfit, 0.001)
    assertEquals(2400.0, stats.totalOperatingExpenses, 0.001)
    assertEquals(4300.0, stats.netProfit, 0.001)
    assertEquals(2050.0, stats.cashBalance, 0.001)
    assertEquals(5300.0, stats.bankBalance, 0.001)
    assertEquals(7350.0, stats.totalMoneyNow, 0.001)
    assertEquals(1800.0, stats.equipmentAsset, 0.001)
    assertEquals(9800.0, stats.totalAssets, 0.001)
    assertEquals(6000.0, stats.ownerCapital, 0.001)
    assertEquals(500.0, stats.ownerDrawings, 0.001)
    assertEquals(3800.0, stats.carriedForward, 0.001)
    assertEquals(9800.0, stats.totalLiabilitiesAndEquity, 0.001)
    assertEquals(0.0, stats.balanceDifference, 0.001)
    assertTrue(stats.isBalanced)

    val bankLedger = stats.ledgers["Bank"] ?: emptyList()
    assertEquals(5, bankLedger.size)
    assertEquals(5000.0, bankLedger[0].balance, 0.001)
    assertEquals(3200.0, bankLedger[1].balance, 0.001)
    assertEquals(6800.0, bankLedger[2].balance, 0.001)
    assertEquals(5600.0, bankLedger[3].balance, 0.001)
    assertEquals(5300.0, bankLedger[4].balance, 0.001)
  }

  @Test
  fun assetDisposal_computesNetGainLossAndRemovesCarryingCostFromBalanceSheet() {
    val txs = listOf(
      BookkeepingTransaction("1", "b", "u", listOf("u"), "2026-01-01", TransactionType.IN, "owner_capital", PaymentMethod.BANK, 10000.0, "Modal"),
      BookkeepingTransaction("2", "b", "u", listOf("u"), "2026-02-01", TransactionType.OUT, "buy_vehicle", PaymentMethod.BANK, 4000.0, "Van lama"),
      BookkeepingTransaction(
        id = "3",
        businessId = "b",
        createdByUid = "u",
        memberUids = listOf("u"),
        date = "2026-05-10",
        type = TransactionType.IN,
        categoryId = "asset_disposal",
        method = PaymentMethod.BANK,
        amount = 4500.0,
        note = "Jual van terpakai",
        disposalAssetType = AssetDisposalType.VEHICLES,
        disposalAssetCost = 4000.0
      )
    )
    val stats = AccountingEngine.computeStatements(
      transactions = txs,
      beginningBalances = BeginningBalances(),
      closingStockByYear = emptyMap(),
      financialYear = "2026",
      lang = LanguageCode.EN
    )

    assertEquals(0.0, stats.vehicles, 0.001)
    assertEquals(500.0, stats.totalOtherIncome, 0.001)
    assertEquals(500.0, stats.netProfit, 0.001)
    assertEquals(10500.0, stats.bankBalance, 0.001)
    assertTrue(stats.isBalanced)
  }

  @Test
  fun creditSalesPurchasesNotes_updateReceivablesPayablesAndKeepBalanceSheetBalanced() {
    val txs = listOf(
      BookkeepingTransaction("1", "b", "u", listOf("u"), "2026-01-01", TransactionType.IN, "owner_capital", PaymentMethod.BANK, 5000.0, "Capital"),
      BookkeepingTransaction("2", "b", "u", listOf("u"), "2026-02-01", TransactionType.CREDIT_SALE, "credit_sale", PaymentMethod.CREDIT, 2000.0, "Inv 001", partyName = "Restoran A", invoiceNo = "INV-001"),
      BookkeepingTransaction("3", "b", "u", listOf("u"), "2026-02-10", TransactionType.CREDIT_NOTE, "sales_return_refund", PaymentMethod.CREDIT, 200.0, "Damaged goods", partyName = "Restoran A", invoiceNo = "CN-001"),
      BookkeepingTransaction("4", "b", "u", listOf("u"), "2026-02-15", TransactionType.IN, "debtors_payment", PaymentMethod.BANK, 800.0, "Partial settlement"),
      BookkeepingTransaction("5", "b", "u", listOf("u"), "2026-03-01", TransactionType.CREDIT_PURCHASE, "credit_purchase", PaymentMethod.CREDIT, 1500.0, "Stock credit", partyName = "Pembekal B", invoiceNo = "BILL-10"),
      BookkeepingTransaction("6", "b", "u", listOf("u"), "2026-03-05", TransactionType.DEBIT_NOTE, "purchase_return_refund", PaymentMethod.CREDIT, 300.0, "Return stock", partyName = "Pembekal B", invoiceNo = "DN-10"),
      BookkeepingTransaction("7", "b", "u", listOf("u"), "2026-03-10", TransactionType.CREDIT_PURCHASE, "buy_equipment", PaymentMethod.CREDIT, 1200.0, "Mixer on credit", partyName = "Mesin Sdn Bhd", invoiceNo = "EQ-99")
    )

    val stats = AccountingEngine.computeStatements(
      transactions = txs,
      beginningBalances = BeginningBalances(),
      closingStockByYear = mapOf("2026" to 400.0),
      financialYear = "2026",
      lang = LanguageCode.MS
    )

    assertEquals(1800.0, stats.totalRevenue, 0.001)
    assertEquals(1200.0, stats.totalPurchasesCogs, 0.001)
    assertEquals(800.0, stats.netCogs, 0.001)
    assertEquals(1000.0, stats.netProfit, 0.001)
    assertEquals(1000.0, stats.tradeDebtors, 0.001)
    assertEquals(1200.0, stats.tradeCreditors, 0.001)
    assertEquals(1200.0, stats.otherCreditors, 0.001)
    assertEquals(1200.0, stats.equipmentAsset, 0.001)
    assertEquals(5800.0, stats.bankBalance, 0.001)
    assertTrue(stats.isBalanced)
  }

  @Test
  fun multiYearStockAndBroughtForwardRollover_staysBalancedInYear2() {
    val txs = listOf(
      BookkeepingTransaction("1", "b", "u", listOf("u"), "2025-01-01", TransactionType.IN, "owner_capital", PaymentMethod.CASH, 3000.0, "Modal"),
      BookkeepingTransaction("2", "b", "u", listOf("u"), "2025-06-01", TransactionType.OUT, "buy_stock", PaymentMethod.CASH, 1000.0, "Stok 2025"),
      BookkeepingTransaction("3", "b", "u", listOf("u"), "2025-09-01", TransactionType.IN, "sales_goods", PaymentMethod.CASH, 1500.0, "Jualan 2025"),
      BookkeepingTransaction("4", "b", "u", listOf("u"), "2026-03-01", TransactionType.IN, "sales_goods", PaymentMethod.CASH, 900.0, "Jualan 2026")
    )
    val stockByYear = mapOf("2025" to 400.0, "2026" to 100.0)

    val stats2025 = AccountingEngine.computeStatements(txs, BeginningBalances(), stockByYear, "2025", LanguageCode.MS)
    val stats2026 = AccountingEngine.computeStatements(txs, BeginningBalances(), stockByYear, "2026", LanguageCode.MS)

    assertEquals(900.0, stats2025.netProfit, 0.001)
    assertTrue(stats2025.isBalanced)

    assertEquals(400.0, stats2026.openingStock, 0.001)
    assertEquals(100.0, stats2026.closingStock, 0.001)
    assertEquals(300.0, stats2026.netCogs, 0.001)
    assertEquals(600.0, stats2026.netProfit, 0.001)
    assertEquals(900.0, stats2026.broughtForward, 0.001)
    assertEquals(1500.0, stats2026.carriedForward, 0.001)
    assertTrue(stats2026.isBalanced)
    assertTrue((stats2026.ledgers["Tunai"] ?: emptyList()).any { it.description == "Baki Bawa Hadapan" && it.debit == 3500.0 })
  }

  @Test
  fun unbalancedBeginningBalances_reportsExactDifferenceAndNotBalanced() {
    val stats = AccountingEngine.computeStatements(
      transactions = emptyList(),
      beginningBalances = BeginningBalances(cash = 1000.0, capital = 800.0),
      closingStockByYear = emptyMap(),
      financialYear = "2026",
      lang = LanguageCode.EN
    )
    assertFalse(stats.isBalanced)
    assertEquals(200.0, stats.balanceDifference, 0.001)
  }
}
