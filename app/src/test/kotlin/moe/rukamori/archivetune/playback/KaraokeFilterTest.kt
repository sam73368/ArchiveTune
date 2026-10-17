package moe.rukamori.archivetune.playback

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class KaraokeFilterTest {
    private val sampleRate = 44_100
    private val alpha = KaraokeFilter.alphaFor(sampleRate)

    private fun rms(values: List<Float>): Double = sqrt(values.sumOf { (it * it).toDouble() } / values.size)

    private fun tone(
        hz: Double,
        frame: Int,
    ): Float = (10_000 * sin(2 * PI * hz * frame / sampleRate)).toFloat()

    /** Runs the filter the way the audio processor does and returns the output of the left channel. */
    private fun processLeft(
        left: (Int) -> Float,
        right: (Int) -> Float,
        frames: Int = 8_000,
    ): List<Float> {
        val filter = KaraokeFilter()
        return (0 until frames).map { i ->
            val l = left(i)
            val r = right(i)
            l - KaraokeFilter.STRENGTH * filter.centreAboveBass(l, r, alpha)
        }.drop(1_000)
    }

    @Test
    fun `a vocal panned dead centre is strongly attenuated`() {
        val input = (0 until 8_000).map { tone(1_000.0, it) }.drop(1_000)
        val output = processLeft({ tone(1_000.0, it) }, { tone(1_000.0, it) })
        assertTrue("expected under 25% of the original level", rms(output) < rms(input) * 0.25)
    }

    @Test
    fun `an instrument panned hard to one side is left alone`() {
        val input = (0 until 8_000).map { tone(1_000.0, it) }.drop(1_000)
        val output = processLeft({ tone(1_000.0, it) }, { 0f })
        // Only half of it is "centre", so at most 0.45 of the amplitude is removed.
        assertTrue("expected over 50% of the original level", rms(output) > rms(input) * 0.5)
    }

    @Test
    fun `bass in the centre is preserved`() {
        val input = (0 until 8_000).map { tone(50.0, it) }.drop(1_000)
        val output = processLeft({ tone(50.0, it) }, { tone(50.0, it) }, 8_000)
        assertTrue("expected over 60% of the original level", rms(output) > rms(input) * 0.6)
    }

    @Test
    fun `opposite phase content is untouched`() {
        val input = (0 until 8_000).map { tone(1_000.0, it) }.drop(1_000)
        val output = processLeft({ tone(1_000.0, it) }, { -tone(1_000.0, it) })
        assertTrue(Math.abs(rms(output) - rms(input)) < rms(input) * 0.02)
    }
}
