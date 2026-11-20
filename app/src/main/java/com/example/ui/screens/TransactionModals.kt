package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.localization.BukuKiraStrings
import com.example.model.AccountingCatalog
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.LanguageCode
import com.example.model.PaymentMethod
import com.example.model.TransactionType
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEntrySheet(
  initialType: TransactionType,
  currency: String,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onSave: (
    date: String,
    type: TransactionType,
    categoryId: String,
    method: PaymentMethod,
    amount: Double,
    note: String,
    disposalType: AssetDisposalType?,
    disposalCost: Double?
  ) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  var txType by remember { mutableStateOf(if (initialType == TransactionType.OUT) TransactionType.OUT else TransactionType.IN) }
  val categories = if (txType == TransactionType.IN) AccountingCatalog.inCategories else AccountingCatalog.outCategories
  var selectedCatId by remember(txType) { mutableStateOf(categories.first().id) }
  var method by remember { mutableStateOf(PaymentMethod.CASH) }
  var dateText by remember {
    mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
  }
  var amountText by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }
  var disposalType by remember { mutableStateOf(AssetDisposalType.EQUIPMENT) }
  var disposalCostText by remember { mutableStateOf("") }

  val isAssetDisposal = selectedCatId == "asset_disposal"
  val parsedAmount = amountText.toDoubleOrNull()
  val parsedDisposalCost = disposalCostText.toDoubleOrNull()
  val canSubmit = parsedAmount != null && parsedAmount >= 0.0 &&
    Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(dateText.trim()) &&
    (!isAssetDisposal || (parsedDisposalCost != null && parsedDisposalCost > 0.0))

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = if (txType == TransactionType.IN) strings.addMoneyIn else strings.addMoneyOut,
        style = MaterialTheme.typography.headlineSmall
      )

      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        listOf(TransactionType.IN to strings.moneyIn, TransactionType.OUT to strings.moneyOut).forEachIndexed { idx, (type, label) ->
          SegmentedButton(
            selected = txType == type,
            onClick = { txType = type },
            shape = SegmentedButtonDefaults.itemShape(index = idx, count = 2)
          ) {
            Text(label)
          }
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = method == PaymentMethod.CASH,
          onClick = { method = PaymentMethod.CASH },
          label = { Text(strings.cashLabel) },
          modifier = Modifier.testTag("method_cash_chip")
        )
        FilterChip(
          selected = method == PaymentMethod.BANK,
          onClick = { method = PaymentMethod.BANK },
          label = { Text(strings.bankLabel) },
          modifier = Modifier.testTag("method_bank_chip")
        )
      }

      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text("Jumlah ($currency)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_amount_input")
      )

      OutlinedTextField(
        value = dateText,
        onValueChange = { dateText = it },
        label = { Text("Tarikh (yyyy-MM-dd)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("tx_date_input")
      )

      Text("Kategori", style = MaterialTheme.typography.titleSmall)
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        categories.forEach { cat ->
          FilterChip(
            selected = selectedCatId == cat.id,
            onClick = { selectedCatId = cat.id },
            label = { Text(AccountingCatalog.localizedLabel(cat.id, language)) },
            modifier = Modifier.testTag("cat_chip_${cat.id}")
          )
        }
      }

      if (isAssetDisposal) {
        HorizontalDivider()
        Text("Maklumat Pelupusan Aset", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AssetDisposalType.entries.forEach { asset ->
            FilterChip(
              selected = disposalType == asset,
              onClick = { disposalType = asset },
              label = { Text(asset.wireValue) }
            )
          }
        }
        OutlinedTextField(
          value = disposalCostText,
          onValueChange = { disposalCostText = it },
          label = { Text("Kos Asal Aset ($currency)") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("tx_disposal_cost_input")
        )
      }

      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text("Nota / Butiran") },
        modifier = Modifier.fillMaxWidth().testTag("tx_note_input")
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
          Text(strings.cancel)
        }
        Button(
          onClick = {
            onSave(
              dateText.trim(),
              txType,
              selectedCatId,
              method,
              parsedAmount!!,
              noteText.trim(),
              if (isAssetDisposal) disposalType else null,
              if (isAssetDisposal) parsedDisposalCost else null
            )
          },
          enabled = canSubmit,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (txType == TransactionType.IN) EmeraldPrimary else CoralError
          ),
          modifier = Modifier.weight(1.5f).height(50.dp).testTag("save_transaction_button")
        ) {
          Text(strings.save)
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreditEntrySheet(
  initialType: TransactionType,
  currency: String,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onSave: (
    date: String,
    type: TransactionType,
    categoryId: String,
    amount: Double,
    partyName: String,
    invoiceNo: String,
    note: String
  ) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  var selectedPurchaseOptId by remember { mutableStateOf("credit_purchase") }
  var dateText by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
  var amountText by remember { mutableStateOf("") }
  var partyName by remember { mutableStateOf("") }
  var invoiceNo by remember { mutableStateOf("") }
  var noteText by remember { mutableStateOf("") }

  val categoryId = when (initialType) {
    TransactionType.CREDIT_SALE -> "credit_sale"
    TransactionType.CREDIT_NOTE -> "sales_return_refund"
    TransactionType.DEBIT_NOTE -> "purchase_return_refund"
    else -> selectedPurchaseOptId
  }

  val title = when (initialType) {
    TransactionType.CREDIT_SALE -> strings.creditSale
    TransactionType.CREDIT_PURCHASE -> strings.creditPurchase
    TransactionType.CREDIT_NOTE -> strings.creditNote
    TransactionType.DEBIT_NOTE -> strings.debitNote
    else -> strings.creditTitle
  }

  val parsedAmount = amountText.toDoubleOrNull()
  val canSubmit = parsedAmount != null && parsedAmount > 0.0 &&
    Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(dateText.trim())

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(title, style = MaterialTheme.typography.headlineSmall)

      if (initialType == TransactionType.CREDIT_PURCHASE) {
        Text("Item Belian Kredit", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AccountingCatalog.creditPurchaseOptions.forEach { opt ->
            val label = when (language) {
              LanguageCode.MS -> opt.labelMs
              LanguageCode.EN -> opt.labelEn
              LanguageCode.ZH -> opt.labelZh
            }
            FilterChip(
              selected = selectedPurchaseOptId == opt.id,
              onClick = { selectedPurchaseOptId = opt.id },
              label = { Text(label) }
            )
          }
        }
      }

      OutlinedTextField(
        value = partyName,
        onValueChange = { partyName = it },
        label = { Text("Nama Pelanggan / Pembekal") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("credit_party_input")
      )

      OutlinedTextField(
        value = invoiceNo,
        onValueChange = { invoiceNo = it },
        label = { Text("No. Invois / Bil / Nota") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("credit_invoice_input")
      )

      OutlinedTextField(
        value = amountText,
        onValueChange = { amountText = it },
        label = { Text("Jumlah ($currency)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("credit_amount_input")
      )

      OutlinedTextField(
        value = dateText,
        onValueChange = { dateText = it },
        label = { Text("Tarikh (yyyy-MM-dd)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
      )

      OutlinedTextField(
        value = noteText,
        onValueChange = { noteText = it },
        label = { Text("Nota") },
        modifier = Modifier.fillMaxWidth()
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
          Text(strings.cancel)
        }
        Button(
          onClick = {
            onSave(
              dateText.trim(),
              initialType,
              categoryId,
              parsedAmount!!,
              partyName.trim(),
              invoiceNo.trim(),
              noteText.trim()
            )
          },
          enabled = canSubmit,
          modifier = Modifier.weight(1.5f).height(50.dp).testTag("save_credit_button")
        ) {
          Text(strings.save)
        }
      }
    }
  }
}

@Composable
fun ClosingStockDialog(
  year: String,
  currentAmount: Double,
  currency: String,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onSave: (Double) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  var text by remember(currentAmount) { mutableStateOf(String.format(Locale.US, "%.2f", currentAmount)) }
  val parsed = text.toDoubleOrNull()

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("${strings.closingStock} ($year)") },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Nilai Stok Akhir ($currency)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("closing_stock_input")
      )
    },
    confirmButton = {
      Button(
        onClick = { parsed?.let { onSave(it.coerceAtLeast(0.0)) } },
        enabled = parsed != null && parsed >= 0.0,
        modifier = Modifier.testTag("save_closing_stock_button")
      ) {
        Text(strings.save)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(strings.cancel) }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeginningBalancesSheet(
  initial: BeginningBalances,
  currency: String,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onSave: (BeginningBalances) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  fun fmt(v: Double) = if (v == 0.0) "" else String.format(Locale.US, "%.2f", v)

  var openingStock by remember { mutableStateOf(fmt(initial.openingStock)) }
  var cash by remember { mutableStateOf(fmt(initial.cash)) }
  var bank by remember { mutableStateOf(fmt(initial.bank)) }
  var tradeDebtors by remember { mutableStateOf(fmt(initial.tradeDebtors)) }
  var otherDebtors by remember { mutableStateOf(fmt(initial.otherDebtors)) }
  var otherCurrent by remember { mutableStateOf(fmt(initial.otherCurrent)) }
  var landBuilding by remember { mutableStateOf(fmt(initial.landBuilding)) }
  var equipment by remember { mutableStateOf(fmt(initial.equipment)) }
  var vehicles by remember { mutableStateOf(fmt(initial.vehicles)) }
  var otherNonCurrent by remember { mutableStateOf(fmt(initial.otherNonCurrent)) }
  var investments by remember { mutableStateOf(fmt(initial.investments)) }
  var loans by remember { mutableStateOf(fmt(initial.loans)) }
  var tradeCreditors by remember { mutableStateOf(fmt(initial.tradeCreditors)) }
  var otherCreditors by remember { mutableStateOf(fmt(initial.otherCreditors)) }
  var capital by remember { mutableStateOf(fmt(initial.capital)) }
  var broughtForward by remember { mutableStateOf(fmt(initial.broughtForward)) }

  fun num(s: String) = s.toDoubleOrNull() ?: 0.0

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(strings.beginningBalances, style = MaterialTheme.typography.headlineSmall)
      Text("Baki permulaan akaun kunci kira-kira ($currency)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

      BalanceField("Stok Awal", openingStock) { openingStock = it }
      BalanceField("Tunai", cash) { cash = it }
      BalanceField("Bank", bank) { bank = it }
      BalanceField("Penghutang Dagangan", tradeDebtors) { tradeDebtors = it }
      BalanceField("Penghutang Lain", otherDebtors) { otherDebtors = it }
      BalanceField("Aset Semasa Lain", otherCurrent) { otherCurrent = it }
      BalanceField("Tanah & Bangunan", landBuilding) { landBuilding = it }
      BalanceField("Loji & Jentera", equipment) { equipment = it }
      BalanceField("Kenderaan", vehicles) { vehicles = it }
      BalanceField("Aset Bukan Semasa Lain", otherNonCurrent) { otherNonCurrent = it }
      BalanceField("Pelaburan", investments) { investments = it }
      BalanceField("Pinjaman & Overdraf", loans) { loans = it }
      BalanceField("Pemiutang Dagangan", tradeCreditors) { tradeCreditors = it }
      BalanceField("Pemiutang Lain", otherCreditors) { otherCreditors = it }
      BalanceField("Akaun Modal", capital) { capital = it }
      BalanceField("Untung Bawa Hadapan", broughtForward) { broughtForward = it }

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f).height(50.dp)) {
          Text(strings.cancel)
        }
        Button(
          onClick = {
            onSave(
              BeginningBalances(
                openingStock = num(openingStock),
                landBuilding = num(landBuilding),
                equipment = num(equipment),
                vehicles = num(vehicles),
                otherNonCurrent = num(otherNonCurrent),
                investments = num(investments),
                tradeDebtors = num(tradeDebtors),
                otherDebtors = num(otherDebtors),
                otherCurrent = num(otherCurrent),
                cash = num(cash),
                bank = num(bank),
                loans = num(loans),
                tradeCreditors = num(tradeCreditors),
                otherCreditors = num(otherCreditors),
                capital = num(capital),
                broughtForward = num(broughtForward)
              )
            )
          },
          modifier = Modifier.weight(1.5f).height(50.dp).testTag("save_beginning_balances_button")
        ) {
          Text(strings.save)
        }
      }
    }
  }
}

@Composable
private fun BalanceField(label: String, value: String, onValueChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    placeholder = { Text("0.00") },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    singleLine = true,
    modifier = Modifier.fillMaxWidth()
  )
}
