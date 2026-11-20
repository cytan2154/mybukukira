package com.example.firebase

import android.content.Context
import com.example.export.BackupPayload
import com.example.model.AccountingCatalog
import com.example.model.AssetDisposalType
import com.example.model.BeginningBalances
import com.example.model.BookkeepingTransaction
import com.example.model.BusinessLicence
import com.example.model.BusinessWorkspace
import com.example.model.LanguageCode
import com.example.model.LicencePlan
import com.example.model.LicenceStatus
import com.example.model.OwnerPrivateInfo
import com.example.model.PaymentMethod
import com.example.model.RegisteredDevice
import com.example.model.TeamInvite
import com.example.model.TeamRole
import com.example.model.TransactionType
import com.example.model.UserProfile
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

class BukuKiraRepository(
  context: Context,
  private val firestoreDatabaseId: String,
  private val authManager: FirebaseAuthManager = FirebaseAuthManager(context.applicationContext, firestoreDatabaseId)
) {

  private val db: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(firestoreDatabaseId).also { instance ->
      runCatching {
        instance.firestoreSettings = firestoreSettings {
          setLocalCacheSettings(persistentCacheSettings {})
        }
      }
    }
  }

  fun currentUid(): String? = authManager.currentUid

  private fun requireUid(): String =
    currentUid() ?: throw IllegalStateException("Authentication required.")

  suspend fun getOrCreateInstallationId(): String =
    authManager.getOrCreateInstallationId(currentUid())

  // ---- Users ----

  fun observeUserProfile(uid: String): Flow<UserProfile?> {
    return db.collection("users").document(uid)
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snapToUserProfile(snap) }
  }

  suspend fun ensureUserProfile(uid: String, email: String, displayName: String): Result<Unit> {
    val path = "users/$uid"
    return try {
      val ref = db.collection("users").document(uid)
      val snap = ref.get().await()
      val safeEmail = email.take(200)
      val safeName = displayName.ifBlank { "Pemilik Perniagaan" }.take(120)
      if (!snap.exists()) {
        ref.set(
          mapOf(
            "userId" to uid,
            "email" to safeEmail,
            "displayName" to safeName,
            "preferredLanguage" to LanguageCode.MS.code,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } else {
        ref.update(
          mapOf(
            "email" to safeEmail,
            "displayName" to safeName,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updatePreferredLanguage(uid: String, language: LanguageCode): Result<Unit> {
    val path = "users/$uid"
    return try {
      db.collection("users").document(uid).update(
        mapOf(
          "preferredLanguage" to language.code,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun setActiveBusinessId(uid: String, businessId: String): Result<Unit> {
    val path = "users/$uid"
    return try {
      db.collection("users").document(uid).update(
        mapOf(
          "activeBusinessId" to businessId,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // ---- Workspaces & Licences ----

  fun observeMemberBusinesses(uid: String): Flow<List<BusinessWorkspace>> {
    return db.collection("businesses")
      .whereArrayContains("memberUids", uid)
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap ->
        snap.documents.mapNotNull { doc -> snapToWorkspace(doc) }
          .sortedBy { it.businessName.lowercase() }
      }
  }

  fun observeLicence(businessId: String): Flow<BusinessLicence?> {
    return db.collection("licences").document(businessId)
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snapToLicence(snap) }
  }

  fun observeRegisteredDevices(businessId: String): Flow<List<RegisteredDevice>> {
    return db.collection("licences").document(businessId).collection("devices")
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap ->
        snap.documents.mapNotNull { doc -> snapToDevice(doc) }
          .sortedByDescending { it.lastValidatedAtMillis }
      }
  }

  fun observeOwnerPrivateInfo(businessId: String): Flow<OwnerPrivateInfo?> {
    return db.collection("businesses").document(businessId)
      .collection("private").document("ownerInfo")
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snapToOwnerPrivateInfo(snap) }
  }

  suspend fun createBusinessWorkspace(
    businessName: String,
    businessRegNo: String,
    businessType: String,
    businessAddress: String,
    ownerName: String,
    currency: String,
    financialYear: String,
    plan: LicencePlan,
    customLicenceKey: String?,
    loadSampleData: Boolean
  ): Result<String> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    val businessId = "biz_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val trialKey = "BK-TRIAL-${UUID.randomUUID().toString().replace("-", "").take(8).uppercase()}"
    val masterKeyToRedeem = customLicenceKey?.trim()?.takeIf { it.length in 6..64 }

    val nowMs = System.currentTimeMillis()
    val startTs = Timestamp(Date(nowMs - 1000L))
    // Keep 60s safety margin below server request.time + 30d to prevent minor device clock skew rejection.
    val expiryTs = Timestamp(Date(nowMs + 30L * 86_400_000L - 60_000L))
    val stockMap = if (loadSampleData) mapOf("2026" to 650.0) else emptyMap()
    val safeYear = if (Regex("^[0-9]{4}$").matches(financialYear)) financialYear else "2026"
    val safeCurrency = if (currency in listOf("RM", "SGD", "USD", "Rp")) currency else "RM"

    val path = "businesses/$businessId"
    return try {
      val batch = db.batch()
      batch.set(
        db.collection("licences").document(businessId),
        mapOf(
          "ownerId" to uid,
          "memberUids" to listOf(uid),
          "licenceKey" to trialKey,
          "status" to LicenceStatus.ACTIVE.wireValue,
          "plan" to LicencePlan.TRIAL.wireValue,
          "startDate" to startTs,
          "expiryDate" to expiryTs,
          "maxDevices" to 5,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.set(
        db.collection("businesses").document(businessId),
        mapOf(
          "ownerId" to uid,
          "memberUids" to listOf(uid),
          "memberRoles" to mapOf(uid to TeamRole.OWNER.wireValue),
          "maxMembers" to 5,
          "businessName" to businessName.trim().ifBlank { "Perniagaan Saya" }.take(120),
          "businessRegNo" to businessRegNo.trim().take(60),
          "businessType" to businessType.trim().take(100),
          "businessAddress" to businessAddress.trim().take(250),
          "ownerName" to ownerName.trim().ifBlank { "Pemilik" }.take(120),
          "currency" to safeCurrency,
          "financialYear" to safeYear,
          "closingStockByYear" to stockMap,
          "beginningBalances" to BeginningBalances().toFirestoreMap(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.update(
        db.collection("users").document(uid),
        mapOf(
          "activeBusinessId" to businessId,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.commit().await()

      if (loadSampleData) {
        val sampleTxs = AccountingCatalog.sampleTransactions(businessId, uid, listOf(uid))
        val txBatch = db.batch()
        sampleTxs.forEach { t ->
          val ref = db.collection("businesses").document(businessId)
            .collection("transactions").document(t.id)
          txBatch.set(ref, txToCreateMap(t))
        }
        txBatch.commit().await()
      }

      if (masterKeyToRedeem != null) {
        val upgradeRes = authManager.activateMasterLicence(businessId, masterKeyToRedeem)
        if (upgradeRes.isFailure) {
          registerCurrentDevice(businessId)
          return Result.failure(
            IllegalStateException(
              "30-day Trial created, but master licence key activation failed: " +
                (upgradeRes.exceptionOrNull()?.localizedMessage ?: "Invalid key")
            )
          )
        }
      } else {
        registerCurrentDevice(businessId)
      }
      Result.success(businessId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun registerCurrentDevice(businessId: String): Result<Unit> =
    authManager.registerCurrentDevice(businessId)

  suspend fun removeRegisteredDevice(businessId: String, installationId: String): Result<Unit> {
    val path = "licences/$businessId/devices/$installationId"
    return try {
      db.collection("licences").document(businessId).collection("devices")
        .document(installationId).delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun activateLicenceForExistingBusiness(
    businessIdInput: String,
    licenceKeyInput: String
  ): Result<Unit> {
    return authManager.verifyAndActivateBusinessAccess(businessIdInput, licenceKeyInput)
      .map { }
  }

  suspend fun updateBusinessSettings(
    businessId: String,
    businessName: String,
    businessRegNo: String,
    businessType: String,
    businessAddress: String,
    ownerName: String,
    currency: String,
    financialYear: String
  ): Result<Unit> {
    val path = "businesses/$businessId"
    return try {
      val safeYear = if (Regex("^[0-9]{4}$").matches(financialYear)) financialYear else "2026"
      val safeCurrency = if (currency in listOf("RM", "SGD", "USD", "Rp")) currency else "RM"
      db.collection("businesses").document(businessId).update(
        mapOf(
          "businessName" to businessName.trim().ifBlank { "Perniagaan Saya" }.take(120),
          "businessRegNo" to businessRegNo.trim().take(60),
          "businessType" to businessType.trim().take(100),
          "businessAddress" to businessAddress.trim().take(250),
          "ownerName" to ownerName.trim().ifBlank { "Pemilik" }.take(120),
          "currency" to safeCurrency,
          "financialYear" to safeYear,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun upsertOwnerPrivateInfo(
    businessId: String,
    ownerIdNumber: String,
    ownerPhone: String,
    ownerEmail: String
  ): Result<Unit> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    val path = "businesses/$businessId/private/ownerInfo"
    return try {
      val ref = db.collection("businesses").document(businessId)
        .collection("private").document("ownerInfo")
      val snap = ref.get().await()
      val idNum = ownerIdNumber.trim().take(40)
      val phone = ownerPhone.trim().take(40)
      val email = ownerEmail.trim().take(120)
      if (!snap.exists()) {
        ref.set(
          mapOf(
            "ownerId" to uid,
            "ownerIdNumber" to idNum,
            "ownerPhone" to phone,
            "ownerEmail" to email,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } else {
        ref.update(
          mapOf(
            "ownerIdNumber" to idNum,
            "ownerPhone" to phone,
            "ownerEmail" to email,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updateClosingStock(
    businessId: String,
    currentMap: Map<String, Double>,
    year: String,
    amount: Double
  ): Result<Unit> {
    val path = "businesses/$businessId"
    return try {
      val updated = currentMap.toMutableMap()
      updated[year] = amount.coerceAtLeast(0.0)
      db.collection("businesses").document(businessId).update(
        mapOf(
          "closingStockByYear" to updated,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun updateBeginningBalances(
    businessId: String,
    balances: BeginningBalances
  ): Result<Unit> {
    val path = "businesses/$businessId"
    return try {
      db.collection("businesses").document(businessId).update(
        mapOf(
          "beginningBalances" to balances.toFirestoreMap(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // ---- Team Invites & Role Management ----

  suspend fun createTeamInvite(
    businessId: String,
    businessName: String,
    role: TeamRole
  ): Result<String> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    if (role == TeamRole.OWNER) {
      return Result.failure(IllegalArgumentException("Cannot invite another OWNER."))
    }
    val inviteCode = "BKINV_${UUID.randomUUID().toString().replace("-", "").take(16).uppercase()}"
    val path = "invites/$inviteCode"
    return try {
      val expiresAt = Timestamp(Date(System.currentTimeMillis() + 3L * 86_400_000L))
      db.collection("invites").document(inviteCode).set(
        mapOf(
          "businessId" to businessId,
          "businessName" to businessName.take(120),
          "role" to role.wireValue,
          "createdByUid" to uid,
          "expiresAt" to expiresAt,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(inviteCode)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun redeemInviteCode(rawInviteCode: String): Result<String> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    val code = rawInviteCode.trim()
    if (code.length < 16 || !Regex("^[a-zA-Z0-9_\\-]{16,128}$").matches(code)) {
      return Result.failure(IllegalArgumentException("Invalid invite code format."))
    }
    val path = "invites/$code"
    return try {
      val inviteSnap = db.collection("invites").document(code).get().await()
      if (!inviteSnap.exists()) {
        return Result.failure(IllegalStateException("Invite code not found."))
      }
      val invite = snapToInvite(inviteSnap)
        ?: return Result.failure(IllegalStateException("Invite record invalid."))
      if (invite.redeemedByUid != null) {
        return Result.failure(IllegalStateException("This invite code has already been used."))
      }
      if (invite.expiresAtMillis <= System.currentTimeMillis()) {
        return Result.failure(IllegalStateException("This invite code has expired."))
      }

      val batch = db.batch()
      batch.update(
        db.collection("invites").document(code),
        mapOf(
          "redeemedByUid" to uid,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.update(
        db.collection("businesses").document(invite.businessId),
        mapOf(
          "memberUids" to FieldValue.arrayUnion(uid),
          "memberRoles.$uid" to invite.role.wireValue,
          "lastInviteCode" to code,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.update(
        db.collection("licences").document(invite.businessId),
        mapOf(
          "memberUids" to FieldValue.arrayUnion(uid),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.commit().await()

      setActiveBusinessId(uid, invite.businessId)
      runCatching { registerCurrentDevice(invite.businessId) }
      Result.success(invite.businessId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun updateMemberRole(
    workspace: BusinessWorkspace,
    targetUid: String,
    newRole: TeamRole
  ): Result<Unit> {
    if (targetUid == workspace.ownerId || newRole == TeamRole.OWNER) {
      return Result.failure(IllegalArgumentException("Owner role cannot be reassigned here."))
    }
    val path = "businesses/${workspace.id}"
    return try {
      val newRoles = workspace.memberRoles.toMutableMap()
      newRoles[targetUid] = newRole
      db.collection("businesses").document(workspace.id).update(
        mapOf(
          "memberRoles" to newRoles.mapValues { it.value.wireValue },
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  suspend fun removeTeamMember(
    workspace: BusinessWorkspace,
    targetUid: String
  ): Result<Unit> {
    val ownerUid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    if (targetUid == workspace.ownerId) {
      return Result.failure(IllegalArgumentException("Cannot remove workspace owner."))
    }
    val path = "businesses/${workspace.id}"
    return try {
      val updatedUids = workspace.memberUids.filter { it != targetUid }
      val updatedRoles = workspace.memberRoles.filterKeys { it != targetUid }
        .mapValues { it.value.wireValue }

      val batch = db.batch()
      batch.update(
        db.collection("businesses").document(workspace.id),
        mapOf(
          "memberUids" to updatedUids,
          "memberRoles" to updatedRoles,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.update(
        db.collection("licences").document(workspace.id),
        mapOf(
          "memberUids" to updatedUids,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.commit().await()

      // Sync memberUids across all transactions so removed member loses list access too
      val txDocs = db.collection("businesses").document(workspace.id)
        .collection("transactions")
        .whereArrayContains("memberUids", ownerUid)
        .get()
        .await()
        .documents

      txDocs.chunked(400).forEach { chunk ->
        val txBatch = db.batch()
        chunk.forEach { doc ->
          txBatch.update(
            doc.reference,
            mapOf(
              "memberUids" to updatedUids,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
        txBatch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  /**
   * Owner syncs newly joined members into existing transactions so new members see historical records.
   */
  suspend fun syncMembersToTransactions(workspace: BusinessWorkspace): Result<Unit> {
    val ownerUid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    if (workspace.ownerId != ownerUid) return Result.success(Unit)
    val path = "businesses/${workspace.id}/transactions"
    return try {
      val txDocs = db.collection("businesses").document(workspace.id)
        .collection("transactions")
        .whereArrayContains("memberUids", ownerUid)
        .get()
        .await()
        .documents

      val needsUpdate = txDocs.filter { doc ->
        val existing = (doc.get("memberUids") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        existing != workspace.memberUids
      }
      needsUpdate.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { doc ->
          batch.update(
            doc.reference,
            mapOf(
              "memberUids" to workspace.memberUids,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
        batch.commit().await()
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  // ---- Transactions ----

  fun observeTransactions(businessId: String, uid: String): Flow<List<BookkeepingTransaction>> {
    return db.collection("businesses").document(businessId)
      .collection("transactions")
      .whereArrayContains("memberUids", uid)
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap ->
        snap.documents.mapNotNull { doc -> snapToTransaction(doc) }
          .sortedWith(compareByDescending<BookkeepingTransaction> { it.date }.thenByDescending { it.updatedAtMillis })
      }
  }

  suspend fun createTransaction(
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
  ): Result<String> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    if (amount < 0 || !amount.isFinite()) {
      return Result.failure(IllegalArgumentException("Amount must be non-negative."))
    }
    if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(date)) {
      return Result.failure(IllegalArgumentException("Date must be yyyy-MM-dd."))
    }
    val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val tx = BookkeepingTransaction(
      id = txId,
      businessId = workspace.id,
      createdByUid = uid,
      memberUids = workspace.memberUids,
      date = date,
      type = type,
      categoryId = categoryId.take(60),
      method = method,
      amount = amount,
      note = note.trim().take(500),
      partyName = partyName?.trim()?.takeIf { it.isNotEmpty() }?.take(120),
      invoiceNo = invoiceNo?.trim()?.takeIf { it.isNotEmpty() }?.take(60),
      disposalAssetType = disposalAssetType,
      disposalAssetCost = disposalAssetCost?.takeIf { it >= 0.0 }
    )
    val path = "businesses/${workspace.id}/transactions/$txId"
    return try {
      db.collection("businesses").document(workspace.id)
        .collection("transactions").document(txId)
        .set(txToCreateMap(tx))
        .await()
      Result.success(txId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  suspend fun deleteTransaction(businessId: String, txId: String): Result<Unit> {
    val path = "businesses/$businessId/transactions/$txId"
    return try {
      db.collection("businesses").document(businessId)
        .collection("transactions").document(txId)
        .delete()
        .await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  suspend fun loadSampleTransactions(workspace: BusinessWorkspace): Result<Unit> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    val path = "businesses/${workspace.id}/transactions"
    return try {
      val sample = AccountingCatalog.sampleTransactions(workspace.id, uid, workspace.memberUids)
      val batch = db.batch()
      sample.forEach { tx ->
        val ref = db.collection("businesses").document(workspace.id)
          .collection("transactions").document(tx.id)
        batch.set(ref, txToCreateMap(tx))
      }
      batch.update(
        db.collection("businesses").document(workspace.id),
        mapOf(
          "closingStockByYear" to (workspace.closingStockByYear + ("2026" to 650.0)),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  suspend fun restoreBackup(workspace: BusinessWorkspace, payload: BackupPayload): Result<Int> {
    val uid = runCatching { requireUid() }.getOrElse { return Result.failure(it) }
    val path = "businesses/${workspace.id}"
    return try {
      if (workspace.ownerId == uid) {
        val updates = mutableMapOf<String, Any>(
          "updatedAt" to FieldValue.serverTimestamp()
        )
        payload.businessName?.let { updates["businessName"] = it.take(120) }
        payload.businessRegNo?.let { updates["businessRegNo"] = it.take(60) }
        payload.businessType?.let { updates["businessType"] = it.take(100) }
        payload.businessAddress?.let { updates["businessAddress"] = it.take(250) }
        payload.ownerName?.let { updates["ownerName"] = it.take(120) }
        payload.currency?.let { updates["currency"] = it }
        payload.financialYear?.let { updates["financialYear"] = it }
        if (payload.closingStockByYear.isNotEmpty()) updates["closingStockByYear"] = payload.closingStockByYear
        payload.beginningBalances?.let { updates["beginningBalances"] = it.toFirestoreMap() }
        db.collection("businesses").document(workspace.id).update(updates).await()
      }

      payload.transactions.chunked(400).forEach { chunk ->
        val batch = db.batch()
        chunk.forEach { tx ->
          val ref = db.collection("businesses").document(workspace.id)
            .collection("transactions").document(tx.id)
          batch.set(ref, txToCreateMap(tx.copy(createdByUid = uid, memberUids = workspace.memberUids)))
        }
        batch.commit().await()
      }
      Result.success(payload.transactions.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  // ---- Serialization & Deserialization ----

  private fun txToCreateMap(t: BookkeepingTransaction): Map<String, Any> {
    val raw = linkedMapOf<String, Any?>(
      "businessId" to t.businessId,
      "createdByUid" to t.createdByUid,
      "memberUids" to t.memberUids,
      "date" to t.date,
      "type" to t.type.wireValue,
      "categoryId" to t.categoryId,
      "method" to t.method.wireValue,
      "amount" to t.amount,
      "note" to t.note,
      "partyName" to t.partyName,
      "invoiceNo" to t.invoiceNo,
      "disposalAssetType" to t.disposalAssetType?.wireValue,
      "disposalAssetCost" to t.disposalAssetCost,
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp()
    )
    @Suppress("UNCHECKED_CAST")
    return raw.filterValues { it != null } as Map<String, Any>
  }

  private fun snapToUserProfile(doc: DocumentSnapshot): UserProfile? {
    if (!doc.exists()) return null
    return UserProfile(
      userId = doc.getString("userId") ?: doc.id,
      email = doc.getString("email") ?: "",
      displayName = doc.getString("displayName") ?: "",
      preferredLanguage = LanguageCode.fromCode(doc.getString("preferredLanguage")),
      activeBusinessId = doc.getString("activeBusinessId")
    )
  }

  private fun snapToWorkspace(doc: DocumentSnapshot): BusinessWorkspace? {
    if (!doc.exists()) return null
    val data = doc.data ?: return null
    val memberUids = (data["memberUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val rawRoles = data["memberRoles"] as? Map<*, *>
    val roles = linkedMapOf<String, TeamRole>()
    rawRoles?.forEach { (k, v) ->
      if (k is String && v is String) roles[k] = TeamRole.fromWire(v)
    }
    val rawStock = data["closingStockByYear"] as? Map<*, *>
    val stockMap = linkedMapOf<String, Double>()
    rawStock?.forEach { (k, v) ->
      if (k is String && v is Number) stockMap[k] = v.toDouble()
    }
    @Suppress("UNCHECKED_CAST")
    val rawBb = data["beginningBalances"] as? Map<String, Any?>
    val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    return BusinessWorkspace(
      id = doc.id,
      ownerId = doc.getString("ownerId") ?: "",
      memberUids = memberUids,
      memberRoles = roles,
      maxMembers = (data["maxMembers"] as? Number)?.toInt() ?: 5,
      businessName = doc.getString("businessName") ?: "",
      businessRegNo = doc.getString("businessRegNo") ?: "",
      businessType = doc.getString("businessType") ?: "",
      businessAddress = doc.getString("businessAddress") ?: "",
      ownerName = doc.getString("ownerName") ?: "",
      currency = doc.getString("currency") ?: "RM",
      financialYear = doc.getString("financialYear") ?: "2026",
      closingStockByYear = stockMap,
      beginningBalances = BeginningBalances.fromMap(rawBb),
      lastInviteCode = doc.getString("lastInviteCode"),
      updatedAtMillis = updatedTs?.toDate()?.time ?: 0L
    )
  }

  private fun snapToLicence(doc: DocumentSnapshot): BusinessLicence? {
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

  private fun snapToDevice(doc: DocumentSnapshot): RegisteredDevice? {
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

  private fun snapToOwnerPrivateInfo(doc: DocumentSnapshot): OwnerPrivateInfo? {
    if (!doc.exists()) return null
    return OwnerPrivateInfo(
      ownerId = doc.getString("ownerId") ?: "",
      ownerIdNumber = doc.getString("ownerIdNumber") ?: "",
      ownerPhone = doc.getString("ownerPhone") ?: "",
      ownerEmail = doc.getString("ownerEmail") ?: ""
    )
  }

  private fun snapToInvite(doc: DocumentSnapshot): TeamInvite? {
    if (!doc.exists()) return null
    return TeamInvite(
      inviteCode = doc.id,
      businessId = doc.getString("businessId") ?: "",
      businessName = doc.getString("businessName") ?: "",
      role = TeamRole.fromWire(doc.getString("role")),
      createdByUid = doc.getString("createdByUid") ?: "",
      expiresAtMillis = doc.getTimestamp("expiresAt")?.toDate()?.time ?: 0L,
      redeemedByUid = doc.getString("redeemedByUid")
    )
  }

  private fun snapToTransaction(doc: DocumentSnapshot): BookkeepingTransaction? {
    if (!doc.exists()) return null
    val data = doc.data ?: return null
    val memberUids = (data["memberUids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
    val updatedTs = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    return BookkeepingTransaction(
      id = doc.id,
      businessId = doc.getString("businessId") ?: "",
      createdByUid = doc.getString("createdByUid") ?: "",
      memberUids = memberUids,
      date = doc.getString("date") ?: "",
      type = TransactionType.fromWire(doc.getString("type") ?: "IN"),
      categoryId = doc.getString("categoryId") ?: "exp_others",
      method = PaymentMethod.fromWire(doc.getString("method") ?: "CASH"),
      amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
      note = doc.getString("note") ?: "",
      partyName = doc.getString("partyName"),
      invoiceNo = doc.getString("invoiceNo"),
      disposalAssetType = AssetDisposalType.fromWire(doc.getString("disposalAssetType")),
      disposalAssetCost = (data["disposalAssetCost"] as? Number)?.toDouble(),
      updatedAtMillis = updatedTs?.toDate()?.time ?: 0L,
      hasPendingWrites = doc.metadata.hasPendingWrites()
    )
  }
}
