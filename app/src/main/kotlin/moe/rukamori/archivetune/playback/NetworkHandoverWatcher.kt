/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import timber.log.Timber

class NetworkHandoverWatcher(
    context: Context,
    private val onHandover: () -> Unit,
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val handler = Handler(Looper.getMainLooper())

    private var lastNetwork: Network? = null
    private var lastAddresses: Set<String> = emptySet()
    private var registered = false

    private val fire = Runnable {
        Timber.tag(TAG).i("Network handover detected; dropping stale media connections")
        runCatching { onHandover() }
    }

    private fun schedule() {
        handler.removeCallbacks(fire)
        handler.postDelayed(fire, DEBOUNCE_MS)
    }

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val previous = lastNetwork
                lastNetwork = network
                if (previous != null && previous != network) schedule()
                if (previous == null && lastAddresses.isNotEmpty()) schedule()
            }

            override fun onLost(network: Network) {
                if (network == lastNetwork) {
                    lastNetwork = null
                }
            }

            override fun onLinkPropertiesChanged(
                network: Network,
                linkProperties: LinkProperties,
            ) {
                val addresses = linkProperties.linkAddresses.map { it.address.hostAddress.orEmpty() }.toSet()
                val changed = lastAddresses.isNotEmpty() && addresses != lastAddresses
                lastAddresses = addresses
                if (changed && network == lastNetwork) schedule()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                val transports =
                    intArrayOf(
                        NetworkCapabilities.TRANSPORT_CELLULAR,
                        NetworkCapabilities.TRANSPORT_WIFI,
                        NetworkCapabilities.TRANSPORT_ETHERNET,
                        NetworkCapabilities.TRANSPORT_VPN,
                    ).filter { networkCapabilities.hasTransport(it) }
                val key = transports.joinToString(",")
                if (lastTransports != null && lastTransports != key && network == lastNetwork) schedule()
                lastTransports = key
            }
        }

    private var lastTransports: String? = null

    fun register() {
        if (registered) return
        registered =
            runCatching {
                connectivityManager.registerDefaultNetworkCallback(callback, handler)
                true
            }.getOrDefault(false)
    }

    fun unregister() {
        if (!registered) return
        registered = false
        handler.removeCallbacks(fire)
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }

    private companion object {
        const val TAG = "NetworkHandover"
        const val DEBOUNCE_MS = 400L
    }
}
