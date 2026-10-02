package com.jvoice.news.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.FlaggedRoute
import com.jvoice.core.flags.ReleaseConfig
import com.jvoice.news.data.model.User
import com.jvoice.study.data.mock.MockDataSource
import com.jvoice.study.data.model.StudyRole
import com.jvoice.study.data.model.StudyUser
import com.jvoice.study.navigation.studentDestinations
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.ui.admin.AdminDashboardScreen
import com.jvoice.news.ui.admin.AdminViewModel
import com.jvoice.news.ui.admin.CategoryManagementScreen
import com.jvoice.news.ui.admin.NewsManagementScreen
import com.jvoice.news.ui.admin.ReporterManagementScreen
import com.jvoice.news.ui.editor.ArticleReviewScreen
import com.jvoice.news.ui.editor.EditorDashboardScreen
import com.jvoice.news.ui.editor.EditorViewModel
import com.jvoice.news.ui.editor.ReviewQueueScreen
import com.jvoice.news.ui.reader.CategoryNewsScreen
import com.jvoice.news.ui.reader.CommentsScreen
import com.jvoice.news.ui.reader.NewsFlipScreen
import com.jvoice.news.ui.reader.VideoClipsScreen
import com.jvoice.news.ui.reader.NewsDetailScreen
import com.jvoice.news.ui.reader.ReaderCategoriesScreen
import com.jvoice.news.ui.reader.ReaderHomeScreen
import com.jvoice.news.ui.reader.ReaderNotificationsScreen
import com.jvoice.news.ui.reader.ReaderProfileScreen
import com.jvoice.news.ui.reader.ReaderSavedScreen
import com.jvoice.news.ui.reader.ReaderViewModel
import com.jvoice.news.ui.reader.SearchScreen
import com.jvoice.news.ui.reporter.CreateNewsScreen
import com.jvoice.news.ui.reporter.MyNewsScreen
import com.jvoice.news.ui.reporter.ReporterDashboardScreen
import com.jvoice.news.ui.reporter.ReporterViewModel
import com.jvoice.news.ui.superadmin.RoleManagementScreen
import com.jvoice.news.ui.superadmin.SuperAdminDashboardScreen
import com.jvoice.news.ui.superadmin.SuperAdminViewModel
import com.jvoice.news.ui.superadmin.SystemSettingsScreen
import com.jvoice.news.ui.superadmin.UserManagementScreen

/**
 * One NavHost per signed-in role. The role selector lives outside the graph
 * (see MainActivity) so switching role rebuilds the whole graph cleanly.
 */
@Composable
fun JVoiceNavGraph(
    navController: NavHostController,
    user: User,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val startDestination = when (user.role) {
        UserRole.READER -> Routes.READER_HOME
        UserRole.REPORTER -> Routes.REPORTER_DASHBOARD
        UserRole.EDITOR -> Routes.EDITOR_DASHBOARD
        UserRole.NEWS_ADMIN -> Routes.ADMIN_DASHBOARD
        UserRole.SUPER_ADMIN -> Routes.SUPER_DASHBOARD
    }

    // Every desk screen reaches the profile the same way - the avatar button
    // in its top bar - without each screen taking a new parameter.
    androidx.compose.runtime.CompositionLocalProvider(
        LocalOpenProfile provides { navController.navigate(Routes.DESK_PROFILE) { launchSingleTop = true } },
        // Staff are readers too: the news feed stays one tap away, and the
        // reader screens offer the way back to the desk.
        LocalOpenReaderNews provides { navController.navigate(Routes.READER_HOME) { launchSingleTop = true } },
        com.jvoice.aishorts.studio.LocalStudioNav provides com.jvoice.aishorts.studio.StudioNav(
            openMine = { navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.MINE) { launchSingleTop = true } },
            openCreate = { id -> navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.create(id)) },
            openReview = { navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.REVIEW) { launchSingleTop = true } }
        ),
        LocalBackToDesk provides (
            if (user.role == UserRole.READER) null
            else ({ if (!navController.popBackStack(startDestination, false)) navController.navigate(startDestination) })
        )
    ) {
    NavHost(navController = navController, startDestination = startDestination) {
        // Play build: the reader graph only - see ReleaseConfig.
        if (!ReleaseConfig.READER_ONLY) {
        // AI Shorts on the studio backend (aishorts/studio).
        composable(com.jvoice.aishorts.studio.StudioRoutes.MINE) {
            com.jvoice.aishorts.studio.MyAiVideosScreen(
                onBack = { navController.popBackStack() },
                onCreate = { navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.create()) },
                onOpen = { id -> navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.detail(id)) }
            )
        }
        composable(
            route = com.jvoice.aishorts.studio.StudioRoutes.CREATE,
            arguments = listOf(navArgument(com.jvoice.aishorts.studio.StudioRoutes.ARG_ARTICLE_ID) { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            com.jvoice.aishorts.studio.CreateShortScreen(
                articleId = entry.arguments?.getString(com.jvoice.aishorts.studio.StudioRoutes.ARG_ARTICLE_ID)?.ifBlank { null },
                onBack = { navController.popBackStack() },
                onDone = { id ->
                    navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.detail(id)) {
                        popUpTo(com.jvoice.aishorts.studio.StudioRoutes.CREATE) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = com.jvoice.aishorts.studio.StudioRoutes.DETAIL,
            arguments = listOf(navArgument(com.jvoice.aishorts.studio.StudioRoutes.ARG_SHORT_ID) { type = NavType.StringType })
        ) { entry ->
            com.jvoice.aishorts.studio.ShortDetailScreen(
                shortId = entry.arguments?.getString(com.jvoice.aishorts.studio.StudioRoutes.ARG_SHORT_ID).orEmpty(),
                onBack = { navController.popBackStack() },
                onRemake = { articleId -> navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.create(articleId.ifBlank { null })) }
            )
        }
        composable(com.jvoice.aishorts.studio.StudioRoutes.REVIEW) {
            com.jvoice.aishorts.studio.ShortsReviewScreen(
                onBack = { navController.popBackStack() },
                onOpen = { id -> navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.detail(id)) }
            )
        }
        composable(Routes.DESK_PROFILE) {
            com.jvoice.news.ui.profile.DeskProfileScreen(
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut
            )
        }
        }
        readerGraph(navController, user, isDarkTheme, onToggleTheme, onSignOut)
        if (!ReleaseConfig.READER_ONLY) {
        // Module 2's Student screens, hosted inside the News NavHost. They keep
        // their own bottom bar — News, Home, Study, Exams, Ranks, Profile — so the
        // module stays navigable after crossing over from News.
        //
        // The News tab is the way back and it is a plain tab switch, not a module
        // crossing: the reader's feed is a destination in *this* NavHost, so
        // switching to it keeps the reader's scroll position and back stack
        // instead of rebuilding the session.
        studentDestinations(
            navController = navController,
            user = studyDemoStudent(),
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSignOut = onSignOut,
            onOpenNews = { navController.switchTab(Routes.READER_HOME) }
        )
        reporterGraph(navController, user, onSignOut)
        editorGraph(navController, onSignOut)
        adminGraph(navController, onSignOut)
        superAdminGraph(navController, onSignOut)
        }
    }
    }
}

/** Opens the reader news feed from a desk screen. */
val LocalOpenReaderNews = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Returns a signed-in staff member from the reader screens to their desk; null for readers. */
val LocalBackToDesk = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Opens the staff profile; null outside the news NavHost. */
val LocalOpenProfile = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Switch between top-level destinations without stacking duplicates. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/* ------------------------------------------------------------------ reader */

private fun NavGraphBuilder.readerGraph(
    navController: NavHostController,
    user: User,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val openArticle: (String) -> Unit = { id -> navController.navigate(Routes.newsDetail(id)) }
    val openComments: (String) -> Unit = { id -> navController.navigate(Routes.comments(id)) }
    val openCategory: (String) -> Unit = { id -> navController.navigate(Routes.categoryNews(id)) }

    // News tab: short-news flip cards with a category chip row on top.
    composable(Routes.READER_HOME) {
        val vm: ReaderViewModel = viewModel()
        NewsFlipScreen(
            viewModel = vm,
            onOpenArticle = openArticle,
            onOpenComments = openComments,
            onOpenSearch = { navController.navigate(Routes.READER_SEARCH) },
            onOpenNotifications = { navController.navigate(Routes.READER_NOTIFICATIONS) },
            onOpenProfile = { navController.navigate(Routes.READER_PROFILE) },
            bottomBar = { ReaderBar(navController, vm) }
        )
    }

    // The original sectioned feed. No longer linked from the toolbar, which
    // is search and notifications only; the route stays for deep links.
    composable(Routes.READER_FEED) {
        val vm: ReaderViewModel = viewModel()
        ReaderHomeScreen(
            viewModel = vm,
            onOpenArticle = openArticle,
            onOpenSearch = { navController.navigate(Routes.READER_SEARCH) },
            onOpenNotifications = { navController.navigate(Routes.READER_NOTIFICATIONS) },
            onOpenCategory = openCategory,
            bottomBar = { ReaderBar(navController, vm) }
        )
    }

    // Clips tab: short-form vertical video, behind the shortsTab flag.
    //
    // Guarded here as well as in ReaderBottomBar because hiding the tab only
    // closes the entrance - see FlaggedRoute. The redirect pops Clips itself
    // rather than switching tabs, so Back does not walk straight back into a
    // flow that is turned off.
    if (!ReleaseConfig.READER_ONLY) composable(Routes.READER_CLIPS) {
        FlaggedRoute(
            key = FeatureFlags.Keys.SHORTS_TAB,
            optIn = true,
            onBlocked = {
                navController.navigate(Routes.READER_HOME) {
                    popUpTo(Routes.READER_CLIPS) { inclusive = true }
                    launchSingleTop = true
                }
            }
        ) {
            val vm: ReaderViewModel = viewModel()
            VideoClipsScreen(
                viewModel = vm,
                onOpenArticle = openArticle,
                bottomBar = { ReaderBar(navController, vm) }
            )
        }
    }

    composable(Routes.READER_CATEGORIES) {
        val vm: ReaderViewModel = viewModel()
        ReaderCategoriesScreen(
            viewModel = vm,
            onOpenCategory = openCategory,
            bottomBar = { ReaderBar(navController, vm) }
        )
    }

    composable(Routes.READER_SAVED) {
        val vm: ReaderViewModel = viewModel()
        ReaderSavedScreen(
            viewModel = vm,
            onOpenArticle = openArticle,
            onBack = { navController.popBackStack() },
            bottomBar = { ReaderBar(navController, vm) }
        )
    }

    composable(Routes.READER_NOTIFICATIONS) {
        val vm: ReaderViewModel = viewModel()
        ReaderNotificationsScreen(
            viewModel = vm,
            onOpenArticle = openArticle,
            onBack = { navController.popBackStack() },
            bottomBar = { ReaderBar(navController, vm) }
        )
    }

    // Reached from the toolbar, not a tab, so it is a pushed screen with a
    // Back arrow and no pill bar.
    composable(Routes.READER_PROFILE) {
        val vm: ReaderViewModel = viewModel()
        ReaderProfileScreen(
            user = user,
            viewModel = vm,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onSignOut = onSignOut,
            onBack = { navController.popBackStack() },
            onOpenSaved = { navController.navigate(Routes.READER_SAVED) },
            onOpenCategories = { navController.navigate(Routes.READER_CATEGORIES) },
            onOpenNotifications = { navController.navigate(Routes.READER_NOTIFICATIONS) }
        )
    }

    // No comments in the Play build until they can be reported and moderated.
    if (!ReleaseConfig.READER_ONLY) composable(
        route = Routes.READER_COMMENTS,
        arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
    ) { entry ->
        val vm: ReaderViewModel = viewModel()
        CommentsScreen(
            viewModel = vm,
            articleId = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty(),
            authorName = user.name,
            onBack = { navController.popBackStack() }
        )
    }

    composable(Routes.READER_SEARCH) {
        val vm: ReaderViewModel = viewModel()
        SearchScreen(
            viewModel = vm,
            onOpenArticle = openArticle,
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Routes.NEWS_DETAIL,
        arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
    ) { entry ->
        val vm: ReaderViewModel = viewModel()
        NewsDetailScreen(
            viewModel = vm,
            articleId = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty(),
            onOpenArticle = openArticle,
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Routes.CATEGORY_NEWS,
        arguments = listOf(navArgument(Routes.ARG_CATEGORY_ID) { type = NavType.StringType })
    ) { entry ->
        val vm: ReaderViewModel = viewModel()
        CategoryNewsScreen(
            viewModel = vm,
            categoryId = entry.arguments?.getString(Routes.ARG_CATEGORY_ID).orEmpty(),
            onOpenArticle = openArticle,
            onBack = { navController.popBackStack() }
        )
    }
}

@Composable
private fun ReaderBar(navController: NavHostController, viewModel: ReaderViewModel) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    ReaderBottomBar(
        currentRoute = backStackEntry?.destination?.route,
        onNavigate = { route -> navController.switchTab(route) }
    )
}

/** The same pill bar, for study destinations hosted inside the News shell. */
@Composable
private fun ReaderPillBar(navController: NavHostController) {
    val vm: ReaderViewModel = viewModel()
    ReaderBar(navController, vm)
}

/** The demo student identity used when Study is browsed from the News shell. */
private fun studyDemoStudent(): StudyUser =
    MockDataSource.demoAccounts[StudyRole.STUDENT]
        ?: MockDataSource.users.first { it.role == StudyRole.STUDENT }

/* ------------------------------------------------------------------ reporter */

private fun NavGraphBuilder.reporterGraph(
    navController: NavHostController,
    user: User,
    onSignOut: () -> Unit
) {
    composable(Routes.REPORTER_DASHBOARD) {
        val vm: ReporterViewModel = viewModel()
        ReporterDashboardScreen(
            user = user,
            viewModel = vm,
            onCreateNews = { navController.navigate(Routes.reporterCreate()) },
            onOpenMyNews = { navController.navigate(Routes.REPORTER_MY_NEWS) },
            onEditArticle = { id -> navController.navigate(Routes.reporterEdit(id)) },
            onSignOut = onSignOut,
            onOpenGroup = { group -> navController.navigate(Routes.reporterGroup(group)) },
            onOpenStory = { id -> navController.navigate(Routes.reporterStory(id)) },
            onOpenReferrals = { navController.navigate(Routes.REPORTER_REFERRALS) }
        )
    }

    composable(Routes.REPORTER_MY_NEWS) {
        val vm: ReporterViewModel = viewModel()
        MyNewsScreen(
            viewModel = vm,
            onEditArticle = { id -> navController.navigate(Routes.reporterEdit(id)) },
            onCreateNews = { navController.navigate(Routes.reporterCreate()) },
            onOpenStory = { id -> navController.navigate(Routes.reporterStory(id)) }
        )
    }

    composable(
        route = Routes.REPORTER_MY_NEWS_GROUP,
        arguments = listOf(
            navArgument(Routes.ARG_GROUP) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val vm: ReporterViewModel = viewModel()
        MyNewsScreen(
            viewModel = vm,
            onEditArticle = { id -> navController.navigate(Routes.reporterEdit(id)) },
            onCreateNews = { navController.navigate(Routes.reporterCreate()) },
            initialGroup = entry.arguments?.getString(Routes.ARG_GROUP),
            onOpenStory = { id -> navController.navigate(Routes.reporterStory(id)) },
            onBack = { navController.popBackStack() }
        )
    }

    composable(
        route = Routes.REPORTER_STORY,
        arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
    ) { entry ->
        val vm: ReporterViewModel = viewModel()
        com.jvoice.news.ui.reporter.ReporterStoryDetailScreen(
            viewModel = vm,
            articleId = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty(),
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(Routes.reporterEdit(id)) }
        )
    }

    composable(Routes.REPORTER_REFERRALS) {
        com.jvoice.news.ui.reporter.ReferralsScreen(onBack = { navController.popBackStack() })
    }

    composable(
        route = Routes.REPORTER_EDITOR,
        arguments = listOf(
            navArgument(Routes.ARG_ARTICLE_ID) {
                type = NavType.StringType
                defaultValue = ""
            }
        )
    ) { entry ->
        val vm: ReporterViewModel = viewModel()
        CreateNewsScreen(
            viewModel = vm,
            articleId = entry.arguments?.getString(Routes.ARG_ARTICLE_ID),
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() }
        )
    }
}

/* ------------------------------------------------------------------ editor */

private fun NavGraphBuilder.editorGraph(
    navController: NavHostController,
    onSignOut: () -> Unit
) {
    composable(Routes.EDITOR_DASHBOARD) {
        val vm: EditorViewModel = viewModel()
        EditorDashboardScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onOpenReview = { id -> navController.navigate(Routes.editorReview(id)) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.EDITOR_QUEUE) {
        val vm: EditorViewModel = viewModel()
        ReviewQueueScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onOpenReview = { id -> navController.navigate(Routes.editorReview(id)) },
            onSignOut = onSignOut
        )
    }

    composable(
        route = Routes.EDITOR_REVIEW,
        arguments = listOf(navArgument(Routes.ARG_ARTICLE_ID) { type = NavType.StringType })
    ) { entry ->
        val vm: EditorViewModel = viewModel()
        ArticleReviewScreen(
            viewModel = vm,
            articleId = entry.arguments?.getString(Routes.ARG_ARTICLE_ID).orEmpty(),
            onDone = { navController.popBackStack() },
            onBack = { navController.popBackStack() },
            onCreateAIShort = { newsId -> navController.navigate(com.jvoice.aishorts.studio.StudioRoutes.create(newsId)) }
        )
    }
}

/* ------------------------------------------------------------------ news admin */

private fun NavGraphBuilder.adminGraph(
    navController: NavHostController,
    onSignOut: () -> Unit
) {
    composable(Routes.ADMIN_DASHBOARD) {
        val vm: AdminViewModel = viewModel()
        AdminDashboardScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.ADMIN_NEWS) {
        val vm: AdminViewModel = viewModel()
        NewsManagementScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onOpenArticle = { id -> navController.navigate(Routes.editorReview(id)) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.ADMIN_CATEGORIES) {
        val vm: AdminViewModel = viewModel()
        CategoryManagementScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.ADMIN_REPORTERS) {
        val vm: AdminViewModel = viewModel()
        ReporterManagementScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }
}

/* ------------------------------------------------------------------ super admin */

private fun NavGraphBuilder.superAdminGraph(
    navController: NavHostController,
    onSignOut: () -> Unit
) {
    composable(Routes.SUPER_DASHBOARD) {
        val vm: SuperAdminViewModel = viewModel()
        SuperAdminDashboardScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.SUPER_USERS) {
        val vm: SuperAdminViewModel = viewModel()
        UserManagementScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.SUPER_ROLES) {
        val vm: SuperAdminViewModel = viewModel()
        RoleManagementScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onSignOut = onSignOut
        )
    }

    composable(Routes.SUPER_SETTINGS) {
        val vm: SuperAdminViewModel = viewModel()
        SystemSettingsScreen(
            viewModel = vm,
            onNavigate = { route -> navController.switchTab(route) },
            onOpenCategories = { navController.navigate(Routes.ADMIN_CATEGORIES) },
            onSignOut = onSignOut
        )
    }
}
