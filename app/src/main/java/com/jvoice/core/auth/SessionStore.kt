package com.jvoice.core.auth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The signed-in desk session, cached on the device.
 *
 * ### Why the session is cached rather than re-fetched
 *
 * Re-validating credentials on every launch costs a network round trip before the
 * first screen can render, which is the single most noticeable startup cost in an
 * app like this. So the session is trusted from disk and only two cheap server
 * checks run at launch: the `isLogin` kill-switch ([AuthGate]) and the update gate
 * ([AppUpdateGate]). This mirrors how the SafeTrack console works.
 *
 * The trade-off is explicit: a revoked account keeps its cached session until the
 * next launch, which is exactly why [ForceLogoutGuard] exists — it watches
 * `forceLogoutAt` live and ends a session mid-use.
 *
 * ### What is not stored here
 *
 * No password, and no auth token. Firebase's own SDK persists the credential; this
 * only holds the resolved identity so the app can route without a database read.
 */
object SessionStore {

    private const val PREFS = "jvoice_desk_session"

    private const val KEY_UID = "uid"
    private const val KEY_LOGIN_ID = "login_id"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PHONE = "phone"
    private const val KEY_ROLE = "role"
    private const val KEY_AVATAR = "avatar_url"

    private var prefs: SharedPreferences? = null

    private val _session = MutableStateFlow<DeskSession?>(null)

    /** The current desk session, or null when nobody is signed in. */
    val session: StateFlow<DeskSession?> = _session.asStateFlow()

    /**
     * A restored session. [role] is non-null by construction: a stored record whose
     * role no longer resolves is discarded on load rather than carried as a session
     * with no permissions.
     */
    data class DeskSession(
        val uid: String,
        val loginId: String,
        val name: String,
        val email: String,
        val phone: String,
        val role: JvRole,
        /** Profile photo, set from the staff profile screen. */
        val avatarUrl: String = ""
    )

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _session.value = read(store)
    }

    private fun read(store: SharedPreferences): DeskSession? {
        val uid = store.getString(KEY_UID, null)?.takeIf { it.isNotBlank() } ?: return null
        // A stored role that no longer resolves - renamed in the database, or written
        // by hand - must not become a session. Dropping it sends the user back to the
        // login screen, which is the safe outcome.
        val role = JvRole.fromCode(store.getString(KEY_ROLE, null)) ?: return null
        return DeskSession(
            uid = uid,
            loginId = store.getString(KEY_LOGIN_ID, "").orEmpty(),
            name = store.getString(KEY_NAME, "").orEmpty(),
            email = store.getString(KEY_EMAIL, "").orEmpty(),
            phone = store.getString(KEY_PHONE, "").orEmpty(),
            role = role,
            avatarUrl = store.getString(KEY_AVATAR, "").orEmpty()
        )
    }

    /**
     * The signed-in person changed their own profile. Only the fields given
     * change; the rest of the session stays as it is.
     */
    fun updateProfile(name: String? = null, phone: String? = null, avatarUrl: String? = null) {
        val current = _session.value ?: return
        val next = current.copy(
            name = name ?: current.name,
            phone = phone ?: current.phone,
            avatarUrl = avatarUrl ?: current.avatarUrl
        )
        prefs?.edit()
            ?.putString(KEY_NAME, next.name)
            ?.putString(KEY_PHONE, next.phone)
            ?.putString(KEY_AVATAR, next.avatarUrl)
            ?.apply()
        _session.value = next
    }

    /** Persists a freshly signed-in user. Returns the session it stored. */
    fun save(user: JvUser): DeskSession? {
        val uid = user.uid?.takeIf { it.isNotBlank() } ?: return null
        val role = user.jvRole ?: return null
        val session = DeskSession(
            uid = uid,
            loginId = user.loginId.orEmpty(),
            name = user.displayName,
            email = user.email.orEmpty(),
            phone = user.phone.orEmpty(),
            role = role,
            avatarUrl = user.avatarUrl.orEmpty()
        )
        prefs?.edit()
            ?.putString(KEY_UID, session.uid)
            ?.putString(KEY_LOGIN_ID, session.loginId)
            ?.putString(KEY_NAME, session.name)
            ?.putString(KEY_EMAIL, session.email)
            ?.putString(KEY_PHONE, session.phone)
            ?.putString(KEY_ROLE, session.role.code)
            ?.putString(KEY_AVATAR, session.avatarUrl)
            ?.apply()
        _session.value = session
        return session
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _session.value = null
    }

    val current: DeskSession? get() = _session.value
    val isSignedIn: Boolean get() = _session.value != null
}
