package com.jvoice.news.ui.reporter

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jvoice.core.data.Referrals
import com.jvoice.core.i18n.current
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.StatusChip
import com.jvoice.news.data.model.NewsArticle
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.theme.StatusDraft
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSubmitted
import com.jvoice.news.utils.toFullDate
import kotlinx.coroutines.launch

/**
 * The public web address of a published story. Shared by a reporter it carries
 * their code, so a reader who gets the app from the story page is credited to
 * them (functions/referrals.js creditInstall).
 */
fun storyLink(articleId: String): String {
    val code = com.jvoice.core.auth.SessionStore.session.value?.loginId.orEmpty()
    return "https://jvoicetelugu.com/read/$articleId" + if (code.isNotBlank()) "?ref=$code" else ""
}

/** Opens Android's share sheet with [text]. */
private fun shareText(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/* ================================================================ story detail */

/**
 * One of the reporter's own stories, in full: what was filed, where it stands
 * (with the desk's note if it came back), how many readers it reached once
 * live, and a link to share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReporterStoryDetailScreen(
    viewModel: ReporterViewModel,
    articleId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit
) {
    val articles by viewModel.myArticles.collectAsState()
    val article = articles.firstOrNull { it.id == articleId }
        ?: com.jvoice.news.data.repository.NewsRepository.articleById(articleId)
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && article != null) {
        ConfirmDialog(
            title = "Delete draft?",
            message = "This draft is removed for good. It was never sent to your editor.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteDraft(article.id)
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Story") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (article == null) {
            EmptyState(
                title = "Story not found",
                description = "It may have been removed.",
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        val live = article.status == NewsStatus.PUBLISHED
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (article.imageUrl.isNotBlank()) {
                NewsImage(
                    url = article.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(18.dp))
                )
            }
            if (article.photoUrls.isNotEmpty()) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    article.photoUrls.forEach { url ->
                        NewsImage(
                            url = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 120.dp, height = 80.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusChip(article.status)
                Text(
                    article.headline.current(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    listOf(viewModel.categoryName(article.categoryId), article.location)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // The desk's word, when the story came back.
            val rejection = article.rejectionReason?.current().orEmpty()
            val editorNote = article.editorNote?.current().orEmpty()
            val note = rejection.ifBlank { editorNote }.ifBlank { null }
            if (note != null && article.status.isEditableByReporter) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StatusRejected.copy(alpha = 0.10f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            if (article.status == NewsStatus.REJECTED) "Rejected by your editor" else "Your editor sent it back",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusRejected
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(note, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Actions
            val headlineText = article.headline.current()
            // Turn this story into an AI video (AI Shorts, on the studio backend).
            com.jvoice.aishorts.studio.LocalStudioNav.current?.let { studio ->
                androidx.compose.material3.OutlinedButton(
                    onClick = { studio.openCreate(article.id) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🎬 Make AI video") }
            }
            if (live) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            shareText(
                                context,
                                headlineText + "\n" + storyLink(article.id),
                                "Share story"
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                    OutlinedButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(storyLink(article.id)))
                            scope.launch { snackbar.showSnackbar("Link copied") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Copy link")
                    }
                }
            }
            if (article.status.isEditableByReporter) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { onEdit(article.id) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (article.status == NewsStatus.DRAFT) "Edit draft" else "Edit & resend")
                    }
                    if (article.status == NewsStatus.DRAFT) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Delete")
                        }
                    }
                }
            }

            ReachCard(article)
            TimelineCard(article)

            // The story itself
            DetailCard(title = "The story") {
                val short = article.shortDescription.current()
                if (short.isNotBlank()) {
                    Text(short, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                }
                Text(article.content.current(), style = MaterialTheme.typography.bodyLarge)
                if (article.videoUrls.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🎬 " + article.videoUrls.size + (if (article.videoUrls.size == 1) " video" else " videos") + " attached",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** How many readers the story reached - views, reactions, comments. */
@Composable
private fun ReachCard(article: NewsArticle) {
    DetailCard(title = "Reach") {
        if (article.status != NewsStatus.PUBLISHED) {
            Text(
                "Reach shows once it is live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(Modifier.fillMaxWidth()) {
                ReachNumber(article.views, "Views", Modifier.weight(1f))
                ReachNumber(article.likes, "Likes", Modifier.weight(1f))
                ReachNumber(article.dislikes, "Dislikes", Modifier.weight(1f))
                ReachNumber(article.comments, "Comments", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ReachNumber(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** When the story was filed, last changed and published. */
@Composable
private fun TimelineCard(article: NewsArticle) {
    DetailCard(title = "Timeline") {
        TimelineRow("Filed", article.createdAt.toFullDate(), StatusDraft)
        if (article.updatedAt > article.createdAt + 1000) {
            TimelineRow("Last updated", article.updatedAt.toFullDate(), StatusSubmitted)
        }
        val published = article.publishedAt
        if (published != null && article.status == NewsStatus.PUBLISHED) {
            TimelineRow("Published", published.toFullDate(), StatusPublished)
        } else {
            TimelineRow("Published", "Not yet", MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun TimelineRow(label: String, value: String, color: Color) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* =================================================================== referrals */

/**
 * The reporter's invitations: their code and link to share, points earned,
 * and everyone who applied with the code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<Referrals.Summary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        Referrals.referrals()
            .onSuccess { summary = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invite a reporter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            summary == null -> EmptyState(
                title = error ?: "Referrals are not available yet.",
                description = "Try again in a little while.",
                modifier = Modifier.padding(padding)
            )
            else -> {
                val s = summary!!
                val invite = "Join J Voice Telugu as a reporter. Apply here: ${s.link} (code ${s.code})"
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        val primary = MaterialTheme.colorScheme.primary
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(primary)
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Your referral code", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                            Text(
                                s.code.ifBlank { "—" },
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                                RefNumber(s.points, "Points")
                                RefNumber(s.referrals.count { it.status == "JOINED" }, "Joined")
                                RefNumber(s.referrals.size, "Invited")
                                RefNumber(s.installs, "App installs")
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { shareText(context, invite, "Invite a reporter") },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = primary
                                    )
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Share invite")
                                }
                                TextButton(onClick = {
                                    clipboard.setText(AnnotatedString(invite))
                                    scope.launch { snackbar.showSnackbar("Invite copied") }
                                }) { Text("Copy", color = Color.White) }
                            }
                        }
                    }
                    item {
                        // Readers: the app, with this reporter's code riding along.
                        val appInvite = "Read local news from your town on the J Voice Telugu app: " +
                            s.appLink.ifBlank { "https://jvoicetelugu.com/app?ref=" + s.code } +
                            " (my code: " + s.code + ")"
                        androidx.compose.material3.Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Share the app with readers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "+" + s.pointsPerInstall + " points for every phone that installs J Voice from your link " +
                                        "(or types your code in the app).",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(onClick = { shareText(context, appInvite, "Share the J Voice app") }) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Share app")
                                    }
                                    OutlinedButton(onClick = {
                                        clipboard.setText(AnnotatedString(appInvite))
                                        scope.launch { snackbar.showSnackbar("Link copied") }
                                    }) { Text("Copy") }
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "Share your link. The person applies on jvoicetelugu.com, the admin checks it, " +
                                "and when their login is made you get +" + s.pointsPerJoin + " points.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text(
                            "People you invited",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (s.referrals.isEmpty()) {
                        item {
                            Text(
                                "No one yet. Share your invite with someone who wants to report for J Voice.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(s.referrals, key = { it.id }) { r -> ReferralRow(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RefNumber(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ReferralRow(r: Referrals.Referral) {
    val (label, color) = when (r.status) {
        "JOINED" -> "Joined" to StatusPublished
        "DECLINED" -> "Declined" to StatusRejected
        else -> "Applied" to StatusSubmitted
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name.ifBlank { "Applicant" }, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        r.area.ifBlank { null },
                        if (r.appliedAt > 0) "Applied " + r.appliedAt.toFullDate() else null,
                        r.joinedAt?.let { "Joined " + it.toFullDate() },
                        r.loginId.ifBlank { null }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
