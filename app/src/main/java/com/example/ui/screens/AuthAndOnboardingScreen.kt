package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.LicencePlan
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary

@Composable
fun AuthScreen(
  isSigningIn: Boolean,
  errorMessage: String?,
  onGoogleSignInClick: () -> Unit
) {
  Scaffold { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .background(
          Brush.verticalGradient(
            listOf(
              MaterialTheme.colorScheme.background,
              MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            )
          )
        ),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .verticalScroll(rememberScrollState())
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_launcher_bukukira_1795150429708),
          contentDescription = "BukuKira Logo",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(24.dp))
        )
        Spacer(Modifier.height(16.dp))
        Text(
          text = "BukuKira",
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.primary
        )
        Text(
          text = "Perakaunan PKS Luar Talian & Segerak Awan",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Card(
          modifier = Modifier.fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
          Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            FeatureRow(
              icon = Icons.Default.CloudDone,
              title = "Segerak Firestore masa nyata & cache luar talian",
              subtitle = "Rekod tunai, bank, dan kredit kekal berfungsi walaupun tanpa internet."
            )
            FeatureRow(
              icon = Icons.Default.AccountBalanceWallet,
              title = "Untung Rugi, Kunci Kira-Kira & Lejar Am",
              subtitle = "Pengiraan perakaunan serta-merta terus pada peranti tanpa menunggu pelayan."
            )
            FeatureRow(
              icon = Icons.Default.Groups,
              title = "Pasukan berbilang peranan & Lesen Perniagaan",
              subtitle = "Sokongan Pemilik, Akauntan, dan Staf dengan eksport PDF, CSV, dan JSON."
            )
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Spacer(Modifier.height(16.dp))
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = errorMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(14.dp)
            )
          }
        }

        Spacer(Modifier.height(24.dp))

        Button(
          onClick = onGoogleSignInClick,
          enabled = !isSigningIn,
          modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("google_sign_in_button"),
          colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
        ) {
          if (isSigningIn) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text("Sedang log masuk...")
          } else {
            Icon(Icons.Default.Login, contentDescription = "Sign in with Google")
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Log Masuk dengan Google",
              style = MaterialTheme.typography.labelLarge
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top
  ) {
    Box(
      modifier = Modifier
        .size(40.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primaryContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(20.dp)
      )
    }
    Spacer(Modifier.width(12.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Spacer(Modifier.height(2.dp))
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
fun WorkspaceOnboardingScreen(
  defaultOwnerName: String,
  isBusy: Boolean,
  errorMessage: String?,
  onCreateBusiness: (
    businessName: String,
    regNo: String,
    businessType: String,
    address: String,
    ownerName: String,
    currency: String,
    year: String,
    plan: LicencePlan,
    customKey: String?,
    loadSample: Boolean
  ) -> Unit,
  onJoinViaInvite: (String) -> Unit,
  onActivateLicence: (String, String) -> Unit,
  onSignOut: () -> Unit
) {
  var selectedTab by remember { mutableIntStateOf(0) }

  var bizName by remember { mutableStateOf("Kedai Kopi & Roti Maju") }
  var regNo by remember { mutableStateOf("SSM-202603104829") }
  var bizType by remember { mutableStateOf("Makanan & Minuman") }
  var bizAddress by remember { mutableStateOf("No. 18, Pekan Changlun, Kedah") }
  var ownerName by remember(defaultOwnerName) { mutableStateOf(defaultOwnerName.ifBlank { "Pemilik Perniagaan" }) }
  var currency by remember { mutableStateOf("RM") }
  var year by remember { mutableStateOf("2026") }
  var customKey by remember { mutableStateOf("") }
  var loadSample by remember { mutableStateOf(true) }

  var inviteCode by remember { mutableStateOf("") }
  var existingBizId by remember { mutableStateOf("") }
  var existingKey by remember { mutableStateOf("") }

  Scaffold { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Sediakan Ruang Kerja", style = MaterialTheme.typography.headlineMedium)
            Text(
              "Cipta perniagaan baharu, sertai pasukan, atau aktifkan lesen sedia ada.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          TextButton(onClick = onSignOut, modifier = Modifier.testTag("onboarding_sign_out_button")) {
            Text("Log Keluar")
          }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          listOf("Cipta Baru", "Kod Jemputan", "Kunci Lesen").forEachIndexed { idx, label ->
            SegmentedButton(
              selected = selectedTab == idx,
              onClick = { selectedTab = idx },
              shape = SegmentedButtonDefaults.itemShape(index = idx, count = 3),
              modifier = Modifier.testTag("onboarding_tab_$idx")
            ) {
              Text(label, maxLines = 1)
            }
          }
        }

        if (!errorMessage.isNullOrBlank()) {
          Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Text(
              errorMessage,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(14.dp)
            )
          }
        }

        when (selectedTab) {
          0 -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.width(8.dp))
                  Text("Profil Perniagaan & Lesen", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                  value = bizName,
                  onValueChange = { bizName = it },
                  label = { Text("Nama Perniagaan") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("onboarding_biz_name")
                )
                OutlinedTextField(
                  value = regNo,
                  onValueChange = { regNo = it },
                  label = { Text("No. Pendaftaran (SSM)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                  value = ownerName,
                  onValueChange = { ownerName = it },
                  label = { Text("Nama Pemilik") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                  value = bizType,
                  onValueChange = { bizType = it },
                  label = { Text("Jenis Perniagaan") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                  value = bizAddress,
                  onValueChange = { bizAddress = it },
                  label = { Text("Alamat") },
                  modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  listOf("RM", "SGD", "USD", "Rp").forEach { cur ->
                    FilterChip(
                      selected = currency == cur,
                      onClick = { currency = cur },
                      label = { Text(cur) }
                    )
                  }
                }
                OutlinedTextField(
                  value = year,
                  onValueChange = { if (it.length <= 4) year = it.filter { ch -> ch.isDigit() } },
                  label = { Text("Tahun Kewangan (cth. 2026)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                  value = customKey,
                  onValueChange = { customKey = it },
                  label = { Text("Kunci Lesen Master (pilihan — kosongkan untuk Percubaan 30 hari)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth()
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(1f)) {
                    Text("Muatkan data contoh kedai kopi", style = MaterialTheme.typography.titleSmall)
                    Text(
                      "Masukkan 10 rekod contoh tahun 2026 supaya laporan boleh disemak terus.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                  Switch(checked = loadSample, onCheckedChange = { loadSample = it })
                }

                Button(
                  onClick = {
                    onCreateBusiness(
                      bizName, regNo, bizType, bizAddress, ownerName,
                      currency, year, LicencePlan.TRIAL, customKey.ifBlank { null }, loadSample
                    )
                  },
                  enabled = !isBusy && bizName.isNotBlank(),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_workspace_button"),
                  colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                ) {
                  if (isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                  else Text("Cipta Perniagaan & Mula")
                }
              }
            }
          }

          1 -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.width(8.dp))
                  Text("Sertai Pasukan Perniagaan", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                  "Masukkan kod jemputan sekali guna (BKINV_...) yang dijana oleh Pemilik perniagaan.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                  value = inviteCode,
                  onValueChange = { inviteCode = it },
                  label = { Text("Kod Jemputan") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("invite_code_input")
                )
                Button(
                  onClick = { onJoinViaInvite(inviteCode) },
                  enabled = !isBusy && inviteCode.trim().length >= 16,
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("redeem_invite_button")
                ) {
                  Text("Tebus Jemputan")
                }
              }
            }
          }

          2 -> {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
              Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.width(8.dp))
                  Text("Aktifkan Lesen pada Peranti Ini", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                  value = existingBizId,
                  onValueChange = { existingBizId = it },
                  label = { Text("ID Perniagaan (Business ID)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("activate_business_id_input")
                )
                OutlinedTextField(
                  value = existingKey,
                  onValueChange = { existingKey = it },
                  label = { Text("Kunci Lesen (Licence Key)") },
                  singleLine = true,
                  modifier = Modifier.fillMaxWidth().testTag("activate_licence_key_input")
                )
                Button(
                  onClick = { onActivateLicence(existingBizId, existingKey) },
                  enabled = !isBusy && existingBizId.isNotBlank() && existingKey.isNotBlank(),
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("activate_licence_button")
                ) {
                  Text("Sahkan & Aktifkan")
                }
              }
            }
          }
        }
      }
    }
  }
}
