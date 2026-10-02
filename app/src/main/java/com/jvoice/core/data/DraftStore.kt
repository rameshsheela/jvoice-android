package com.jvoice.core.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * The story a reporter is still writing, kept on the phone.
 *
 * Saved as they type, per account, so closing the app, a crash or a dead
 * battery does not lose it; it comes back the next time they open Create News,
 * and is cleared once J Voice has the story. (Once sent, an offline story is
 * Firestore's to keep: its queued write survives a restart and goes out when
 * the phone is back online.)
 *
 * Stored as a flat JSON object of strings and booleans - the form's own
 * fields - so this file knows nothing about the form's types.
 */
object DraftStore {

    private const val PREFS = "jvoice_story_drafts"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(uid: String, fields: Map<String, Any>) {
        if (uid.isBlank()) return
        prefs?.edit()?.putString(uid, JSONObject(fields).toString())?.apply()
    }

    fun load(uid: String): JSONObject? {
        if (uid.isBlank()) return null
        val raw = prefs?.getString(uid, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun clear(uid: String) {
        if (uid.isBlank()) return
        prefs?.edit()?.remove(uid)?.apply()
    }
}
