package com.example

import com.example.accounting.AccountingEngine
import com.example.export.ExportManager
import com.example.model.AccountingCatalog
import com.example.model.BeginningBalances
import com.example.model.BusinessWorkspace
import com.example.model.LanguageCode
import com.example.model.TeamRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportManagerTest {

  @Test
  fun jsonBackupAndRestore_roundTripsTransactionsAndSettings() {
    val ws = BusinessWorkspace(
      id = "biz_1",
      ownerId = "owner_1",
      memberUids = listOf("owner_1"),
      memberRoles = mapOf("owner_1" to TeamRole.OWNER),
      businessName = "Kedai Kopi & Roti Maju",
      businessRegNo = "SSM-123",
      businessType = "F&B",
      businessAddress = "Changlun",
      ownerName = "Ahmad",
      currency = "RM",
      financialYear = "2026",
      closingStockByYear = mapOf("2026" to 650.0),
      beginningBalances = BeginningBalances(cash = 100.0)
    )
    val txs = AccountingCatalog.sampleTransactions("biz_1", "owner_1", listOf("owner_1"))

    val json = ExportManager.buildJsonBackup(ws, txs)
    val parsed = ExportManager.parseJsonBackup(json, "biz_2", "owner_2", listOf("owner_2"))

    assertEquals("Kedai Kopi & Roti Maju", parsed.businessName)
    assertEquals(650.0, parsed.closingStockByYear["2026"] ?: 0.0, 0.001)
    assertEquals(100.0, parsed.beginningBalances?.cash ?: 0.0, 0.001)
    assertEquals(txs.size, parsed.transactions.size)
    assertEquals("biz_2", parsed.transactions.first().businessId)
  }

  @Test
  fun csvExport_includesUtf8BomBusinessHeaderAndStatements() {
    val ws = BusinessWorkspace(
      id = "biz_1",
      ownerId = "owner_1",
      memberUids = listOf("owner_1"),
      businessName = "Kedai Kopi Maju",
      currency = "RM",
      financialYear = "2026"
    )
    val txs = AccountingCatalog.sampleTransactions("biz_1", "owner_1", listOf("owner_1"))
    val stats = AccountingEngine.computeStatements(txs, BeginningBalances(), mapOf("2026" to 650.0), "2026", LanguageCode.EN)

    val csv = ExportManager.buildCsvReport(ws, txs, stats, LanguageCode.EN)
    assertTrue(csv.startsWith("\uFEFF"))
    assertTrue(csv.contains("Kedai Kopi Maju"))
    assertTrue(csv.contains("PROFIT AND LOSS STATEMENT"))
    assertTrue(csv.contains("BALANCED"))
  }

  @Test
  fun balanceSheetPdfLayout_alwaysFitsOnSingleA4Page_andLedgerFormatsThreeColumns() {
    val txs = AccountingCatalog.sampleTransactions("biz_1", "owner_1", listOf("owner_1"))
    val stats = AccountingEngine.computeStatements(txs, BeginningBalances(), mapOf("2026" to 650.0), "2026", LanguageCode.EN)

    val bsRows = stats.rows.filter {
      it.statementType == com.example.model.StatementType.BALANCE_SHEET
    }
    val pnlRows = stats.rows.filter {
      it.statementType == com.example.model.StatementType.PNL
    }
    assertTrue(pnlRows.none { it.section == "PROFIT & LOSS SUMMARY" })
    assertTrue(pnlRows.none { it.label == "Add: Other income" || it.label == "Less: Operating expenses" })

    val layout = ExportManager.computeBalanceSheetLayout(
      rows = bsRows,
      hasAddress = true,
      disclaimerLineCount = 3,
      hasOwnerId = true,
      pageHeight = 842f
    )
    assertTrue(layout.rowCount >= 20)
    assertTrue(layout.sectionCount >= 4)
    assertTrue(layout.fittedTotalHeight <= layout.maxAvailableHeight)

    // Even with heavily inflated disclaimers/notes, scale clamps total height onto 1 A4 page
    val extremeLayout = ExportManager.computeBalanceSheetLayout(
      rows = bsRows + bsRows,
      hasAddress = true,
      disclaimerLineCount = 10,
      hasOwnerId = true,
      pageHeight = 842f
    )
    assertTrue(extremeLayout.scale < 1f)
    assertTrue(extremeLayout.fittedTotalHeight <= extremeLayout.maxAvailableHeight + 0.01f)

    // Verify 3-column running balance & comma formatting for Bank ledger
    val bankLedger = stats.ledgers["Bank"] ?: emptyList()
    assertEquals("5,000.00", com.example.localization.BukuKiraStrings.formatAmount(bankLedger[0].balance))
    assertEquals("3,200.00", com.example.localization.BukuKiraStrings.formatAmount(bankLedger[1].balance))
    assertEquals("5,300.00", com.example.localization.BukuKiraStrings.formatAmount(bankLedger[4].balance))
  }
}
