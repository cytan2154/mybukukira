package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.IndigoAccent
import com.example.ui.theme.TealTile
import com.example.ui.viewmodel.RecordsFilter

@Composable
fun RecordsScreen(
  workspace: BusinessWorkspace,
  uid: String,
  currentUserRole: TeamRole,
  isLicenceOperational: Boolean,
  language: LanguageCode,
  searchQuery: String,
  selectedFilter: RecordsFilter,
  transactions: List<BookkeepingTransaction>,
  onSearchQueryChange: (String) -> Unit,
  onFilterChange: (RecordsFilter) -> Unit,
  onOpenMoneyIn: () -> Unit = {},
  onOpenMoneyOut: () -> Unit = {},
  onDeleteTransaction: (String) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  var pendingDeleteTx by remember { mutableStateOf<BookkeepingTransaction?>(null) }

  if (pendingDeleteTx != null) {
    val tx = pendingDeleteTx!!
    AlertDialog(
      onDismissRequest = { pendingDeleteTx = null },
      title = { Text(strings.delete) },
      text = { Text("${tx.date} — ${BukuKiraStrings.formatCurrency(workspace.currency, tx.amount)}\n${tx.note}") },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteTransaction(tx.id)
            pendingDeleteTx = null
          }
        ) {
          Text(strings.delete, color = CoralError)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDeleteTx = null }) {
          Text(strings.cancel)
        }
      }
    )
  }

  Column(
    modifier = Modifier.fillMaxSize()
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onOpenMoneyIn,
          enabled = isLicenceOperational,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
          modifier = Modifier.weight(1f).height(52.dp).testTag("records_quick_in_button")
        ) {
          Icon(Icons.Rounded.AddCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.addMoneyIn, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(
          onClick = onOpenMoneyOut,
          enabled = isLicenceOperational,
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = CoralError),
          modifier = Modifier.weight(1f).height(52.dp).testTag("records_quick_out_button")
        ) {
          Icon(Icons.Rounded.RemoveCircle, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(6.dp))
          Text(strings.addMoneyOut, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(strings.searchPlaceholder) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.searchPlaceholder) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Default.Clear, contentDescription = "Clear search")
            }
          }
        },
        shape = RoundedCornerShape(14.dp),
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .testTag("records_search_input")
      )

      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val filters = listOf(
          Triple(RecordsFilter.ALL, strings.filterAll, TealTile),
          Triple(RecordsFilter.IN, strings.filterIn, EmeraldPrimary),
          Triple(RecordsFilter.OUT, strings.filterOut, CoralError),
          Triple(RecordsFilter.CREDIT, strings.filterCredit, IndigoAccent)
        )
        filters.forEach { (filter, label, tint) ->
          val active = selectedFilter == filter
          Surface(
            onClick = { onFilterChange(filter) },
            shape = RoundedCornerShape(12.dp),
            color = if (active) tint else MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.2.dp, if (active) tint else tint.copy(alpha = 0.35f)),
            modifier = Modifier.height(44.dp).testTag("filter_chip_${filter.name.lowercase()}")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (active) Color.White else tint
              )
            }
          }
        }
      }
    }

    if (transactions.isEmpty()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(32.dp),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = strings.noRecordsYet,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(transactions, key = { it.id }) { tx ->
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
            onDelete = { pendingDeleteTx = tx }
          )
        }
      }
    }
  }
}
