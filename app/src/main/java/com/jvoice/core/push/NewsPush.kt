package com.jvoice.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.jvoice.core.auth.JvRole
import com.jvoice.core.auth.SessionStore
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LanguagePreference
import com.jvoice.news.MainActivity
import com.jvoice.news.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Push notifications for published stories.
 *
 * The desk's "Send notification" switch writes a `newsNotifications` document;
 * the `pushNewsNotification` Cloud Function (firebase/functions) relays that to
 * the FCM topic [TOPIC], and [JVoiceMessagingService] hands the payload here.
 *
 * The message is data-only, so this object is what actually draws the
 * notification - in the reader's chosen language, which the server cannot know.
 */
object NewsPush {

    private const val TAG = "NewsPush"

    /** Every reader device subscribes to this; the function sends to it. */
    const val TOPIC = "news"

    private const val CHANNEL_ID = "news"
    private const val CHANNEL_BREAKING_ID = "breaking"

    /**
     * The review alarm for editors and news admins (functions/review.js): a
     * filed story rings every 5 minutes until it is decided. Its own channel,
     * so it has its own tone and the reader alerts stay as they are. The id is
     * versioned because a channel's sound cannot change once created.
     */
    const val REVIEW_TOPIC = "desk-review"
    private const val CHANNEL_REVIEW_ID = "desk_review_v1"
    private const val REVIEW_NOTIFICATION_ID = 7001

    /** The desk roles that review stories and so get the alarm. */
    private val REVIEWERS = setOf(JvRole.EDITOR, JvRole.NEWS_ADMIN, JvRole.SUPER_ADMIN)

    /** Intent extra carrying the tapped story's id into [MainActivity]. */
    const val EXTRA_ARTICLE_ID = "articleId"

    /** Set from a notification tap; the reader shell navigates to it and clears it. */
    private val _pendingArticleId = MutableStateFlow<String?>(null)
    val pendingArticleId: StateFlow<String?> = _pendingArticleId.asStateFlow()

    /**
     * Subscribes this device to the news topic. Safe to call on every launch -
     * FCM keeps the subscription across token refreshes, and re-subscribing is
     * a no-op server-side.
     */
    fun start(context: Context) {
        if (!FirebaseAvailability.init(context)) return
        ensureChannels(context)
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC)
            .addOnFailureListener { Log.w(TAG, "Topic subscribe failed: ${it.message}") }
    }

    /**
     * Joins or leaves the review alarm for whoever is signed in now. Called
     * whenever the desk session changes, so signing out stops the ringing.
     */
    fun syncReviewAlerts(context: Context, role: JvRole?) {
        if (!FirebaseAvailability.init(context)) return
        val messaging = FirebaseMessaging.getInstance()
        if (role in REVIEWERS) {
            ensureChannels(context)
            messaging.subscribeToTopic(REVIEW_TOPIC)
                .addOnFailureListener { Log.w(TAG, "Review subscribe failed: ${it.message}") }
        } else {
            messaging.unsubscribeFromTopic(REVIEW_TOPIC)
            NotificationManagerCompat.from(context).cancel(REVIEW_NOTIFICATION_ID)
        }
    }

    /**
     * A REVIEW_ALERT (ring) or REVIEW_UPDATE (silent correction) from the
     * review function. Shown only while a reviewer is signed in on this phone -
     * a topic subscription can outlive a sign-out made offline.
     */
    fun showReview(context: Context, data: Map<String, String>) {
        SessionStore.init(context)
        val manager = NotificationManagerCompat.from(context)
        if (SessionStore.session.value?.role !in REVIEWERS) {
            manager.cancel(REVIEW_NOTIFICATION_ID)
            return
        }
        val count = data["count"]?.toIntOrNull() ?: 0
        if (count <= 0) {
            manager.cancel(REVIEW_NOTIFICATION_ID)
            return
        }
        // A silent update only corrects an alert already showing.
        if (data["type"] == "REVIEW_UPDATE") return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannels(context)
        LanguagePreference.init(context)
        val telugu = LanguagePreference.current == AppLanguage.TELUGU
        val title = when {
            count == 1 && telugu -> "సమీక్ష కోసం 1 వార్త వేచి ఉంది"
            count == 1 -> "1 story waiting for review"
            telugu -> "సమీక్ష కోసం $count వార్తలు వేచి ఉన్నాయి"
            else -> "$count stories waiting for review"
        }
        val body = data["headline"].orEmpty().ifBlank {
            if (telugu) "ఆమోదించండి, తిరస్కరించండి లేదా వెనక్కి పంపండి" else "Approve, reject or send back"
        }
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tap = PendingIntent.getActivity(
            context, REVIEW_NOTIFICATION_ID, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REVIEW_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(reviewSound(context))
            // Same id every time, so a reminder replaces the last one - and
            // rings again, which is the point of it.
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(REVIEW_NOTIFICATION_ID, notification)
    }

    private fun reviewSound(context: Context): Uri =
        Uri.parse("android.resource://" + context.packageName + "/" + R.raw.jvoice_review_alert)

    /** Records the story a tapped notification points at, if any. */
    fun onIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ARTICLE_ID)?.takeIf { it.isNotBlank() } ?: return
        _pendingArticleId.value = id
    }

    fun consumePendingArticle() {
        _pendingArticleId.value = null
    }

    /**
     * Draws the notification for one FCM data payload, as sent by the function:
     * `titleEn`/`titleTe`, `messageEn`/`messageTe`, `articleId`, `type`.
     */
    fun show(context: Context, data: Map<String, String>) {
        LanguagePreference.init(context)
        val lang = LanguagePreference.current
        val title = pick(data, "title", lang)
        val body = pick(data, "message", lang)
        if (title.isBlank() && body.isBlank()) return

        // Without the runtime grant (API 33+) the post is refused - and on older
        // releases the grant is implicit.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "Notification permission not granted; dropping push")
            return
        }

        ensureChannels(context)
        val breaking = data["type"] == "BREAKING"
        val articleId = data["articleId"].orEmpty()

        val open = Intent(context, MainActivity::class.java).apply {
            // Reuse the running activity if there is one, so a tap while the app
            // is open lands in onNewIntent instead of stacking a second copy.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (articleId.isNotBlank()) putExtra(EXTRA_ARTICLE_ID, articleId)
        }
        val tap = PendingIntent.getActivity(
            context,
            (data["notificationId"] ?: articleId).hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, if (breaking) CHANNEL_BREAKING_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (breaking) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()

        // One notification per story: a re-published story replaces its earlier
        // alert rather than stacking a duplicate.
        val id = if (articleId.isNotBlank()) articleId.hashCode() else System.currentTimeMillis().toInt()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** `<key>En` / `<key>Te` from the payload, falling back to the other language. */
    private fun pick(data: Map<String, String>, key: String, lang: AppLanguage): String {
        val te = data["${key}Te"].orEmpty()
        val en = data["${key}En"].orEmpty()
        return when (lang) {
            AppLanguage.TELUGU -> te.ifBlank { en }
            AppLanguage.ENGLISH -> en.ifBlank { te }
        }
    }

    /** Channels are required from API 26; creating an existing one is a no-op. */
    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "News", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New stories from J Voice"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_BREAKING_ID, "Breaking news", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Breaking news alerts"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REVIEW_ID, "Stories to review", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Rings every 5 minutes while a filed story waits for a decision"
                setSound(
                    reviewSound(context),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
            }
        )
    }
}
