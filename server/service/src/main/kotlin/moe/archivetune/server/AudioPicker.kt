package moe.archivetune.server

const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

data class AudioCandidate(
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val url: String,
    val contentLength: Long?,
    val durationMs: Long?,
    val userAgent: String? = null,
)

object AudioPicker {
    fun ordered(candidates: List<AudioCandidate>): List<AudioCandidate> =
        candidates
            .filter { it.mimeType.startsWith("audio/") && it.url.isNotBlank() }
            .sortedWith(
                compareByDescending<AudioCandidate> { codecRank(it.mimeType) }
                    .thenByDescending { it.bitrate },
            )

    fun best(candidates: List<AudioCandidate>): AudioCandidate? = ordered(candidates).firstOrNull()

    private fun codecRank(mimeType: String): Int =
        when {
            mimeType.contains("mp4a") -> 2
            mimeType.contains("opus") -> 1
            else -> 0
        }
}
