package moe.archivetune.server

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.simpstream.SimpMusicPlayer
import java.util.concurrent.ConcurrentHashMap

class StreamResolver(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private data class Entry(
        val resolvedAt: Long,
        val stream: AudioCandidate,
    )

    private val cache = ConcurrentHashMap<String, Entry>()

    suspend fun resolve(videoId: String): Result<AudioCandidate> {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { now - it.resolvedAt < ttlMillis }?.let { return Result.success(it.stream) }
        return try {
            val response = SimpMusicPlayer.player(videoId).getOrThrow().second
            val candidates =
                response.streamingData
                    ?.adaptiveFormats
                    .orEmpty()
                    .filter { it.isAudio }
                    .mapNotNull { format ->
                        val url = format.url ?: return@mapNotNull null
                        AudioCandidate(
                            itag = format.itag,
                            mimeType = format.mimeType,
                            bitrate = format.bitrate,
                            url = url,
                            contentLength = format.contentLength,
                            durationMs = format.approxDurationMs?.toLongOrNull(),
                        )
                    }
            val best = AudioPicker.best(candidates) ?: return Result.failure(IllegalStateException("No audio stream for $videoId"))
            cache[videoId] = Entry(now, best)
            Result.success(best)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 20 * 60 * 1000L
    }
}
