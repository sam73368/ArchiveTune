//
// ArchiveTune (2026)
// © Rukamori — github.com/rukamori
// GPL-3.0 License | Contributors: see git history
// Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
//

import AVFoundation
import Foundation
import MediaPlayer
import Shared
import UIKit

/// iOS implementation of the shared `AudioEngine`: AVPlayer playback, background audio,
/// lock screen / Control Center metadata and remote commands. Queue logic lives in Kotlin
/// (`PlayerController`); this class only plays what it is told and reports events back.
final class AudioEngineImpl: NSObject, AudioEngine {
    private let player = AVPlayer()
    private var listener: AudioEngineListener?
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var itemStatusObservation: NSKeyValueObservation?
    private var timeControlObservation: NSKeyValueObservation?
    private var loadTask: Task<Void, Never>?
    private var loadGeneration = 0
    private var isResolving = false
    private var currentTrack: PlayerTrack?
    private var nowPlaying: [String: Any] = [:]
    private var artworkTask: URLSessionDataTask?

    override init() {
        super.init()
        configureAudioSession()
        configureRemoteCommands()
        player.automaticallyWaitsToMinimizeStalling = true
        timeObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.5, preferredTimescale: 600),
            queue: .main
        ) { [weak self] _ in
            self?.reportProgress()
        }
        timeControlObservation = player.observe(\.timeControlStatus, options: [.new]) { [weak self] _, _ in
            DispatchQueue.main.async { self?.reportState() }
        }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(handleInterruption(_:)),
            name: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance()
        )
        DispatchQueue.main.async {
            UIApplication.shared.beginReceivingRemoteControlEvents()
        }
    }

    // MARK: - AudioEngine

    func setListener(listener: AudioEngineListener) {
        self.listener = listener
    }

    func load(track: PlayerTrack, autoplay: Bool) {
        loadGeneration += 1
        let generation = loadGeneration
        loadTask?.cancel()
        currentTrack = track
        isResolving = true
        player.pause()
        player.replaceCurrentItem(with: nil)
        listener?.onPlaybackStateChanged(isPlaying: false, isBuffering: true)
        updateNowPlayingMetadata(track)

        let videoId = track.id
        loadTask = Task { [weak self] in
            do {
                let url = try await StreamResolver.audioURL(videoId: videoId)
                await MainActor.run {
                    guard let self, generation == self.loadGeneration else { return }
                    self.start(url: url, autoplay: autoplay)
                }
            } catch {
                await MainActor.run {
                    guard let self, generation == self.loadGeneration else { return }
                    self.isResolving = false
                    self.listener?.onError(message: error.localizedDescription)
                }
            }
        }
    }

    func play() {
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
    }

    func pause() {
        player.pause()
    }

    func seekTo(positionMs: Int64) {
        let time = CMTime(seconds: Double(positionMs) / 1000.0, preferredTimescale: 600)
        player.seek(to: time) { [weak self] _ in
            DispatchQueue.main.async { self?.updateNowPlayingPlayback() }
        }
    }

    func stop() {
        loadTask?.cancel()
        player.pause()
        player.replaceCurrentItem(with: nil)
        currentTrack = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    func setQueueNavigation(hasNext: Bool, hasPrevious: Bool) {
        let center = MPRemoteCommandCenter.shared()
        center.nextTrackCommand.isEnabled = hasNext
        // "Previous" also restarts the current track, so keep it available.
        center.previousTrackCommand.isEnabled = true
    }

    // MARK: - Playback

    private func start(url: URL, autoplay: Bool) {
        isResolving = false
        let item = AVPlayerItem(url: url)
        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.listener?.onTrackEnded()
        }
        itemStatusObservation = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard item.status == .failed else { return }
            let message = item.error?.localizedDescription ?? "Erreur de lecture"
            DispatchQueue.main.async { self?.listener?.onError(message: message) }
        }
        player.replaceCurrentItem(with: item)
        if autoplay {
            play()
        }
        reportState()
    }

    private func reportState() {
        let status = player.timeControlStatus
        let playing = status == .playing
        let buffering = isResolving || status == .waitingToPlayAtSpecifiedRate
        listener?.onPlaybackStateChanged(isPlaying: playing, isBuffering: buffering)
        updateNowPlayingPlayback()
    }

    private func reportProgress() {
        let position = player.currentTime().seconds
        let duration = player.currentItem?.duration.seconds ?? 0
        let positionMs = position.isFinite ? Int64(position * 1000) : 0
        let durationMs = duration.isFinite ? Int64(duration * 1000) : 0
        listener?.onProgress(positionMs: positionMs, durationMs: durationMs)
    }

    @objc private func handleInterruption(_ notification: Notification) {
        guard
            let info = notification.userInfo,
            let rawType = info[AVAudioSessionInterruptionTypeKey] as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: rawType)
        else { return }
        switch type {
        case .began:
            player.pause()
        case .ended:
            if let rawOptions = info[AVAudioSessionInterruptionOptionKey] as? UInt,
               AVAudioSession.InterruptionOptions(rawValue: rawOptions).contains(.shouldResume) {
                play()
            }
        @unknown default:
            break
        }
    }

    // MARK: - Audio session & remote commands

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default, policy: .longFormAudio, options: [])
        try? session.setActive(true)
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        center.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        center.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self else { return .commandFailed }
            if self.player.timeControlStatus == .playing { self.pause() } else { self.play() }
            return .success
        }
        center.nextTrackCommand.addTarget { [weak self] _ in
            self?.listener?.onRemoteNext()
            return .success
        }
        center.previousTrackCommand.addTarget { [weak self] _ in
            self?.listener?.onRemotePrevious()
            return .success
        }
        center.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seekTo(positionMs: Int64(event.positionTime * 1000))
            return .success
        }
    }

    // MARK: - Now playing

    private func updateNowPlayingMetadata(_ track: PlayerTrack) {
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: track.title,
            MPMediaItemPropertyArtist: track.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: 0.0,
            MPNowPlayingInfoPropertyPlaybackRate: 0.0,
        ]
        if let album = track.album {
            info[MPMediaItemPropertyAlbumTitle] = album
        }
        if track.durationMs > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = Double(track.durationMs) / 1000.0
        }
        nowPlaying = info
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        loadArtwork(for: track)
    }

    private func updateNowPlayingPlayback() {
        guard currentTrack != nil else { return }
        let position = player.currentTime().seconds
        if position.isFinite {
            nowPlaying[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position
        }
        if let duration = player.currentItem?.duration.seconds, duration.isFinite, duration > 0 {
            nowPlaying[MPMediaItemPropertyPlaybackDuration] = duration
        }
        nowPlaying[MPNowPlayingInfoPropertyPlaybackRate] = player.timeControlStatus == .playing ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nowPlaying
    }

    private func loadArtwork(for track: PlayerTrack) {
        artworkTask?.cancel()
        guard let urlString = track.artworkUrl, let url = URL(string: urlString) else { return }
        let trackId = track.id
        artworkTask = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data, let image = UIImage(data: data) else { return }
            DispatchQueue.main.async {
                guard let self, self.currentTrack?.id == trackId else { return }
                let artwork = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                self.nowPlaying[MPMediaItemPropertyArtwork] = artwork
                MPNowPlayingInfoCenter.default().nowPlayingInfo = self.nowPlaying
            }
        }
        artworkTask?.resume()
    }
}
