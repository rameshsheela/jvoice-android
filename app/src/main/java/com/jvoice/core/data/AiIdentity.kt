package com.jvoice.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * A staff member's AI voice and face - the same studio profile the website
 * uses (Firestore studioReporters/{uid}), trained through the same studio
 * server (StudioApi):
 *
 *  * voice: 1-2 minutes of their reading, recorded here or picked as a file,
 *    is cloned by ElevenLabs; a short Telugu sample in that voice is kept
 *  * face: 1-4 photos go to Storage (studio/uploads/{uid}/); the first becomes
 *    a HeyGen talking avatar, and a sample video of it speaking is kept
 *
 * AI Shorts then use this voice and face automatically, in the app and on the
 * website.
 */
object AiIdentity {

    private const val TAG = "AiIdentity"

    /** Read aloud while recording - clear, varied Telugu for the clone to learn from. */
    const val READING_SCRIPT =
        "నమస్కారం. నేను జె వాయిస్ తెలుగు రిపోర్టర్‌ని. మా ప్రాంతంలో జరుగుతున్న ముఖ్యమైన వార్తలను, " +
            "ప్రజల సమస్యలను, ఉద్యోగ మరియు విద్యా సమాచారాన్ని మీకు ఎప్పటికప్పుడు అందిస్తాను. " +
            "ఈ రోజు మన ఊరిలో కురిసిన భారీ వర్షానికి రోడ్లు జలమయమయ్యాయి. అధికారులు వెంటనే స్పందించి " +
            "సహాయక చర్యలు చేపట్టారు. ప్రజలు జాగ్రత్తగా ఉండాలని, అవసరమైతే తప్ప బయటకు రావద్దని సూచించారు. " +
            "మరిన్ని వివరాల కోసం జె వాయిస్ తెలుగు చూస్తూ ఉండండి. ధన్యవాదాలు."

    private const val SAMPLE_TE =
        "నమస్కారం. నేను జె వాయిస్ తెలుగు రిపోర్టర్‌ని. మా ప్రాంతంలోని ముఖ్యమైన వార్తలను మీకు అందిస్తున్నాను."

    data class Profile(
        val voiceId: String = "",
        val voicePreviewUrl: String = "",
        val avatarId: String = "",
        val avatarStatus: String = "",
        val avatarPreviewUrl: String = "",
        val photos: List<String> = emptyList(),
        val consentGranted: Boolean = false,
        /** Generated looks by key (anchor, field, kurta, standing) -> image URL. */
        val looks: Map<String, String> = emptyMap(),
        /** The look the avatar was made from, or "" for the reporter's own photo. */
        val lookKey: String = ""
    ) {
        val voiceReady get() = Regex("^[A-Za-z0-9]{20}$").matches(voiceId)
        val avatarReady get() = avatarStatus == "approved" && avatarId.isNotBlank()
    }

    private val _profile = MutableStateFlow<Profile?>(null)
    /** Null until loaded. */
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    private var listener: ListenerRegistration? = null

    private fun uid(): String? =
        if (!FirebaseAvailability.isAvailable) null else FirebaseAuth.getInstance().currentUser?.uid

    private fun doc(uid: String) = FirebaseFirestore.getInstance().collection("studioReporters").document(uid)

    fun watch() {
        val uid = uid() ?: return
        listener?.remove()
        listener = doc(uid).addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "profile: ${err.message}")
                _profile.value = Profile()
                return@addSnapshotListener
            }
            val d = snap?.data.orEmpty()
            @Suppress("UNCHECKED_CAST")
            val photos = (d["photos"] as? List<Any?>).orEmpty().mapNotNull { it?.toString() }
                .ifEmpty { listOfNotNull(d["profileImage"]?.toString()) }
                .filter { it.startsWith("http") }
            _profile.value = Profile(
                voiceId = d["voiceId"]?.toString().orEmpty(),
                voicePreviewUrl = d["voicePreviewUrl"]?.toString().orEmpty(),
                avatarId = d["avatarId"]?.toString().orEmpty(),
                avatarStatus = d["avatarStatus"]?.toString().orEmpty(),
                avatarPreviewUrl = d["avatarPreviewUrl"]?.toString().orEmpty(),
                photos = photos,
                consentGranted = d["consentStatus"] == "granted",
                looks = (d["looks"] as? Map<*, *>).orEmpty()
                    .mapNotNull { (k, v) -> if (k != null && v is String && v.startsWith("http")) k.toString() to v else null }
                    .toMap(),
                lookKey = d["lookKey"]?.toString().orEmpty()
            )
        }
    }

    fun stop() {
        listener?.remove()
        listener = null
    }

    private suspend fun save(patch: Map<String, Any?>) {
        val uid = uid() ?: throw Exception("You are signed out. Sign in again.")
        doc(uid).set(patch + ("id" to uid), SetOptions.merge()).await()
    }

    suspend fun grantConsent(): Result<Unit> = runCatching { save(mapOf("consentStatus" to "granted")) }

    /* ---------------------------------------------------------------- voice */

    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    /** Starts recording from the microphone. The caller has the RECORD_AUDIO grant. */
    fun startRecording(context: Context): Result<Unit> = runCatching {
        stopRecording()
        // Ogg/Opus where the phone can (API 29+): small, clear, and a format the voice service reads.
        val ogg = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val file = File(context.cacheDir, "voice-sample-${System.currentTimeMillis()}." + if (ogg) "ogg" else "m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        if (ogg) {
            r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
        } else {
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        r.setAudioSamplingRate(48000)
        r.setAudioEncodingBitRate(96000)
        r.setAudioChannels(1)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
        recordingFile = file
    }

    /** Stops recording; returns the recorded file, or null when nothing was recording. */
    fun stopRecording(): File? {
        val r = recorder ?: return null
        recorder = null
        return runCatching {
            r.stop()
            r.release()
            recordingFile
        }.getOrElse {
            r.release()
            null
        }
    }

    private fun dataUrl(mime: String, bytes: ByteArray) =
        "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Clones the voice from a recording file and/or picked audio files, then makes a sample. */
    suspend fun trainVoice(context: Context, name: String, recorded: File?, picked: List<Uri>): Result<Unit> = runCatching {
        val samples = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            recorded?.let {
                val mime = if (it.name.endsWith(".ogg")) "audio/ogg" else "audio/mp4"
                samples += dataUrl(mime, it.readBytes())
            }
            picked.take(3).forEach { uri ->
                val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read the audio file.")
                samples += dataUrl(mime, bytes)
            }
        }
        if (samples.isEmpty()) throw Exception("Record your voice or pick an audio file first.")
        if (samples.sumOf { it.length } > 26_000_000) throw Exception("The recordings are too long - keep them under about 3 minutes.")
        val res = StudioApi.call(
            "/api/voice/clone",
            JSONObject().put("name", name).put("samples", JSONArray(samples)).put("description", "J Voice reporter $name")
        ).getOrThrow()
        val voiceId = res.optString("voiceId").ifBlank { throw Exception("The voice service returned no voice.") }
        save(mapOf("voiceId" to voiceId, "voiceStatus" to "approved", "consentStatus" to "granted"))
        makeVoiceSample(voiceId).getOrThrow()
    }

    suspend fun makeVoiceSample(voiceId: String): Result<Unit> = runCatching {
        val res = StudioApi.call(
            "/api/voice",
            JSONObject().put("text", SAMPLE_TE).put("voiceId", voiceId).put("language", "te")
        ).getOrThrow()
        save(mapOf("voicePreviewUrl" to res.optString("audioUrl")))
    }

    /* ----------------------------------------------------------------- face */

    /** Uploads 1-4 photos and makes the first one a talking avatar. */
    suspend fun trainFace(context: Context, uris: List<Uri>): Result<Unit> = runCatching {
        if (uris.isEmpty()) throw Exception("Pick 1 to 4 photos of your face.")
        val jpegs = withContext(Dispatchers.IO) { uris.take(4).map { shrink(context, it) } }
        trainFaceFromJpegs(jpegs).getOrThrow()
    }

    /** The face scan's photos (JPEG, straight-on first) - uploaded, then the first becomes the avatar. */
    suspend fun trainFaceFromJpegs(jpegs: List<ByteArray>): Result<Unit> = runCatching {
        val uid = uid() ?: throw Exception("You are signed out. Sign in again.")
        if (jpegs.isEmpty()) throw Exception("No photos were taken.")
        val urls = jpegs.take(6).map { jpeg ->
            val ref = FirebaseStorage.getInstance().reference.child("studio/uploads/$uid/${UUID.randomUUID()}.jpg")
            ref.putBytes(jpeg, StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
            ref.downloadUrl.await().toString()
        }
        save(mapOf("photos" to urls, "profileImage" to urls.first(), "cutoutImage" to urls.first(), "consentStatus" to "granted"))
        val res = StudioApi.call("/api/avatar/create", JSONObject().put("photo", urls.first())).getOrThrow()
        val avatarId = res.optString("avatarId").ifBlank { throw Exception("The avatar service returned no avatar.") }
        save(mapOf("avatarId" to avatarId, "avatarStatus" to "approved", "avatarPreviewUrl" to null))
    }

    /** Makes the avatar from the photos already saved - the retry when that step failed. */
    suspend fun createAvatarFromSaved(): Result<Unit> = runCatching {
        val photo = _profile.value?.photos?.firstOrNull() ?: throw Exception("Scan or upload your photos first.")
        val res = StudioApi.call("/api/avatar/create", JSONObject().put("photo", photo)).getOrThrow()
        val avatarId = res.optString("avatarId").ifBlank { throw Exception("The avatar service returned no avatar.") }
        save(mapOf("avatarId" to avatarId, "avatarStatus" to "approved", "avatarPreviewUrl" to null))
    }

    /** The looks a reporter can present in: key, name, what it is. */
    data class Look(val key: String, val name: String, val detail: String)

    val LOOKS = listOf(
        Look("anchor", "News anchor", "Dark suit and tie, TV studio"),
        Look("field", "Field reporter", "Formal shirt, handheld mic, on the street"),
        Look("kurta", "Formal kurta", "White kurta, office"),
        Look("standing", "Standing", "Standing still, blazer, newsroom")
    )

    /** Re-dresses the reporter's straight-on photo in [key]'s look (about 30-60 seconds). */
    suspend fun makeLook(key: String): Result<String> = runCatching {
        val photo = _profile.value?.photos?.firstOrNull() ?: throw Exception("Scan or upload your photos first.")
        val res = StudioApi.call("/api/avatar/look", JSONObject().put("photo", photo).put("look", key), timeoutMs = 180_000).getOrThrow()
        val url = res.optString("url").ifBlank { throw Exception("The look came back empty.") }
        save(mapOf("looks" to mapOf(key to url)))
        url
    }

    /** Makes the avatar from a look, or from the own photo when [key] is "". */
    suspend fun useLook(key: String): Result<Unit> = runCatching {
        val p = _profile.value ?: throw Exception("Your profile is still loading.")
        val image = (if (key.isBlank()) p.photos.firstOrNull() else p.looks[key]) ?: throw Exception("Make that look first.")
        val res = StudioApi.call("/api/avatar/create", JSONObject().put("photo", image)).getOrThrow()
        val avatarId = res.optString("avatarId").ifBlank { throw Exception("The avatar service returned no avatar.") }
        save(
            mapOf(
                "avatarId" to avatarId, "avatarStatus" to "approved", "avatarPreviewUrl" to null,
                "lookKey" to key, "profileImage" to image, "cutoutImage" to image
            )
        )
    }

    /** A photo shrunk to 1080 px JPEG - enough for an avatar, quick to upload. */
    private fun shrink(context: Context, uri: Uri): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1080) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw Exception("Could not read the photo.")
        val scale = minOf(1f, 1080f / maxOf(bmp.width, bmp.height))
        val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        return ByteArrayOutputStream().use { s ->
            out.compress(Bitmap.CompressFormat.JPEG, 85, s)
            s.toByteArray()
        }
    }

    /** Makes a sample video of the avatar speaking (the voice sample, or a stock line). */
    suspend fun makeAvatarSample(onProgress: (String) -> Unit): Result<Unit> = runCatching {
        val p = _profile.value ?: throw Exception("Your profile is still loading.")
        if (!p.avatarReady) throw Exception("Upload your photos first.")
        onProgress("Asking the AI to animate your photo…")
        val body = JSONObject().put("avatarId", p.avatarId)
        if (p.voicePreviewUrl.isNotBlank()) body.put("audioUrl", p.voicePreviewUrl) else body.put("text", SAMPLE_TE)
        val videoId = StudioApi.call("/api/avatar/generate", body).getOrThrow().optString("videoId")
            .ifBlank { throw Exception("The avatar service did not start the video.") }
        for (i in 0 until 90) {
            delay(4000)
            onProgress("Animating your avatar… ${minOf(95, 5 + i * 2)}% (1–3 minutes)")
            val s = StudioApi.call("/api/avatar/status/${Uri.encode(videoId)}").getOrNull() ?: continue
            when (s.optString("status")) {
                "completed" -> {
                    save(mapOf("avatarPreviewUrl" to s.optString("url")))
                    return@runCatching
                }
                "failed" -> throw Exception(s.optString("error").ifBlank { "The avatar video failed." })
            }
        }
        throw Exception("It is taking too long - try again later.")
    }
}
