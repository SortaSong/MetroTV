/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.extensions.toggleRepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Full-screen now-playing view optimised for Android TV.
 *
 * Layout: album art (left 45%) | track info + controls (right 55%)
 * All interactive elements are [Modifier.focusable] so D-pad navigation works out of the box.
 */
@Composable
fun TvPlayerScreen(navController: NavController) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)

    // Position tracking (updated every second)
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(1L) }

    LaunchedEffect(playerConnection) {
        while (isActive) {
            positionMs = playerConnection.player.currentPosition
            durationMs = playerConnection.player.duration.coerceAtLeast(1L)
            delay(1000L)
        }
    }

    val progress by animateFloatAsState(
        targetValue = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
        label = "progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Blurred album art background
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp),
        )
        // Dark scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Left: Album art ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.45f),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight(0.8f)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            Spacer(Modifier.size(40.dp))

            // ── Right: Track info + controls ──────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                // Title
                Text(
                    text = mediaMetadata?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))

                // Artists
                Text(
                    text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Album
                mediaMetadata?.album?.title?.let { album ->
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(32.dp))

                // Progress bar
                Slider(
                    value = progress,
                    onValueChange = { fraction ->
                        playerConnection.player.seekTo((fraction * durationMs).toLong())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusable(),
                )

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatMs(positionMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                    Text(
                        text = formatMs(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Main transport controls: shuffle | prev | play-pause | next | repeat
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Shuffle
                    TvIconButton(
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                        iconRes = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                        tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                    )

                    // Previous
                    TvIconButton(
                        onClick = { playerConnection.player.seekToPreviousMediaItem() },
                        iconRes = R.drawable.skip_previous,
                        size = 56.dp,
                        enabled = canSkipPrevious,
                    )

                    // Play / Pause (larger button)
                    IconButton(
                        onClick = { playerConnection.player.togglePlayPause() },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .focusable(),
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }

                    // Next
                    TvIconButton(
                        onClick = { playerConnection.player.seekToNextMediaItem() },
                        iconRes = R.drawable.skip_next,
                        size = 56.dp,
                        enabled = canSkipNext,
                    )

                    // Repeat
                    TvIconButton(
                        onClick = { playerConnection.player.toggleRepeatMode() },
                        iconRes = when (repeatMode) {
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> R.drawable.repeat
                        },
                        tint = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Color.White
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Secondary: Like + navigate back
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isLiked = currentSong?.song?.liked == true
                    TvIconButton(
                        onClick = { playerConnection.toggleLike() },
                        iconRes = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                        tint = if (isLiked) MaterialTheme.colorScheme.error else Color.White,
                    )

                    TvIconButton(
                        onClick = { navController.navigateUp() },
                        iconRes = R.drawable.expand_more,
                    )
                }
            }
        }
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = Color.White,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .focusable(),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSecs = ms / 1000
    val minutes = totalSecs / 60
    val seconds = totalSecs % 60
    return "%d:%02d".format(minutes, seconds)
}
