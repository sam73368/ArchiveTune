/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class BassEnhancerFilterTest {
    private val sampleRate = 44_100
    private val frequencyHz = BassEnhancerFilter.DEFAULT_FREQUENCY_HZ.toFloat()
    private val lowAlpha = BassEnhancerFilter.lowAlphaFor(sampleRate, frequencyHz)
    private val harmonicsAlpha = BassEnhancerFilter.harmonicsAlphaFor(sampleRate, frequencyHz)

    private fun rms(values: List<Float>): Double = sqrt(values.sumOf { (it * it).toDouble() } / values.size)

    private fun tone(
        hz: Double,
        frame: Int,
    ): Float = (12_000 * sin(2 * PI * hz * frame / sampleRate)).toFloat()

    private fun enhanced(
        hz: Double,
        amount: Float,
        frames: Int = 20_000,
    ): List<Float> {
        val filter = BassEnhancerFilter()
        return (0 until frames).map { i ->
            val dry = tone(hz, i)
            dry + filter.contribution(dry, lowAlpha, harmonicsAlpha, amount)
        }.drop(5_000)
    }

    private fun dry(
        hz: Double,
        frames: Int = 20_000,
    ): List<Float> = (0 until frames).map { tone(hz, it) }.drop(5_000)

    @Test
    fun `a deep bass tone gets louder when the amount is raised`() {
        val untouched = rms(dry(60.0))
        val half = rms(enhanced(60.0, 0.5f))
        val full = rms(enhanced(60.0, 1f))
        assertTrue("expected more than 1.5 dB at half amount", half > untouched * 1.19)
        assertTrue("expected more at full amount than at half", full > half)
    }

    @Test
    fun `a zero amount leaves the signal untouched`() {
        val untouched = dry(60.0)
        val processed = enhanced(60.0, 0f)
        untouched.zip(processed).forEach { (a, b) -> assertEquals(a, b, 0.001f) }
    }

    @Test
    fun `midrange and treble stay practically unchanged`() {
        val untouched = rms(dry(1_000.0))
        val processed = rms(enhanced(1_000.0, 1f))
        assertTrue("expected under 0.5 dB of change at 1 kHz", abs(processed / untouched - 1.0) < 0.06)
    }

    @Test
    fun `harmonics are generated above the bass tone`() {
        val processed = enhanced(60.0, 1f)
        val third = harmonicLevel(processed, 180.0)
        val untouchedThird = harmonicLevel(dry(60.0), 180.0)
        assertTrue("expected a measurable third harmonic", third > untouchedThird + 100.0)
    }

    @Test
    fun `reset clears the filter memory`() {
        val filter = BassEnhancerFilter()
        repeat(2_000) { filter.contribution(tone(60.0, it), lowAlpha, harmonicsAlpha, 1f) }
        filter.reset()
        assertEquals(0f, filter.contribution(0f, lowAlpha, harmonicsAlpha, 1f), 0.0001f)
    }

    private fun harmonicLevel(
        values: List<Float>,
        hz: Double,
    ): Double {
        val inPhase = 2 * values.indices.sumOf { values[it] * cos(2 * PI * hz * it / sampleRate) } / values.size
        val quadrature = 2 * values.indices.sumOf { values[it] * sin(2 * PI * hz * it / sampleRate) } / values.size
        return sqrt(inPhase * inPhase + quadrature * quadrature)
    }
}
