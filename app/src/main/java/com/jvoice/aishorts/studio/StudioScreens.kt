package com.jvoice.aishorts.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jvoice.core.auth.SessionStore
import com.jvoice.core.data.StoryMedia
import com.jvoice.news.components.ArticleVideo
import com.jvoice.news.components.NewsImage
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ shared */

@Composable
private fun StatusBadge(status: String, label: String) {
    val color = when (status) {
        "published" -> MaterialTheme.colorScheme.tertiary
        "pending_review" -> MaterialTheme.colorScheme.primary
        "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun ShortRow(s: AiShort, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val thumb = s.posterUrl.ifBlank { s.thumbnailUrl }
            if (thumb.startsWith("http")) {
                NewsImage(url = thumb, contentDescription = null, modifier = Modifier.size(width = 54.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)))
            } else {
                Box(
                    Modifier.size(width = 54.dp, height = 90.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.outline) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                StatusBadge(s.status, s.statusLabel)
                Spacer(Modifier.height(4.dp))
                Text(s.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(s.reporterName, if (s.status == "published") s.views.toString() + " views" else "").filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* -------------------------------------------------------- reporter: list */

/** The reporter's own AI videos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAiVideosScreen(onBack: () -> Unit, onCreate: () -> Unit, onOpen: (String) -> Unit) {
    val shorts by remember { StudioShorts.mine() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My AI videos") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Default.Add, contentDescription = null) }, text = { Text("Make AI video") })
        }
    ) { padding ->
        val list = shorts
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No AI videos yet", style = MaterialTheme.typography.titleMedium)
                Text("Turn one of your stories into a short video - it speaks in your voice once your AI voice is set up in your profile.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) { items(list, key = { it.id }) { s -> ShortRow(s) { onOpen(s.id) } } }
        }
    }
}

/* ------------------------------------------------------ reporter: create */

/**
 * Make an AI video: story → script → template → pictures → generate.
 * Starting from a J Voice story fills the story and its photos in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateShortScreen(articleId: String?, onBack: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var short by remember { mutableStateOf(StudioShorts.blank()) }
    val media = remember { mutableStateListOf<ShortMedia>() }
    var profile by remember { mutableStateOf<StudioProfile?>(null) }
    var scripting by remember { mutableStateOf(false) }
    var suggesting by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    val steps = remember {
        mutableStateListOf(GenStep("Narration"), GenStep("Voice"), GenStep("Avatar"), GenStep("Video"), GenStep("Save"))
    }
    var stepMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { profile = StudioShorts.profile() }
    LaunchedEffect(articleId) {
        if (articleId.isNullOrBlank()) return@LaunchedEffect
        val a = StudioShorts.article(articleId) ?: return@LaunchedEffect snackbar.showSnackbar("That story could not be opened").let { }
        short = short.copy(title = a.headline, content = a.content, language = a.language, articleId = articleId)
        media.clear()
        a.photos.take(6).forEachIndexed { i, url -> media += ShortMedia("article-$i", "photo", url, "Story photo ${i + 1}", "upload") }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uploading += uris.size
        scope.launch {
            for (uri in uris) {
                StoryMedia.upload(context, uri, StoryMedia.PHOTOS)
                    .onSuccess { url -> media += ShortMedia(StudioShorts.newId("m"), "photo", url, "Photo", "upload") }
                    .onFailure { snackbar.showSnackbar(it.message ?: "Upload failed") }
                uploading -= 1
            }
        }
    }

    val canGenerate = short.title.isNotBlank() && short.content.trim().length >= 20 && !generating && uploading == 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Make AI video") },
                navigationIcon = { IconButton(onClick = onBack, enabled = !generating) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Button(
                    enabled = canGenerate,
                    onClick = {
                        generating = true
                        for (i in steps.indices) steps[i] = steps[i].copy(state = "pending")
                        scope.launch {
                            val result = StudioShorts.generate(short.copy(media = media.toList())) { i, state, msg ->
                                steps[i] = steps[i].copy(state = state)
                                stepMessage = msg
                            }
                            generating = false
                            result
                                .onSuccess { onDone(it.id) }
                                .onFailure { e ->
                                    steps.indices.firstOrNull { steps[it].state == "active" }?.let { steps[it] = steps[it].copy(state = "error") }
                                    snackbar.showSnackbar(e.message ?: "Could not make the video")
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (generating) "Making your video…" else "🎬 Generate video") }
                Text(
                    "Uses real AI - takes 1–5 minutes. Stay on this screen.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (generating || steps.any { it.state != "pending" }) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Progress", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        steps.forEach { st ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (st.state) {
                                    "active" -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    "done" -> Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                    else -> Box(Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    st.label + when (st.state) { "skipped" -> " - skipped"; "error" -> " - failed"; else -> "" },
                                    color = if (st.state == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        if (stepMessage.isNotBlank()) Text(stepMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // 1. Story
            Text("1. Story", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("te" to "తెలుగు", "en" to "English").forEach { (code, label) ->
                    FilterChip(selected = short.language == code, onClick = { short = short.copy(language = code) }, label = { Text(label) }, enabled = !generating)
                }
            }
            OutlinedTextField(
                value = short.title, onValueChange = { short = short.copy(title = it) }, enabled = !generating,
                label = { Text("Headline") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = short.content, onValueChange = { short = short.copy(content = it) }, enabled = !generating,
                label = { Text("The story (5–10 lines)") }, minLines = 5, modifier = Modifier.fillMaxWidth()
            )

            // 2. Script
            Text("2. Narration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = short.script, onValueChange = { short = short.copy(script = it) }, enabled = !generating,
                label = { Text("What the video says (leave empty to let AI write it)") }, minLines = 4, modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(
                enabled = !scripting && !generating && short.content.trim().length >= 20,
                onClick = {
                    scripting = true
                    scope.launch {
                        StudioShorts.generateScript(short.content, short.language, SessionStore.session.value?.name.orEmpty(), profile?.location.orEmpty())
                            .onSuccess { short = short.copy(script = it) }
                            .onFailure { snackbar.showSnackbar(it.message ?: "Could not write the script") }
                        scripting = false
                    }
                }
            ) { Text(if (scripting) "Writing…" else "✨ Write it with AI") }

            // 3. Template
            Text("3. Look", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SHORT_TEMPLATES.forEach { t ->
                    FilterChip(selected = short.templateId == t.id, onClick = { short = short.copy(templateId = t.id) }, label = { Text(t.name) }, enabled = !generating)
                }
            }
            Text(SHORT_TEMPLATES.first { it.id == short.templateId }.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 4. Pictures
            Text("4. Pictures", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (media.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    media.toList().forEach { m ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NewsImage(url = m.url, contentDescription = null, modifier = Modifier.size(width = 72.dp, height = 110.dp).clip(RoundedCornerShape(10.dp)))
                            TextButton(enabled = !generating, onClick = { media.remove(m) }) { Text(if (m.videoUrl != null) "🎬 Remove" else "Remove") }
                        }
                    }
                }
            } else {
                Text("No pictures yet - add your photos, or let AI suggest stock pictures.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (uploading > 0) Text("Uploading " + uploading + "…", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !generating, onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("📷 Add photos") }
                OutlinedButton(
                    enabled = !generating && !suggesting && short.content.isNotBlank(),
                    onClick = {
                        suggesting = true
                        scope.launch {
                            StudioShorts.stockSuggestions(short.title + "\n" + short.content)
                                .onSuccess { found -> found.take(6).forEach { f -> if (media.none { it.id == f.id }) media += f } }
                                .onFailure { snackbar.showSnackbar(it.message ?: "No suggestions") }
                            suggesting = false
                        }
                    }
                ) { Text(if (suggesting) "Finding…" else "✨ Suggest pictures") }
            }

            // 5. Voice & face
            Text("5. Voice & face", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val p = profile
            Text(
                when {
                    p == null -> "Checking your AI voice…"
                    p.voiceTrained && p.avatarReady -> "Your AI voice and your AI avatar will present this video."
                    p.voiceTrained -> "Your AI voice will narrate; your photo appears (set up an avatar in your profile to appear on video)."
                    else -> "The J Voice stock voice will narrate. Set up your own voice and avatar in your profile."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ------------------------------------------------ detail: reporter + editor */

/** One AI video: play it, and act on it - the reporter submits it, an editor publishes or sends it back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortDetailScreen(shortId: String, onBack: () -> Unit, onRemake: (String) -> Unit) {
    val short by remember(shortId) { StudioShorts.one(shortId) }.collectAsState(initial = null)
    val session by SessionStore.session.collectAsState()
    val isEditor = session?.role?.code in setOf("editor", "news_admin", "super_admin")
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var videoUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sendingBack by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(short?.videoId, short?.videoUrl) {
        val s = short ?: return@LaunchedEffect
        videoUrl = s.videoUrl.ifBlank { StudioShorts.video(s.videoId)?.videoUrl.orEmpty() }
    }

    if (sendingBack) {
        AlertDialog(
            onDismissRequest = { if (!busy) sendingBack = false },
            title = { Text("Send back to the reporter") },
            text = { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("What should they change?") }, minLines = 3) },
            confirmButton = {
                TextButton(enabled = note.isNotBlank() && !busy, onClick = {
                    busy = true
                    scope.launch {
                        StudioShorts.sendBack(shortId, note).onSuccess { sendingBack = false; onBack() }.onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                        busy = false
                    }
                }) { Text("Send back") }
            },
            dismissButton = { TextButton(onClick = { sendingBack = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI video") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val s = short
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (videoUrl.startsWith("http")) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ArticleVideo(url = videoUrl, modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(9f / 16f))
                }
            } else {
                Text("No finished video yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusBadge(s.status, s.statusLabel)
            Text(s.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("By " + s.reporterName.ifBlank { "reporter" } + if (s.status == "published") " · " + s.views + " views" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.editorNote.isNotBlank() && s.status == "rejected") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Note from the editor", style = MaterialTheme.typography.labelLarge)
                        Text(s.editorNote)
                    }
                }
            }
            Text("Narration", style = MaterialTheme.typography.titleSmall)
            Text(s.script.ifBlank { s.content })

            val mine = s.reporterId == session?.uid
            if (isEditor && s.status == "pending_review") {
                Button(enabled = !busy && videoUrl.startsWith("http"), onClick = {
                    busy = true
                    scope.launch {
                        StudioShorts.approve(s).onSuccess { snackbar.showSnackbar("Published - it is live in Clips") }.onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Approve & publish") }
                OutlinedButton(enabled = !busy, onClick = { sendingBack = true }, modifier = Modifier.fillMaxWidth()) { Text("Send back to reporter") }
            }
            if (mine && (s.status == "draft" || s.status == "rejected") && videoUrl.startsWith("http")) {
                Button(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        StudioShorts.submit(s.id).onSuccess { snackbar.showSnackbar("Sent to your editor") }.onFailure { snackbar.showSnackbar(it.message ?: "Failed") }
                        busy = false
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Send to editor") }
            }
            if (mine && s.status != "published" && s.status != "pending_review") {
                OutlinedButton(onClick = { onRemake(s.articleId) }, modifier = Modifier.fillMaxWidth()) { Text("Make a new version") }
            }
        }
    }
}

/* ------------------------------------------------------------ editor: queue */

/** Editors and admins: AI videos waiting for review, and recent ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortsReviewScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val shorts by remember { StudioShorts.all() }.collectAsState(initial = null)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Shorts review") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        val list = shorts
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val waiting = list.filter { it.status == "pending_review" }
        val recent = list.filter { it.status == "published" || it.status == "rejected" }.sortedByDescending { it.publishedAt }.take(20)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Text("Waiting for review (" + waiting.size + ")", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (waiting.isEmpty()) item { Text("Nothing waiting.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(waiting, key = { "w" + it.id }) { s -> ShortRow(s) { onOpen(s.id) } }
            item { Text("Recently handled", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
            items(recent, key = { "r" + it.id }) { s -> ShortRow(s) { onOpen(s.id) } }
        }
    }
}

/** A tile for the reporter dashboard: opens their AI videos. */
@Composable
fun AiShortsCard(onOpen: () -> Unit, onCreate: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AI videos", style = MaterialTheme.typography.titleSmall)
                Text("Turn your stories into short videos in your voice", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onCreate) { Text("Make one") }
        }
    }
}
