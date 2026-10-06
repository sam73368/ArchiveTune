/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.coroutines.MainScope
import moe.rukamori.archivetune.shared.player.AudioEngine
import moe.rukamori.archivetune.shared.player.PlayerController
import platform.UIKit.UIViewController

/**
 * Entry point used by the Swift app: `MainViewControllerKt.MainViewController(engine: engine)`.
 * [engine] is the Swift AVPlayer-based implementation of [AudioEngine].
 */
fun MainViewController(engine: AudioEngine): UIViewController {
    val player = PlayerController(engine, MainScope())
    return ComposeUIViewController { App(player) }
}
