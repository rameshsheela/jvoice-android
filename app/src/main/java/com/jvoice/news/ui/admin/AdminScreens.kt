package com.jvoice.news.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.EmptyState
import com.jvoice.news.components.LoadingState
import com.jvoice.news.components.Pill
import com.jvoice.news.components.SectionHeader
import com.jvoice.news.components.StatCard
import com.jvoice.news.components.WorkflowNewsRow
import com.jvoice.news.data.model.Category
import com.jvoice.news.data.model.NewsStatus
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.navigation.AdminScaffold
import com.jvoice.news.navigation.Routes
import com.jvoice.news.navigation.StatGrid
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSubmitted
import kotlinx.coroutines.launch
import com.jvoice.news.data.model.Reporter
import com.jvoice.core.data.StaffAccounts
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import com.jvoice.core.i18n.LocalizedText
import com.jvoice.core.i18n.ContentLanguageTabs
import com.jvoice.core.i18n.LocalizedOutlinedTextField
import com.jvoice.core.i18n.Strings
import com.jvoice.core.i18n.lt
import com.jvoice.core.i18n.rememberLocalizedFormState
import com.jvoice.core.i18n.tr

/* ------------------------------------------------------------------ dashboard */

@Composable
fun AdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val approvedWaiting by viewModel.pendingApprovedCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminScaffold(
        role = UserRole.NEWS_ADMIN,
        title = "News Admin",
        currentRoute = Routes.ADMIN_DASHBOARD,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding), "Loading newsroom stats...")
            return@AdminScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { SectionHeader("People") }
            item {
                StatGrid(
                    stats = listOf(
                        "Total users" to stats.totalUsers.toString(),
                        "Reporters" to stats.totalReporters.toString(),
                        "Editors" to stats.totalEditors.toString()
                    ),
                    columns = 3,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { (label, value), modifier -> StatCard(label, value, modifier) }
            }

            item { SectionHeader("Content") }
            item {
                StatGrid(
                    stats = listOf(
                        "Total news" to stats.totalNews.toString(),
                        "Published" to stats.publishedNews.toString(),
                        "Pending" to stats.pendingNews.toString(),
                        "Reported" to stats.reportedNews.toString()
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { (label, value), modifier ->
                    val accent = when (label) {
                        "Published" -> StatusPublished
                        "Pending" -> StatusSubmitted
                        "Reported" -> StatusRejected
                        else -> MaterialTheme.colorScheme.primary
                    }
                    StatCard(label, value, modifier, accent) { onNavigate(Routes.ADMIN_NEWS) }
                }
            }

            if (approvedWaiting > 0) {
                item {
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                approvedWaiting.toString() + " approved article(s) are not live yet",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                "Open News Management to publish them to the reader feed.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { onNavigate(Routes.ADMIN_NEWS) }) {
                                Text("Go to News Management")
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Quick actions") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("News Management") },
                        supportingContent = { Text("Search, filter, feature and remove articles") },
                        modifier = Modifier.clickable { onNavigate(Routes.ADMIN_NEWS) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Category Management") },
                        supportingContent = { Text("Add, rename, enable or disable categories") },
                        modifier = Modifier.clickable { onNavigate(Routes.ADMIN_CATEGORIES) }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Reporter Management") },
                        supportingContent = { Text("Locations, output and account status") },
                        modifier = Modifier.clickable { onNavigate(Routes.ADMIN_REPORTERS) }
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ news management */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsManagementScreen(
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit,
    onOpenArticle: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val articles by viewModel.filteredArticles.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val query by viewModel.query.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingRemove by remember { mutableStateOf<String?>(null) }

    pendingRemove?.let { id ->
        ConfirmDialog(
            title = "Remove article?",
            message = "The article is deleted from the local demo data and disappears from the reader feed.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                viewModel.removeArticle(id)
                pendingRemove = null
                scope.launch { snackbarHostState.showSnackbar("Article removed") }
            },
            onDismiss = { pendingRemove = null }
        )
    }

    AdminScaffold(
        role = UserRole.NEWS_ADMIN,
        title = "News Management",
        currentRoute = Routes.ADMIN_NEWS,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Search headline, reporter or location") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { viewModel.setCategoryFilter(null) },
                    label = { Text("All categories") }
                )
                categories.forEach { category ->
                    FilterChip(
                        selected = categoryFilter == category.id,
                        onClick = {
                            viewModel.setCategoryFilter(
                                if (categoryFilter == category.id) null else category.id
                            )
                        },
                        label = { Text(category.name.en) }
                    )
                }
            }

            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { viewModel.setStatusFilter(null) },
                    label = { Text("Any status") }
                )
                NewsStatus.entries.forEach { status ->
                    FilterChip(
                        selected = statusFilter == status,
                        onClick = {
                            viewModel.setStatusFilter(if (statusFilter == status) null else status)
                        },
                        label = { Text(status.label) }
                    )
                }
            }

            if (articles.isEmpty()) {
                EmptyState(
                    title = "No matching articles",
                    description = "Try clearing the search or the filters.",
                    actionLabel = "Clear filters",
                    onAction = {
                        viewModel.setQuery("")
                        viewModel.setCategoryFilter(null)
                        viewModel.setStatusFilter(null)
                    }
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(articles, key = { it.id }) { article ->
                        var menuOpen by remember { mutableStateOf(false) }
                        WorkflowNewsRow(
                            article = article,
                            categoryName = viewModel.categoryName(article.categoryId),
                            onClick = { onOpenArticle(article.id) },
                            trailing = {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Open / edit in review") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                onOpenArticle(article.id)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (article.isBreaking) "Remove breaking tag"
                                                    else "Mark as Breaking News"
                                                )
                                            },
                                            leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                viewModel.toggleBreaking(article.id)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Breaking flag updated")
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (article.isFeatured) "Unpin" else "Pin / feature") },
                                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                viewModel.toggleFeatured(article.id)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Pin updated")
                                                }
                                            }
                                        )
                                        if (article.status == NewsStatus.APPROVED) {
                                            DropdownMenuItem(
                                                text = { Text("Publish now") },
                                                onClick = {
                                                    menuOpen = false
                                                    viewModel.publish(article.id)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Published to readers")
                                                    }
                                                }
                                            )
                                        }
                                        if (article.status == NewsStatus.PUBLISHED) {
                                            DropdownMenuItem(
                                                text = { Text("Unpublish") },
                                                onClick = {
                                                    menuOpen = false
                                                    viewModel.unpublish(article.id)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Removed from reader feed")
                                                    }
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Remove article") },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                            onClick = {
                                                menuOpen = false
                                                pendingRemove = article.id
                                            }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ categories */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val usage by viewModel.categoryUsage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Category?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Category?>(null) }

    if (showAdd || editing != null) {
        CategoryDialog(
            category = editing,
            onDismiss = { showAdd = false; editing = null },
            onSave = { name, emoji ->
                val target = editing
                if (target == null) {
                    viewModel.addCategory(name, emoji)
                    scope.launch { snackbarHostState.showSnackbar("Category added") }
                } else {
                    viewModel.updateCategory(target.id, name, emoji)
                    scope.launch { snackbarHostState.showSnackbar("Category updated") }
                }
                showAdd = false
                editing = null
            }
        )
    }

    pendingDelete?.let { category ->
        ConfirmDialog(
            title = "Delete " + category.name.en + "?",
            message = "Categories still holding articles cannot be deleted.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                val deleted = viewModel.deleteCategory(category.id)
                pendingDelete = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (deleted) "Category deleted"
                        else "Cannot delete - articles are still using this category"
                    )
                }
            },
            onDismiss = { pendingDelete = null }
        )
    }

    AdminScaffold(
        role = UserRole.NEWS_ADMIN,
        title = "Category Management",
        currentRoute = Routes.ADMIN_CATEGORIES,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add category") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item { SectionHeader(usage.size.toString() + " categories") }
            items(usage, key = { it.first.id }) { (category, count) ->
                ListItem(
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(category.emoji, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    },
                    headlineContent = { Text(category.name.en) },
                    supportingContent = {
                        Column {
                            Text(category.name.te, style = MaterialTheme.typography.labelMedium)
                            Text(
                                count.toString() + " article(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = category.isEnabled,
                                onCheckedChange = { viewModel.toggleCategory(category.id) }
                            )
                            IconButton(onClick = { editing = category }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { pendingDelete = category }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (LocalizedText, String) -> Unit
) {
    // One bilingual value rather than two loose strings, so the dialogue cannot
    // save a half-built name that the model would then have to reassemble.
    var name by remember { mutableStateOf(category?.name ?: LocalizedText.EMPTY) }
    var emoji by remember { mutableStateOf(category?.emoji ?: "📰") }
    val formState = rememberLocalizedFormState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Add category" else "Edit category") },
        text = {
            Column {
                // A category name is one field in two languages, so it gets the
                // same tabbed treatment as a story rather than two stacked boxes.
                ContentLanguageTabs(
                    state = formState,
                    fields = listOf(name),
                    showHint = false
                )
                Spacer(Modifier.height(10.dp))
                LocalizedOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    language = formState.language,
                    label = lt("Name", "\u0c2a\u0c47\u0c30\u0c41"),
                    required = true,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it },
                    label = { Text("Icon") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                // One language is enough to save; the other can follow later.
                enabled = !name.isBlank,
                onClick = { onSave(name.trimmed(), emoji.trim()) }
            ) { Text(tr(Strings.Common.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ------------------------------------------------------------------ reporters */

@Composable
fun ReporterManagementScreen(
    viewModel: AdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val reporters by viewModel.reporterStats.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<Pair<StaffAccounts.Created, String>?>(null) }
    var resetFor by remember { mutableStateOf<Reporter?>(null) }

    AdminScaffold(
        role = UserRole.NEWS_ADMIN,
        title = "Reporter Management",
        currentRoute = Routes.ADMIN_REPORTERS,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add reporter") }
            )
        }
    ) { padding ->
        if (reporters.isEmpty()) {
            EmptyState(title = "No reporters", modifier = Modifier.padding(padding))
            return@AdminScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item { SectionHeader(reporters.size.toString() + " reporters") }
            items(reporters, key = { it.reporter.userId }) { stats ->
                var locationMenu by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(false) }
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stats.reporter.name, style = MaterialTheme.typography.titleSmall)
                                if (stats.reporter.loginId.isNotBlank()) {
                                    Text(
                                        "ID " + stats.reporter.loginId,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    stats.reporter.beat + " • " + stats.reporter.assignedLocation,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Pill(
                                if (stats.reporter.isActive) "Active" else "Disabled",
                                if (stats.reporter.isActive) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("Articles", stats.total.toString(), Modifier.weight(1f))
                            MiniStat("Approved", stats.approved.toString(), Modifier.weight(1f))
                            MiniStat("Rejected", stats.rejected.toString(), Modifier.weight(1f))
                            MiniStat("Pending", stats.pending.toString(), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box {
                                TextButton(onClick = { locationMenu = true }, enabled = !busy) {
                                    Text("Location")
                                }
                                DropdownMenu(
                                    expanded = locationMenu,
                                    onDismissRequest = { locationMenu = false }
                                ) {
                                    viewModel.locations.forEach { loc ->
                                        DropdownMenuItem(
                                            text = { Text(loc) },
                                            onClick = {
                                                locationMenu = false
                                                busy = true
                                                scope.launch {
                                                    val r = viewModel.updateReporterLocation(stats.reporter.userId, loc)
                                                    busy = false
                                                    snackbarHostState.showSnackbar(
                                                        r.fold({ "Assigned to $loc" }, { it.message ?: "Could not change location" })
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            TextButton(onClick = { resetFor = stats.reporter }, enabled = !busy) {
                                Text("Reset password")
                            }
                            Spacer(Modifier.weight(1f))
                            Switch(
                                checked = stats.reporter.isActive,
                                enabled = !busy,
                                onCheckedChange = {
                                    busy = true
                                    scope.launch {
                                        val r = viewModel.toggleReporterActive(stats.reporter.userId)
                                        busy = false
                                        snackbarHostState.showSnackbar(
                                            r.fold(
                                                { if (stats.reporter.isActive) "Account suspended" else "Account activated" },
                                                { it.message ?: "Could not change the account" }
                                            )
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddReporterDialog(
            viewModel = viewModel,
            onDismiss = { showAdd = false },
            onCreated = { person, password ->
                showAdd = false
                created = person to password
            }
        )
    }

    created?.let { (person, password) ->
        CredentialsDialog(
            title = "Reporter login created",
            loginId = person.loginId,
            employeeId = person.employeeId,
            name = person.name,
            password = password,
            onDismiss = { created = null }
        )
    }

    resetFor?.let { reporter ->
        ResetPasswordDialog(
            reporter = reporter,
            viewModel = viewModel,
            onDismiss = { resetFor = null },
            onDone = {
                resetFor = null
                scope.launch { snackbarHostState.showSnackbar("New password set for " + reporter.name) }
            }
        )
    }
}

/**
 * Creates a reporter login. The admin picks the area and types the temporary
 * password; the login id - which is also the employee id - is numbered by the
 * server, and shown here beforehand as a preview.
 */
@Composable
private fun AddReporterDialog(
    viewModel: AdminViewModel,
    onDismiss: () -> Unit,
    onCreated: (StaffAccounts.Created, String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var area by remember { mutableStateOf("01") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val areaOk = area.length == 2 && area.all { it.isDigit() } && area != "00"
    LaunchedEffect(area) {
        preview = ""
        if (areaOk) preview = viewModel.nextReporterId(area).getOrDefault("")
    }

    val problem = when {
        !areaOk -> "Area code must be 2 digits, 01 to 99"
        name.isBlank() -> "Enter the full name"
        phone.filter { it.isDigit() }.length < 10 -> "Enter a 10-digit mobile number"
        password.length < 8 -> "The password must be at least 8 characters"
        else -> null
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add reporter") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = area,
                    onValueChange = { v -> area = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Area code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    if (preview.isNotBlank()) "Login ID / Employee ID: $preview"
                    else "Login ID / Employee ID: jv" + area.padStart(2, '0') + "r…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { v -> phone = v.filter { it.isDigit() || it == '+' || it == ' ' }.take(16) },
                    label = { Text("Mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Temporary password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    }
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (problem != null) {
                        error = problem
                        return@TextButton
                    }
                    error = null
                    saving = true
                    scope.launch {
                        viewModel.createReporter(area, name.trim(), phone.trim(), location.trim(), password)
                            .onSuccess { onCreated(it, password) }
                            .onFailure { error = it.message ?: "Could not create the login" }
                        saving = false
                    }
                }
            ) { Text(if (saving) "Creating…" else "Create login") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

/** Shown once after a login is made, so the admin can hand it over. */
@Composable
private fun CredentialsDialog(
    title: String,
    loginId: String,
    employeeId: String,
    name: String,
    password: String,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    val block = "J Voice login - $name\nLogin ID: $loginId\nEmployee ID: $employeeId\n" +
        "Temporary password: $password\nSign in with the Login ID and password."
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Name: $name")
                Text("Login ID: $loginId", style = MaterialTheme.typography.titleSmall)
                Text("Employee ID: $employeeId")
                Text("Temporary password: $password")
                Text(
                    "The password is not shown again - hand it over now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(block))
                copied = true
            }) { Text(if (copied) "Copied" else "Copy") }
        }
    )
}

@Composable
private fun ResetPasswordDialog(
    reporter: Reporter,
    viewModel: AdminViewModel,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Reset password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    reporter.name + if (reporter.loginId.isNotBlank()) " · " + reporter.loginId else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show")
                        }
                    }
                )
                Text(
                    "Their current sessions end and the old password stops working.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    if (password.length < 8) {
                        error = "The password must be at least 8 characters"
                        return@TextButton
                    }
                    saving = true
                    scope.launch {
                        viewModel.setReporterPassword(reporter.userId, password)
                            .onSuccess { onDone() }
                            .onFailure { error = it.message ?: "Could not set the password" }
                        saving = false
                    }
                }
            ) { Text(if (saving) "Saving…" else "Set password") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        }
    )
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleSmall)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
