/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.smartplaylist

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongPlayStat
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.random.Random

enum class SmartSort { RECENTLY_ADDED, MOST_PLAYED, LEAST_PLAYED, NOT_HEARD_FOR_LONGEST, RANDOM, TITLE }

enum class LikedFilter { ANY, LIKED, NOT_LIKED }

enum class PlayFilter { ANY, NEVER, PLAYED }

/**
 * A rule-based playlist. It is never stored as a list of songs: it is evaluated against the
 * library each time it is opened, so it always reflects the current listening history.
 */
data class SmartRule(
    val id: String,
    val name: String,
    val liked: LikedFilter = LikedFilter.ANY,
    val plays: PlayFilter = PlayFilter.ANY,
    /** Only songs last heard more than this many days ago (0 = off). Implies the song was played. */
    val idleDays: Int = 0,
    /** Only songs heard at least this many times (0 = off). */
    val minPlays: Int = 0,
    /** Only songs added to the library in the last N days (0 = off). */
    val addedWithinDays: Int = 0,
    val allowExplicit: Boolean = true,
    val sort: SmartSort = SmartSort.RECENTLY_ADDED,
    /** Maximum number of songs (0 = no limit). */
    val limit: Int = 0,
    val preset: Boolean = false,
)

object SmartPlaylists {
    const val PRESET_PREFIX = "preset_"
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Nameless presets: titles are localised in the UI from the id. */
    val presets: List<SmartRule> =
        listOf(
            SmartRule(
                id = "preset_rediscover",
                name = "",
                liked = LikedFilter.LIKED,
                idleDays = 90,
                sort = SmartSort.NOT_HEARD_FOR_LONGEST,
                limit = 50,
                preset = true,
            ),
            SmartRule(
                id = "preset_never_played",
                name = "",
                plays = PlayFilter.NEVER,
                sort = SmartSort.RANDOM,
                limit = 100,
                preset = true,
            ),
            SmartRule(
                id = "preset_recently_added",
                name = "",
                addedWithinDays = 30,
                sort = SmartSort.RECENTLY_ADDED,
                preset = true,
            ),
            SmartRule(
                id = "preset_obsessed",
                name = "",
                plays = PlayFilter.PLAYED,
                minPlays = 5,
                sort = SmartSort.MOST_PLAYED,
                limit = 50,
                preset = true,
            ),
            SmartRule(
                id = "preset_liked_unheard",
                name = "",
                liked = LikedFilter.LIKED,
                plays = PlayFilter.NEVER,
                sort = SmartSort.RANDOM,
                preset = true,
            ),
        )

    fun nowMillis(): Long = LocalDateTime.now().toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun LocalDateTime.millis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

    /** Songs that belong to the user's library (not every song that merely appeared in the history). */
    private fun Song.inUserLibrary(): Boolean = song.liked || song.inLibrary != null || song.isLocal

    private fun Song.addedAt(): Long = (song.inLibrary ?: song.likedDate ?: song.date ?: song.dateDownload)?.millis() ?: 0L

    fun evaluate(
        rule: SmartRule,
        songs: List<Song>,
        stats: Map<String, SongPlayStat>,
        now: Long = nowMillis(),
        random: Random = Random.Default,
    ): List<Song> {
        val idleBefore = now - rule.idleDays * DAY_MS
        val addedAfter = now - rule.addedWithinDays * DAY_MS
        val filtered =
            songs.filter { song ->
                val entity = song.song
                if (!song.inUserLibrary()) return@filter false
                if (entity.blockedAt != null || entity.isPodcast) return@filter false
                if (song.artists.any { it.blockedAt != null }) return@filter false
                if (!rule.allowExplicit && entity.explicit) return@filter false
                when (rule.liked) {
                    LikedFilter.ANY -> Unit
                    LikedFilter.LIKED -> if (!entity.liked) return@filter false
                    LikedFilter.NOT_LIKED -> if (entity.liked) return@filter false
                }
                val stat = stats[entity.id]
                val played = stat != null && stat.plays > 0
                when (rule.plays) {
                    PlayFilter.ANY -> Unit
                    PlayFilter.NEVER -> if (played) return@filter false
                    PlayFilter.PLAYED -> if (!played) return@filter false
                }
                if (rule.minPlays > 0 && (stat?.plays ?: 0) < rule.minPlays) return@filter false
                if (rule.idleDays > 0 && (stat == null || stat.lastPlayed > idleBefore)) return@filter false
                if (rule.addedWithinDays > 0 && song.addedAt() < addedAfter) return@filter false
                true
            }
        val sorted =
            when (rule.sort) {
                SmartSort.RECENTLY_ADDED -> filtered.sortedByDescending { it.addedAt() }
                SmartSort.MOST_PLAYED -> filtered.sortedByDescending { stats[it.id]?.plays ?: 0 }
                SmartSort.LEAST_PLAYED -> filtered.sortedBy { stats[it.id]?.plays ?: 0 }
                SmartSort.NOT_HEARD_FOR_LONGEST -> filtered.sortedBy { stats[it.id]?.lastPlayed ?: Long.MIN_VALUE }
                SmartSort.RANDOM -> filtered.shuffled(random)
                SmartSort.TITLE -> filtered.sortedBy { it.song.title.lowercase() }
            }
        return if (rule.limit > 0) sorted.take(rule.limit) else sorted
    }

    fun encode(rules: List<SmartRule>): String {
        val array = JSONArray()
        rules.filterNot { it.preset }.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("name", rule.name)
                    .put("liked", rule.liked.name)
                    .put("plays", rule.plays.name)
                    .put("idleDays", rule.idleDays)
                    .put("minPlays", rule.minPlays)
                    .put("addedWithinDays", rule.addedWithinDays)
                    .put("allowExplicit", rule.allowExplicit)
                    .put("sort", rule.sort.name)
                    .put("limit", rule.limit),
            )
        }
        return array.toString()
    }

    fun decode(raw: String): List<SmartRule> {
        if (raw.isBlank()) return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                if (id.isBlank()) continue
                add(
                    SmartRule(
                        id = id,
                        name = obj.optString("name"),
                        liked = enumOrDefault(obj.optString("liked"), LikedFilter.ANY),
                        plays = enumOrDefault(obj.optString("plays"), PlayFilter.ANY),
                        idleDays = obj.optInt("idleDays", 0).coerceAtLeast(0),
                        minPlays = obj.optInt("minPlays", 0).coerceAtLeast(0),
                        addedWithinDays = obj.optInt("addedWithinDays", 0).coerceAtLeast(0),
                        allowExplicit = obj.optBoolean("allowExplicit", true),
                        sort = enumOrDefault(obj.optString("sort"), SmartSort.RECENTLY_ADDED),
                        limit = obj.optInt("limit", 0).coerceAtLeast(0),
                    ),
                )
            }
        }
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(
        name: String,
        default: T,
    ): T = enumValues<T>().firstOrNull { it.name == name } ?: default
}
