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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.zIndex

/**
 * Full-screen queue view for Android TV with reorder and remove support.
 *
 * Each row has 3 focus zones:
 * [Drag Handle] - [Track Info] - [Remove Button]
 *
 * Controls:
 * - Left/Right: Navigate between zones in a row.
 * - Up/Down: Navigate between rows.
 * - Center on Drag Handle: Enters "Drag Mode".
 *   - Up/Down in Drag Mode: Moves the item.
 *   - Center/Back in Drag Mode: Exits Drag Mode.
 * - Center on Track Info: Plays the track.
 * - Center on Remove Button: Removes the track.
 */
@Composable
fun TvQueueScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentQueue by playerConnection.queueWindows.collectAsState()
    val currentIndex = playerConnection.player.currentMediaItemIndex

    val listState = rememberLazyListState()

    // -1 means no item is currently being dragged
    var draggingIndex by remember { mutableIntStateOf(-1) }

    // Track which ROW currently has focus (to keep it visible)
    var focusedRowIndex by remember { mutableIntStateOf(currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))) }

    LaunchedEffect(focusedRowIndex) {
        listState.animateScrollToItem(focusedRowIndex)
    }

    // Scroll to current track on first load
    LaunchedEffect(Unit) {
        val target = currentIndex.coerceIn(0, (currentQueue.size - 1).coerceAtLeast(0))
        focusedRowIndex = target
        listState.scrollToItem(target)
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
                .padding(horizontal = 60.dp, vertical = 32.dp),
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
                    text = "Queue (${currentQueue.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (draggingIndex != -1) {
                    Spacer(Modifier.width(24.dp))
                    Text(
                        text = "Move mode: Press Up/Down to move, Center to drop",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.3f))

            LazyColumn(
                state = listState,
                // We handle key events manually for drag-and-drop
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
            ) {
                itemsIndexed(
                    items = currentQueue,
                    key = { _, window -> window.uid.hashCode() }
                ) { idx, window ->
                    val isCurrent = window.firstPeriodIndex == currentIndex
                    val isDragging = (draggingIndex == idx)
                    
                    TvQueueItem(
                        index = idx,
                        window = window,
                        isCurrent = isCurrent,
                        isDragging = isDragging,
                        totalItems = currentQueue.size,
                        onPlay = {
                            playerConnection.player.seekToDefaultPosition(idx)
                            navController.navigateUp()
                        },
                        onEnterDrag = { draggingIndex = idx },
                        onExitDrag = { draggingIndex = -1 },
                        onMove = { from, to ->
                            playerConnection.player.moveMediaItem(from, to)
                            // Update dragging index to follow the item
                            draggingIndex = to
                            focusedRowIndex = to
                        },
                        onRemove = {
                            playerConnection.player.removeMediaItem(idx)
                            // If we removed the item we were on, ensure focus stays valid
                            if (focusedRowIndex >= currentQueue.size - 1) {
                                focusedRowIndex = (currentQueue.size - 2).coerceAtLeast(0)
                            }
                        },
                        onFocused = { focusedRowIndex = idx }
                    )
                }
            }
        }
    }
}

@Composable
fun TvQueueItem(
    index: Int,
    window: androidx.media3.common.Timeline.Window,
    isCurrent: Boolean,
    isDragging: Boolean,
    totalItems: Int,
    onPlay: () -> Unit,
    onEnterDrag: () -> Unit,
    onExitDrag: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: () -> Unit,
    onFocused: () -> Unit,
) {
    val title = window.mediaItem.mediaMetadata.title?.toString() ?: ""
    val artist = window.mediaItem.mediaMetadata.artist?.toString() ?: ""
    val thumbnailUri = window.mediaItem.mediaMetadata.artworkUri

    // FocusRequesters for the 3 zones
    val dragFocus = remember { FocusRequester() }
    val contentFocus = remember { FocusRequester() }
    val removeFocus = remember { FocusRequester() }

    // If we are dragging, we force focus to the drag handle
    LaunchedEffect(isDragging) {
        if (isDragging) {
            dragFocus.requestFocus()
        }
    }

    // Row container
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                else if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
                else Color.Transparent
            )
            .padding(4.dp)
    ) {
        // 1. Drag Handle (Left)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isDragging) MaterialTheme.colorScheme.primary else Color.Transparent)
                .focusRequester(dragFocus)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isDragging) { contentFocus.requestFocus(); true } else true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isDragging) onExitDrag() else onEnterDrag()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isDragging) {
                                if (index > 0) onMove(index, index - 1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isDragging) {
                                if (index < totalItems - 1) onMove(index, index + 1)
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (isDragging) { onExitDrag(); true } else false
                        }
                        else -> false
                    }
                }
                .clickable { if (isDragging) onExitDrag() else onEnterDrag() }
        ) {
            Icon(
                painter = painterResource(R.drawable.drag_handle),
                contentDescription = "Reorder",
                tint = if (isDragging) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(8.dp))

        // 2. Content (Center) - Plays on click
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .focusRequester(contentFocus)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { dragFocus.requestFocus(); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { removeFocus.requestFocus(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onPlay(); true }
                        else -> false
                    }
                }
                .clickable { onPlay() }
                .padding(8.dp)
                .background(
                    // Visual feedback for focus state is handled by the system focus highlight usually,
                    // but we can add custom background if needed.
                    // For now, let generic Android TV focus highlight do its job on the clipped row.
                    Color.Transparent 
                )
        ) {
            // Index Number
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(32.dp)
            )
            
            // Thumbnail
            if (thumbnailUri != null) {
                AsyncImage(
                    model = thumbnailUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp))
                )
            } else {
                Box(Modifier.size(40.dp).background(Color.DarkGray, RoundedCornerShape(4.dp)))
            }
            
            Spacer(Modifier.width(12.dp))
            
            // Title & Artist
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (artist.isNotEmpty()) {
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))

        // 3. Remove Button (Right)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .focusRequester(removeFocus)
                .onFocusChanged { if (it.isFocused) onFocused() }
                .focusable()
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { contentFocus.requestFocus(); true }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { onRemove(); true }
                        else -> false
                    }
                }
                .clickable { onRemove() }
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
