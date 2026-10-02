package com.jvoice.news

import android.Manifest
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.jvoice.core.auth.AuthGate
import com.jvoice.core.auth.DeskSessionGate
import com.jvoice.core.auth.SessionStore
import com.jvoice.core.auth.StaffLoginScreen
import com.jvoice.core.firebase.FirebaseAvailability
import com.jvoice.core.i18n.FirstRunLanguageDialog
import com.jvoice.core.i18n.LanguagePreference
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.ReleaseConfig
import com.jvoice.core.flags.LocalFeatureFlags
import com.jvoice.core.i18n.LocalAppLanguage
import com.jvoice.core.push.NewsPush
import com.jvoice.news.navigation.Routes
import com.jvoice.core.reader.ReaderProfile
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.data.repository.EngagementRepository
import com.jvoice.news.data.repository.NewsRepository
import com.jvoice.news.data.repository.ReadStateRepository
import com.jvoice.news.navigation.JVoiceNavGraph
import com.jvoice.news.theme.JVoiceTheme
import com.jvoice.news.ui.auth.SPLASH_DURATION_MS
import com.jvoice.news.ui.auth.SessionViewModel
import com.jvoice.news.ui.auth.SplashScreen
import com.jvoice.shell.AppModule
import com.jvoice.shell.FullscreenHost
import com.jvoice.shell.FullscreenHostOverlay
import com.jvoice.shell.LocalFullscreenHost
import com.jvoice.shell.LocalModuleSwitcher
import com.jvoice.shell.ModuleSwitcher
import com.jvoice.study.data.model.StudyRole
import com.jvoice.study.navigation.StudyNavGraph
import com.jvoice.study.ui.auth.StudySessionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, as the API requires. The system splash is a
        // plain navy screen (see Theme.JVoice.Starting) and is not held: the
        // Compose SplashScreen takes over on the first frame in the same navy.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // The volume keys adjust media, not the ringer - readers reach for them
        // while a story video plays.
        volumeControlStream = AudioManager.STREAM_MUSIC
        // Read state, the language choice and the desk session are the three things
        // that persist across launches.
        ReadStateRepository.init(this)
        LanguagePreference.init(this)
        SessionStore.init(this)
        com.jvoice.core.data.DraftStore.init(this)
        // Attempted once, here, so every later Firebase call can just ask
        // FirebaseAvailability rather than each guarding initialisation itself.
        FirebaseAvailability.init(this)
        // The reader's name and location. After FirebaseAvailability, because a
        // fresh install restores them from the server.
        ReaderProfile.init(this)
        // After ReaderProfile, which owns the device id the credit is keyed on.
        com.jvoice.core.data.InstallReferral.start(this)
        // Attaches the Firestore content listeners and the RTDB staff listener.
        // Must come after FirebaseAvailability.init - both no-op without it, so
        // the wrong order would leave every screen permanently empty rather than
        // failing loudly.
        NewsRepository.start()
        // Likes, dislikes and comment counts; this device's own votes come
        // from its prefs. Order: after ReaderProfile, which owns the device id.
        EngagementRepository.init(this)
        EngagementRepository.start()
        // App-level flags. Starts here rather than lazily in a screen so the
        // first paint already has them and nothing flickers off after a beat.
        FeatureFlags.start()
        // Story pushes: subscribe this device to the news topic, and note the
        // story a tapped notification asked for (the shell navigates to it).
        NewsPush.start(this)
        NewsPush.onIntent(intent)
        // TEMPORARY: one-off export of the bundled study content so it can be
        // uploaded to Firestore. Debug-only, and removed together with the mock
        // data once the upload is verified. See DevExport.
        com.jvoice.core.data.DevExport.exportIfNeeded(this)
        setContent { JVoiceApp() }
    }

    /** A notification tap while the app is open - singleTop routes it here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        NewsPush.onIntent(intent)
    }
}

/**
 * App shell.
 *
 * Two ways in, and they are deliberately different:
 *
 *  * **Readers** are the default. The app opens straight into the reader feed
 *    behind a short [SplashScreen]; there is no account and no choice to make.
 *    Their state lives on the device.
 *  * **Desk staff** sign in through [StaffLoginScreen] against Firebase, and their
 *    role comes from `users/{uid}/role`. The way there is the "Staff sign in"
 *    action on the reader's Profile tab.
 *
 * A stored desk session short-circuits the reader default on launch, but only
 * after [DeskSessionGate] has cleared it against the server's two kill-switches.
 */
@Composable
fun JVoiceApp() {
    val systemDark = isSystemInDarkTheme()
    var darkTheme by remember(systemDark) { mutableStateOf(systemDark) }
    val scope = rememberCoroutineScope()

    // The chosen language is provided to the whole tree, so every screen renders
    // content in it without carrying it through its own signature.
    val language by LanguagePreference.language.collectAsState()
    val hasChosenLanguage by LanguagePreference.hasChosen.collectAsState()

    // Runtime notification permission - only meaningful from API 33. Below that
    // the grant is implicit at install time, so there is nothing to ask.
    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* The grant is recorded by the OS; NewsPush checks it before posting. */ }

    fun requestNotifications() {
        LanguagePreference.markNotificationsAsked()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // The slot the fullscreen video player draws into - see FullscreenHost.
    val fullscreenHost = remember { FullscreenHost() }

    JVoiceTheme(darkTheme = darkTheme) {
        val featureFlags by FeatureFlags.flags.collectAsState()
        CompositionLocalProvider(
            LocalAppLanguage provides language,
            LocalFeatureFlags provides featureFlags,
            LocalFullscreenHost provides fullscreenHost
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                val newsSession: SessionViewModel = viewModel()
                val studySession: StudySessionViewModel = viewModel()
                val newsUser by newsSession.currentUser.collectAsState()
                val studyUser by studySession.currentUser.collectAsState()

                val deskSession by SessionStore.session.collectAsState()
                val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

                // Editors and news admins get the review alarm while signed in here;
                // anyone else (or nobody) leaves it.
                LaunchedEffect(deskSession?.uid, deskSession?.role) {
                    NewsPush.syncReviewAlerts(
                        appContext,
                        if (ReleaseConfig.READER_ONLY) null else deskSession?.role
                    )
                }

                // The Play build has no desk: a desk session left over from an
                // earlier install is ended, and the phone reads as a reader.
                if (ReleaseConfig.READER_ONLY) {
                    LaunchedEffect(deskSession?.uid) {
                        if (deskSession != null) {
                            AuthGate.logout()
                            NewsRepository.onSessionChanged()
                        }
                    }
                }

                // The staff login is reached from the reader's Profile tab, and Back
                // from it returns to the reader.
                var showStaffLogin by remember { mutableStateOf(false) }

                // The splash covers the first moments of whatever is underneath -
                // the session gate, the reader feed composing - and then lifts.
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_DURATION_MS)
                    showSplash = false
                }


                // Set once the launch gate has cleared this uid, so the checks run on
                // launch rather than on every recomposition.
                var gatedUid by remember { mutableStateOf<String?>(null) }

                /**
                 * A Super Admin owns both modules, so let them cross over directly.
                 * Supplied through a CompositionLocal so no existing screen signature
                 * has to change.
                 */
                fun enterNews(role: UserRole) {
                    showStaffLogin = false
                    studySession.signOut()
                    newsSession.signInAs(role)
                }

                fun enterStudy(role: StudyRole) {
                    showStaffLogin = false
                    newsSession.signOut()
                    studySession.signInAs(role)
                }

                /**
                 * Routes a signed-in desk account into whichever module its role owns.
                 * A role carrying both (Super Admin) starts in News.
                 */
                fun enterForDeskRole(session: SessionStore.DeskSession) {
                    // The article query differs for a signed-in desk account, so
                    // the listener has to be rebuilt - see NewsRepository.attachArticles.
                    NewsRepository.onSessionChanged()
                    val newsRole = session.role.newsRole
                    val studyRole = session.role.studyRole
                    when {
                        newsRole != null -> enterNews(newsRole)
                        studyRole != null -> enterStudy(studyRole)
                        // Unreachable: JvRole entries all carry at least one module
                        // role, and a record with no usable role never becomes a
                        // session. Signing out is the safe response if that changes.
                        else -> scope.launch { AuthGate.logout() }
                    }
                }

                /**
                 * Ends both the module session and the Firebase desk session.
                 *
                 * Where it lands depends on who was signed in. Desk staff drop back
                 * to the reader default. An anonymous reader or student has nothing
                 * to sign out *of* - for them the action means "let me in as staff",
                 * so it opens the login instead of bouncing them straight back to
                 * the feed they were just on.
                 */
                fun signOutEverything() {
                    val wasDesk = SessionStore.isSignedIn
                    gatedUid = null
                    newsSession.signOut()
                    studySession.signOut()
                    showStaffLogin = !wasDesk
                    if (wasDesk) {
                        scope.launch {
                            AuthGate.logout()
                            // Back to the anonymous, published-only query.
                            NewsRepository.onSessionChanged()
                        }
                    }
                }

                fun switcherFor(current: AppModule) = ModuleSwitcher(
                    current = current,
                    switchToOtherAsSuperAdmin = {
                        if (current == AppModule.NEWS) {
                            enterStudy(StudyRole.SUPER_ADMIN)
                        } else {
                            enterNews(UserRole.SUPER_ADMIN)
                        }
                    },
                    openStudyAsStudent = { enterStudy(StudyRole.STUDENT) },
                    openNewsAsReader = { enterNews(UserRole.READER) }
                )

                val session = deskSession
                when {
                    // A stored desk session that has not yet cleared the launch gate.
                    // Nothing else renders until the two server checks answer.
                    !ReleaseConfig.READER_ONLY &&
                        session != null && gatedUid != session.uid &&
                        newsUser == null && studyUser == null -> {
                        DeskSessionGate(
                            session = session,
                            installedVersion = BuildConfig.VERSION_CODE.toLong(),
                            onReady = {
                                gatedUid = session.uid
                                enterForDeskRole(session)
                            },
                            onRejected = {
                                // AuthGate.logout has already cleared the session, so
                                // falling through lands on the reader default.
                                gatedUid = null
                            }
                        )
                    }

                    newsUser != null -> {
                        // A fresh NavHostController per role keeps each role's graph isolated.
                        val navController = rememberNavController()
                        CompositionLocalProvider(
                            LocalModuleSwitcher provides switcherFor(AppModule.NEWS)
                        ) {
                            JVoiceNavGraph(
                                navController = navController,
                                user = newsUser!!,
                                isDarkTheme = darkTheme,
                                onToggleTheme = { darkTheme = it },
                                onSignOut = ::signOutEverything
                            )
                        }
                        // A tapped story notification: open that story on top of
                        // whatever the role's graph started on. The detail route
                        // lives in the reader graph, which every role's NavHost
                        // includes, so this works for desk sessions too.
                        val pendingArticleId by NewsPush.pendingArticleId.collectAsState()
                        LaunchedEffect(pendingArticleId) {
                            val id = pendingArticleId ?: return@LaunchedEffect
                            NewsPush.consumePendingArticle()
                            navController.navigate(Routes.newsDetail(id))
                        }
                    }

                    !ReleaseConfig.READER_ONLY && studyUser != null -> {
                        val navController = rememberNavController()
                        CompositionLocalProvider(
                            LocalModuleSwitcher provides switcherFor(AppModule.STUDY)
                        ) {
                            StudyNavGraph(
                                navController = navController,
                                user = studyUser!!,
                                isDarkTheme = darkTheme,
                                onToggleTheme = { darkTheme = it },
                                onSignOut = ::signOutEverything
                            )
                        }
                    }

                    !ReleaseConfig.READER_ONLY && showStaffLogin -> {
                        BackHandler { showStaffLogin = false }
                        StaffLoginScreen(
                            onSignedIn = { signedIn ->
                                gatedUid = signedIn.uid
                                enterForDeskRole(signedIn)
                            },
                            onBack = { showStaffLogin = false }
                        )
                    }

                    // Nobody signed in and no login requested: the reader default.
                    // Signing in swaps this branch for the news graph on the next
                    // frame, so nothing is drawn here - the splash covers the gap on
                    // launch, and later it is a single frame at most.
                    else -> LaunchedEffect(Unit) { newsSession.signInAs(UserRole.READER) }
                }

                // Above every screen and below only the splash and first-run
                // dialogue: the fullscreen video, when one is up.
                FullscreenHostOverlay(fullscreenHost)

                if (showSplash) {
                    SplashScreen(Modifier.fillMaxSize())
                }

                // Sits above whatever is showing, so the very first launch answers
                // the language and notification questions before anything else.
                // Once answered it never returns - the profile tab owns the choice
                // from then on. Held back until the splash lifts so the first thing
                // seen is the brand, not a dialogue over it.
                if (!hasChosenLanguage && !showSplash) {
                    FirstRunLanguageDialog(
                        selected = language,
                        // `apply`, not `set`: the tap flips the app over live but
                        // leaves the dialogue open for the notification question.
                        onSelect = LanguagePreference::apply,
                        locations = NewsRepository.locations,
                        initialLocation = ReaderProfile.location.value,
                        onProfile = { name, location ->
                            if (name.isNotBlank()) ReaderProfile.setName(name)
                            NewsRepository.setLocation(location)
                        },
                        onAllowNotifications = { requestNotifications() },
                        onSkipNotifications = { LanguagePreference.markNotificationsAsked() },
                        // Answering either notification button is what closes the
                        // dialogue - the default language stands if no row was tapped.
                        onDone = { LanguagePreference.markChosen() }
                    )
                }
            }
        }
    }
}
