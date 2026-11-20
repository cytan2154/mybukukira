package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.firebase.AuthOutcome
import com.example.firebase.BusinessAccessStatus
import com.example.firebase.FirebaseAuthManager
import com.example.localization.BukuKiraStrings
import com.example.model.TransactionType
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BeginningBalancesSheet
import com.example.ui.screens.BusinessLicenseGateScreen
import com.example.ui.screens.BusinessSettingsSheet
import com.example.ui.screens.ClosingStockDialog
import com.example.ui.screens.CreditEntrySheet
import com.example.ui.screens.CreditScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LanguagePickerDialog
import com.example.ui.screens.LicenceAndDevicesSheet
import com.example.ui.screens.RecordsScreen
import com.example.ui.screens.StatementsScreen
import com.example.ui.screens.TeamManagementSheet
import com.example.ui.screens.TransactionEntrySheet
import com.example.ui.screens.WorkspaceOnboardingScreen
import com.example.ui.theme.BukuKiraTheme
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.viewmodel.BukuKiraUiState
import com.example.ui.viewmodel.BukuKiraViewModelFactory
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val firestoreDatabaseId = getString(R.string.firestore_database_id)
    setContent {
      BukuKiraTheme {
        BukuKiraAppRoot(firestoreDatabaseId = firestoreDatabaseId)
      }
    }
  }
}

private enum class BukuKiraTab {
  HOME,
  RECORDS,
  CREDIT,
  STATEMENTS
}

@Composable
fun BukuKiraAppRoot(firestoreDatabaseId: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val authManager = remember(firestoreDatabaseId) {
    FirebaseAuthManager(context.applicationContext, firestoreDatabaseId)
  }
  val currentUser by authManager.observeAuthState()
    .collectAsStateWithLifecycle(initialValue = authManager.currentUser)
  var isSigningIn by remember { mutableStateOf(false) }
  var authError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(authManager) {
    if (authManager.currentUser == null) {
      authManager.attemptAutoSignIn()
    }
  }

  val signedInUser = currentUser
  if (signedInUser == null) {
    AuthScreen(
      isSigningIn = isSigningIn,
      errorMessage = authError,
      onGoogleSignInClick = {
        scope.launch {
          isSigningIn = true
          authError = null
          when (val outcome = authManager.signInWithGoogle(context)) {
            is AuthOutcome.Success -> Unit
            AuthOutcome.Cancelled -> Unit
            is AuthOutcome.Failure -> authError = outcome.message
          }
          isSigningIn = false
        }
      }
    )
  } else {
    key(signedInUser.uid) {
      val vm: MainViewModel = viewModel(
        key = signedInUser.uid,
        factory = BukuKiraViewModelFactory(
          appContext = context.applicationContext,
          firestoreDatabaseId = firestoreDatabaseId,
          uid = signedInUser.uid,
          authManager = authManager
        )
      )
      AuthenticatedWorkspaceApp(
        vm = vm,
        userDisplayName = signedInUser.displayName.orEmpty(),
        userEmail = signedInUser.email.orEmpty(),
        onSignOut = {
          scope.launch {
            authManager.signOut()
          }
        }
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspaceApp(
  vm: MainViewModel,
  userDisplayName: String,
  userEmail: String,
  onSignOut: () -> Unit
) {
  val state by vm.uiState.collectAsStateWithLifecycle()
  val feedback by vm.feedback.collectAsStateWithLifecycle()
  val isBusy by vm.isBusy.collectAsStateWithLifecycle()
  val generatedInvite by vm.generatedInvite.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(feedback) {
    val msg = feedback.first ?: feedback.second
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      vm.clearFeedback()
    }
  }

  when (val current = state) {
    is BukuKiraUiState.Loading -> {
      Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
    }

    is BukuKiraUiState.Error -> {
      Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
          Text(current.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(24.dp))
        }
      }
    }

    is BukuKiraUiState.Ready -> {
      var showOnboardingOverlay by remember { mutableStateOf(false) }

      if (current.activeWorkspace == null || showOnboardingOverlay) {
        if (showOnboardingOverlay && current.activeWorkspace != null) {
          BackHandler { showOnboardingOverlay = false }
        }
        WorkspaceOnboardingScreen(
          defaultOwnerName = userDisplayName,
          isBusy = isBusy,
          errorMessage = feedback.second,
          onCreateBusiness = { name, reg, type, addr, owner, cur, yr, sample ->
            vm.createBusiness(name, reg, type, addr, owner, cur, yr, sample) {
              showOnboardingOverlay = false
            }
          },
          onJoinViaInvite = { code ->
            vm.redeemInviteCode(code) {
              showOnboardingOverlay = false
            }
          },
          onActivateLicence = { bizId, key ->
            vm.activateLicence(bizId, key) {
              showOnboardingOverlay = false
            }
          },
          onSignOut = onSignOut
        )
        return
      }

      val ws = current.activeWorkspace

      if (!isBusy && current.accessStatus !is BusinessAccessStatus.Granted) {
        BusinessLicenseGateScreen(
          accessStatus = current.accessStatus,
          activeWorkspace = ws,
          allWorkspaces = current.workspaces,
          userEmail = userEmail,
          installationId = current.currentInstallationId,
          isBusy = isBusy,
          errorMessage = feedback.second,
          onVerifyLicence = { bizId, key -> vm.activateLicence(bizId, key) },
          onSelectWorkspace = { bizId -> vm.selectWorkspace(bizId) },
          onOpenOnboarding = { showOnboardingOverlay = true },
          onSignOut = onSignOut
        )
        return
      }
      val strings = BukuKiraStrings.ui(current.language)
      var currentTab by remember { mutableStateOf(BukuKiraTab.HOME) }
      if (currentTab != BukuKiraTab.HOME) {
        BackHandler { currentTab = BukuKiraTab.HOME }
      }

      var txModalInitialType by remember { mutableStateOf<TransactionType?>(null) }
      var creditModalInitialType by remember { mutableStateOf<TransactionType?>(null) }
      var showClosingStockDialog by remember { mutableStateOf(false) }
      var showBeginningBalancesSheet by remember { mutableStateOf(false) }
      var showSettingsSheet by remember { mutableStateOf(false) }
      var showTeamSheet by remember { mutableStateOf(false) }
      var showLicenceSheet by remember { mutableStateOf(false) }
      var showLanguageDialog by remember { mutableStateOf(false) }

      // SAF launchers (zero storage permission needed)
      val pdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
      ) { uri ->
        val stats = current.stats
        if (uri != null && stats != null) vm.exportPdf(uri, ws, stats, current.language, current.ownerPrivateInfo)
      }
      val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
      ) { uri ->
        val stats = current.stats
        if (uri != null && stats != null) vm.exportCsv(uri, ws, current.transactions, stats, current.language)
      }
      val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
      ) { uri ->
        if (uri != null) vm.exportJsonBackup(uri, ws, current.transactions)
      }
      val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
      ) { uri ->
        if (uri != null) vm.restoreJsonBackup(uri, ws)
      }

      val navItems = listOf(
        Triple(BukuKiraTab.HOME, strings.navHome, Icons.Default.Home),
        Triple(BukuKiraTab.RECORDS, strings.navRecords, Icons.AutoMirrored.Filled.ReceiptLong),
        Triple(BukuKiraTab.CREDIT, strings.navCredit, Icons.Default.CreditCard),
        Triple(BukuKiraTab.STATEMENTS, strings.navStatements, Icons.Default.AccountBalance)
      )

      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Scaffold(
          snackbarHost = { SnackbarHost(snackbarHostState) },
          topBar = {
            TopAppBar(
              title = {
                Column {
                  Text(
                    ws.businessName.ifBlank { "BukuKira" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                  Text(
                    "${ws.ownerName.ifBlank { ws.currency }} • ${current.selectedYear}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.88f)
                  )
                }
              },
              actions = {
                IconButton(
                  onClick = { showLanguageDialog = true },
                  modifier = Modifier.testTag("top_language_button")
                ) {
                  Icon(Icons.Default.Language, contentDescription = "Language", tint = Color.White)
                }
                IconButton(
                  onClick = { showTeamSheet = true },
                  modifier = Modifier.testTag("top_team_button")
                ) {
                  Icon(Icons.Default.Groups, contentDescription = strings.teamTitle, tint = Color.White)
                }
                IconButton(
                  onClick = { showLicenceSheet = true },
                  modifier = Modifier.testTag("top_licence_button")
                ) {
                  Icon(Icons.Default.Key, contentDescription = strings.licenceTitle, tint = Color.White)
                }
                IconButton(
                  onClick = { showSettingsSheet = true },
                  modifier = Modifier.testTag("top_settings_button")
                ) {
                  Icon(Icons.Default.Settings, contentDescription = strings.settingsTitle, tint = Color.White)
                }
              },
              colors = TopAppBarDefaults.topAppBarColors(
                containerColor = EmeraldPrimary,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
              )
            )
          },
          bottomBar = {
            if (!isWide) {
              NavigationBar {
                navItems.forEach { (tab, label, icon) ->
                  NavigationBarItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                  )
                }
              }
            }
          }
        ) { innerPadding ->
          Row(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            if (isWide) {
              NavigationRail(modifier = Modifier.fillMaxHeight()) {
                navItems.forEach { (tab, label, icon) ->
                  NavigationRailItem(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("rail_${tab.name.lowercase()}")
                  )
                }
              }
            }

            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.TopCenter
            ) {
              Box(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp)) {
                when (currentTab) {
                  BukuKiraTab.HOME -> HomeScreen(
                    workspace = ws,
                    stats = current.stats,
                    transactions = current.transactions,
                    selectedYear = current.selectedYear,
                    language = current.language,
                    pendingSyncCount = current.pendingSyncCount,
                    isLicenceOperational = current.isLicenceOperational,
                    currentUserRole = current.currentUserRole,
                    onOpenMoneyIn = { txModalInitialType = TransactionType.IN },
                    onOpenMoneyOut = { txModalInitialType = TransactionType.OUT },
                    onViewAllRecords = { currentTab = BukuKiraTab.RECORDS },
                    onLoadSampleData = { vm.loadSampleData(ws) }
                  )

                  BukuKiraTab.RECORDS -> RecordsScreen(
                    workspace = ws,
                    uid = current.uid,
                    currentUserRole = current.currentUserRole,
                    isLicenceOperational = current.isLicenceOperational,
                    language = current.language,
                    searchQuery = current.searchQuery,
                    selectedFilter = current.recordsFilter,
                    transactions = current.filteredTransactions,
                    onSearchQueryChange = vm::setSearchQuery,
                    onFilterChange = vm::setRecordsFilter,
                    onOpenMoneyIn = { txModalInitialType = TransactionType.IN },
                    onOpenMoneyOut = { txModalInitialType = TransactionType.OUT },
                    onDeleteTransaction = { txId -> vm.deleteTransaction(ws.id, txId) }
                  )

                  BukuKiraTab.CREDIT -> CreditScreen(
                    workspace = ws,
                    uid = current.uid,
                    stats = current.stats,
                    transactions = current.transactions,
                    selectedYear = current.selectedYear,
                    language = current.language,
                    currentUserRole = current.currentUserRole,
                    isLicenceOperational = current.isLicenceOperational,
                    onOpenCreditEntry = { type -> creditModalInitialType = type },
                    onDeleteTransaction = { txId -> vm.deleteTransaction(ws.id, txId) }
                  )

                  BukuKiraTab.STATEMENTS -> {
                    val years = remember(current.transactions, ws.financialYear) {
                      (current.transactions.mapNotNull { it.date.take(4).takeIf { y -> y.length == 4 } } +
                        listOf("2024", "2025", "2026", "2027", ws.financialYear))
                        .distinct()
                        .sorted()
                    }
                    StatementsScreen(
                      workspace = ws,
                      stats = current.stats,
                      selectedYear = current.selectedYear,
                      availableYears = years,
                      language = current.language,
                      currentUserRole = current.currentUserRole,
                      ownerPrivateInfo = current.ownerPrivateInfo,
                      onSelectYear = vm::setSelectedYear,
                      onOpenClosingStock = { showClosingStockDialog = true },
                      onOpenBeginningBalances = { showBeginningBalancesSheet = true },
                      onExportPdf = { pdfLauncher.launch("BukuKira_Penyata_${current.selectedYear}.pdf") },
                      onExportCsv = { csvLauncher.launch("BukuKira_Rekod_${current.selectedYear}.csv") },
                      onBackupJson = { backupLauncher.launch("BukuKira_Backup_${current.selectedYear}.json") },
                      onRestoreJson = { restoreLauncher.launch(arrayOf("application/json", "text/plain")) }
                    )
                  }
                }
              }
            }
          }
        }
      }

      txModalInitialType?.let { initialType ->
        TransactionEntrySheet(
          initialType = initialType,
          currency = ws.currency,
          language = current.language,
          onDismiss = { txModalInitialType = null },
          onSave = { date, type, catId, method, amount, note, dispType, dispCost ->
            vm.createTransaction(
              workspace = ws,
              date = date,
              type = type,
              categoryId = catId,
              method = method,
              amount = amount,
              note = note,
              disposalAssetType = dispType,
              disposalAssetCost = dispCost
            )
            txModalInitialType = null
          }
        )
      }

      creditModalInitialType?.let { initialType ->
        CreditEntrySheet(
          initialType = initialType,
          currency = ws.currency,
          language = current.language,
          onDismiss = { creditModalInitialType = null },
          onSave = { date, type, catId, amount, party, inv, note ->
            vm.createTransaction(
              workspace = ws,
              date = date,
              type = type,
              categoryId = catId,
              method = com.example.model.PaymentMethod.CREDIT,
              amount = amount,
              note = note,
              partyName = party,
              invoiceNo = inv
            )
            creditModalInitialType = null
          }
        )
      }

      if (showClosingStockDialog) {
        ClosingStockDialog(
          year = current.selectedYear,
          currentAmount = current.stats?.closingStock ?: 0.0,
          currency = ws.currency,
          language = current.language,
          onDismiss = { showClosingStockDialog = false },
          onSave = { amount ->
            vm.updateClosingStock(ws, current.selectedYear, amount)
            showClosingStockDialog = false
          }
        )
      }

      if (showBeginningBalancesSheet) {
        BeginningBalancesSheet(
          initial = ws.beginningBalances,
          currency = ws.currency,
          language = current.language,
          onDismiss = { showBeginningBalancesSheet = false },
          onSave = { bb ->
            vm.updateBeginningBalances(ws, bb)
            showBeginningBalancesSheet = false
          }
        )
      }

      if (showSettingsSheet) {
        BusinessSettingsSheet(
          workspace = ws,
          allWorkspaces = current.workspaces,
          ownerPrivateInfo = current.ownerPrivateInfo,
          currentUserRole = current.currentUserRole,
          language = current.language,
          onDismiss = { showSettingsSheet = false },
          onSelectWorkspace = { bizId ->
            vm.selectWorkspace(bizId)
            showSettingsSheet = false
          },
          onOpenNewWorkspaceDialog = {
            showSettingsSheet = false
            showOnboardingOverlay = true
          },
          onSaveSettings = { name, reg, type, addr, owner, cur, yr, idNum, phone, email ->
            vm.updateBusinessSettings(ws, name, reg, type, addr, owner, cur, yr, idNum, phone, email)
            showSettingsSheet = false
          },
          onLoadSampleData = {
            vm.loadSampleData(ws)
            showSettingsSheet = false
          },
          onSignOut = {
            showSettingsSheet = false
            onSignOut()
          }
        )
      }

      if (showTeamSheet) {
        TeamManagementSheet(
          workspace = ws,
          currentUid = current.uid,
          generatedInviteCode = generatedInvite,
          language = current.language,
          onDismiss = {
            vm.clearGeneratedInvite()
            showTeamSheet = false
          },
          onGenerateInvite = { role -> vm.generateInvite(ws, role) },
          onSyncTeamAccess = { vm.syncTeamAccess(ws) },
          onUpdateRole = { targetUid, role -> vm.updateMemberRole(ws, targetUid, role) },
          onRemoveMember = { targetUid -> vm.removeMember(ws, targetUid) }
        )
      }

      if (showLicenceSheet) {
        LicenceAndDevicesSheet(
          workspace = ws,
          licence = current.activeLicence,
          devices = current.registeredDevices,
          accessStatus = current.accessStatus,
          currentInstallationId = current.currentInstallationId,
          currentUid = current.uid,
          language = current.language,
          onDismiss = { showLicenceSheet = false },
          onReverifyDevice = { vm.reverifyCurrentDevice(ws.id) },
          onRemoveDevice = { installId -> vm.removeDevice(ws.id, installId) }
        )
      }

      if (showLanguageDialog) {
        LanguagePickerDialog(
          current = current.language,
          onDismiss = { showLanguageDialog = false },
          onSelect = vm::setLanguage
        )
      }
    }
  }
}
