//
// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
//

import Shared
import SwiftUI
import UIKit

@main
struct ArchiveTuneApp: App {
    /// One audio engine for the whole app lifetime (keeps playing in the background).
    private let engine = AudioEngineImpl()
    private let platform = PlatformServicesImpl()

    var body: some Scene {
        WindowGroup {
            ComposeView(engine: engine, platform: platform)
                .ignoresSafeArea(.all)
        }
    }
}

/// Hosts the shared Compose Multiplatform UI.
struct ComposeView: UIViewControllerRepresentable {
    let engine: AudioEngineImpl
    let platform: PlatformServicesImpl

    // Spelled out: the shared Kotlin framework exports its own `Context` model, which would
    // shadow SwiftUI's `Context` typealias here.
    func makeUIViewController(context: UIViewControllerRepresentableContext<ComposeView>) -> UIViewController {
        MainViewControllerKt.MainViewController(engine: engine, platform: platform)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: UIViewControllerRepresentableContext<ComposeView>) {}
}
