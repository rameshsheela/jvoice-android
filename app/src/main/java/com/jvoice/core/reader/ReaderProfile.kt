package com.jvoice.core.reader

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LanguagePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's own details: a display name, a preferred location and the
 * reading language.
 *
 * Readers have no account, so this is keyed on the device rather than a uid -
 * the same `ANDROID_ID` scheme the failed-login log already uses. It lives in
 * two places, for two different reasons:
 *
 *  * **SharedPreferences** is the source of truth the UI reads. It is there on
 *    the first frame and it works offline.
 *  * **RTDB `readers/{deviceId}`** is a write-through copy, so the desk can see
 *    who reads from where, and so a reinstall on the same device gets its name
 *    and location back ([init] pulls the node once when the local copy is empty).
 *
 * Nothing here is sensitive: a first name and a city. The rules for the node
 * are in `firebase/database.rules.json`.
 */
object ReaderProfile {

    private const val TAG = "ReaderProfile"
    private const val PREFS = "jvoice_reader"
    private const val KEY_NAME = "name"
    private const val KEY_LOCATION = "location"
    private const val NODE = "readers"

    /** The bureau the feed opens on until the reader picks one. */
    const val DEFAULT_LOCATION = "Hyderabad"

    /** Bounds the rules enforce too; kept in step by hand. */
    const val MAX_NAME_LENGTH = 40

    private var prefs: SharedPreferences? = null

    /** The device key the profile is stored under; "unknown" before [init]. */
    var deviceId: String = "unknown"
        private set

    private val _name = MutableStateFlow("")
    /** Empty until the reader sets one. */
    val name: StateFlow<String> = _name.asStateFlow()

    private val _location = MutableStateFlow(DEFAULT_LOCATION)
    val location: StateFlow<String> = _location.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        val store = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        deviceId = try {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
        _name.value = store.getString(KEY_NAME, "").orEmpty()
        _location.value = store.getString(KEY_LOCATION, null) ?: DEFAULT_LOCATION

        // A fresh install with a copy on the server: take the server's. Only
        // when there is nothing local, so a device that already has a name
        // never has it overwritten by a stale remote value.
        if (_name.value.isEmpty() && !store.contains(KEY_LOCATION)) restoreFromRemote()
    }

    fun setName(raw: String) {
        val name = raw.trim().take(MAX_NAME_LENGTH)
        _name.value = name
        prefs?.edit()?.putString(KEY_NAME, name)?.apply()
        push()
    }

    fun setLocation(location: String) {
        _location.value = location
        prefs?.edit()?.putString(KEY_LOCATION, location)?.apply()
        push()
    }

    /**
     * The language itself is owned by [LanguagePreference]; this only mirrors
     * a change to the server. Called from there, so every path that sets the
     * language - the first-run dialogue, the profile toggle - is covered.
     */
    fun onLanguageChanged() = push()

    private fun node() =
        if (FirebaseAvailability.isAvailable && deviceId != "unknown")
            FirebaseDatabase.getInstance().reference.child(NODE).child(deviceId)
        else null

    /** Writes the whole record; RTDB's own offline queue carries it if needed. */
    private fun push() {
        val ref = node() ?: return
        ref.setValue(
            mapOf(
                "name" to _name.value,
                "location" to _location.value,
                "language" to LanguagePreference.current.code,
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).addOnFailureListener { Log.w(TAG, "profile push failed: ${it.message}") }
    }

    private fun restoreFromRemote() {
        val ref = node() ?: return
        ref.get().addOnSuccessListener { snap ->
            val name = snap.child("name").getValue(String::class.java).orEmpty()
            val location = snap.child("location").getValue(String::class.java)
            // Still empty locally? The reader may have typed a name while the
            // read was in flight - theirs wins.
            if (name.isNotEmpty() && _name.value.isEmpty()) {
                _name.value = name
                prefs?.edit()?.putString(KEY_NAME, name)?.apply()
            }
            if (!location.isNullOrEmpty() && prefs?.contains(KEY_LOCATION) != true) {
                _location.value = location
                prefs?.edit()?.putString(KEY_LOCATION, location)?.apply()
            }
            // A language on the server means this device chose one before; take
            // it and skip the first-run question, unless it has been answered
            // again already.
            val language = snap.child("language").getValue(String::class.java)
            if (!language.isNullOrEmpty() && !LanguagePreference.hasChosen.value) {
                LanguagePreference.set(AppLanguage.fromCode(language))
            }
        }.addOnFailureListener { Log.w(TAG, "profile restore failed: ${it.message}") }
    }
}
