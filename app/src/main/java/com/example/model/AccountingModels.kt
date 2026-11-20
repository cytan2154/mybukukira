package com.example.model

enum class TransactionType(val wireValue: String) {
  IN("IN"),
  OUT("OUT"),
  CREDIT_SALE("CREDIT_SALE"),
  CREDIT_PURCHASE("CREDIT_PURCHASE"),
  CREDIT_NOTE("CREDIT_NOTE"),
  DEBIT_NOTE("DEBIT_NOTE");

  companion object {
    fun fromWire(value: String): TransactionType {
      val norm = value.trim().uppercase()
      return when (norm) {
        "IN", "DUIT MASUK", "MONEY IN" -> IN
        "OUT", "DUIT KELUAR", "MONEY OUT" -> OUT
        "CREDIT_SALE", "JUALAN KREDIT" -> CREDIT_SALE
        "CREDIT_PURCHASE", "BELIAN KREDIT" -> CREDIT_PURCHASE
        "CREDIT_NOTE" -> CREDIT_NOTE
        "DEBIT_NOTE" -> DEBIT_NOTE
        else -> IN
      }
    }
  }
}

enum class PaymentMethod(val wireValue: String) {
  CASH("CASH"),
  BANK("BANK"),
  CREDIT("CREDIT");

  companion object {
    fun fromWire(value: String): PaymentMethod {
      return when (value.trim().uppercase()) {
        "CASH", "TUNAI" -> CASH
        "BANK" -> BANK
        "CREDIT", "KREDIT" -> CREDIT
        else -> CASH
      }
    }
  }
}

enum class AssetDisposalType(val wireValue: String) {
  EQUIPMENT("equipment"),
  LAND_BUILDING("landBuilding"),
  VEHICLES("vehicles"),
  OTHER_NON_CURRENT("otherNonCurrent"),
  INVESTMENTS("investments");

  companion object {
    fun fromWire(value: String?): AssetDisposalType? {
      return when (value?.trim()) {
        "equipment", "equipments" -> EQUIPMENT
        "landBuilding" -> LAND_BUILDING
        "vehicles" -> VEHICLES
        "otherNonCurrent" -> OTHER_NON_CURRENT
        "investments" -> INVESTMENTS
        else -> null
      }
    }
  }
}

enum class StatementClassification {
  PNL_REVENUE,
  PNL_OTHER_INCOME,
  PNL_SALES_RETURN,
  PNL_COGS,
  PNL_PURCHASE_RETURN,
  PNL_EXPENSE,
  BS_TRANSFER,
  BS_ASSET_DISPOSAL,
  BS_TRADE_DEBTORS_COLLECT,
  BS_EQUITY_ADD,
  BS_EQUITY_DRAW,
  BS_EQUITY_DRAW_RETURN,
  BS_LIABILITY_ADD,
  BS_LIABILITY_PAY,
  BS_LAND_BUILDING_ADD,
  BS_EQUIPMENT_ADD,
  BS_VEHICLES_ADD,
  BS_OTHER_NONCURRENT_ADD,
  BS_INVESTMENT_ADD,
  BS_OTHER_DEBTORS_ADD,
  BS_OTHER_CURRENT_ADD,
  BS_TRADE_CREDITORS_PAY,
  BS_OTHER_CREDITORS_PAY
}

data class CategorySpec(
  val id: String,
  val labelMs: String,
  val subMs: String,
  val labelEn: String,
  val subEn: String,
  val labelZh: String,
  val subZh: String,
  val accountKeyMs: String,
  val classification: StatementClassification,
  val direction: TransactionType,
  val groupKey: String? = null
)

data class CreditPurchaseOption(
  val id: String,
  val labelMs: String,
  val effectMs: String,
  val labelEn: String,
  val effectEn: String,
  val labelZh: String,
  val effectZh: String
)

enum class TeamRole(val wireValue: String) {
  OWNER("OWNER"),
  ACCOUNTANT("ACCOUNTANT"),
  STAFF("STAFF");

  companion object {
    fun fromWire(value: String?): TeamRole {
      return when (value?.trim()?.uppercase()) {
        "OWNER" -> OWNER
        "ACCOUNTANT" -> ACCOUNTANT
        else -> STAFF
      }
    }
  }
}

enum class LicenceStatus(val wireValue: String) {
  ACTIVE("ACTIVE"),
  SUSPENDED("SUSPENDED"),
  EXPIRED("EXPIRED");

  companion object {
    fun fromWire(value: String?): LicenceStatus {
      return when (value?.trim()?.uppercase()) {
        "ACTIVE" -> ACTIVE
        "SUSPENDED" -> SUSPENDED
        else -> EXPIRED
      }
    }
  }
}

enum class LicencePlan(val wireValue: String) {
  TRIAL("TRIAL"),
  PRO("PRO"),
  ENTERPRISE("ENTERPRISE");

  companion object {
    fun fromWire(value: String?): LicencePlan {
      return when (value?.trim()?.uppercase()) {
        "PRO" -> PRO
        "ENTERPRISE" -> ENTERPRISE
        else -> TRIAL
      }
    }
  }
}

enum class LanguageCode(val code: String, val displayName: String) {
  MS("ms", "Bahasa Melayu"),
  EN("en", "English"),
  ZH("zh", "中文");

  companion object {
    fun fromCode(code: String?): LanguageCode {
      return when (code?.trim()?.lowercase()) {
        "en" -> EN
        "zh" -> ZH
        else -> MS
      }
    }
  }
}

data class BeginningBalances(
  val openingStock: Double = 0.0,
  val landBuilding: Double = 0.0,
  val equipment: Double = 0.0,
  val vehicles: Double = 0.0,
  val otherNonCurrent: Double = 0.0,
  val investments: Double = 0.0,
  val tradeDebtors: Double = 0.0,
  val otherDebtors: Double = 0.0,
  val otherCurrent: Double = 0.0,
  val cash: Double = 0.0,
  val bank: Double = 0.0,
  val loans: Double = 0.0,
  val tradeCreditors: Double = 0.0,
  val otherCreditors: Double = 0.0,
  val capital: Double = 0.0,
  val broughtForward: Double = 0.0
) {
  fun toFirestoreMap(): Map<String, Double> = mapOf(
    "openingStock" to openingStock,
    "landBuilding" to landBuilding,
    "equipment" to equipment,
    "vehicles" to vehicles,
    "otherNonCurrent" to otherNonCurrent,
    "investments" to investments,
    "tradeDebtors" to tradeDebtors,
    "otherDebtors" to otherDebtors,
    "otherCurrent" to otherCurrent,
    "cash" to cash,
    "bank" to bank,
    "loans" to loans,
    "tradeCreditors" to tradeCreditors,
    "otherCreditors" to otherCreditors,
    "capital" to capital,
    "broughtForward" to broughtForward
  )

  companion object {
    fun fromMap(map: Map<String, Any?>?): BeginningBalances {
      if (map == null) return BeginningBalances()
      fun d(key: String): Double = (map[key] as? Number)?.toDouble() ?: 0.0
      return BeginningBalances(
        openingStock = d("openingStock"),
        landBuilding = d("landBuilding"),
        equipment = d("equipment"),
        vehicles = d("vehicles"),
        otherNonCurrent = d("otherNonCurrent"),
        investments = d("investments"),
        tradeDebtors = d("tradeDebtors"),
        otherDebtors = d("otherDebtors"),
        otherCurrent = d("otherCurrent"),
        cash = d("cash"),
        bank = d("bank"),
        loans = d("loans"),
        tradeCreditors = d("tradeCreditors"),
        otherCreditors = d("otherCreditors"),
        capital = d("capital"),
        broughtForward = d("broughtForward")
      )
    }
  }
}

data class BookkeepingTransaction(
  val id: String = "",
  val businessId: String = "",
  val createdByUid: String = "",
  val memberUids: List<String> = emptyList(),
  val date: String = "",
  val type: TransactionType = TransactionType.IN,
  val categoryId: String = "sales_goods",
  val method: PaymentMethod = PaymentMethod.CASH,
  val amount: Double = 0.0,
  val note: String = "",
  val partyName: String? = null,
  val invoiceNo: String? = null,
  val disposalAssetType: AssetDisposalType? = null,
  val disposalAssetCost: Double? = null,
  val updatedAtMillis: Long = 0L,
  val hasPendingWrites: Boolean = false
)

data class BusinessWorkspace(
  val id: String = "",
  val ownerId: String = "",
  val memberUids: List<String> = emptyList(),
  val memberRoles: Map<String, TeamRole> = emptyMap(),
  val maxMembers: Int = 5,
  val businessName: String = "",
  val businessRegNo: String = "",
  val businessType: String = "",
  val businessAddress: String = "",
  val ownerName: String = "",
  val currency: String = "RM",
  val financialYear: String = "2026",
  val closingStockByYear: Map<String, Double> = emptyMap(),
  val beginningBalances: BeginningBalances = BeginningBalances(),
  val lastInviteCode: String? = null,
  val updatedAtMillis: Long = 0L
)

data class OwnerPrivateInfo(
  val ownerId: String = "",
  val ownerIdNumber: String = "",
  val ownerPhone: String = "",
  val ownerEmail: String = ""
)

data class BusinessLicence(
  val businessId: String = "",
  val ownerId: String = "",
  val memberUids: List<String> = emptyList(),
  val licenceKey: String = "",
  val status: LicenceStatus = LicenceStatus.ACTIVE,
  val plan: LicencePlan = LicencePlan.TRIAL,
  val startDateMillis: Long = 0L,
  val expiryDateMillis: Long = 0L,
  val maxDevices: Int = 5
) {
  fun isOperational(nowMillis: Long = System.currentTimeMillis()): Boolean {
    return status == LicenceStatus.ACTIVE && expiryDateMillis > nowMillis
  }
}

data class RegisteredDevice(
  val installationId: String = "",
  val userId: String = "",
  val deviceName: String = "",
  val platform: String = "ANDROID",
  val lastValidatedAtMillis: Long = 0L
)

data class TeamInvite(
  val inviteCode: String = "",
  val businessId: String = "",
  val businessName: String = "",
  val role: TeamRole = TeamRole.STAFF,
  val createdByUid: String = "",
  val expiresAtMillis: Long = 0L,
  val redeemedByUid: String? = null
)

data class UserProfile(
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val preferredLanguage: LanguageCode = LanguageCode.MS,
  val activeBusinessId: String? = null
)

data class LedgerEntry(
  val date: String,
  val description: String,
  val debit: Double,
  val credit: Double,
  val balance: Double = 0.0
)

enum class StatementType {
  PNL,
  BALANCE_SHEET,
  CHECK
}

data class StatementRow(
  val statementType: StatementType,
  val section: String,
  val label: String,
  val amount: Double,
  val note: String = "",
  val isTotal: Boolean = false
)

data class FinancialStats(
  val selectedYear: String,
  val cashBalance: Double,
  val bankBalance: Double,
  val totalMoneyNow: Double,
  val totalRevenue: Double,
  val revenueByCategory: Map<String, Double>,
  val totalOtherIncome: Double,
  val otherIncomeByCategory: Map<String, Double>,
  val totalPurchasesCogs: Double,
  val openingStock: Double,
  val closingStock: Double,
  val netCogs: Double,
  val grossProfit: Double,
  val totalOperatingExpenses: Double,
  val expensesByCategory: Map<String, Double>,
  val totalExpenses: Double,
  val netProfit: Double,
  val landBuilding: Double,
  val equipmentAsset: Double,
  val vehicles: Double,
  val otherNonCurrent: Double,
  val investments: Double,
  val tradeDebtors: Double,
  val otherDebtors: Double,
  val otherCurrent: Double,
  val loansLiability: Double,
  val tradeCreditors: Double,
  val otherCreditors: Double,
  val ownerCapital: Double,
  val ownerDrawings: Double,
  val broughtForward: Double,
  val carriedForward: Double,
  val nonCurrentAssets: Double,
  val currentAssets: Double,
  val totalAssets: Double,
  val totalLiabilities: Double,
  val totalEquity: Double,
  val totalLiabilitiesAndEquity: Double,
  val balanceDifference: Double,
  val isBalanced: Boolean,
  val ledgers: Map<String, List<LedgerEntry>>,
  val rows: List<StatementRow>
)
