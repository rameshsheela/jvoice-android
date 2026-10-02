package com.jvoice.news.ui.reader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.jvoice.core.i18n.LanguagePreference
import com.jvoice.core.i18n.LanguagePreferenceCard
import com.jvoice.core.i18n.Strings
import com.jvoice.core.i18n.tr
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jvoice.core.flags.FeatureFlags
import com.jvoice.core.flags.ReleaseConfig
import com.jvoice.core.flags.flagOptedIn
import com.jvoice.core.reader.ReaderProfile
import com.jvoice.news.BuildConfig
import com.jvoice.news.utils.CONTACT_PAGE_URL
import com.jvoice.news.utils.CONTACT_PHONE
import com.jvoice.news.utils.CONTACT_PHONE_DISPLAY
import com.jvoice.news.utils.PRIVACY_POLICY_URL
import com.jvoice.news.utils.PUBLISHER_ADDRESS
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.NewsCard
import com.jvoice.news.components.Pill
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.data.model.NotificationType
import com.jvoice.news.data.model.User
import com.jvoice.news.utils.toRelativeTime
import kotlinx.coroutines.launch
import com.jvoice.core.i18n.current

/* ------------------------------------------------------------------ categories */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderCategoriesScreen(
    viewModel: ReaderViewModel,
    onOpenCategory: (String) -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val enabled = categories.filter { it.isEnabled }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Categories • విభాగాలు") }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        if (enabled.isEmpty()) {
            EmptyState(
                title = "No categories enabled",
                description = "A News Admin can enable categories from the admin dashboard.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(enabled, key = { it.id }) { category ->
                    Card(
                        modifier = Modifier
                            .aspectRatio(1.35f)
                            .clickable { onOpenCategory(category.id) },
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(category.emoji, style = MaterialTheme.typography.headlineSmall)
                            Column {
                                Text(category.name.en, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    category.name.te,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    viewModel.articlesInCategory(category.id).size.toString() + " articles",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ category feed */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryNewsScreen(
    viewModel: ReaderViewModel,
    categoryId: String,
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit
) {
    val savedIds by viewModel.savedIds.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val category = categories.firstOrNull { it.id == categoryId }
    val articles = viewModel.articlesInCategory(categoryId)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(category?.displayName ?: "Category") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (articles.isEmpty()) {
            EmptyState(
                title = "Nothing published here yet",
                description = "Articles approved in this category will show up here.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(articles, key = { it.id }) { article ->
                    NewsCard(
                        article = article,
                        categoryName = viewModel.categoryName(article.categoryId),
                        isSaved = savedIds.contains(article.id),
                        onClick = { onOpenArticle(article.id) },
                        onToggleSave = {
                            val saved = viewModel.toggleSave(article.id)
                            scope.launch {
                                snackbarHostState.showSnackbar(if (saved) "Saved" else "Removed")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ saved */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSavedScreen(
    viewModel: ReaderViewModel,
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val saved by viewModel.savedArticles.collectAsState()
    val savedIds by viewModel.savedIds.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear all bookmarks?",
            message = "This removes every saved article from this device. Demo data only.",
            confirmLabel = "Clear all",
            destructive = true,
            onConfirm = {
                viewModel.clearSaved()
                confirmClear = false
                scope.launch { snackbarHostState.showSnackbar("All bookmarks cleared") }
            },
            onDismiss = { confirmClear = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Saved • సేవ్ చేసినవి") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (saved.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (saved.isEmpty()) {
            EmptyState(
                title = "No saved articles",
                description = "Tap the bookmark icon on any news card to read it later.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                items(saved, key = { it.id }) { article ->
                    NewsCard(
                        article = article,
                        categoryName = viewModel.categoryName(article.categoryId),
                        isSaved = savedIds.contains(article.id),
                        onClick = { onOpenArticle(article.id) },
                        onToggleSave = {
                            viewModel.toggleSave(article.id)
                            scope.launch {
                                snackbarHostState.showSnackbar("Removed from bookmarks")
                            }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ notifications */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderNotificationsScreen(
    viewModel: ReaderViewModel,
    onOpenArticle: (String) -> Unit,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit
) {
    val notifications by viewModel.notifications.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (notifications.any { !it.isRead }) {
                        IconButton(onClick = { viewModel.markAllRead() }) {
                            Icon(Icons.Default.DoneAll, contentDescription = "Mark all read")
                        }
                    }
                }
            )
        },
        bottomBar = bottomBar
    ) { padding ->
        if (notifications.isEmpty()) {
            EmptyState(
                title = "No notifications",
                description = "Breaking news alerts will appear here.",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(notifications, key = { it.id }) { item ->
                    val accent = when (item.type) {
                        NotificationType.BREAKING -> MaterialTheme.colorScheme.error
                        NotificationType.APPROVAL -> MaterialTheme.colorScheme.tertiary
                        NotificationType.REJECTION -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                    ListItem(
                        leadingContent = {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.12f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (item.type == NotificationType.BREAKING) Icons.Default.Campaign
                                        else Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            }
                        },
                        headlineContent = {
                            Text(
                                item.title.current(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Column {
                                Text(item.message.current(), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    item.timeMillis.toRelativeTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        },
                        trailingContent = {
                            if (!item.isRead) {
                                Pill("NEW", MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable {
                            viewModel.markNotificationRead(item.id)
                            item.articleId?.let(onOpenArticle)
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ profile */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderProfileScreen(
    user: User?,
    viewModel: ReaderViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    onOpenSaved: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenNotifications: () -> Unit = {}
) {
    val saved by viewModel.savedArticles.collectAsState()
    val location by viewModel.selectedLocation.collectAsState()
    val language by LanguagePreference.language.collectAsState()
    val name by ReaderProfile.name.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingName by remember { mutableStateOf(false) }
    var pickingLocation by remember { mutableStateOf(false) }
    var confirmStaffLogin by remember { mutableStateOf(false) }
    // Hidden until the desk is offered from the public app.
    // Never in the Play build, whatever the server flag says - see ReleaseConfig.
    val staffLoginOffered = !ReleaseConfig.READER_ONLY && flagOptedIn(FeatureFlags.Keys.STAFF_LOGIN)

    if (editingName) {
        NameDialog(
            initial = name,
            onDismiss = { editingName = false },
            onSave = { typed ->
                ReaderProfile.setName(typed)
                editingName = false
                scope.launch { snackbarHostState.showSnackbar("Name saved") }
            }
        )
    }

    if (pickingLocation) {
        LocationDialog(
            options = viewModel.locations,
            selected = location,
            onDismiss = { pickingLocation = false },
            onPick = { picked ->
                viewModel.setLocation(picked)
                pickingLocation = false
            }
        )
    }

    // Signed-in staff reading the news get "Back to my desk" instead of "Staff sign in".
    val backToDesk = com.jvoice.news.navigation.LocalBackToDesk.current

    if (confirmStaffLogin) {
        ConfirmDialog(
            title = "Staff sign in?",
            message = "For J Voice reporters, editors and admins. You will leave the reader and go to the sign-in screen.",
            confirmLabel = "Continue",
            onConfirm = {
                confirmStaffLogin = false
                onSignOut()
            },
            onDismiss = { confirmStaffLogin = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(tr(Strings.Common.profile)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ------------------------------------------------------ header
            // The reader's name, or an invitation to add one. Tapping anywhere
            // on the row edits it.
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { editingName = true }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (name.isBlank()) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                name.trim().first().uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            name.ifBlank { "Add your name" },
                            style = MaterialTheme.typography.titleLarge,
                            color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "J Voice reader  •  " + location,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit name",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }

            // ------------------------------------------------- preferences
            item { SectionHeader("Preferences") }
            item {
                LanguagePreferenceCard(
                    selected = language,
                    onSelect = { picked ->
                        LanguagePreference.set(picked)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                Strings.Language.changedTo.get(picked) + ": " + picked.labelNative
                            )
                        }
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    headlineContent = { Text("Location") },
                    supportingContent = { Text(location) },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    },
                    modifier = Modifier.clickable { pickingLocation = true }
                )
            }
            item { NotificationPermissionRow() }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                    headlineContent = { Text("Dark mode") },
                    supportingContent = { Text("Follows the system unless changed here") },
                    trailingContent = {
                        Switch(checked = isDarkTheme, onCheckedChange = onToggleTheme)
                    }
                )
            }

            // ----------------------------------------------------- my news
            item { SectionHeader("My news") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                    headlineContent = { Text("Saved news") },
                    supportingContent = { Text(saved.size.toString() + " saved articles") },
                    modifier = Modifier.clickable(onClick = onOpenSaved)
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    headlineContent = { Text("Notifications") },
                    supportingContent = { Text("Breaking news alerts") },
                    modifier = Modifier.clickable(onClick = onOpenNotifications)
                )
            }

            // ------------------------------------------------- invited by
            // An APK install carries no Play referrer, so the reader can say
            // once which reporter sent them - it earns that reporter points.
            item { InvitedByRow() }

            // ------------------------------------------------------- about
            item { SectionHeader("About") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    headlineContent = { Text("J Voice") },
                    supportingContent = {
                        Text("Telugu news published by J Voice, $PUBLISHER_ADDRESS  •  Version " + BuildConfig.VERSION_NAME)
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Call, contentDescription = null) },
                    headlineContent = { Text("Contact us") },
                    supportingContent = { Text("Call the newsroom: $CONTACT_PHONE_DISPLAY") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$CONTACT_PHONE"))
                            )
                        }
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.Language, contentDescription = null) },
                    headlineContent = { Text("Contact page") },
                    supportingContent = { Text(CONTACT_PAGE_URL.removePrefix("https://")) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(CONTACT_PAGE_URL))
                            )
                        }
                    }
                )
            }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                    headlineContent = { Text("Privacy policy") },
                    supportingContent = { Text("How J Voice handles your information") },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.clickable {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PRIVACY_POLICY_URL))
                            )
                        }
                    }
                )
            }

            if (backToDesk != null) {
                // Signed-in staff reading the news: back to their desk, not a
                // "sign in" that would sign them out.
                item {
                    androidx.compose.material3.Button(
                        onClick = backToDesk,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back to my desk")
                    }
                }
            } else if (staffLoginOffered) {
                item {
                    OutlinedButton(
                        onClick = { confirmStaffLogin = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Staff sign in")
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Edit the reader's display name. */
@Composable
private fun NameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= ReaderProfile.MAX_NAME_LENGTH) text = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text("e.g. Sai Charan") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Pick the reader's location from the bureau list. */
@Composable
private fun LocationDialog(
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your location") },
        text = {
            LazyColumn {
                items(options) { option ->
                    ListItem(
                        headlineContent = { Text(option) },
                        trailingContent = {
                            if (option == selected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        modifier = Modifier.clickable { onPick(option) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The notification permission, with the OS state and a way to grant it.
 *
 * Below API 33 the permission is granted at install, so the row simply reports
 * that. From 33 a tap asks; if the system will no longer show the prompt (the
 * reader has said no twice) the tap opens the app's notification settings
 * instead, which is the only remaining way to turn them on.
 */
@Composable
private fun NotificationPermissionRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun granted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    var isGranted by remember { mutableStateOf(granted()) }

    // Re-read on return from the system settings page.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) isGranted = granted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        isGranted = result
        LanguagePreference.markNotificationsAsked()
        val activity = context as? Activity
        if (!result && activity != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            openNotificationSettings(context)
        }
    }

    ListItem(
        leadingContent = {
            Icon(
                if (isGranted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                contentDescription = null
            )
        },
        headlineContent = { Text("Notification permission") },
        supportingContent = {
            Text(if (isGranted) "Allowed - breaking news alerts are on" else "Not allowed yet")
        },
        trailingContent = {
            if (!isGranted) {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text("Allow") }
            }
        },
        modifier = if (isGranted) Modifier else Modifier.clickable {
            openNotificationSettings(context)
        }
    )
}

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No settings screen to hand off to: nothing more can be done here.
    }
}

/** "Invited by a J Voice reporter?" - one code, once per phone. */
@Composable
private fun InvitedByRow() {
    val creditedTo by com.jvoice.core.data.InstallReferral.creditedTo.collectAsState()
    var open by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (creditedTo != null) {
        ListItem(
            leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
            headlineContent = { Text("Invited by") },
            supportingContent = { Text(creditedTo!!.ifBlank { "A J Voice reporter" }) }
        )
        return
    }
    ListItem(
        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
        headlineContent = { Text("Invited by a J Voice reporter?") },
        supportingContent = { Text("Enter their code - it thanks them for bringing you") },
        trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable { open = true }
    )
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("Reporter's code") },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = code,
                        onValueChange = { code = it; error = null },
                        placeholder = { Text("e.g. jv01r001") },
                        singleLine = true,
                        isError = error != null,
                        supportingText = error?.let { { Text(it) } }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = !busy && code.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            com.jvoice.core.data.InstallReferral.claim(code)
                                .onSuccess { open = false }
                                .onFailure { error = it.message }
                            busy = false
                        }
                    }
                ) { Text(if (busy) "Saving…" else "Save") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(enabled = !busy, onClick = { open = false }) { Text("Cancel") }
            }
        )
    }
}
