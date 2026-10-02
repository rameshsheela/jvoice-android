package com.jvoice.core.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.tasks.await

/**
 * The launch-time kill-switch, and the one correct way to log out.
 *
 * Two independent server levers end a session the app did not end itself:
 *
 *  * **`isLogin`** — checked once per launch, here. An admin setting it false means
 *    the next launch signs the user out. Cheap: one scalar read.
 *  * **`forceLogoutAt`** — watched live by [ForceLogoutGuard], so a session can be
 *    ended mid-use rather than at next launch.
 *
 * They are deliberately separate. The launch check is a single read on a path the
 * app already needs; a live listener on every field would cost a socket for the
 * whole session.
 */
object AuthGate {

    private const val TAG = "AuthGate"
    private const val KEY_IS_LOGIN = "isLogin"

    private fun usersRef() = FirebaseDatabase.getInstance().reference.child("users")

    /**
     * Interprets a raw `isLogin` value.
     *
     * **Missing means enabled.** This is the important case: accounts created before
     * the flag existed, or written by hand in the console without it, must keep
     * working. Only an *explicit* falsy value disables — `false`, `"false"`,
     * `"no"`, `0`.
     *
     * The string cases are not paranoia. The Firebase console writes values as
     * strings when typed in by hand, so `"false"` is what an admin actually
     * produces, and treating it as a non-empty string (truthy) would make the
     * kill-switch silently do nothing.
     */
    fun isEnabled(value: Any?): Boolean = when (value) {
        null -> true
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> !(
            value.equals("false", ignoreCase = true) ||
                value.equals("no", ignoreCase = true) ||
                value == "0"
            )
        else -> true
    }

    /**
     * Reads the kill-switch for [uid].
     *
     * A transient failure — offline, timeout — returns true. Locking a user out of
     * an app they have a valid cached session for because the network blipped is a
     * worse failure than letting a disabled account through for one more launch,
     * and [ForceLogoutGuard] will still catch them once connectivity returns.
     */
    suspend fun isAccountEnabled(uid: String): Boolean {
        if (!FirebaseAvailability.isAvailable) return true
        return try {
            val value = usersRef().child(uid).child(KEY_IS_LOGIN).get().await().value
            isEnabled(value).also {
                Log.d(TAG, "isLogin for $uid = $value -> enabled=$it")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read isLogin for $uid, allowing this launch: ${e.message}")
            true
        }
    }

    /** Best-effort write of the kill-switch. Failure is logged, never thrown. */
    fun setLoginFlag(uid: String, enabled: Boolean) {
        if (!FirebaseAvailability.isAvailable) return
        usersRef().child(uid).child(KEY_IS_LOGIN).setValue(enabled)
            .addOnFailureListener { Log.w(TAG, "Failed to set isLogin=$enabled: ${it.message}") }
    }

    /** Stamps the successful sign-in, for the desk's "last seen" view. */
    fun markLogin(uid: String) {
        if (!FirebaseAvailability.isAvailable) return
        usersRef().child(uid).child("lastLoginAt").setValue(ServerValue.TIMESTAMP)
            .addOnFailureListener { Log.w(TAG, "Failed to stamp lastLoginAt: ${it.message}") }
    }

    /**
     * Signs out of this device only.
     *
     * `isLogin` is deliberately left alone. It used to be cleared here, but it is
     * the account's on/off switch, read at every launch on every device - so
     * signing out on one phone (or the website) signed the same account out of
     * every other device at its next launch. Suspending an account is done by
     * the staffAccounts function, which also disables the login itself.
     *
     * Local state is cleared last, and unconditionally.
     */
    suspend fun logout() {
        if (FirebaseAvailability.isAvailable) {
            try {
                FirebaseAuth.getInstance().signOut()
            } catch (e: Exception) {
                Log.e(TAG, "Firebase signOut failed: ${e.message}")
            }
        }

        SessionStore.clear()
    }

    /** The uid Firebase currently holds a credential for, if any. */
    fun currentUid(): String? =
        if (!FirebaseAvailability.isAvailable) null
        else runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()

    /**
     * Re-reads `users/{uid}` and refreshes the cached session.
     *
     * Used when Firebase holds a credential but the device has no stored session —
     * app data cleared, or a reinstall while still authenticated.
     */
    suspend fun restoreSession(uid: String): SessionStore.DeskSession? {
        if (!FirebaseAvailability.isAvailable) return null
        return try {
            val snapshot = usersRef().child(uid).get().await()
            val user = snapshot.getValue(JvUser::class.java) ?: return null
            SessionStore.save(user.copy(uid = user.uid ?: uid))
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore session for $uid: ${e.message}")
            null
        }
    }
}
