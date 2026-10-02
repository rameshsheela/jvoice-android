package com.jvoice.news.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jvoice.shell.LocalFullscreenHost
import com.jvoice.news.R

/*
 * Story video, from either of the two places a reporter may put one:
 *
 *  * **YouTube** - any watch / share / Shorts / embed link. Not played in
 *    the app: YouTube's embed is unreliable inside a third-party WebView
 *    (region, ads, "video unavailable"), so the card shows YouTube's own
 *    thumbnail for the video with a play badge, and a tap hands over to the
 *    YouTube app or site. The reader gets YouTube's player, which always
 *    works, at the cost of leaving J Voice for the duration.
 *  * **Our own storage** - a direct file URL, typically the download URL of
 *    an upload to Firebase Storage (mp4 or webm). Played in place with
 *    ExoPlayer.
 *
 * A storage video takes the story photo's place: [VideoOrPhoto] shows the
 * photo with a play button until it is tapped, so a feed of video stories
 * does not spin up a player per card as it scrolls, and only then swaps in
 * the player.
 *
 * The player has its own fullscreen button. Fullscreen is a dialog over the
 * whole window, landscape and immersive, and it hosts the *same* player view
 * - the ExoPlayer view is created once and re-parented - so going fullscreen
 * and back does not restart the video.
 */

/** The photo, with a play button when the story has a video; the player once tapped. */
@Composable
fun VideoOrPhoto(
    videoUrl: String?,
    imageUrl: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    /** 16:9 with rounded corners; false fills whatever frame the caller gives. */
    framed: Boolean = true,
    onPhotoClick: (() -> Unit)? = null,
    /** Composes over the photo (badges, byline); hidden while the video plays. */
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val youtubeId = remember(videoUrl) { videoUrl?.let(::youtubeVideoId) }
    var playing by remember(videoUrl) { mutableStateOf(false) }

    if (videoUrl != null && youtubeId == null && playing) {
        ArticleVideo(url = videoUrl, modifier = modifier, framed = framed)
        return
    }

    val frame = if (framed) {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
    } else {
        modifier
    }
    Box(
        frame.then(
            if (onPhotoClick != null) Modifier.clickable(onClick = onPhotoClick) else Modifier
        )
    ) {
        NewsImage(
            // A YouTube story shows YouTube's own thumbnail for the video, so
            // the reader sees what they will get when they tap.
            url = if (youtubeId != null) youtubeThumbnail(youtubeId) else imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize()
        )
        overlay()
        if (videoUrl != null) {
            // The play button is the one thing on the photo that must not
            // open the article: it plays the video in place, or hands a
            // YouTube link to YouTube.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(if (youtubeId != null) YouTubeRed else Color.Black.copy(alpha = 0.55f))
                    .clickable {
                        if (youtubeId != null) openYouTube(context, youtubeId) else playing = true
                    }
                    .padding(horizontal = if (youtubeId != null) 18.dp else 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = if (youtubeId != null) "Watch on YouTube" else "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                    if (youtubeId != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Watch on YouTube",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/** YouTube's brand red, for the badge that hands over to it. */
private val YouTubeRed = Color(0xFFFF0000)

/** YouTube's stock thumbnail for a video. `hqdefault` exists for every video. */
fun youtubeThumbnail(videoId: String) = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

/**
 * Opens the video in the YouTube app when installed, else in the browser.
 * A `vnd.youtube:` intent would target the app only; the plain https link
 * lets Android pick whichever the reader has.
 */
private fun openYouTube(context: Context, videoId: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** The player for one video URL, with a fullscreen button. */
@Composable
fun ArticleVideo(url: String, modifier: Modifier = Modifier, framed: Boolean = true) {
    val context = LocalContext.current
    val host = LocalFullscreenHost.current
    var fullscreen by remember { mutableStateOf(false) }

    // One player for the life of this composable, whichever frame it is in.
    val surface = remember(url) { VideoSurface.create(context, url) }
    DisposableEffect(surface) { onDispose { surface.release() } }

    // Fullscreen lives in the app-level overlay slot, so it fills the window
    // in landscape. Taken down with this composable too, so a card leaving
    // the deck cannot strand a fullscreen player.
    DisposableEffect(fullscreen, host) {
        // Captured now: by the time onDispose runs after a close, `fullscreen`
        // already reads false, and checking it there would never take the
        // overlay down.
        val shown = fullscreen && host != null
        if (shown) {
            host!!.show { FullscreenVideo(surface = surface, onClose = { fullscreen = false }) }
        }
        onDispose { if (shown) host?.hide() }
    }

    val frame = if (framed) {
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
    } else {
        modifier.fillMaxSize()
    }
    Box(frame.background(Color.Black)) {
        // While fullscreen the view lives in the dialog; the frame stays black.
        if (!fullscreen) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { surface.attach() })
            if (host != null) {
                FrameButton(
                    icon = Icons.Default.Fullscreen,
                    description = "Full screen",
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { fullscreen = true }
                )
            }
        }
    }
}

@Composable
private fun FrameButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(6.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
    ) {
        Icon(icon, contentDescription = description, tint = Color.White)
    }
}

/**
 * The whole window, no system bars, the same player re-parented into it.
 * Closing - the button or Back - puts the bars back. The app never rotates
 * (portrait in the manifest), so a landscape clip plays letterboxed.
 */
@Composable
private fun FullscreenVideo(surface: VideoSurface, onClose: () -> Unit) {
    val activity = LocalContext.current.findActivity()
    val hostView = LocalView.current
    BackHandler(onBack = onClose)

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, hostView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { surface.attach() })
        FrameButton(
            icon = Icons.Default.Close,
            description = "Exit full screen",
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            onClick = onClose
        )
    }
}

/**
 * The Android view that plays the video - a PlayerView over ExoPlayer -
 * created once and moved between frames.
 */
private class VideoSurface private constructor(
    private val view: PlayerView,
    private val player: ExoPlayer
) {
    /** Taller than wide, once the first frame is known; false until then. */
    val isPortrait: Boolean
        get() = player.videoSize.let { it.height > it.width && it.width > 0 }

    /** Returns the view, detached from wherever it was last shown. */
    fun attach(): View {
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    fun release() {
        (view.parent as? ViewGroup)?.removeView(view)
        view.player = null
        player.release()
    }

    companion object {
        fun create(context: Context, url: String): VideoSurface {
            val player = ExoPlayer.Builder(context).build().apply {
                // Declared as media with audio focus: music in another app
                // pauses when the story starts, and resumes when it ends,
                // and the volume keys drive the media stream.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    /* handleAudioFocus = */ true
                )
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                // The reader pressed play to get here.
                playWhenReady = true
            }
            // Inflated from XML because the surface type (TextureView, see
            // the layout's note) can only be chosen there.
            val view = (LayoutInflater.from(context)
                .inflate(R.layout.view_article_player, null) as PlayerView).apply {
                this.player = player
            }
            return VideoSurface(view, player)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * The video id from any of YouTube's link shapes, or null when [url] is not
 * a YouTube link at all:
 *
 *     https://www.youtube.com/watch?v=ID      https://youtu.be/ID
 *     https://www.youtube.com/shorts/ID       https://www.youtube.com/embed/ID
 *     https://m.youtube.com/watch?v=ID
 */
fun youtubeVideoId(url: String): String? {
    val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
    val id = when (host) {
        "youtu.be" -> uri.pathSegments.firstOrNull()
        "youtube.com", "youtube-nocookie.com" -> when (uri.pathSegments.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")
            "shorts", "embed", "live", "v" -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        else -> null
    }
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{6,}")) }
}
