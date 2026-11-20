package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.BukuKiraStrings
import com.example.model.AccountingCatalog
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TealSoftBg
import com.example.ui.theme.TealSoftBorder
import com.example.ui.theme.TealTile

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
  val netProfit = stats?.netProfit ?: 0.0

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
          .clip(RoundedCornerShape(22.dp))
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
              color = Color.White.copy(alpha = 0.88f)
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
            style = TabularAmountStyle.merge(MaterialTheme.typography.displaySmall).copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Surface(
              color = Color.White.copy(alpha = 0.15f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.cashLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f))
                Spacer(Modifier.height(2.dp))
                Text(
                  BukuKiraStrings.formatCurrency(workspace.currency, stats?.cashBalance ?: 0.0),
                  style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
            Surface(
              color = Color.White.copy(alpha = 0.15f),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.weight(1f)
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(strings.bankLabel, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.82f))
                Spacer(Modifier.height(2.dp))
                Text(
                  BukuKiraStrings.formatCurrency(workspace.currency, stats?.bankBalance ?: 0.0),
                  style = TabularAmountStyle.merge(MaterialTheme.typography.titleMedium).copy(fontWeight = FontWeight.Bold),
                  color = Color.White
                )
              }
            }
          }
        }
      }
    }

    // Big Colorful Action Boxes (Money In / Money Out)
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        HomeActionBox(
          modifier = Modifier.weight(1f).testTag("record_in_button"),
          containerColor = EmeraldPrimary,
          icon = Icons.Rounded.Add,
          title = strings.addMoneyIn,
          subtitle = strings.moneyInSub,
          enabled = isLicenceOperational,
          onClick = onOpenMoneyIn
        )
        HomeActionBox(
          modifier = Modifier.weight(1f).testTag("record_out_button"),
          containerColor = CoralError,
          icon = Icons.Rounded.Remove,
          title = strings.addMoneyOut,
          subtitle = strings.moneyOutSub,
          enabled = isLicenceOperational,
          onClick = onOpenMoneyOut
        )
      }
    }

    // Colorful Cashflow Stat Boxes
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        ColorfulStatBox(
          modifier = Modifier.weight(1f),
          title = strings.moneyIn,
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, totalIn),
          accent = EmeraldPrimary,
          bgColor = TealSoftBg,
          borderColor = TealSoftBorder,
          icon = Icons.Rounded.ArrowDownward
        )
        ColorfulStatBox(
          modifier = Modifier.weight(1f),
          title = strings.moneyOut,
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, totalOut),
          accent = CoralError,
          bgColor = CoralContainer.copy(alpha = 0.42f),
          borderColor = CoralError.copy(alpha = 0.35f),
          icon = Icons.Rounded.ArrowUpward
        )
      }
    }

    // Colorful Net Profit & Trade Receivables Boxes
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        ColorfulStatBox(
          modifier = Modifier.weight(1f),
          title = "${strings.netProfitYear} $selectedYear",
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, netProfit),
          accent = if (netProfit >= 0) IndigoAccent else CoralError,
          bgColor = if (netProfit >= 0) IndigoContainer.copy(alpha = 0.55f) else CoralContainer.copy(alpha = 0.42f),
          borderColor = if (netProfit >= 0) IndigoAccent.copy(alpha = 0.35f) else CoralError.copy(alpha = 0.35f),
          icon = Icons.Rounded.TrendingUp
        )
        ColorfulStatBox(
          modifier = Modifier.weight(1f),
          title = strings.tradeDebtorsLabel,
          amountText = BukuKiraStrings.formatCurrency(workspace.currency, stats?.tradeDebtors ?: 0.0),
          accent = TealTile,
          bgColor = TealSoftBg,
          borderColor = TealSoftBorder,
          icon = Icons.Rounded.AccountBalanceWallet
        )
      }
    }

    // Recent Records Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(strings.recentRecords, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Text(strings.noRecordsYet, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentUserRole == TeamRole.OWNER && isLicenceOperational) {
              Button(
                onClick = onLoadSampleData,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent),
                modifier = Modifier
                  .fillMaxWidth()
                  .height(50.dp)
                  .testTag("load_sample_data_button")
              ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Muatkan Data Contoh 2026", fontWeight = FontWeight.Bold)
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
private fun HomeActionBox(
  modifier: Modifier,
  containerColor: Color,
  icon: ImageVector,
  title: String,
  subtitle: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = RoundedCornerShape(18.dp),
    color = if (enabled) containerColor else containerColor.copy(alpha = 0.45f),
    shadowElevation = 2.dp,
    modifier = modifier.heightIn(min = 96.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.22f),
        modifier = Modifier.size(34.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = Color.White,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = Color.White.copy(alpha = 0.9f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}

@Composable
private fun ColorfulStatBox(
  modifier: Modifier,
  title: String,
  amountText: String,
  accent: Color,
  bgColor: Color,
  borderColor: Color,
  icon: ImageVector
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    color = bgColor,
    border = BorderStroke(1.2.dp, borderColor)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = CircleShape,
          color = accent.copy(alpha = 0.14f),
          modifier = Modifier.size(24.dp)
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = title, tint = accent, modifier = Modifier.size(14.dp))
          }
        }
        Spacer(Modifier.width(6.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = accent,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      Text(
        text = amountText,
        style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.ExtraBold),
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
    TransactionType.CREDIT_NOTE, TransactionType.DEBIT_NOTE -> IndigoAccent
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
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          Surface(
            color = accent.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = tx.method.wireValue,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = accent,
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
          style = TabularAmountStyle.merge(MaterialTheme.typography.titleSmall).copy(fontWeight = FontWeight.Bold),
          color = accent
        )
        if (canDelete) {
          TextButton(
            onClick = onDelete,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.testTag("delete_tx_${tx.id}")
          ) {
            Text(BukuKiraStrings.ui(language).delete, style = MaterialTheme.typography.labelSmall, color = CoralError)
          }
        }
      }
    }
  }
}
