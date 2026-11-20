package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessWorkspace
import com.example.model.LanguageCode
import com.example.model.TeamRole
import com.example.ui.theme.CoralError
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
          RecordsFilter.ALL to strings.filterAll,
          RecordsFilter.IN to strings.filterIn,
          RecordsFilter.OUT to strings.filterOut,
          RecordsFilter.CREDIT to strings.filterCredit
        )
        filters.forEach { (filter, label) ->
          FilterChip(
            selected = selectedFilter == filter,
            onClick = { onFilterChange(filter) },
            label = { Text(label) },
            modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
          )
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
