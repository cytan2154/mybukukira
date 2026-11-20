package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.firebase.BusinessAccessStatus
import com.example.firebase.FirebaseAuthManager
import com.example.firebase.LegacyLicenseErrorCode
import com.example.model.BusinessLicence
import com.example.model.BusinessWorkspace
import com.example.model.LicencePlan
import com.example.model.LicenceStatus
import com.example.model.RegisteredDevice
import com.example.model.TeamRole
import com.example.ui.screens.BusinessLicenseGateScreen
import com.example.ui.theme.BukuKiraTheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirebaseAuthManagerTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private val validInstallId = "0123456789abcdef0123456789abcdef"
  private val otherInstallId = "fedcba9876543210fedcba9876543210"
  private val now = 1_750_000_000_000L

  private fun sampleWorkspace(
    id: String = "biz_kopi_01",
    ownerId: String = "uid_owner",
    members: List<String> = listOf("uid_owner", "uid_staff")
  ) = BusinessWorkspace(
    id = id,
    ownerId = ownerId,
    memberUids = members,
    memberRoles = members.associateWith { if (it == ownerId) TeamRole.OWNER else TeamRole.STAFF },
    businessName = "Kedai Kopi Maju"
  )

  private fun sampleLicence(
    businessId: String = "biz_kopi_01",
    key: String = "BK-PRO-99887766",
    status: LicenceStatus = LicenceStatus.ACTIVE,
    startMs: Long = now - 86_400_000L,
    expiryMs: Long = now + 30L * 86_400_000L,
    maxDevices: Int = 2
  ) = BusinessLicence(
    businessId = businessId,
    ownerId = "uid_owner",
    memberUids = listOf("uid_owner", "uid_staff"),
    licenceKey = key,
    status = status,
    plan = LicencePlan.PRO,
    startDateMillis = startMs,
    expiryDateMillis = expiryMs,
    maxDevices = maxDevices
  )

  private fun sampleDevice(
    installId: String = validInstallId,
    uid: String = "uid_owner"
  ) = RegisteredDevice(
    installationId = installId,
    userId = uid,
    deviceName = "Pixel 9",
    platform = "ANDROID",
    lastValidatedAtMillis = now - 1000L
  )

  @Test
  fun `evaluateBusinessAccess blocks unauthenticated users even with active licence`() {
    val status = FirebaseAuthManager.evaluateBusinessAccess(
      uid = null,
      workspace = sampleWorkspace(),
      licence = sampleLicence(),
      registeredDevices = listOf(sampleDevice()),
      installationId = validInstallId,
      nowMillis = now
    )
    assertEquals(BusinessAccessStatus.Unauthenticated, status)
    assertFalse(status.isGranted)
  }

  @Test
  fun `evaluateBusinessAccess requires workspace when activeWorkspace is null`() {
    val status = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = null,
      licence = null,
      registeredDevices = emptyList(),
      installationId = validInstallId,
      nowMillis = now
    )
    assertEquals(BusinessAccessStatus.WorkspaceRequired("uid_owner"), status)
  }

  @Test
  fun `evaluateBusinessAccess denies non-members`() {
    val status = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_outsider",
      workspace = sampleWorkspace(),
      licence = sampleLicence(),
      registeredDevices = listOf(sampleDevice(uid = "uid_outsider")),
      installationId = validInstallId,
      nowMillis = now
    )
    assertTrue(status is BusinessAccessStatus.LicenseDenied)
    assertEquals(LegacyLicenseErrorCode.MEMBERSHIP_REQUIRED, (status as BusinessAccessStatus.LicenseDenied).code)
  }

  @Test
  fun `evaluateBusinessAccess enforces suspended, not-started, and expired licences`() {
    val suspended = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(status = LicenceStatus.SUSPENDED),
      registeredDevices = listOf(sampleDevice()),
      installationId = validInstallId,
      nowMillis = now
    )
    assertEquals(LegacyLicenseErrorCode.LICENCE_INACTIVE, (suspended as BusinessAccessStatus.LicenseDenied).code)

    val notStarted = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(startMs = now + 60_000L),
      registeredDevices = listOf(sampleDevice()),
      installationId = validInstallId,
      nowMillis = now
    )
    assertEquals(LegacyLicenseErrorCode.LICENCE_NOT_STARTED, (notStarted as BusinessAccessStatus.LicenseDenied).code)

    val expired = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(expiryMs = now - 1000L),
      registeredDevices = listOf(sampleDevice()),
      installationId = validInstallId,
      nowMillis = now
    )
    assertEquals(LegacyLicenseErrorCode.LICENCE_EXPIRED, (expired as BusinessAccessStatus.LicenseDenied).code)
  }

  @Test
  fun `evaluateBusinessAccess requires activation on unregistered device and denies when device limit full`() {
    val unregistered = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(maxDevices = 2),
      registeredDevices = listOf(sampleDevice(installId = otherInstallId, uid = "uid_staff")),
      installationId = validInstallId,
      nowMillis = now
    )
    assertTrue(unregistered is BusinessAccessStatus.LicenseRequired)
    assertEquals(
      LegacyLicenseErrorCode.DEVICE_NOT_REGISTERED,
      (unregistered as BusinessAccessStatus.LicenseRequired).code
    )

    val limitFull = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(maxDevices = 1),
      registeredDevices = listOf(sampleDevice(installId = otherInstallId, uid = "uid_staff")),
      installationId = validInstallId,
      nowMillis = now
    )
    assertTrue(limitFull is BusinessAccessStatus.LicenseDenied)
    assertEquals(
      LegacyLicenseErrorCode.MAX_DEVICES_REACHED,
      (limitFull as BusinessAccessStatus.LicenseDenied).code
    )
  }

  @Test
  fun `evaluateBusinessAccess grants access when auth member, active licence, and registered device align`() {
    val status = FirebaseAuthManager.evaluateBusinessAccess(
      uid = "uid_owner",
      workspace = sampleWorkspace(),
      licence = sampleLicence(),
      registeredDevices = listOf(sampleDevice()),
      installationId = validInstallId,
      nowMillis = now
    )
    assertTrue(status is BusinessAccessStatus.Granted)
    assertTrue(status.isGranted)
    assertEquals("biz_kopi_01", (status as BusinessAccessStatus.Granted).businessId)
  }

  @Test
  fun `validateLegacyLicenseRecord enforces constant-time key, hex id, window, and existing device allowance`() {
    val licence = sampleLicence(maxDevices = 1)

    val missing = FirebaseAuthManager.validateLegacyLicenseRecord(
      businessId = "biz_kopi_01",
      licenceKeyInput = " ",
      installationId = validInstallId,
      licence = licence,
      registeredDevices = emptyList(),
      nowMillis = now
    )
    assertFalse(missing.ok)
    assertEquals(LegacyLicenseErrorCode.LICENCE_REQUIRED, missing.code)

    val badInstall = FirebaseAuthManager.validateLegacyLicenseRecord(
      businessId = "biz_kopi_01",
      licenceKeyInput = "BK-PRO-99887766",
      installationId = "not-hex",
      licence = licence,
      registeredDevices = emptyList(),
      nowMillis = now
    )
    assertFalse(badInstall.ok)
    assertEquals(LegacyLicenseErrorCode.INSTALLATION_ID_REQUIRED, badInstall.code)

    val wrongKey = FirebaseAuthManager.validateLegacyLicenseRecord(
      businessId = "biz_kopi_01",
      licenceKeyInput = "BK-PRO-WRONGKEY",
      installationId = validInstallId,
      licence = licence,
      registeredDevices = emptyList(),
      nowMillis = now
    )
    assertFalse(wrongKey.ok)
    assertEquals(LegacyLicenseErrorCode.LICENCE_INVALID, wrongKey.code)

    // Already-registered device succeeds even when registeredDevices.size == maxDevices
    val existingDeviceOk = FirebaseAuthManager.validateLegacyLicenseRecord(
      businessId = "biz_kopi_01",
      licenceKeyInput = "BK-PRO-99887766",
      installationId = validInstallId,
      licence = licence,
      registeredDevices = listOf(sampleDevice(installId = validInstallId)),
      nowMillis = now
    )
    assertTrue(existingDeviceOk.ok)
    assertEquals(licence, existingDeviceOk.licence)
  }

  @Test
  fun `safeEquals matches exact keys only`() {
    assertTrue(FirebaseAuthManager.safeEquals("BK-PRO-123456", "BK-PRO-123456"))
    assertFalse(FirebaseAuthManager.safeEquals("BK-PRO-123456", "bk-pro-123456"))
    assertFalse(FirebaseAuthManager.safeEquals("BK-PRO-123456", "BK-PRO-12345"))
  }

  @Test
  fun `getOrCreateInstallationId persists 32-char hex ID per user`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = FirebaseAuthManager(context, "test-db-id")

    val id1 = manager.getOrCreateInstallationId("uid_alpha")
    val id1Repeat = manager.getOrCreateInstallationId("uid_alpha")
    val id2 = manager.getOrCreateInstallationId("uid_beta")

    assertTrue(Regex("^[a-f0-9]{32}$").matches(id1))
    assertEquals(id1, id1Repeat)
    assertTrue(Regex("^[a-f0-9]{32}$").matches(id2))
    assertNotEquals(id1, id2)
  }

  @Test
  fun `BusinessLicenseGateScreen displays denial code and submits licence key`() {
    var submittedBizId: String? = null
    var submittedKey: String? = null
    val ws = sampleWorkspace()

    composeTestRule.setContent {
      BukuKiraTheme {
        BusinessLicenseGateScreen(
          accessStatus = BusinessAccessStatus.LicenseRequired(
            uid = "uid_owner",
            businessId = ws.id,
            code = LegacyLicenseErrorCode.DEVICE_NOT_REGISTERED,
            reason = "Peranti ini belum diaktifkan."
          ),
          activeWorkspace = ws,
          allWorkspaces = listOf(ws),
          userEmail = "owner@kedai.my",
          installationId = validInstallId,
          isBusy = false,
          errorMessage = null,
          onVerifyLicence = { bizId, key ->
            submittedBizId = bizId
            submittedKey = key
          },
          onSelectWorkspace = {},
          onOpenOnboarding = {},
          onSignOut = {}
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithText("Kod Status: DEVICE_NOT_REGISTERED").assertIsDisplayed()
    composeTestRule.onNodeWithTag("gate_licence_key_input").performScrollTo().performTextInput("BK-PRO-99887766")
    composeTestRule.onNodeWithTag("gate_verify_licence_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    assertEquals("biz_kopi_01", submittedBizId)
    assertEquals("BK-PRO-99887766", submittedKey)
  }
}
