package com.jvoice.core.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the FCM data messages the `pushNewsNotification` function sends to
 * the `news` topic and hands them to [NewsPush] to draw.
 *
 * Data-only messages reach this service in the foreground and the background
 * alike, which is why the function sends them that way: a `notification`
 * payload would be drawn by the system in the background, in whatever language
 * the server guessed, and skip this code entirely.
 */
class JVoiceMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data.isEmpty()) return
        when (message.data["type"]) {
            "REVIEW_ALERT", "REVIEW_UPDATE" -> NewsPush.showReview(applicationContext, message.data)
            else -> NewsPush.show(applicationContext, message.data)
        }
    }

    override fun onNewToken(token: String) {
        // Topic subscriptions survive a token rotation, and nothing is keyed on
        // the token itself, so there is nothing to upload.
        Log.d("NewsPush", "FCM token refreshed")
    }
}
