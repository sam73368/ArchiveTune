/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import moe.rukamori.archivetune.shared.player.PlayerController
import moe.rukamori.archivetune.shared.ui.AppIcons
import moe.rukamori.archivetune.shared.ui.ArchiveTunePink
import moe.rukamori.archivetune.shared.ui.Navigator
import moe.rukamori.archivetune.shared.ui.Screen
import moe.rukamori.archivetune.shared.ui.ScreenHeader
import moe.rukamori.archivetune.shared.ui.Tab
import moe.rukamori.archivetune.shared.ui.player.FullPlayer
import moe.rukamori.archivetune.shared.ui.player.MiniPlayer
import moe.rukamori.archivetune.shared.ui.screens.AlbumScreen
import moe.rukamori.archivetune.shared.ui.screens.ArtistItemsScreen
import moe.rukamori.archivetune.shared.ui.screens.ArtistScreen
import moe.rukamori.archivetune.shared.ui.screens.HomeScreen
import moe.rukamori.archivetune.shared.ui.screens.PlaylistScreen
import moe.rukamori.archivetune.shared.ui.screens.SearchScreen

private val ArchiveTuneColors =
    darkColorScheme(
        primary = ArchiveTunePink,
        onPrimary = Color(0xFF5E1133),
        primaryContainer = Color(0xFF7B2949),
        secondary = Color(0xFFE2BDC7),
        background = Color(0xFF141218),
        surface = Color(0xFF141218),
        surfaceVariant = Color(0xFF2B2930),
        surfaceContainerHigh = Color(0xFF2B2930),
    )

@Composable
fun App(player: PlayerController) {
    setSingletonImageLoaderFactory { context ->
        ImageLoader
            .Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }
    MaterialTheme(colorScheme = ArchiveTuneColors) {
        val navigator = remember { Navigator() }
        var playerExpanded by remember { mutableStateOf(false) }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
                    ) {
                        val padding = PaddingValues(bottom = 8.dp)
                        when (val screen = navigator.current) {
                            is Screen.Album -> AlbumScreen(screen, navigator, player, padding)
                            is Screen.Artist -> ArtistScreen(screen, navigator, player, padding)
                            is Screen.Playlist -> PlaylistScreen(screen, navigator, player, padding)
                            is Screen.ArtistItems -> ArtistItemsScreen(screen, navigator, player, padding)
                            null ->
                                when (navigator.tab.value) {
                                    Tab.HOME -> HomeScreen(navigator, player, padding)
                                    Tab.SEARCH -> SearchScreen(navigator, player, padding)
                                    Tab.LIBRARY -> LibraryPlaceholder()
                                }
                        }
                    }
                    MiniPlayer(player = player, onExpand = { playerExpanded = true })
                    NavigationBar {
                        TabItem(navigator, Tab.HOME, "Accueil") { Icon(AppIcons.Home, contentDescription = null) }
                        TabItem(navigator, Tab.SEARCH, "Recherche") { Icon(AppIcons.Search, contentDescription = null) }
                        TabItem(navigator, Tab.LIBRARY, "Bibliothèque") { Icon(AppIcons.Library, contentDescription = null) }
                    }
                }

                AnimatedVisibility(
                    visible = playerExpanded,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it }),
                ) {
                    FullPlayer(player = player, onCollapse = { playerExpanded = false })
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(
    navigator: Navigator,
    tab: Tab,
    label: String,
    icon: @Composable () -> Unit,
) {
    NavigationBarItem(
        selected = navigator.tab.value == tab,
        onClick = { navigator.selectTab(tab) },
        icon = icon,
        label = { Text(label) },
    )
}

@Composable
private fun LibraryPlaceholder() {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Bibliothèque", onBack = null)
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Bientôt : tes favoris, playlists, historique, la connexion à ton compte YouTube Music et l'import des sauvegardes Android.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
