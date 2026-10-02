package com.jvoice.aishorts.studio

import android.media.MediaMetadataRetriever
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.jvoice.core.auth.SessionStore
import com.jvoice.core.data.StudioApi
import com.jvoice.core.firebase.FirebaseAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/**
 * AI Shorts in the app, on the same backend as the web studio (J Voice studio/):
 * the studioApi server for script, voice, avatar and render, and the Firestore
 * collections `shorts` (the story being turned into video) and `shortVideos`
 * (its generated video). A short made here opens in the web Editor Desk and
 * vice versa; a published one plays in the Clips tab.
 */

data class ShortMedia(
    val id: String,
    val type: String, // "photo" | "video"
    val url: String,
    val name: String,
    val source: String, // "upload" | "stock" | "commons" | "demo"
    val videoUrl: String? = null,
    val credit: String? = null
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("id", id); put("type", type); put("url", url); put("name", name); put("source", source)
        if (videoUrl != null) put("videoUrl", videoUrl)
        if (credit != null) put("credit", credit)
    }

    fun toJson(): JSONObject = JSONObject(toMap().filterValues { it != null })
}

data class AiShort(
    val id: String,
    val reporterId: String,
    val reporterName: String,
    val title: String,
    val content: String,
    val script: String,
    val language: String,
    val media: List<ShortMedia>,
    val templateId: String,
    val status: String, // draft | processing | pending_review | published | rejected
    val createdAt: String,
    val views: Int,
    val thumbnailUrl: String,
    val videoId: String,
    val editorNote: String,
    val articleId: String,
    val videoUrl: String,
    val posterUrl: String,
    val durationSeconds: Int,
    val publishedAt: Long
) {
    val statusLabel: String
        get() = when (status) {
            "draft" -> "Draft"
            "processing" -> "Processing"
            "pending_review" -> "Waiting for editor"
            "published" -> "Published"
            "rejected" -> "Sent back"
            else -> status
        }
}

data class GeneratedShortVideo(val id: String, val videoUrl: String, val thumbnailUrl: String, val duration: Int)

/** The six studio templates (J Voice studio/src/data/templates.ts). */
data class ShortTemplate(val id: String, val name: String, val description: String)

val SHORT_TEMPLATES = listOf(
    ShortTemplate("tpl-01-reporter-left", "Reporter Left", "Reporter on the left, story visuals behind"),
    ShortTemplate("tpl-02-reporter-right", "Reporter Right", "Reporter on the right, story visuals behind"),
    ShortTemplate("tpl-03-fullscreen", "Full Screen News", "Full-screen visuals, big headline"),
    ShortTemplate("tpl-04-breaking", "Breaking News", "Red breaking-news banner"),
    ShortTemplate("tpl-05-photo-report", "Photo Report", "Photos first, blue accent"),
    ShortTemplate("tpl-06-jvoice-reporter", "J Voice Reporter", "Reporter presents the story")
)

/** The reporter's studio profile (studioReporters/{uid}): their cloned voice and avatar. */
data class StudioProfile(
    val voiceId: String,
    val avatarId: String,
    val avatarReady: Boolean,
    val profileImage: String,
    val location: String
) {
    val voiceTrained: Boolean get() = Regex("^[A-Za-z0-9]{20}$").matches(voiceId)
}

/** One step of generation, for the progress list. */
data class GenStep(val label: String, val state: String = "pending") // pending | active | done | skipped | error

object StudioShorts {

    private const val TAG = "StudioShorts"
    private fun db() = FirebaseFirestore.getInstance()
    private fun uid(): String? = if (FirebaseAvailability.isAvailable) FirebaseAuth.getInstance().currentUser?.uid else null

    fun newId(prefix: String) = prefix + "-" + System.currentTimeMillis().toString(36) + Random.nextInt(0, 1 shl 20).toString(36)

    private fun isoNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())

    /* ---------------------------------------------------------------- read */

    @Suppress("UNCHECKED_CAST")
    private fun toShort(id: String, d: Map<String, Any?>): AiShort = AiShort(
        id = id,
        reporterId = d["reporterId"]?.toString().orEmpty(),
        reporterName = d["reporterName"]?.toString().orEmpty(),
        title = d["title"]?.toString().orEmpty(),
        content = d["content"]?.toString().orEmpty(),
        script = d["script"]?.toString().orEmpty(),
        language = d["language"]?.toString() ?: "te",
        media = (d["media"] as? List<Map<String, Any?>>).orEmpty().map {
            ShortMedia(
                id = it["id"]?.toString().orEmpty(),
                type = it["type"]?.toString() ?: "photo",
                url = it["url"]?.toString().orEmpty(),
                name = it["name"]?.toString().orEmpty(),
                source = it["source"]?.toString() ?: "upload",
                videoUrl = it["videoUrl"]?.toString(),
                credit = it["credit"]?.toString()
            )
        },
        templateId = d["templateId"]?.toString() ?: SHORT_TEMPLATES.first().id,
        status = d["status"]?.toString() ?: "draft",
        createdAt = d["createdAt"]?.toString().orEmpty(),
        views = (d["views"] as? Number)?.toInt() ?: 0,
        thumbnailUrl = d["thumbnailUrl"]?.toString().orEmpty(),
        videoId = d["videoId"]?.toString().orEmpty(),
        editorNote = d["editorNote"]?.toString().orEmpty(),
        articleId = d["articleId"]?.toString().orEmpty(),
        videoUrl = d["videoUrl"]?.toString().orEmpty(),
        posterUrl = d["posterUrl"]?.toString().orEmpty(),
        durationSeconds = (d["durationSeconds"] as? Number)?.toInt() ?: 0,
        publishedAt = (d["publishedAt"] as? Number)?.toLong() ?: 0L
    )

    private fun listen(query: Query): Flow<List<AiShort>> = callbackFlow {
        if (!FirebaseAvailability.isAvailable) {
            trySend(emptyList()); close(); return@callbackFlow
        }
        val reg: ListenerRegistration = query.addSnapshotListener { snap, e ->
            if (e != null) {
                Log.w(TAG, "listen failed: ${e.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents.orEmpty().map { toShort(it.id, it.data.orEmpty()) }.sortedByDescending { it.createdAt })
        }
        awaitClose { reg.remove() }
    }

    /** The signed-in reporter's own shorts. */
    fun mine(): Flow<List<AiShort>> = listen(db().collection("shorts").whereEqualTo("reporterId", uid().orEmpty()))

    /** Every short, for editors (the rules let editors read all). */
    fun all(): Flow<List<AiShort>> = listen(db().collection("shorts"))

    /** Published shorts - the Clips tab. */
    fun published(): Flow<List<AiShort>> = listen(db().collection("shorts").whereEqualTo("status", "published"))

    fun one(id: String): Flow<AiShort?> = callbackFlow {
        if (!FirebaseAvailability.isAvailable) {
            trySend(null); close(); return@callbackFlow
        }
        val reg = db().collection("shorts").document(id).addSnapshotListener { snap, e ->
            trySend(if (e == null && snap != null && snap.exists()) toShort(snap.id, snap.data.orEmpty()) else null)
        }
        awaitClose { reg.remove() }
    }

    suspend fun video(id: String): GeneratedShortVideo? = runCatching {
        val d = db().collection("shortVideos").document(id).get().await()
        if (!d.exists()) null else GeneratedShortVideo(
            id = d.id,
            videoUrl = d.getString("videoUrl").orEmpty(),
            thumbnailUrl = d.getString("thumbnailUrl").orEmpty(),
            duration = (d.get("duration") as? Number)?.toInt() ?: 0
        )
    }.getOrNull()

    suspend fun profile(): StudioProfile {
        val id = uid() ?: return StudioProfile("", "", false, "", "")
        val d = runCatching { db().collection("studioReporters").document(id).get().await() }.getOrNull()
        return StudioProfile(
            voiceId = d?.getString("voiceId").orEmpty(),
            avatarId = d?.getString("avatarId").orEmpty(),
            avatarReady = d?.getString("avatarStatus") == "approved" && !d.getString("avatarId").isNullOrBlank(),
            profileImage = d?.getString("profileImage").orEmpty().ifBlank { SessionStore.session.value?.avatarUrl.orEmpty() },
            location = d?.getString("location").orEmpty()
        )
    }

    /** A J Voice story to start a short from. */
    data class ArticleSeed(val headline: String, val content: String, val language: String, val photos: List<String>)

    /** An article's text and photos (a reporter can read their own). */
    suspend fun article(articleId: String): ArticleSeed? = runCatching {
        val d = db().collection("articles").document(articleId).get().await()
        if (!d.exists()) return@runCatching null
        @Suppress("UNCHECKED_CAST")
        fun side(key: String, lang: String) = (d.get(key) as? Map<String, Any?>)?.get(lang)?.toString().orEmpty()
        val te = side("headline", "te").isNotBlank()
        val lang = if (te) "te" else "en"
        @Suppress("UNCHECKED_CAST")
        val photos = listOfNotNull(d.getString("imageUrl")?.takeIf { it.isNotBlank() }) +
            (d.get("photoUrls") as? List<String>).orEmpty()
        ArticleSeed(side("headline", lang), side("content", lang).ifBlank { side("content", if (te) "en" else "te") }, lang, photos.distinct())
    }.getOrNull()

    /* --------------------------------------------------------------- write */

    private fun shortMap(s: AiShort): Map<String, Any?> = mapOf(
        "id" to s.id,
        "reporterId" to s.reporterId,
        "reporterName" to s.reporterName,
        "title" to s.title,
        "content" to s.content,
        "script" to s.script,
        "language" to s.language,
        "media" to s.media.map { it.toMap() },
        "templateId" to s.templateId,
        "status" to s.status,
        "createdAt" to s.createdAt,
        "views" to s.views,
        "thumbnailUrl" to s.thumbnailUrl,
        "videoId" to s.videoId,
        "articleId" to s.articleId.ifBlank { null },
        "updatedAt" to System.currentTimeMillis()
    ).filterValues { it != null }

    suspend fun submit(id: String): Result<Unit> = runCatching {
        db().collection("shorts").document(id)
            .update(mapOf("status" to "pending_review", "updatedAt" to System.currentTimeMillis())).await()
        Unit
    }

    /** Editor: publish - the clip the app plays is copied onto the short, as the web does. */
    suspend fun approve(s: AiShort): Result<Unit> = runCatching {
        val v = video(s.videoId)
        val url = v?.videoUrl.orEmpty()
        require(url.startsWith("http")) { "This short has no finished video yet." }
        db().collection("shorts").document(s.id).update(
            mapOf(
                "status" to "published",
                "publishedAt" to System.currentTimeMillis(),
                "videoUrl" to url,
                "posterUrl" to (v?.thumbnailUrl ?: s.thumbnailUrl),
                "durationSeconds" to (v?.duration ?: 0),
                "publishedBy" to uid(),
                "editorNote" to null,
                "updatedAt" to System.currentTimeMillis()
            )
        ).await()
        Unit
    }

    suspend fun sendBack(id: String, note: String): Result<Unit> = runCatching {
        db().collection("shorts").document(id)
            .update(mapOf("status" to "rejected", "editorNote" to note.trim(), "updatedAt" to System.currentTimeMillis())).await()
        Unit
    }

    /* ------------------------------------------------------------ AI calls */

    suspend fun generateScript(text: String, language: String, reporterName: String, location: String): Result<String> =
        StudioApi.call(
            "/api/text/script",
            JSONObject().put("text", text).put("language", language).put("reporterName", reporterName).put("location", location)
        ).mapCatching { it.optString("script").ifBlank { throw Exception("The script came back empty.") } }

    /** Stock photos and clips for the story (Pexels), from AI search words. */
    suspend fun stockSuggestions(text: String): Result<List<ShortMedia>> = runCatching {
        val queries = StudioApi.call("/api/text/queries", JSONObject().put("text", text)).getOrThrow()
            .optJSONArray("queries") ?: JSONArray()
        val out = mutableListOf<ShortMedia>()
        for (i in 0 until minOf(2, queries.length())) {
            val q = queries.optString(i)
            if (q.isBlank()) continue
            val items = StudioApi.call("/api/visuals/search?q=" + URLEncoder.encode(q, "UTF-8") + "&per=4").getOrNull()
                ?.optJSONArray("items") ?: continue
            for (j in 0 until items.length()) {
                val it = items.getJSONObject(j)
                out += ShortMedia(
                    id = it.optString("id").ifBlank { newId("stock") },
                    type = it.optString("type", "photo"),
                    url = it.optString("url"),
                    name = q,
                    source = "stock",
                    videoUrl = it.optString("videoUrl").takeIf { v -> v.startsWith("http") },
                    credit = it.optString("credit").ifBlank { null }
                )
            }
        }
        out.distinctBy { it.id }
    }

    private fun estimateDuration(text: String): Int {
        val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
        return maxOf(12, Math.round(words / 2.6f) + 6)
    }

    private suspend fun audioSeconds(url: String, fallback: Int): Int = withContext(Dispatchers.IO) {
        runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(url, HashMap())
            val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            r.release()
            if (ms > 3000) (ms / 1000).toInt() + 2 else fallback
        }.getOrDefault(fallback)
    }

    private fun subtitles(script: String, duration: Int): JSONArray {
        val sentences = script.split(Regex("(?<=[।.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        val arr = JSONArray()
        if (sentences.isEmpty()) return arr
        val per = duration.toDouble() / sentences.size
        sentences.forEachIndexed { i, t ->
            arr.put(JSONObject().put("start", Math.round(i * per * 100) / 100.0).put("end", Math.round((i + 1) * per * 100) / 100.0).put("text", t))
        }
        return arr
    }

    /**
     * Voice → avatar (when the reporter has one) → render → save.
     * Reports each step through [onStep]; any failure stops with its reason
     * and nothing is saved as ready without a real MP4.
     */
    suspend fun generate(
        base: AiShort,
        onStep: (index: Int, state: String, message: String) -> Unit
    ): Result<AiShort> = runCatching {
        val me = uid() ?: throw Exception("You are signed out. Sign in again.")
        val session = SessionStore.session.value
        val profile = profile()
        var script = base.script.trim()

        // 1. Script
        onStep(0, "active", "Writing the narration…")
        if (script.isBlank()) {
            script = generateScript(base.content, base.language, session?.name.orEmpty(), profile.location).getOrThrow()
        }
        onStep(0, "done", "")

        // 2. Voice
        onStep(1, "active", if (profile.voiceTrained) "Speaking it in your voice…" else "Speaking it in the J Voice voice…")
        val voice = StudioApi.call(
            "/api/voice",
            JSONObject().put("text", script).put("language", base.language).apply { if (profile.voiceTrained) put("voiceId", profile.voiceId) }
        ).getOrThrow()
        val audioUrl = voice.optString("audioUrl").ifBlank { throw Exception("The narration came back empty.") }
        val duration = audioSeconds(audioUrl, estimateDuration(script))
        onStep(1, "done", "")

        // 3. Avatar (optional)
        var avatarVideoUrl = ""
        if (profile.avatarReady) {
            onStep(2, "active", "Animating your avatar… (1–3 minutes)")
            val started = StudioApi.call("/api/avatar/generate", JSONObject().put("avatarId", profile.avatarId).put("audioUrl", audioUrl))
            val videoId = started.getOrNull()?.optString("videoId").orEmpty()
            if (videoId.isNotBlank()) {
                for (i in 0 until 75) {
                    delay(4000)
                    val s = StudioApi.call("/api/avatar/status/" + URLEncoder.encode(videoId, "UTF-8")).getOrNull() ?: continue
                    if (s.optString("status") == "completed" && s.optString("url").startsWith("http")) {
                        avatarVideoUrl = s.optString("url"); break
                    }
                    if (s.optString("status") == "failed") break
                    onStep(2, "active", "Animating your avatar… " + minOf(95, 5 + i * 2) + "%")
                }
            }
            onStep(2, if (avatarVideoUrl.isNotBlank()) "done" else "skipped", if (avatarVideoUrl.isBlank()) "Avatar not ready - your photo is used" else "")
        } else {
            onStep(2, "skipped", "No AI avatar yet - your photo is used")
        }

        // 4. Render
        onStep(3, "active", "Rendering the video…")
        val cutout = profile.profileImage.ifBlank { "/studio/avatars/editor-cutout.svg" }
        val data = JSONObject()
            .put("headline", base.title)
            .put("script", script)
            .put("language", base.language)
            .put("reporterName", session?.name ?: base.reporterName)
            .put("reporterTitle", "J Voice Reporter")
            .put("reporterLocation", profile.location.ifBlank { "Telangana" })
            .put("reporterAvatar", cutout)
            .put("reporterCutout", cutout)
            .put("media", JSONArray().apply { base.media.forEach { put(it.toJson()) } })
            .put("audioUrl", audioUrl)
            .put("subtitles", subtitles(script, duration))
            .apply { if (avatarVideoUrl.isNotBlank()) put("avatarVideoUrl", avatarVideoUrl) }
        val render = StudioApi.call(
            "/api/video/render",
            JSONObject().put("templateId", base.templateId).put("data", data).put("duration", duration)
        ).getOrThrow()
        val renderId = render.optString("renderId").ifBlank { throw Exception("The render did not start.") }
        var videoUrl = ""
        for (i in 0 until 100) {
            delay(3000)
            val s = StudioApi.call("/api/video/render/" + URLEncoder.encode(renderId, "UTF-8")).getOrNull() ?: continue
            when (s.optString("status")) {
                "succeeded" -> if (s.optString("url").startsWith("http")) videoUrl = s.optString("url")
                "failed" -> throw Exception(s.optString("error").ifBlank { "The render failed." })
            }
            if (videoUrl.isNotBlank()) break
            onStep(3, "active", "Rendering the video… " + minOf(95, 5 + i * 3) + "%")
        }
        if (videoUrl.isBlank()) throw Exception("The render is taking too long. Try again in a few minutes.")
        onStep(3, "done", "")

        // 5. Save - the same documents the web studio writes.
        onStep(4, "active", "Saving…")
        val videoId = newId("vid")
        val thumb = base.media.firstOrNull()?.url ?: "/studio/media/city-generic.svg"
        val createdAt = base.createdAt.ifBlank { isoNow() }
        db().collection("shortVideos").document(videoId).set(
            mapOf(
                "id" to videoId,
                "storyId" to base.id,
                "reporterId" to me,
                "templateId" to base.templateId,
                "audioUrl" to audioUrl,
                "videoUrl" to videoUrl,
                "thumbnailUrl" to thumb,
                "duration" to duration,
                "status" to "ready",
                "createdAt" to isoNow(),
                "updatedAt" to System.currentTimeMillis()
            ).let { if (avatarVideoUrl.isNotBlank()) it + ("avatarVideoUrl" to avatarVideoUrl) else it }
        ).await()
        val saved = base.copy(
            reporterId = me,
            reporterName = session?.name ?: base.reporterName,
            script = script,
            status = "draft",
            createdAt = createdAt,
            thumbnailUrl = thumb,
            videoId = videoId
        )
        db().collection("shorts").document(saved.id).set(shortMap(saved), SetOptions.merge()).await()
        onStep(4, "done", "")
        saved
    }

    /** A new short, before generation. */
    fun blank(): AiShort = AiShort(
        id = newId("story"), reporterId = uid().orEmpty(), reporterName = SessionStore.session.value?.name.orEmpty(),
        title = "", content = "", script = "", language = "te", media = emptyList(),
        templateId = SHORT_TEMPLATES.first().id, status = "draft", createdAt = "", views = 0, thumbnailUrl = "",
        videoId = "", editorNote = "", articleId = "", videoUrl = "", posterUrl = "", durationSeconds = 0, publishedAt = 0
    )
}
