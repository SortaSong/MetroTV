/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R

/**
 * Full-screen queue view for Android TV.
 *
 * D-pad up/down moves focus (and scrolls) between tracks.
 * Center (or click) selects a track and starts playback, then navigates back.
 * Back returns to the player.
 */
@Composable
fun TvQueueScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentQueue by playerConnection.queueWindows.collectAsState()
    val currentIndex = playerConnection.player.currentMediaItemIndex

    val listState = rememberLazyListState()

    // Track which item currently has focus so we can scroll to it.
    var focusedIndex by remember { mutableIntStateOf(currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))) }

    // One FocusRequester per queue item.
    val focusRequesters = remember(currentQueue.size) {
        List(currentQueue.size) { FocusRequester() }
    }

    // On entry: scroll to current track and focus it.
    LaunchedEffect(currentIndex, currentQueue.size) {
        val target = currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
        focusedIndex = target
        listState.scrollToItem(target)
        if (target in focusRequesters.indices) {
            focusRequesters[target].requestFocus()
        }
    }

    // Whenever focusedIndex changes, scroll the list to keep it visible.
    LaunchedEffect(focusedIndex) {
        listState.animateScrollToItem(focusedIndex)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 80.dp, vertical = 48.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Queue  (${currentQueue.size} tracks)",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

            LazyColumn(
                state = listState,
                // Disable the LazyColumn''s own scroll-on-keypress so D-pad moves focus instead.
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            ) {
                itemsIndexed(currentQueue) { idx, window ->
                    val isCurrent = window.firstPeriodIndex == currentIndex
                    val isFocused = focusedIndex == idx
                    val title = window.mediaItem.mediaMetadata.title?.toString() ?: ""
                    val artist = window.mediaItem.mediaMetadata.artist?.toString() ?: ""
                    val thumbnailUri = window.mediaItem.mediaMetadata.artworkUri

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else -> Color.Transparent
                                }
                            )
                            .focusRequester(focusRequesters[idx])
                            .onFocusChanged { if (it.isFocused) focusedIndex = idx }
                            .focusable()
                            // D-pad key handling: up/down moves focus; center selects
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                                when (keyEvent.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        val prev = idx - 1
                                        if (prev >= 0) focusRequesters[prev].requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        val next = idx + 1
                                        if (next < focusRequesters.size) focusRequesters[next].requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        playerConnection.player.seekToDefaultPosition(idx)
                                        navController.navigateUp()
                                        true
                                    }
                                    else -> false
                                }
                            }
                            .clickable {
                                playerConnection.player.seekToDefaultPosition(idx)
                                navController.navigateUp()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            if (isCurrent) {
                                Icon(
                                    painter = painterResource(R.drawable.music_note),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(
                                    text = "${idx + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        if (thumbnailUri != null) {
                            AsyncImage(
                                model = thumbnailUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (artist.isNotEmpty()) {
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
