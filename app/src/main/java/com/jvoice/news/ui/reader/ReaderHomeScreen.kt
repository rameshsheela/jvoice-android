package com.jvoice.news.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.flagEnabled
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.CompactNewsCard
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.ErrorState
import com.jvoice.news.components.FeaturedNewsCard
import com.jvoice.news.components.LoadingState
import com.jvoice.news.components.NewsCard
import com.jvoice.news.components.PullToRefreshBox
import com.jvoice.news.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderHomeScreen(
    viewModel: ReaderViewModel,
    onOpenArticle: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCategory: (String) -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val feed by viewModel.feed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val error by viewModel.errorMessage.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val location by viewModel.selectedLocation.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var locationMenuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "J Voice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (flagEnabled(FeatureFlags.Keys.NEWS_LOCATION_DROPDOWN)) {
                            Spacer(Modifier.width(10.dp))
                            Box {
                                TextButton(onClick = { locationMenuOpen = true }) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = "Change location",
                                        modifier = Modifier.height(16.dp)
                                    )
                                    Spacer(Modifier.width(2.dp))
                                    Text(location, style = MaterialTheme.typography.labelLarge)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = locationMenuOpen,
                                    onDismissRequest = { locationMenuOpen = false }
                                ) {
                                    viewModel.locations.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { Text(loc) },
                                            onClick = {
                                                viewModel.setLocation(loc)
                                                locationMenuOpen = false
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Location set to " + loc)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search news")
                    }
                    IconButton(onClick = onOpenNotifications) {
                        if (unread > 0) {
                            BadgedBox(badge = { Badge { Text(unread.toString()) } }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                            }
                        } else {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            isLoading -> LoadingState(Modifier.padding(padding))
            error != null -> ErrorState(
                message = error ?: "Something went wrong",
                modifier = Modifier.padding(padding),
                onRetry = viewModel::retry
            )
            feed.latest.isEmpty() -> EmptyState(
                title = "No published news yet",
                description = "Once an editor approves an article it appears here.",
                modifier = Modifier.padding(padding)
            )
            else -> PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                HomeContent(
                    feed = feed,
                    savedIds = savedIds,
                    categoryName = viewModel::categoryName,
                    onOpenArticle = onOpenArticle,
                    onOpenCategory = onOpenCategory,
                    onToggleSave = { id ->
                        val saved = viewModel.toggleSave(id)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (saved) "Saved to your bookmarks" else "Removed from bookmarks"
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeContent(
    feed: HomeFeed,
    savedIds: Set<String>,
    categoryName: (String) -> String,
    onOpenArticle: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onToggleSave: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        if (feed.breaking.isNotEmpty()) {
            item { SectionHeader("Breaking News", subtitle = "బ్రేకింగ్ న్యూస్") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feed.breaking, key = { it.id }) { article ->
                        FeaturedNewsCard(
                            article = article,
                            categoryName = categoryName(article.categoryId),
                            onClick = { onOpenArticle(article.id) },
                            modifier = Modifier.width(300.dp)
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        if (feed.trending.isNotEmpty()) {
            item { SectionHeader("Trending Now", subtitle = "ట్రెండింగ్") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(feed.trending, key = { _, a -> a.id }) { index, article ->
                        CompactNewsCard(
                            article = article,
                            categoryName = categoryName(article.categoryId),
                            onClick = { onOpenArticle(article.id) },
                            rank = index + 1
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        item { SectionHeader("Latest News", subtitle = "తాజా వార్తలు") }
        items(feed.latest, key = { "latest_" + it.id }) { article ->
            NewsCard(
                article = article,
                categoryName = categoryName(article.categoryId),
                isSaved = savedIds.contains(article.id),
                onClick = { onOpenArticle(article.id) },
                onToggleSave = { onToggleSave(article.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }

        feed.categorySections.forEach { (category, articles) ->
            item(key = "header_" + category.id) {
                SectionHeader(
                    title = category.emoji + "  " + category.name.en,
                    subtitle = category.name.te,
                    actionLabel = "See all",
                    onAction = { onOpenCategory(category.id) }
                )
            }
            item(key = "row_" + category.id) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(articles, key = { it.id }) { article ->
                        CompactNewsCard(
                            article = article,
                            categoryName = category.name.en,
                            onClick = { onOpenArticle(article.id) }
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        item {
            Text(
                "You have reached the end of the demo feed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            )
        }
    }
}
