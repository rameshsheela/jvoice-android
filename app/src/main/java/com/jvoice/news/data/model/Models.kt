package com.jvoice.news.data.model


import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.anyMatches

/**
 * J Voice - Module 1 (News) prototype models.
 * All data backed by local dummy/demo content. No backend, no Firebase.
 *
 * Every reader-visible string is a [LocalizedText] rather than a [String]: the
 * story is written once in both languages and the language is chosen when it is
 * drawn, not when it is filed.
 */

enum class UserRole(val label: String, val teluguLabel: String) {
    READER("Reader", "పాఠకుడు"),
    REPORTER("Reporter", "రిపోర్టర్"),
    EDITOR("Editor", "ఎడిటర్"),
    NEWS_ADMIN("News Admin", "న్యూస్ అడ్మిన్"),
    SUPER_ADMIN("Super Admin", "సూపర్ అడ్మిన్");

    /** The role name in both languages. `name` is taken by [Enum]. */
    val localizedLabel: LocalizedText get() = LocalizedText(en = label, te = teluguLabel)
}

enum class NewsStatus(val label: String, val teluguLabel: String) {
    DRAFT("Draft", "డ్రాఫ్ట్"),
    SUBMITTED("Submitted", "సమర్పించినది"),
    UNDER_REVIEW("Under Review", "సమీక్షలో"),
    APPROVED("Approved", "ఆమోదించినది"),
    REJECTED("Rejected", "తిరస్కరించినది"),
    SENT_BACK("Sent Back", "వెనక్కి పంపినది"),
    PUBLISHED("Published", "ప్రచురించినది");

    val localizedLabel: LocalizedText get() = LocalizedText(en = label, te = teluguLabel)

    val isVisibleToReader: Boolean get() = this == PUBLISHED
    val isEditableByReporter: Boolean get() = this == DRAFT || this == REJECTED || this == SENT_BACK
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "+91 90000 00000",
    val role: UserRole,
    val location: String = "Hyderabad",
    val avatarUrl: String = "",
    val isActive: Boolean = true,
    val joinedOn: String = "01 Jan 2025",
    /** Sign-in id, also the employee id - `jv01r001`. Empty for older accounts. */
    val loginId: String = ""
)

data class Reporter(
    val userId: String,
    val name: String,
    val assignedLocation: String,
    val beat: String = "General",
    val isActive: Boolean = true,
    val avatarUrl: String = "",
    /** Sign-in id, also the employee id - `jv01r001`. */
    val loginId: String = ""
)

/** Derived, computed live from the repository article list. */
data class ReporterStats(
    val reporter: Reporter,
    val total: Int,
    val approved: Int,
    val rejected: Int,
    val pending: Int,
    val published: Int
)

data class Category(
    val id: String,
    val name: LocalizedText,
    val emoji: String = "📰",
    val isEnabled: Boolean = true
) {
    /** Both languages on one line - the desk's category list wants this. */
    val displayName: String get() = name.inline()
}

data class NewsArticle(
    val id: String,
    val headline: LocalizedText,
    val shortDescription: LocalizedText,
    val content: LocalizedText,
    val categoryId: String,
    val location: String,
    val imageUrl: String,
    /** Additional photos supplied with the story. The cover [imageUrl] stays as-is. */
    val photoUrls: List<String> = emptyList(),
    /** Videos supplied with the story. Originals are never modified. */
    val videoUrls: List<String> = emptyList(),
    val tags: List<LocalizedText> = emptyList(),
    val isBreaking: Boolean = false,
    val isFeatured: Boolean = false,
    val isTrending: Boolean = false,
    val status: NewsStatus = NewsStatus.DRAFT,
    val reporterId: String,
    val reporterName: String,
    /**
     * The reporter's photo, copied onto the story rather than looked up.
     *
     * Denormalised deliberately: reporter profiles live in the Realtime Database
     * under `users/`, which the rules keep closed to anonymous readers - and every
     * reader of this field is anonymous. Copying the URL at write time is the only
     * way the byline photo can render at all, and it has the side benefit that a
     * story keeps the face that filed it after the reporter changes their picture.
     */
    val reporterAvatarUrl: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val publishedAt: Long? = null,
    val rejectionReason: LocalizedText? = null,
    val editorNote: LocalizedText? = null,
    val views: Int = 0,
    val reportCount: Int = 0,
    /** Reader reactions and comment count - moved by readers, see EngagementRepository. */
    val likes: Int = 0,
    val dislikes: Int = 0,
    val comments: Int = 0,
    /**
     * Whether the story opens a detail screen. Off for stories that are
     * complete on the card - a video with a caption, say - where a tap
     * through would only show the same thing again.
     */
    val detailEnabled: Boolean = true,
    /** Whether publishing the story sends a notification to readers' phones. */
    val notifyReaders: Boolean = true
) {
    /** Cover photo first, then any extras. */
    val allPhotos: List<String>
        get() = (listOf(imageUrl) + photoUrls).filter { it.isNotBlank() }.distinct()

    /**
     * True when this story is fully translated. The desk badges the ones that are
     * not; readers never see the difference because [headline] and friends fall
     * back to whichever language was written.
     */
    val isFullyTranslated: Boolean
        get() = headline.isComplete && shortDescription.isComplete && content.isComplete

    /**
     * Searches both languages at once, whichever one the reader is browsing in -
     * typing "metro" has to find the story even when the feed is in Telugu.
     */
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isBlank()) return true
        return headline.matches(q) ||
            shortDescription.matches(q) ||
            content.matches(q) ||
            tags.anyMatches(q) ||
            location.contains(q, ignoreCase = true) ||
            reporterName.contains(q, ignoreCase = true)
    }
}

enum class NotificationType { BREAKING, APPROVAL, REJECTION, SYSTEM, GENERAL }

data class NotificationItem(
    val id: String,
    val title: LocalizedText,
    val message: LocalizedText,
    val timeMillis: Long,
    val type: NotificationType = NotificationType.GENERAL,
    val isRead: Boolean = false,
    val articleId: String? = null,
    val targetRole: UserRole? = null
)

data class RolePermission(
    val role: UserRole,
    val permissions: List<String>
)

data class SystemSettings(
    val appName: String = "J Voice",
    val defaultLocation: String = "Hyderabad",
    val breakingNewsEnabled: Boolean = true,
    val breakingNewsAutoExpiryHours: Int = 12,
    val pushNotificationsEnabled: Boolean = true,
    val emailDigestEnabled: Boolean = false,
    val teluguFirstUi: Boolean = true,
    val commentsEnabled: Boolean = false,
    val autoModerationEnabled: Boolean = true,
    val profanityFilterEnabled: Boolean = true,
    val maxReportsBeforeAutoHide: Int = 5
)

/** Generic UI state used by every screen for loading / empty / error handling. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Empty(val message: String) : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
}
