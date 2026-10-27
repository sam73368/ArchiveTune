/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkResilientLoadErrorPolicyTest {
    @Test
    fun treatsConnectionDropsAsNetworkFailures() {
        assertTrue(NetworkResilientLoadErrorPolicy.isNetworkFailure(SocketException("Connection reset")))
        assertTrue(NetworkResilientLoadErrorPolicy.isNetworkFailure(SocketTimeoutException()))
        assertTrue(NetworkResilientLoadErrorPolicy.isNetworkFailure(UnknownHostException("rr1.googlevideo.com")))
        assertTrue(NetworkResilientLoadErrorPolicy.isNetworkFailure(RuntimeException(IOException("wrapped", SocketException("reset")))))
    }

    @Test
    fun doesNotRetryUnrelatedFailuresAsNetworkProblems() {
        assertFalse(NetworkResilientLoadErrorPolicy.isNetworkFailure(IOException("Studio stream temporarily unavailable")))
        assertFalse(NetworkResilientLoadErrorPolicy.isNetworkFailure(IllegalStateException("bug")))
        assertFalse(NetworkResilientLoadErrorPolicy.isNetworkFailure(null))
    }

    @Test
    fun retryDelaysGrowThenStayCapped() {
        assertEquals(250L, NetworkResilientLoadErrorPolicy.retryDelayMs(1))
        assertEquals(1_200L, NetworkResilientLoadErrorPolicy.retryDelayMs(3))
        assertEquals(3_000L, NetworkResilientLoadErrorPolicy.retryDelayMs(5))
        assertEquals(3_000L, NetworkResilientLoadErrorPolicy.retryDelayMs(10))
        val total = (1..NetworkResilientLoadErrorPolicy.NETWORK_RETRY_COUNT).sumOf { NetworkResilientLoadErrorPolicy.retryDelayMs(it) }
        assertTrue("retries must cover a long handover or tunnel", total >= 20_000L)
    }
}
