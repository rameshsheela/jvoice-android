package com.jvoice.news.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvoice.core.i18n.current
import com.jvoice.core.reader.ReaderDirectory
import com.jvoice.core.reader.ReaderProfile
import com.jvoice.news.components.EmptyState
import com.jvoice.news.data.model.ArticleComment
import com.jvoice.news.data.repository.EngagementRepository
import com.jvoice.news.utils.toReadableCount
import com.jvoice.news.utils.toRelativeTime
import kotlinx.coroutines.launch

/**
 * Comments on one article, threaded one level deep, with the reaction counts in
 * the header.
 *
 * Live from Firestore: the listener is attached for as long as this screen is
 * showing, so another reader's comment appears without a refresh. A reply is
 * posted with the parent's id and is drawn indented beneath it; replying to a
 * reply attaches to the same parent, which keeps every thread flat enough to
 * read on a phone.
 *
 * The author name is the one on the reader's profile, or "Reader" if they have
 * not set one - a name is not required to speak. Names are shown *live*: a
 * reader who renames themselves is renamed on their old comments too, on
 * every phone, because the screen looks each commenter up in
 * [ReaderDirectory] rather than trusting the name stored with the comment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    viewModel: ReaderViewModel,
    articleId: String,
    authorName: String,
    onBack: () -> Unit
) {
    // Attach the article's comment listener for the life of the screen.
    DisposableEffect(articleId) {
        val registration = EngagementRepository.watchComments(articleId)
        onDispose { registration?.remove() }
    }

    val allComments by EngagementRepository.comments.collectAsState()
    val engagementMap by EngagementRepository.engagement.collectAsState()
    val profileName by ReaderProfile.name.collectAsState()
    val liveNames by ReaderDirectory.names.collectAsState()

    // Follow every commenter in view, so a rename lands here as it happens.
    LaunchedEffect(allComments) {
        ReaderDirectory.watch(allComments.map { it.deviceId }.toSet())
    }

    // The freshest name known for a comment's author: this device's own
    // profile for own comments (instant, no round trip), the directory for
    // everyone else, and the name stored with the comment when neither knows.
    fun displayName(comment: ArticleComment): String = when {
        comment.isOwn && profileName.isNotBlank() -> profileName
        else -> liveNames[comment.deviceId] ?: comment.authorName
    }

    val topLevel = remember(allComments, articleId) {
        EngagementRepository.topLevelComments(articleId)
    }
    val replies = remember(allComments) {
        allComments.filter { it.parentId != null }
            .groupBy { it.parentId!! }
            .mapValues { (_, list) -> list.sortedBy { it.timeMillis } }
    }
    val total = topLevel.size + replies.values.sumOf { it.size }
    val engagement = engagementMap[articleId]
    val article = viewModel.articleById(articleId)
    val name = profileName.ifBlank { authorName }

    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ArticleComment?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun post() {
        val target = replyingTo
        val ok = EngagementRepository.addComment(
            articleId = articleId,
            authorName = name,
            text = draft,
            parentId = target?.id
        )
        if (ok) {
            draft = ""
            replyingTo = null
            scope.launch {
                snackbarHostState.showSnackbar(if (target == null) "Comment posted" else "Reply posted")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Comments", style = MaterialTheme.typography.titleMedium)
                        Text(
                            total.toString() + " comments  •  " +
                                (engagement?.likes ?: 0).toReadableCount() + " likes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        // Edge-to-edge: clear the system bar, then the keyboard
                        // when it is up. Each padding consumes what it applies,
                        // so the two never stack.
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Who the draft answers, with a way out of it.
                    replyingTo?.let { target ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Replying to " + displayName(target),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            IconButton(
                                onClick = { replyingTo = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Cancel reply",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = {
                                if (it.length <= EngagementRepository.MAX_COMMENT_LENGTH) draft = it
                            },
                            placeholder = {
                                Text(if (replyingTo == null) "Add a comment..." else "Write a reply...")
                            },
                            maxLines = 4,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = ::post, enabled = draft.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Post",
                                tint = if (draft.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (article != null) {
                Text(
                    article.headline.current(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
                HorizontalDivider()
            }

            if (topLevel.isEmpty()) {
                EmptyState(
                    title = "No comments yet",
                    description = "Be the first to say something about this story."
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    topLevel.forEach { comment ->
                        item(key = comment.id) {
                            CommentRow(
                                comment = comment,
                                authorName = displayName(comment),
                                liked = EngagementRepository.hasLikedComment(comment.id),
                                onLike = { EngagementRepository.likeComment(comment) },
                                onReply = { replyingTo = comment }
                            )
                        }
                        // The thread: indented, oldest first, so it reads downwards.
                        replies[comment.id]?.forEach { reply ->
                            item(key = reply.id) {
                                CommentRow(
                                    comment = reply,
                                    authorName = displayName(reply),
                                    liked = EngagementRepository.hasLikedComment(reply.id),
                                    onLike = { EngagementRepository.likeComment(reply) },
                                    // A reply to a reply joins the same thread.
                                    onReply = { replyingTo = comment },
                                    indent = 44.dp
                                )
                            }
                        }
                        item { HorizontalDivider(Modifier.padding(start = 60.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: ArticleComment,
    authorName: String,
    liked: Boolean,
    onLike: () -> Unit,
    onReply: () -> Unit,
    indent: androidx.compose.ui.unit.Dp = 0.dp
) {
    val isReply = indent > 0.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp + indent, end = 16.dp, top = if (isReply) 6.dp else 12.dp, bottom = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (comment.isOwn) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(if (isReply) 28.dp else 34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    authorName.take(1).uppercase(),
                    style = if (isReply) MaterialTheme.typography.labelLarge
                    else MaterialTheme.typography.titleSmall
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    authorName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (comment.isOwn) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "You",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    comment.timeMillis.toRelativeTime(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    Modifier
                        .clickable(onClick = onLike)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like comment",
                        modifier = Modifier.size(14.dp),
                        tint = if (liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        comment.likes.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (liked) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    Modifier
                        .clickable(onClick = onReply)
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Reply",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
