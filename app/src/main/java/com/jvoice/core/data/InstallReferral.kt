package com.jvoice.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.reader.ReaderProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Which reporter brought this phone to J Voice - for their referral points.
 *
 *  * **From Play:** the reporter's link (jvoicetelugu.com/app?ref=…) opens the
 *    Play listing with `referrer=ref=<code>`. Play hands that back once, on
 *    first open, through the Install Referrer API; [start] reads it.
 *  * **Typed:** an APK install carries no referrer, so the reader can enter
 *    the code once on their Profile ([claim]).
 *
 * Either way it is credited once per device by the creditInstall function,
 * which keys on the device id - so it cannot be claimed twice.
 */
object InstallReferral {

    private const val TAG = "InstallReferral"
    private const val PREFS = "jvoice_install_referral"
    private const val KEY_DONE = "done"
    private const val KEY_REPORTER = "reporter"
    private const val KEY_PLAY_CHECKED = "play_checked"

    private var prefs: SharedPreferences? = null

    private val _creditedTo = MutableStateFlow<String?>(null)
    /** The reporter this phone is credited to, once it is. */
    val creditedTo: StateFlow<String?> = _creditedTo.asStateFlow()

    fun start(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        if (store.getBoolean(KEY_DONE, false)) {
            _creditedTo.value = store.getString(KEY_REPORTER, "")
            return
        }
        if (store.getBoolean(KEY_PLAY_CHECKED, false)) return
        val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
        runCatching {
            client.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(code: Int) {
                    val referrer = if (code == InstallReferrerClient.InstallReferrerResponse.OK) {
                        runCatching { client.installReferrer.installReferrer }.getOrNull()
                    } else null
                    runCatching { client.endConnection() }
                    // Not from Play (an APK), or Play had nothing: nothing to ask again.
                    if (code != InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE) {
                        store.edit().putBoolean(KEY_PLAY_CHECKED, true).apply()
                    }
                    val ref = referrer?.let(::codeFrom) ?: return
                    CoroutineScope(Dispatchers.IO).launch { claim(ref, "play") }
                }

                override fun onInstallReferrerServiceDisconnected() {}
            })
        }.onFailure { Log.w(TAG, "install referrer unavailable: ${it.message}") }
    }

    /** `ref=jv32r001` (possibly among utm_ pairs) -> `jv32r001`. */
    private fun codeFrom(referrer: String): String? =
        referrer.split('&')
            .mapNotNull { pair -> pair.split('=', limit = 2).takeIf { it.size == 2 } }
            .firstOrNull { it[0] == "ref" }
            ?.get(1)?.lowercase()?.filter { it.isLetterOrDigit() }
            ?.takeIf { it.length in 3..20 }

    /**
     * Credits [code]'s reporter with this install.
     *
     * @return the reporter's name, or a failure with a sentence for the screen.
     */
    suspend fun claim(code: String, source: String = "typed"): Result<String> {
        if (!FirebaseAvailability.isAvailable) return Result.failure(Exception("Not connected to J Voice."))
        val clean = code.trim().lowercase().filter { it.isLetterOrDigit() }
        if (clean.length < 3) return Result.failure(Exception("Enter the reporter's code, like jv01r001."))
        val deviceId = ReaderProfile.deviceId.takeIf { it != "unknown" }
            ?: return Result.failure(Exception("Could not read this phone's id."))
        return try {
            val result = FirebaseFunctions.getInstance("asia-south1")
                .getHttpsCallable("creditInstall")
                .call(mapOf("code" to clean, "deviceId" to deviceId, "source" to source))
                .await()
            val reporter = (result.getData() as? Map<*, *>)?.get("reporter")?.toString().orEmpty()
            prefs?.edit()?.putBoolean(KEY_DONE, true)?.putString(KEY_REPORTER, reporter)?.apply()
            _creditedTo.value = reporter
            Result.success(reporter)
        } catch (e: Exception) {
            Log.w(TAG, "claim failed: ${e.message}")
            val message = when ((e as? FirebaseFunctionsException)?.code) {
                FirebaseFunctionsException.Code.NOT_FOUND -> "That code is not a J Voice reporter code."
                FirebaseFunctionsException.Code.UNAVAILABLE -> "No connection. Check the internet and try again."
                else -> "Could not save the code. Try again."
            }
            Result.failure(Exception(message))
        }
    }
}
