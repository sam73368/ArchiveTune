package moe.rukamori.archivetune.smartplaylist

import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import moe.rukamori.archivetune.db.entities.SongPlayStat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

class SmartPlaylistTest {
    private val now = LocalDateTime.of(2026, 10, 11, 12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val day = 24L * 60 * 60 * 1000

    private fun song(
        id: String,
        liked: Boolean = false,
        inLibrary: Boolean = true,
        explicit: Boolean = false,
        addedDaysAgo: Long = 400,
    ) = Song(
        song =
            SongEntity(
                id = id,
                title = id,
                liked = liked,
                explicit = explicit,
                inLibrary = if (inLibrary) LocalDateTime.ofEpochSecond((now - addedDaysAgo * day) / 1000, 0, ZoneOffset.UTC) else null,
            ),
        artists = emptyList(),
    )

    private fun stat(
        id: String,
        plays: Int,
        daysAgo: Long,
    ) = SongPlayStat(id, plays, now - daysAgo * day)

    private fun run(
        rule: SmartRule,
        songs: List<Song>,
        stats: List<SongPlayStat>,
    ) = SmartPlaylists.evaluate(rule, songs, stats.associateBy { it.songId }, now).map { it.id }

    @Test
    fun `songs outside the library are ignored`() {
        val result = run(SmartRule("r", "r"), listOf(song("a"), song("ghost", inLibrary = false)), emptyList())
        assertEquals(listOf("a"), result)
    }

    @Test
    fun `never played keeps only songs without any listen`() {
        val rule = SmartRule("r", "r", plays = PlayFilter.NEVER, sort = SmartSort.TITLE)
        val result = run(rule, listOf(song("a"), song("b"), song("c")), listOf(stat("b", 3, 1)))
        assertEquals(listOf("a", "c"), result)
    }

    @Test
    fun `rediscover returns liked songs not heard for a while longest first`() {
        val rule = SmartRule("r", "r", liked = LikedFilter.LIKED, idleDays = 90, sort = SmartSort.NOT_HEARD_FOR_LONGEST)
        val songs = listOf(song("old", liked = true), song("older", liked = true), song("recent", liked = true), song("notliked"))
        val stats = listOf(stat("old", 4, 120), stat("older", 2, 300), stat("recent", 9, 5), stat("notliked", 1, 400))
        assertEquals(listOf("older", "old"), run(rule, songs, stats))
    }

    @Test
    fun `idle filter excludes songs that were never played`() {
        val rule = SmartRule("r", "r", idleDays = 30)
        assertEquals(emptyList<String>(), run(rule, listOf(song("a")), emptyList()))
    }

    @Test
    fun `minimum plays and most played ordering with a limit`() {
        val rule = SmartRule("r", "r", plays = PlayFilter.PLAYED, minPlays = 5, sort = SmartSort.MOST_PLAYED, limit = 2)
        val songs = listOf(song("a"), song("b"), song("c"), song("d"))
        val stats = listOf(stat("a", 6, 1), stat("b", 20, 1), stat("c", 9, 1), stat("d", 2, 1))
        assertEquals(listOf("b", "c"), run(rule, songs, stats))
    }

    @Test
    fun `added within filters by library date and explicit can be excluded`() {
        val rule = SmartRule("r", "r", addedWithinDays = 30, allowExplicit = false)
        val songs = listOf(song("new", addedDaysAgo = 3), song("old", addedDaysAgo = 200), song("newExplicit", explicit = true, addedDaysAgo = 2))
        assertEquals(listOf("new"), run(rule, songs, emptyList()))
    }

    @Test
    fun `custom rules survive an encode and decode round trip and presets are not stored`() {
        val custom = SmartRule("id1", "Mine", liked = LikedFilter.LIKED, idleDays = 30, minPlays = 2, addedWithinDays = 7, allowExplicit = false, sort = SmartSort.RANDOM, limit = 25)
        val decoded = SmartPlaylists.decode(SmartPlaylists.encode(SmartPlaylists.presets + custom))
        assertEquals(listOf(custom), decoded)
        assertEquals(emptyList<SmartRule>(), SmartPlaylists.decode("not json"))
    }
}
