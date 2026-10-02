package com.jvoice.news.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.NewsImage
import com.jvoice.news.components.Pill
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.components.StatusChip
import com.jvoice.news.data.repository.NewsRepository
import com.jvoice.news.utils.toFullDate
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.current
import com.jvoice.core.i18n.ContentLanguageTabs
import com.jvoice.core.i18n.LocalizedFormHeader
import com.jvoice.core.i18n.LocalizedOutlinedTextField
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.Strings
import com.jvoice.core.i18n.lt
import com.jvoice.core.i18n.rememberLocalizedFormState
import com.jvoice.core.i18n.tr

private enum class ReviewAction { NONE, REJECT, SEND_BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReviewScreen(
    viewModel: EditorViewModel,
    articleId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    // Additive: the AI Shorts entry point. Defaults to a no-op so existing
    // callers keep working unchanged.
    onCreateAIShort: (String) -> Unit = {}
) {
    val allArticles by NewsRepository.articles.collectAsState()
    val article = remember(articleId, allArticles) { viewModel.articleById(articleId) }
    val draft by viewModel.draft.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var approving by remember { mutableStateOf(false) }

    var action by remember { mutableStateOf(ReviewAction.NONE) }
    // The reason reaches the reporter, who may work in the other language, so it
    // is authored as a pair like the copy is.
    var reasonText by remember { mutableStateOf(LocalizedText.EMPTY) }
    var newTag by remember { mutableStateOf("") }
    // Which language the copy fields below are bound to.
    val formState = rememberLocalizedFormState()

    LaunchedEffect(articleId) { viewModel.loadDraft(articleId) }

    if (action != ReviewAction.NONE) {
        val isReject = action == ReviewAction.REJECT
        AlertDialog(
            onDismissRequest = { action = ReviewAction.NONE; reasonText = LocalizedText.EMPTY },
            title = { Text(if (isReject) "Reject article" else "Send back for correction") },
            text = {
                Column {
                    Text(
                        if (isReject) "Tell the reporter why this article is being rejected."
                        else "Describe the corrections the reporter should make."
                    )
                    Spacer(Modifier.height(12.dp))
                    ContentLanguageTabs(
                        state = formState,
                        fields = listOf(reasonText),
                        showHint = false
                    )
                    Spacer(Modifier.height(8.dp))
                    LocalizedOutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        language = formState.language,
                        label = if (isReject)
                            lt("Rejection reason", "\u0c24\u0c3f\u0c30\u0c38\u0c4d\u0c15\u0c30\u0c23 \u0c15\u0c3e\u0c30\u0c23\u0c02")
                        else lt("Correction note", "\u0c38\u0c30\u0c3f\u0c26\u0c3f\u0c26\u0c4d\u0c26\u0c41\u0c2c\u0c3e\u0c1f\u0c41 \u0c28\u0c4b\u0c1f\u0c4d"),
                        required = true,
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !reasonText.isBlank,
                    onClick = {
                        if (isReject) viewModel.reject(reasonText) else viewModel.sendBack(reasonText)
                        action = ReviewAction.NONE
                        reasonText = LocalizedText.EMPTY
                        onDone()
                    }
                ) {
                    Text(
                        if (isReject) "Reject" else "Send back",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { action = ReviewAction.NONE; reasonText = LocalizedText.EMPTY }) {
                    Text(tr(Strings.Common.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Article Review") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.saveEdits()
                        scope.launch { snackbarHostState.showSnackbar("Edits saved") }
                    }) { Text("Save") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (article != null) {
                Column(Modifier.navigationBarsPadding().padding(16.dp)) {
                    OutlinedButton(
                        onClick = { onCreateAIShort(article.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("\uD83E\uDD16  Create AI Short")
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { action = ReviewAction.SEND_BACK },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send back")
                        }
                        OutlinedButton(
                            onClick = { action = ReviewAction.REJECT },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Reject")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            enabled = !approving,
                            onClick = {
                                approving = true
                                scope.launch {
                                    val error = viewModel.approveOnly()
                                    approving = false
                                    if (error == null) onDone() else snackbarHostState.showSnackbar(error)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Approve only") }
                        Button(
                            enabled = !approving,
                            onClick = {
                                approving = true
                                scope.launch {
                                    val error = viewModel.approveAndPublish()
                                    approving = false
                                    if (error == null) onDone() else snackbarHostState.showSnackbar(error)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (approving) "Publishing…" else "Approve & publish")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (article == null) {
            EmptyState(
                title = "Article not found",
                description = "It may have been removed from the demo data.",
                modifier = Modifier.padding(padding),
                actionLabel = "Back",
                onAction = onBack
            )
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusChip(article.status)
                Spacer(Modifier.width(8.dp))
                Pill(article.reporterName, MaterialTheme.colorScheme.secondary)
            }
            Text(
                article.location + "  •  submitted " + article.createdAt.toFullDate(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            NewsImage(
                url = article.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
            )

            SectionHeader("Edit copy", subtitle = "Changes are saved with your decision")

            // One set of boxes, two languages: the tab decides which side of each
            // field is bound. Switching tabs does not lose anything - the draft
            // holds the pair.
            LocalizedFormHeader(state = formState, fields = draft.localizedFields)
            Spacer(Modifier.height(10.dp))

            LocalizedOutlinedTextField(
                value = draft.headline,
                onValueChange = { value -> viewModel.updateDraft { it.copy(headline = value) } },
                language = formState.language,
                label = lt("Headline", "\u0c36\u0c40\u0c30\u0c4d\u0c37\u0c3f\u0c15"),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LocalizedOutlinedTextField(
                value = draft.shortDescription,
                onValueChange = { value -> viewModel.updateDraft { it.copy(shortDescription = value) } },
                language = formState.language,
                label = lt("Short description", "\u0c38\u0c02\u0c15\u0c4d\u0c37\u0c3f\u0c2a\u0c4d\u0c24 \u0c35\u0c3f\u0c35\u0c30\u0c23"),
                minLines = 2,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            LocalizedOutlinedTextField(
                value = draft.content,
                onValueChange = { value -> viewModel.updateDraft { it.copy(content = value) } },
                language = formState.language,
                label = lt("Full article", "\u0c2a\u0c42\u0c30\u0c4d\u0c24\u0c3f \u0c15\u0c25\u0c28\u0c02"),
                minLines = 10,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            HorizontalDivider(Modifier.padding(16.dp))
            SectionHeader("Category")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.filter { it.isEnabled }.forEach { category ->
                    FilterChip(
                        selected = draft.categoryId == category.id,
                        onClick = { viewModel.updateDraft { it.copy(categoryId = category.id) } },
                        label = { Text(category.name.current()) }
                    )
                }
            }

            SectionHeader("Tags")
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                draft.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { viewModel.removeTag(tag) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#" + tag.get(formState.language))
                                // Flags a tag that exists in one language only.
                                if (!tag.isComplete) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("\u26a0", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.height(16.dp))
                        }
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it },
                    label = { Text("Add tag (" + formState.language.labelNative + ")") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    viewModel.addTag(newTag, formState.language)
                    newTag = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add tag")
                }
            }

            SectionHeader("When published")
            ReviewSwitch(
                title = "Opens a full article page",
                subtitle = "Off for a story that is complete on the card",
                checked = draft.detailEnabled,
                onChange = { value -> viewModel.updateDraft { it.copy(detailEnabled = value) } }
            )
            ReviewSwitch(
                title = "Notify readers when published",
                subtitle = "Sends a notification to every reader's phone",
                checked = draft.notifyReaders,
                onChange = { value -> viewModel.updateDraft { it.copy(notifyReaders = value) } }
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReviewSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
