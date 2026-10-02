package com.jvoice.core.data

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.tasks.await

/**
 * A reporter's invitations: their code (their login id), the link a friend
 * applies through, points earned, and everyone who applied with the code -
 * from the `reporterReferrals` Cloud Function.
 */
object Referrals {

    private const val TAG = "Referrals"

    data class Referral(
        val id: String,
        val name: String,
        val area: String,
        /** APPLIED, JOINED or DECLINED. */
        val status: String,
        val appliedAt: Long,
        val joinedAt: Long?,
        val loginId: String
    )

    data class Summary(
        val code: String,
        val link: String,
        val points: Int,
        val pointsPerJoin: Int,
        val referrals: List<Referral>,
        /** The reader download page with this code - jvoicetelugu.com/app?ref=… */
        val appLink: String = "",
        val installs: Int = 0,
        val pointsPerInstall: Int = 2
    )

    suspend fun referrals(): Result<Summary> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        return try {
            val result = FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("reporterReferrals")
                .call()
                .await()
            val d = result.getData() as? Map<*, *> ?: emptyMap<String, Any?>()
            val list = (d["referrals"] as? List<*>).orEmpty().mapNotNull { raw ->
                val r = raw as? Map<*, *> ?: return@mapNotNull null
                Referral(
                    id = r["id"]?.toString().orEmpty(),
                    name = r["name"]?.toString().orEmpty(),
                    area = r["area"]?.toString().orEmpty(),
                    status = r["status"]?.toString().orEmpty(),
                    appliedAt = (r["appliedAt"] as? Number)?.toLong() ?: 0L,
                    joinedAt = (r["joinedAt"] as? Number)?.toLong(),
                    loginId = r["loginId"]?.toString().orEmpty()
                )
            }
            Result.success(
                Summary(
                    code = d["code"]?.toString().orEmpty(),
                    link = d["link"]?.toString().orEmpty(),
                    points = (d["points"] as? Number)?.toInt() ?: 0,
                    pointsPerJoin = (d["pointsPerJoin"] as? Number)?.toInt() ?: 10,
                    referrals = list,
                    appLink = d["appLink"]?.toString().orEmpty(),
                    installs = (d["installs"] as? Number)?.toInt() ?: 0,
                    pointsPerInstall = (d["pointsPerInstall"] as? Number)?.toInt() ?: 2
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "referrals failed: ${e.message}")
            val code = (e as? FirebaseFunctionsException)?.code
            val message = when (code) {
                FirebaseFunctionsException.Code.NOT_FOUND,
                FirebaseFunctionsException.Code.UNIMPLEMENTED,
                FirebaseFunctionsException.Code.INTERNAL -> "Referrals are not available yet."
                FirebaseFunctionsException.Code.UNAVAILABLE -> "No connection. Check the internet and try again."
                null -> "Referrals are not available yet."
                else -> e.message?.takeIf { it.isNotBlank() } ?: "Referrals are not available yet."
            }
            Result.failure(Exception(message))
        }
    }
}
