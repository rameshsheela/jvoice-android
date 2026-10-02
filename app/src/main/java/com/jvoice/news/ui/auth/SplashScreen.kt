package com.jvoice.news.ui.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jvoice.news.components.JVoiceLogo

/**
 * How long the splash stays up. Long enough for the logo to register as a
 * moment rather than a flicker; short enough that a returning reader is not
 * kept waiting for something they have seen before.
 */
const val SPLASH_DURATION_MS = 1600L

/** The launch ground - the same navy the system splash paints, so the two meet without a seam. */
val SplashNavy = Color(0xFF0D1F63)

/**
 * The launch splash: the full J Voice lockup on the brand navy.
 *
 * The system splash (Android 12+) cannot show the lockup - it masks its icon
 * to a small circle - so it is styled as a plain navy screen with no icon
 * and hands over to this the moment the app can draw. Same colour on both
 * sides, so what the reader sees is one navy screen on which the logo rises.
 *
 * Purely visual. It draws over whatever the app is doing underneath - the
 * desk session gate, the first Firestore snapshot, the feed composing - so by
 * the time it lifts there is usually already a page to land on.
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "splashAlpha"
    )
    // A touch of scale under the fade, so the mark settles into place rather
    // than simply appearing.
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 650),
        label = "splashScale"
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to SplashNavy,
                    0.55f to Color(0xFF122A80),
                    1f to Color(0xFF0A1547)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // A soft glow behind the mark, so it sits in light rather than on flat paint.
        Canvas(Modifier.fillMaxSize().alpha(alpha)) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color.White.copy(alpha = 0.16f),
                    0.6f to Color(0xFF2B4BC8).copy(alpha = 0.10f),
                    1f to Color.Transparent,
                    center = center,
                    radius = size.minDimension * 0.62f
                ),
                radius = size.minDimension * 0.62f,
                center = center
            )
        }

        JVoiceLogo(
            width = 250.dp,
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp)
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "తెలుగు వార్తలు  •  Telugu News",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "jvoicetelugu.com",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        }
    }
}
