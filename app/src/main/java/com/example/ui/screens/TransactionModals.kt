package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.PointOfSale
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.localization.BukuKiraStrings
import com.example.model.AccountingCatalog
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.CategorySpec
import com.example.model.LanguageCode
import com.example.model.PaymentMethod
import com.example.model.TransactionType
import com.example.ui.theme.CoralContainer
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class TxModalCopy(
  val recordInTitle: String,
  val recordOutTitle: String,
  val saveRecord: String,
  val amountLabel: String,
  val paymentMethodLabel: String,
  val cashLabel: String,
  val bankQrLabel: String,
  val dateLabel: String,
  val noteOptionalLabel: String,
  val notePlaceholderIn: String,
  val notePlaceholderOut: String,
  val selectRecordTypeLabel: String,
  val secOtherIncome: String,
  val secSpecialRefunds: String,
  val secCapitalLoans: String,
  val secCogs: String,
  val secReturnsTransfers: String,
  val secOperatingExpenses: String,
  val secAssetsDeposits: String,
  val secEquityLoansPayables: String
)

private fun txModalCopy(lang: LanguageCode): TxModalCopy = when (lang) {
  LanguageCode.EN -> TxModalCopy(
    recordInTitle = "Record Money In (+)",
    recordOutTitle = "Record Money Out (-)",
    saveRecord = "Save Record",
    amountLabel = "Amount",
    paymentMethodLabel = "Payment Method",
    cashLabel = "Cash",
    bankQrLabel = "Bank / QR",
    dateLabel = "Date",
    noteOptionalLabel = "Note (Optional)",
    notePlaceholderIn = "e.g., Sales today",
    notePlaceholderOut = "e.g., Bought stock",
    selectRecordTypeLabel = "Select Record Type",
    secOtherIncome = "OTHER INCOME",
    secSpecialRefunds = "SPECIAL, REFUNDS & RECEIVABLES",
    secCapitalLoans = "CAPITAL & LOANS",
    secCogs = "COST OF GOODS SOLD",
    secReturnsTransfers = "RETURNS & TRANSFERS",
    secOperatingExpenses = "OPERATING EXPENSES",
    secAssetsDeposits = "ASSETS & DEPOSITS",
    secEquityLoansPayables = "EQUITY, LOANS & PAYABLES"
  )
  LanguageCode.ZH -> TxModalCopy(
    recordInTitle = "记录收款 (+)",
    recordOutTitle = "记录付款 (-)",
    saveRecord = "保存记录",
    amountLabel = "金额",
    paymentMethodLabel = "付款方式",
    cashLabel = "现金",
    bankQrLabel = "银行 / QR",
    dateLabel = "日期",
    noteOptionalLabel = "备注（可选）",
    notePlaceholderIn = "例如：今日销售",
    notePlaceholderOut = "例如：采购库存",
    selectRecordTypeLabel = "选择记录类型",
    secOtherIncome = "其他收入",
    secSpecialRefunds = "特殊、退款与应收款",
    secCapitalLoans = "资本与贷款",
    secCogs = "销售成本（库存）",
    secReturnsTransfers = "退货与转账",
    secOperatingExpenses = "营业费用",
    secAssetsDeposits = "资产与押金",
    secEquityLoansPayables = "权益、贷款与应付款"
  )
  LanguageCode.MS -> TxModalCopy(
    recordInTitle = "Rekod Duit Masuk (+)",
    recordOutTitle = "Rekod Duit Keluar (-)",
    saveRecord = "Simpan Rekod",
    amountLabel = "Jumlah",
    paymentMethodLabel = "Kaedah Bayaran",
    cashLabel = "Tunai",
    bankQrLabel = "Bank / QR",
    dateLabel = "Tarikh",
    noteOptionalLabel = "Nota (Pilihan)",
    notePlaceholderIn = "cth: Jualan hari ini",
    notePlaceholderOut = "cth: Beli stok",
    selectRecordTypeLabel = "Pilih Jenis Rekod",
    secOtherIncome = "PENDAPATAN LAIN",
    secSpecialRefunds = "KHAS, PULANGAN & PENGHUTANG",
    secCapitalLoans = "MODAL & PINJAMAN",
    secCogs = "KOS JUALAN (STOK)",
    secReturnsTransfers = "PULANGAN & PINDAHAN",
    secOperatingExpenses = "PERBELANJAAN OPERASI",
    secAssetsDeposits = "ASET & DEPOSIT",
    secEquityLoansPayables = "EKUITI, PINJAMAN & PEMIUTANG"
  )
}

private data class CategoryGroup(
  val heading: String?,
  val items: List<CategorySpec>
)

private fun groupedCategories(txType: TransactionType, copy: TxModalCopy): List<CategoryGroup> {
  fun pick(ids: List<String>, source: List<CategorySpec>): List<CategorySpec> {
    val map = source.associateBy { it.id }
    return ids.mapNotNull { map[it] }
  }
  return if (txType == TransactionType.IN) {
    val src = AccountingCatalog.inCategories
    listOf(
      CategoryGroup(null, pick(listOf("sales_goods"), src)),
      CategoryGroup(
        copy.secOtherIncome,
        pick(
          listOf("other_income_dividend", "other_income_interest", "other_income_rent_royalty", "other_income_general"),
          src
        )
      ),
      CategoryGroup(
        copy.secSpecialRefunds,
        pick(
          listOf("asset_disposal", "purchase_return_refund", "transfer_bank_to_cash", "debtors_payment"),
          src
        )
      ),
      CategoryGroup(
        copy.secCapitalLoans,
        pick(listOf("owner_capital", "drawing_return", "loan_received"), src)
      )
    ).filter { it.items.isNotEmpty() }
  } else {
    val src = AccountingCatalog.outCategories
    listOf(
      CategoryGroup(copy.secCogs, pick(listOf("buy_stock"), src)),
      CategoryGroup(copy.secReturnsTransfers, pick(listOf("sales_return_refund", "transfer_cash_to_bank"), src)),
      CategoryGroup(
        copy.secOperatingExpenses,
        pick(
          listOf(
            "exp_loan_interest",
            "staff_wages",
            "rent_utilities",
            "exp_contract",
            "exp_commission",
            "exp_bad_debt",
            "transport_fuel",
            "exp_repair",
            "exp_promo",
            "exp_others"
          ),
          src
        )
      ),
      CategoryGroup(
        copy.secAssetsDeposits,
        pick(
          listOf(
            "buy_land_building",
            "buy_equipment",
            "buy_vehicle",
            "buy_other_noncurrent",
            "buy_investment",
            "advance_other_debtor",
            "prepayment_other_current"
          ),
          src
        )
      ),
      CategoryGroup(
        copy.secEquityLoansPayables,
        pick(listOf("owner_drawing", "loan_repay", "creditors_payment", "other_creditors_payment"), src)
      )
    ).filter { it.items.isNotEmpty() }
  }
}

private fun categoryIcon(catId: String): ImageVector = when {
  catId == "sales_goods" -> Icons.Rounded.PointOfSale
  catId == "other_income_dividend" || catId == "loan_received" || catId == "loan_repay" -> Icons.Rounded.MonetizationOn
  catId == "other_income_interest" || catId == "exp_loan_interest" || catId == "exp_commission" -> Icons.Rounded.Percent
  catId == "other_income_rent_royalty" || catId == "rent_utilities" || catId == "buy_land_building" -> Icons.Rounded.Apartment
  catId == "transfer_bank_to_cash" || catId == "transfer_cash_to_bank" -> Icons.Rounded.SwapHoriz
  catId == "purchase_return_refund" || catId == "sales_return_refund" || catId == "drawing_return" -> Icons.Rounded.Undo
  catId == "buy_stock" -> Icons.Rounded.Inventory2
  catId.startsWith("buy_") || catId == "asset_disposal" -> Icons.Rounded.Apartment
  catId == "owner_capital" || catId == "owner_drawing" || catId == "debtors_payment" ||
    catId == "creditors_payment" || catId == "other_creditors_payment" -> Icons.Rounded.AccountBalanceWallet
  else -> Icons.Rounded.ReceiptLong
}

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
  val copy = txModalCopy(language)
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

  val isOut = txType == TransactionType.OUT
  val accentColor = if (isOut) CoralError else EmeraldPrimary
  val accentSoft = if (isOut) CoralContainer else EmeraldContainer

  val isAssetDisposal = selectedCatId == "asset_disposal"
  val parsedAmount = amountText.toDoubleOrNull()
  val parsedDisposalCost = disposalCostText.toDoubleOrNull()
  val canSubmit = parsedAmount != null && parsedAmount >= 0.0 &&
    Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(dateText.trim()) &&
    (!isAssetDisposal || (parsedDisposalCost != null && parsedDisposalCost > 0.0))

  val groups = remember(txType, language) { groupedCategories(txType, copy) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth()) {
      // Frozen Top Header + Save Record Button
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Surface(
              shape = CircleShape,
              color = accentSoft,
              modifier = Modifier.size(34.dp)
            ) {
              Box(contentAlignment = Alignment.Center) {
                Icon(
                  imageVector = if (isOut) Icons.Rounded.Remove else Icons.Rounded.Add,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
            Text(
              text = if (isOut) copy.recordOutTitle else copy.recordInTitle,
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
          ) {
            IconButton(
              onClick = onDismiss,
              modifier = Modifier.size(48.dp)
            ) {
              Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = strings.cancel
              )
            }
          }
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
          shape = RoundedCornerShape(14.dp),
          colors = ButtonDefaults.buttonColors(containerColor = accentColor),
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag("save_transaction_button")
        ) {
          Text(
            text = copy.saveRecord,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
          )
        }
      }

      HorizontalDivider()

      // Scrollable Body
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
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

        // Hero Amount Input
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = copy.amountLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            placeholder = {
              Text(
                text = "0.00",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
              )
            },
            leadingIcon = {
              Text(
                text = currency,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            },
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedBorderColor = accentColor,
              unfocusedBorderColor = accentColor
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("tx_amount_input")
          )
        }

        // Payment Method 2-Card Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = copy.paymentMethodLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            PaymentMethodCard(
              selected = method == PaymentMethod.CASH,
              label = copy.cashLabel,
              icon = Icons.Rounded.Payments,
              accentColor = accentColor,
              accentSoft = accentSoft,
              onClick = { method = PaymentMethod.CASH },
              modifier = Modifier
                .weight(1f)
                .testTag("method_cash_chip")
            )
            PaymentMethodCard(
              selected = method == PaymentMethod.BANK,
              label = copy.bankQrLabel,
              icon = Icons.Rounded.AccountBalance,
              accentColor = accentColor,
              accentSoft = accentSoft,
              onClick = { method = PaymentMethod.BANK },
              modifier = Modifier
                .weight(1f)
                .testTag("method_bank_chip")
            )
          }
        }

        // 2-Column Date + Note (Optional)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = copy.dateLabel,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
              value = dateText,
              onValueChange = { dateText = it },
              singleLine = true,
              trailingIcon = {
                Icon(
                  imageVector = Icons.Rounded.CalendarToday,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
              },
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_date_input")
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = copy.noteOptionalLabel,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
              value = noteText,
              onValueChange = { noteText = it },
              placeholder = {
                Text(
                  text = if (isOut) copy.notePlaceholderOut else copy.notePlaceholderIn,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              },
              singleLine = true,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_note_input")
            )
          }
        }

        if (isAssetDisposal) {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "Maklumat Pelupusan Aset",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
              )
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
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("tx_disposal_cost_input")
              )
            }
          }
        }

        // Select Record Type (Classified Sections + Descriptions)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = copy.selectRecordTypeLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
          )

          groups.forEach { group ->
            if (group.heading != null) {
              Column(
                modifier = Modifier.padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                Text(
                  text = group.heading,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.ExtraBold,
                  color = accentColor
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              }
            }

            group.items.chunked(2).forEach { pair ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                pair.forEach { cat ->
                  CategoryCardItem(
                    selected = selectedCatId == cat.id,
                    title = AccountingCatalog.localizedLabel(cat.id, language),
                    description = AccountingCatalog.localizedSub(cat.id, language),
                    icon = categoryIcon(cat.id),
                    accentColor = accentColor,
                    accentSoft = accentSoft,
                    onClick = { selectedCatId = cat.id },
                    modifier = Modifier
                      .weight(1f)
                      .testTag("cat_chip_${cat.id}")
                  )
                }
                if (pair.size == 1) {
                  Spacer(modifier = Modifier.weight(1f))
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(24.dp))
      }
    }
  }
}

@Composable
private fun PaymentMethodCard(
  selected: Boolean,
  label: String,
  icon: ImageVector,
  accentColor: Color,
  accentSoft: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (selected) accentSoft.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier.heightIn(min = 50.dp)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun CategoryCardItem(
  selected: Boolean,
  title: String,
  description: String,
  icon: ImageVector,
  accentColor: Color,
  accentSoft: Color,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (selected) accentSoft.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface,
    border = BorderStroke(
      width = if (selected) 1.5.dp else 1.dp,
      color = if (selected) accentColor else MaterialTheme.colorScheme.outlineVariant
    ),
    modifier = modifier.heightIn(min = 68.dp)
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.Top,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(34.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          lineHeight = 17.sp,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          lineHeight = 15.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
