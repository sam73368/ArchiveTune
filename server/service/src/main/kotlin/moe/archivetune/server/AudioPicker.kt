package moe.archivetune.server

data class AudioCandidate(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    val contentLength: Long?,
    val durationMs: Long?,
)

object AudioPicker {
    fun best(candidates: List<AudioCandidate>): AudioCandidate? =
        candidates
            .filter { it.mimeType.startsWith("audio/") && it.url.isNotBlank() }
            .sortedWith(
                compareByDescending<AudioCandidate> { codecRank(it.mimeType) }
                    .thenByDescending { it.bitrate },
            ).firstOrNull()

    private fun codecRank(mimeType: String): Int =
        when {
            mimeType.contains("mp4a") -> 2
            mimeType.contains("opus") -> 1
            else -> 0
        }
}
