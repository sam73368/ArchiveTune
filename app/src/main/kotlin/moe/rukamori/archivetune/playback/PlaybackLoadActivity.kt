/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.SystemClock
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.util.concurrent.atomic.AtomicInteger

object PlaybackLoadActivity : TransferListener {
    const val STALL_AFTER_MS = 6_000L
    const val RESOLVE_GRACE_MS = 20_000L

    private val resolving = AtomicInteger(0)

    @Volatile
    private var lastActivityAt = 0L

    @Volatile
    private var resolvingSince = 0L

    private fun touch() {
        lastActivityAt = SystemClock.elapsedRealtime()
    }

    fun <T> resolving(block: () -> T): T {
        if (resolving.getAndIncrement() == 0) resolvingSince = SystemClock.elapsedRealtime()
        touch()
        try {
            return block()
        } finally {
            resolving.decrementAndGet()
            touch()
        }
    }

    fun lastSignalAt(now: Long = SystemClock.elapsedRealtime()): Long =
        if (resolving.get() > 0 && now - resolvingSince < RESOLVE_GRACE_MS) now else lastActivityAt

    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = touch()

    override fun onTransferStart(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = touch()

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
    ) {
        if (bytesTransferred > 0) touch()
    }

    override fun onTransferEnd(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = touch()
}
