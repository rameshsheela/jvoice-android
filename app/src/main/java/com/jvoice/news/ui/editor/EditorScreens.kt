package com.jvoice.news.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.LoadingState
import com.jvoice.news.components.Pill
import com.jvoice.news.components.PullToRefreshBox
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.components.StatCard
import com.jvoice.news.components.WorkflowNewsRow
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.navigation.AdminScaffold
import com.jvoice.news.navigation.Routes
import com.jvoice.news.navigation.StatGrid
import com.jvoice.news.theme.StatusApproved
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSubmitted
import com.jvoice.news.utils.toRelativeTime

/* ------------------------------------------------------------------ dashboard */

@Composable
fun EditorDashboardScreen(
    viewModel: EditorViewModel,
    onNavigate: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val queue by viewModel.queue.collectAsState()
    val handled by viewModel.recentlyHandled.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminScaffold(
        role = UserRole.EDITOR,
        title = "Editor Dashboard",
        currentRoute = Routes.EDITOR_DASHBOARD,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding), "Loading the desk...")
            return@AdminScaffold
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item { SectionHeader("Today", subtitle = "ఈ రోజు") }
                item {
                    StatGrid(
                        stats = listOf(
                            "Awaiting review" to stats.awaitingReview.toString(),
                            "Approved today" to stats.approvedToday.toString(),
                            "Rejected today" to stats.rejectedToday.toString(),
                            "Published today" to stats.publishedToday.toString()
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) { (label, value), modifier ->
                        val accent = when (label) {
                            "Awaiting review" -> StatusSubmitted
                            "Approved today" -> StatusApproved
                            "Rejected today" -> StatusRejected
                            else -> StatusPublished
                        }
                        StatCard(label, value, modifier, accent)
                    }
                }

                item {
                    SectionHeader(
                        "Review queue",
                        subtitle = queue.size.toString() + " article(s) waiting",
                        actionLabel = "Open queue",
                        onAction = { onNavigate(Routes.EDITOR_QUEUE) }
                    )
                }

                if (queue.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Queue is clear",
                            description = "Nothing is waiting for review right now.",
                            modifier = Modifier.height(220.dp)
                        )
                    }
                } else {
                    items(queue.take(4), key = { it.id }) { article ->
                        WorkflowNewsRow(
                            article = article,
                            categoryName = viewModel.categoryName(article.categoryId),
                            onClick = { onOpenReview(article.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }

                if (handled.isNotEmpty()) {
                    item { SectionHeader("Recently handled") }
                    items(handled, key = { "h_" + it.id }) { article ->
                        WorkflowNewsRow(
                            article = article,
                            categoryName = viewModel.categoryName(article.categoryId),
                            onClick = { onOpenReview(article.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ review queue */

@Composable
fun ReviewQueueScreen(
    viewModel: EditorViewModel,
    onNavigate: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val queue by viewModel.queue.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminScaffold(
        role = UserRole.EDITOR,
        title = "Review Queue",
        currentRoute = Routes.EDITOR_QUEUE,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (queue.isEmpty()) {
            EmptyState(
                title = "Nothing to review",
                description = "When a reporter submits an article it lands here.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(queue, key = { it.id }) { article ->
                    WorkflowNewsRow(
                        article = article,
                        categoryName = viewModel.categoryName(article.categoryId),
                        onClick = { onOpenReview(article.id) },
                        trailing = {
                            Column {
                                Pill(
                                    "Submitted " + article.createdAt.toRelativeTime(),
                                    MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    article.location,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
