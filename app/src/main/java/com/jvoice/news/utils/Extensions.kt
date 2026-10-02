package com.jvoice.news.utils

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.theme.StatusApproved
import com.jvoice.news.theme.StatusDraft
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSentBack
import com.jvoice.news.theme.StatusSubmitted
import com.jvoice.news.theme.StatusUnderReview
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LanguagePreference

/** "2 hours ago" style relative time, with a Telugu-friendly short form. */
fun Long.toRelativeTime(): String {
    val diff = System.currentTimeMillis() - this
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> if (hours == 1L) "1 hour ago" else "$hours hours ago"
        days < 7 -> if (days == 1L) "Yesterday" else "$days days ago"
        else -> toFullDate()
    }
}

fun Long.toFullDate(): String =
    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(this))

fun Long.toShortDate(): String =
    SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH).format(Date(this))

fun Int.toReadableCount(): String = when {
    this >= 100_000 -> String.format(Locale.ENGLISH, "%.1fL", this / 100_000f)
    this >= 1_000 -> String.format(Locale.ENGLISH, "%.1fK", this / 1_000f)
    else -> toString()
}

fun NewsStatus.color(): Color = when (this) {
    NewsStatus.DRAFT -> StatusDraft
    NewsStatus.SUBMITTED -> StatusSubmitted
    NewsStatus.UNDER_REVIEW -> StatusUnderReview
    NewsStatus.APPROVED -> StatusApproved
    NewsStatus.REJECTED -> StatusRejected
    NewsStatus.SENT_BACK -> StatusSentBack
    NewsStatus.PUBLISHED -> StatusPublished
}

/**
 * Local share sheet - uses the OS chooser, no backend involved.
 *
 * Shares in the language the reader is reading in, since that is the version
 * they chose to read and presumably the one their contacts read too.
 */
/** Where a shared story opens for someone without the app. */
const val WEB_BASE_URL = "https://jvoice-b4b2e.web.app"

/** The published privacy policy; the Play listing points here too. */
const val PRIVACY_POLICY_URL = "https://jvoicetelugu.com/privacy-policy"

/**
 * The newsroom's contact page and phone - the same details as the website's
 * /contact page (J Voice web/src/reader/Contact.jsx). Play's News & Magazines
 * policy requires them to be easy to find inside the app.
 */
const val CONTACT_PAGE_URL = "https://jvoicetelugu.com/contact"
const val CONTACT_PHONE = "+918919931583"
const val CONTACT_PHONE_DISPLAY = "+91 89199 31583"
const val CONTACT_EMAIL = "jvtelugu99@gmail.com"
const val PUBLISHER_ADDRESS = "Lyr Garden Road, beside Bus Stand, Thorrur, Telangana 506163"

/** The app on Google Play. Live once the listing is published. */
const val PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.jvoice.news"

fun articleLink(articleId: String) = "$WEB_BASE_URL/read/$articleId"

/** Off the composition: a share outlives the card that started it. */
private val shareScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

/**
 * Shares a story as its photo with the headline, summary and link as the
 * caption - the shape WhatsApp and friends render as a picture with text
 * under it. The photo is taken from Coil's cache (it is already on screen),
 * staged in the app's cache and handed over through the FileProvider.
 *
 * If the photo cannot be had - offline, no image, a decode failure - the
 * share still goes out as text. The reader tapped Share; something must
 * appear.
 */
fun Context.shareArticle(
    article: NewsArticle,
    language: AppLanguage = LanguagePreference.current
) {
    val headline = article.headline.get(language)
    val text = buildString {
        append(headline)
        append("\n\n")
        append(article.shortDescription.get(language))
        append("\n\nRead on J Voice: ")
        append(articleLink(article.id))
        append("\nGet the app: ")
        append(PLAY_STORE_URL)
    }
    val context = applicationContext
    shareScope.launch {
        val image = if (article.imageUrl.isNotBlank()) context.stageShareImage(article) else null
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_SUBJECT, headline)
            putExtra(Intent.EXTRA_TEXT, text)
            if (image != null) {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, image)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                type = "text/plain"
            }
        }
        context.startActivity(
            Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Fetches the cover through Coil and writes it to cache/shares; null on any failure. */
private suspend fun Context.stageShareImage(article: NewsArticle): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(this@stageShareImage)
                .data(article.imageUrl)
                // A hardware bitmap cannot be read back for compression.
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request) as? SuccessResult ?: return@withContext null
            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null
            val dir = File(cacheDir, "shares").apply { mkdirs() }
            // One file per story; re-sharing overwrites rather than piling up.
            val file = File(dir, article.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            FileProvider.getUriForFile(this@stageShareImage, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            Log.w("Share", "image share fell back to text: ${e.message}")
            null
        }
    }

fun String.toTagList(): List<String> =
    split(",", " ")
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()
