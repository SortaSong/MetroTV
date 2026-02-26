/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.tv

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil3.compose.AsyncImage
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.ui.component.AppNavigationRail
import com.metrolist.music.ui.screens.settings.NavigationTab
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.navigationBuilder

/**
 * Root TV layout: permanent left NavigationRail + content area with NavHost and bottom now-playing bar.
 * Only rendered when the device is an Android TV (detected via [isAndroidTv]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvMainScreen(
    navController: NavHostController,
    navigationItems: List<Screens>,
    pureBlack: Boolean,
    defaultOpenTab: NavigationTab,
    tabOpenedFromShortcut: NavigationTab?,
    latestVersionName: String,
    snackbarHostState: SnackbarHostState,
) {
    val activity = LocalContext.current as Activity
    val currentBackStack by navController.currentBackStack.collectAsState()
    val currentRoute = currentBackStack.lastOrNull()?.destination?.route

    // Shared nav item click handler
    val onNavItemClick: (Screens, Boolean) -> Unit = remember(navController) {
        { screen, isSelected ->
            if (!isSelected) {
                navController.navigate(screen.route) {
                    popUpTo(navController.graph.startDestinationId) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Left navigation rail ──────────────────────────────────────────────
        AppNavigationRail(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onItemClick = onNavItemClick,
            pureBlack = pureBlack,
        )

        // ── Main content area ─────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            // NavHost fills remaining space above the now-playing bar
            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = when (tabOpenedFromShortcut ?: defaultOpenTab) {
                        NavigationTab.HOME -> Screens.Home
                        NavigationTab.LIBRARY -> Screens.Library
                        else -> Screens.Home
                    }.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(200)) },
                    popExitTransition = { fadeOut(tween(200)) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // All existing mobile routes (settings, search, library, etc.)
                    navigationBuilder(
                        navController = navController,
                        scrollBehavior = scrollBehavior,
                        latestVersionName = latestVersionName,
                        activity = activity,
                        snackbarHostState = snackbarHostState,
                    )

                    // TV-only full-screen player route
                    composable("tv_player") {
                        TvPlayerScreen(navController = navController)
                    }
                }
            }

            // ── Now-playing bar at bottom of content area ─────────────────────
            TvNowPlayingBar(
                onOpenPlayer = {
                    navController.navigate("tv_player") {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}

/**
 * Slim bottom bar showing the currently playing track with basic transport controls.
 * Navigates to the full-screen [TvPlayerScreen] when the user presses the "open" button.
 */
@Composable
private fun TvNowPlayingBar(
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    // Hide the bar when nothing is loaded
    if (mediaMetadata == null) return

    val bg = MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(bg)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Album art thumbnail
        AsyncImage(
            model = mediaMetadata?.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(6.dp)),
        )

        Spacer(Modifier.width(12.dp))

        // Track title + artist
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata?.title ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            mediaMetadata?.artists?.joinToString { it.name }?.let { artists ->
                Text(
                    text = artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Transport controls
        IconButton(
            onClick = { playerConnection.player.seekToPreviousMediaItem() },
            enabled = canSkipPrevious,
            modifier = Modifier.focusable(),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = null,
            )
        }

        IconButton(
            onClick = { playerConnection.player.togglePlayPause() },
            modifier = Modifier.focusable(),
        ) {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                contentDescription = null,
            )
        }

        IconButton(
            onClick = { playerConnection.player.seekToNextMediaItem() },
            enabled = canSkipNext,
            modifier = Modifier.focusable(),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = null,
            )
        }

        // Open full-screen player
        IconButton(
            onClick = onOpenPlayer,
            modifier = Modifier.focusable(),
        ) {
            Icon(
                painter = painterResource(R.drawable.expand_less),
                contentDescription = null,
            )
        }
    }
}
