/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.Loader
import java.io.IOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.channels.ClosedChannelException
import java.nio.channels.UnresolvedAddressException
import javax.net.ssl.SSLException

class NetworkResilientLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {
    override fun getMinimumLoadableRetryCount(dataType: Int): Int =
        if (dataType == C.DATA_TYPE_MEDIA) NETWORK_RETRY_COUNT else super.getMinimumLoadableRetryCount(dataType)

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val exception = loadErrorInfo.exception
        val networkFailure = isNetworkFailure(exception)
        val base = super.getRetryDelayMsFor(loadErrorInfo)
        if (base == C.TIME_UNSET && !(exception is Loader.UnexpectedLoaderException && networkFailure)) {
            return C.TIME_UNSET
        }
        if (!networkFailure) {
            return if (loadErrorInfo.errorCount > DEFAULT_RETRY_COUNT) C.TIME_UNSET else base
        }
        return retryDelayMs(loadErrorInfo.errorCount)
    }

    companion object {
        const val NETWORK_RETRY_COUNT = 10
        private const val DEFAULT_RETRY_COUNT = 3
        private val DELAYS_MS = longArrayOf(250L, 600L, 1_200L, 2_000L, 3_000L)

        fun retryDelayMs(errorCount: Int): Long =
            DELAYS_MS[(errorCount - 1).coerceIn(0, DELAYS_MS.lastIndex)]

        fun isNetworkFailure(throwable: Throwable?): Boolean {
            var current = throwable
            var depth = 0
            while (current != null && depth < 8) {
                if (current is HttpDataSource.InvalidResponseCodeException) return false
                if (current is SocketException ||
                    current is SocketTimeoutException ||
                    current is UnknownHostException ||
                    current is SSLException ||
                    current is ClosedChannelException ||
                    current is UnresolvedAddressException ||
                    current is HttpDataSource.HttpDataSourceException ||
                    current is java.io.InterruptedIOException ||
                    (current is IOException && current.message?.contains("unexpected end of stream", ignoreCase = true) == true)
                ) {
                    return true
                }
                current = current.cause
                depth++
            }
            return false
        }
    }
}
