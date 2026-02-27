/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.tv

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.extensions.toggleRepeatMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen now-playing view optimised for Android TV.
 *
 * Layout: album art (left 45%) | track info + controls (right 55%)
 *
 * D-pad mapping:
 *  Center        -> Play / Pause
 *  Left / Right  -> Seek -/+10 s immediately; hold 5 s to skip to prev/next
 *  Up (single)   -> Navigate to queue screen
 *  Up (double)   -> Like / unlike
 *  Down (single) -> Toggle shuffle
 *  Down (double) -> Cycle repeat mode
 *  Back          -> Navigate up (minimize player)
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

    // ── Hold-to-skip: arc progress for prev (left) and next (right) ────────────
    val holdSkipDurationMs = 5000L
    var holdLeftProgress by remember { mutableFloatStateOf(0f) }
    var holdRightProgress by remember { mutableFloatStateOf(0f) }
    var holdLeftJob: Job? by remember { mutableStateOf(null) }
    var holdRightJob: Job? by remember { mutableStateOf(null) }
    val scope = rememberCoroutineScope()

    // ── Delayed single vs double tap for Up / Down ─────────────────────────────
    val doubleTapWindowMs = 450L
    var pendingUpJob: Job? by remember { mutableStateOf(null) }
    var pendingDownJob: Job? by remember { mutableStateOf(null) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                val action = keyEvent.nativeKeyEvent.action
                val code = keyEvent.nativeKeyEvent.keyCode
                val isDown = action == KeyEvent.ACTION_DOWN
                val isUp = action == KeyEvent.ACTION_UP
                val isFirst = keyEvent.nativeKeyEvent.repeatCount == 0

                when {
                    // ── Play/Pause ────────────────────────────────────────────
                    isDown && isFirst && (code == KeyEvent.KEYCODE_DPAD_CENTER ||
                        code == KeyEvent.KEYCODE_ENTER ||
                        code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) -> {
                        playerConnection.player.togglePlayPause()
                        true
                    }

                    // ── Left: seek -10s immediately on first press, hold 5s to skip prev ──
                    isDown && isFirst && (code == KeyEvent.KEYCODE_DPAD_LEFT ||
                        code == KeyEvent.KEYCODE_MEDIA_REWIND) -> {
                        // Immediate seek
                        playerConnection.player.seekTo(
                            (playerConnection.player.currentPosition - 10_000L).coerceAtLeast(0L)
                        )
                        // Start hold timer for skip
                        holdLeftJob?.cancel()
                        holdLeftProgress = 0f
                        holdLeftJob = scope.launch {
                            val steps = 50
                            val stepMs = holdSkipDurationMs / steps
                            for (i in 1..steps) {
                                delay(stepMs)
                                holdLeftProgress = i.toFloat() / steps
                            }
                            playerConnection.player.seekToPreviousMediaItem()
                            holdLeftProgress = 0f
                        }
                        true
                    }
                    isUp && (code == KeyEvent.KEYCODE_DPAD_LEFT ||
                        code == KeyEvent.KEYCODE_MEDIA_REWIND) -> {
                        holdLeftJob?.cancel()
                        holdLeftJob = null
                        holdLeftProgress = 0f
                        true
                    }

                    // ── Right: seek +10s immediately on first press, hold 5s to skip next ─
                    isDown && isFirst && (code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
                        playerConnection.player.seekTo(
                            (playerConnection.player.currentPosition + 10_000L).coerceAtMost(durationMs)
                        )
                        holdRightJob?.cancel()
                        holdRightProgress = 0f
                        holdRightJob = scope.launch {
                            val steps = 50
                            val stepMs = holdSkipDurationMs / steps
                            for (i in 1..steps) {
                                delay(stepMs)
                                holdRightProgress = i.toFloat() / steps
                            }
                            playerConnection.player.seekToNextMediaItem()
                            holdRightProgress = 0f
                        }
                        true
                    }
                    isUp && (code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) -> {
                        holdRightJob?.cancel()
                        holdRightJob = null
                        holdRightProgress = 0f
                        true
                    }

                    // ── Up: single = queue screen, double = like ───────────────
                    isDown && isFirst && code == KeyEvent.KEYCODE_DPAD_UP -> {
                        if (pendingUpJob?.isActive == true) {
                            pendingUpJob?.cancel()
                            pendingUpJob = null
                            playerConnection.toggleLike()
                        } else {
                            pendingUpJob = scope.launch {
                                delay(doubleTapWindowMs)
                                navController.navigate("tv_queue") { launchSingleTop = true }
                                pendingUpJob = null
                            }
                        }
                        true
                    }

                    // ── Down: single = shuffle, double = repeat ────────────────
                    isDown && isFirst && code == KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (pendingDownJob?.isActive == true) {
                            pendingDownJob?.cancel()
                            pendingDownJob = null
                            playerConnection.player.toggleRepeatMode()
                        } else {
                            pendingDownJob = scope.launch {
                                delay(doubleTapWindowMs)
                                playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                pendingDownJob = null
                            }
                        }
                        true
                    }

                    else -> false
                }
            },
    ) {
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(40.dp),
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))

        Row(
            modifier = Modifier.fillMaxSize().padding(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Album art ─────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxHeight().weight(0.45f),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = mediaMetadata?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxHeight(0.8f).clip(RoundedCornerShape(16.dp)),
                )
            }

            Spacer(Modifier.size(40.dp))

            // ── Track info + controls ─────────────────────────────────────────
            Column(
                modifier = Modifier.weight(0.55f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = mediaMetadata?.title ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mediaMetadata?.artists?.joinToString { it.name } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                mediaMetadata?.album?.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(32.dp))

                Slider(
                    value = progress,
                    onValueChange = { playerConnection.player.seekTo((it * durationMs).toLong()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(positionMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIconButton(
                        onClick = { playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled },
                        iconRes = if (shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle,
                        tint = if (shuffleModeEnabled) MaterialTheme.colorScheme.primary else Color.White,
                    )
                    HoldProgressButton(
                        iconRes = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        holdProgress = holdLeftProgress,
                        size = 56.dp,
                    )
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
                    HoldProgressButton(
                        iconRes = R.drawable.skip_next,
                        enabled = canSkipNext,
                        holdProgress = holdRightProgress,
                        size = 56.dp,
                    )
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

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    val isLiked = currentSong?.song?.liked == true
                    TvIconButton(
                        onClick = { playerConnection.toggleLike() },
                        iconRes = if (isLiked) R.drawable.favorite else R.drawable.favorite_border,
                        tint = if (isLiked) MaterialTheme.colorScheme.error else Color.White,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "▲ Queue  ▲▲ Like  ▼ Shuffle  ▼▼ Repeat  ◄► Seek / Hold 5s Skip",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
        }
    }
}

@Composable
private fun HoldProgressButton(
    iconRes: Int,
    holdProgress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
) {
    val arcColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        if (holdProgress > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = 360f * holdProgress,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.6f),
        )
    }
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    iconRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Color.White,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(size).focusable(),
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
