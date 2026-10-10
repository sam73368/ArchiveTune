/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.audiosource.TitleMatch
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_OMV
import moe.rukamori.archivetune.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_UGC
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Finds another YouTube upload of the same recording when the requested one can't (or shouldn't)
 * be played:
 *
 * - **Studio audio for music videos** — YouTube Music often lists the official clip (OMV) in an
 *   album instead of the audio track; the clip has intros, skits and sound effects. When the user
 *   hides music videos we look for the studio "song" (ATV) upload of the same title, e.g. the
 *   single release.
 * - **Premium-only tracks** — some labels lock the audio track behind Music Premium for free
 *   accounts ("Music Premium members only"). Another release of the same song, or as a last
 *   resort a clip/lyric upload with the same length, is still playable.
 *
 * Only search + matching lives here; [MusicService] resolves the stream through the normal
 * [moe.rukamori.archivetune.utils.YTPlayerUtils] client chain and decides when to call this.
 */
object StudioAudioResolver {
    private const val TAG = "StudioAudioResolver"
    private const val MAX_CANDIDATES = 4
    private const val NEGATIVE_CACHE_MS = 30 * 60 * 1000L
    private const val RETRY_AFTER_FAILURE_MS = 2 * 60 * 1000L

    /** Tight tolerance for a different upload of the same recording (premium rescue). */
    private const val SAME_RECORDING_TOLERANCE_MS = 12_000L

    data class Request(
        val mediaId: String,
        val title: String,
        val artists: List<String>,
        val durationMs: Long?,
        /** true: only studio "song" uploads (ATV) are acceptable — used to replace a clip. */
        val studioOnly: Boolean,
        /** "Hide explicit" is on: never swap in an explicit upload. */
        val hideExplicit: Boolean = false,
    )

    private val negativeCache = ConcurrentHashMap<String, Long>()

    /** Remembers which upload actually played for a media id, so later chunks/replays reuse it. */
    private val resolvedAlternates = ConcurrentHashMap<String, String>()

    fun cachedAlternate(mediaId: String): String? = resolvedAlternates[mediaId]

    fun rememberAlternate(
        mediaId: String,
        alternateId: String,
    ) {
        resolvedAlternates[mediaId] = alternateId
        negativeCache.remove(cacheKey(mediaId, studioOnly = true))
        negativeCache.remove(cacheKey(mediaId, studioOnly = false))
    }

    fun forgetAlternate(mediaId: String) {
        resolvedAlternates.remove(mediaId)
    }

    /**
     * Stops retrying a lookup for a while (every playback chunk would otherwise search again).
     * A definite "nothing found" is remembered longer than a failure (offline, timeout).
     */
    fun markNoAlternate(
        mediaId: String,
        studioOnly: Boolean,
        definite: Boolean,
    ) {
        val ttl = if (definite) NEGATIVE_CACHE_MS else RETRY_AFTER_FAILURE_MS
        negativeCache[cacheKey(mediaId, studioOnly)] = System.currentTimeMillis() + ttl
    }

    /** True while a recent lookup for this media id found nothing (or failed). */
    fun isKnownUnavailable(
        mediaId: String,
        studioOnly: Boolean,
    ): Boolean {
        val key = cacheKey(mediaId, studioOnly)
        val until = negativeCache[key] ?: return false
        if (until > System.currentTimeMillis()) return true
        negativeCache.remove(key)
        return false
    }

    /**
     * Ordered list of candidate video ids (best first), never containing [Request.mediaId].
     * Empty when nothing trustworthy was found; null when the search itself failed.
     */
    suspend fun findCandidates(request: Request): List<String>? {
        val title = request.title.trim()
        if (title.isBlank()) return emptyList()

        val wantedTitle = cleanTitle(title)
        val primaryArtist = request.artists.firstOrNull { it.isNotBlank() }.orEmpty()
        val query = listOf(wantedTitle, primaryArtist).filter { it.isNotBlank() }.joinToString(" ")

        val songs =
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_SONG)
                .onFailure { Timber.tag(TAG).w(it, "song search failed for %s", request.mediaId) }
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                ?: return null

        val wantedKey = comparable(wantedTitle)
        val studioMatches =
            songs
                .asSequence()
                .filter { it.id != request.mediaId && it.musicVideoType() == MUSIC_VIDEO_TYPE_ATV }
                .filter { !request.hideExplicit || !it.explicit }
                .filter { matchesArtist(request.artists, it) }
                // Replacing a clip must be the very same title ("Love" is not "Love Me Like You Do");
                // the looser match is only for the Premium-only rescue.
                .filter { if (request.studioOnly) comparable(it.title) == wantedKey else matchesTitle(wantedTitle, it.title) }
                .filterNot { looksLikeAlteredVersion(wantedTitle, it.title) }
                .filter {
                    if (request.studioOnly) {
                        studioDurationPlausible(request.durationMs, it.duration)
                    } else {
                        durationClose(request.durationMs, it.duration, SAME_RECORDING_TOLERANCE_MS)
                    }
                }.sortedWith(
                    compareBy<SongItem> { if (comparable(it.title) == wantedKey) 0 else 1 }
                        .thenBy { durationDistance(request.durationMs, it.duration) },
                ).map { it.id }
                .toList()

        if (request.studioOnly) {
            return studioMatches.take(MAX_CANDIDATES).also { logResult(request, it) }
        }

        // Premium rescue: a clip or a fan "audio"/lyrics upload is better than an error, but only
        // when it is clearly the same recording (same title, same artist, near-identical length).
        val videos =
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_VIDEO)
                .onFailure { Timber.tag(TAG).w(it, "video search failed for %s", request.mediaId) }
                .getOrNull()
                ?.items
                ?.filterIsInstance<SongItem>()
                .orEmpty()
        val videoMatches =
            if (request.durationMs == null) {
                emptyList()
            } else {
                videos
                    .asSequence()
                    .filter { it.id != request.mediaId }
                    .filter { it.musicVideoType().let { type -> type == MUSIC_VIDEO_TYPE_OMV || type == MUSIC_VIDEO_TYPE_UGC } }
                    .filter { video ->
                        matchesTitle(wantedTitle, video.title) &&
                            (matchesArtist(request.artists, video) || titleMentionsArtist(request.artists, video.title))
                    }.filterNot { looksLikeAlteredVersion(wantedTitle, it.title) }
                    .filter { durationClose(request.durationMs, it.duration, SAME_RECORDING_TOLERANCE_MS) }
                    // Official clips first, then the closest length.
                    .sortedWith(
                        compareBy<SongItem> { if (it.musicVideoType() == MUSIC_VIDEO_TYPE_OMV) 0 else 1 }
                            .thenBy { durationDistance(request.durationMs, it.duration) },
                    ).map { it.id }
                    .toList()
            }

        return (studioMatches + videoMatches).distinct().take(MAX_CANDIDATES).also { logResult(request, it) }
    }

    private fun logResult(
        request: Request,
        candidates: List<String>,
    ) {
        Timber.tag(TAG).i(
            "%s alternates for %s \"%s\": %s",
            if (request.studioOnly) "studio" else "rescue",
            request.mediaId,
            request.title,
            candidates.ifEmpty { listOf("none") }.joinToString(),
        )
    }

    private fun cacheKey(
        mediaId: String,
        studioOnly: Boolean,
    ) = "$mediaId:${if (studioOnly) "studio" else "rescue"}"

    private fun SongItem.musicVideoType(): String? =
        endpoint
            ?.watchEndpointMusicSupportedConfigs
            ?.watchEndpointMusicConfig
            ?.musicVideoType

    private val decorationRegex =
        Regex(
            """\s*[\[(][^\])]*\b(clip|video|vid[eé]o|officiel+e?|official|audio|lyrics?|paroles|visuali[sz]er|hd|4k)\b[^\])]*[\])]""",
            RegexOption.IGNORE_CASE,
        )

    /** Strips "(Clip officiel)", "[Official Video]", "(Audio)", "(Paroles)"… */
    internal fun cleanTitle(title: String): String = title.replace(decorationRegex, "").replace(Regex("\\s+"), " ").trim()

    private fun comparable(value: String): String = TitleMatch.normalize(cleanTitle(value))

    internal fun matchesTitle(
        wanted: String,
        candidate: String,
    ): Boolean {
        val wantedNorm = comparable(wanted)
        if (wantedNorm.isBlank()) return false
        val candidateNorm = comparable(candidate)
        if (candidateNorm == wantedNorm) return true
        // "Bigflo et Oli - Florian", "Florian (Lyrics)": the wanted title must appear as a whole
        // word run, and the rest may only be decoration around it.
        val padded = " $candidateNorm "
        if (" $wantedNorm " !in padded) return false
        val remainder = padded.replace(" $wantedNorm ", " ").trim()
        return remainder.split(' ').count { it.length > 1 } <= 4
    }

    private val alteredVersionPatterns =
        listOf(
            "remix", "live", "acoustic", "acoustique", "cover", "karaoke", "instrumental", "nightcore",
            "sped up", "speed up", "slowed", "reverb", "8d", "chipmunk", "parodie", "parody", "reaction",
            "piano", "unplugged", "acapella", "a cappella", "extended", "club mix", "mix", "edit",
            "stripped", "orchestral", "lofi", "lo-fi", "tribute", "bootleg",
        ).map { token -> token to Regex("(?<![\\p{L}\\p{N}])${Regex.escape(token)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE) }

    private fun looksLikeAlteredVersion(
        wanted: String,
        candidate: String,
    ): Boolean {
        return alteredVersionPatterns.any { (_, pattern) ->
            pattern.containsMatchIn(candidate) && !pattern.containsMatchIn(wanted)
        }
    }

    private fun matchesArtist(
        artists: List<String>,
        item: SongItem,
    ): Boolean {
        if (artists.isEmpty()) return false
        val wanted = artists.map { TitleMatch.normalize(it) }.filter { it.isNotBlank() }
        val candidate = item.artists.map { TitleMatch.normalize(it.name.replace(" - Topic", "")) }
        return wanted.any { w -> candidate.any { c -> c == w || (w.length >= 4 && c.length >= 4 && (w in c || c in w)) } }
    }

    private fun titleMentionsArtist(
        artists: List<String>,
        title: String,
    ): Boolean {
        val normalizedTitle = " ${TitleMatch.normalize(title)} "
        return artists
            .map { TitleMatch.normalize(it) }
            .filter { it.length >= 3 }
            .any { artist ->
                " $artist " in normalizedTitle ||
                    // "Bigflo & Oli" vs "Bigflo et Oli"
                    artist.split(' ').filter { it.length >= 3 }.let { parts -> parts.isNotEmpty() && parts.all { " $it " in normalizedTitle } }
            }
    }

    private fun durationClose(
        wantedMs: Long?,
        candidateSeconds: Int?,
        toleranceMs: Long,
    ): Boolean {
        if (wantedMs == null || wantedMs <= 0 || candidateSeconds == null || candidateSeconds <= 0) return false
        return abs(wantedMs - candidateSeconds * 1000L) <= toleranceMs
    }

    /**
     * A studio cut can be shorter than the clip (no skit/intro) but never wildly different; unknown
     * lengths are given the benefit of the doubt.
     */
    internal fun studioDurationPlausible(
        clipMs: Long?,
        candidateSeconds: Int?,
    ): Boolean {
        if (clipMs == null || clipMs <= 0 || candidateSeconds == null || candidateSeconds <= 0) return true
        val candidateMs = candidateSeconds * 1000L
        return candidateMs in (clipMs * 0.5).toLong()..(clipMs * 1.2).toLong()
    }

    private fun durationDistance(
        wantedMs: Long?,
        candidateSeconds: Int?,
    ): Long {
        if (wantedMs == null || candidateSeconds == null) return Long.MAX_VALUE / 2
        return abs(wantedMs - candidateSeconds * 1000L)
    }
}
