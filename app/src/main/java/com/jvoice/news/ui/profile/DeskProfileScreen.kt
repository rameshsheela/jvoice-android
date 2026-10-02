package com.jvoice.news.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jvoice.core.auth.DeskProfile
import com.jvoice.core.auth.JvRole
import com.jvoice.core.auth.SessionStore
import com.jvoice.news.components.ConfirmDialog
import com.jvoice.news.components.NewsImage
import com.jvoice.news.data.repository.NewsRepository
import kotlinx.coroutines.launch

/**
 * AI voice and face set-up (voice recording, camera face scan) stays on the
 * website's studio. Off in the Play build, which ships without the CAMERA and
 * RECORD_AUDIO permissions - see AndroidManifest.xml.
 */
private const val AI_IDENTITY_IN_APP = false

/**
 * A staff member's own profile - reporter, editor or admin: photo, name,
 * phone, password, and signing out. The login id is shown but fixed; it is
 * also the employee id and only an admin changes accounts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeskProfileScreen(onBack: () -> Unit, onSignOut: () -> Unit) {
    val session by SessionStore.session.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(session?.name.orEmpty()) }
    var phone by remember { mutableStateOf(session?.phone.orEmpty()) }
    var loginId by remember { mutableStateOf(session?.loginId.orEmpty()) }
    var savingDetails by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // The profile is a short menu; each item opens its own page (null = the menu).
    var page by remember { mutableStateOf<String?>(null) }
    androidx.activity.compose.BackHandler(enabled = page != null) { page = null }
    val isNewsDesk = session?.role in setOf(JvRole.REPORTER, JvRole.EDITOR, JvRole.NEWS_ADMIN, JvRole.SUPER_ADMIN)
    val ai by com.jvoice.core.data.AiIdentity.profile.collectAsState()
    DisposableEffect(Unit) {
        com.jvoice.core.data.AiIdentity.watch()
        onDispose { com.jvoice.core.data.AiIdentity.stop() }
    }

    // The server copy wins over the cached one - it may have been changed on
    // the website or by an admin.
    LaunchedEffect(Unit) {
        DeskProfile.load().onSuccess { p ->
            name = p.name
            phone = p.phone
            loginId = p.loginId
            SessionStore.updateProfile(name = p.name, phone = p.phone, avatarUrl = p.avatarUrl)
            NewsRepository.refreshCurrentUser()
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        uploading = true
        scope.launch {
            val result = DeskProfile.uploadPhoto(context, uri)
            uploading = false
            result.onSuccess { NewsRepository.refreshCurrentUser() }
            snackbar.showSnackbar(result.fold({ "Photo updated" }, { it.message ?: "Upload failed" }))
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            message = "You will need your login ID and password to sign in again on this phone.",
            confirmLabel = "Sign out",
            onConfirm = { confirmSignOut = false; onSignOut() },
            onDismiss = { confirmSignOut = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (page) {
                            "details" -> "Personal details"
                            "voice" -> "AI voice"
                            "face" -> "AI avatar & look"
                            "password" -> "Change password"
                            else -> "My profile"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (page != null) page = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------------------------------------------------------- header
            if (page == null) Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Avatar(
                        url = session?.avatarUrl.orEmpty().ifBlank {
                            ai?.let { a -> a.looks[a.lookKey] ?: a.photos.firstOrNull() }.orEmpty()
                        },
                        name = name,
                        size = 108,
                        modifier = Modifier.clickable(enabled = !uploading) {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uploading) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(name.ifBlank { "Your name" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(roleLabel(session?.role), loginId).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (page == null) {
                val a = ai
                val lookName = com.jvoice.core.data.AiIdentity.LOOKS.firstOrNull { it.key == a?.lookKey }?.name ?: "My photo"
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ProfileRow("👤", "Personal details", listOf(name, phone).filter { it.isNotBlank() }.joinToString(" · ")) { page = "details" }
                    if (isNewsDesk && AI_IDENTITY_IN_APP) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ProfileRow(
                            "🎙️", "AI voice",
                            when {
                                a == null -> "Loading…"
                                !a.voiceReady -> "Not set up - record your voice"
                                a.voicePreviewUrl.isNotBlank() -> "Trained · sample ready"
                                else -> "Trained"
                            },
                            attention = a != null && !a.voiceReady
                        ) { page = "voice" }
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                        ProfileRow(
                            "🧑", "AI avatar & look",
                            when {
                                a == null -> "Loading…"
                                a.photos.isEmpty() -> "Not set up - scan your face"
                                !a.avatarReady -> "Photos saved · avatar not made yet"
                                a.avatarPreviewUrl.isNotBlank() -> "$lookName · sample video ready"
                                else -> lookName
                            },
                            attention = a != null && !a.avatarReady
                        ) { page = "face" }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ProfileRow("🔒", "Change password", "Your sign-in password") { page = "password" }
                }
            }

            // --------------------------------------------------------- details
            if (page == "details") SectionCard(title = "Your details") {
                OutlinedTextField(
                    value = loginId,
                    onValueChange = {},
                    readOnly = true,
                    enabled = false,
                    label = { Text("Login ID = Employee ID") },
                    supportingText = { Text("Set by the admin - it cannot be changed here.") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !savingDetails,
                    onClick = {
                        savingDetails = true
                        scope.launch {
                            val result = DeskProfile.saveDetails(name, phone)
                            savingDetails = false
                            result.onSuccess { NewsRepository.refreshCurrentUser() }
                            snackbar.showSnackbar(result.fold({ "Details saved" }, { it.message ?: "Could not save" }))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (savingDetails) "Saving…" else "Save details") }
            }

            // ------------------------------------------ AI voice & face
            // For the news desk, who make AI Shorts.
            if (AI_IDENTITY_IN_APP && (page == "voice" || page == "face")) {
                AiIdentitySection(name = name, snackbar = snackbar, part = page!!)
            }

            // -------------------------------------------------------- password
            if (page == "password") SectionCard(title = "Change password") {
                val visual = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                val eye: @Composable () -> Unit = {
                    IconButton(onClick = { showPasswords = !showPasswords }) {
                        Icon(
                            if (showPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPasswords) "Hide" else "Show"
                        )
                    }
                }
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = visual,
                    trailingIcon = eye,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    supportingText = { Text("At least 8 characters") },
                    singleLine = true,
                    visualTransformation = visual,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                val mismatch = confirmPassword.isNotEmpty() && confirmPassword != newPassword
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) ({ Text("The two passwords do not match") }) else null,
                    visualTransformation = visual,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    enabled = !changingPassword && currentPassword.isNotBlank() &&
                        newPassword.length >= 8 && newPassword == confirmPassword,
                    onClick = {
                        changingPassword = true
                        scope.launch {
                            val result = DeskProfile.changePassword(currentPassword, newPassword)
                            changingPassword = false
                            result.onSuccess {
                                currentPassword = ""; newPassword = ""; confirmPassword = ""
                            }
                            snackbar.showSnackbar(result.fold({ "Password changed" }, { it.message ?: "Could not change it" }))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (changingPassword) "Changing…" else "Change password") }
            }

            if (page == null) OutlinedButton(
                onClick = { confirmSignOut = true },
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sign out")
            }
        }
    }
}

/** One line of the profile menu: an icon, what it is, and where it stands. */
@Composable
private fun ProfileRow(icon: String, title: String, status: String, attention: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (attention) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

private fun roleLabel(role: JvRole?): String = when (role) {
    JvRole.REPORTER -> "Reporter"
    JvRole.EDITOR -> "Editor"
    JvRole.NEWS_ADMIN -> "News admin"
    JvRole.SUPER_ADMIN -> "Super admin"
    JvRole.CONTENT_CREATOR -> "Content creator"
    JvRole.EXAM_ADMIN -> "Exam admin"
    JvRole.STUDY_ADMIN -> "Study admin"
    null -> ""
}

/**
 * The person's photo in a circle, or their initials when there is none.
 * Also used for the profile button on the dashboards.
 */
@Composable
fun Avatar(url: String, name: String, size: Int, modifier: Modifier = Modifier) {
    val shape = CircleShape
    if (url.isNotBlank()) {
        NewsImage(
            url = url,
            contentDescription = "Profile photo",
            modifier = modifier.size(size.dp).clip(shape)
        )
    } else {
        val initials = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            .take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
        Box(
            modifier
                .size(size.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initials,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = if (size >= 64) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
