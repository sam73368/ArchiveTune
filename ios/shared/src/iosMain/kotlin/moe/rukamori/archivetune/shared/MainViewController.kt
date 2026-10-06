/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import moe.rukamori.archivetune.shared.platform.PlatformServices
import moe.rukamori.archivetune.shared.player.AudioEngine
import moe.rukamori.archivetune.shared.player.PlayerController
import platform.UIKit.UIViewController

/**
 * Entry point used by the Swift app:
 * `MainViewControllerKt.MainViewController(engine: engine, platform: platform)`.
 * [engine] is the Swift AVPlayer-based [AudioEngine]; [platform] the Swift [PlatformServices].
 */
fun MainViewController(
    engine: AudioEngine,
    platform: PlatformServices,
): UIViewController {
    val player = PlayerController(engine, MainScope())
    return ComposeUIViewController { App(player, platform) }
}
