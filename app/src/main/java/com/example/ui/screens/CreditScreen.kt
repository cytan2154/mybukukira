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
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AssignmentReturn
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.TabularAmountStyle

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

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Text(strings.creditTitle, style = MaterialTheme.typography.headlineSmall)
    }

    // Receivables & Payables Balance Cards
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Card(
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.tradeDebtorsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              BukuKiraStrings.formatCurrency(workspace.currency, stats?.tradeDebtors ?: 0.0),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge),
              color = EmeraldPrimary
            )
          }
        }
        Card(
          modifier = Modifier.weight(1f),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.tradeCreditorsLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              BukuKiraStrings.formatCurrency(workspace.currency, (stats?.tradeCreditors ?: 0.0) + (stats?.otherCreditors ?: 0.0)),
              style = TabularAmountStyle.merge(MaterialTheme.typography.titleLarge),
              color = CoralError
            )
          }
        }
      }
    }

    // 4 Quick Credit Entry Buttons (2x2 Grid)
    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CreditActionButton(
            modifier = Modifier.weight(1f).testTag("credit_sale_button"),
            icon = Icons.Default.PointOfSale,
            label = strings.creditSale,
            enabled = isLicenceOperational,
            onClick = { onOpenCreditEntry(TransactionType.CREDIT_SALE) }
          )
          CreditActionButton(
            modifier = Modifier.weight(1f).testTag("credit_purchase_button"),
            icon = Icons.Default.AddShoppingCart,
            label = strings.creditPurchase,
            enabled = isLicenceOperational,
            onClick = { onOpenCreditEntry(TransactionType.CREDIT_PURCHASE) }
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          CreditActionButton(
            modifier = Modifier.weight(1f).testTag("credit_note_button"),
            icon = Icons.Default.Receipt,
            label = strings.creditNote,
            enabled = isLicenceOperational,
            onClick = { onOpenCreditEntry(TransactionType.CREDIT_NOTE) }
          )
          CreditActionButton(
            modifier = Modifier.weight(1f).testTag("debit_note_button"),
            icon = Icons.Default.AssignmentReturn,
            label = strings.debitNote,
            enabled = isLicenceOperational,
            onClick = { onOpenCreditEntry(TransactionType.DEBIT_NOTE) }
          )
        }
      }
    }

    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("${strings.creditTitle} ($selectedYear)", style = MaterialTheme.typography.titleMedium)
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
private fun CreditActionButton(
  modifier: Modifier,
  icon: ImageVector,
  label: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  FilledTonalButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(54.dp)
  ) {
    Icon(icon, contentDescription = label, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
