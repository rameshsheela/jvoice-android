package com.jvoice.news.data.repository

import com.google.firebase.firestore.ListenerRegistration
import com.jvoice.core.auth.SessionStore
import com.jvoice.core.data.Firestore
import com.jvoice.core.reader.ReaderProfile
import com.jvoice.core.data.StaffAccounts
import com.jvoice.core.data.StaffDirectory
import com.jvoice.core.data.articleFrom
import com.jvoice.core.data.categoryFrom
import com.jvoice.core.data.increment
import com.jvoice.core.data.notificationFrom
import com.jvoice.core.data.toMap
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LanguagePreference
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.NotificationItem
import com.jvoice.news.data.model.NotificationType
import com.jvoice.news.data.model.Reporter
import com.jvoice.news.data.model.ReporterStats
import com.jvoice.news.data.model.RolePermission
import com.jvoice.news.data.model.SystemSettings
import com.jvoice.news.data.model.User
import com.jvoice.news.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The News module's data, backed by Firestore.
 *
 * ## What changed, and what deliberately did not
 *
 * This used to be an in-memory singleton seeded from `MockData` — 25 fabricated
 * articles, demo accounts, the lot. All of that is gone. Articles, categories and
 * notifications now live in Firestore; the desk's people come from the Realtime
 * Database via [StaffDirectory], which is where authentication already put them.
 *
 * The public API is unchanged on purpose. Every screen and view model reads the
 * same StateFlows and calls the same functions, so the migration touched no UI
 * code at all. The flows simply start empty and fill when the listeners deliver.
 *
 * ## Empty is a real state now
 *
 * Before, every screen had data at launch. Now a fresh project genuinely has no
 * articles, and the existing empty states are what users see. That is the point of
 * removing the dummy data — an empty feed is honest, whereas fabricated headlines
 * hide the fact that nothing is wired up.
 *
 * ## Writes do not wait for the server
 *
 * Mutations write to Firestore and return. The snapshot listener echoes the change
 * back, and Firestore's local cache applies it immediately, so the UI updates at
 * once and the write syncs when there is a network. That is what makes the app
 * usable for a reporter filing from a village with no signal.
 */
object NewsRepository {

    /* ------------------------------------------------------------ live content */

    private val _articles = MutableStateFlow<List<NewsArticle>>(emptyList())
    val articles: StateFlow<List<NewsArticle>> = _articles.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    /** Desk accounts, from the Realtime Database. Admin-visible only, by rule. */
    val users: StateFlow<List<User>> get() = StaffDirectory.staff
    val reporters: StateFlow<List<Reporter>> get() = StaffDirectory.reporters

    /* ------------------------------------------------------------ local state */

    /**
     * Saved stories, on the device.
     *
     * Readers are anonymous, so there is nowhere server-side to hang a bookmark.
     * Starts empty — it used to be seeded with two fabricated article ids.
     */
    private val _savedArticleIds = MutableStateFlow<Set<String>>(emptySet())
    val savedArticleIds: StateFlow<Set<String>> = _savedArticleIds.asStateFlow()

    /**
     * Platform settings.
     *
     * Still in memory. These are presentation toggles rather than content, and
     * moving them to Firestore means a rules path and a listener for a handful of
     * booleans nothing outside the Super Admin screen reads. Left as a known gap
     * rather than half-migrated.
     */
    private val _settings = MutableStateFlow(SystemSettings())
    val settings: StateFlow<SystemSettings> = _settings.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    /**
     * The reader's location filter. Owned by [ReaderProfile], which persists it
     * and mirrors it to the server; this is just the feed's view of it.
     */
    val selectedLocation: StateFlow<String> get() = ReaderProfile.location

    /**
     * Reference data, not content.
     *
     * These two were previously read from `MockData`, which made them look like
     * dummy content. They are not: [locations] is the bureau list the filters and
     * the reporter form offer, and [rolePermissions] is a description of what each
     * role may do. Both are properties of the product, so they live in code where
     * they can be reviewed, not in a database where they can be edited into
     * something the app does not understand.
     */
    val locations: List<String> = listOf(
        "Hyderabad", "Warangal", "Karimnagar", "Nizamabad", "Khammam",
        "Vijayawada", "Visakhapatnam", "Guntur", "Tirupati", "New Delhi", "Bengaluru"
    )

    val rolePermissions: List<RolePermission> = listOf(
        RolePermission(
            UserRole.READER,
            listOf("Read news", "Save news", "Share news", "Search news", "Receive notifications")
        ),
        RolePermission(
            UserRole.REPORTER,
            listOf("Create news", "Edit own news", "Submit news", "Save drafts", "View own statistics")
        ),
        RolePermission(
            UserRole.EDITOR,
            listOf("Review news", "Edit news", "Approve / reject news", "Send back for correction", "Publish news")
        ),
        RolePermission(
            UserRole.NEWS_ADMIN,
            listOf("Manage news", "Manage categories", "Manage reporters", "Mark breaking news", "Pin / feature news", "Remove articles")
        ),
        RolePermission(
            UserRole.SUPER_ADMIN,
            listOf("Full access", "Manage all users", "Change user roles", "Manage role permissions", "System settings", "Content moderation")
        )
    )

    /* -------------------------------------------------------------- listeners */

    private val registrations = mutableListOf<ListenerRegistration>()

    /**
     * Attaches the content listeners. Idempotent; safe to call from app start.
     *
     * No-op when Firebase is unavailable, leaving every flow empty — the screens
     * then show their empty states rather than crashing.
     */
    fun start() {
        attachListeners()
    }

    /**
     * Re-attaches the article listener for the current audience.
     *
     * Called on sign-in and sign-out. It matters because the query has to differ:
     * see [attachListeners].
     */
    fun onSessionChanged() {
        articleRegistration?.remove()
        articleRegistration = null
        attachArticles()
        notificationRegistration?.remove()
        notificationRegistration = null
        attachNotifications()
    }

    private var articleRegistration: ListenerRegistration? = null
    private var notificationRegistration: ListenerRegistration? = null

    /**
     * The notifications query, by audience - the same reasoning as
     * [attachArticles]. The rule lets anyone read a broadcast (no targetRole)
     * and only the desk read the rest, so an anonymous reader has to ask for
     * broadcasts explicitly or the whole query is refused.
     */
    private fun attachNotifications() {
        val isDesk = SessionStore.isSignedIn
        notificationRegistration = Firestore.listen(
            Firestore.NEWS_NOTIFICATIONS,
            ::notificationFrom,
            { list -> _notifications.value = list.sortedByDescending { it.timeMillis } },
            narrow = if (isDesk) null else { q -> q.whereEqualTo("targetRole", null) }
        )
    }

    /**
     * The articles query, chosen by who is asking.
     *
     * Firestore rules are a condition the query must satisfy, not a filter applied
     * to results — so an anonymous reader listening to the whole collection is
     * refused outright rather than quietly handed the published subset. A reader
     * therefore asks for published stories explicitly; only a signed-in desk
     * account, which the rule allows to see everything, listens unfiltered.
     */
    private fun attachArticles() {
        val isDesk = SessionStore.isSignedIn
        articleRegistration = Firestore.listen(
            Firestore.ARTICLES,
            ::articleFrom,
            { list ->
                // Sorted here rather than in the query: the desk needs drafts
                // ordered by edit time and readers need published ones by publish
                // time, so one server-side ordering cannot serve both. The dataset
                // is small enough that sorting in memory is free.
                _articles.value = list.sortedByDescending { it.publishedAt ?: it.createdAt }
            },
            narrow = if (isDesk) null else { q -> q.whereEqualTo("status", "PUBLISHED") }
        )
        articleRegistration?.let(registrations::add)
    }

    private fun attachListeners() {
        if (registrations.isNotEmpty()) return
        attachArticles()

        Firestore.listen(Firestore.CATEGORIES, ::categoryFrom) { list ->
            _categories.value = list.sortedBy { it.name.en }
        }?.let(registrations::add)

        attachNotifications()

        StaffDirectory.start()
    }

    private fun now() = System.currentTimeMillis()

    /* ---------------------------------------------------------------- session */

    /**
     * Sets the current user for [role].
     *
     * For a desk role the identity comes from the signed-in Firebase session, so
     * the app shows the real person rather than a stand-in. For [UserRole.READER]
     * there is no account by design, so a local anonymous identity is built — that
     * is what "readers stay anonymous" means in practice.
     */
    fun signInAs(role: UserRole) {
        val desk = SessionStore.current
        _currentUser.value = if (role == UserRole.READER || desk == null) {
            User(
                id = "local_reader",
                name = "Reader",
                email = "",
                role = UserRole.READER,
                location = ReaderProfile.location.value
            )
        } else {
            User(
                id = desk.uid,
                name = desk.name,
                email = desk.email,
                phone = desk.phone,
                role = role,
                location = ReaderProfile.location.value,
                avatarUrl = desk.avatarUrl,
                loginId = desk.loginId
            )
        }
    }

    /** Picks up a profile change (name, phone, photo) without re-routing. */
    fun refreshCurrentUser() {
        val user = _currentUser.value ?: return
        if (user.role != UserRole.READER) signInAs(user.role)
    }

    fun signOut() {
        _currentUser.value = null
    }

    fun setLocation(location: String) = ReaderProfile.setLocation(location)

    /* ---------------------------------------------------------------- lookups */

    fun articleById(id: String): NewsArticle? = _articles.value.firstOrNull { it.id == id }

    fun categoryById(id: String): Category? = _categories.value.firstOrNull { it.id == id }

    fun categoryName(
        id: String,
        language: AppLanguage = LanguagePreference.current
    ): String = categoryById(id)?.name?.get(language)
        ?: if (language == AppLanguage.TELUGU) "సాధారణం" else "General"

    fun publishedArticles(): List<NewsArticle> =
        _articles.value.filter { it.status == NewsStatus.PUBLISHED }
            .sortedByDescending { it.publishedAt ?: it.createdAt }

    fun relatedArticles(article: NewsArticle, limit: Int = 4): List<NewsArticle> =
        publishedArticles()
            .filter { it.id != article.id }
            .sortedByDescending { candidate ->
                var score = 0
                if (candidate.categoryId == article.categoryId) score += 3
                if (candidate.location == article.location) score += 2
                if (candidate.tags.any { tag -> article.tags.any { it.en == tag.en } }) score += 1
                score
            }
            .take(limit)

    fun moreFromCategory(article: NewsArticle, limit: Int = 5): List<NewsArticle> =
        publishedArticles().filter { it.categoryId == article.categoryId && it.id != article.id }
            .take(limit)

    /* ------------------------------------------------------------ reader state */

    fun toggleSaved(articleId: String): Boolean {
        val saved = _savedArticleIds.value.contains(articleId)
        _savedArticleIds.value = if (saved) {
            _savedArticleIds.value - articleId
        } else {
            _savedArticleIds.value + articleId
        }
        return !saved
    }

    fun isSaved(articleId: String) = _savedArticleIds.value.contains(articleId)

    fun clearSaved() {
        _savedArticleIds.value = emptySet()
    }

    /**
     * Records a read.
     *
     * An atomic server-side increment, not a read-modify-write: every reader
     * touches the same counter and a local increment would lose most of them.
     */
    fun registerView(articleId: String) {
        Firestore.update(Firestore.ARTICLES, articleId, increment("views"))
    }

    fun reportArticle(articleId: String) {
        Firestore.update(Firestore.ARTICLES, articleId, increment("reportCount"))
    }

    /* ----------------------------------------------------------------- search */

    fun search(
        query: String,
        categoryId: String? = null,
        location: String? = null,
        onlyPublished: Boolean = true
    ): List<NewsArticle> {
        val q = query.trim()
        return _articles.value
            .filter { !onlyPublished || it.status == NewsStatus.PUBLISHED }
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { location == null || it.location.equals(location, ignoreCase = true) }
            .filter { article ->
                if (q.isBlank()) return@filter true
                // Searches both languages at once, so a reader browsing in Telugu
                // still finds a story by its English title.
                article.matches(q) ||
                    categoryById(article.categoryId)?.name?.matches(q) == true
            }
            .sortedByDescending { it.publishedAt ?: it.createdAt }
    }

    /* ------------------------------------------------------ reporter workflow */

    suspend fun createOrUpdateArticle(
        existingId: String?,
        headline: LocalizedText,
        shortDescription: LocalizedText,
        content: LocalizedText,
        categoryId: String,
        location: String,
        imageUrl: String,
        tags: List<LocalizedText>,
        isBreaking: Boolean,
        submit: Boolean,
        photoUrls: List<String> = emptyList(),
        videoUrls: List<String> = emptyList(),
        reporterId: String,
        reporterName: String
    ): Firestore.WriteResult {
        val status = if (submit) NewsStatus.SUBMITTED else NewsStatus.DRAFT
        val existing = existingId?.let { articleById(it) }
        val id = existingId ?: Firestore.newId(Firestore.ARTICLES)

        val article = NewsArticle(
            id = id,
            headline = headline,
            shortDescription = shortDescription,
            content = content,
            categoryId = categoryId,
            location = location,
            imageUrl = imageUrl,
            photoUrls = photoUrls,
            videoUrls = videoUrls,
            tags = tags,
            isBreaking = isBreaking,
            isFeatured = existing?.isFeatured ?: false,
            isTrending = existing?.isTrending ?: false,
            status = status,
            reporterId = reporterId,
            reporterName = reporterName,
            createdAt = existing?.createdAt ?: now(),
            updatedAt = now(),
            publishedAt = existing?.publishedAt,
            // Submitting clears the previous verdict: the story is back in the
            // queue, and a stale rejection reason on it reads as a fresh one.
            rejectionReason = if (submit) null else existing?.rejectionReason,
            editorNote = if (submit) null else existing?.editorNote,
            views = existing?.views ?: 0,
            reportCount = existing?.reportCount ?: 0,
            // The desk's choices survive a reporter's resubmit.
            detailEnabled = existing?.detailEnabled ?: true,
            notifyReaders = existing?.notifyReaders ?: true
        )
        val result = Firestore.setAndWait(Firestore.ARTICLES, id, article.toMap())
        if (result is Firestore.WriteResult.Failed) return result

        if (submit) {
            pushNotification(
                title = LocalizedText("New article submitted", "కొత్త కథనం సమర్పించబడింది"),
                message = headline.trimmedTo(70),
                type = NotificationType.SYSTEM,
                articleId = id,
                targetRole = UserRole.EDITOR
            )
        }
        return result
    }

    fun articlesByReporter(reporterId: String): List<NewsArticle> =
        _articles.value.filter { it.reporterId == reporterId }
            .sortedByDescending { it.updatedAt }

    fun deleteDraft(articleId: String) {
        Firestore.delete(Firestore.ARTICLES, articleId)
    }

    /* -------------------------------------------------------- editor workflow */

    fun reviewQueue(): List<NewsArticle> =
        _articles.value
            .filter { it.status == NewsStatus.SUBMITTED || it.status == NewsStatus.UNDER_REVIEW }
            .sortedBy { it.createdAt }

    fun markUnderReview(articleId: String) {
        val article = articleById(articleId) ?: return
        if (article.status != NewsStatus.SUBMITTED) return
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf("status" to NewsStatus.UNDER_REVIEW.name, "updatedAt" to now())
        )
    }

    fun applyEditorEdits(
        articleId: String,
        headline: LocalizedText,
        shortDescription: LocalizedText,
        content: LocalizedText,
        categoryId: String,
        tags: List<LocalizedText>,
        detailEnabled: Boolean,
        notifyReaders: Boolean
    ) {
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf(
                "headline" to headline.toMap(),
                "shortDescription" to shortDescription.toMap(),
                "content" to content.toMap(),
                "categoryId" to categoryId,
                "tags" to tags.map { it.toMap() },
                "detailEnabled" to detailEnabled,
                "notifyReaders" to notifyReaders,
                "updatedAt" to now()
            )
        )
    }

    /**
     * Approve, and publish in the same step when [publishNow] - the copy goes
     * live for readers and their phones are told, as the console does when it
     * publishes (notifyPublished in J Voice web/src/store/firestoreData.js).
     *
     * Waits for J Voice to take the change, so the screen can say when it did not.
     */
    suspend fun approveArticle(
        articleId: String,
        publishNow: Boolean = true,
        notifyReaders: Boolean? = null
    ): Firestore.WriteResult {
        val article = articleById(articleId)
            ?: return Firestore.WriteResult.Failed("This story is no longer in the queue. Pull to refresh.")
        val publishedAt = if (publishNow) now() else article.publishedAt
        val result = Firestore.updateAndWait(
            Firestore.ARTICLES, articleId,
            mapOf(
                "status" to (if (publishNow) NewsStatus.PUBLISHED else NewsStatus.APPROVED).name,
                "publishedAt" to publishedAt,
                "sortAt" to (publishedAt ?: article.createdAt),
                "rejectionReason" to null,
                "editorNote" to null,
                "updatedAt" to now()
            )
        )
        if (result is Firestore.WriteResult.Failed) return result

        pushNotification(
            title = if (publishNow)
                LocalizedText("Article published", "కథనం ప్రచురించబడింది")
            else LocalizedText("Article approved", "కథనం ఆమోదించబడింది"),
            message = article.headline.trimmedTo(70),
            type = NotificationType.APPROVAL,
            articleId = articleId,
            targetRole = UserRole.REPORTER
        )
        // A published story reaches readers' phones unless the desk switched
        // that off: a broadcast (no targetRole) is what pushNewsNotification sends.
        if (publishNow && (notifyReaders ?: article.notifyReaders)) {
            pushNotification(
                title = if (article.isBreaking) LocalizedText("Breaking news", "బ్రేకింగ్ న్యూస్")
                else LocalizedText("New story", "కొత్త వార్త"),
                message = article.headline.trimmedTo(80),
                type = if (article.isBreaking) NotificationType.BREAKING else NotificationType.GENERAL,
                articleId = articleId,
                targetRole = null
            )
        }
        return result
    }

    fun rejectArticle(articleId: String, reason: LocalizedText) {
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf(
                "status" to NewsStatus.REJECTED.name,
                "rejectionReason" to reason.toMap(),
                "updatedAt" to now()
            )
        )
        pushNotification(
            title = LocalizedText("Article rejected", "కథనం తిరస్కరించబడింది"),
            message = reason.trimmedTo(80),
            type = NotificationType.REJECTION,
            articleId = articleId,
            targetRole = UserRole.REPORTER
        )
    }

    fun sendBackForCorrection(articleId: String, note: LocalizedText) {
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf(
                "status" to NewsStatus.SENT_BACK.name,
                "editorNote" to note.toMap(),
                "updatedAt" to now()
            )
        )
        pushNotification(
            title = LocalizedText("Sent back for correction", "సరిదిద్దుబాటుకు వెనక్కి పంపబడింది"),
            message = note.trimmedTo(80),
            type = NotificationType.REJECTION,
            articleId = articleId,
            targetRole = UserRole.REPORTER
        )
    }

    /* --------------------------------------------------------- admin: articles */

    fun publishApproved(articleId: String) {
        val article = articleById(articleId) ?: return
        val stamp = now()
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf(
                "status" to NewsStatus.PUBLISHED.name,
                "publishedAt" to stamp,
                "sortAt" to stamp,
                "updatedAt" to stamp
            )
        )
        // Tell readers' phones, as approve-and-publish does.
        if (article.notifyReaders) pushNotification(
            title = if (article.isBreaking) LocalizedText("Breaking news", "బ్రేకింగ్ న్యూస్")
            else LocalizedText("New story", "కొత్త వార్త"),
            message = article.headline.trimmedTo(80),
            type = if (article.isBreaking) NotificationType.BREAKING else NotificationType.GENERAL,
            articleId = articleId,
            targetRole = null
        )
    }

    fun unpublish(articleId: String) {
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf("status" to NewsStatus.APPROVED.name, "updatedAt" to now())
        )
    }

    fun toggleBreaking(articleId: String) {
        val article = articleById(articleId) ?: return
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf("isBreaking" to !article.isBreaking, "updatedAt" to now())
        )
    }

    fun toggleFeatured(articleId: String) {
        val article = articleById(articleId) ?: return
        Firestore.update(
            Firestore.ARTICLES, articleId,
            mapOf("isFeatured" to !article.isFeatured, "updatedAt" to now())
        )
    }

    /** Removes a story and everything filed under it - comments and reports. */
    fun removeArticle(articleId: String) {
        Firestore.deleteWithChildren(Firestore.ARTICLES, articleId, "comments", "reports")
    }

    fun filterArticles(
        query: String = "",
        categoryId: String? = null,
        status: NewsStatus? = null
    ): List<NewsArticle> {
        val q = query.trim()
        return _articles.value
            .filter { categoryId == null || it.categoryId == categoryId }
            .filter { status == null || it.status == status }
            .filter { q.isBlank() || it.matches(q) }
            .sortedByDescending { it.updatedAt }
    }

    /* ------------------------------------------------------- admin: categories */

    fun addCategory(name: LocalizedText, emoji: String) {
        val id = Firestore.newId(Firestore.CATEGORIES)
        Firestore.set(
            Firestore.CATEGORIES, id,
            Category(id = id, name = name, emoji = emoji.ifBlank { "📰" }).toMap()
        )
    }

    fun updateCategory(id: String, name: LocalizedText, emoji: String) {
        Firestore.update(
            Firestore.CATEGORIES, id,
            mapOf("name" to name.toMap(), "emoji" to emoji)
        )
    }

    fun toggleCategoryEnabled(id: String) {
        val category = categoryById(id) ?: return
        Firestore.update(Firestore.CATEGORIES, id, mapOf("isEnabled" to !category.isEnabled))
    }

    /** @return false when the category still has articles attached. */
    fun deleteCategory(id: String): Boolean {
        if (_articles.value.any { it.categoryId == id }) return false
        Firestore.delete(Firestore.CATEGORIES, id)
        return true
    }

    fun articleCountForCategory(id: String) = _articles.value.count { it.categoryId == id }

    /* -------------------------------------------------------- admin: reporters */

    fun reporterStats(): List<ReporterStats> = reporters.value.map { reporter ->
        val own = _articles.value.filter { it.reporterId == reporter.userId }
        ReporterStats(
            reporter = reporter,
            total = own.size,
            approved = own.count { it.status == NewsStatus.APPROVED || it.status == NewsStatus.PUBLISHED },
            rejected = own.count { it.status == NewsStatus.REJECTED },
            pending = own.count { it.status == NewsStatus.SUBMITTED || it.status == NewsStatus.UNDER_REVIEW },
            published = own.count { it.status == NewsStatus.PUBLISHED }
        )
    }

    /**
     * Suspends or restores a reporter through the staffAccounts function, which
     * also blocks the Auth login and ends any open session - the `isLogin` flag
     * alone only stopped the next launch.
     */
    suspend fun toggleReporterActive(userId: String): Result<Unit> {
        val current = reporters.value.firstOrNull { it.userId == userId }?.isActive ?: true
        return StaffAccounts.setActive(userId, !current)
    }

    /** Through the function: a news admin may not write a profile directly. */
    suspend fun updateReporterLocation(userId: String, location: String): Result<Unit> =
        StaffAccounts.setLocation(userId, location)

    suspend fun createReporter(
        area: String,
        name: String,
        phone: String,
        location: String,
        password: String
    ): Result<StaffAccounts.Created> =
        StaffAccounts.create("reporter", area, name, phone, location, password)

    suspend fun nextReporterId(area: String): Result<String> = StaffAccounts.nextId("reporter", area)

    suspend fun setReporterPassword(userId: String, password: String): Result<Unit> =
        StaffAccounts.setPassword(userId, password)

    /* ------------------------------------------------------ super admin: users */

    fun setUserActive(userId: String, active: Boolean) {
        StaffDirectory.setActive(userId, active)
    }

    /**
     * Changes a role.
     *
     * Takes the news-module enum for call-site compatibility and maps it back to
     * the stored role code. Roles the news module has no equivalent for cannot be
     * assigned from here, which is correct: this is the news people screen.
     */
    fun changeUserRole(userId: String, role: UserRole) {
        val jvRole = com.jvoice.core.auth.JvRole.entries
            .firstOrNull { it.newsRole == role } ?: return
        StaffDirectory.setRole(userId, jvRole)
    }

    fun updateUser(userId: String, name: String, email: String, location: String) {
        StaffDirectory.updateProfile(userId, name, email, location)
    }

    fun usersByRole(role: UserRole?): List<User> =
        users.value.filter { role == null || it.role == role }

    /* --------------------------------------------------------------- settings */

    fun updateSettings(transform: (SystemSettings) -> SystemSettings) {
        _settings.update(transform)
    }

    /* ---------------------------------------------------------- notifications */

    fun pushNotification(
        title: LocalizedText,
        message: LocalizedText,
        type: NotificationType,
        articleId: String? = null,
        targetRole: UserRole? = null
    ) {
        val id = Firestore.newId(Firestore.NEWS_NOTIFICATIONS)
        Firestore.set(
            Firestore.NEWS_NOTIFICATIONS, id,
            NotificationItem(
                id = id,
                title = title,
                message = message,
                timeMillis = now(),
                type = type,
                isRead = false,
                articleId = articleId,
                targetRole = targetRole
            ).toMap()
        )
    }

    /**
     * Notifications for [role], with read state resolved for who is asking.
     *
     * A reader's "read" lives on the device (see [ReadStateRepository]): the
     * document is shared by every reader and anonymous callers may not write
     * it, so a flag there could neither be per-person nor be set. The desk,
     * signed in, keeps the server flag.
     */
    fun notificationsFor(role: UserRole): List<NotificationItem> =
        _notifications.value
            .filter { it.targetRole == null || it.targetRole == role }
            .map {
                if (role == UserRole.READER) it.copy(isRead = ReadStateRepository.isNotificationRead(it.id)) else it
            }
            .sortedByDescending { it.timeMillis }

    fun unreadCountFor(role: UserRole): Int = notificationsFor(role).count { !it.isRead }

    fun markNotificationRead(id: String, role: UserRole = UserRole.READER) {
        if (role == UserRole.READER) {
            ReadStateRepository.markNotificationRead(id)
        } else {
            Firestore.update(Firestore.NEWS_NOTIFICATIONS, id, mapOf("isRead" to true))
        }
    }

    fun markAllNotificationsRead(role: UserRole) {
        notificationsFor(role).filterNot { it.isRead }.forEach { markNotificationRead(it.id, role) }
    }

    /* ----------------------------------------------------- dashboard counters */

    fun countByStatus(status: NewsStatus) = _articles.value.count { it.status == status }

    fun countPending() = _articles.value.count {
        it.status == NewsStatus.SUBMITTED || it.status == NewsStatus.UNDER_REVIEW
    }

    fun countReported() = _articles.value.count { it.reportCount > 0 }

    fun countUsersWithRole(role: UserRole) = users.value.count { it.role == role }

    private fun isToday(millis: Long?): Boolean {
        if (millis == null) return false
        val dayStart = now() - (now() % 86_400_000L)
        return millis >= dayStart
    }

    fun approvedToday() = _articles.value.count {
        (it.status == NewsStatus.APPROVED || it.status == NewsStatus.PUBLISHED) && isToday(it.updatedAt)
    }

    fun rejectedToday() = _articles.value.count { it.status == NewsStatus.REJECTED && isToday(it.updatedAt) }

    fun publishedToday() = _articles.value.count { it.status == NewsStatus.PUBLISHED && isToday(it.publishedAt) }
}
