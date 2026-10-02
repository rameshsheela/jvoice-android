package com.jvoice.core.flags

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-level feature flags, held at `/flags` in the Realtime Database.
 *
 * ## Why RTDB and not Firestore
 *
 * This is one small object that every launch reads and that has to take effect
 * *live* — the point of a flag is turning something off without shipping a build.
 * RTDB value events give that for the cost of one tiny node; a Firestore document
 * listener would work too but the session signals already live here, so the app
 * keeps one connection rather than opening a second for four booleans.
 *
 * ## Missing means ON - except for opt-in flags
 *
 * Every flag defaults to `true` when the node is absent or unreadable. That is
 * deliberate and it is the direction that fails safely: a network blip, a deleted
 * node or a typo'd key leaves the app exactly as it shipped, rather than silently
 * stripping the toolbar and the tabs and looking broken. A feature is only ever
 * hidden by an *explicit* `false`.
 *
 * The exception is a control that ships *off* and is switched on later, such as
 * the location picker and the Clips tab: for those the same failure modes must
 * keep it hidden, so they read through [flagOptedIn], which needs an explicit
 * `true`. Which way a
 * flag reads is decided at the call site, not here, because it is a property of
 * the feature - is it part of the shipped app, or a later addition - rather than
 * of the storage.
 *
 * The same reasoning as [com.jvoice.core.auth.AuthGate.isEnabled], and the string
 * cases matter for the same reason: the Firebase console writes hand-typed values
 * as strings, so `"false"` is what an operator actually produces.
 */
object FeatureFlags {

    private const val TAG = "FeatureFlags"
    private const val NODE = "flags"

    /**
     * The flag keys, as stored. Named after what they control rather than where
     * they appear, so moving a control does not orphan its flag.
     */
    object Keys {
        /** The location picker in the news toolbar. Opt-in: see [flagOptedIn]. */
        const val NEWS_LOCATION_DROPDOWN = "newsLocationDropdown"

        /** The row of location filter chips in the swipe feed. */
        const val LOCATION_CHIPS = "locationChips"

        /** The Clips / Shorts tab in the reader's bottom bar. Opt-in: see [flagOptedIn]. */
        const val SHORTS_TAB = "shortsTab"

        /** The Study tab in the reader's bottom bar. Opt-in: see [flagOptedIn]. */
        const val STUDY_TAB = "studyTab"

        /**
         * The "Staff sign in" entry on the reader's profile. Opt-in: the desk
         * is not offered from the public app until this is switched on.
         */
        const val STAFF_LOGIN = "staffLogin"
    }

    /** Current values. Absent keys are simply not present and read as ON. */
    private val _flags = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    private var listener: ValueEventListener? = null

    fun start() {
        if (listener != null || !FirebaseAvailability.isAvailable) return
        val ref = FirebaseDatabase.getInstance().reference.child(NODE)

        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val parsed = snapshot.children.mapNotNull { child ->
                    val key = child.key ?: return@mapNotNull null
                    key to interpret(child.value)
                }.toMap()
                _flags.value = parsed
                Log.i(TAG, "flags: $parsed")
            }

            override fun onCancelled(error: DatabaseError) {
                // Leaves the map empty, which means every flag reads as ON - see
                // the class note. A rules problem must not blank the UI.
                Log.w(TAG, "flags unreadable, defaulting everything on: ${error.message}")
            }
        }
        ref.addValueEventListener(l)
        listener = l
    }

    /** Only an explicit falsy value turns a flag off. */
    private fun interpret(value: Any?): Boolean = when (value) {
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

    /** Is [key] on? Unknown keys are on. */
    fun isEnabled(key: String): Boolean = _flags.value[key] ?: true

    /** Has [key] been explicitly switched on? Unknown keys are off. */
    fun isOptedIn(key: String): Boolean = _flags.value[key] == true
}

/**
 * The flags, readable from any composable without threading them through every
 * screen signature — the same approach
 * [com.jvoice.core.i18n.LocalAppLanguage] uses.
 *
 * `compositionLocalOf` rather than the static variant because these change at
 * runtime and every screen reading one has to recompose.
 */
val LocalFeatureFlags = compositionLocalOf { emptyMap<String, Boolean>() }

/** Reads one flag inside a composable. Unknown keys are on. */
@Composable
@ReadOnlyComposable
fun flagEnabled(key: String): Boolean = LocalFeatureFlags.current[key] ?: true

/**
 * Reads an opt-in flag inside a composable: on only for an explicit `true`.
 * The mirror image of [flagEnabled], for features that ship hidden - see the
 * class note on [FeatureFlags].
 */
@Composable
@ReadOnlyComposable
fun flagOptedIn(key: String): Boolean = LocalFeatureFlags.current[key] == true

/**
 * Puts a whole destination behind a flag, not just the control that opens it.
 *
 * [flagEnabled] hides an *entrance* - a tab, a toolbar icon - which is all a
 * control needs. A flow needs more, because hiding the way in does nothing about
 * whoever is already inside:
 *
 *  * a reader sitting on the screen when an admin flips the flag keeps the
 *    screen, and only the tab underneath it vanishes;
 *  * Navigation restores the last route after process death, so an app killed on
 *    a flagged-off screen reopens straight back onto it.
 *
 * One live check covers both. [content] composes only while the flag is on, and
 * [onBlocked] - a redirect to somewhere always reachable - runs the moment it is
 * not: on first composition for the restored-route case, and on recomposition
 * for the live flip.
 *
 * Nothing is drawn in the blocked state on purpose. The redirect is immediate,
 * and a flash of "this feature is off" on the way out reads as a crash rather
 * than a decision.
 */
@Composable
fun FlaggedRoute(
    key: String,
    onBlocked: () -> Unit,
    /** Read the flag as opt-in ([flagOptedIn]) rather than fail-open. */
    optIn: Boolean = false,
    content: @Composable () -> Unit
) {
    val enabled = if (optIn) flagOptedIn(key) else flagEnabled(key)
    LaunchedEffect(enabled) { if (!enabled) onBlocked() }
    if (enabled) content()
}
