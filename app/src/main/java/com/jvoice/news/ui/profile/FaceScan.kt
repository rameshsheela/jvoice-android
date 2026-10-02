package com.jvoice.news.ui.profile

import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * The guided face scan for a reporter's AI avatar - the prototype studio's
 * FaceScan, native: the front camera, an oval to put the face in, and six
 * prompts (straight, one side, the other side, up, down, straight with a
 * smile). ML Kit checks the head pose on the phone - no frame leaves it - and a
 * photo is taken by itself once the pose is held; "Take photo" is always there
 * in case the check does not settle.
 *
 * Hands back the photos as JPEG bytes, the straight-on one first - that one
 * becomes the talking avatar.
 */

private enum class Pose { FRONT, SIDE_A, SIDE_B, UP, DOWN, SMILE }

private data class Prompt(val pose: Pose, val title: String, val hint: String, val arrow: String)

private val PROMPTS = listOf(
    Prompt(Pose.FRONT, "Look straight at the camera", "Face inside the oval, shoulders visible", "●"),
    Prompt(Pose.SIDE_A, "Turn your head to one side", "Slowly, until the photo is taken", "↔"),
    Prompt(Pose.SIDE_B, "Now to the other side", "Slowly, until the photo is taken", "↔"),
    Prompt(Pose.UP, "Look up a little", "Raise your chin", "↑"),
    Prompt(Pose.DOWN, "Look down a little", "Lower your chin", "↓"),
    Prompt(Pose.SMILE, "Back to the centre, gentle smile", "Hold still for the last photo", "●")
)

private const val HOLD_MS = 700L
private const val TURN_DEG = 18f
private const val PITCH_DEG = 10f

/** What the camera sees right now, from ML Kit. */
private data class Seen(val faces: Int, val yaw: Float, val pitch: Float, val sizeFraction: Float)

@Composable
fun FaceScanDialog(onDone: (List<ByteArray>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    var step by remember { mutableStateOf(0) }
    var seen by remember { mutableStateOf(Seen(0, 0f, 0f, 0f)) }
    var holdingSince by remember { mutableStateOf<Long?>(null) }
    var firstSideSign by remember { mutableStateOf(0f) }
    val shots = remember { mutableStateListOf<Bitmap>() }
    var cameraError by remember { mutableStateOf<String?>(null) }

    // Camera + face analysis, bound for as long as the dialog is open.
    DisposableEffect(Unit) {
        val executor = Executors.newSingleThreadExecutor()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .build()
        )
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            runCatching {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                        return@setAnalyzer
                    }
                    val rotation = proxy.imageInfo.rotationDegrees
                    val width = if (rotation % 180 == 0) proxy.width else proxy.height
                    detector.process(InputImage.fromMediaImage(media, rotation))
                        .addOnSuccessListener { faces ->
                            val f = faces.maxByOrNull { it.boundingBox.width() }
                            seen = Seen(
                                faces = faces.size,
                                yaw = f?.headEulerAngleY ?: 0f,
                                pitch = f?.headEulerAngleX ?: 0f,
                                sizeFraction = if (f == null) 0f else f.boundingBox.width().toFloat() / width
                            )
                        }
                        .addOnCompleteListener { proxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
            }.onFailure { cameraError = "Could not open the front camera: ${it.message}" }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            detector.close()
            executor.shutdown()
        }
    }

    val prompt = PROMPTS.getOrNull(step)

    // Is the face where this prompt wants it? Plus a line of coaching when not.
    fun check(s: Seen): Pair<Boolean, String> {
        if (s.faces == 0) return false to "No face seen - move into the light"
        if (s.faces > 1) return false to "Only one person in the picture"
        if (s.sizeFraction < 0.25f) return false to "Move closer"
        if (s.sizeFraction > 0.8f) return false to "Move back a little"
        return when (prompt?.pose) {
            Pose.FRONT, Pose.SMILE ->
                if (abs(s.yaw) < 8f && abs(s.pitch) < 10f) true to "Hold still…" else false to "Look straight at the camera"
            Pose.SIDE_A ->
                if (abs(s.yaw) > TURN_DEG) true to "Hold…" else false to "Turn your head a bit more"
            Pose.SIDE_B ->
                if (abs(s.yaw) > TURN_DEG && s.yaw * firstSideSign < 0) true to "Hold…"
                else false to if (s.yaw * firstSideSign > 0) "The other side" else "Turn your head a bit more"
            Pose.UP -> if (s.pitch > PITCH_DEG) true to "Hold…" else false to "Raise your chin a little"
            Pose.DOWN -> if (s.pitch < -PITCH_DEG) true to "Hold…" else false to "Lower your chin a little"
            null -> false to ""
        }
    }

    fun capture() {
        val bmp = previewView.bitmap ?: return
        shots += bmp
        if (prompt?.pose == Pose.SIDE_A) firstSideSign = if (seen.yaw >= 0f) 1f else -1f
        holdingSince = null
        step += 1
    }

    // Take the photo by itself once the pose has been held.
    val (ok, coach) = check(seen)
    LaunchedEffect(ok, step) {
        if (!ok || prompt == null) {
            holdingSince = null
            return@LaunchedEffect
        }
        holdingSince = System.currentTimeMillis()
        delay(HOLD_MS)
        if (check(seen).first && step < PROMPTS.size) capture()
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (prompt != null) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                // The oval the face goes in: green when the pose is right.
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width * 0.66f
                    val h = w * 1.3f
                    drawOval(
                        color = if (ok) Color(0xFF34D399) else Color.White.copy(alpha = 0.85f),
                        topLeft = Offset((size.width - w) / 2, size.height * 0.42f - h / 2),
                        size = Size(w, h),
                        style = Stroke(width = 6.dp.toPx())
                    )
                }
                Column(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(progress = { step / PROMPTS.size.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("Photo ${step + 1} of ${PROMPTS.size}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    Text(prompt.arrow, color = Color.White, fontSize = 40.sp)
                    Text(prompt.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text(if (ok) coach else prompt.hint, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center)
                    if (!ok && coach.isNotBlank()) {
                        Text(coach, color = Color(0xFFFDE047), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    cameraError?.let { Text(it, color = Color(0xFFFCA5A5)) }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel", color = Color.White) }
                    Button(onClick = { capture() }, modifier = Modifier.weight(1f)) { Text("Take photo") }
                }
            } else {
                // All six taken: check them, then use or redo.
                Column(
                    Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Your photos", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("The first one becomes your AI avatar.", color = Color.White.copy(alpha = 0.8f))
                    shots.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { b ->
                                Image(
                                    bitmap = b.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).height(150.dp).clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            // Straight-on first; the smile shot is the second choice.
                            val order = listOf(0, 5, 1, 2, 3, 4).filter { it < shots.size }
                            onDone(order.map { jpeg(shots[it]) })
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Use these photos") }
                    TextButton(
                        onClick = { shots.clear(); step = 0; firstSideSign = 0f },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Take them again", color = Color.White) }
                }
            }
        }
    }
}

private fun jpeg(b: Bitmap): ByteArray {
    val scale = minOf(1f, 1080f / maxOf(b.width, b.height))
    val out = if (scale < 1f) Bitmap.createScaledBitmap(b, (b.width * scale).toInt(), (b.height * scale).toInt(), true) else b
    return ByteArrayOutputStream().use { s ->
        out.compress(Bitmap.CompressFormat.JPEG, 88, s)
        s.toByteArray()
    }
}
