package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.AccountingCatalog
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TabularAmountStyle

@Composable
fun HomeScreen(
  workspace: BusinessWorkspace,
  stats: FinancialStats?,
  transactions: List<BookkeepingTransaction>,
  selectedYear: String,
  language: LanguageCode,
  pendingSyncCount: Int,
  isLicenceOperational: Boolean,
  currentUserRole: TeamRole,
  onOpenMoneyIn: () -> Unit,
  onOpenMoneyOut: () -> Unit,
  onViewAllRecords: () -> Unit,
  onLoadSampleData: () -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val yearTxs = transactions.filter { it.date.startsWith(selectedYear) }
  val totalIn = yearTxs.filter { it.type == TransactionType.IN }.sumOf { it.amount }
  val totalOut = yearTxs.filter { it.type == TransactionType.OUT }.sumOf { it.amount }
  val recent = yearTxs.take(5)

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    if (!isLicenceOperational) {
      item {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(10.dp))
            Text(
              strings.readOnlyLicence,
              style = MaterialTheme.typography.titleSmall,
              color = MaterialTheme.colorScheme.onErrorContainer
            )
          }
        }
      }
    }

    // Emerald Hero Liquidity Card
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .background(
            Brush.linearGradient(listOf(EmeraldDark, EmeraldPrimary))
          )
          .padding(20.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = strings.cashAndBank,
              style = MaterialTheme.typography.labelLarge,
              color = Color.White.copy(alpha = 0.85f)
            )
            Surface(
              color = Color.White.copy(alpha = 0.16f),
              shape = RoundedCornerShape(999.dp)
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (pendingSyncCount == 0) Icons.Default.CloudDone else Icons.Default.CloudUpload,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                  text = if (pendingSyncCount == 0) strings.synced else "${strings.pendingSync} ($pendingSyncCount)",
                  style = MaterialTheme.typography.labelSmall,
                  color = Color.White
                )
              }
            }
          }

          Text(
            text = BukuKiraStrings.formatCurrency(workspace.currency, stats?.totalMoneyNow ?: 0.0),
            style = TabularAmountStyle.merge(MaterialTheme.typography.displaySmall),
            color = Color.White
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Surface(
              color = Color.White.copy(alpha = 0.14f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.cashLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(2.dp))
                Text(
                  BukuKiraStrings.formatCurrency(workspace.currency, stats?.cashBalance ?: 0.0),
                  style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium),
                  color = Color.White
                )
              }
            }
            Surface(
              color = Color.White.copy(alpha = 0.14f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.bankLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                Spacer(Modifier.height(2.dp))
                Text(
                  BukuKiraStrings.formatCurrency(workspace.currency, stats?.bankBalance ?: 0.0),
                  style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium),
                  color = Color.White
                )
              }
            }
          }
        }
      }
    }

    // Primary Entry Buttons
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Button(
          onClick = onOpenMoneyIn,
          enabled = isLicenceOperational,
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .testTag("record_in_button"),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
        ) {
          Icon(Icons.Default.AddCircle, contentDescription = strings.addMoneyIn)
          Spacer(Modifier.width(8.dp))
          Text(strings.addMoneyIn, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
          onClick = onOpenMoneyOut,
          enabled = isLicenceOperational,
          modifier = Modifier
            .weight(1f)
            .height(54.dp)
            .testTag("record_out_button"),
          colors = ButtonDefaults.buttonColors(containerColor = CoralError)
        ) {
          Icon(Icons.Default.RemoveCircle, contentDescription = strings.addMoneyOut)
          Spacer(Modifier.width(8.dp))
          Text(strings.addMoneyOut, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }

    // Cashflow & Profit Cards
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        SummaryStatCard(
          modifier = Modifier.weight(1f),
          title = strings.moneyIn,
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, totalIn),
          accent = EmeraldPrimary,
          icon = Icons.Default.ArrowDownward
        )
        SummaryStatCard(
          modifier = Modifier.weight(1f),
          title = strings.moneyOut,
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, totalOut),
          accent = CoralError,
          icon = Icons.Default.ArrowUpward
        )
      }
    }

    item {
      val netProfit = stats?.netProfit ?: 0.0
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.TrendingUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                "${strings.netProfitYear} $selectedYear",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                BukuKiraStrings.formatCurrency(workspace.currency, netProfit),
                style = TabularAmountStyle.merge(MaterialTheme.typography.headlineSmall),
                color = if (netProfit >= 0) EmeraldPrimary else CoralError
              )
            }
          }
        }
      }
    }

    // Recent Records Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(strings.recentRecords, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onViewAllRecords, modifier = Modifier.testTag("home_view_all_button")) {
          Text(strings.viewAll)
          Spacer(Modifier.width(4.dp))
          Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
        }
      }
    }

    if (recent.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Text(strings.noRecordsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentUserRole == TeamRole.OWNER && isLicenceOperational) {
              OutlinedButton(
                onClick = onLoadSampleData,
                modifier = Modifier.testTag("load_sample_data_button")
              ) {
                Text("Muatkan Data Contoh 2026")
              }
            }
          }
        }
      }
    } else {
      items(recent, key = { it.id }) { tx ->
        TransactionRowCard(
          tx = tx,
          currency = workspace.currency,
          language = language,
          canDelete = false,
          onDelete = {}
        )
      }
    }
  }
}

@Composable
private fun SummaryStatCard(
  modifier: Modifier,
  title: String,
  amountText: String,
  accent: Color,
  icon: androidx.compose.ui.graphics.vector.ImageVector
) {
  Card(
    modifier = modifier,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Text(
        text = amountText,
        style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge),
        color = accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
fun TransactionRowCard(
  tx: BookkeepingTransaction,
  currency: String,
  language: LanguageCode,
  canDelete: Boolean,
  onDelete: () -> Unit
) {
  val isIncoming = tx.type == TransactionType.IN || tx.type == TransactionType.CREDIT_SALE
  val accent = when (tx.type) {
    TransactionType.IN, TransactionType.CREDIT_SALE -> EmeraldPrimary
    TransactionType.CREDIT_NOTE, TransactionType.DEBIT_NOTE -> MaterialTheme.colorScheme.tertiary
    else -> CoralError
  }
  val sign = if (isIncoming) "+" else "-"
  val categoryTitle = AccountingCatalog.localizedLabel(tx.categoryId, language)

  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = categoryTitle,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = tx.method.wireValue,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
          if (tx.hasPendingWrites) {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = "Pending sync",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(14.dp)
            )
          }
        }
        val subtitle = buildString {
          append(tx.date)
          if (!tx.partyName.isNullOrBlank()) append(" • ").append(tx.partyName)
          if (!tx.invoiceNo.isNullOrBlank()) append(" (#").append(tx.invoiceNo).append(")")
          if (tx.note.isNotBlank()) append(" — ").append(tx.note)
        }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
      Spacer(Modifier.width(10.dp))
      Column(horizontalAlignment = Alignment.End) {
        Text(
          text = "$sign${BukuKiraStrings.formatCurrency(currency, tx.amount)}",
          style = TabularAmountStyle.merge(MaterialTheme.typography.titleSmall),
          color = accent
        )
        if (canDelete) {
          TextButton(
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.testTag("delete_tx_${tx.id}")
          ) {
            Text("Padam", style = MaterialTheme.typography.labelSmall, color = CoralError)
          }
        }
      }
    }
  }
}
