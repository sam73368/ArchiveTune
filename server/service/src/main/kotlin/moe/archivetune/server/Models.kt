package moe.archivetune.server

import kotlinx.serialization.Serializable

@Serializable
data class Health(
    val status: String,
    val version: String,
    val uptimeSeconds: Long,
)

@Serializable
data class TrackResult(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val albumId: String?,
    val durationSeconds: Int?,
    val thumbnail: String?,
    val explicit: Boolean,
)

@Serializable
data class AlbumResult(
    val id: String,
    val title: String,
    val artists: List<String>,
    val year: Int?,
    val thumbnail: String?,
    val explicit: Boolean,
)

@Serializable
data class SearchResponse(
    val query: String,
    val type: String,
    val tracks: List<TrackResult> = emptyList(),
    val albums: List<AlbumResult> = emptyList(),
)

@Serializable
data class ResolveResponse(
    val id: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val contentLength: Long?,
    val durationMs: Long?,
    val streamPath: String,
)

@Serializable
data class ErrorBody(
    val error: String,
)
