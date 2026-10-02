package com.jvoice.news.data.model

/**
 * Reader engagement on an article: reactions and comments.
 *
 * Additive models - the existing news models are untouched.
 */

/** The signed-in reader's own reaction to one article. */
enum class Reaction { NONE, LIKE, DISLIKE }

data class ArticleComment(
    val id: String,
    val articleId: String,
    val authorName: String,
    val text: String,
    val timeMillis: Long,
    val likes: Int = 0,
    /** The comment this one replies to; null for a top-level comment. */
    val parentId: String? = null,
    /** The device that posted it - what [isOwn] is judged from. */
    val deviceId: String = "",
    val isOwn: Boolean = false
)

data class ArticleEngagement(
    val articleId: String,
    val likes: Int = 0,
    val dislikes: Int = 0,
    /** Top-level comments and replies together. */
    val comments: Int = 0,
    val myReaction: Reaction = Reaction.NONE
)

/** Why a reader is reporting a story. */
enum class ReportReason(val label: String, val hint: String) {
    INCORRECT_FACTS("Wrong facts", "A name, number, date or claim is incorrect"),
    MISLEADING_HEADLINE("Misleading title", "The headline does not match the story"),
    OUTDATED("Outdated", "This has since changed or been corrected"),
    OFFENSIVE("Offensive", "Hateful, abusive or inappropriate content"),
    SPAM("Spam / ad", "Promotional content posing as news"),
    DUPLICATE("Duplicate", "The same story is already published"),
    OTHER("Other", "Tell us what is wrong in the box below")
}

data class ArticleReport(
    val id: String,
    val articleId: String,
    val reason: ReportReason,
    /** The reader's suggested correction, if they offered one. */
    val suggestion: String = "",
    val reportedBy: String = "",
    val timeMillis: Long = 0L
)
