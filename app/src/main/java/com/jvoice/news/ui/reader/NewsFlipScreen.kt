package com.jvoice.news.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.AsyncImage
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.flagEnabled
import com.jvoice.core.flags.flagOptedIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.BreakingBadge
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.ReportSheet
import com.jvoice.news.components.LoadingState
import com.jvoice.news.components.VideoOrPhoto
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.VerticalCardPager
import com.jvoice.news.data.model.ArticleEngagement
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.Reaction
import com.jvoice.news.data.repository.EngagementRepository
import com.jvoice.news.data.repository.NewsRepository
import com.jvoice.news.data.repository.ReadStateRepository
import com.jvoice.news.utils.shareArticle
import com.jvoice.news.utils.toReadableCount
import com.jvoice.news.utils.toRelativeTime
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.current
import com.jvoice.core.i18n.currentLanguage

/**
 * The News tab: short-news pages turned vertically, with a category chip row on top
 * for filtering.
 *
 * Each page is two hinged leaves - the image above, the story below - which turn as
 * separate sheets and reveal the next article on their reverse. Tapping either leaf
 * opens the full article.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFlipScreen(
    viewModel: ReaderViewModel,
    onOpenArticle: (String) -> Unit,
    onOpenComments: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val allClips by viewModel.clips.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val location by viewModel.selectedLocation.collectAsState()
    val unread by viewModel.unreadCount.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var locationMenuOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // The deck: every published story, unread ones first and the ones this
    // device has already read after them - so what is new is what comes up,
    // and when nothing is new the older stories are still there to swipe.
    // Within each half the chosen category leads and the rest of the news
    // follows, so a category with a single story still swipes on.
    //
    // The order is rebuilt only when the set of stories or the filter
    // changes - deliberately not when the read set changes, and not when a
    // story's counts change. Marking the current card read also bumps its
    // view count, so keying on the articles themselves re-sorted the deck on
    // every swipe and sent the reader's card to the read half at the end.
    val storyIds = allClips.map { it.id }
    val deckIds = remember(storyIds, categoryFilter) {
        val alreadyRead = ReadStateRepository.readIds.value
        fun order(list: List<NewsArticle>) =
            if (categoryFilter == null) list
            else list.filter { it.categoryId == categoryFilter } +
                list.filterNot { it.categoryId == categoryFilter }
        (order(allClips.filterNot { alreadyRead.contains(it.id) }) +
            order(allClips.filter { alreadyRead.contains(it.id) })).map { it.id }
    }
    // The cards themselves always carry the latest copy of each story.
    val cards = remember(deckIds, allClips) {
        val byId = allClips.associateBy { it.id }
        deckIds.mapNotNull(byId::get)
    }

    // The story on screen. A story counts as read when its card comes to rest
    // in front of the reader - never merely because it entered the deck - and
    // the first time that happens on this device it counts as one view.
    var shownId by remember { mutableStateOf<String?>(null) }
    fun onCardShown(article: NewsArticle) {
        shownId = article.id
        if (ReadStateRepository.markRead(article.id)) NewsRepository.registerView(article.id)
    }

    // When the deck is rebuilt - a story published while reading, a filter
    // change - the flip is re-seated on the story that was showing, if it is
    // still there, so nothing moves under the reader's thumb.
    val pageOnReset = cards.indexOfFirst { it.id == shownId }.coerceAtLeast(0)

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "J Voice",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // Opt-in, unlike the other flags: the picker only
                            // appears once an admin has switched it on.
                            if (flagOptedIn(FeatureFlags.Keys.NEWS_LOCATION_DROPDOWN)) {
                                Spacer(Modifier.width(6.dp))
                                Box {
                                    Row(
                                        Modifier
                                            .clip(RoundedCornerShape(50))
                                            .clickable { locationMenuOpen = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = "Change location",
                                            modifier = Modifier.size(15.dp)
                                        )
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
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    // Search, notifications and the profile. The profile moved
                    // up here from the pill bar so the bar holds content tabs only.
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
                        IconButton(onClick = onOpenProfile) {
                            Icon(Icons.Default.Person, contentDescription = "Profile")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // ---- category filter chips
                if (flagEnabled(FeatureFlags.Keys.LOCATION_CHIPS)) {
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                            label = { Text("All") }
                        )
                        categories.filter { it.isEnabled }.forEach { category ->
                            val count = allClips.count { it.categoryId == category.id }
                            if (count > 0) {
                                FilterChip(
                                    selected = categoryFilter == category.id,
                                    onClick = {
                                        categoryFilter = if (categoryFilter == category.id) null else category.id
                                    },
                                    label = { Text(category.emoji + " " + category.name.en) }
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding))
            return@Scaffold
        }
        if (cards.isEmpty()) {
            EmptyState(
                title = "Nothing here yet",
                description = "Published articles appear here as news pages.",
                modifier = Modifier.padding(padding),
                actionLabel = if (categoryFilter != null) "Clear filter" else null,
                onAction = if (categoryFilter != null) ({ categoryFilter = null }) else null
            )
            return@Scaffold
        }

        CompositionLocalProvider(
            LocalReaderSnackbar provides snackbarHostState,
            LocalReaderName provides (viewModel.currentUserName())
        ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // One card per story - photo over text - sliding up as a unit.
            // See components/VerticalCardPager.kt
            VerticalCardPager(
                count = cards.size,
                modifier = Modifier.fillMaxSize(),
                topFraction = 0.36f,
                resetKey = deckIds,
                pageOnReset = pageOnReset,
                onPageSettled = { index ->
                    cards.getOrNull(index)?.let(::onCardShown)
                },
                topPanel = { index ->
                    ImageLeaf(
                        article = cards[index],
                        categoryName = viewModel.categoryName(cards[index].categoryId),
                        isSaved = savedIds.contains(cards[index].id),
                        onToggleSave = {
                            val saved = viewModel.toggleSave(cards[index].id)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (saved) "Saved to your bookmarks" else "Removed from bookmarks"
                                )
                            }
                        },
                        onShare = { context.shareArticle(cards[index]) },
                        onClick = { onOpenArticle(cards[index].id) }
                    )
                },
                bottomPanel = { index ->
                    StoryLeaf(
                        article = cards[index],
                        isFirstPage = index == 0,
                        onOpenComments = { onOpenComments(cards[index].id) }
                    )
                }
            )
        }
        }
    }
}

/** Lets the leaves reach the screen's snackbar and the signed-in reader's name. */
val LocalReaderSnackbar = staticCompositionLocalOf<SnackbarHostState?> { null }
val LocalReaderName = staticCompositionLocalOf { "Reader" }

/** Upper leaf: the image, carrying category, time and the save / share actions. */
@Composable
private fun ImageLeaf(
    article: NewsArticle,
    categoryName: String,
    isSaved: Boolean,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
    onClick: () -> Unit
) {
    // A story with a video plays it here, in the photo's place, once the
    // reader taps play. The tap-through to the article is only offered when
    // the story has one.
    VideoOrPhoto(
        videoUrl = article.videoUrls.firstOrNull { it.isNotBlank() },
        imageUrl = article.imageUrl,
        contentDescription = article.headline.current(),
        framed = false,
        onPhotoClick = if (article.detailEnabled) onClick else null,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (article.isBreaking) {
                BreakingBadge()
                Spacer(Modifier.width(8.dp))
            }
            // A badge when a published AI video (Clips) was made from this story.
            val clips by com.jvoice.news.data.repository.ClipsRepository.clips.collectAsState()
            val hasVideo = remember(clips, article.id) { clips.any { it.relatedArticleId == article.id } }
            if (hasVideo) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "Video",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Who filed it: a name on the photo, top-right, opposite the badges.
        // Deliberately just the name - a portrait here competes with the
        // story's own image, and the initials fallback read as a second story.
        if (article.reporterName.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
            ) {
                Text(
                    article.reporterName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .widthIn(max = 160.dp)
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.20f)
            ) {
                Text(
                    categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                (article.publishedAt ?: article.createdAt).toRelativeTime(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSave, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = if (isSaved) "Remove bookmark" else "Save",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onShare, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Lower leaf: the story. */
@Composable
private fun StoryLeaf(
    article: NewsArticle,
    isFirstPage: Boolean,
    onOpenComments: () -> Unit
) {
    val engagementMap by EngagementRepository.engagement.collectAsState()
    val engagement = engagementMap[article.id] ?: ArticleEngagement(article.id)
    var showReport by remember { mutableStateOf(false) }
    val snackbar = LocalReaderSnackbar.current
    val scope = rememberCoroutineScope()
    val reporterName = LocalReaderName.current

    if (showReport) {
        ReportSheet(
            headline = article.headline.current(),
            onDismiss = { showReport = false },
            onSubmit = { reason, suggestion ->
                EngagementRepository.submitReport(article.id, reason, suggestion, reporterName)
                showReport = false
                scope.launch {
                    snackbar?.showSnackbar("Thanks - your report was sent to the desk")
                }
            }
        )
    }

    // Only the image opens the article; the story panel is not tappable.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            article.headline.current(),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(10.dp))
        Text(
            article.shortDescription.current(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        // The excerpt has to be recomputed when the language changes, not just
        // when the story does - keying only on the id would leave a Telugu
        // excerpt under an English headline after a toggle.
        val language = currentLanguage()
        val body = article.content.get(language)
        val excerpt = remember(article.id, language) {
            body.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.startsWith("(") }
                .orEmpty()
        }

        // ---------------------------------- the part of the leaf that flexes
        // The leaf is a fixed height and the reaction bar below must never be
        // pushed off it, so everything between the description and the bar
        // lives in this weighted block: it gets whatever is left and no more.
        // Inside it the excerpt is what gives way - it is measured with the
        // space that remains after the location line and ellipsises to fit.
        Column(
            Modifier
                .weight(1f)
                .clipToBounds()
        ) {
            Column(Modifier.weight(1f, fill = false)) {
                if (excerpt.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        excerpt,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 9,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    article.location + "  •  " + article.views.toReadableCount() + " views",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isFirstPage) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Swipe up for the next story  •  tap the photo to read",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // ---------------------------------------- like / dislike / comment
        HorizontalDivider()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReactionButton(
                icon = if (engagement.myReaction == Reaction.LIKE) Icons.Filled.ThumbUp
                else Icons.Outlined.ThumbUp,
                label = engagement.likes.toReadableCount(),
                active = engagement.myReaction == Reaction.LIKE,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = { EngagementRepository.toggleLike(article.id) }
            )
            Spacer(Modifier.width(6.dp))
            ReactionButton(
                icon = if (engagement.myReaction == Reaction.DISLIKE) Icons.Filled.ThumbDown
                else Icons.Outlined.ThumbDown,
                label = engagement.dislikes.toReadableCount(),
                active = engagement.myReaction == Reaction.DISLIKE,
                activeColor = MaterialTheme.colorScheme.error,
                onClick = { EngagementRepository.toggleDislike(article.id) }
            )
            // No Comment button: comments are off in the Play build until they
            // can be reported and moderated (Play's user-generated content policy).
            Spacer(Modifier.weight(1f))
            // Report is a rare action, so it is kept small and out of the way in
            // the corner rather than weighted like the reactions beside it.
            ReportButton(
                modifier = Modifier.align(Alignment.Bottom),
                onClick = { showReport = true }
            )
        }
    }
}

/**
 * The Report control in the corner of the story leaf: a small flag and
 * nothing else. The label lives in the content description for readers who
 * need it; everyone else gets an unobtrusive corner icon.
 */
@Composable
private fun ReportButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            // Generous touch target around a small glyph.
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = "Report this story",
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(14.dp)
        )
    }
}


@Composable
private fun ReactionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = if (active) activeColor.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}
