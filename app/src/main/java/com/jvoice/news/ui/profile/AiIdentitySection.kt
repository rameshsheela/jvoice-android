package com.jvoice.news.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jvoice.core.data.AiIdentity
import com.jvoice.news.components.ArticleVideo
import com.jvoice.news.components.NewsImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * "My AI voice & face" on the staff profile: record or upload the voice,
 * upload photos, and hear/see the samples the AI made. The same studio
 * profile the website uses, so AI Shorts everywhere use it.
 */
@Composable
/** [part] is "voice" or "face" - each has its own page on the profile. */
fun AiIdentitySection(name: String, snackbar: SnackbarHostState, part: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by AiIdentity.profile.collectAsState()

    DisposableEffect(Unit) {
        onDispose { AiIdentity.stopRecording() }
    }

    var consent by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }
    var recorded by remember { mutableStateOf<File?>(null) }
    var voiceBusy by remember { mutableStateOf("") }
    var faceBusy by remember { mutableStateOf("") }
    // The last thing that went wrong with the face, kept on screen (a snackbar is gone in seconds).
    var faceError by remember { mutableStateOf("") }

    LaunchedEffect(recording) {
        while (recording) {
            delay(1000)
            seconds += 1
        }
    }

    val p = profile
    val consented = consent || p?.consentGranted == true

    fun say(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    fun train(picked: List<android.net.Uri>) {
        voiceBusy = "Training your AI voice… (about a minute)"
        scope.launch {
            val result = AiIdentity.trainVoice(context, name, recorded, picked)
            voiceBusy = ""
            result.onSuccess {
                recorded = null
                say("Your AI voice is ready - play the sample")
            }.onFailure { say(it.message ?: "Could not train the voice") }
        }
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            AiIdentity.startRecording(context)
                .onSuccess { seconds = 0; recorded = null; recording = true }
                .onFailure { say("Could not start recording: ${it.message}") }
        } else say("Allow the microphone to record your voice")
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) train(uris)
    }
    var scanning by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) scanning = true else say("Allow the camera to scan your face")
    }
    if (scanning) {
        FaceScanDialog(
            onCancel = { scanning = false },
            onDone = { jpegs ->
                scanning = false
                faceBusy = "Uploading your photos and creating your AI avatar…"
                scope.launch {
                    val result = AiIdentity.trainFaceFromJpegs(jpegs)
                    faceBusy = ""
                    result.onSuccess { faceError = ""; say("Your AI avatar is ready - make a sample video") }
                        .onFailure { faceError = it.message ?: "Could not create the avatar"; say(faceError) }
                }
            }
        )
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(4)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        faceBusy = "Uploading your photos and creating your AI avatar…"
        scope.launch {
            val result = AiIdentity.trainFace(context, uris)
            faceBusy = ""
            result.onSuccess { faceError = ""; say("Your AI avatar is ready - make a sample video") }
                .onFailure { faceError = it.message ?: "Could not create the avatar"; say(faceError) }
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (part == "voice") "Record or upload 1–2 minutes of your voice once. Your AI Shorts then speak in your voice - in the app and on jvoicetelugu.com."
                else "Scan your face once and pick a look. Your AI Shorts are then presented by you - in the app and on jvoicetelugu.com.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (p == null) {
                Text("Loading…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            if (!p.consentGranted) {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(
                        "I agree that J Voice may create an AI copy of my voice and face, used only for my own J Voice news videos. I can ask for it to be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }

            // ------------------------------------------------------ voice
            if (part == "voice") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎙️ Voice", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(if (p.voiceReady) "Trained" else "Not trained") })
            }
            if (p.voicePreviewUrl.isNotBlank()) {
                AudioSample(url = p.voicePreviewUrl, label = "Sample in your AI voice")
            }

            if (voiceBusy.isNotBlank()) {
                Text(voiceBusy, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            } else if (recording) {
                Text(
                    "Read this aloud, clearly, in a quiet place:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(AiIdentity.READING_SCRIPT, style = MaterialTheme.typography.bodyLarge)
                Button(
                    onClick = {
                        recorded = AiIdentity.stopRecording()
                        recording = false
                        if (seconds < 20) say("That was short - 60 seconds or more trains a better voice")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Stop recording  (%d:%02d)".format(seconds / 60, seconds % 60))
                }
            } else {
                if (recorded != null) {
                    AudioSample(url = recorded!!.absolutePath, label = "Your recording (%d:%02d)".format(seconds / 60, seconds % 60))
                    Button(onClick = { train(emptyList()) }, enabled = consented, modifier = Modifier.fillMaxWidth()) {
                        Text("Use this recording - train my voice")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = consented,
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                AiIdentity.startRecording(context)
                                    .onSuccess { seconds = 0; recorded = null; recording = true }
                                    .onFailure { say("Could not start recording: ${it.message}") }
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (recorded != null) "Record again" else "Record voice")
                    }
                    OutlinedButton(enabled = consented, onClick = { pickAudio.launch("audio/*") }, modifier = Modifier.weight(1f)) {
                        Text("Upload audio")
                    }
                }
                if (p.voiceReady) {
                    OutlinedButton(
                        onClick = {
                            voiceBusy = "Making a new sample…"
                            scope.launch {
                                AiIdentity.makeVoiceSample(p.voiceId).onFailure { say(it.message ?: "Could not make a sample") }
                                voiceBusy = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Make a new voice sample") }
                }
                if (!consented) {
                    Text("Tick the consent box to record or upload.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            }

            // ------------------------------------------------------- face
            if (part == "face") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🧑 Face", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(if (p.avatarReady) "Avatar ready" else "No avatar") })
            }
            if (p.photos.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.photos.take(4).forEach { url ->
                        NewsImage(url = url, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
                    }
                }
            } else {
                Text(
                    "Scan your face with the camera (6 quick photos), or upload 1–4 clear front-facing photos - good light, no sunglasses.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // ---------------------------------------------------- looks
            if (p.photos.isNotEmpty()) {
                LooksPicker(p = p, onBusy = { faceBusy = it }, say = { say(it) })
            }

            if (p.avatarPreviewUrl.isNotBlank()) {
                Text("Sample video of your AI avatar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ArticleVideo(url = p.avatarPreviewUrl, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))
            }
            if (faceError.isNotBlank() && faceBusy.isBlank()) {
                Text("Last try failed: $faceError", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (faceBusy.isNotBlank()) {
                Text(faceBusy, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            } else {
                // Photos saved but no avatar yet (that step failed): try again without a new scan.
                if (p.photos.isNotEmpty() && !p.avatarReady) {
                    Button(
                        enabled = consented,
                        onClick = {
                            faceBusy = "Creating your AI avatar from these photos…"
                            scope.launch {
                                AiIdentity.createAvatarFromSaved()
                                    .onSuccess { faceError = ""; say("Your AI avatar is ready - make a sample video") }
                                    .onFailure { faceError = it.message ?: "Could not create the avatar"; say(faceError) }
                                faceBusy = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create AI avatar from these photos") }
                }
                // The guided camera scan, as in the studio; uploading stays for photos already on the phone.
                Button(
                    enabled = consented,
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) scanning = true
                        else cameraPermission.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (p.photos.isEmpty()) "📷 Scan my face" else "📷 Scan my face again") }
                // (when photos exist, the create/retry button above is the main action)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        enabled = consented,
                        onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Upload photos") }
                    if (p.avatarReady) {
                        Button(
                            onClick = {
                                scope.launch {
                                    AiIdentity.makeAvatarSample { faceBusy = it }
                                        .onSuccess { say("Your avatar sample is ready") }
                                        .onFailure { say(it.message ?: "Could not make the sample") }
                                    faceBusy = ""
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (p.avatarPreviewUrl.isBlank()) "Make sample video" else "New sample video") }
                    }
                }
            }
            }
        }
    }
}

/** A one-button player for a voice clip (a web URL or a file on the phone). */
@Composable
private fun AudioSample(url: String, label: String) {
    var player by remember(url) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(url) { mutableStateOf(false) }
    DisposableEffect(url) {
        onDispose { player?.release(); player = null }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = {
            val mp = player
            if (playing && mp != null) {
                mp.pause(); playing = false
            } else if (mp != null) {
                mp.start(); playing = true
            } else {
                runCatching {
                    MediaPlayer().apply {
                        setDataSource(url)
                        setOnPreparedListener { it.start(); playing = true }
                        setOnCompletionListener { playing = false }
                        prepareAsync()
                    }
                }.onSuccess { player = it }
            }
        }) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (playing) "Pause" else "Play")
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * "Choose a look": the reporter's photo re-dressed for the camera (suit,
 * field shirt with mic, kurta, standing). Tap an empty look to make it; tap a
 * made look to see it large and use it for the avatar.
 */
@Composable
private fun LooksPicker(p: AiIdentity.Profile, onBusy: (String) -> Unit, say: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var making by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }

    fun make(key: String) {
        making = key
        scope.launch {
            AiIdentity.makeLook(key)
                .onSuccess { open = key }
                .onFailure { say(it.message ?: "Could not make that look") }
            making = ""
        }
    }

    Text("Choose a look", style = MaterialTheme.typography.titleSmall)
    Text(
        "Your face, dressed for the camera. Making a look takes about a minute.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // The own photo counts as a look too.
        LookTile("My photo", p.photos.firstOrNull(), p.avatarReady && p.lookKey.isBlank(), busy = false) { open = "" }
        AiIdentity.LOOKS.forEach { look ->
            val image = p.looks[look.key]
            LookTile(look.name, image, p.avatarReady && p.lookKey == look.key, busy = making == look.key) {
                if (image != null) open = look.key else if (making.isBlank()) make(look.key)
            }
        }
    }

    val key = open ?: return
    val image = if (key.isBlank()) p.photos.firstOrNull() else p.looks[key]
    val look = AiIdentity.LOOKS.firstOrNull { it.key == key }
    val name = look?.name ?: "My photo"
    AlertDialog(
        onDismissRequest = { open = null },
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                look?.let { Text(it.detail, style = MaterialTheme.typography.bodySmall) }
                if (image != null) {
                    NewsImage(url = image, contentDescription = name, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                open = null
                onBusy("Creating your AI avatar in this look…")
                scope.launch {
                    AiIdentity.useLook(key)
                        .onSuccess { say("$name is your avatar now - make a sample video") }
                        .onFailure { say(it.message ?: "Could not use that look") }
                    onBusy("")
                }
            }) { Text("Use this look") }
        },
        dismissButton = {
            Row {
                if (key.isNotBlank()) TextButton(onClick = { open = null; make(key) }) { Text("Make again") }
                TextButton(onClick = { open = null }) { Text("Close") }
            }
        }
    )
}

@Composable
private fun LookTile(name: String, image: String?, selected: Boolean, busy: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 124.dp)
                .clip(shape)
                .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                NewsImage(url = image, contentDescription = name, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    if (busy) "Making…" else "Tap to make",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(name + if (selected) " ✓" else "", style = MaterialTheme.typography.labelMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
    }
}
