package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.StatementRow
import com.example.model.StatementType
import com.example.model.TeamRole
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TabularAmountStyle

@Composable
fun StatementsScreen(
  workspace: BusinessWorkspace,
  stats: FinancialStats?,
  selectedYear: String,
  availableYears: List<String>,
  language: LanguageCode,
  currentUserRole: TeamRole,
  onSelectYear: (String) -> Unit,
  onOpenClosingStock: () -> Unit,
  onOpenBeginningBalances: () -> Unit,
  onExportPdf: () -> Unit,
  onExportCsv: () -> Unit,
  onBackupJson: () -> Unit,
  onRestoreJson: () -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val labels = BukuKiraStrings.statementLabels(language)
  var selectedTab by remember { mutableIntStateOf(0) } // 0=P&L, 1=Balance Sheet, 2=General Ledger
  val canEditBalances = currentUserRole == TeamRole.OWNER || currentUserRole == TeamRole.ACCOUNTANT

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Year Selector Chips
    item {
      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(strings.financialYear, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          availableYears.forEach { yr ->
            FilterChip(
              selected = yr == selectedYear,
              onClick = { onSelectYear(yr) },
              label = { Text(yr) },
              modifier = Modifier.testTag("year_chip_$yr")
            )
          }
        }
      }
    }

    // Closing Stock & Beginning Balances Quick Cards
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Card(
          onClick = { if (canEditBalances) onOpenClosingStock() },
          modifier = Modifier.weight(1f).testTag("closing_stock_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text(strings.closingStock, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
              BukuKiraStrings.formatCurrency(workspace.currency, stats?.closingStock ?: 0.0),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium)
            )
          }
        }

        Card(
          onClick = { if (canEditBalances) onOpenBeginningBalances() },
          modifier = Modifier.weight(1f).testTag("beginning_balances_card"),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(6.dp))
              Text(strings.beginningBalances, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                if (canEditBalances) "Ubah / Semak" else "Lihat",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
              )
              if (canEditBalances) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
              }
            }
          }
        }
      }
    }

    // Balance Check Banner
    if (stats != null) {
      item {
        val balanced = stats.isBalanced
        Card(
          colors = CardDefaults.cardColors(
            containerColor = if (balanced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
          ),
          modifier = Modifier.fillMaxWidth().testTag("balance_check_banner")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (balanced) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = if (balanced) EmeraldPrimary else CoralError
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (balanced) labels.balanced else labels.unbalanced,
                style = MaterialTheme.typography.titleSmall,
                color = if (balanced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
              )
              Text(
                text = "${labels.balanceDiff}: ${BukuKiraStrings.formatCurrency(workspace.currency, stats.balanceDifference)}",
                style = MaterialTheme.typography.bodySmall,
                color = if (balanced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
              )
            }
          }
        }
      }
    }

    // Export Buttons
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onExportPdf,
            modifier = Modifier.weight(1f).height(48.dp).testTag("export_pdf_button")
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.exportPdf, maxLines = 1)
          }
          OutlinedButton(
            onClick = onExportCsv,
            modifier = Modifier.weight(1f).height(48.dp).testTag("export_csv_button")
          ) {
            Icon(Icons.Default.TableView, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.exportCsv, maxLines = 1)
          }
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onBackupJson,
            modifier = Modifier.weight(1f).height(48.dp).testTag("backup_json_button")
          ) {
            Icon(Icons.Default.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.backupJson, maxLines = 1)
          }
          OutlinedButton(
            onClick = onRestoreJson,
            enabled = currentUserRole == TeamRole.OWNER,
            modifier = Modifier.weight(1f).height(48.dp).testTag("restore_json_button")
          ) {
            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.restoreJson, maxLines = 1)
          }
        }
      }
    }

    // Statement Mode Switcher
    item {
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(strings.tabPnl, strings.tabBs, strings.tabLedger).forEachIndexed { idx, title ->
          SegmentedButton(
            selected = selectedTab == idx,
            onClick = { selectedTab = idx },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
            modifier = Modifier.testTag("statement_tab_$idx")
          ) {
            Text(title, maxLines = 1)
          }
        }
      }
    }

    if (stats != null) {
      when (selectedTab) {
        0 -> {
          val grouped = stats.rows.filter { it.statementType == StatementType.PNL }.groupBy { it.section }
          grouped.forEach { (section, rows) ->
            item {
              StatementSectionCard(section = section, rows = rows, currency = workspace.currency)
            }
          }
        }
        1 -> {
          val grouped = stats.rows.filter {
            it.statementType == StatementType.BALANCE_SHEET || it.statementType == StatementType.CHECK
          }.groupBy { it.section }
          grouped.forEach { (section, rows) ->
            item {
              StatementSectionCard(section = section, rows = rows, currency = workspace.currency)
            }
          }
        }
        else -> {
          if (stats.ledgers.isEmpty()) {
            item {
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Text(
                  strings.noRecordsYet,
                  modifier = Modifier.padding(20.dp),
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          } else {
            items(stats.ledgers.entries.toList(), key = { it.key }) { (accountName, entries) ->
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(accountName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                  HorizontalDivider()
                  Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Tarikh / Butiran", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1.6f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Dr", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Cr", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  entries.forEach { e ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                      Column(modifier = Modifier.weight(1.6f)) {
                        Text(e.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(e.description, style = MaterialTheme.typography.bodySmall)
                      }
                      Text(
                        text = if (e.debit > 0) String.format(java.util.Locale.US, "%.2f", e.debit) else "-",
                        style = TabularAmountStyle.merge(MaterialTheme.typography.bodySmall),
                        modifier = Modifier.weight(0.7f),
                        textAlign = TextAlign.End
                      )
                      Text(
                        text = if (e.credit > 0) String.format(java.util.Locale.US, "%.2f", e.credit) else "-",
                        style = TabularAmountStyle.merge(MaterialTheme.typography.bodySmall),
                        modifier = Modifier.weight(0.7f),
                        textAlign = TextAlign.End
                      )
                    }
                  }
                  HorizontalDivider()
                  Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Jumlah", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.6f))
                    Text(
                      String.format(java.util.Locale.US, "%.2f", entries.sumOf { it.debit }),
                      style = TabularAmountStyle.merge(MaterialTheme.typography.labelMedium),
                      modifier = Modifier.weight(0.7f),
                      textAlign = TextAlign.End
                    )
                    Text(
                      String.format(java.util.Locale.US, "%.2f", entries.sumOf { it.credit }),
                      style = TabularAmountStyle.merge(MaterialTheme.typography.labelMedium),
                      modifier = Modifier.weight(0.7f),
                      textAlign = TextAlign.End
                    )
                  }
                }
              }
            }
          }
        }
      }
    }

    // Disclaimer
    item {
      Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
      ) {
        Text(
          text = labels.disclaimer,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(14.dp)
        )
      }
    }
  }
}

@Composable
private fun StatementSectionCard(
  section: String,
  rows: List<StatementRow>,
  currency: String
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(section, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
      HorizontalDivider()
      rows.forEach { row ->
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = row.label,
              style = if (row.isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
              fontWeight = if (row.isTotal) FontWeight.Bold else FontWeight.Normal,
              modifier = Modifier.weight(1f).padding(end = 8.dp)
            )
            Text(
              text = BukuKiraStrings.formatCurrency(currency, row.amount),
              style = TabularAmountStyle.merge(
                if (row.isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
              ),
              color = when {
                row.amount < 0 -> CoralError
                row.isTotal -> EmeraldPrimary
                else -> MaterialTheme.colorScheme.onSurface
              }
            )
          }
          if (row.note.isNotBlank()) {
            Text(
              text = row.note,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}
