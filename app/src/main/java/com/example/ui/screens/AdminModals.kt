package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.firebase.BusinessAccessStatus
import com.example.localization.BukuKiraStrings
import com.example.model.BusinessLicence
import com.example.model.BusinessWorkspace
import com.example.model.LanguageCode
import com.example.model.OwnerPrivateInfo
import com.example.model.RegisteredDevice
import com.example.model.TeamRole
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessSettingsSheet(
  workspace: BusinessWorkspace,
  allWorkspaces: List<BusinessWorkspace>,
  ownerPrivateInfo: OwnerPrivateInfo?,
  currentUserRole: TeamRole,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onSelectWorkspace: (String) -> Unit,
  onOpenNewWorkspaceDialog: () -> Unit,
  onSaveSettings: (
    businessName: String,
    regNo: String,
    businessType: String,
    address: String,
    ownerName: String,
    currency: String,
    financialYear: String,
    ownerIdNumber: String,
    ownerPhone: String,
    ownerEmail: String
  ) -> Unit,
  onLoadSampleData: () -> Unit,
  onSignOut: () -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val isOwner = currentUserRole == TeamRole.OWNER

  var bizName by remember(workspace.id) { mutableStateOf(workspace.businessName) }
  var regNo by remember(workspace.id) { mutableStateOf(workspace.businessRegNo) }
  var bizType by remember(workspace.id) { mutableStateOf(workspace.businessType) }
  var bizAddress by remember(workspace.id) { mutableStateOf(workspace.businessAddress) }
  var ownerName by remember(workspace.id) { mutableStateOf(workspace.ownerName) }
  var currency by remember(workspace.id) { mutableStateOf(workspace.currency) }
  var year by remember(workspace.id) { mutableStateOf(workspace.financialYear) }

  var ownerIdNum by remember(ownerPrivateInfo) { mutableStateOf(ownerPrivateInfo?.ownerIdNumber.orEmpty()) }
  var ownerPhone by remember(ownerPrivateInfo) { mutableStateOf(ownerPrivateInfo?.ownerPhone.orEmpty()) }
  var ownerEmail by remember(ownerPrivateInfo) { mutableStateOf(ownerPrivateInfo?.ownerEmail.orEmpty()) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(strings.settingsTitle, style = MaterialTheme.typography.headlineSmall)

      // Workspace Switcher
      if (allWorkspaces.size > 1) {
        Text("Tukar Ruang Kerja", style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          allWorkspaces.forEach { ws ->
            FilterChip(
              selected = ws.id == workspace.id,
              onClick = { onSelectWorkspace(ws.id) },
              label = { Text("${ws.businessName} (${ws.id})") }
            )
          }
        }
      }

      OutlinedButton(
        onClick = onOpenNewWorkspaceDialog,
        modifier = Modifier.fillMaxWidth().testTag("open_add_workspace_button")
      ) {
        Text("+ Cipta / Sertai / Aktifkan Perniagaan Lain")
      }

      HorizontalDivider()

      OutlinedTextField(
        value = bizName,
        onValueChange = { bizName = it },
        enabled = isOwner,
        label = { Text("Nama Perniagaan") },
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = regNo,
        onValueChange = { regNo = it },
        enabled = isOwner,
        label = { Text("No. Pendaftaran (SSM)") },
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = bizType,
        onValueChange = { bizType = it },
        enabled = isOwner,
        label = { Text("Jenis Perniagaan") },
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = bizAddress,
        onValueChange = { bizAddress = it },
        enabled = isOwner,
        label = { Text("Alamat") },
        modifier = Modifier.fillMaxWidth()
      )
      OutlinedTextField(
        value = ownerName,
        onValueChange = { ownerName = it },
        enabled = isOwner,
        label = { Text("Nama Pemilik") },
        modifier = Modifier.fillMaxWidth()
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("RM", "SGD", "USD", "Rp").forEach { cur ->
          FilterChip(
            selected = currency == cur,
            onClick = { if (isOwner) currency = cur },
            enabled = isOwner,
            label = { Text(cur) }
          )
        }
      }
      OutlinedTextField(
        value = year,
        onValueChange = { if (it.length <= 4) year = it.filter { c -> c.isDigit() } },
        enabled = isOwner,
        label = { Text(strings.financialYear) },
        modifier = Modifier.fillMaxWidth()
      )

      if (isOwner) {
        HorizontalDivider()
        Text("Maklumat Peribadi Pemilik (Sulit — Pemilik Sahaja)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(
          value = ownerIdNum,
          onValueChange = { ownerIdNum = it },
          label = { Text("No. KP / Pengenalan Pemilik") },
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = ownerPhone,
          onValueChange = { ownerPhone = it },
          label = { Text("No. Telefon Pemilik") },
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = ownerEmail,
          onValueChange = { ownerEmail = it },
          label = { Text("E-mel Pemilik") },
          modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
          onClick = onLoadSampleData,
          modifier = Modifier.fillMaxWidth().testTag("settings_load_sample_button")
        ) {
          Text("Muatkan Rekod Contoh Kedai Kopi (2026)")
        }

        Button(
          onClick = {
            onSaveSettings(
              bizName, regNo, bizType, bizAddress, ownerName,
              currency, year, ownerIdNum, ownerPhone, ownerEmail
            )
          },
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_settings_button")
        ) {
          Text(strings.save)
        }
      }

      OutlinedButton(
        onClick = onSignOut,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("settings_sign_out_button")
      ) {
        Text(strings.signOut, color = CoralError)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManagementSheet(
  workspace: BusinessWorkspace,
  currentUid: String,
  generatedInviteCode: String?,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onGenerateInvite: (TeamRole) -> Unit,
  onSyncTeamAccess: () -> Unit,
  onUpdateRole: (String, TeamRole) -> Unit,
  onRemoveMember: (String) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val isOwner = workspace.ownerId == currentUid
  val clipboard = LocalClipboardManager.current
  var selectedInviteRole by remember { mutableStateOf(TeamRole.STAFF) }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(strings.teamTitle, style = MaterialTheme.typography.headlineSmall)
      Text(
        "Ahli pasukan (${workspace.memberUids.size} / ${workspace.maxMembers})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      workspace.memberUids.forEach { memberUid ->
        val role = workspace.memberRoles[memberUid] ?: TeamRole.STAFF
        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = if (memberUid == currentUid) "$memberUid (Anda)" else memberUid,
                style = MaterialTheme.typography.labelLarge
              )
              Text(
                text = role.wireValue,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
            if (isOwner && memberUid != workspace.ownerId) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                  onClick = {
                    val nextRole = if (role == TeamRole.STAFF) TeamRole.ACCOUNTANT else TeamRole.STAFF
                    onUpdateRole(memberUid, nextRole)
                  }
                ) {
                  Text(if (role == TeamRole.STAFF) "Jadikan Akauntan" else "Jadikan Staf")
                }
                IconButton(onClick = { onRemoveMember(memberUid) }) {
                  Icon(Icons.Default.DeleteOutline, contentDescription = "Remove member", tint = CoralError)
                }
              }
            }
          }
        }
      }

      if (isOwner) {
        HorizontalDivider()
        Text("Jana Kod Jemputan Sekali Guna (Sah 3 Hari)", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(
            selected = selectedInviteRole == TeamRole.STAFF,
            onClick = { selectedInviteRole = TeamRole.STAFF },
            label = { Text("STAFF") }
          )
          FilterChip(
            selected = selectedInviteRole == TeamRole.ACCOUNTANT,
            onClick = { selectedInviteRole = TeamRole.ACCOUNTANT },
            label = { Text("ACCOUNTANT") }
          )
        }
        Button(
          onClick = { onGenerateInvite(selectedInviteRole) },
          modifier = Modifier.fillMaxWidth().testTag("generate_invite_button")
        ) {
          Text("Jana Kod Jemputan")
        }

        if (!generatedInviteCode.isNullOrBlank()) {
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("Kod Jemputan Baharu:", style = MaterialTheme.typography.labelSmall)
                Text(
                  generatedInviteCode,
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.testTag("generated_invite_code_text")
                )
              }
              IconButton(onClick = { clipboard.setText(AnnotatedString(generatedInviteCode)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy invite code")
              }
            }
          }
        }

        OutlinedButton(
          onClick = onSyncTeamAccess,
          modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).testTag("sync_team_access_button")
        ) {
          Icon(Icons.Default.Sync, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Segerak Akses Rekod Kepada Semua Ahli")
        }
      } else {
        Spacer(Modifier.height(20.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenceAndDevicesSheet(
  workspace: BusinessWorkspace,
  licence: BusinessLicence?,
  devices: List<RegisteredDevice>,
  accessStatus: BusinessAccessStatus,
  currentInstallationId: String,
  currentUid: String,
  language: LanguageCode,
  onDismiss: () -> Unit,
  onReverifyDevice: () -> Unit,
  onRedeemMasterKey: (String) -> Unit,
  onRemoveDevice: (String) -> Unit
) {
  val strings = BukuKiraStrings.ui(language)
  val isOwner = workspace.ownerId == currentUid
  val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
  var masterKeyInput by remember(workspace.id) { mutableStateOf("") }

  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(strings.licenceTitle, style = MaterialTheme.typography.headlineSmall)
      }

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
      ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Business ID: ${workspace.id}", style = MaterialTheme.typography.bodySmall)
          if (licence != null) {
            Text("Licence Key: ${licence.licenceKey}", style = MaterialTheme.typography.titleSmall)
            Text(
              "Plan: ${licence.plan.wireValue} • Status: ${licence.status.wireValue}",
              color = if (accessStatus.isGranted) EmeraldPrimary else CoralError,
              style = MaterialTheme.typography.labelLarge
            )
            if (licence.expiryDateMillis > 0L) {
              Text("Sah sehingga: ${dateFmt.format(Date(licence.expiryDateMillis))}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Had Peranti: ${devices.size} / ${licence.maxDevices}", style = MaterialTheme.typography.bodySmall)
          }
          if (currentInstallationId.isNotBlank()) {
            Text(
              "ID Peranti Ini: ${currentInstallationId.take(12)}...",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      OutlinedButton(
        onClick = onReverifyDevice,
        modifier = Modifier.fillMaxWidth().testTag("reverify_device_button")
      ) {
        Icon(Icons.Default.Sync, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Sahkan Semula Peranti & Lesen")
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Tebus / Naik Taraf Kunci Lesen Master", style = MaterialTheme.typography.titleSmall)
          OutlinedTextField(
            value = masterKeyInput,
            onValueChange = { masterKeyInput = it },
            label = { Text("Kunci Lesen PRO / ENTERPRISE") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("redeem_master_key_input")
          )
          Button(
            onClick = {
              onRedeemMasterKey(masterKeyInput.trim())
              masterKeyInput = ""
            },
            enabled = masterKeyInput.trim().length >= 6,
            modifier = Modifier.fillMaxWidth().testTag("redeem_master_key_button")
          ) {
            Text("Aktifkan Kunci Master")
          }
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Peranti Berdaftar (${devices.size})", style = MaterialTheme.typography.titleMedium)
      }

      devices.forEach { dev ->
        val isCurrent = dev.installationId == currentInstallationId
        Card(modifier = Modifier.fillMaxWidth()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = dev.deviceName.ifBlank { dev.platform } + if (isCurrent) " (Peranti Ini)" else "",
                style = MaterialTheme.typography.titleSmall
              )
              Text("${dev.platform} • ${dev.installationId.take(12)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isOwner || dev.userId == currentUid) {
              IconButton(onClick = { onRemoveDevice(dev.installationId) }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Remove device", tint = CoralError)
              }
            }
          }
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
fun LanguagePickerDialog(
  current: LanguageCode,
  onDismiss: () -> Unit,
  onSelect: (LanguageCode) -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Bahasa / Language / 语言") },
    text = {
      Column {
        LanguageCode.entries.forEach { lang ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            RadioButton(
              selected = current == lang,
              onClick = {
                onSelect(lang)
                onDismiss()
              }
            )
            Spacer(Modifier.width(8.dp))
            Text(lang.displayName, style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("OK") }
    }
  )
}
