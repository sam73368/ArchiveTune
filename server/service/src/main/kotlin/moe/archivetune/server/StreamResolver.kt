package moe.archivetune.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.response.PlayerResponse
import moe.rukamori.archivetune.simpstream.SimpMusicPlayer
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class StreamResolver(
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
) {
    private data class Entry(
        val resolvedAt: Long,
        val stream: AudioCandidate,
    )

    private val cache = ConcurrentHashMap<String, Entry>()
    private val probeClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(5))
            .build()

    private val fallbackClients: List<YouTubeClient> =
        listOf(
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.ANDROID_VR_1_65_10,
            YouTubeClient.IOS,
            YouTubeClient.ANDROID_MUSIC,
            YouTubeClient.IOS_MUSIC,
            YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
            YouTubeClient.MOBILE,
            YouTubeClient.IPADOS,
            YouTubeClient.VISIONOS,
        )

    suspend fun resolve(videoId: String): Result<AudioCandidate> {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { now - it.resolvedAt < ttlMillis }?.let { return Result.success(it.stream) }
        val failures = mutableListOf<String>()
        val resolved =
            attempt("simpmusic", failures) {
                candidatesOf(SimpMusicPlayer.player(videoId).getOrThrow().second, userAgent = null)
            } ?: fallbackClients.firstNotNullOfOrNull { client ->
                attempt(client.clientName + " " + client.clientVersion, failures) {
                    val response =
                        YouTube
                            .player(videoId = videoId, client = client, setLogin = false)
                            .getOrThrow()
                    val status = response.playabilityStatus
                    if (status.status != "OK") error("playability ${status.status}: ${status.reason}")
                    candidatesOf(response, client.userAgent)
                }
            }
        if (resolved == null) {
            return Result.failure(IllegalStateException("No playable audio for $videoId (${failures.joinToString("; ")})"))
        }
        cache[videoId] = Entry(now, resolved)
        return Result.success(resolved)
    }

    fun invalidate(videoId: String) {
        cache.remove(videoId)
    }

    private suspend fun attempt(
        source: String,
        failures: MutableList<String>,
        block: suspend () -> List<AudioCandidate>,
    ): AudioCandidate? =
        try {
            val candidates = block()
            val ordered = AudioPicker.ordered(candidates)
            if (ordered.isEmpty()) error("no audio format with a direct URL")
            val playable = ordered.firstOrNull { probe(it) }
            if (playable == null) error("every audio URL was rejected by googlevideo")
            System.err.println("resolve: $source -> itag ${playable.itag} (${playable.mimeType})")
            playable
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            val reason = "$source: ${error.message ?: error::class.simpleName}"
            System.err.println("resolve: $reason")
            failures += reason
            null
        }

    private fun candidatesOf(
        response: PlayerResponse,
        userAgent: String?,
    ): List<AudioCandidate> =
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
                    userAgent = userAgent,
                )
            }

    private suspend fun probe(candidate: AudioCandidate): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request =
                    HttpRequest
                        .newBuilder(URI.create(candidate.url))
                        .timeout(Duration.ofSeconds(8))
                        .header("Range", "bytes=0-1")
                        .header("User-Agent", candidate.userAgent ?: BROWSER_USER_AGENT)
                        .GET()
                        .build()
                val code = probeClient.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()
                if (code !in 200..299) System.err.println("probe itag ${candidate.itag}: HTTP $code")
                code in 200..299
            } catch (error: Exception) {
                System.err.println("probe itag ${candidate.itag} failed: $error")
                true
            }
        }

    private companion object {
        const val DEFAULT_TTL_MILLIS = 20 * 60 * 1000L
    }
}
