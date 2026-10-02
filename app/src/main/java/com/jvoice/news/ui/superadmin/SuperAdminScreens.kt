package com.jvoice.news.ui.superadmin

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.jvoice.news.data.model.User
import com.jvoice.news.data.model.UserRole
import com.jvoice.news.navigation.AdminScaffold
import com.jvoice.news.navigation.Routes
import com.jvoice.news.navigation.StatGrid
import com.jvoice.news.theme.StatusPublished
import com.jvoice.news.theme.StatusRejected
import com.jvoice.news.theme.StatusSubmitted
import kotlinx.coroutines.launch

/* ------------------------------------------------------------------ dashboard */

@Composable
fun SuperAdminDashboardScreen(
    viewModel: SuperAdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val stats by viewModel.stats.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val categoryCount by viewModel.categoryCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    AdminScaffold(
        role = UserRole.SUPER_ADMIN,
        title = "Super Admin",
        currentRoute = Routes.SUPER_DASHBOARD,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        if (isLoading) {
            LoadingState(Modifier.padding(padding), "Loading system overview...")
            return@AdminScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item { SectionHeader("People", subtitle = "వినియోగదారులు") }
            item {
                StatGrid(
                    stats = listOf(
                        "Total users" to stats.totalUsers.toString(),
                        "Reporters" to stats.totalReporters.toString(),
                        "Editors" to stats.totalEditors.toString(),
                        "Admins" to stats.totalAdmins.toString()
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { (label, value), modifier ->
                    StatCard(label, value, modifier) { onNavigate(Routes.SUPER_USERS) }
                }
            }

            item { SectionHeader("Content") }
            item {
                StatGrid(
                    stats = listOf(
                        "Total articles" to stats.totalArticles.toString(),
                        "Published" to stats.published.toString(),
                        "Pending" to stats.pending.toString(),
                        "Rejected" to stats.rejected.toString()
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) { (label, value), modifier ->
                    val accent = when (label) {
                        "Published" -> StatusPublished
                        "Pending" -> StatusSubmitted
                        "Rejected" -> StatusRejected
                        else -> MaterialTheme.colorScheme.primary
                    }
                    StatCard(label, value, modifier, accent)
                }
            }

            item { SectionHeader("System") }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    ListItem(
                        headlineContent = { Text("User Management") },
                        supportingContent = { Text("View, edit, activate and change roles") },
                        trailingContent = { Text(">") },
                        modifier = Modifier.padding(0.dp)
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Role Management") },
                        supportingContent = { Text("Permissions for each of the five roles") }
                    )
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("System Settings") },
                        supportingContent = {
                            Text(categoryCount.toString() + " categories • breaking news, alerts, moderation")
                        }
                    )
                }
            }
            item {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = { onNavigate(Routes.SUPER_USERS) }) { Text("Users") }
                    TextButton(onClick = { onNavigate(Routes.SUPER_ROLES) }) { Text("Roles") }
                    TextButton(onClick = { onNavigate(Routes.SUPER_SETTINGS) }) { Text("Settings") }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ user management */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    viewModel: SuperAdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val users by viewModel.users.collectAsState()
    val roleFilter by viewModel.roleFilter.collectAsState()
    val query by viewModel.query.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editingUser by remember { mutableStateOf<User?>(null) }
    var pendingDeactivate by remember { mutableStateOf<User?>(null) }

    editingUser?.let { user ->
        EditUserDialog(
            user = user,
            locations = viewModel.locations,
            onDismiss = { editingUser = null },
            onSave = { name, email, location ->
                viewModel.updateUser(user.id, name, email, location)
                editingUser = null
                scope.launch { snackbarHostState.showSnackbar("User updated") }
            }
        )
    }

    pendingDeactivate?.let { user ->
        ConfirmDialog(
            title = "Deactivate " + user.name + "?",
            message = "The account stays in the demo data but is marked inactive.",
            confirmLabel = "Deactivate",
            destructive = true,
            onConfirm = {
                viewModel.setActive(user.id, false)
                pendingDeactivate = null
                scope.launch { snackbarHostState.showSnackbar("Account deactivated") }
            },
            onDismiss = { pendingDeactivate = null }
        )
    }

    AdminScaffold(
        role = UserRole.SUPER_ADMIN,
        title = "User Management",
        currentRoute = Routes.SUPER_USERS,
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
                placeholder = { Text("Search name, email or location") },
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
                    selected = roleFilter == null,
                    onClick = { viewModel.setRoleFilter(null) },
                    label = { Text("All roles") }
                )
                UserRole.entries.forEach { role ->
                    FilterChip(
                        selected = roleFilter == role,
                        onClick = { viewModel.setRoleFilter(if (roleFilter == role) null else role) },
                        label = { Text(role.label) }
                    )
                }
            }

            if (users.isEmpty()) {
                EmptyState(
                    title = "No users match",
                    description = "Clear the search or role filter.",
                    actionLabel = "Clear",
                    onAction = {
                        viewModel.setQuery("")
                        viewModel.setRoleFilter(null)
                    }
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(users, key = { it.id }) { user ->
                        var menuOpen by remember { mutableStateOf(false) }
                        var roleMenuOpen by remember { mutableStateOf(false) }

                        ListItem(
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(user.name.take(1), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            },
                            headlineContent = { Text(user.name) },
                            supportingContent = {
                                Column {
                                    Text(user.email, style = MaterialTheme.typography.labelSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Pill(user.role.label, MaterialTheme.colorScheme.primary)
                                        Pill(
                                            if (user.isActive) "Active" else "Inactive",
                                            if (user.isActive) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.error
                                        )
                                        Pill(user.location, MaterialTheme.colorScheme.outline)
                                    }
                                }
                            },
                            trailingContent = {
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Actions")
                                    }
                                    DropdownMenu(
                                        expanded = menuOpen,
                                        onDismissRequest = { menuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Edit details") },
                                            onClick = {
                                                menuOpen = false
                                                editingUser = user
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(if (user.isActive) "Deactivate" else "Activate") },
                                            onClick = {
                                                menuOpen = false
                                                if (user.isActive) {
                                                    pendingDeactivate = user
                                                } else {
                                                    viewModel.setActive(user.id, true)
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar("Account activated")
                                                    }
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Change role") },
                                            onClick = {
                                                menuOpen = false
                                                roleMenuOpen = true
                                            }
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = roleMenuOpen,
                                        onDismissRequest = { roleMenuOpen = false }
                                    ) {
                                        UserRole.entries.forEach { role ->
                                            DropdownMenuItem(
                                                text = { Text(role.label) },
                                                trailingIcon = {
                                                    if (role == user.role) {
                                                        Icon(Icons.Default.Check, contentDescription = null)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.changeRole(user.id, role)
                                                    roleMenuOpen = false
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(
                                                            user.name + " is now a " + role.label
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EditUserDialog(
    user: User,
    locations: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var email by remember { mutableStateOf(user.email) }
    var location by remember { mutableStateOf(user.location) }
    var locationMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit user") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Box {
                    TextButton(onClick = { locationMenu = true }) {
                        Text("Location: " + location)
                    }
                    DropdownMenu(
                        expanded = locationMenu,
                        onDismissRequest = { locationMenu = false }
                    ) {
                        locations.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text(loc) },
                                onClick = { location = loc; locationMenu = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), email.trim(), location) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/* ------------------------------------------------------------------ role management */

@Composable
fun RoleManagementScreen(
    viewModel: SuperAdminViewModel,
    onNavigate: (String) -> Unit,
    onSignOut: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    AdminScaffold(
        role = UserRole.SUPER_ADMIN,
        title = "Role Management",
        currentRoute = Routes.SUPER_ROLES,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                SectionHeader(
                    "Role permissions",
                    subtitle = "Read-only in Module 1 - permissions are defined in the mock data"
                )
            }
            items(viewModel.rolePermissions, key = { it.role.name }) { rolePermission ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rolePermission.role.label, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Pill(rolePermission.role.teluguLabel, MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(8.dp))
                        rolePermission.permissions.forEach { permission ->
                            Row(
                                Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(permission, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ settings */

@Composable
fun SystemSettingsScreen(
    viewModel: SuperAdminViewModel,
    onNavigate: (String) -> Unit,
    onOpenCategories: () -> Unit,
    onSignOut: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val categoryCount by viewModel.categoryCount.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    AdminScaffold(
        role = UserRole.SUPER_ADMIN,
        title = "System Settings",
        currentRoute = Routes.SUPER_SETTINGS,
        onNavigate = onNavigate,
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            item { SectionHeader("App settings") }
            item {
                ListItem(
                    headlineContent = { Text("App name") },
                    supportingContent = { Text(settings.appName) }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Default location") },
                    supportingContent = { Text(settings.defaultLocation) }
                )
            }
            item {
                SettingSwitch(
                    title = "Telugu-first interface",
                    subtitle = "Show Telugu labels above English",
                    checked = settings.teluguFirstUi
                ) { value -> viewModel.updateSettings { it.copy(teluguFirstUi = value) } }
            }

            item { SectionHeader("News categories") }
            item {
                ListItem(
                    headlineContent = { Text(categoryCount.toString() + " categories configured") },
                    supportingContent = { Text("Managed from the News Admin category screen") },
                    trailingContent = {
                        TextButton(onClick = onOpenCategories) { Text("Manage") }
                    }
                )
            }

            item { SectionHeader("Breaking news") }
            item {
                SettingSwitch(
                    title = "Breaking news banner",
                    subtitle = "Show the breaking rail on the reader home screen",
                    checked = settings.breakingNewsEnabled
                ) { value -> viewModel.updateSettings { it.copy(breakingNewsEnabled = value) } }
            }
            item {
                ListItem(
                    headlineContent = { Text("Auto-expire breaking tag") },
                    supportingContent = {
                        Text("After " + settings.breakingNewsAutoExpiryHours + " hours")
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        breakingNewsAutoExpiryHours =
                                        (it.breakingNewsAutoExpiryHours - 2).coerceAtLeast(2)
                                    )
                                }
                            }) { Text("-") }
                            TextButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        breakingNewsAutoExpiryHours =
                                        (it.breakingNewsAutoExpiryHours + 2).coerceAtMost(48)
                                    )
                                }
                            }) { Text("+") }
                        }
                    }
                )
            }

            item { SectionHeader("Notifications") }
            item {
                SettingSwitch(
                    title = "Push notifications",
                    subtitle = "Local demo toggle - no FCM in Module 1",
                    checked = settings.pushNotificationsEnabled
                ) { value -> viewModel.updateSettings { it.copy(pushNotificationsEnabled = value) } }
            }
            item {
                SettingSwitch(
                    title = "Daily email digest",
                    subtitle = "Placeholder setting for a later module",
                    checked = settings.emailDigestEnabled
                ) { value -> viewModel.updateSettings { it.copy(emailDigestEnabled = value) } }
            }

            item { SectionHeader("User roles") }
            item {
                ListItem(
                    headlineContent = { Text("5 roles configured") },
                    supportingContent = { Text("Reader, Reporter, Editor, News Admin, Super Admin") },
                    trailingContent = {
                        TextButton(onClick = { onNavigate(Routes.SUPER_ROLES) }) { Text("View") }
                    }
                )
            }

            item { SectionHeader("Content moderation") }
            item {
                SettingSwitch(
                    title = "Auto moderation",
                    subtitle = "Flag articles that cross the report threshold",
                    checked = settings.autoModerationEnabled
                ) { value -> viewModel.updateSettings { it.copy(autoModerationEnabled = value) } }
            }
            item {
                SettingSwitch(
                    title = "Profanity filter",
                    subtitle = "Applies to headlines and article bodies",
                    checked = settings.profanityFilterEnabled
                ) { value -> viewModel.updateSettings { it.copy(profanityFilterEnabled = value) } }
            }
            item {
                SettingSwitch(
                    title = "Reader comments",
                    subtitle = "Disabled for the Module 1 prototype",
                    checked = settings.commentsEnabled
                ) { value -> viewModel.updateSettings { it.copy(commentsEnabled = value) } }
            }
            item {
                ListItem(
                    headlineContent = { Text("Auto-hide after reports") },
                    supportingContent = {
                        Text(settings.maxReportsBeforeAutoHide.toString() + " reader reports")
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        maxReportsBeforeAutoHide =
                                        (it.maxReportsBeforeAutoHide - 1).coerceAtLeast(1)
                                    )
                                }
                            }) { Text("-") }
                            TextButton(onClick = {
                                viewModel.updateSettings {
                                    it.copy(
                                        maxReportsBeforeAutoHide =
                                        (it.maxReportsBeforeAutoHide + 1).coerceAtMost(50)
                                    )
                                }
                            }) { Text("+") }
                        }
                    }
                )
            }

            item {
                TextButton(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Settings are stored in memory only (demo)")
                        }
                    },
                    modifier = Modifier.padding(16.dp)
                ) { Text("About these settings") }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}
