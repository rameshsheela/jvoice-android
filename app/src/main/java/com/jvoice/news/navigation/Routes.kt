package com.jvoice.news.navigation

object Routes {
    // auth
    const val ROLE_SELECTOR = "role_selector"

    // reader
    const val READER_HOME = "reader/home"
    const val READER_CLIPS = "reader/clips"
    const val READER_FEED = "reader/feed"
    const val READER_CATEGORIES = "reader/categories"
    const val READER_SAVED = "reader/saved"
    const val READER_NOTIFICATIONS = "reader/notifications"
    const val READER_PROFILE = "reader/profile"
    const val READER_SEARCH = "reader/search"
    const val NEWS_DETAIL = "reader/detail/{articleId}"
    const val CATEGORY_NEWS = "reader/category/{categoryId}"
    const val READER_COMMENTS = "reader/comments/{articleId}"

    fun newsDetail(articleId: String) = "reader/detail/" + articleId
    fun categoryNews(categoryId: String) = "reader/category/" + categoryId
    fun comments(articleId: String) = "reader/comments/" + articleId

    // reporter
    /** The signed-in staff member's own profile - any desk role. */
    const val DESK_PROFILE = "desk/profile"

    const val REPORTER_DASHBOARD = "reporter/dashboard"
    const val REPORTER_MY_NEWS = "reporter/my_news"
    /** My stories opened on one group - a dashboard tile. */
    const val REPORTER_MY_NEWS_GROUP = "reporter/my_news_group?group={group}"
    const val REPORTER_EDITOR = "reporter/editor?articleId={articleId}"
    const val REPORTER_STORY = "reporter/story/{articleId}"
    const val REPORTER_REFERRALS = "reporter/referrals"
    const val ARG_GROUP = "group"

    fun reporterGroup(group: String) = "reporter/my_news_group?group=" + group
    fun reporterStory(articleId: String) = "reporter/story/" + articleId

    fun reporterCreate() = "reporter/editor?articleId="
    fun reporterEdit(articleId: String) = "reporter/editor?articleId=" + articleId

    // editor
    const val EDITOR_DASHBOARD = "editor/dashboard"
    const val EDITOR_QUEUE = "editor/queue"
    const val EDITOR_REVIEW = "editor/review/{articleId}"

    fun editorReview(articleId: String) = "editor/review/" + articleId

    // news admin
    const val ADMIN_DASHBOARD = "admin/dashboard"
    const val ADMIN_NEWS = "admin/news"
    const val ADMIN_CATEGORIES = "admin/categories"
    const val ADMIN_REPORTERS = "admin/reporters"

    // super admin
    const val SUPER_DASHBOARD = "super/dashboard"
    const val SUPER_USERS = "super/users"
    const val SUPER_ROLES = "super/roles"
    const val SUPER_SETTINGS = "super/settings"

    const val ARG_ARTICLE_ID = "articleId"
    const val ARG_CATEGORY_ID = "categoryId"
}
