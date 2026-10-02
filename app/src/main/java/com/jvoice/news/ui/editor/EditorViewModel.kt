package com.jvoice.news.ui.editor

import androidx.lifecycle.ViewModel
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LocalizedText
import androidx.lifecycle.viewModelScope
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.repository.NewsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorStats(
    val awaitingReview: Int = 0,
    val approvedToday: Int = 0,
    val rejectedToday: Int = 0,
    val publishedToday: Int = 0
)

/** Working copy of the article the editor is currently reviewing. */
/**
 * The editor's working copy of a story, in both languages.
 *
 * The review screen edits one language at a time through a tab, but the draft
 * always carries the pair - an editor fixing the Telugu headline must not drop
 * the English one that was already filed.
 */
data class ReviewDraft(
    val id: String = "",
    val headline: LocalizedText = LocalizedText.EMPTY,
    val shortDescription: LocalizedText = LocalizedText.EMPTY,
    val content: LocalizedText = LocalizedText.EMPTY,
    val categoryId: String = "",
    val tags: List<LocalizedText> = emptyList(),
    /** Opens a full article page when tapped - off for stories complete on the card. */
    val detailEnabled: Boolean = true,
    /** Send a notification to readers' phones when published. */
    val notifyReaders: Boolean = true
) {
    /** The fields the language tabs report completeness for. */
    val localizedFields: List<LocalizedText>
        get() = listOf(headline, shortDescription, content)
}

class EditorViewModel : ViewModel() {

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _draft = MutableStateFlow(ReviewDraft())
    val draft: StateFlow<ReviewDraft> = _draft.asStateFlow()

    val categories: StateFlow<List<Category>> = NewsRepository.categories

    val queue: StateFlow<List<NewsArticle>> = NewsRepository.articles
        .map { NewsRepository.reviewQueue() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyHandled: StateFlow<List<NewsArticle>> = NewsRepository.articles
        .map { list ->
            list.filter {
                it.status == NewsStatus.PUBLISHED ||
                    it.status == NewsStatus.APPROVED ||
                    it.status == NewsStatus.REJECTED ||
                    it.status == NewsStatus.SENT_BACK
            }.sortedByDescending { it.updatedAt }.take(8)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<EditorStats> = NewsRepository.articles
        .map {
            EditorStats(
                awaitingReview = NewsRepository.countPending(),
                approvedToday = NewsRepository.approvedToday(),
                rejectedToday = NewsRepository.rejectedToday(),
                publishedToday = NewsRepository.publishedToday()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorStats())

    init {
        viewModelScope.launch {
            delay(500)
            _isLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            delay(800)
            _isRefreshing.value = false
        }
    }

    fun categoryName(id: String) = NewsRepository.categoryName(id)

    fun articleById(id: String) = NewsRepository.articleById(id)

    fun loadDraft(articleId: String) {
        val article = NewsRepository.articleById(articleId) ?: return
        NewsRepository.markUnderReview(articleId)
        _draft.value = ReviewDraft(
            id = article.id,
            headline = article.headline,
            shortDescription = article.shortDescription,
            content = article.content,
            categoryId = article.categoryId,
            tags = article.tags,
            detailEnabled = article.detailEnabled,
            notifyReaders = article.notifyReaders
        )
    }

    fun updateDraft(transform: (ReviewDraft) -> ReviewDraft) {
        _draft.value = transform(_draft.value)
    }

/**
     * Adds a tag for one language only. The other side is left blank rather than
     * mirrored, so the tag chip shows as untranslated instead of pretending the
     * English word is also the Telugu one.
     */
    fun addTag(tag: String, language: AppLanguage) {
        val clean = tag.trim().removePrefix("#")
        if (clean.isBlank()) return
        val existing = _draft.value.tags
        // Same word in the same language is a duplicate; the same word in the
        // other language is a translation, so fill that side in place.
        val alreadyThere = existing.any { it.rawFor(language).equals(clean, ignoreCase = true) }
        if (alreadyThere) return
        val translating = existing.indexOfFirst {
            it.rawFor(language).isBlank() && it.rawFor(language.other).isNotBlank() &&
                it.rawFor(language.other).equals(clean, ignoreCase = true)
        }
        _draft.value = if (translating >= 0) {
            _draft.value.copy(
                tags = existing.toMutableList().also {
                    it[translating] = it[translating].with(language, clean)
                }
            )
        } else {
            _draft.value.copy(tags = existing + LocalizedText.EMPTY.with(language, clean))
        }
    }

    fun removeTag(tag: LocalizedText) {
        _draft.value = _draft.value.copy(tags = _draft.value.tags - tag)
    }

    private fun persistEdits() {
        val draft = _draft.value
        if (draft.id.isBlank()) return
        NewsRepository.applyEditorEdits(
            articleId = draft.id,
            headline = draft.headline.trimmed(),
            shortDescription = draft.shortDescription.trimmed(),
            content = draft.content.trimmed(),
            categoryId = draft.categoryId,
            tags = draft.tags,
            detailEnabled = draft.detailEnabled,
            notifyReaders = draft.notifyReaders
        )
    }

    fun saveEdits() = persistEdits()

    /** Approve + publish: the article immediately becomes visible to readers. */
    suspend fun approveAndPublish(): String? {
        persistEdits()
        return messageFor(
            NewsRepository.approveArticle(_draft.value.id, publishNow = true, notifyReaders = _draft.value.notifyReaders),
            published = true
        )
    }

    suspend fun approveOnly(): String? {
        persistEdits()
        return messageFor(NewsRepository.approveArticle(_draft.value.id, publishNow = false), published = false)
    }

    /** Null when done; otherwise what to tell the editor. */
    private fun messageFor(result: com.jvoice.core.data.Firestore.WriteResult, published: Boolean): String? =
        when (result) {
            is com.jvoice.core.data.Firestore.WriteResult.Saved -> null
            is com.jvoice.core.data.Firestore.WriteResult.Queued ->
                "No internet - it will " + (if (published) "publish" else "be approved") + " when the phone is back online"
            is com.jvoice.core.data.Firestore.WriteResult.Failed -> result.message
        }

/**
     * Rejection reasons and editor notes are read by the reporter, who may well
     * work in the other language, so they travel as a pair like the copy does.
     * The desk types one side; the reporter sees whichever is filled.
     */
    fun reject(reason: LocalizedText) {
        persistEdits()
        NewsRepository.rejectArticle(_draft.value.id, reason.trimmed())
    }

    fun sendBack(note: LocalizedText) {
        persistEdits()
        NewsRepository.sendBackForCorrection(_draft.value.id, note.trimmed())
    }
}
