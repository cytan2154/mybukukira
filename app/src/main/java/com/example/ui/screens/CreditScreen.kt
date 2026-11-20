package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.rounded.AssignmentReturn
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.BukuKiraStrings
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.OrangeDarkText
import com.example.ui.theme.OrangeSoftBg
import com.example.ui.theme.OrangeSoftBorder
import com.example.ui.theme.OrangeTile
import com.example.ui.theme.PinkTile
import com.example.ui.theme.TabularAmountStyle
import com.example.ui.theme.TealSoftBg
import com.example.ui.theme.TealSoftBorder
import com.example.ui.theme.TealTile

@Composable
fun CreditScreen(
  workspace: BusinessWorkspace,
  uid: String,
  stats: FinancialStats?,
  transactions: List<BookkeepingTransaction>,
  selectedYear: String,
  language: LanguageCode,
  currentUserRole: TeamRole,
  isLicenceOperational: Boolean,
  onOpenCreditEntry: (TransactionType) -> Unit,
  onDeleteTransaction: (String) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val creditTxs = transactions.filter {
    it.date.startsWith(selectedYear) && it.type in listOf(
      TransactionType.CREDIT_SALE,
      TransactionType.CREDIT_PURCHASE,
      TransactionType.CREDIT_NOTE,
      TransactionType.DEBIT_NOTE
    )
  }
  val totalCreditSales = creditTxs
    .filter { it.type == TransactionType.CREDIT_SALE }
    .sumOf { it.amount }
  val totalCreditPurchases = creditTxs
    .filter { it.type == TransactionType.CREDIT_PURCHASE }
    .sumOf { it.amount }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Credit Sales & Purchases 2x2 Big Colorful Tiles Container
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
      ) {
        Column(
          modifier = Modifier.padding(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = strings.creditSectionTitle,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = strings.creditSectionSubtitle,
              style = MaterialTheme.typography.bodySmall,
              lineHeight = 18.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              CreditTileBox(
                modifier = Modifier.weight(1f).testTag("credit_sale_button"),
                containerColor = TealTile,
                icon = Icons.Rounded.ReceiptLong,
                title = strings.creditSale,
                subtitle = strings.creditSaleSub,
                enabled = isLicenceOperational,
                onClick = { onOpenCreditEntry(TransactionType.CREDIT_SALE) }
              )
              CreditTileBox(
                modifier = Modifier.weight(1f).testTag("credit_purchase_button"),
                containerColor = OrangeTile,
                icon = Icons.Rounded.ShoppingCart,
                title = strings.creditPurchase,
                subtitle = strings.creditPurchaseSub,
                enabled = isLicenceOperational,
                onClick = { onOpenCreditEntry(TransactionType.CREDIT_PURCHASE) }
              )
            }
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              CreditTileBox(
                modifier = Modifier.weight(1f).testTag("credit_note_button"),
                containerColor = IndigoAccent,
                icon = Icons.Rounded.Receipt,
                title = strings.creditNote,
                subtitle = strings.creditNoteSub,
                enabled = isLicenceOperational,
                onClick = { onOpenCreditEntry(TransactionType.CREDIT_NOTE) }
              )
              CreditTileBox(
                modifier = Modifier.weight(1f).testTag("debit_note_button"),
                containerColor = PinkTile,
                icon = Icons.Rounded.AssignmentReturn,
                title = strings.debitNote,
                subtitle = strings.debitNoteSub,
                enabled = isLicenceOperational,
                onClick = { onOpenCreditEntry(TransactionType.DEBIT_NOTE) }
              )
            }
          }
        }
      }
    }

    // Total Credit Sales & Total Credit Purchases Summary Boxes
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          color = TealSoftBg,
          border = BorderStroke(1.2.dp, TealSoftBorder)
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = strings.totalCreditSalesLabel,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = TealTile,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = BukuKiraStrings.formatCurrency(workspace.currency, totalCreditSales),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.ExtraBold),
              color = TealTile,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${strings.tradeDebtorsLabel}: ${BukuKiraStrings.formatCurrency(workspace.currency, stats?.tradeDebtors ?: 0.0)}",
              style = MaterialTheme.typography.labelSmall,
              color = TealTile.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Surface(
          modifier = Modifier.weight(1f),
          shape = RoundedCornerShape(16.dp),
          color = OrangeSoftBg,
          border = BorderStroke(1.2.dp, OrangeSoftBorder)
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = strings.totalCreditPurchasesLabel,
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = OrangeDarkText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = BukuKiraStrings.formatCurrency(workspace.currency, totalCreditPurchases),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge).copy(fontWeight = FontWeight.ExtraBold),
              color = OrangeDarkText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${strings.tradeCreditorsLabel}: ${BukuKiraStrings.formatCurrency(workspace.currency, (stats?.tradeCreditors ?: 0.0) + (stats?.otherCreditors ?: 0.0))}",
              style = MaterialTheme.typography.labelSmall,
              color = OrangeDarkText.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }

    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = TealTile)
        Spacer(Modifier.width(8.dp))
        Text("${strings.creditTitle} ($selectedYear)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      }
    }

    if (creditTxs.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Text(
            strings.noRecordsYet,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
          )
        }
      }
    } else {
      items(creditTxs, key = { it.id }) { tx ->
        val canDelete = isLicenceOperational && (
          currentUserRole == TeamRole.OWNER ||
            currentUserRole == TeamRole.ACCOUNTANT ||
            tx.createdByUid == uid
          )
        TransactionRowCard(
          tx = tx,
          currency = workspace.currency,
          language = language,
          canDelete = canDelete,
          onDelete = { onDeleteTransaction(tx.id) }
        )
      }
    }
  }
}

@Composable
private fun CreditTileBox(
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
    modifier = modifier.heightIn(min = 114.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = Color.White,
        modifier = Modifier.size(26.dp)
      )
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
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.9f),
          maxLines = 2,
          lineHeight = 16.sp,
          overflow = TextOverflow.Ellipsis
        )
      }
    }
  }
}
