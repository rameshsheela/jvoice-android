package com.jvoice.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * A slot at the very top of the app's window that any screen can fill with
 * a full-window overlay - the fullscreen video player uses it.
 *
 * Why not a `Dialog`: a dialog is its own window, sized when it opens. The
 * fullscreen player turns the phone to landscape, and the dialog's window
 * kept its portrait size while the screen beneath it rotated - a black
 * column down the middle. The app's own window re-lays out with the
 * rotation, so an overlay drawn in it fills the screen in either
 * orientation.
 *
 * One overlay at a time; showing a second replaces the first.
 */
class FullscreenHost {
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(content: @Composable () -> Unit) {
        this.content = content
    }

    fun hide() {
        content = null
    }
}

val LocalFullscreenHost = staticCompositionLocalOf<FullscreenHost?> { null }

/** Draws the host's overlay, if any, over [Box]-sized content. Place at the root. */
@Composable
fun FullscreenHostOverlay(host: FullscreenHost) {
    host.content?.let { overlay ->
        Box(Modifier.fillMaxSize()) { overlay() }
    }
}
