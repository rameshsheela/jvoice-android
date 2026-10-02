package com.jvoice.core.i18n

import android.content.Context
import android.content.SharedPreferences
import com.jvoice.core.reader.ReaderProfile
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The reader's chosen language, kept on the device.
 *
 * Like [com.jvoice.news.data.repository.ReadStateRepository] this genuinely
 * persists rather than living in memory - a language choice that reset on every
 * launch would be worse than no choice at all.
 *
 * [hasChosen] backs the first-run dialogue: the very first launch asks for a
 * language and for notification permission, and never asks again.
 */
object LanguagePreference {

    private const val PREFS = "jvoice_language"
    private const val KEY_LANGUAGE = "app_language"
    private const val KEY_CHOSEN = "language_chosen"
    private const val KEY_NOTIFICATIONS_ASKED = "notifications_asked"

    private var prefs: SharedPreferences? = null

    private val _language = MutableStateFlow(AppLanguage.DEFAULT)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    /** False until the reader has been through the first-run picker. */
    private val _hasChosen = MutableStateFlow(false)
    val hasChosen: StateFlow<Boolean> = _hasChosen.asStateFlow()

    /** So the notification prompt is only ever raised once. */
    private val _notificationsAsked = MutableStateFlow(false)
    val notificationsAsked: StateFlow<Boolean> = _notificationsAsked.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _language.value = AppLanguage.fromCode(store.getString(KEY_LANGUAGE, null))
        _hasChosen.value = store.getBoolean(KEY_CHOSEN, false)
        _notificationsAsked.value = store.getBoolean(KEY_NOTIFICATIONS_ASKED, false)
    }

    val current: AppLanguage get() = _language.value

    /**
     * Applies [language] and records that the reader has made a choice, so the
     * first-run dialogue never comes back.
     */
    fun set(language: AppLanguage) {
        apply(language)
        markChosen()
    }

    /**
     * Applies [language] without answering the first-run question.
     *
     * The first-run dialogue needs this: tapping a language row there must flip
     * the app over live so the reader can see the result, but the dialogue has to
     * stay open until they have also answered the notification prompt. Committing
     * on every tap would dismiss it after the first one.
     */
    fun apply(language: AppLanguage) {
        if (_language.value == language) return
        _language.value = language
        prefs?.edit()?.putString(KEY_LANGUAGE, language.code)?.apply()
        // Mirrored to the reader's server record with their name and location.
        ReaderProfile.onLanguageChanged()
    }

    /** Closes the first-run question for good. */
    fun markChosen() {
        if (_hasChosen.value) return
        _hasChosen.value = true
        prefs?.edit()?.putBoolean(KEY_CHOSEN, true)?.apply()
    }

    /** Profile-tab toggle: flip to the other language. */
    fun toggle() = set(_language.value.other)

    fun markNotificationsAsked() {
        if (_notificationsAsked.value) return
        _notificationsAsked.value = true
        prefs?.edit()?.putBoolean(KEY_NOTIFICATIONS_ASKED, true)?.apply()
    }
}

/**
 * The active language, readable from any composable without threading it through
 * every screen signature - the same trick
 * [com.jvoice.shell.LocalModuleSwitcher] uses.
 *
 * Deliberately [compositionLocalOf] and not `staticCompositionLocalOf`: this value
 * changes when the reader flips the toggle, and every screen reading it has to
 * recompose.
 */
val LocalAppLanguage = compositionLocalOf { AppLanguage.DEFAULT }
