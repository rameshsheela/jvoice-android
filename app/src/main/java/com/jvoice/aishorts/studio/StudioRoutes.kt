package com.jvoice.aishorts.studio

import androidx.compose.runtime.staticCompositionLocalOf

/** Routes of the real AI Shorts screens (on the studio backend). */
object StudioRoutes {
    const val MINE = "shorts/mine"
    const val CREATE = "shorts/create?articleId={articleId}"
    const val DETAIL = "shorts/detail/{shortId}"
    const val REVIEW = "shorts/review"
    const val ARG_ARTICLE_ID = "articleId"
    const val ARG_SHORT_ID = "shortId"

    fun create(articleId: String? = null) = "shorts/create?articleId=" + (articleId ?: "")
    fun detail(shortId: String) = "shorts/detail/$shortId"
}

/** Opens the AI Shorts screens from anywhere in the news NavHost without new parameters. */
class StudioNav(
    val openMine: () -> Unit,
    val openCreate: (articleId: String?) -> Unit,
    val openReview: () -> Unit
)

val LocalStudioNav = staticCompositionLocalOf<StudioNav?> { null }
