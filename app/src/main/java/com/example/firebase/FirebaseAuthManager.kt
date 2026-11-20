package com.example.firebase

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.R
import com.example.model.BusinessLicence
import com.example.model.BusinessWorkspace
import com.example.model.LicencePlan
import com.example.model.LicenceStatus
import com.example.model.RegisteredDevice
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom

internal val Context.bukuKiraDeviceDataStore by preferencesDataStore(name = "bukukira_device_prefs")

sealed interface AuthOutcome {
  data class Success(val user: FirebaseUser) : AuthOutcome
  data object Cancelled : AuthOutcome
  data class Failure(val message: String) : AuthOutcome
}

enum class LegacyLicenseErrorCode {
  AUTH_REQUIRED,
  LICENCE_REQUIRED,
  INSTALLATION_ID_REQUIRED,
  LICENCE_INVALID,
  LICENCE_INACTIVE,
  LICENCE_NOT_STARTED,
  LICENCE_EXPIRED,
  MAX_DEVICES_INVALID,
  MAX_DEVICES_REACHED,
  DEVICE_NOT_REGISTERED,
  MEMBERSHIP_REQUIRED,
  PERMISSION_OR_NETWORK_ERROR
}

class LicenseValidationException(
  val code: LegacyLicenseErrorCode,
  override val message: String
) : Exception(message)

data class LegacyLicenseValidationResult(
  val ok: Boolean,
  val code: LegacyLicenseErrorCode? = null,
  val message: String? = null,
  val licence: BusinessLicence? = null
)

sealed interface BusinessAccessStatus {
  data object Unauthenticated : BusinessAccessStatus
  data class WorkspaceRequired(val uid: String) : BusinessAccessStatus
  data class LicenseRequired(
    val uid: String,
    val businessId: String,
    val code: LegacyLicenseErrorCode,
    val reason: String
  ) : BusinessAccessStatus

  data class LicenseDenied(
    val uid: String,
    val businessId: String,
    val code: LegacyLicenseErrorCode,
    val reason: String
  ) : BusinessAccessStatus

  data class Granted(
    val uid: String,
    val businessId: String,
    val licence: BusinessLicence,
    val installationId: String
  ) : BusinessAccessStatus

  val isGranted: Boolean
    get() = this is Granted
}

/**
 * Coordinates Google Sign-In via Credential Manager + Firebase Auth and enforces
 * combined Firebase Auth + legacy BukuKira license key & device registration gating.
 */
class FirebaseAuthManager(
  private val context: Context,
  private val firestoreDatabaseId: String,
  private val authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) {

  private val appContext: Context = context.applicationContext

  private val auth: FirebaseAuth
    get() = authProvider()

  private val db: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(firestoreDatabaseId).also { instance ->
      runCatching {
        instance.firestoreSettings = firestoreSettings {
          setLocalCacheSettings(persistentCacheSettings {})
        }
      }
    }
  }

  val currentUser: FirebaseUser?
    get() = auth.currentUser

  val currentUid: String?
    get() = auth.currentUser?.uid

  fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
    val firebaseAuth = auth
    val listener = FirebaseAuth.AuthStateListener { instance ->
      trySend(instance.currentUser)
    }
    trySend(firebaseAuth.currentUser)
    firebaseAuth.addAuthStateListener(listener)
    awaitClose { firebaseAuth.removeAuthStateListener(listener) }
  }

  /**
   * Silent startup check using GetGoogleIdOption (autoSelectEnabled = true).
   */
  suspend fun attemptAutoSignIn(): FirebaseUser? {
    auth.currentUser?.let { return it }

    val webClientId = runCatching { appContext.getString(R.string.default_web_client_id) }
      .getOrNull()
      ?.takeIf { it.isNotBlank() } ?: return null

    return try {
      val credentialManager = CredentialManager.create(appContext)
      val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(true)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(true)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(option)
        .build()

      val result = credentialManager.getCredential(appContext, request)
      val credential = result.credential
      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
        auth.signInWithCredential(firebaseCred).await().user
      } else {
        null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.d(TAG, "Silent auto sign-in unavailable: ${e.message}")
      null
    }
  }

  /**
   * Explicit interactive Google Sign-In via GetSignInWithGoogleOption.
   * Accepts an Activity/UI context when available for Credential Manager UI resolution.
   */
  suspend fun signInWithGoogle(activityContext: Context = appContext): AuthOutcome {
    val webClientId = runCatching { activityContext.getString(R.string.default_web_client_id) }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?: return AuthOutcome.Failure("Missing default_web_client_id configuration.")

    val credentialManager = CredentialManager.create(activityContext)
    val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
    val request = GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .build()

    return try {
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
        val user = auth.signInWithCredential(firebaseCred).await().user
        if (user != null) AuthOutcome.Success(user)
        else AuthOutcome.Failure("Firebase sign-in returned no user.")
      } else {
        AuthOutcome.Failure("Unexpected credential type.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In cancelled or credential unavailable for current signing configuration", e)
      AuthOutcome.Cancelled
    } catch (e: NoCredentialException) {
      AuthOutcome.Failure("No Google account credential available on this device.")
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Google sign-in failed", e)
      AuthOutcome.Failure(e.localizedMessage ?: "Google Sign-In failed.")
    }
  }

  /**
   * Safe sign-out clearing CredentialManager state, wiping local Firestore persistence cache
   * (protecting against revoked-member stale reads), and signing out of Firebase Auth.
   */
  suspend fun signOut(beforeSignOut: suspend () -> Unit = {}) {
    withContext(NonCancellable) {
      runCatching { beforeSignOut() }
      runCatching {
        CredentialManager.create(appContext).clearCredentialState(ClearCredentialStateRequest())
      }
      runCatching { auth.signOut() }
      runCatching {
        db.terminate().await()
        db.clearPersistence().await()
      }
    }
  }

  /**
   * Returns a persistent 32-character lowercase hexadecimal installation ID compatible with
   * both legacy BukuKira license tracking (`^[a-f0-9]{32}$`) and Firestore rules.
   */
  suspend fun getOrCreateInstallationId(uid: String? = currentUid): String {
    val scopedKey = if (!uid.isNullOrBlank()) {
      stringPreferencesKey("installation_hex_id_${sanitizePrefsSuffix(uid)}")
    } else {
      stringPreferencesKey("installation_hex_id")
    }
    val existing = appContext.bukuKiraDeviceDataStore.data.first()[scopedKey]
    if (!existing.isNullOrBlank() && INSTALLATION_ID_REGEX.matches(existing)) {
      return existing
    }
    val bytes = ByteArray(16)
    SecureRandom().nextBytes(bytes)
    val hex = bytes.joinToString("") { "%02x".format(it) }
    appContext.bukuKiraDeviceDataStore.edit { prefs -> prefs[scopedKey] = hex }
    return hex
  }

  /**
   * Validates Firebase Auth, legacy license key, active window, and device slots, then
   * registers the current device under `/licences/{businessId}/devices/{installationId}`
   * and binds `activeBusinessId` for the signed-in user.
   */
  suspend fun verifyAndActivateBusinessAccess(
    businessIdInput: String,
    licenceKeyInput: String,
    nowMillis: Long = System.currentTimeMillis()
  ): Result<BusinessAccessStatus.Granted> {
    val user = currentUser
      ?: return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.AUTH_REQUIRED,
          "Google Sign-In is required before validating a business licence."
        )
      )

    val businessId = businessIdInput.trim()
    val licenceKey = licenceKeyInput.trim()
    if (businessId.isEmpty() || licenceKey.isEmpty()) {
      return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.LICENCE_REQUIRED,
          "Business ID and Licence Key are required."
        )
      )
    }
    if (!BUSINESS_ID_REGEX.matches(businessId)) {
      return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.LICENCE_INVALID,
          "Invalid Business ID format."
        )
      )
    }

    val installId = getOrCreateInstallationId(user.uid)
    val path = "licences/$businessId"
    return try {
      val licenceSnap = db.collection("licences").document(businessId).get().await()
      val licence = snapToLicence(licenceSnap)
        ?: return Result.failure(
          LicenseValidationException(
            LegacyLicenseErrorCode.LICENCE_INVALID,
            "Business licence not found or your Google account does not belong to this workspace."
          )
        )

      if (licence.ownerId != user.uid && user.uid !in licence.memberUids) {
        return Result.failure(
          LicenseValidationException(
            LegacyLicenseErrorCode.MEMBERSHIP_REQUIRED,
            "Your Google account is not a member of this business workspace."
          )
        )
      }

      val devicesSnap = db.collection("licences").document(businessId)
        .collection("devices").get().await()
      val devices = devicesSnap.documents.mapNotNull { snapToDevice(it) }

      val validation = validateLegacyLicenseRecord(
        businessId = businessId,
        licenceKeyInput = licenceKey,
        installationId = installId,
        licence = licence,
        registeredDevices = devices,
        nowMillis = nowMillis
      )
      if (!validation.ok) {
        if (validation.code == LegacyLicenseErrorCode.LICENCE_INVALID) {
          // Key didn't match the existing workspace licence: attempt server-side Master Licence upgrade/redemption.
          val masterResult = activateMasterLicence(businessId, licenceKey, nowMillis)
          if (masterResult.isSuccess) return masterResult
          return Result.failure(
            masterResult.exceptionOrNull() ?: LicenseValidationException(
              LegacyLicenseErrorCode.LICENCE_INVALID,
              validation.message ?: "Licence validation failed."
            )
          )
        }
        return Result.failure(
          LicenseValidationException(
            validation.code ?: LegacyLicenseErrorCode.LICENCE_INVALID,
            validation.message ?: "Licence validation failed."
          )
        )
      }

      upsertDeviceDoc(businessId, installId, user.uid, devices.any { it.installationId == installId })

      db.collection("users").document(user.uid).update(
        mapOf(
          "activeBusinessId" to businessId,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()

      recordLocalValidation(user.uid, businessId, licence.licenceKey, nowMillis)

      Result.success(
        BusinessAccessStatus.Granted(
          uid = user.uid,
          businessId = businessId,
          licence = licence,
          installationId = installId
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      val code = if (e is LicenseValidationException) e.code else LegacyLicenseErrorCode.PERMISSION_OR_NETWORK_ERROR
      Result.failure(
        LicenseValidationException(
          code,
          e.localizedMessage ?: "Failed to validate business licence."
        )
      )
    }
  }

  /**
   * Calls the trusted `activateMasterLicence` HTTPS Callable Cloud Function to redeem a
   * developer-issued key from `masterLicences/{licenceKey}` and upgrade `licences/{businessId}`
   * from TRIAL to PRO / ENTERPRISE, then registers the current device.
   */
  suspend fun activateMasterLicence(
    businessIdInput: String,
    licenceKeyInput: String,
    nowMillis: Long = System.currentTimeMillis()
  ): Result<BusinessAccessStatus.Granted> {
    val user = currentUser
      ?: return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.AUTH_REQUIRED,
          "Google Sign-In is required before activating a master licence key."
        )
      )

    val businessId = businessIdInput.trim()
    val licenceKey = licenceKeyInput.trim()
    if (!BUSINESS_ID_REGEX.matches(businessId) || licenceKey.length < 6) {
      return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.LICENCE_REQUIRED,
          "Valid Business ID and Master Licence Key are required."
        )
      )
    }

    return try {
      FirebaseFunctions.getInstance()
        .getHttpsCallable("activateMasterLicence")
        .call(hashMapOf("businessId" to businessId, "licenceKey" to licenceKey))
        .await()

      val installId = getOrCreateInstallationId(user.uid)
      val updatedSnap = db.collection("licences").document(businessId).get().await()
      val updatedLicence = snapToLicence(updatedSnap)
        ?: return Result.failure(
          LicenseValidationException(
            LegacyLicenseErrorCode.LICENCE_INVALID,
            "Master licence activated but workspace licence could not be loaded."
          )
        )

      val devicesSnap = db.collection("licences").document(businessId)
        .collection("devices").get().await()
      val devices = devicesSnap.documents.mapNotNull { snapToDevice(it) }

      val validation = validateLegacyLicenseRecord(
        businessId = businessId,
        licenceKeyInput = updatedLicence.licenceKey,
        installationId = installId,
        licence = updatedLicence,
        registeredDevices = devices,
        nowMillis = nowMillis
      )
      if (!validation.ok) {
        return Result.failure(
          LicenseValidationException(
            validation.code ?: LegacyLicenseErrorCode.LICENCE_INVALID,
            validation.message ?: "Upgraded licence validation failed."
          )
        )
      }

      upsertDeviceDoc(businessId, installId, user.uid, devices.any { it.installationId == installId })
      db.collection("users").document(user.uid).update(
        mapOf(
          "activeBusinessId" to businessId,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      recordLocalValidation(user.uid, businessId, updatedLicence.licenceKey, nowMillis)

      Result.success(
        BusinessAccessStatus.Granted(
          uid = user.uid,
          businessId = businessId,
          licence = updatedLicence,
          installationId = installId
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: FirebaseFunctionsException) {
      val mappedCode = when (e.code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> LegacyLicenseErrorCode.AUTH_REQUIRED
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> LegacyLicenseErrorCode.MEMBERSHIP_REQUIRED
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> LegacyLicenseErrorCode.MAX_DEVICES_REACHED
        FirebaseFunctionsException.Code.FAILED_PRECONDITION -> LegacyLicenseErrorCode.LICENCE_INACTIVE
        FirebaseFunctionsException.Code.NOT_FOUND -> LegacyLicenseErrorCode.LICENCE_INVALID
        else -> LegacyLicenseErrorCode.PERMISSION_OR_NETWORK_ERROR
      }
      Result.failure(
        LicenseValidationException(
          mappedCode,
          e.message ?: "Master licence activation failed."
        )
      )
    } catch (e: Exception) {
      Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.PERMISSION_OR_NETWORK_ERROR,
          e.localizedMessage ?: "Master licence activation failed."
        )
      )
    }
  }

  /**
   * Registers or refreshes the current device after workspace creation or invite redemption,
   * reusing the exact legacy licence & device-limit rules.
   */
  suspend fun registerCurrentDevice(
    businessId: String,
    nowMillis: Long = System.currentTimeMillis()
  ): Result<Unit> {
    val user = currentUser
      ?: return Result.failure(
        LicenseValidationException(
          LegacyLicenseErrorCode.AUTH_REQUIRED,
          "Authentication required."
        )
      )
    val installId = getOrCreateInstallationId(user.uid)
    val path = "licences/$businessId/devices/$installId"
    return try {
      val licenceSnap = db.collection("licences").document(businessId).get().await()
      val licence = snapToLicence(licenceSnap)
        ?: return Result.failure(
          LicenseValidationException(
            LegacyLicenseErrorCode.LICENCE_INVALID,
            "Licence record not found."
          )
        )

      val devicesSnap = db.collection("licences").document(businessId)
        .collection("devices").get().await()
      val devices = devicesSnap.documents.mapNotNull { snapToDevice(it) }

      val validation = validateLegacyLicenseRecord(
        businessId = businessId,
        licenceKeyInput = licence.licenceKey,
        installationId = installId,
        licence = licence,
        registeredDevices = devices,
        nowMillis = nowMillis
      )
      if (!validation.ok) {
        return Result.failure(
          LicenseValidationException(
            validation.code ?: LegacyLicenseErrorCode.LICENCE_INVALID,
            validation.message ?: "Device registration rejected by licence rules."
          )
        )
      }

      upsertDeviceDoc(businessId, installId, user.uid, devices.any { it.installationId == installId })
      recordLocalValidation(user.uid, businessId, licence.licenceKey, nowMillis)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  private suspend fun upsertDeviceDoc(
    businessId: String,
    installId: String,
    uid: String,
    existsAlready: Boolean
  ) {
    val deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100).ifBlank { "Android Device" }
    val devRef = db.collection("licences").document(businessId)
      .collection("devices").document(installId)
    if (existsAlready) {
      devRef.update(
        mapOf(
          "userId" to uid,
          "deviceName" to deviceLabel,
          "platform" to "ANDROID",
          "lastValidatedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } else {
      devRef.set(
        mapOf(
          "installationId" to installId,
          "userId" to uid,
          "deviceName" to deviceLabel,
          "platform" to "ANDROID",
          "lastValidatedAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }
  }

  private suspend fun recordLocalValidation(
    uid: String,
    businessId: String,
    licenceKey: String,
    nowMillis: Long
  ) {
    val digestKey = stringPreferencesKey("verified_key_${sanitizePrefsSuffix(uid)}_${sanitizePrefsSuffix(businessId)}")
    val tsKey = longPreferencesKey("verified_at_${sanitizePrefsSuffix(uid)}_${sanitizePrefsSuffix(businessId)}")
    val digest = sha256Hex(licenceKey.trim())
    appContext.bukuKiraDeviceDataStore.edit { prefs ->
      prefs[digestKey] = digest
      prefs[tsKey] = nowMillis
    }
  }

  companion object {
    private const val TAG = "FirebaseAuthManager"
    private val INSTALLATION_ID_REGEX = Regex("^[a-f0-9]{32}$")
    private val BUSINESS_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    /**
     * Constant-time string comparison matching legacy Apps Script `bkMasterSafeEqual_`.
     */
    fun safeEquals(a: String, b: String): Boolean {
      val left = a.toByteArray(Charsets.UTF_8)
      val right = b.toByteArray(Charsets.UTF_8)
      return MessageDigest.isEqual(left, right)
    }

    /**
     * Validates a licence against all legacy BukuKira master validation rules:
     * non-empty inputs, 32-char hex installationId, constant-time key match,
     * ACTIVE status, startDate <= now <= expiryDate, maxDevices >= 1, and slot availability.
     */
    fun validateLegacyLicenseRecord(
      businessId: String,
      licenceKeyInput: String,
      installationId: String,
      licence: BusinessLicence?,
      registeredDevices: List<RegisteredDevice>,
      nowMillis: Long = System.currentTimeMillis()
    ): LegacyLicenseValidationResult {
      val cleanBizId = businessId.trim()
      val cleanKey = licenceKeyInput.trim()
      val cleanInstallId = installationId.trim()

      if (cleanBizId.isEmpty() || cleanKey.isEmpty()) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.LICENCE_REQUIRED,
          message = "Business ID and Licence Key are required."
        )
      }
      if (!INSTALLATION_ID_REGEX.matches(cleanInstallId)) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.INSTALLATION_ID_REQUIRED,
          message = "Valid 32-character hex installationId is required."
        )
      }
      if (licence == null || licence.businessId != cleanBizId || !safeEquals(licence.licenceKey.trim(), cleanKey)) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.LICENCE_INVALID,
          message = "Licence key does not match this Business ID."
        )
      }
      if (licence.status != LicenceStatus.ACTIVE) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.LICENCE_INACTIVE,
          message = "Licence status is ${licence.status.wireValue}."
        )
      }
      if (licence.startDateMillis > 0L && nowMillis < licence.startDateMillis) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.LICENCE_NOT_STARTED,
          message = "Licence validity window has not started yet."
        )
      }
      if (nowMillis > licence.expiryDateMillis) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.LICENCE_EXPIRED,
          message = "Licence has expired."
        )
      }
      if (licence.maxDevices < 1) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.MAX_DEVICES_INVALID,
          message = "Licence maxDevices configuration is invalid."
        )
      }

      val alreadyRegistered = registeredDevices.any { it.installationId == cleanInstallId }
      if (!alreadyRegistered && registeredDevices.size >= licence.maxDevices) {
        return LegacyLicenseValidationResult(
          ok = false,
          code = LegacyLicenseErrorCode.MAX_DEVICES_REACHED,
          message = "Maximum device limit (${licence.maxDevices}) reached for this licence."
        )
      }

      return LegacyLicenseValidationResult(ok = true, licence = licence)
    }

    /**
     * Reactive combined gate ensuring business access requires BOTH a valid Firebase Auth
     * session + workspace membership AND a valid active legacy license bound to this device.
     */
    fun evaluateBusinessAccess(
      uid: String?,
      workspace: BusinessWorkspace?,
      licence: BusinessLicence?,
      registeredDevices: List<RegisteredDevice>,
      installationId: String?,
      nowMillis: Long = System.currentTimeMillis()
    ): BusinessAccessStatus {
      if (uid.isNullOrBlank()) {
        return BusinessAccessStatus.Unauthenticated
      }
      if (workspace == null) {
        return BusinessAccessStatus.WorkspaceRequired(uid)
      }
      if (uid != workspace.ownerId && uid !in workspace.memberUids) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.MEMBERSHIP_REQUIRED,
          reason = "Signed-in Google account is not a member of ${workspace.businessName}."
        )
      }
      if (licence == null) {
        return BusinessAccessStatus.LicenseRequired(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.LICENCE_REQUIRED,
          reason = "No active licence record found for ${workspace.businessName}. Enter your Business ID & Licence Key to activate."
        )
      }
      if (licence.businessId != workspace.id) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.LICENCE_INVALID,
          reason = "Licence record does not match workspace ${workspace.id}."
        )
      }
      if (licence.status != LicenceStatus.ACTIVE) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.LICENCE_INACTIVE,
          reason = "Business licence is ${licence.status.wireValue}."
        )
      }
      if (licence.startDateMillis > 0L && nowMillis < licence.startDateMillis) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.LICENCE_NOT_STARTED,
          reason = "Business licence start date has not arrived yet."
        )
      }
      if (nowMillis > licence.expiryDateMillis) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.LICENCE_EXPIRED,
          reason = "Business licence has expired. Re-verify with a renewed licence key."
        )
      }
      if (licence.maxDevices < 1) {
        return BusinessAccessStatus.LicenseDenied(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.MAX_DEVICES_INVALID,
          reason = "Invalid device limit (${licence.maxDevices}) on business licence."
        )
      }
      val cleanInstallId = installationId?.trim().orEmpty()
      if (!INSTALLATION_ID_REGEX.matches(cleanInstallId)) {
        return BusinessAccessStatus.LicenseRequired(
          uid = uid,
          businessId = workspace.id,
          code = LegacyLicenseErrorCode.INSTALLATION_ID_REQUIRED,
          reason = "Device installation ID unavailable. Verify your licence key to register this device."
        )
      }
      val isRegisteredForUser = registeredDevices.any {
        it.installationId == cleanInstallId && (it.userId == uid || it.userId.isBlank())
      }
      if (!isRegisteredForUser) {
        return if (registeredDevices.size >= licence.maxDevices) {
          BusinessAccessStatus.LicenseDenied(
            uid = uid,
            businessId = workspace.id,
            code = LegacyLicenseErrorCode.MAX_DEVICES_REACHED,
            reason = "Maximum device limit (${licence.maxDevices}) reached. Remove an old device or upgrade your plan."
          )
        } else {
          BusinessAccessStatus.LicenseRequired(
            uid = uid,
            businessId = workspace.id,
            code = LegacyLicenseErrorCode.DEVICE_NOT_REGISTERED,
            reason = "This device is not yet activated for ${workspace.businessName}. Enter the Licence Key to unlock access."
          )
        }
      }

      return BusinessAccessStatus.Granted(
        uid = uid,
        businessId = workspace.id,
        licence = licence,
        installationId = cleanInstallId
      )
    }

    internal fun snapToLicence(doc: DocumentSnapshot): BusinessLicence? {
      if (!doc.exists()) return null
      val data = doc.data ?: return null
      val memberUids = (data["memberUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
      return BusinessLicence(
        businessId = doc.id,
        ownerId = doc.getString("ownerId") ?: "",
        memberUids = memberUids,
        licenceKey = doc.getString("licenceKey") ?: "",
        status = LicenceStatus.fromWire(doc.getString("status")),
        plan = LicencePlan.fromWire(doc.getString("plan")),
        startDateMillis = doc.getTimestamp("startDate")?.toDate()?.time ?: 0L,
        expiryDateMillis = doc.getTimestamp("expiryDate")?.toDate()?.time ?: 0L,
        maxDevices = (data["maxDevices"] as? Number)?.toInt() ?: 5
      )
    }

    internal fun snapToDevice(doc: DocumentSnapshot): RegisteredDevice? {
      if (!doc.exists()) return null
      val ts = doc.getTimestamp("lastValidatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      return RegisteredDevice(
        installationId = doc.getString("installationId") ?: doc.id,
        userId = doc.getString("userId") ?: "",
        deviceName = doc.getString("deviceName") ?: "",
        platform = doc.getString("platform") ?: "ANDROID",
        lastValidatedAtMillis = ts?.toDate()?.time ?: 0L
      )
    }

    private fun sanitizePrefsSuffix(raw: String): String =
      raw.replace(Regex("[^a-zA-Z0-9_]"), "_").take(64)

    private fun sha256Hex(value: String): String {
      val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
      return digest.joinToString("") { "%02x".format(it) }
    }
  }
}
