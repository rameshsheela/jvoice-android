package com.jvoice.core.auth

import android.app.Application
import android.provider.Settings
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.lt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Where the sign-in form is in its lifecycle. */
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val session: SessionStore.DeskSession) : LoginUiState

    /** Message is bilingual — a Telugu reporter should read the failure in Telugu. */
    data class Error(val message: LocalizedText) : LoginUiState
}

/**
 * Desk sign-in.
 *
 * ### The login identifier
 *
 * Staff type a bare login id — `jv01r001`, `jv01e001` — and it is expanded to
 * `jv01r001@jvoicetelugu.com` before it reaches Firebase. Firebase Auth only does
 * email/password, but asking a district reporter to type a full address is a
 * needless source of typos. An input that already contains `@` is passed through
 * untouched, so an external address still works.
 *
 * ### Two deliberate divergences from the SafeTrack implementation this follows
 *
 * 1. **The attempted password is never written anywhere.** SafeTrack's
 *    `logFailedAttempt` stores it in plaintext under `failedLoginAttempts`, and its
 *    own module documentation flags that as a known weakness. Recording *that* a
 *    login failed is useful to the desk; recording what was typed is a credential
 *    leak, and near-misses of a real password are the worst thing to leak. Only the
 *    login id, device and count are kept.
 *
 * 2. **A blank or unknown role is a failure, not a default.** A record with no
 *    usable role fails sign-in rather than falling through to some least-privileged
 *    guess, because guessing here means handing out a desk session.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val database get() = FirebaseDatabase.getInstance().reference

    companion object {
        private const val TAG = "LoginViewModel"

        /** Staff login domain. A bare username is expanded onto this. */
        const val LOGIN_DOMAIN = "jvoicetelugu.com"

        /** `jv01r001` -> `jv01r001@jvoicetelugu.com`; `x@y.com` -> unchanged. */
        fun toEmail(loginId: String): String {
            val id = loginId.trim()
            return if (id.contains("@")) id else "$id@$LOGIN_DOMAIN"
        }
    }

    fun reset() {
        _uiState.value = LoginUiState.Idle
    }

    fun signIn(loginId: String, password: String) {
        val id = loginId.trim()

        if (id.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error(
                lt(
                    "Enter your login ID and password.",
                    "మీ లాగిన్ ఐడీ, పాస్‌వర్డ్ నమోదు చేయండి."
                )
            )
            return
        }

        if (!FirebaseAvailability.isAvailable) {
            _uiState.value = LoginUiState.Error(
                lt(
                    "Staff sign-in is not configured in this build.",
                    "ఈ బిల్డ్‌లో సిబ్బంది సైన్-ఇన్ కాన్ఫిగర్ చేయలేదు."
                )
            )
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val email = toEmail(id)
                Log.d(TAG, "Signing in as $email")

                val result = FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(email, password)
                    .await()
                val uid = result.user?.uid
                    ?: throw IllegalStateException("Signed in but no uid was returned")

                val snapshot = database.child("users").child(uid).get().await()
                val user = snapshot.getValue(JvUser::class.java)
                    ?: throw IllegalStateException("no user record")

                val role = user.jvRole
                if (role == null) {
                    // Authenticated, but not a desk account. Drop the credential so the
                    // app is not left in a half-signed-in state with no session.
                    runCatching { FirebaseAuth.getInstance().signOut() }
                    _uiState.value = LoginUiState.Error(
                        lt(
                            "This account has no desk role assigned. Contact the administrator.",
                            "ఈ ఖాతాకు డెస్క్ రోల్ కేటాయించలేదు. అడ్మినిస్ట్రేటర్‌ను సంప్రదించండి."
                        )
                    )
                    return@launch
                }

                val session = SessionStore.save(user.copy(uid = uid))
                    ?: throw IllegalStateException("Could not store the session")

                // Re-enables an account previously disabled via isLogin=false, and
                // records the sign-in for the desk's session view.
                AuthGate.setLoginFlag(uid, true)
                AuthGate.markLogin(uid)

                Log.d(TAG, "Signed in: uid=$uid role=${role.code}")
                _uiState.value = LoginUiState.Success(session)
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in failed: ${e.message}", e)
                val raw = e.message.orEmpty()
                val msg = raw.lowercase()

                if (isCredentialFailure(msg)) {
                    logFailedAttempt(id)
                }
                _uiState.value = LoginUiState.Error(friendlyError(msg, raw))
            }
        }
    }

    /**
     * Did this fail because the id/password were wrong, as opposed to network or
     * data problems?
     *
     * Matched on the exception message because the Firebase SDK surfaces several
     * different codes for the same user-visible mistake, and has changed which one
     * it uses: modern versions return a single generic `INVALID_LOGIN_CREDENTIALS`
     * for both a wrong password and an unknown account, where older ones
     * distinguished them. All the spellings are checked so this keeps working
     * across SDK updates.
     */
    private fun isCredentialFailure(msg: String): Boolean =
        "no user record" in msg || "user-not-found" in msg ||
            "password is invalid" in msg || "wrong-password" in msg ||
            "invalid_login_credentials" in msg || "invalid-credential" in msg ||
            "auth credential is incorrect" in msg

    private fun friendlyError(msg: String, raw: String): LocalizedText = when {
        "no user record" in msg && "users/" in msg -> lt(
            "Your profile is missing from the system. Contact the administrator.",
            "మీ ప్రొఫైల్ సిస్టమ్‌లో కనబడలేదు. అడ్మినిస్ట్రేటర్‌ను సంప్రదించండి."
        )

        "no user record" in msg || "user-not-found" in msg ||
            "password is invalid" in msg || "wrong-password" in msg ||
            "invalid_login_credentials" in msg || "invalid-credential" in msg ||
            "auth credential is incorrect" in msg -> lt(
            // One message for both cases on purpose: telling an attacker which of
            // the two was wrong confirms whether an account exists.
            "Incorrect login ID or password.",
            "లాగిన్ ఐడీ లేదా పాస్‌వర్డ్ తప్పు."
        )

        "too-many-requests" in msg || "too many" in msg -> lt(
            "Too many attempts. Wait a few minutes and try again.",
            "చాలా ప్రయత్నాలు. కొన్ని నిమిషాలు ఆగి మళ్లీ ప్రయత్నించండి."
        )

        "network" in msg || "timeout" in msg || "unreachable" in msg ||
            "host" in msg -> lt(
            "Network problem. Check your connection and try again.",
            "నెట్‌వర్క్ సమస్య. మీ కనెక్షన్ చూసి మళ్లీ ప్రయత్నించండి."
        )

        "operation is not allowed" in msg || "operation-not-allowed" in msg -> lt(
            // The exact failure when email/password sign-in has not been switched on
            // in the console. Worth its own message - it looks like a bad password
            // otherwise and sends people hunting the wrong problem.
            "Email sign-in is not enabled on the Firebase project yet.",
            "Firebase ప్రాజెక్ట్‌లో ఇమెయిల్ సైన్-ఇన్ ఇంకా ప్రారంభించలేదు."
        )

        else -> lt(
            raw.ifBlank { "Sign-in failed. Please try again." },
            raw.ifBlank { "సైన్-ఇన్ విఫలమైంది. మళ్లీ ప్రయత్నించండి." }
        )
    }

    /**
     * Records a wrong-login attempt at `failedLoginAttempts/{deviceId}`.
     *
     * One record per device, keyed on ANDROID_ID and updated in place — repeated
     * tries increment `attempts` rather than creating a node each time, so a
     * brute-force attempt cannot inflate the database.
     *
     * **The password is not recorded.** See the class note.
     */
    private fun logFailedAttempt(loginId: String) {
        if (!FirebaseAvailability.isAvailable) return
        try {
            val deviceId = deviceId()
            val updates = mapOf(
                "deviceId" to deviceId,
                "loginId" to loginId,
                "lastAttemptAt" to ServerValue.TIMESTAMP,
                "attempts" to ServerValue.increment(1)
            )
            database.child("failedLoginAttempts").child(deviceId)
                .updateChildren(updates)
                .addOnFailureListener { Log.w(TAG, "Could not log failed attempt: ${it.message}") }
        } catch (e: Exception) {
            Log.w(TAG, "logFailedAttempt error: ${e.message}")
        }
    }

    /**
     * Queues a password-reset callback at `passwordResetRequests/{phone}`.
     *
     * There is no self-service reset: staff accounts are created by the desk and
     * reset by the desk. This only records that someone asked, one record per phone
     * number with a request count, for an administrator to act on.
     */
    fun requestPasswordReset(phone: String, onDone: (LocalizedText) -> Unit) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) {
            onDone(
                lt(
                    "Enter a valid 10-digit mobile number.",
                    "సరైన 10 అంకెల మొబైల్ నంబర్ నమోదు చేయండి."
                )
            )
            return
        }
        if (!FirebaseAvailability.isAvailable) {
            onDone(
                lt(
                    "Not available in this build.",
                    "ఈ బిల్డ్‌లో అందుబాటులో లేదు."
                )
            )
            return
        }
        val updates = mapOf(
            "phone" to digits,
            "lastRequestedAt" to ServerValue.TIMESTAMP,
            "requests" to ServerValue.increment(1)
        )
        database.child("passwordResetRequests").child(digits)
            .updateChildren(updates)
            .addOnSuccessListener {
                onDone(
                    lt(
                        "Request sent. The desk will call you back.",
                        "అభ్యర్థన పంపబడింది. డెస్క్ మీకు కాల్ చేస్తుంది."
                    )
                )
            }
            .addOnFailureListener {
                onDone(
                    lt(
                        "Could not send the request. Try again.",
                        "అభ్యర్థన పంపడం సాధ్యం కాలేదు. మళ్లీ ప్రయత్నించండి."
                    )
                )
            }
    }

    private fun deviceId(): String = try {
        Settings.Secure.getString(
            getApplication<Application>().contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    } catch (e: Exception) {
        "unknown"
    }
}
