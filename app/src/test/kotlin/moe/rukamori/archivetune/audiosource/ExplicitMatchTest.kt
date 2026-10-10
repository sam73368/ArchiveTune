package moe.rukamori.archivetune.audiosource

import org.junit.Assert.assertEquals
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
    fun `non explicit song is not influenced by the flag`() {
        val clean = target.copy(explicit = false)
        val best = TrackMatching.best(clean, listOf(candidate("first", false), candidate("second", true)))
        assertEquals("first", best?.id)
    }

    @Test
    fun `unknown flag is neutral`() {
        assertEquals(0, TrackMatching.explicitAdjustment(true, null))
    }
}
