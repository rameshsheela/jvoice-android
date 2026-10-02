package com.jvoice.core.data

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.tasks.await

/**
 * The `staffAccounts` Cloud Function (firebase/functions/staff.js): creates and
 * manages reporter and editor logins for a news or super admin.
 *
 * A login is an Auth user, its role claim and its `users/{uid}` record, and the
 * phone can make none of the first two - so every account change goes through
 * here rather than writing the database directly.
 *
 * Login ids are also employee ids: `jv` + area code + `r`/`e` + number, e.g.
 * `jv01r001`, signing in as `jv01r001@jvoicetelugu.com`.
 *
 * Every call returns [Result]; a failure carries a message written for a person
 * (the function's own, where it gave one).
 */
object StaffAccounts {

    private const val TAG = "StaffAccounts"
    private const val REGION = "asia-south1"
    private const val NAME = "staffAccounts"

    /** The account a successful create hands back. */
    data class Created(val uid: String, val loginId: String, val employeeId: String, val name: String)

    private suspend fun call(data: Map<String, Any?>): Result<Map<*, *>> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        return try {
            val result = FirebaseFunctions.getInstance(REGION)
                .getHttpsCallable(NAME)
                .call(data)
                .await()
            Result.success(result.getData() as? Map<*, *> ?: emptyMap<String, Any?>())
        } catch (e: Exception) {
            Log.w(TAG, "${data["action"]} failed: ${e.message}")
            val message = when {
                e is FirebaseFunctionsException &&
                    e.code == FirebaseFunctionsException.Code.UNAVAILABLE ->
                    "No connection. Check the internet and try again."
                e is FirebaseFunctionsException && !e.message.isNullOrBlank() -> e.message!!
                else -> "Could not reach J Voice. Try again."
            }
            Result.failure(Exception(message))
        }
    }

    /** The id the next reporter/editor in this area would get - a preview only. */
    suspend fun nextId(role: String, area: String): Result<String> =
        call(mapOf("action" to "nextId", "role" to role, "area" to area))
            .map { it["loginId"]?.toString().orEmpty() }

    suspend fun create(
        role: String,
        area: String,
        name: String,
        phone: String,
        location: String,
        password: String
    ): Result<Created> =
        call(
            mapOf(
                "action" to "create",
                "role" to role,
                "area" to area,
                "name" to name,
                "phone" to phone,
                "location" to location,
                "password" to password
            )
        ).map {
            val p = it["person"] as? Map<*, *> ?: emptyMap<String, Any?>()
            Created(
                uid = p["uid"]?.toString().orEmpty(),
                loginId = p["loginId"]?.toString().orEmpty(),
                employeeId = p["employeeId"]?.toString().orEmpty(),
                name = p["name"]?.toString().orEmpty()
            )
        }

    suspend fun setActive(uid: String, active: Boolean): Result<Unit> =
        call(mapOf("action" to "setActive", "uid" to uid, "active" to active)).map { }

    suspend fun setPassword(uid: String, password: String): Result<Unit> =
        call(mapOf("action" to "setPassword", "uid" to uid, "password" to password)).map { }

    suspend fun setLocation(uid: String, location: String): Result<Unit> =
        call(mapOf("action" to "update", "uid" to uid, "location" to location)).map { }
}
