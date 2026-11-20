package com.example.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth

enum class OperationType {
  CREATE,
  UPDATE,
  DELETE,
  LIST,
  GET,
  WRITE
}

data class FirestoreErrorInfo(
  val error: String,
  val operationType: OperationType,
  val path: String?,
  val authInfo: AuthInfo
) {
  data class AuthInfo(
    val userId: String?,
    val email: String?,
    val emailVerified: Boolean?,
    val isAnonymous: Boolean?,
    val tenantId: String?,
    val providerInfo: List<ProviderInfo>
  )

  data class ProviderInfo(
    val providerId: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?
  )

  fun toJson(): String {
    val providersJson = authInfo.providerInfo.joinToString(",", "[", "]") { p ->
      """{"providerId":"${esc(p.providerId)}","displayName":"${esc(p.displayName)}","email":"${esc(p.email)}"}"""
    }
    return """{"error":"${esc(error)}","operationType":"${operationType.name}","path":"${esc(path)}","authInfo":{"userId":"${esc(authInfo.userId)}","email":"${esc(authInfo.email)}","emailVerified":${authInfo.emailVerified},"isAnonymous":${authInfo.isAnonymous},"providerInfo":$providersJson}}"""
  }

  private fun esc(value: String?): String {
    return (value ?: "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
  }
}

fun handleFirestoreError(
  error: Throwable,
  operationType: OperationType,
  path: String?
): FirestoreErrorInfo {
  val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
  val user = auth?.currentUser
  val info = FirestoreErrorInfo(
    error = error.message ?: error.toString(),
    operationType = operationType,
    path = path,
    authInfo = FirestoreErrorInfo.AuthInfo(
      userId = user?.uid,
      email = user?.email,
      emailVerified = user?.isEmailVerified,
      isAnonymous = user?.isAnonymous,
      tenantId = user?.tenantId,
      providerInfo = user?.providerData?.map { p ->
        FirestoreErrorInfo.ProviderInfo(
          providerId = p.providerId,
          displayName = p.displayName,
          email = p.email,
          photoUrl = p.photoUrl?.toString()
        )
      } ?: emptyList()
    )
  )
  Log.e("FirestoreError", "Firestore Error: ${info.toJson()}", error)
  return info
}
