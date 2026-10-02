package com.jvoice.news.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Vertical page turn where **only the lower panel flips**.
 *
 * The image on top stays flat and simply crossfades to the next article, while the
 * story below is a hinged sheet: it is pinned along its top edge (the seam) and
 * rotates up through 180 degrees. Past halfway the sheet is showing its reverse, so
 * it swaps to the next story and corrects the rotation by 180 degrees - the next
 * article arrives on the back of the page you are turning, with a fold shadow that
 * peaks when the sheet is edge-on.
 *
 * Drag up to turn forward, drag down to come back. The turn follows the finger and
 * settles on release: to the far side if it is past a fifth of the way *or* was
 * flicked, otherwise back to where it was.
 *
 * ## Feel
 *
 * A full turn takes [TURN_DISTANCE] of the panel's height of finger travel, not
 * the whole height: the sheet is hinged at the seam, so the finger is moving
 * its free edge, and a page that needs a full-screen drag to turn feels stuck.
 * The release threshold and flick speed are low for the same reason - the
 * reader has already said which way they want to go; the app should agree
 * quickly.
 *
 * ## Keeping it at 60fps
 *
 * The drag offset is read only inside `graphicsLayer` and draw lambdas, never in
 * composition. Reading it in composition would recompose every leaf - three
 * stories and two images, each collecting its own flows - on every pointer
 * event, which is what made the first version of this stutter. The only
 * composition-level facts are which pages are on the front and back of the
 * sheet, and those change once per turn, not once per frame.
 */
@Composable
fun VerticalTwoPanelFlip(
    count: Int,
    modifier: Modifier = Modifier,
    topFraction: Float = 0.46f,
    /**
     * Changing this re-seats the flip on [pageOnReset] - a new deck. The
     * caller passes the index the reader's current story has in the new
     * deck, so a story arriving at the top does not shove the page they are
     * reading out from under them.
     */
    resetKey: Any? = null,
    pageOnReset: Int = 0,
    /**
     * The page that has come to rest on screen - on first show, after every
     * turn, and after a reset. This is the one signal to mark a story read
     * on, because it is the only one that means "the reader is looking at it".
     */
    onPageSettled: (Int) -> Unit = {},
    topPanel: @Composable (index: Int) -> Unit,
    bottomPanel: @Composable (index: Int) -> Unit
) {
    if (count <= 0) return

    var current by rememberSaveable { mutableIntStateOf(0) }
    if (current > count - 1) current = count - 1

    // -1f .. 1f. Positive turns the current page away, negative brings the previous
    // page back down.
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableIntStateOf(1) }

    LaunchedEffect(resetKey) {
        current = pageOnReset.coerceIn(0, count - 1)
        progress.snapTo(0f)
    }

    // Whatever page is showing, whenever that changes - including the first
    // composition and a reset - is reported once.
    LaunchedEffect(current, count) {
        if (current in 0 until count) onPageSettled(current)
    }

    // Which way the sheet is going, and whether it has passed edge-on. These are
    // the only values composition needs, and they are derived so the leaves
    // recompose when a boolean flips, not when the offset moves.
    val forward by remember { derivedStateOf { progress.value >= 0f } }
    val pastHalf by remember { derivedStateOf { abs(progress.value) >= 0.5f } }

    val dragState = rememberDraggableState { dragAmount ->
        val delta = -dragAmount / (heightPx * TURN_DISTANCE)
        val lower = if (current > 0) -1f else 0f
        val upper = if (current < count - 1) 1f else 0f
        // Main.immediate so the offset lands before this event's frame is
        // drawn; the default dispatcher would defer it by a frame.
        scope.launch(Dispatchers.Main.immediate) {
            progress.snapTo((progress.value + delta).coerceIn(lower, upper))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocityPx ->
                    // Velocity is in px/s, downwards positive; the offset is in
                    // page-heights, upwards positive.
                    val velocity = -velocityPx / (heightPx * TURN_DISTANCE)
                    val flick = abs(velocity) > FLICK_VELOCITY
                    val p = progress.value
                    val spec = spring<Float>(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                    when {
                        current < count - 1 &&
                            (p > SETTLE_THRESHOLD || (flick && velocity > 0f && p > 0f)) -> {
                            progress.animateTo(1f, spec, initialVelocity = velocity)
                            current += 1
                            progress.snapTo(0f)
                        }
                        current > 0 &&
                            (p < -SETTLE_THRESHOLD || (flick && velocity < 0f && p < 0f)) -> {
                            progress.animateTo(-1f, spec, initialVelocity = velocity)
                            current -= 1
                            progress.snapTo(0f)
                        }
                        else -> progress.animateTo(0f, spec, initialVelocity = velocity)
                    }
                }
            )
    ) {
        // Where the turn is heading. Null when there is nowhere to go.
        val targetIndex = when {
            forward && current < count - 1 -> current + 1
            !forward && current > 0 -> current - 1
            else -> null
        }

        val leafFront = if (forward) current else (current - 1).coerceAtLeast(0)
        val leafBack = if (forward) targetIndex else current
        // What sits under the turning sheet.
        val storyBeneath = if (forward) (targetIndex ?: current) else current
        // Going backwards the sheet starts fully turned and comes down, so it is
        // showing its reverse until halfway.
        val showingBack = if (forward) pastHalf else !pastHalf

        Column(Modifier.fillMaxSize()) {

            // ---------------------------------------------------- image: no flip
            // Stays flat and crossfades towards the article being turned to.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(topFraction)
            ) {
                if (targetIndex != null) {
                    topPanel(targetIndex)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - abs(progress.value) }
                ) {
                    topPanel(current)
                }
            }

            // ------------------------------------------------- story: the flip
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f - topFraction)
            ) {
                bottomPanel(storyBeneath)
                TurningLeaf(
                    // The sheet's own rotation: 0 flat, 1 fully turned.
                    turn = { val p = progress.value; if (p >= 0f) p else 1f - abs(p) },
                    showingBack = showingBack,
                    frontIndex = leafFront,
                    backIndex = leafBack,
                    content = bottomPanel
                )
            }
        }
    }
}

/** Finger travel for a full turn, as a fraction of the panel height. */
private const val TURN_DISTANCE = 0.45f

/** Past this much of a turn the release completes it. */
private const val SETTLE_THRESHOLD = 0.2f

/** Turns per second: a flick faster than this completes the turn regardless. */
private const val FLICK_VELOCITY = 0.8f

/**
 * The hinged sheet: shows its reverse past the halfway point.
 *
 * [turn] is a lambda, not a value, so it is read in the layer and draw passes
 * only - see the class note.
 */
@Composable
private fun TurningLeaf(
    turn: () -> Float,
    showingBack: Boolean,
    frontIndex: Int,
    backIndex: Int?,
    content: @Composable (index: Int) -> Unit
) {
    if (frontIndex < 0) return

    val visibleIndex = if (showingBack) backIndex else frontIndex
    if (visibleIndex == null || visibleIndex < 0) return

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val angle = -180f * turn().coerceIn(0f, 1f)
                cameraDistance = 26f * density
                transformOrigin = TransformOrigin(0.5f, 0f)
                rotationX = if (angle <= -90f) angle + 180f else angle
            }
            .drawWithContent {
                drawContent()
                // 0 flat, 1 edge-on - the fold shadow peaks as the sheet turns
                // through the seam.
                val angle = 180f * turn().coerceIn(0f, 1f)
                val fold = 1f - abs(angle - 90f) / 90f
                if (fold > 0.01f) {
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.50f * fold),
                            0.6f to Color.Black.copy(alpha = 0.20f * fold),
                            1f to Color.Black.copy(alpha = 0.04f * fold)
                        )
                    )
                }
            }
    ) {
        content(visibleIndex)
    }
}
