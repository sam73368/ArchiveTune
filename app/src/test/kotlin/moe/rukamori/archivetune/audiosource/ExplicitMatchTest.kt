package moe.rukamori.archivetune.audiosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExplicitMatchTest {
    private val target =
        TrackMatching.Target(
            title = "Blinded in Chains",
            artists = listOf("Avenged Sevenfold"),
            album = "City of Evil",
            durationMs = 394_000L,
            explicit = true,
        )

    private fun candidate(
        id: String,
        explicit: Boolean?,
    ) = TrackMatching.Candidate(
        id = id,
        title = "Blinded in Chains",
        artists = listOf("Avenged Sevenfold"),
        album = "City of Evil",
        durationMs = 394_000L,
        explicit = explicit,
    )

    @Test
    fun `explicit song picks the explicit edition even when the clean one is listed first`() {
        val best = TrackMatching.best(target, listOf(candidate("clean", false), candidate("explicit", true)))
        assertEquals("explicit", best?.id)
    }

    @Test
    fun `clean edition of an explicit song is rejected`() {
        assertNull(TrackMatching.best(target, listOf(candidate("clean", false))))
    }

    @Test
    fun `explicit edition is preferred even when the wanted song is not flagged`() {
        val unflagged = target.copy(explicit = false)
        val best = TrackMatching.best(unflagged, listOf(candidate("first", false), candidate("second", true)))
        assertEquals("second", best?.id)
    }

    @Test
    fun `hide explicit turns the preference off but still accepts clean editions`() {
        TrackMatching.preferExplicitEditions = false
        try {
            val unflagged = target.copy(explicit = false)
            val best = TrackMatching.best(unflagged, listOf(candidate("first", false), candidate("second", true)))
            assertEquals("first", best?.id)
        } finally {
            TrackMatching.preferExplicitEditions = true
        }
    }

    @Test
    fun `unknown flag is neutral`() {
        assertEquals(0, TrackMatching.explicitAdjustment(true, null))
    }
}
