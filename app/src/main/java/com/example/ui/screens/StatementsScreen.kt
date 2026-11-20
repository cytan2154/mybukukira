package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.BukuKiraStrings
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.OwnerPrivateInfo
import com.example.model.StatementRow
import com.example.model.StatementType
import com.example.model.TeamRole
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.OrangeDarkText
import com.example.ui.theme.OrangeSoftBg
import com.example.ui.theme.OrangeSoftBorder
import com.example.ui.theme.OrangeTile
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TealTile

@Composable
fun StatementsScreen(
  workspace: BusinessWorkspace,
  stats: FinancialStats?,
  selectedYear: String,
  availableYears: List<String>,
  language: LanguageCode,
  currentUserRole: TeamRole,
  ownerPrivateInfo: OwnerPrivateInfo? = null,
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

    // Closing Stock & Beginning Balances Big Colorful Boxes
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          onClick = { if (canEditBalances) onOpenClosingStock() },
          enabled = canEditBalances,
          shape = RoundedCornerShape(16.dp),
          color = OrangeSoftBg,
          border = BorderStroke(1.2.dp, OrangeSoftBorder),
          modifier = Modifier.weight(1f).heightIn(min = 78.dp).testTag("closing_stock_card")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Inventory, contentDescription = null, modifier = Modifier.size(16.dp), tint = OrangeDarkText)
              Spacer(Modifier.width(6.dp))
              Text(strings.closingStock, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = OrangeDarkText)
            }
            Text(
              BukuKiraStrings.formatCurrency(workspace.currency, stats?.closingStock ?: 0.0),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.ExtraBold),
              color = OrangeDarkText
            )
          }
        }

        Surface(
          onClick = { if (canEditBalances) onOpenBeginningBalances() },
          enabled = canEditBalances,
          shape = RoundedCornerShape(16.dp),
          color = IndigoContainer.copy(alpha = 0.65f),
          border = BorderStroke(1.2.dp, IndigoAccent.copy(alpha = 0.35f)),
          modifier = Modifier.weight(1f).heightIn(min = 78.dp).testTag("beginning_balances_card")
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.AccountBalance, contentDescription = null, modifier = Modifier.size(16.dp), tint = IndigoAccent)
              Spacer(Modifier.width(6.dp))
              Text(strings.beginningBalances, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = IndigoAccent)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                if (canEditBalances) "Ubah / Semak" else "Lihat",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = IndigoAccent
              )
              if (canEditBalances) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp), tint = IndigoAccent)
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

    // Big Colorful 2x2 Export & Backup Action Boxes
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          StatementColorfulActionBox(
            modifier = Modifier.weight(1f).testTag("export_pdf_button"),
            containerColor = EmeraldPrimary,
            icon = Icons.Default.PictureAsPdf,
            label = strings.exportPdf,
            enabled = true,
            onClick = onExportPdf
          )
          StatementColorfulActionBox(
            modifier = Modifier.weight(1f).testTag("export_csv_button"),
            containerColor = TealTile,
            icon = Icons.Default.TableView,
            label = strings.exportCsv,
            enabled = true,
            onClick = onExportCsv
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          StatementColorfulActionBox(
            modifier = Modifier.weight(1f).testTag("backup_json_button"),
            containerColor = IndigoAccent,
            icon = Icons.Default.SaveAlt,
            label = strings.backupJson,
            enabled = true,
            onClick = onBackupJson
          )
          StatementColorfulActionBox(
            modifier = Modifier.weight(1f).testTag("restore_json_button"),
            containerColor = OrangeTile,
            icon = Icons.Default.Restore,
            label = strings.restoreJson,
            enabled = currentUserRole == TeamRole.OWNER,
            onClick = onRestoreJson
          )
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
          item {
            OwnerSignatureCard(
              ownerName = workspace.ownerName,
              ownerIdNumber = ownerPrivateInfo?.ownerIdNumber,
              labels = labels
            )
          }
        }
        1 -> {
          val grouped = stats.rows.filter {
            it.statementType == StatementType.BALANCE_SHEET
          }.groupBy { it.section }
          grouped.forEach { (section, rows) ->
            item {
              StatementSectionCard(section = section, rows = rows, currency = workspace.currency)
            }
          }
          item {
            OwnerSignatureCard(
              ownerName = workspace.ownerName,
              ownerIdNumber = ownerPrivateInfo?.ownerIdNumber,
              labels = labels
            )
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
              val scrollState = rememberScrollState()
              val closingBalance = entries.lastOrNull()?.balance ?: 0.0
              Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
              ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                  Text(
                    text = "${labels.ledgerAccountPrefix}: $accountName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                  HorizontalDivider(thickness = 1.5.dp, color = MaterialTheme.colorScheme.onSurface)
                  BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val tableWidth = if (maxWidth >= 460.dp) maxWidth else 460.dp
                    Column(
                      modifier = Modifier
                        .let { if (maxWidth < 460.dp) it.horizontalScroll(scrollState) else it }
                        .width(tableWidth),
                      verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = labels.ledgerColDate,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.width(78.dp),
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = labels.ledgerColDetails,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.weight(1f).padding(end = 6.dp),
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = labels.ledgerColDebit,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.width(74.dp),
                          textAlign = TextAlign.End,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = labels.ledgerColCredit,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.width(74.dp),
                          textAlign = TextAlign.End,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                          text = labels.ledgerColBalance,
                          style = MaterialTheme.typography.labelSmall,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.width(80.dp),
                          textAlign = TextAlign.End,
                          color = MaterialTheme.colorScheme.onSurface
                        )
                      }
                      HorizontalDivider()
                      entries.forEach { e ->
                        Row(
                          modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                          verticalAlignment = Alignment.Top
                        ) {
                          Text(
                            text = e.date,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(78.dp)
                          )
                          Text(
                            text = e.description,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 6.dp)
                          )
                          Text(
                            text = if (e.debit > 0) BukuKiraStrings.formatAmount(e.debit) else "-",
                            style = TabularAmountStyle.merge(MaterialTheme.typography.bodySmall).copy(fontSize = 11.5.sp),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(74.dp),
                            textAlign = TextAlign.End
                          )
                          Text(
                            text = if (e.credit > 0) BukuKiraStrings.formatAmount(e.credit) else "-",
                            style = TabularAmountStyle.merge(MaterialTheme.typography.bodySmall).copy(fontSize = 11.5.sp),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(74.dp),
                            textAlign = TextAlign.End
                          )
                          Text(
                            text = BukuKiraStrings.formatAmount(e.balance),
                            style = TabularAmountStyle.merge(MaterialTheme.typography.bodySmall).copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(80.dp),
                            textAlign = TextAlign.End
                          )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                      }
                      Row(
                        modifier = Modifier
                          .fillMaxWidth()
                          .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                          .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Spacer(Modifier.width(74.dp))
                        Text(
                          text = labels.ledgerTotal,
                          style = MaterialTheme.typography.labelMedium,
                          fontWeight = FontWeight.Bold,
                          modifier = Modifier.weight(1f).padding(end = 6.dp)
                        )
                        Text(
                          text = BukuKiraStrings.formatAmount(entries.sumOf { it.debit }),
                          style = TabularAmountStyle.merge(MaterialTheme.typography.labelSmall).copy(fontWeight = FontWeight.Bold),
                          maxLines = 1,
                          softWrap = false,
                          modifier = Modifier.width(74.dp),
                          textAlign = TextAlign.End
                        )
                        Text(
                          text = BukuKiraStrings.formatAmount(entries.sumOf { it.credit }),
                          style = TabularAmountStyle.merge(MaterialTheme.typography.labelSmall).copy(fontWeight = FontWeight.Bold),
                          maxLines = 1,
                          softWrap = false,
                          modifier = Modifier.width(74.dp),
                          textAlign = TextAlign.End
                        )
                        Text(
                          text = BukuKiraStrings.formatAmount(closingBalance),
                          style = TabularAmountStyle.merge(MaterialTheme.typography.labelSmall).copy(fontWeight = FontWeight.Bold),
                          maxLines = 1,
                          softWrap = false,
                          modifier = Modifier.width(76.dp),
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

@Composable
private fun OwnerSignatureCard(
  ownerName: String,
  ownerIdNumber: String?,
  labels: BukuKiraStrings.StatementLabelSet
) {
  Card(
    modifier = Modifier.fillMaxWidth().testTag("owner_signature_card"),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom
    ) {
      Column(
        modifier = Modifier.weight(1f).padding(end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
      ) {
        Text(
          text = labels.preparedCertifiedBy,
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = ownerName.trim().ifEmpty { "-" },
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )
        val cleanId = ownerIdNumber?.trim().orEmpty()
        if (cleanId.isNotEmpty()) {
          Text(
            text = "${labels.ownerIdNoLabel} $cleanId",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      Column(
        horizontalAlignment = Alignment.End
      ) {
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(
          modifier = Modifier.width(135.dp),
          thickness = 1.dp,
          color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        Text(
          text = labels.ownerSignatureLabel,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.End
        )
      }
    }
  }
}

@Composable
private fun StatementColorfulActionBox(
  modifier: Modifier,
  containerColor: Color,
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(16.dp),
    color = if (enabled) containerColor else containerColor.copy(alpha = 0.42f),
    shadowElevation = 2.dp,
    modifier = modifier.heightIn(min = 58.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = Color.White,
        modifier = Modifier.size(20.dp)
      )
      Spacer(Modifier.width(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

