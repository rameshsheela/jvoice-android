package com.jvoice.news.ui.reporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.StatusChip
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.navigation.ProfileButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.LoadingState
import com.jvoice.news.components.PullToRefreshBox
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.components.StatCard
import com.jvoice.news.components.WorkflowNewsRow
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.User
import com.jvoice.news.navigation.StatGrid
import com.jvoice.news.theme.StatusApproved
import com.jvoice.news.theme.StatusDraft
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSubmitted
import com.jvoice.news.utils.toRelativeTime
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.current

@Composable
fun ReporterBottomBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute.contains("dashboard"),
            onClick = { onNavigate("dashboard") },
            icon = { Icon(Icons.Default.Edit, contentDescription = null) },
            label = { Text("Dashboard") }
        )
        NavigationBarItem(
            selected = currentRoute.contains("my_news"),
            onClick = { onNavigate("my_news") },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text("My News") }
        )
    }
}

/* ------------------------------------------------------------------ dashboard */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReporterDashboardScreen(
    user: User?,
    viewModel: ReporterViewModel,
    onCreateNews: () -> Unit,
    onOpenMyNews: () -> Unit,
    onEditArticle: (String) -> Unit,
    onSignOut: () -> Unit,
    onOpenGroup: (String) -> Unit = { onOpenMyNews() },
    onOpenStory: (String) -> Unit = onEditArticle,
    onOpenReferrals: () -> Unit = {}
) {
    val stats by viewModel.stats.collectAsState()
    // Points for the invite card; quietly absent if the function is not live.
    var referralPoints by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        com.jvoice.core.data.Referrals.referrals().onSuccess { referralPoints = it.points }
    }
    val articles by viewModel.myArticles.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    // Only what the desk sent this reporter - reader broadcasts are not news to them.
    val deskUpdates = notifications.filter { it.targetRole == UserRole.REPORTER }
    val studioNav = com.jvoice.aishorts.studio.LocalStudioNav.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("J Voice", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "Reporter desk · రిపోర్టర్",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    com.jvoice.news.navigation.ReadNewsButton()
                    ProfileButton()
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNews,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create News") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding), "Loading your desk...")
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item { GreetingCard(user = user, filed = stats.total, live = stats.published) }

                val needsWork = stats.sentBack + stats.rejected
                if (needsWork > 0) {
                    item {
                        AttentionCard(
                            text = if (needsWork == 1) "1 story came back from your editor"
                            else "$needsWork stories came back from your editor",
                            onClick = onOpenMyNews
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StoryGroup.entries.forEach { group ->
                            StatusTile(
                                label = group.label,
                                value = articles.count { group.matches(it.status) },
                                color = group.color,
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenGroup(group.key) }
                            )
                        }
                    }
                }

                item { InviteCard(points = referralPoints, onClick = onOpenReferrals) }
                studioNav?.let { studio ->
                    item { com.jvoice.aishorts.studio.AiShortsCard(onOpen = studio.openMine, onCreate = { studio.openCreate(null) }) }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Recent stories",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (articles.isNotEmpty()) {
                            TextButton(onClick = onOpenMyNews) { Text("See all") }
                        }
                    }
                }

                if (articles.isEmpty()) {
                    item {
                        EmptyState(
                            title = "No stories yet",
                            description = "Tap Create News to file your first story.",
                            actionLabel = "Create News",
                            onAction = onCreateNews,
                            modifier = Modifier.height(220.dp)
                        )
                    }
                } else {
                    items(articles.take(5), key = { it.id }) { article ->
                        StoryRow(
                            article = article,
                            categoryName = viewModel.categoryName(article.categoryId),
                            onClick = { onOpenStory(article.id) }
                        )
                    }
                }

                if (deskUpdates.isNotEmpty()) {
                    item {
                        Text(
                            "From your editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    items(deskUpdates.take(4), key = { "n_" + it.id }) { item ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title.current(), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        item.message.current(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        item.timeMillis.toRelativeTime(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
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

/** Brand card at the top: who is signed in, and their totals. */
@Composable
private fun GreetingCard(user: User?, filed: Int, live: Int) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(primary, primary.copy(alpha = 0.78f))))
            .padding(20.dp)
    ) {
        Column {
            Text(greeting + ",", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            Text(
                user?.name?.substringBefore(' ')?.ifBlank { null } ?: "Reporter",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            val sub = listOf(user?.loginId.orEmpty(), user?.location.orEmpty()).filter { it.isNotBlank() }
            if (sub.isNotEmpty()) {
                Text(sub.joinToString(" · "), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                HeroNumber(filed, "Stories filed")
                HeroNumber(live, "Live now")
            }
        }
    }
}

@Composable
private fun HeroNumber(value: Int, label: String) {
    Column {
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AttentionCard(text: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StatusRejected.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = StatusRejected, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Fix it and send it again",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** One number in the status strip: a coloured dot, the count, and what it counts. */
@Composable
private fun StatusTile(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.height(8.dp))
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The four ways a reporter sorts their stories - the dashboard tiles and the
 * My News chips use the same groups, so a tile's count is the list it opens.
 */
enum class StoryGroup(val key: String, val label: String, val statuses: Set<NewsStatus>) {
    WAITING("waiting", "Waiting", setOf(NewsStatus.SUBMITTED, NewsStatus.UNDER_REVIEW, NewsStatus.APPROVED)),
    LIVE("live", "Live", setOf(NewsStatus.PUBLISHED)),
    SENT_BACK("sent_back", "Sent back", setOf(NewsStatus.SENT_BACK, NewsStatus.REJECTED)),
    DRAFTS("drafts", "Drafts", setOf(NewsStatus.DRAFT));

    fun matches(status: NewsStatus) = status in statuses

    val color: Color
        get() = when (this) {
            WAITING -> StatusSubmitted
            LIVE -> StatusPublished
            SENT_BACK -> StatusRejected
            DRAFTS -> StatusDraft
        }

    companion object {
        fun fromKey(key: String?): StoryGroup? = entries.firstOrNull { it.key == key }
    }
}

/** "Invite a reporter" - opens the referral screen; shows points once known. */
@Composable
private fun InviteCard(points: Int?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.PersonAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Invite a reporter",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    "Share your code - earn points when they join",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
            if (points != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        points.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        "points",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            } else {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
    }
}

/** A story in the recent list: its photo, status and headline. */
@Composable
internal fun StoryRow(article: NewsArticle, categoryName: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (article.imageUrl.isNotBlank()) {
                NewsImage(
                    url = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StatusChip(article.status)
                Spacer(Modifier.height(4.dp))
                Text(
                    article.headline.current(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    categoryName + " · " + article.updatedAt.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ my news */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyNewsScreen(
    viewModel: ReporterViewModel,
    onEditArticle: (String) -> Unit,
    onCreateNews: () -> Unit,
    initialGroup: String? = null,
    onOpenStory: (String) -> Unit = onEditArticle,
    onBack: (() -> Unit)? = null
) {
    val articles by viewModel.myArticles.collectAsState()
    var group by remember(initialGroup) { mutableStateOf(StoryGroup.fromKey(initialGroup)) }

    val filtered = group?.let { g -> articles.filter { g.matches(it.status) } } ?: articles

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.label?.let { "My stories · $it" } ?: "My stories") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNews,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Create News") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = group == null,
                    onClick = { group = null },
                    label = { Text("All (" + articles.size + ")") }
                )
                StoryGroup.entries.forEach { g ->
                    val count = articles.count { g.matches(it.status) }
                    FilterChip(
                        selected = group == g,
                        onClick = { group = if (group == g) null else g },
                        label = { Text(g.label + " (" + count + ")") }
                    )
                }
            }

            if (filtered.isEmpty()) {
                EmptyState(
                    title = "Nothing here",
                    description = if (group == null) "You have not filed a story yet."
                    else "No stories in " + group!!.label.lowercase() + " right now.",
                    actionLabel = "Create News",
                    onAction = onCreateNews
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { article ->
                        StoryRow(
                            article = article,
                            categoryName = viewModel.categoryName(article.categoryId),
                            onClick = { onOpenStory(article.id) }
                        )
                    }
                }
            }
        }
    }
}
