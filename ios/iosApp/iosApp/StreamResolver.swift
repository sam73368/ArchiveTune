//
// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
//

import Foundation
import YouTubeKit

/// Turns a YouTube video id into a URL AVPlayer can stream.
///
/// Uses YouTubeKit (MIT, github.com/alexeichhorn/YouTubeKit): local extraction first (signature
/// deciphering through JavaScriptCore), then its remote fallback. Audio-only m4a is preferred,
/// like the Android app's audio-only playback.
enum StreamResolver {
    struct NoPlayableStream: LocalizedError {
        var errorDescription: String? { "Aucun flux audio lisible pour cette chanson" }
    }

    static func audioURL(videoId: String) async throws -> URL {
        let streams = try await YouTubeKit.YouTube(videoID: videoId, methods: [.local, .remote]).streams

        let m4aAudio: YouTubeKit.Stream? = streams
            .filterAudioOnly()
            .filter { $0.fileExtension == .m4a }
            .highestAudioBitrateStream()
        if let stream = m4aAudio {
            return stream.url
        }

        let playableAudio: YouTubeKit.Stream? = streams
            .filterAudioOnly()
            .filter { $0.isNativelyPlayable }
            .highestAudioBitrateStream()
        if let stream = playableAudio {
            return stream.url
        }

        let muxed: YouTubeKit.Stream? = streams
            .filterVideoAndAudio()
            .filter { $0.isNativelyPlayable }
            .highestResolutionStream()
        if let stream = muxed {
            return stream.url
        }

        throw NoPlayableStream()
    }
}
