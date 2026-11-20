package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.firebase.BusinessAccessStatus
import com.example.firebase.LegacyLicenseErrorCode
import com.example.model.BusinessWorkspace
import com.example.ui.theme.CoralError
import com.example.ui.theme.EmeraldDark

@Composable
fun BusinessLicenseGateScreen(
  accessStatus: BusinessAccessStatus,
  activeWorkspace: BusinessWorkspace,
  allWorkspaces: List<BusinessWorkspace>,
  userEmail: String,
  installationId: String,
  isBusy: Boolean,
  errorMessage: String?,
  onVerifyLicence: (businessId: String, licenceKey: String) -> Unit,
  onSelectWorkspace: (businessId: String) -> Unit,
  onOpenOnboarding: () -> Unit,
  onSignOut: () -> Unit
) {
  val isDenied = accessStatus is BusinessAccessStatus.LicenseDenied
  val statusCode = when (accessStatus) {
    is BusinessAccessStatus.LicenseDenied -> accessStatus.code
    is BusinessAccessStatus.LicenseRequired -> accessStatus.code
    else -> LegacyLicenseErrorCode.LICENCE_REQUIRED
  }
  val reasonText = when (accessStatus) {
    is BusinessAccessStatus.LicenseDenied -> accessStatus.reason
    is BusinessAccessStatus.LicenseRequired -> accessStatus.reason
    else -> "Pengesahan lesen perniagaan diperlukan."
  }

  var businessIdInput by remember(activeWorkspace.id) { mutableStateOf(activeWorkspace.id) }
  var licenceKeyInput by remember(activeWorkspace.id) { mutableStateOf("") }

  Scaffold { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 560.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
              modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                  if (isDenied) MaterialTheme.colorScheme.errorContainer
                  else MaterialTheme.colorScheme.primaryContainer
                ),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = if (isDenied) Icons.Default.GppBad else Icons.Default.VerifiedUser,
                contentDescription = "Business Licence Verification",
                tint = if (isDenied) CoralError else MaterialTheme.colorScheme.primary
              )
            }
            Spacer(Modifier.width(12.dp))
            Column {
              Text(
                text = if (isDenied) "Akses Lesen Disekat" else "Pengesahan Lesen Diperlukan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = activeWorkspace.businessName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          TextButton(
            onClick = onSignOut,
            modifier = Modifier.testTag("gate_sign_out_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Log Keluar", modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Log Keluar")
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(
            containerColor = if (isDenied) {
              MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
            } else {
              MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            }
          )
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Text(
              text = "Kod Status: ${statusCode.name}",
              style = MaterialTheme.typography.labelLarge,
              color = if (isDenied) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.primary
            )
            Text(
              text = reasonText,
              style = MaterialTheme.typography.bodyMedium
            )
            if (userEmail.isNotBlank()) {
              Text(
                text = "Akaun Google: $userEmail",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            if (installationId.isNotBlank()) {
              Text(
                text = "ID Peranti: ${installationId.take(12)}...",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp)
            )
          }
        }

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(Modifier.width(8.dp))
              Text("Sahkan Kunci Lesen BukuKira", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
              value = businessIdInput,
              onValueChange = { businessIdInput = it },
              label = { Text("ID Perniagaan (Business ID)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("gate_business_id_input")
            )
            OutlinedTextField(
              value = licenceKeyInput,
              onValueChange = { licenceKeyInput = it },
              label = { Text("Kunci Lesen (Licence Key)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("gate_licence_key_input")
            )
            Button(
              onClick = { onVerifyLicence(businessIdInput, licenceKeyInput) },
              enabled = !isBusy && businessIdInput.isNotBlank() && licenceKeyInput.isNotBlank(),
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("gate_verify_licence_button"),
              colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
            ) {
              if (isBusy) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  color = Color.White,
                  strokeWidth = 2.dp
                )
              } else {
                Text("Sahkan & Buka Akses")
              }
            }
          }
        }

        if (allWorkspaces.size > 1) {
          Text("Tukar Ruang Kerja Berdaftar", style = MaterialTheme.typography.titleSmall)
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            allWorkspaces.forEach { ws ->
              FilterChip(
                selected = ws.id == activeWorkspace.id,
                onClick = { onSelectWorkspace(ws.id) },
                label = { Text("${ws.businessName} (${ws.id})") },
                modifier = Modifier.testTag("gate_switch_workspace_${ws.id}")
              )
            }
          }
        }

        OutlinedButton(
          onClick = onOpenOnboarding,
          modifier = Modifier.fillMaxWidth().testTag("gate_open_onboarding_button")
        ) {
          Icon(Icons.Default.AddBusiness, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(Modifier.width(8.dp))
          Text("Cipta / Sertai Perniagaan Lain")
        }
      }
    }
  }
}
