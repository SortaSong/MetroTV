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
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil3.compose.AsyncImage
import com.metrolist.innertube.utils.parseCookieString
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AccountChannelHandleKey
import com.metrolist.music.constants.AccountEmailKey
import com.metrolist.music.constants.AccountNameKey
import com.metrolist.music.constants.DataSyncIdKey
import com.metrolist.music.constants.InnerTubeCookieKey
import com.metrolist.music.constants.VisitorDataKey
import com.metrolist.music.constants.YtmSyncKey
import com.metrolist.music.extensions.togglePlayPause
import com.metrolist.music.ui.screens.settings.NavigationTab
import com.metrolist.music.ui.screens.Screens
import com.metrolist.music.ui.screens.navigationBuilder
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AccountSettingsViewModel
import com.metrolist.music.viewmodels.HomeViewModel
import androidx.compose.foundation.layout.RowScope
import kotlinx.coroutines.launch

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
        TvNavigationRail(
            navigationItems = navigationItems,
            currentRoute = currentRoute,
            onNavItemClick = onNavItemClick,
            pureBlack = pureBlack,
            onSettingsClick = {
                navController.navigate("tv_settings") { launchSingleTop = true }
            },
            onPlayerClick = {
                navController.navigate("tv_player") { launchSingleTop = true }
            },
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

                    // TV-only queue screen
                    composable("tv_queue") {
                        TvQueueScreen(navController = navController)
                    }

                    // TV account/settings panel — TV-native focus-aware replacement for AccountSettings.
                    // AccountSettings uses Modifier.clickable in a verticalScroll Column; on TV with
                    // D-pad the focus stays in the NavigationRail and clicks never reach those items.
                    // This composable has explicit FocusRequesters per item and auto-focuses on entry,
                    // then navigates to the EXACT same routes ("login", "settings", "settings/integrations")
                    // that AccountSettings navigates to.
                    composable("tv_settings") {
                        TvAccountMenu(navController = navController, latestVersionName = latestVersionName)
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

/**
 * TV-specific NavigationRail that adds Settings and Now-Playing items
 * below the standard navigation items.
 */
@Composable
private fun TvNavigationRail(
    navigationItems: List<Screens>,
    currentRoute: String?,
    onNavItemClick: (Screens, Boolean) -> Unit,
    pureBlack: Boolean,
    onSettingsClick: () -> Unit,
    onPlayerClick: () -> Unit,
) {
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by (playerConnection?.isPlaying?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(false) })
    val mediaMetadata by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { androidx.compose.runtime.mutableStateOf(null) })

    val containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainer
    val isSettingsSelected = currentRoute == "tv_settings" ||
        currentRoute?.startsWith("settings") == true ||
        currentRoute?.startsWith("account") == true
    val isPlayerSelected = currentRoute == "tv_player"

    NavigationRail(containerColor = containerColor) {
        Spacer(Modifier.weight(1f))

        // Standard nav items (Home, Search, Library, …)
        navigationItems.forEach { screen ->
            val isSelected = currentRoute == screen.route || currentRoute?.startsWith("${screen.route}/") == true
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavItemClick(screen, isSelected) },
                icon = {
                    Icon(
                        painter = painterResource(if (isSelected) screen.iconIdActive else screen.iconIdInactive),
                        contentDescription = stringResource(screen.titleId),
                    )
                },
                label = { Text(stringResource(screen.titleId), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        Spacer(Modifier.weight(1f))

        // Now-playing item — visible when something is loaded
        if (mediaMetadata != null) {
            NavigationRailItem(
                selected = isPlayerSelected,
                onClick = onPlayerClick,
                icon = {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = stringResource(R.string.queue),
                    )
                },
                label = { Text(stringResource(R.string.queue), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }

        // Account/Settings item always visible at the bottom
        NavigationRailItem(
            selected = isSettingsSelected,
            onClick = onSettingsClick,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = stringResource(R.string.settings),
                )
            },
            label = { Text(stringResource(R.string.settings), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
    }
}

/**
 * TV-native account/settings menu shown when the user opens the Settings rail item.
 *
 * Replaces the mobile AccountSettings composable for TV because AccountSettings uses
 * Modifier.clickable inside a verticalScroll Column — on TV with D-pad, focus stays in
 * the NavigationRail and those clickable items never receive a click event.
 *
 * This composable uses explicit FocusRequesters and auto-focuses the first item on entry,
 * so D-pad center always reaches the correct item. Navigation targets are the EXACT SAME
 * routes as AccountSettings uses: "login", "account", "settings", "settings/integrations".
 */
@Composable
private fun TvAccountMenu(navController: NavController, latestVersionName: String) {
    val (innerTubeCookie, onInnerTubeCookieChange) = rememberPreference(InnerTubeCookieKey, "")
    val (ytmSync, onYtmSyncChange) = rememberPreference(YtmSyncKey, true)

    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()
    val accountName by homeViewModel.accountName.collectAsState()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // One FocusRequester per menu item: 0=login/account, 1=sync(if logged in), 2=integrations, 3=settings
    val focusRequesters = remember { List(4) { FocusRequester() } }
    var focusedIndex by remember { mutableIntStateOf(0) }

    // Auto-focus the first item as soon as the screen appears
    LaunchedEffect(Unit) {
        focusRequesters[0].requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .padding(horizontal = 80.dp, vertical = 48.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Login / Account row ────────────────────────────────────────────
            TvMenuRow(
                focusRequester = focusRequesters[0],
                isFocused = focusedIndex == 0,
                onFocused = { focusedIndex = 0 },
                onClick = {
                    if (isLoggedIn) navController.navigate("account")
                    else navController.navigate("login")
                },
            ) {
                if (isLoggedIn && accountImageUrl != null) {
                    AsyncImage(
                        model = accountImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                    Spacer(Modifier.width(16.dp))
                } else {
                    Icon(
                        painter = painterResource(R.drawable.login),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isLoggedIn) (accountName.ifEmpty { stringResource(R.string.account) })
                               else stringResource(R.string.login),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (!isLoggedIn) {
                        Text(
                            text = stringResource(R.string.login),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isLoggedIn) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            scope.launch {
                                accountSettingsViewModel.logoutKeepData(context, onInnerTubeCookieChange)
                            }
                        }
                    ) {
                        Text(stringResource(R.string.action_logout))
                    }
                }
            }

            // ── Sync row (only when logged in) ────────────────────────────────
            if (isLoggedIn) {
                Spacer(Modifier.height(8.dp))
                TvMenuRow(
                    focusRequester = focusRequesters[1],
                    isFocused = focusedIndex == 1,
                    onFocused = { focusedIndex = 1 },
                    onClick = { onYtmSyncChange(!ytmSync) },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.cached),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.yt_sync),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = ytmSync, onCheckedChange = onYtmSyncChange)
                }
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // ── Integrations row ──────────────────────────────────────────────
            TvMenuRow(
                focusRequester = focusRequesters[2],
                isFocused = focusedIndex == 2,
                onFocused = { focusedIndex = 2 },
                onClick = { navController.navigate("settings/integrations") },
            ) {
                Icon(
                    painter = painterResource(R.drawable.integration),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.integrations),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // ── Settings row ──────────────────────────────────────────────────
            TvMenuRow(
                focusRequester = focusRequesters[3],
                isFocused = focusedIndex == 3,
                onFocused = { focusedIndex = 3 },
                onClick = { navController.navigate("settings") },
            ) {
                Icon(
                    painter = painterResource(R.drawable.settings),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(R.drawable.navigate_next),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Single focusable row in the TV account menu. Handles focus highlight and click. */
@Composable
private fun TvMenuRow(
    focusRequester: FocusRequester,
    isFocused: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceContainer,
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        content = content,
    )
}
