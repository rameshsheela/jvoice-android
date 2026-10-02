package com.jvoice.news.ui.reporter

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.jvoice.core.data.StoryMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.SectionHeader
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.AppLanguage
import com.jvoice.core.i18n.LocalizedFormHeader
import com.jvoice.core.i18n.LocalizedOutlinedTextField
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.Places
import com.jvoice.core.i18n.current
import com.jvoice.core.i18n.currentLanguage
import com.jvoice.core.i18n.lt
import com.jvoice.core.i18n.rememberLocalizedFormState

/**
 * Create News / Edit News. Reporters may edit drafts, rejected and sent-back
 * articles. Saving waits for J Voice to accept the story and shows why if it
 * does not, so nothing is closed on a failed save.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewsScreen(
    viewModel: ReporterViewModel,
    articleId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val form by viewModel.form.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showErrors by remember { mutableStateOf(false) }
    // Which language the copy fields below are bound to.
    val formState = rememberLocalizedFormState()
    var confirmSubmit by remember { mutableStateOf(false) }
    // One save at a time: the button waits for J Voice to accept the story.
    var saving by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    var confirmTranslate by remember { mutableStateOf(false) }
    // Uploads in flight, and the overall progress of the one going now.
    var uploading by remember { mutableStateOf(0) }
    var uploadProgress by remember { mutableStateOf(0f) }
    val context = LocalContext.current
    val busy = saving || uploading > 0

    val otherLanguage = if (formState.language == AppLanguage.TELUGU) AppLanguage.ENGLISH else AppLanguage.TELUGU
    fun runTranslate() {
        val from = formState.language
        translating = true
        scope.launch {
            val error = viewModel.translate(from)
            translating = false
            if (error == null) {
                // Show the result straight away so the reporter can correct it.
                formState.select(otherLanguage)
                snackbarHostState.showSnackbar(
                    if (otherLanguage == AppLanguage.TELUGU) "Translated to Telugu - please check it"
                    else "Translated to English - please check it"
                )
            } else {
                snackbarHostState.showSnackbar(error)
            }
        }
    }

    fun uploadAll(uris: List<android.net.Uri>, folder: String) {
        if (uris.isEmpty()) return
        uploading += uris.size
        scope.launch {
            val done = mutableListOf<String>()
            for (uri in uris) {
                uploadProgress = 0f
                StoryMedia.upload(context, uri, folder) { uploadProgress = it }
                    .onSuccess { url -> done += url; if (folder == StoryMedia.VIDEOS) viewModel.addVideo(url) }
                    .onFailure { snackbarHostState.showSnackbar(it.message ?: "Upload failed") }
                uploading -= 1
            }
            if (folder == StoryMedia.PHOTOS) viewModel.addPhotos(done)
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        uploadAll(uris, StoryMedia.PHOTOS)
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) uploadAll(listOf(uri), StoryMedia.VIDEOS)
    }

    LaunchedEffect(articleId) {
        if (articleId.isNullOrBlank()) viewModel.startNewArticle() else viewModel.loadForEdit(articleId)
        // A story left half-written comes back; say so, and offer a clean start.
        if (articleId.isNullOrBlank() && viewModel.restoredDraft.value) {
            val result = snackbarHostState.showSnackbar(
                message = "Your unsent story is back",
                actionLabel = "Start new",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.discardDraft()
        }
    }

    if (confirmTranslate) {
        ConfirmDialog(
            title = if (otherLanguage == AppLanguage.TELUGU) "Replace the Telugu version?" else "Replace the English version?",
            message = "The other language already has text. Translating replaces it with a fresh translation.",
            confirmLabel = "Translate",
            onConfirm = {
                confirmTranslate = false
                runTranslate()
            },
            onDismiss = { confirmTranslate = false }
        )
    }

    if (confirmSubmit) {
        ConfirmDialog(
            title = "Submit for review?",
            message = "The article moves to the editor's review queue and can no longer be edited by you until it comes back.",
            confirmLabel = "Submit",
            onConfirm = {
                confirmSubmit = false
                saving = true
                scope.launch {
                    val error = viewModel.save(submit = true)
                    saving = false
                    if (error == null) onDone() else snackbarHostState.showSnackbar(error)
                }
            },
            onDismiss = { confirmSubmit = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (articleId.isNullOrBlank()) "Create News" else "Edit News") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        saving = true
                        scope.launch {
                            val error = viewModel.save(submit = false)
                            saving = false
                            if (error == null) {
                                onDone()
                            } else {
                                showErrors = true
                                snackbarHostState.showSnackbar(error)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Save Draft") }

                Button(
                    enabled = !busy,
                    onClick = {
                        showErrors = true
                        if (form.isValid) {
                            confirmSubmit = true
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Please fix the highlighted fields")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Submit")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            SectionHeader("Article content")

            // One set of boxes for both languages; the tab above chooses which
            // side they are bound to. Filing in one language is enough - the tab
            // badge shows what is still missing.
            LocalizedFormHeader(state = formState, fields = form.localizedFields)

            // Write in one language, then fill the other with one tap.
            OutlinedButton(
                enabled = !translating && !busy,
                onClick = {
                    val other = listOf(form.headline, form.shortDescription, form.content)
                        .any { it.rawFor(otherLanguage).isNotBlank() }
                    if (other) confirmTranslate = true else runTranslate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    when {
                        translating -> "Translating…"
                        otherLanguage == AppLanguage.TELUGU -> "🌐 Translate to తెలుగు"
                        else -> "🌐 Translate to English"
                    }
                )
            }
            Spacer(Modifier.height(4.dp))

            LocalizedOutlinedTextField(
                value = form.headline,
                onValueChange = { value -> viewModel.updateForm { it.copy(headline = value) } },
                language = formState.language,
                label = lt("Headline", "శీర్షిక"),
                placeholder = lt("Headline in this language", "ఈ భాషలో శీర్షిక రాయండి"),
                required = true,
                showError = showErrors,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (showErrors && form.headlineError != null) {
                FieldError(form.headlineError!!)
            }

            LocalizedOutlinedTextField(
                value = form.shortDescription,
                onValueChange = { value -> viewModel.updateForm { it.copy(shortDescription = value) } },
                language = formState.language,
                label = lt("Short description", "సంక్షిప్త వివరణ"),
                required = true,
                showError = showErrors,
                minLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (showErrors && form.descriptionError != null) {
                FieldError(form.descriptionError!!)
            }

            LocalizedOutlinedTextField(
                value = form.content,
                onValueChange = { value -> viewModel.updateForm { it.copy(content = value) } },
                language = formState.language,
                label = lt("Full article", "పూర్తి కథనం"),
                required = true,
                showError = showErrors,
                minLines = 8,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            if (showErrors && form.contentError != null) {
                FieldError(form.contentError!!)
            } else {
                // Counts the language on screen, so the reporter sees the length
                // of what they are actually typing.
                Text(
                    form.content.rawFor(formState.language).length.toString() +
                        (if (currentLanguage() == AppLanguage.TELUGU) " అక్షరాలు" else " characters"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            HorizontalDivider(Modifier.padding(16.dp))
            SectionHeader("Classification")

            Text(
                "Category *",
                style = MaterialTheme.typography.labelMedium,
                color = if (showErrors && form.categoryError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.filter { it.isEnabled }.forEach { category ->
                    FilterChip(
                        selected = form.categoryId == category.id,
                        onClick = { viewModel.updateForm { it.copy(categoryId = category.id) } },
                        label = { Text(category.emoji + " " + category.name.current()) }
                    )
                }
            }

            Text(
                "Location",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                viewModel.locations.forEach { loc ->
                    FilterChip(
                        selected = form.location == loc,
                        onClick = { viewModel.updateForm { it.copy(location = loc) } },
                        // The chip shows the Telugu name but `loc` stays the
                        // English key the article is filtered on.
                        label = { Text(Places.render(loc, currentLanguage())) }
                    )
                }
            }

            // Tags are paired up by position across the two boxes on save, so
            // keeping the same order in both gives each tag its translation.
            LocalizedOutlinedTextField(
                value = form.tagsText,
                onValueChange = { value -> viewModel.updateForm { it.copy(tagsText = value) } },
                language = formState.language,
                label = lt("Tags (comma separated)", "ట్యాగ్లు (కామాలతో వేరు చేయండి)"),
                placeholder = lt("education, telangana", "విద్య, తెలంగాణ"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            HorizontalDivider(Modifier.padding(16.dp))
            SectionHeader("Photos & videos", subtitle = "Upload from the phone, or paste a YouTube link")

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    enabled = !saving,
                    onClick = {
                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("📷 Add photos") }
                OutlinedButton(
                    enabled = !saving,
                    onClick = {
                        pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("🎬 Add video") }
            }

            if (uploading > 0) {
                Text(
                    "Uploading… " + (uploadProgress * 100).toInt() + "%" +
                        (if (uploading > 1) " (" + uploading + " left)" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val photos = listOfNotNull(form.imageUrl.ifBlank { null }) +
                form.photosText.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (photos.isNotEmpty()) {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    photos.forEach { url ->
                        val isCover = url == form.imageUrl
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            NewsImage(
                                url = url,
                                contentDescription = if (isCover) "Cover photo" else "Photo",
                                modifier = Modifier
                                    .size(width = 150.dp, height = 96.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isCover) {
                                    Text(
                                        "★ Cover",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                } else {
                                    TextButton(onClick = { viewModel.makeCover(url) }) { Text("Make cover") }
                                }
                                TextButton(onClick = { viewModel.removePhoto(url) }) { Text("Remove") }
                            }
                        }
                    }
                }
            }

            val videos = form.videosText.lines().map { it.trim() }.filter { it.isNotBlank() }
            videos.forEachIndexed { index, url ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(onClick = {}, label = { Text("🎬 Video " + (index + 1) + " uploaded") })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.removeVideo(url) }) { Text("Remove") }
                }
            }

            OutlinedTextField(
                value = form.youtubeUrl,
                onValueChange = { value -> viewModel.updateForm { it.copy(youtubeUrl = value) } },
                label = { Text("YouTube link (optional)") },
                placeholder = { Text("https://youtu.be/…") },
                singleLine = true,
                isError = form.youtubeUrl.isNotBlank() && !isYouTube(form.youtubeUrl),
                supportingText = {
                    Text(
                        if (form.youtubeUrl.isNotBlank() && !isYouTube(form.youtubeUrl))
                            "Paste the link from YouTube's Share button"
                        else "Readers see YouTube's thumbnail and open it in YouTube."
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Mark as Breaking News", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Editors can still change this during review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = form.isBreaking,
                    onCheckedChange = { value -> viewModel.updateForm { it.copy(isBreaking = value) } }
                )
            }

            Box(Modifier.height(12.dp))
        }
    }
}

/**
 * Validation message for a bilingual field. The message itself is bilingual too -
 * the reporter reads it in whichever language they set the app to, regardless of
 * which content tab they are typing in.
 */
@Composable
private fun FieldError(message: LocalizedText) {
    Text(
        message.current(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
    )
}
