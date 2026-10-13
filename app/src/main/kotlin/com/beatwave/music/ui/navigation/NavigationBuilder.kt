/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.navigation

import com.beatwave.music.feature.stats.StatsScreen

import com.beatwave.music.feature.home.HomeScreen
import com.beatwave.music.feature.home.BrowseScreen
import com.beatwave.music.feature.home.ChartsScreen
import com.beatwave.music.feature.home.MoodAndGenresScreen
import com.beatwave.music.feature.home.NewReleaseScreen
import com.beatwave.music.feature.home.YouTubeBrowseScreen

import android.app.Activity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import com.beatwave.music.ui.utils.Motion
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.beatwave.music.constants.DarkModeKey
import com.beatwave.music.constants.PureBlackKey
import com.beatwave.music.ui.overlays.ambient.AmbientModeScreen
import com.beatwave.music.feature.player.AodScreen
import com.beatwave.music.feature.album.AlbumScreen
import com.beatwave.music.feature.artist.ArtistAlbumsScreen
import com.beatwave.music.feature.artist.ArtistItemsScreen
import com.beatwave.music.feature.artist.ArtistScreen
import com.beatwave.music.feature.artist.ArtistSongsScreen
import com.beatwave.music.feature.equalizer.EqScreen
import com.beatwave.music.feature.equalizer.axion.AxionEqScreen
import com.beatwave.music.feature.library.LibraryScreen
import com.beatwave.music.feature.history.HistoryScreen
import com.beatwave.music.feature.localmusic.LocalFolderScreen
import com.beatwave.music.feature.localmusic.LocalMusicScreen
import com.beatwave.music.feature.localmusic.LocalSongsScreen
import com.beatwave.music.feature.playlist.AutoPlaylistScreen
import com.beatwave.music.feature.playlist.CachePlaylistScreen
import com.beatwave.music.feature.playlist.LocalPlaylistScreen
import com.beatwave.music.feature.playlist.OnlinePlaylistScreen
import com.beatwave.music.feature.playlist.TopPlaylistScreen
import com.beatwave.music.feature.search.OnlineSearchResult
import com.beatwave.music.feature.search.SearchScreen
import com.beatwave.music.feature.settings.AboutScreen
import com.beatwave.music.feature.settings.AppearanceSettings
import com.beatwave.music.feature.settings.BackupAndRestore
import com.beatwave.music.feature.settings.CanvasSelection
import com.beatwave.music.feature.settings.ContentSettings
import com.beatwave.music.feature.settings.DarkMode
import com.beatwave.music.feature.settings.FontSelectionScreen
import com.beatwave.music.feature.settings.GlassEffectSettings
import com.beatwave.music.feature.settings.HomeFeedOrderScreen
import com.beatwave.music.feature.settings.LocalFoldersSettingsScreen

import com.beatwave.music.feature.settings.ModuleDetailScreen
import com.beatwave.music.feature.settings.ModuleSourceScreen
import com.beatwave.music.feature.settings.PlayerSettings
import com.beatwave.music.feature.settings.PlayerThemeScreen
import com.beatwave.music.feature.settings.PrivacySettings
import com.beatwave.music.feature.settings.RomanizationSettings
import com.beatwave.music.feature.settings.ScanMusicScreen
import com.beatwave.music.feature.settings.SettingsScreen
import com.beatwave.music.feature.settings.StandbySettings
import com.beatwave.music.feature.spotifyimport.SpotifyScreen
import com.beatwave.music.feature.youtubeimport.YouTubeImportScreen
import com.beatwave.music.feature.settings.StorageSettings
import com.beatwave.music.feature.settings.TrackUsageScreen
import com.beatwave.music.feature.settings.ThemeScreen
import com.beatwave.music.feature.settings.diy.DiyEditorScreen
import com.beatwave.music.feature.settings.diy.PlayerIconsScreen
import com.beatwave.music.feature.settings.diy.PresetsScreen
import com.beatwave.music.feature.settings.integrations.IntegrationScreen
import com.beatwave.music.feature.settings.integrations.LastFMSettings
import com.beatwave.music.feature.wrapped.WrappedScreen
import com.beatwave.music.ui.utils.LocalNavAnimatedVisibilityScope
import com.beatwave.music.ui.utils.hasMorphSource
import com.beatwave.music.ui.utils.morphArtworkId
import com.beatwave.music.ui.utils.morphContainer
import com.beatwave.music.utils.rememberEnumPreference
import com.beatwave.music.utils.rememberPreference
import com.beatwave.music.feature.spotifyimport.SpotifyImportViewModel
import com.beatwave.music.feature.settings.changelog.ChangelogScreen
import com.beatwave.music.feature.settings.commits.CommitScreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    activity: Activity,
    snackbarHostState: SnackbarHostState,
) {
    // Reverted from the pager (MainTabsPager.kt, still in the tree but unused): the
    // pager itself became the lag source it was meant to fix, so these are real
    // NavHost destinations again -- one AnimatedContent transition per switch (the
    // iOS parallax push set at the NavHost level in MainActivity), multi-back-stack
    // via popUpTo/saveState/restoreState in onNavItemClick, same as everything else
    // in this file.
    sharedComposable(Screens.Home.route) {
        HomeScreen(navController = navController, snackbarHostState = snackbarHostState)
    }

    // Only ever reachable as a tab while local-only mode is on (see Screens.mainScreens),
    // but the route is always registered: a saved back stack entry from a session that
    // had the mode on must still resolve after it is turned off.
    sharedComposable(Screens.Songs.route) {
        LocalSongsScreen(navController)
    }

    sharedComposable(Screens.Library.route) {
        LibraryScreen(navController)
    }

    sharedComposable(Screens.Settings.route) {
        SettingsScreen(navController, scrollBehavior)
    }

    sharedComposable("history") {
        HistoryScreen(navController)
    }

    sharedComposable("stats") {
        StatsScreen(navController)
    }

    sharedComposable("mood_and_genres") {
        MoodAndGenresScreen(navController, scrollBehavior)
    }

    sharedComposable("new_release") {
        NewReleaseScreen(navController, scrollBehavior)
    }

    sharedComposable("charts_screen") {
        ChartsScreen(navController)
    }

    sharedComposable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) {
        BrowseScreen(
            navController,
            scrollBehavior,
            it.arguments?.getString("browseId")
        )
    }

    composable(
        route = "search/{query}",
        arguments = listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
        val isSystemDark = isSystemInDarkTheme()
        val useDarkTheme = remember(darkTheme, isSystemDark) {
            if (darkTheme == DarkMode.AUTO) isSystemDark else darkTheme == DarkMode.ON
        }
        val pureBlackEnabled by rememberPreference(PureBlackKey, defaultValue = false)
        val pureBlack = remember(pureBlackEnabled, useDarkTheme) {
            pureBlackEnabled && useDarkTheme
        }
        OnlineSearchResult(navController, pureBlack = pureBlack)
    }

    sharedComposable(
        route = "album/{albumId}",
        arguments = listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        AlbumScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "artist/{artistId}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "artist/{artistId}/songs",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        ArtistSongsScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        ArtistAlbumsScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "artist/{artistId}/items?browseId={browseId}?params={params}",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        ArtistItemsScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "online_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        OnlinePlaylistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "local_playlist/{playlistId}",
        arguments = listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        LocalPlaylistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "local_music",
    ) {
        LocalMusicScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "local_folder/{path}",
        arguments = listOf(
            navArgument("path") {
                type = NavType.StringType
            },
        ),
    ) {
        LocalFolderScreen(navController, it.arguments?.getString("path").orEmpty())
    }

    sharedComposable(
        route = "auto_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        AutoPlaylistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "cache_playlist/{playlist}",
        arguments = listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        CachePlaylistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "top_playlist/{top}",
        arguments = listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        TopPlaylistScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        YouTubeBrowseScreen(navController)
    }

    sharedComposable("settings/appearance") {
        AppearanceSettings(navController, scrollBehavior, activity, snackbarHostState)
    }

    sharedComposable("settings/home_feed_order") {
        HomeFeedOrderScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/theme") {
        ThemeScreen(navController)
    }

    sharedComposable("settings/appearance/font") {
        FontSelectionScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/presets") {
        PresetsScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/playericons") {
        PlayerIconsScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/diy") {
        DiyEditorScreen(navController)
    }

    sharedComposable("settings/appearance/canvas") {
        CanvasSelection(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/playertheme") {
        PlayerThemeScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/appearance/liquidglass") {
        GlassEffectSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/content") {
        ContentSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/content/romanization") {
        RomanizationSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/player") {
        PlayerSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/standby") {
        StandbySettings(navController, scrollBehavior)
    }

    sharedComposable("standby") {
        AodScreen(
            onDismiss = { navController.navigateUp() }
        )
    }

    sharedComposable("settings/scan_music") {
        ScanMusicScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/player/local_folders") {
        LocalFoldersSettingsScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/storage") {
        StorageSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/track_usage") {
        TrackUsageScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/equalizer") {
        AxionEqScreen(onBackClick = { navController.navigateUp() })
    }

    sharedComposable("settings/privacy") {
        PrivacySettings(navController, scrollBehavior)
    }

    sharedComposable("settings/backup_restore") {
        BackupAndRestore(navController, scrollBehavior)
    }

    sharedComposable("settings/spotify") {
        SpotifyScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/youtube_import") {
        YouTubeImportScreen(navController, scrollBehavior)
    }



    sharedComposable("settings/integrations") {
        IntegrationScreen(navController, scrollBehavior)
    }

    sharedComposable("settings/modules") {
        ModuleSourceScreen(navController, scrollBehavior)
    }

    sharedComposable(
        route = "settings/modules/{moduleId}",
        arguments = listOf(
            navArgument("moduleId") {
                type = NavType.StringType
            },
        ),
    ) {
        ModuleDetailScreen(navController, scrollBehavior, it.arguments?.getString("moduleId") ?: "")
    }

    sharedComposable("settings/integrations/lastfm") {
        LastFMSettings(navController, scrollBehavior)
    }

    sharedComposable("settings/about") {
        AboutScreen(navController, scrollBehavior)
    }

    sharedComposable("wrapped") {
        WrappedScreen(navController)
    }

    sharedComposable("ambient_mode") {
        AmbientModeScreen(navController)
    }

    dialog("equalizer") {
        EqScreen(navController = navController)
    }

    sharedComposable("settings/changelog") {
        ChangelogScreen(navController,scrollBehavior)
    }
    sharedComposable("settings/commits") {
        CommitScreen(navController, scrollBehavior)
    }
}

/**
 * [composable] that also publishes the destination's own [AnimatedVisibilityScope].
 *
 * A shared element needs both ends to name the transition they are part of, and for a
 * NavHost destination that scope only exists inside the entry's content lambda. Rather
 * than repeat a CompositionLocalProvider in every screen, destinations that take part in
 * an artwork morph are declared with this instead of [composable].
 *
 * Applied only where there is actually a shared pair. A destination with no shared element
 * gains nothing from the local, and wrapping all sixty-odd of them would be churn.
 */

/**
 * A destination that grows out of the tile that opened it, and shrinks back into it.
 *
 * The whole screen is one shared element paired with the tapped tile's artwork box, so
 * the detail page reads as that tile opening rather than as a new page arriving over it.
 * See `Modifier.morphContainer` for the mechanics and for what the reference capture
 * actually does.
 *
 * The enter/exit specs here deliberately do almost nothing: the morph IS the transition,
 * and any slide or scale declared at this level would fight the shared element's own
 * interpolation of the same bounds. What is left is the treatment of the screen BEHIND
 * the morph -- it holds still and dims, because the tile the card is flying to and from
 * must not travel out from under it.
 *
 * Falls back to a plain cross-fade when the destination carries no id, or when its tile
 * was never on screen to be nominated (a deep link, a restored back stack): with no
 * source half, `morphContainer` is inert and this is all that is left.
 */
private fun NavGraphBuilder.sharedComposable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { fadeIn(tween(1)) },
    exitTransition = { fadeOut(tween(180), targetAlpha = 0.7f) },
    popEnterTransition = { fadeIn(tween(180), initialAlpha = 0.7f) },
    popExitTransition = { fadeOut(tween(1)) },
) { entry ->
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
        val morphId = entry.morphArtworkId()
        // `morphContainer` no-ops with no id (deep link, restored back stack), and
        // also finds no partner to interpolate from when the id's tile never called
        // `sharedArtworkSource` -- a bespoke card rendering its own AsyncImage instead
        // of going through one of Items.kt's shared composables. Both cases used to
        // fall all the way back to the bare fadeIn(1) above: an instant pop with no
        // motion at all. `animateEnterExit` is the generic, cheap fallback for both:
        // a soft grow-and-fade that costs nothing but a graphicsLayer scale (no
        // shared-element matching, no tile identity needed) -- which is the whole
        // point: every tile-opened screen gets SOME grow-in, not only the ones that
        // were wired into the shared-artwork system.
        //
        // Gated on `!hasMorphSource`, not id == null -- stacking this on TOP of an id
        // that IS driving a real container morph would scale the card twice
        // (morphContainer's own interpolation, then this fallback's 0.94 on top of
        // it), which reads as the card overshooting past its target and snapping back.
        val hasRealMorph = morphId != null && hasMorphSource(morphId)
        Box(
            Modifier
                .morphContainer(morphId, isSource = false)
                .then(
                    if (!hasRealMorph) {
                        Modifier.animateEnterExit(
                            // enter (push, never gesture-driven): spring is fine.
                            enter = fadeIn(Motion.appear()) + scaleIn(Motion.appear(), initialScale = 0.94f),
                            // exit (pop, predictive-back-scrubbed): tween, see appearExit().
                            exit = fadeOut(Motion.appearExit()) + scaleOut(Motion.appearExit(), targetScale = 0.94f),
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            content(entry)
        }
    }
}




