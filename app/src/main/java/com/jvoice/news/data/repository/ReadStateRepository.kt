package com.jvoice.news.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which stories the reader has already seen, kept on the device.
 *
 * Unlike the rest of the prototype this one genuinely persists - it is written to
 * SharedPreferences, so read state survives the app being killed. That is the whole
 * point of it: the reader should not be shown the same story again next time.
 *
 * Read stories are not hidden, only *deprioritised*: the feed deals the unread
 * ones first and the already-read ones after them, so it never runs dry and
 * never repeats itself while there is something new.
 */
object ReadStateRepository {

    private const val PREFS = "jvoice_read_state"
    private const val KEY_READ = "read_article_ids"
    private const val KEY_READ_NOTIFICATIONS = "read_notification_ids"
    private const val KEY_ROUND = "round"

    private var prefs: SharedPreferences? = null

    private val _readIds = MutableStateFlow<Set<String>>(emptySet())
    val readIds: StateFlow<Set<String>> = _readIds.asStateFlow()

    /** Notifications this device has opened. Same reasoning as [readIds]. */
    private val _readNotificationIds = MutableStateFlow<Set<String>>(emptySet())
    val readNotificationIds: StateFlow<Set<String>> = _readNotificationIds.asStateFlow()

    /** Bumped each time the feed loops; used to rebuild the deck. */
    private val _round = MutableStateFlow(1)
    val round: StateFlow<Int> = _round.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _readIds.value = store.getStringSet(KEY_READ, emptySet())?.toSet() ?: emptySet()
        _readNotificationIds.value = store.getStringSet(KEY_READ_NOTIFICATIONS, emptySet())?.toSet() ?: emptySet()
        _round.value = store.getInt(KEY_ROUND, 1)
    }

    fun isRead(articleId: String): Boolean = _readIds.value.contains(articleId)

    /**
     * Records a read. Returns true only the first time for a story, which is
     * what the caller uses to count a view once per device.
     */
    fun markRead(articleId: String): Boolean {
        if (articleId.isBlank() || _readIds.value.contains(articleId)) return false
        val updated = _readIds.value + articleId
        _readIds.value = updated
        prefs?.edit()?.putStringSet(KEY_READ, updated)?.apply()
        return true
    }

    fun isNotificationRead(id: String): Boolean = _readNotificationIds.value.contains(id)

    fun markNotificationRead(id: String) {
        if (id.isBlank() || _readNotificationIds.value.contains(id)) return
        val updated = _readNotificationIds.value + id
        _readNotificationIds.value = updated
        prefs?.edit()?.putStringSet(KEY_READ_NOTIFICATIONS, updated)?.apply()
    }

    /** True when every id given has been read. */
    fun hasReadAll(allIds: Collection<String>): Boolean =
        allIds.isNotEmpty() && _readIds.value.containsAll(allIds)

    /** Finishes the round: clears the read set so the feed starts over. */
    fun startNewRound() {
        _readIds.value = emptySet()
        _round.value = _round.value + 1
        prefs?.edit()
            ?.putStringSet(KEY_READ, emptySet())
            ?.putInt(KEY_ROUND, _round.value)
            ?.apply()
    }

    fun unreadCount(allIds: Collection<String>): Int =
        allIds.count { !_readIds.value.contains(it) }
}
