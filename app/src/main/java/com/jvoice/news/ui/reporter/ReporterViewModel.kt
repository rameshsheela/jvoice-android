package com.jvoice.news.ui.reporter

import com.jvoice.core.data.Firestore
import com.jvoice.core.data.DraftStore
import com.jvoice.core.data.StoryMedia
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.NotificationItem
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.data.repository.NewsRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.lt

data class ReporterStatsUi(
    val total: Int = 0,
    val drafts: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val rejected: Int = 0,
    val published: Int = 0,
    val sentBack: Int = 0
)

/** Editable form state for Create / Edit News. */
/**
 * The reporter's filing form, bilingual.
 *
 * Validation runs against "is either language filled", not "is this language
 * filled". A reporter filing in Telugu at 2am should not be blocked on English
 * copy - the story goes out in Telugu and the desk adds the translation later.
 * [needsTranslation] is what surfaces that debt.
 *
 * `tagsText` stays a single comma-separated string per language rather than a
 * list, because that is what the text box holds; it is split on save.
 */
data class ArticleForm(
    val id: String? = null,
    val headline: LocalizedText = LocalizedText.EMPTY,
    val shortDescription: LocalizedText = LocalizedText.EMPTY,
    val content: LocalizedText = LocalizedText.EMPTY,
    val categoryId: String = "",
    val location: String = "Hyderabad",
    val imageUrl: String = "",
    val photosText: String = "",
    val videosText: String = "",
    /** A YouTube link pasted by the reporter - saved as the story's first video. */
    val youtubeUrl: String = "",
    val tagsText: LocalizedText = LocalizedText.EMPTY,
    val isBreaking: Boolean = false
) {
    /** The fields the language tabs report completeness for. */
    val localizedFields: List<LocalizedText>
        get() = listOf(headline, shortDescription, content)

    val headlineError: LocalizedText?
        get() = if (headline.isBlank) lt("Headline is required", "శీర్షిక తప్పనిసరి") else null
    val descriptionError: LocalizedText?
        get() = if (shortDescription.isBlank) lt("Short description is required", "సంక్షిప్త వివరణ తప్పనిసరి") else null

    /**
     * Length is checked on the longest version written, not on a chosen language:
     * a filled Telugu body and an empty English one is a valid story, and
     * measuring the empty side would reject it.
     */
    val contentError: LocalizedText?
        get() = if (maxOf(content.en.length, content.te.length) < 20)
            lt("Article should be at least 20 characters", "కథనం కనిష్టం 20 అక్షరాలు ఉండాలి") else null

    val categoryError: LocalizedText?
        get() = if (categoryId.isBlank()) lt("Pick a category", "విభాగం ఎంచుకోండి") else null

    val isValid: Boolean
        get() = headlineError == null && descriptionError == null &&
            contentError == null && categoryError == null

    /** Languages still missing from at least one of the copy fields. */
    val needsTranslation: List<AppLanguage>
        get() = AppLanguage.entries.filter { language ->
            localizedFields.any { it.rawFor(language).isBlank() }
        }
}

/** youtube.com/watch, youtu.be, /shorts/, /embed/ and /live/ links. */
fun isYouTube(url: String): Boolean =
    Regex("^(https?://)?((www|m|music)\\.)?(youtube\\.com/(watch\\?|shorts/|embed/|live/)|youtu\\.be/)\\S+", RegexOption.IGNORE_CASE)
        .containsMatchIn(url.trim())

class ReporterViewModel : ViewModel() {

    private val reporterUser get() = NewsRepository.currentUser.value

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _form = MutableStateFlow(ArticleForm())
    val form: StateFlow<ArticleForm> = _form.asStateFlow()

    val categories: StateFlow<List<Category>> = NewsRepository.categories
    val locations: List<String> = NewsRepository.locations

    val myArticles: StateFlow<List<NewsArticle>> = combine(
        NewsRepository.articles,
        NewsRepository.currentUser
    ) { articles, user ->
        articles.filter { it.reporterId == (user?.id ?: "") }.sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats: StateFlow<ReporterStatsUi> = myArticles.map { list ->
        ReporterStatsUi(
            total = list.size,
            drafts = list.count { it.status == NewsStatus.DRAFT },
            pending = list.count { it.status == NewsStatus.SUBMITTED || it.status == NewsStatus.UNDER_REVIEW },
            approved = list.count { it.status == NewsStatus.APPROVED || it.status == NewsStatus.PUBLISHED },
            rejected = list.count { it.status == NewsStatus.REJECTED },
            published = list.count { it.status == NewsStatus.PUBLISHED },
            sentBack = list.count { it.status == NewsStatus.SENT_BACK }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReporterStatsUi())

    val notifications: StateFlow<List<NotificationItem>> = NewsRepository.notifications
        .map { NewsRepository.notificationsFor(UserRole.REPORTER) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun articlesWithStatus(status: NewsStatus) = myArticles.value.filter { it.status == status }

    // ------------------------------------------------------------------ form
    /** True when [startNewArticle] brought back a story saved on the phone. */
    private val _restoredDraft = MutableStateFlow(false)
    val restoredDraft: StateFlow<Boolean> = _restoredDraft.asStateFlow()

    fun startNewArticle() {
        val fresh = ArticleForm(
            categoryId = NewsRepository.categories.value.firstOrNull { it.isEnabled }?.id ?: "",
            location = reporterUser?.location ?: "Hyderabad"
        )
        val saved = DraftStore.load(reporterUser?.id.orEmpty())?.let { fromDraft(it, fresh) }
        _restoredDraft.value = saved != null
        _form.value = saved ?: fresh
    }

    /** The unsent story this reporter was writing, or null. */
    private fun fromDraft(j: org.json.JSONObject, base: ArticleForm): ArticleForm? {
        fun lt(key: String) = LocalizedText(en = j.optString(key + "En"), te = j.optString(key + "Te"))
        val form = base.copy(
            headline = lt("headline"),
            shortDescription = lt("shortDescription"),
            content = lt("content"),
            tagsText = lt("tags"),
            categoryId = j.optString("categoryId").ifBlank { base.categoryId },
            location = j.optString("location").ifBlank { base.location },
            imageUrl = j.optString("imageUrl"),
            photosText = j.optString("photosText"),
            videosText = j.optString("videosText"),
            youtubeUrl = j.optString("youtubeUrl"),
            isBreaking = j.optBoolean("isBreaking")
        )
        val empty = form.headline.isBlank && form.shortDescription.isBlank && form.content.isBlank &&
            form.imageUrl.isBlank() && form.videosText.isBlank() && form.youtubeUrl.isBlank()
        return if (empty) null else form
    }

    private fun persistDraft(f: ArticleForm) {
        // Only a new story: an edit of a filed one is already on the server.
        if (f.id != null) return
        DraftStore.save(
            reporterUser?.id.orEmpty(),
            mapOf(
                "headlineEn" to f.headline.en, "headlineTe" to f.headline.te,
                "shortDescriptionEn" to f.shortDescription.en, "shortDescriptionTe" to f.shortDescription.te,
                "contentEn" to f.content.en, "contentTe" to f.content.te,
                "tagsEn" to f.tagsText.en, "tagsTe" to f.tagsText.te,
                "categoryId" to f.categoryId, "location" to f.location,
                "imageUrl" to f.imageUrl, "photosText" to f.photosText, "videosText" to f.videosText,
                "youtubeUrl" to f.youtubeUrl, "isBreaking" to f.isBreaking
            )
        )
    }

    /** Throws away the story saved on the phone and starts clean. */
    fun discardDraft() {
        DraftStore.clear(reporterUser?.id.orEmpty())
        _restoredDraft.value = false
        startNewArticle()
    }

    fun loadForEdit(articleId: String) {
        val article = NewsRepository.articleById(articleId) ?: return startNewArticle()
        _form.value = ArticleForm(
            id = article.id,
            headline = article.headline,
            shortDescription = article.shortDescription,
            content = article.content,
            categoryId = article.categoryId,
            location = article.location,
            imageUrl = article.imageUrl,
            photosText = article.photoUrls.joinToString("\n"),
            // A YouTube link has its own box; uploaded clips stay in the list.
            videosText = article.videoUrls.filterNot(::isYouTube).joinToString("\n"),
            youtubeUrl = article.videoUrls.firstOrNull(::isYouTube).orEmpty(),
            // Rebuilt per language so each box shows only its own tags.
            tagsText = LocalizedText(
                en = article.tags.mapNotNull { it.en.ifBlank { null } }.joinToString(", "),
                te = article.tags.mapNotNull { it.te.ifBlank { null } }.joinToString(", ")
            ),
            isBreaking = article.isBreaking
        )
    }

    fun updateForm(transform: (ArticleForm) -> ArticleForm) {
        _form.value = transform(_form.value)
        persistDraft(_form.value)
    }

    /**
     * Stores the story and waits for J Voice to accept it.
     *
     * @return null when it was saved (or queued to send once the phone is back
     * online), otherwise the message to show - the form stays open so nothing
     * typed is lost.
     */
    suspend fun save(submit: Boolean): String? {
        val current = _form.value
        if (submit && !current.isValid) return "Please fix the highlighted fields"
        // A draft only needs a headline in one language to be worth keeping.
        if (!submit && current.headline.isBlank) return "Add a headline before saving"
        if (current.youtubeUrl.isNotBlank() && !isYouTube(current.youtubeUrl.trim())) {
            return "That is not a YouTube link - paste the link from YouTube's Share button"
        }

        val user = reporterUser ?: return "You are signed out. Sign in again."
        val result = NewsRepository.createOrUpdateArticle(
            existingId = current.id,
            headline = current.headline.trimmed(),
            shortDescription = current.shortDescription.trimmed(),
            content = current.content.trimmed(),
            categoryId = current.categoryId,
            location = current.location,
            imageUrl = current.imageUrl.trim(),
            tags = zipTags(current.tagsText),
            isBreaking = current.isBreaking,
            submit = submit,
            photoUrls = current.photosText.lines().map { it.trim() }.filter { it.isNotBlank() },
            videoUrls = listOfNotNull(current.youtubeUrl.trim().ifBlank { null }) +
                current.videosText.lines().map { it.trim() }.filter { it.isNotBlank() },
            reporterId = user.id,
            reporterName = user.name
        )
        val failed = (result as? Firestore.WriteResult.Failed)?.message
        // J Voice has it (or has it queued to send): the phone copy is done.
        if (failed == null && current.id == null) {
            DraftStore.clear(user.id)
            _restoredDraft.value = false
        }
        return failed
    }

    fun deleteDraft(articleId: String) = NewsRepository.deleteDraft(articleId)

    /**
     * Fills the other language from [from]: headline, short description, the
     * article and the tags. Overwrites what the other side had - the screen asks
     * first when there is something to lose.
     *
     * @return null on success, otherwise the message to show.
     */
    suspend fun translate(from: AppLanguage): String? {
        val to = if (from == AppLanguage.TELUGU) AppLanguage.ENGLISH else AppLanguage.TELUGU
        val f = _form.value
        val source = listOf(f.headline, f.shortDescription, f.content, f.tagsText).map { it.rawFor(from) }
        if (source.all { it.isBlank() }) return "Write the story first, then translate it"
        val result = StoryMedia.translate(source, code(from), code(to))
        val out = result.getOrElse { return it.message ?: "Could not translate" }
        _form.value = _form.value.let {
            it.copy(
                headline = it.headline.with(to, out[0]),
                shortDescription = it.shortDescription.with(to, out[1]),
                content = it.content.with(to, out[2]),
                tagsText = it.tagsText.with(to, out[3])
            )
        }
        persistDraft(_form.value)
        return null
    }

    private fun code(language: AppLanguage) = if (language == AppLanguage.TELUGU) "te" else "en"

    /** Adds uploaded photos: the first becomes the cover if there is none yet. */
    fun addPhotos(urls: List<String>) {
        if (urls.isEmpty()) return
        _form.value = _form.value.let { f ->
            val cover = f.imageUrl.ifBlank { urls.first() }
            val extra = (f.photosText.lines().map { it.trim() }.filter { it.isNotBlank() } +
                urls.filter { it != cover }).distinct()
            f.copy(imageUrl = cover, photosText = extra.joinToString("\n"))
        }
        persistDraft(_form.value)
    }

    fun addVideo(url: String) {
        _form.value = _form.value.let { f ->
            val list = f.videosText.lines().map { it.trim() }.filter { it.isNotBlank() } + url
            f.copy(videosText = list.distinct().joinToString("\n"))
        }
        persistDraft(_form.value)
    }

    /** Removes one photo; removing the cover promotes the next photo to cover. */
    fun removePhoto(url: String) {
        _form.value = _form.value.let { f ->
            val extra = f.photosText.lines().map { it.trim() }.filter { it.isNotBlank() && it != url }
            if (f.imageUrl == url) {
                f.copy(imageUrl = extra.firstOrNull().orEmpty(), photosText = extra.drop(1).joinToString("\n"))
            } else {
                f.copy(photosText = extra.joinToString("\n"))
            }
        }
        persistDraft(_form.value)
    }

    fun makeCover(url: String) {
        _form.value = _form.value.let { f ->
            if (f.imageUrl == url) return@let f
            val extra = (listOfNotNull(f.imageUrl.ifBlank { null }) +
                f.photosText.lines().map { it.trim() }.filter { it.isNotBlank() && it != url })
            f.copy(imageUrl = url, photosText = extra.joinToString("\n"))
        }
        persistDraft(_form.value)
    }

    fun removeVideo(url: String) {
        _form.value = _form.value.let { f ->
            f.copy(videosText = f.videosText.lines().map { it.trim() }.filter { it.isNotBlank() && it != url }.joinToString("\n"))
        }
        persistDraft(_form.value)
    }

    fun canEdit(article: NewsArticle) = article.status.isEditableByReporter

    /**
     * Pairs up the two comma-separated tag boxes by position.
     *
     * Position is the only signal available - the boxes are free text, so there
     * is nothing to join on. Any surplus on one side becomes a single-language
     * tag rather than being dropped, which is the honest outcome: the tag exists,
     * its translation does not yet.
     */
    private fun zipTags(text: LocalizedText): List<LocalizedText> {
        fun split(value: String) =
            value.split(",").map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }

        val en = split(text.en)
        val te = split(text.te)
        return (0 until maxOf(en.size, te.size)).map { index ->
            LocalizedText(
                en = en.getOrElse(index) { "" },
                te = te.getOrElse(index) { "" }
            )
        }
    }
}
