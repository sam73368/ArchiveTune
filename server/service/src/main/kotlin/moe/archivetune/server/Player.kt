package moe.archivetune.server

import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Serializable
data class PlayRequest(
    val query: String? = null,
    val type: String? = null,
    val trackId: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val device: String? = null,
    val radio: Boolean = true,
    val enqueue: Boolean = false,
)

@Serializable
data class PlayResult(
    val device: String,
    val type: String,
    val startIndex: Int,
    val queue: List<TrackResult>,
    val message: String,
)

@Serializable
data class ControlRequest(
    val device: String? = null,
    val action: String,
    val value: Double? = null,
)

@Serializable
data class QueueResponse(
    val type: String,
    val title: String,
    val tracks: List<TrackResult>,
)

class PlayerService(
    private val library: Library,
    private val cast: CastController,
    private val port: Int,
    private val token: String?,
    private val publicBaseUrl: String?,
) {
    suspend fun buildQueue(request: PlayRequest): QueueResponse {
        val type =
            when {
                request.albumId != null -> "album"
                request.artistId != null -> "artist"
                request.trackId != null -> "song"
                else -> request.type?.lowercase()?.takeIf { it in setOf("song", "album", "artist") } ?: "song"
            }
        val query = request.query?.trim().orEmpty()
        return when (type) {
            "album" -> {
                val albumId =
                    request.albumId
                        ?: library.bestAlbum(query.ifEmpty { error("query or albumId is required") })?.id
                        ?: error("no album found for '$query'")
                val details = library.album(albumId)
                QueueResponse("album", details.album.title + " - " + details.album.artists.joinToString(", "), details.tracks)
            }
            "artist" -> {
                val artistId =
                    request.artistId
                        ?: library.searchArtists(query.ifEmpty { error("query or artistId is required") }).firstOrNull()?.id
                        ?: error("no artist found for '$query'")
                val details = library.artist(artistId)
                if (details.topTracks.isEmpty()) error("no songs found for ${details.artist.name}")
                QueueResponse("artist", details.artist.name, details.topTracks)
            }
            else -> {
                val seed =
                    request.trackId?.let { library.track(it) }
                        ?: library.bestTrack(query.ifEmpty { error("query or trackId is required") })
                        ?: error("no song found for '$query'")
                val tracks = if (request.radio && !request.enqueue) library.radio(seed) else listOf(seed)
                QueueResponse("song", seed.title + " - " + seed.artists.joinToString(", "), tracks)
            }
        }
    }

    suspend fun play(request: PlayRequest): PlayResult {
        val device = cast.find(request.device) ?: error(deviceError(request.device))
        val queue = buildQueue(request)
        val castTracks = queue.tracks.map { it.toCastTrack() }
        cast.withSession(device) { session ->
            if (request.enqueue) {
                session.enqueue(castTracks, ::streamUrl)
            } else {
                session.playQueue(castTracks, 0, ::streamUrl)
            }
        }
        val verb = if (request.enqueue) "Added to the queue on" else "Playing on"
        return PlayResult(
            device = device.name,
            type = queue.type,
            startIndex = 0,
            queue = queue.tracks,
            message = "$verb ${device.name}: ${queue.title} (${queue.tracks.size} track(s))",
        )
    }

    suspend fun playTracks(
        tracks: List<TrackResult>,
        startIndex: Int,
        deviceName: String?,
    ): PlayResult {
        val device = cast.find(deviceName) ?: error(deviceError(deviceName))
        cast.withSession(device) { it.playQueue(tracks.map { track -> track.toCastTrack() }, startIndex, ::streamUrl) }
        return PlayResult(device.name, "tracks", startIndex, tracks, "Playing on ${device.name}")
    }

    suspend fun control(request: ControlRequest): CastStatus {
        val device = cast.find(request.device) ?: error(deviceError(request.device))
        return cast.withSession(device) { session ->
            session.control(request.action.lowercase(), request.value)
            session.status()
        }
    }

    suspend fun status(deviceName: String?): CastStatus {
        val device = cast.find(deviceName) ?: error(deviceError(deviceName))
        return cast.withSession(device) { it.status() }
    }

    private fun deviceError(name: String?): String {
        val names = cast.devices().joinToString(", ") { "'" + it.name + "'" }.ifEmpty { "none found" }
        return if (name.isNullOrBlank()) {
            "device is required (available: $names)"
        } else {
            "no device named '$name' (available: $names)"
        }
    }

    private fun streamUrl(
        track: CastTrack,
        localHost: String,
    ): String {
        val base = publicBaseUrl?.trimEnd('/') ?: "http://$localHost:$port"
        val suffix = token?.takeIf { it.isNotBlank() }?.let { "?token=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return "$base/stream/${track.id}$suffix"
    }

    private fun TrackResult.toCastTrack() =
        CastTrack(
            id = id,
            title = title,
            artists = artists,
            album = album,
            thumbnail = thumbnail?.let(::largeThumbnail),
            durationSeconds = durationSeconds,
        )

    companion object {
        fun largeThumbnail(url: String): String =
            url
                .replace(Regex("""=w\d+-h\d+"""), "=w544-h544")
                .replace(Regex("""=s\d+"""), "=s544")
    }
}
