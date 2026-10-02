package com.jvoice.news.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.jvoice.core.data.Firestore
import com.jvoice.core.data.int
import com.jvoice.core.data.long
import com.jvoice.core.data.str
import com.jvoice.core.data.strOrNull
import com.jvoice.core.reader.ReaderProfile
import com.jvoice.news.data.model.ArticleComment
import com.jvoice.news.data.model.ArticleEngagement
import com.jvoice.news.data.model.ArticleReport
import com.jvoice.news.data.model.Reaction
import com.jvoice.news.data.model.ReportReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Likes, dislikes, comments and reports on news articles - shared between
 * every reader through Firestore, and kept *with the article*:
 *
 *     articles/{articleId}                likes, dislikes, comments (counters,
 *                                         on the story document itself)
 *     articles/{articleId}/comments/{id}  one document per comment; a reply
 *                                         carries the parentId it answers
 *     articles/{articleId}/reports/{id}   one document per reader report
 *
 * Everything about a story lives under it, so the desk finds it in one place
 * and removing a story removes its engagement with it (see
 * [NewsRepository.removeArticle] - Firestore does not cascade on its own).
 * The counters ride on the article document the feed already listens to, so
 * showing them costs no second query.
 *
 * ## What stays on the device
 *
 * Readers are anonymous, so *which way this device voted* cannot live on the
 * server under an identity. It is kept in SharedPreferences: the reaction per
 * article, and the set of comments already liked. That is what stops the same
 * thumb being counted twice, and it is what the rules cannot enforce - a
 * determined reader could clear app data and vote again, which is an accepted
 * cost of not making people sign up to read the news.
 *
 * ## Counters
 *
 * Every counter change is a [FieldValue.increment], never a read-modify-write,
 * so two readers liking at once both land. The rules allow anonymous callers to
 * move a counter by one per write and nothing else on the document.
 */
object EngagementRepository {

    private const val PREFS = "jvoice_engagement"
    private const val KEY_REACTIONS = "reactions"       // articleId=LIKE,articleId=DISLIKE,...
    private const val KEY_LIKED_COMMENTS = "likedComments"

    const val MAX_COMMENT_LENGTH = 1000

    /** The comments subcollection path for a story. */
    fun commentsPath(articleId: String) = "${Firestore.ARTICLES}/$articleId/comments"

    /** The reports subcollection path for a story. */
    fun reportsPath(articleId: String) = "${Firestore.ARTICLES}/$articleId/reports"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var prefs: SharedPreferences? = null
    private var started = false

    /** This device's reactions by article id. */
    private val _myReactions = MutableStateFlow<Map<String, Reaction>>(emptyMap())

    /**
     * Optimistic counter deltas applied locally the moment a thumb is tapped,
     * so the number moves under the finger; the article listener's next
     * snapshot carries the server's value and the delta is dropped.
     */
    private val _pending = MutableStateFlow<Map<String, ArticleEngagement>>(emptyMap())

    /**
     * Counts with this device's own reaction folded in. The type is spelled
     * out because an empty literal gives the compiler nothing to infer from.
     */
    private val _engagement = MutableStateFlow<Map<String, ArticleEngagement>>(emptyMap())
    val engagement: StateFlow<Map<String, ArticleEngagement>> = _engagement.asStateFlow()

    /** Every comment loaded so far, across articles. */
    private val _comments = MutableStateFlow<List<ArticleComment>>(emptyList())
    val comments: StateFlow<List<ArticleComment>> = _comments.asStateFlow()

    private val _likedComments = MutableStateFlow<Set<String>>(emptySet())

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        _myReactions.value = store.getString(KEY_REACTIONS, "").orEmpty()
            .split(',')
            .mapNotNull { entry ->
                val (id, name) = entry.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                runCatching { id to Reaction.valueOf(name) }.getOrNull()
            }
            .toMap()
        _likedComments.value = store.getStringSet(KEY_LIKED_COMMENTS, emptySet()).orEmpty()
    }

    /**
     * Derives the engagement map from the articles the feed already holds.
     * Safe to call more than once.
     */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            combine(NewsRepository.articles, _myReactions, _pending) { articles, mine, pending ->
                val fromServer = articles.associate { a ->
                    a.id to ArticleEngagement(
                        articleId = a.id,
                        likes = a.likes,
                        dislikes = a.dislikes,
                        comments = a.comments
                    )
                }
                (fromServer.keys + mine.keys + pending.keys).associateWith { id ->
                    val base = fromServer[id] ?: ArticleEngagement(id)
                    val local = pending[id]
                    base.copy(
                        likes = (base.likes + (local?.likes ?: 0)).coerceAtLeast(0),
                        dislikes = (base.dislikes + (local?.dislikes ?: 0)).coerceAtLeast(0),
                        myReaction = mine[id] ?: Reaction.NONE
                    )
                }
            }.collect { _engagement.value = it }
        }
        // A server snapshot supersedes any optimistic delta.
        scope.launch { NewsRepository.articles.collect { _pending.value = emptyMap() } }
    }

    fun engagementFor(articleId: String): ArticleEngagement =
        _engagement.value[articleId] ?: ArticleEngagement(articleId)

    fun commentCount(articleId: String): Int = engagementFor(articleId).comments

    /* ---------------------------------------------------------------- reactions */

    /**
     * Like is exclusive with dislike, and tapping the active one clears it -
     * the counts move accordingly.
     */
    fun toggleLike(articleId: String) = react(articleId, Reaction.LIKE)

    fun toggleDislike(articleId: String) = react(articleId, Reaction.DISLIKE)

    private fun react(articleId: String, wanted: Reaction) {
        val current = _myReactions.value[articleId] ?: Reaction.NONE
        val next = if (current == wanted) Reaction.NONE else wanted

        // The deltas the server needs: undo the old vote, apply the new one.
        var likes = 0L
        var dislikes = 0L
        when (current) {
            Reaction.LIKE -> likes -= 1
            Reaction.DISLIKE -> dislikes -= 1
            Reaction.NONE -> Unit
        }
        when (next) {
            Reaction.LIKE -> likes += 1
            Reaction.DISLIKE -> dislikes += 1
            Reaction.NONE -> Unit
        }

        _myReactions.update { if (next == Reaction.NONE) it - articleId else it + (articleId to next) }
        _pending.update { map ->
            val p = map[articleId] ?: ArticleEngagement(articleId)
            map + (articleId to p.copy(likes = p.likes + likes.toInt(), dislikes = p.dislikes + dislikes.toInt()))
        }
        persistReactions()

        val fields = buildMap<String, Any?> {
            if (likes != 0L) put("likes", FieldValue.increment(likes))
            if (dislikes != 0L) put("dislikes", FieldValue.increment(dislikes))
        }
        if (fields.isNotEmpty()) Firestore.update(Firestore.ARTICLES, articleId, fields)
    }

    private fun persistReactions() {
        prefs?.edit()?.putString(
            KEY_REACTIONS,
            _myReactions.value.entries.joinToString(",") { it.key + "=" + it.value.name }
        )?.apply()
    }

    /* ----------------------------------------------------------------- comments */

    /**
     * Listens to one article's comments for as long as the returned
     * registration lives. The screen attaches it on entry and removes it on
     * exit; the loaded comments stay in [comments] afterwards, so reopening
     * the same story is instant.
     */
    fun watchComments(articleId: String): ListenerRegistration? =
        Firestore.listen(
            commentsPath(articleId),
            { id, data -> commentFrom(articleId, id, data) },
            onChange = { list ->
                _comments.update { old -> old.filterNot { it.articleId == articleId } + list }
            }
        )

    private fun commentFrom(articleId: String, id: String, data: Map<String, Any?>) = ArticleComment(
        id = id,
        articleId = articleId,
        parentId = data.strOrNull("parentId"),
        authorName = data.str("authorName").ifBlank { "Reader" },
        deviceId = data.str("deviceId"),
        text = data.str("text"),
        timeMillis = data.long("createdAt"),
        likes = data.int("likes"),
        isOwn = data.str("deviceId") == ReaderProfile.deviceId
    )

    /** Top-level comments, newest first. */
    fun topLevelComments(articleId: String): List<ArticleComment> =
        _comments.value
            .filter { it.articleId == articleId && it.parentId == null }
            .sortedByDescending { it.timeMillis }

    /** Replies under one comment, oldest first - a thread reads downwards. */
    fun repliesTo(commentId: String): List<ArticleComment> =
        _comments.value.filter { it.parentId == commentId }.sortedBy { it.timeMillis }

    fun commentsFor(articleId: String): List<ArticleComment> =
        _comments.value.filter { it.articleId == articleId }

    /**
     * Posts a comment, or a reply when [parentId] is given. Returns false for
     * an empty body. The article's comment counter moves with it.
     */
    fun addComment(
        articleId: String,
        authorName: String,
        text: String,
        parentId: String? = null
    ): Boolean {
        val body = text.trim().take(MAX_COMMENT_LENGTH)
        if (body.isBlank()) return false
        val path = commentsPath(articleId)
        Firestore.set(
            path, Firestore.newId(path),
            mapOf(
                "parentId" to parentId,
                "authorName" to authorName.trim().take(ReaderProfile.MAX_NAME_LENGTH).ifBlank { "Reader" },
                "deviceId" to ReaderProfile.deviceId,
                "location" to ReaderProfile.location.value,
                "text" to body,
                "likes" to 0,
                "createdAt" to System.currentTimeMillis()
            )
        )
        Firestore.update(Firestore.ARTICLES, articleId, mapOf("comments" to FieldValue.increment(1)))
        return true
    }

    fun hasLikedComment(commentId: String): Boolean = commentId in _likedComments.value

    /** One like per device per comment; a second tap does nothing. */
    fun likeComment(comment: ArticleComment) {
        if (hasLikedComment(comment.id)) return
        _likedComments.update { it + comment.id }
        prefs?.edit()?.putStringSet(KEY_LIKED_COMMENTS, _likedComments.value)?.apply()
        Firestore.update(commentsPath(comment.articleId), comment.id, mapOf("likes" to FieldValue.increment(1)))
    }

    /* ------------------------------------------------------------------ reports */

    private val _reports = MutableStateFlow(emptyList<ArticleReport>())
    val reports: StateFlow<List<ArticleReport>> = _reports.asStateFlow()

    /**
     * Files a reader's report on a story.
     *
     * Two writes, both fire-and-forget: the report itself under the story,
     * where the desk reads it, and a bump to the article's `reportCount` so
     * the moderation views can sort by it without counting. The report
     * carries the headline as well as the id so a desk user can see what was
     * reported without opening each one.
     *
     * The reporter is named from the profile if a name was set. Anonymous
     * readers are still identifiable to the desk by device, which is what
     * lets repeat abuse of the button be spotted.
     */
    fun submitReport(
        articleId: String,
        reason: ReportReason,
        suggestion: String,
        reportedBy: String
    ) {
        val name = ReaderProfile.name.value.ifBlank { reportedBy }
        val path = reportsPath(articleId)
        val id = Firestore.newId(path)
        val report = ArticleReport(
            id = id,
            articleId = articleId,
            reason = reason,
            suggestion = suggestion.trim(),
            reportedBy = name,
            timeMillis = System.currentTimeMillis()
        )
        _reports.update { listOf(report) + it }

        Firestore.set(
            path, id,
            mapOf(
                "headline" to NewsRepository.articleById(articleId)?.headline?.en.orEmpty(),
                "reason" to reason.name,
                "suggestion" to report.suggestion,
                "reportedBy" to name,
                "deviceId" to ReaderProfile.deviceId,
                "location" to ReaderProfile.location.value,
                "status" to "OPEN",
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
        NewsRepository.reportArticle(articleId)
    }

    fun reportsFor(articleId: String): List<ArticleReport> =
        _reports.value.filter { it.articleId == articleId }
}
