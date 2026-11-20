package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.accounting.AccountingEngine
import com.example.export.ExportManager
import com.example.firebase.BukuKiraRepository
import com.example.firebase.BusinessAccessStatus
import com.example.firebase.FirebaseAuthManager
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessLicence
import com.example.model.BusinessWorkspace
import com.example.model.FinancialStats
import com.example.model.LanguageCode
import com.example.model.LicencePlan
import com.example.model.OwnerPrivateInfo
import com.example.model.PaymentMethod
import com.example.model.RegisteredDevice
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecordsFilter {
  ALL,
  IN,
  OUT,
  CREDIT
}

sealed interface BukuKiraUiState {
  data object Loading : BukuKiraUiState
  data class Error(val message: String) : BukuKiraUiState
  data class Ready(
    val uid: String,
    val userProfile: UserProfile?,
    val language: LanguageCode,
    val workspaces: List<BusinessWorkspace>,
    val activeWorkspace: BusinessWorkspace?,
    val activeLicence: BusinessLicence?,
    val registeredDevices: List<RegisteredDevice>,
    val ownerPrivateInfo: OwnerPrivateInfo?,
    val currentUserRole: TeamRole,
    val accessStatus: BusinessAccessStatus,
    val currentInstallationId: String,
    val isLicenceOperational: Boolean,
    val transactions: List<BookkeepingTransaction>,
    val filteredTransactions: List<BookkeepingTransaction>,
    val selectedYear: String,
    val stats: FinancialStats?,
    val pendingSyncCount: Int,
    val searchQuery: String,
    val recordsFilter: RecordsFilter
  ) : BukuKiraUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
  private val appContext: Context,
  private val repository: BukuKiraRepository,
  private val authManager: FirebaseAuthManager,
  private val uid: String
) : ViewModel() {

  private val searchQueryFlow = MutableStateFlow("")
  private val recordsFilterFlow = MutableStateFlow(RecordsFilter.ALL)
  private val yearOverrideFlow = MutableStateFlow<String?>(null)

  private val _feedback = MutableStateFlow<Pair<String?, String?>>(null to null) // message to error
  val feedback: StateFlow<Pair<String?, String?>> = _feedback

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy

  private val _generatedInvite = MutableStateFlow<String?>(null)
  val generatedInvite: StateFlow<String?> = _generatedInvite

  init {
    viewModelScope.launch {
      val user = authManager.currentUser
      if (user != null && user.uid == uid) {
        repository.ensureUserProfile(uid, user.email.orEmpty(), user.displayName.orEmpty())
      }
    }
  }

  private val userProfileFlow = repository.observeUserProfile(uid)
    .catch { err ->
      unwrapFirestoreOrThrow(err)
      emit(null)
    }

  private val workspacesFlow = repository.observeMemberBusinesses(uid)
    .catch { err ->
      unwrapFirestoreOrThrow(err)
      _feedback.value = null to (err.localizedMessage ?: "Failed to load businesses.")
      emit(emptyList())
    }

  private val activeWorkspaceFlow = combine(userProfileFlow, workspacesFlow) { profile, list ->
    val activeId = profile?.activeBusinessId
    list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
  }.distinctUntilChanged()

  private val activeLicenceFlow = activeWorkspaceFlow.flatMapLatest { ws ->
    if (ws == null) flowOf(null)
    else repository.observeLicence(ws.id).catch { err ->
      unwrapFirestoreOrThrow(err)
      emit(null)
    }
  }

  private val registeredDevicesFlow = activeWorkspaceFlow.flatMapLatest { ws ->
    if (ws == null) flowOf(emptyList())
    else repository.observeRegisteredDevices(ws.id).catch { err ->
      unwrapFirestoreOrThrow(err)
      emit(emptyList())
    }
  }

  private val ownerPrivateInfoFlow = activeWorkspaceFlow.flatMapLatest { ws ->
    if (ws == null || ws.ownerId != uid) flowOf(null)
    else repository.observeOwnerPrivateInfo(ws.id).catch { err ->
      unwrapFirestoreOrThrow(err)
      emit(null)
    }
  }

  private val transactionsFlow = activeWorkspaceFlow.flatMapLatest { ws ->
    if (ws == null) flowOf(emptyList())
    else repository.observeTransactions(ws.id, uid).catch { err ->
      unwrapFirestoreOrThrow(err)
      emit(emptyList())
    }
  }

  private val installationIdFlow = flow {
    emit(authManager.getOrCreateInstallationId(uid))
  }

  private val workspaceBundleFlow = combine(
    userProfileFlow,
    workspacesFlow,
    activeWorkspaceFlow,
    activeLicenceFlow,
    registeredDevicesFlow
  ) { profile, workspaces, activeWs, licence, devices ->
    WorkspaceBundle(profile, workspaces, activeWs, licence, devices)
  }

  private val queryControlsFlow = combine(
    searchQueryFlow,
    recordsFilterFlow,
    yearOverrideFlow,
    installationIdFlow
  ) { query, filter, yearOverride, installId ->
    QueryControls(query, filter, yearOverride, installId)
  }

  val uiState: StateFlow<BukuKiraUiState> = combine(
    workspaceBundleFlow,
    ownerPrivateInfoFlow,
    transactionsFlow,
    queryControlsFlow
  ) { bundle, ownerPrivate, txs, controls ->
    val (query, filter, yearOverride, installId) = controls
    val ws = bundle.activeWorkspace
    val lang = bundle.profile?.preferredLanguage ?: LanguageCode.MS
    val selectedYear = yearOverride ?: ws?.financialYear ?: "2026"
    val role = ws?.memberRoles?.get(uid) ?: TeamRole.STAFF
    val accessStatus = FirebaseAuthManager.evaluateBusinessAccess(
      uid = uid,
      workspace = ws,
      licence = bundle.licence,
      registeredDevices = bundle.devices,
      installationId = installId
    )
    val isOp = accessStatus.isGranted

    val stats = ws?.let {
      AccountingEngine.computeStatements(
        transactions = txs,
        beginningBalances = it.beginningBalances,
        closingStockByYear = it.closingStockByYear,
        financialYear = selectedYear,
        lang = lang
      )
    }

    val filtered = txs.filter { t ->
      val matchesFilter = when (filter) {
        RecordsFilter.ALL -> true
        RecordsFilter.IN -> t.type == TransactionType.IN
        RecordsFilter.OUT -> t.type == TransactionType.OUT
        RecordsFilter.CREDIT -> t.type in listOf(
          TransactionType.CREDIT_SALE,
          TransactionType.CREDIT_PURCHASE,
          TransactionType.CREDIT_NOTE,
          TransactionType.DEBIT_NOTE
        )
      }
      val q = query.trim().lowercase()
      val matchesQuery = if (q.isEmpty()) true else {
        t.note.lowercase().contains(q) ||
          t.categoryId.lowercase().contains(q) ||
          (t.partyName?.lowercase()?.contains(q) == true) ||
          (t.invoiceNo?.lowercase()?.contains(q) == true) ||
          t.date.contains(q)
      }
      matchesFilter && matchesQuery
    }

    BukuKiraUiState.Ready(
      uid = uid,
      userProfile = bundle.profile,
      language = lang,
      workspaces = bundle.workspaces,
      activeWorkspace = ws,
      activeLicence = bundle.licence,
      registeredDevices = bundle.devices,
      ownerPrivateInfo = ownerPrivate,
      currentUserRole = role,
      accessStatus = accessStatus,
      currentInstallationId = installId,
      isLicenceOperational = isOp,
      transactions = txs,
      filteredTransactions = filtered,
      selectedYear = selectedYear,
      stats = stats,
      pendingSyncCount = txs.count { it.hasPendingWrites },
      searchQuery = query,
      recordsFilter = filter
    ) as BukuKiraUiState
  }.catch { err ->
    val fsErr = generateSequence(err) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (err is CancellationException && fsErr == null) throw err
    emit(BukuKiraUiState.Error(fsErr?.localizedMessage ?: err.localizedMessage ?: "Unexpected sync error."))
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5_000),
    initialValue = BukuKiraUiState.Loading
  )

  private fun unwrapFirestoreOrThrow(err: Throwable) {
    val fsErr = generateSequence(err) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (err is CancellationException && fsErr == null) throw err
  }

  fun clearFeedback() {
    _feedback.value = null to null
  }

  fun clearGeneratedInvite() {
    _generatedInvite.value = null
  }

  fun setSearchQuery(query: String) {
    searchQueryFlow.value = query
  }

  fun setRecordsFilter(filter: RecordsFilter) {
    recordsFilterFlow.value = filter
  }

  fun setSelectedYear(year: String) {
    if (Regex("^[0-9]{4}$").matches(year)) {
      yearOverrideFlow.value = year
    }
  }

  fun setLanguage(lang: LanguageCode) {
    viewModelScope.launch {
      repository.updatePreferredLanguage(uid, lang)
        .onFailure { _feedback.value = null to it.localizedMessage }
    }
  }

  fun selectWorkspace(businessId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.setActiveBusinessId(uid, businessId)
        .onSuccess {
          yearOverrideFlow.value = null
        }
        .onFailure { _feedback.value = null to it.localizedMessage }
      _isBusy.value = false
    }
  }

  fun createBusiness(
    businessName: String,
    businessRegNo: String,
    businessType: String,
    businessAddress: String,
    ownerName: String,
    currency: String,
    financialYear: String,
    plan: LicencePlan,
    customLicenceKey: String?,
    loadSampleData: Boolean,
    onSuccess: () -> Unit = {}
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.createBusinessWorkspace(
        businessName = businessName,
        businessRegNo = businessRegNo,
        businessType = businessType,
        businessAddress = businessAddress,
        ownerName = ownerName,
        currency = currency,
        financialYear = financialYear,
        plan = plan,
        customLicenceKey = customLicenceKey,
        loadSampleData = loadSampleData
      ).onSuccess {
        yearOverrideFlow.value = null
        _feedback.value = "Workspace ready!" to null
        onSuccess()
      }.onFailure {
        _feedback.value = null to (it.localizedMessage ?: "Failed to create workspace.")
      }
      _isBusy.value = false
    }
  }

  fun activateLicence(businessId: String, licenceKey: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      authManager.verifyAndActivateBusinessAccess(businessId, licenceKey)
        .onSuccess {
          yearOverrideFlow.value = null
          _feedback.value = "Licence validated & device activated." to null
          onSuccess()
        }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Activation failed.") }
      _isBusy.value = false
    }
  }

  fun activateMasterLicence(businessId: String, masterLicenceKey: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      authManager.activateMasterLicence(businessId, masterLicenceKey)
        .onSuccess {
          yearOverrideFlow.value = null
          _feedback.value = "Master licence key redeemed! Plan upgraded to ${it.licence.plan.wireValue}." to null
          onSuccess()
        }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Master licence activation failed.") }
      _isBusy.value = false
    }
  }

  fun reverifyCurrentDevice(businessId: String) {
    viewModelScope.launch {
      _isBusy.value = true
      authManager.registerCurrentDevice(businessId)
        .onSuccess { _feedback.value = "Device licence verified." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Verification failed.") }
      _isBusy.value = false
    }
  }

  fun redeemInviteCode(code: String, onSuccess: () -> Unit = {}) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.redeemInviteCode(code)
        .onSuccess {
          yearOverrideFlow.value = null
          _feedback.value = "Joined business workspace!" to null
          onSuccess()
        }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Invite redemption failed.") }
      _isBusy.value = false
    }
  }

  fun createTransaction(
    workspace: BusinessWorkspace,
    date: String,
    type: TransactionType,
    categoryId: String,
    method: PaymentMethod,
    amount: Double,
    note: String,
    partyName: String? = null,
    invoiceNo: String? = null,
    disposalAssetType: AssetDisposalType? = null,
    disposalAssetCost: Double? = null
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.createTransaction(
        workspace = workspace,
        date = date,
        type = type,
        categoryId = categoryId,
        method = method,
        amount = amount,
        note = note,
        partyName = partyName,
        invoiceNo = invoiceNo,
        disposalAssetType = disposalAssetType,
        disposalAssetCost = disposalAssetCost
      ).onSuccess {
        _feedback.value = "Record saved." to null
      }.onFailure {
        _feedback.value = null to (it.localizedMessage ?: "Failed to save record.")
      }
      _isBusy.value = false
    }
  }

  fun deleteTransaction(businessId: String, txId: String) {
    viewModelScope.launch {
      repository.deleteTransaction(businessId, txId)
        .onSuccess { _feedback.value = "Record deleted." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Delete denied.") }
    }
  }

  fun updateClosingStock(workspace: BusinessWorkspace, year: String, amount: Double) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateClosingStock(workspace.id, workspace.closingStockByYear, year, amount)
        .onSuccess { _feedback.value = "Closing stock updated for $year." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Failed to update closing stock.") }
      _isBusy.value = false
    }
  }

  fun updateBeginningBalances(workspace: BusinessWorkspace, balances: BeginningBalances) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.updateBeginningBalances(workspace.id, balances)
        .onSuccess { _feedback.value = "Beginning balances saved." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Failed to save balances.") }
      _isBusy.value = false
    }
  }

  fun updateBusinessSettings(
    workspace: BusinessWorkspace,
    businessName: String,
    businessRegNo: String,
    businessType: String,
    businessAddress: String,
    ownerName: String,
    currency: String,
    financialYear: String,
    ownerIdNumber: String,
    ownerPhone: String,
    ownerEmail: String
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res1 = repository.updateBusinessSettings(
        workspace.id, businessName, businessRegNo, businessType, businessAddress, ownerName, currency, financialYear
      )
      val res2 = if (workspace.ownerId == uid) {
        repository.upsertOwnerPrivateInfo(workspace.id, ownerIdNumber, ownerPhone, ownerEmail)
      } else Result.success(Unit)

      if (res1.isSuccess && res2.isSuccess) {
        yearOverrideFlow.value = null
        _feedback.value = "Business settings updated." to null
      } else {
        val err = res1.exceptionOrNull() ?: res2.exceptionOrNull()
        _feedback.value = null to (err?.localizedMessage ?: "Failed to update settings.")
      }
      _isBusy.value = false
    }
  }

  fun generateInvite(workspace: BusinessWorkspace, role: TeamRole) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.createTeamInvite(workspace.id, workspace.businessName, role)
        .onSuccess { code -> _generatedInvite.value = code }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Failed to create invite.") }
      _isBusy.value = false
    }
  }

  fun syncTeamAccess(workspace: BusinessWorkspace) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.syncMembersToTransactions(workspace)
        .onSuccess { _feedback.value = "Team transaction access synchronized." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Sync failed.") }
      _isBusy.value = false
    }
  }

  fun updateMemberRole(workspace: BusinessWorkspace, targetUid: String, newRole: TeamRole) {
    viewModelScope.launch {
      repository.updateMemberRole(workspace, targetUid, newRole)
        .onSuccess { _feedback.value = "Role updated." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Role update failed.") }
    }
  }

  fun removeMember(workspace: BusinessWorkspace, targetUid: String) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.removeTeamMember(workspace, targetUid)
        .onSuccess { _feedback.value = "Member removed & access revoked." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Remove failed.") }
      _isBusy.value = false
    }
  }

  fun removeDevice(businessId: String, installationId: String) {
    viewModelScope.launch {
      repository.removeRegisteredDevice(businessId, installationId)
        .onSuccess { _feedback.value = "Device removed." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Failed to remove device.") }
    }
  }

  fun loadSampleData(workspace: BusinessWorkspace) {
    viewModelScope.launch {
      _isBusy.value = true
      repository.loadSampleTransactions(workspace)
        .onSuccess { _feedback.value = "Sample business records loaded." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Failed to load sample records.") }
      _isBusy.value = false
    }
  }

  fun exportPdf(uri: Uri, workspace: BusinessWorkspace, stats: FinancialStats, lang: LanguageCode) {
    viewModelScope.launch {
      _isBusy.value = true
      ExportManager.writePdfToUri(appContext, uri, workspace, stats, lang)
        .onSuccess { _feedback.value = "PDF statements exported." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "PDF export failed.") }
      _isBusy.value = false
    }
  }

  fun exportCsv(uri: Uri, workspace: BusinessWorkspace, txs: List<BookkeepingTransaction>, stats: FinancialStats, lang: LanguageCode) {
    viewModelScope.launch {
      _isBusy.value = true
      runCatching {
        val csv = ExportManager.buildCsvReport(workspace, txs, stats, lang)
        appContext.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open CSV file.")
      }.onSuccess { _feedback.value = "CSV exported." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "CSV export failed.") }
      _isBusy.value = false
    }
  }

  fun exportJsonBackup(uri: Uri, workspace: BusinessWorkspace, txs: List<BookkeepingTransaction>) {
    viewModelScope.launch {
      _isBusy.value = true
      runCatching {
        val json = ExportManager.buildJsonBackup(workspace, txs)
        appContext.contentResolver.openOutputStream(uri)?.use { out ->
          out.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot open backup file.")
      }.onSuccess { _feedback.value = "JSON backup saved." to null }
        .onFailure { _feedback.value = null to (it.localizedMessage ?: "Backup failed.") }
      _isBusy.value = false
    }
  }

  fun restoreJsonBackup(uri: Uri, workspace: BusinessWorkspace) {
    viewModelScope.launch {
      _isBusy.value = true
      runCatching {
        val raw = appContext.contentResolver.openInputStream(uri)?.use { input ->
          input.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Cannot read backup file.")
        val payload = ExportManager.parseJsonBackup(raw, workspace.id, uid, workspace.memberUids)
        repository.restoreBackup(workspace, payload).getOrThrow()
      }.onSuccess { count ->
        _feedback.value = "Restored $count transactions." to null
      }.onFailure {
        _feedback.value = null to (it.localizedMessage ?: "Restore failed.")
      }
      _isBusy.value = false
    }
  }

  private data class WorkspaceBundle(
    val profile: UserProfile?,
    val workspaces: List<BusinessWorkspace>,
    val activeWorkspace: BusinessWorkspace?,
    val licence: BusinessLicence?,
    val devices: List<RegisteredDevice>
  )

  private data class QueryControls(
    val query: String,
    val filter: RecordsFilter,
    val yearOverride: String?,
    val installationId: String
  )
}

class BukuKiraViewModelFactory(
  private val appContext: Context,
  private val firestoreDatabaseId: String,
  private val uid: String,
  private val authManager: FirebaseAuthManager = FirebaseAuthManager(appContext.applicationContext, firestoreDatabaseId)
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return MainViewModel(
      appContext = appContext.applicationContext,
      repository = BukuKiraRepository(appContext.applicationContext, firestoreDatabaseId, authManager),
      authManager = authManager,
      uid = uid
    ) as T
  }
}
