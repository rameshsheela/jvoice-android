package com.jvoice.news.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Vertical story cards, Inshorts style: the photo and the story move together
 * as one card. Swipe up and the card slides off the top while the next one
 * follows right behind it; release and it snaps to the nearest card.
 *
 * Built on [VerticalPager], so the drag, fling and snap are the platform's own
 * and feel like every other paged list on the phone.
 *
 * Each card is split into [topPanel] (the photo, [topFraction] of the height)
 * over [bottomPanel] (the story).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VerticalCardPager(
    count: Int,
    modifier: Modifier = Modifier,
    topFraction: Float = 0.46f,
    /**
     * Changing this re-seats the pager on [pageOnReset] - a new deck. The
     * caller passes the index the reader's current story has in the new
     * deck, so a story arriving at the top does not shove the page they are
     * reading out from under them.
     */
    resetKey: Any? = null,
    pageOnReset: Int = 0,
    /**
     * The card that has come to rest on screen - on first show, after every
     * swipe, and after a reset. This is the one signal to mark a story read
     * on, because it is the only one that means "the reader is looking at it".
     */
    onPageSettled: (Int) -> Unit = {},
    topPanel: @Composable (index: Int) -> Unit,
    bottomPanel: @Composable (index: Int) -> Unit
) {
    if (count <= 0) return

    val pager = rememberPagerState(pageCount = { count })
    val settled by rememberUpdatedState(onPageSettled)

    // Re-seat on a new deck, then report each card the pager comes to rest on.
    // Collecting only after the jump means a reset reports where the reader
    // landed, not the card it jumped away from.
    LaunchedEffect(resetKey) {
        pager.scrollToPage(pageOnReset.coerceIn(0, count - 1))
        snapshotFlow { pager.settledPage }
            .distinctUntilChanged()
            .collect { page -> if (page in 0 until pager.pageCount) settled(page) }
    }

    VerticalPager(
        state = pager,
        modifier = modifier.fillMaxSize(),
        // The next card is composed before the swipe starts, so its photo is
        // already loading when it slides into view.
        beyondBoundsPageCount = 1
    ) { page ->
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(topFraction)
            ) { topPanel(page) }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f - topFraction)
            ) { bottomPanel(page) }
        }
    }
}
