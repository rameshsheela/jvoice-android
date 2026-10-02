package com.jvoice.news.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jvoice.news.components.ArticleVideo
import com.jvoice.news.components.VideoOrPhoto
import com.jvoice.news.components.BreakingBadge
import com.jvoice.news.components.CompactNewsCard
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.Pill
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.data.repository.NewsRepository
import com.jvoice.news.utils.shareArticle
import com.jvoice.news.utils.toFullDate
import com.jvoice.news.utils.toReadableCount
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.current

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(
    viewModel: ReaderViewModel,
    articleId: String,
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit
) {
    val allArticles by NewsRepository.articles.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val article = remember(articleId, allArticles) { viewModel.articleById(articleId) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(articleId) { viewModel.registerView(articleId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (article != null) {
                        IconButton(onClick = {
                            viewModel.reportArticle(article.id)
                            scope.launch {
                                snackbarHostState.showSnackbar("Reported to moderators (demo)")
                            }
                        }) {
                            Icon(Icons.Default.Flag, contentDescription = "Report article")
                        }
                        IconButton(onClick = { context.shareArticle(article) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share")
                        }
                        IconButton(onClick = {
                            val saved = viewModel.toggleSave(article.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (saved) "Saved to bookmarks" else "Removed from bookmarks"
                                )
                            }
                        }) {
                            Icon(
                                if (savedIds.contains(article.id)) Icons.Default.Bookmark
                                else Icons.Default.BookmarkBorder,
                                contentDescription = "Save"
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (article == null) {
            EmptyState(
                title = "Article not available",
                description = "This demo article may have been removed by an admin.",
                modifier = Modifier.padding(padding),
                actionLabel = "Go back",
                onAction = onBack
            )
            return@Scaffold
        }

        val related = remember(article.id, allArticles) { viewModel.related(article) }
        val moreInCategory = remember(article.id, allArticles) { viewModel.moreFromCategory(article) }
        val categoryName = viewModel.categoryName(article.categoryId)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(categoryName, MaterialTheme.colorScheme.primary)
                        if (article.isBreaking) {
                            Spacer(Modifier.width(8.dp))
                            BreakingBadge()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(article.headline.current(), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        article.shortDescription.current(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // The hero: the photo, or the story's first video in its place once
            // the reader taps play. Any further videos follow in the same frame.
            val videos = article.videoUrls.filter { it.isNotBlank() }
            item {
                VideoOrPhoto(
                    videoUrl = videos.firstOrNull(),
                    imageUrl = article.imageUrl,
                    contentDescription = article.headline.current(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
            items(videos.drop(1), key = { "video:" + it }) { url ->
                ArticleVideo(
                    url = url,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                article.reporterName.take(1),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(article.reporterName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            article.location + "  •  " + (article.publishedAt ?: article.createdAt).toFullDate(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        article.views.toReadableCount() + " views",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp)) }

            item {
                Text(
                    article.content.current(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            if (article.tags.isNotEmpty()) {
                item {
                    Row(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        article.tags.take(4).forEach { tag ->
                            AssistChip(onClick = {}, label = { Text("#" + tag) })
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }

            if (related.isNotEmpty()) {
                item { SectionHeader("Related News", subtitle = "సంబంధిత వార్తలు") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(related, key = { it.id }) { item ->
                            CompactNewsCard(
                                article = item,
                                categoryName = viewModel.categoryName(item.categoryId),
                                onClick = { onOpenArticle(item.id) }
                            )
                        }
                    }
                }
            }

            if (moreInCategory.isNotEmpty()) {
                item {
                    SectionHeader(
                        "More in " + categoryName,
                        subtitle = "Same category"
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(moreInCategory, key = { it.id }) { item ->
                            CompactNewsCard(
                                article = item,
                                categoryName = categoryName,
                                onClick = { onOpenArticle(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
