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
}
