package com.jvoice.news.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.ReleaseConfig
import com.jvoice.core.flags.flagOptedIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvoice.news.data.model.UserRole
import com.jvoice.shell.AppModule
import com.jvoice.shell.LocalModuleSwitcher
import com.jvoice.study.navigation.StudyRoutes
import kotlinx.coroutines.launch
import com.jvoice.news.components.JVoiceMark

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

/* ------------------------------------------------------------------ reader shell */

/**
 * Reader bottom navigation: a floating pill bar with the content tabs - News,
 * and Clips and Study when they are switched on. Search, notifications and the
 * profile live in the home top bar.
 *
 * The Study tab crosses into Module 2 as a Student.
 */
@Composable
fun ReaderBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    // Clips and Study are opt-in: each appears only once an admin has switched
    // its flag on. Built with buildList rather than a filter over a fixed list
    // so the pill lays out for the tabs it has, not around gaps.
    val items = buildList {
        add(NavItem(Routes.READER_HOME, "News", Icons.Default.Home))
        if (!ReleaseConfig.READER_ONLY && flagOptedIn(FeatureFlags.Keys.SHORTS_TAB)) {
            add(NavItem(Routes.READER_CLIPS, "Clips", Icons.Default.Bolt))
        }
        if (!ReleaseConfig.READER_ONLY && flagOptedIn(FeatureFlags.Keys.STUDY_TAB)) {
            add(NavItem(StudyRoutes.HOME, "Study", Icons.Default.School))
        }
    }

    // A bar with nowhere to go is just a lump on the page. With News alone the
    // bar is left out entirely and the Scaffold gives the content the space.
    if (items.size < 2) return

    // The app is edge-to-edge (forced from Android 15 at this targetSdk), and
    // unlike Material's NavigationBar this hand-built pill claims no insets of
    // its own - without this padding it draws underneath the system navigation
    // bar. Gesture navigation gets a slim strip, three-button gets a full bar.
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            // Translucent so the page shows through the bar.
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            ),
            shadowElevation = 6.dp
        ) {
            Row(
                Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val tint = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.Transparent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { if (!selected) onNavigate(item.route) }
                    ) {
                        // Compact: a single row, and only the selected tab is labelled.
                        Row(
                            Modifier.padding(
                                start = if (selected) 12.dp else 13.dp,
                                end = if (selected) 14.dp else 13.dp,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.badgeCount > 0) {
                                BadgedBox(badge = { Badge { Text(item.badgeCount.toString()) } }) {
                                    Icon(
                                        item.icon,
                                        contentDescription = item.label,
                                        tint = tint,
                                        modifier = Modifier.size(21.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = tint,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                            AnimatedVisibility(visible = selected) {
                                Row {
                                    Spacer(Modifier.width(7.dp))
                                    Text(
                                        item.label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tint,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ admin shell */

private fun drawerItemsFor(role: UserRole): List<NavItem> = when (role) {
    UserRole.NEWS_ADMIN -> listOf(
        NavItem(Routes.ADMIN_DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavItem(Routes.ADMIN_NEWS, "News Management", Icons.AutoMirrored.Filled.Article),
        NavItem(Routes.ADMIN_CATEGORIES, "Categories", Icons.Default.Category),
        NavItem(Routes.ADMIN_REPORTERS, "Reporters", Icons.Default.Groups),
        NavItem(com.jvoice.aishorts.studio.StudioRoutes.REVIEW, "AI Shorts review", Icons.Default.Movie)
    )
    UserRole.SUPER_ADMIN -> listOf(
        NavItem(Routes.SUPER_DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavItem(Routes.SUPER_USERS, "User Management", Icons.Default.Groups),
        NavItem(Routes.SUPER_ROLES, "Role Management", Icons.Default.Shield),
        NavItem(Routes.SUPER_SETTINGS, "System Settings", Icons.Default.Settings),
        NavItem(com.jvoice.aishorts.studio.StudioRoutes.REVIEW, "AI Shorts review", Icons.Default.Movie)
    )
    UserRole.EDITOR -> listOf(
        NavItem(Routes.EDITOR_DASHBOARD, "Dashboard", Icons.Default.Dashboard),
        NavItem(Routes.EDITOR_QUEUE, "Review Queue", Icons.AutoMirrored.Filled.Article),
        NavItem(com.jvoice.aishorts.studio.StudioRoutes.REVIEW, "AI Shorts review", Icons.Default.Movie)
    )
    else -> emptyList()
}

/**
 * Drawer-based shell used by Editor, News Admin and Super Admin screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScaffold(
    role: UserRole,
    title: String,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit,
    snackbarHostState: SnackbarHostState,
    actions: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val items = drawerItemsFor(role)
    // A Super Admin owns both modules, so offer a direct hop to the other one.
    val moduleSwitcher = LocalModuleSwitcher.current
        ?.takeIf { role == UserRole.SUPER_ADMIN }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("J Voice", style = MaterialTheme.typography.titleLarge)
                            Text(
                                role.label + " • " + role.teluguLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = null) },
                        selected = item.route == currentRoute,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item.route != currentRoute) onNavigate(item.route)
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                if (moduleSwitcher != null) {
                    NavigationDrawerItem(
                        label = {
                            Text(moduleSwitcher.other.emoji + "  " + moduleSwitcher.other.label + " module")
                        },
                        icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null) },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            moduleSwitcher.switchToOtherAsSuperAdmin()
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
                NavigationDrawerItem(
                    label = { Text("Sign out") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onSignOut()
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open menu")
                        }
                    },
                    actions = {
                        actions()
                        ReadNewsButton()
                        ProfileButton()
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = floatingActionButton,
            content = content
        )
    }
}

/* ------------------------------------------------------------------ shared bits */

/** Opens the reader news feed - staff read J Voice like everyone else. */
@Composable
fun ReadNewsButton() {
    val open = LocalOpenReaderNews.current ?: return
    IconButton(onClick = open) {
        Icon(Icons.Default.Newspaper, contentDescription = "Read news")
    }
}

/** The signed-in person's photo (or initials) in the top bar; opens their profile. */
@Composable
fun ProfileButton() {
    val open = LocalOpenProfile.current ?: return
    val session by com.jvoice.core.auth.SessionStore.session.collectAsState()
    val desk = session ?: return
    IconButton(onClick = open) {
        com.jvoice.news.ui.profile.Avatar(url = desk.avatarUrl, name = desk.name, size = 34)
    }
}

@Composable
fun StatGrid(
    stats: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    cell: @Composable (Pair<String, String>, Modifier) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        stats.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { stat -> cell(stat, Modifier.weight(1f)) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
