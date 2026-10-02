package com.jvoice.core.data

import com.google.firebase.firestore.FieldValue
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.NotificationItem
import com.jvoice.news.data.model.NotificationType
import com.jvoice.news.data.model.UserRole

/**
 * Firestore ↔ model for the News module.
 *
 * Enums are stored by `name` (`"PUBLISHED"`, not an ordinal). Ordinals are
 * smaller and completely unmaintainable: inserting a value into the middle of
 * [NewsStatus] would silently reinterpret every stored document.
 *
 * Timestamps are plain epoch millis rather than Firestore `Timestamp` objects,
 * because the models already use `Long` and the app's relative-time formatting
 * works on millis. The one exception is a *server* timestamp on create, which is
 * written as [FieldValue.serverTimestamp] and then read back as millis — see
 * [articleToMap].
 */

/* ================================================================== article */

fun NewsArticle.toMap(): Map<String, Any?> = mapOf(
    "headline" to headline.toMap(),
    "shortDescription" to shortDescription.toMap(),
    "content" to content.toMap(),
    "categoryId" to categoryId,
    "location" to location,
    "imageUrl" to imageUrl,
    "photoUrls" to photoUrls,
    "videoUrls" to videoUrls,
    "detailEnabled" to detailEnabled,
    "notifyReaders" to notifyReaders,
    "tags" to tags.toMapList(),
    "isBreaking" to isBreaking,
    "isFeatured" to isFeatured,
    "isTrending" to isTrending,
    "status" to status.name,
    "reporterId" to reporterId,
    "reporterName" to reporterName,
    "reporterAvatarUrl" to reporterAvatarUrl,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt,
    "publishedAt" to publishedAt,
    "rejectionReason" to rejectionReason?.toMap(),
    "editorNote" to editorNote?.toMap(),
    "views" to views,
    "reportCount" to reportCount,
    "likes" to likes,
    "dislikes" to dislikes,
    "comments" to comments,
    // Denormalised so the reader feed can order published stories without
    // reading every draft: Firestore cannot order on a field some documents
    // lack, and publishedAt is null until publication.
    "sortAt" to (publishedAt ?: createdAt)
)

fun articleFrom(id: String, data: Map<String, Any?>): NewsArticle {
    val createdAt = data.long("createdAt")
    return NewsArticle(
        id = id,
        headline = localizedFrom(data["headline"]),
        shortDescription = localizedFrom(data["shortDescription"]),
        content = localizedFrom(data["content"]),
        categoryId = data.str("categoryId"),
        location = data.str("location"),
        imageUrl = data.str("imageUrl"),
        photoUrls = data.strList("photoUrls"),
        videoUrls = data.strList("videoUrls"),
        tags = localizedListFrom(data["tags"]),
        isBreaking = data.bool("isBreaking"),
        isFeatured = data.bool("isFeatured"),
        isTrending = data.bool("isTrending"),
        status = data.enum("status", NewsStatus.DRAFT),
        reporterId = data.str("reporterId"),
        reporterName = data.str("reporterName"),
        reporterAvatarUrl = data.str("reporterAvatarUrl"),
        createdAt = createdAt,
        // Defaults to createdAt rather than 0, so a record written without it
        // does not sort to the beginning of time.
        updatedAt = data.long("updatedAt", createdAt),
        publishedAt = data.longOrNull("publishedAt"),
        rejectionReason = data["rejectionReason"]?.let { localizedFrom(it) },
        editorNote = data["editorNote"]?.let { localizedFrom(it) },
        views = data.int("views"),
        reportCount = data.int("reportCount"),
        likes = data.int("likes"),
        dislikes = data.int("dislikes"),
        comments = data.int("comments"),
        // Absent means on: every story before the flag existed opens.
        detailEnabled = data.bool("detailEnabled", true),
        // Absent means on, as the console treats it.
        notifyReaders = data.bool("notifyReaders", true)
    )
}

/* ================================================================= category */

fun Category.toMap(): Map<String, Any?> = mapOf(
    "name" to name.toMap(),
    "emoji" to emoji,
    "isEnabled" to isEnabled
)

fun categoryFrom(id: String, data: Map<String, Any?>): Category = Category(
    id = id,
    name = localizedFrom(data["name"]),
    emoji = data.str("emoji", "📰"),
    // Missing means enabled: a category added by hand without the flag should
    // appear, not silently vanish from the app.
    isEnabled = data.bool("isEnabled", default = true)
)

/* ============================================================= notification */

fun NotificationItem.toMap(): Map<String, Any?> = mapOf(
    "title" to title.toMap(),
    "message" to message.toMap(),
    "timeMillis" to timeMillis,
    "type" to type.name,
    "isRead" to isRead,
    "articleId" to articleId,
    // Stored as the enum name or null. Null means "everyone", which is what the
    // Firestore rule for this collection keys its public read on.
    "targetRole" to targetRole?.name
)

fun notificationFrom(id: String, data: Map<String, Any?>): NotificationItem = NotificationItem(
    id = id,
    title = localizedFrom(data["title"]),
    message = localizedFrom(data["message"]),
    timeMillis = data.long("timeMillis"),
    type = data.enum("type", NotificationType.GENERAL),
    isRead = data.bool("isRead"),
    articleId = data.strOrNull("articleId"),
    targetRole = (data["targetRole"] as? String)?.let { raw ->
        UserRole.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }
)

/* ======================================================= atomic field updates */

/**
 * An atomic increment, for counters several clients touch at once.
 *
 * View counts and reader reports are written by every reader independently. A
 * read-modify-write would lose counts under concurrency; [FieldValue.increment]
 * is applied server-side.
 */
fun increment(field: String, by: Long = 1): Map<String, Any?> =
    mapOf(field to FieldValue.increment(by))
