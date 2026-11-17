/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import java.net.InetAddress
import okhttp3.Dns

object PlexDns : Dns {
    private val ipv4Label = Regex("""^(\d{1,3})-(\d{1,3})-(\d{1,3})-(\d{1,3})$""")

    fun ipv4Of(host: String): String? {
        if (!host.endsWith(".plex.direct", ignoreCase = true)) return null
        val match = ipv4Label.matchEntire(host.substringBefore('.')) ?: return null
        val parts = match.groupValues.drop(1).map { it.toInt() }
        if (parts.any { it > 255 }) return null
        return parts.joinToString(".")
    }

    fun rank(
        host: String,
        phoneAddresses: List<String>,
    ): Int {
        val ip = ipv4Of(host) ?: return RANK_PUBLIC
        val octets = ip.split('.').map { it.toInt() }
        val isPrivate =
            octets[0] == 10 ||
                (octets[0] == 192 && octets[1] == 168) ||
                (octets[0] == 172 && octets[1] in 16..31)
        if (!isPrivate) return RANK_PUBLIC
        val sameSubnet = phoneAddresses.any { phone -> phone.substringBeforeLast('.') == ip.substringBeforeLast('.') }
        if (sameSubnet) return RANK_SAME_SUBNET
        val containerGateway = octets[0] == 172 && octets[1] in 16..31 && octets[3] == 1
        return if (containerGateway) RANK_CONTAINER else RANK_PRIVATE
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val ip = ipv4Of(hostname) ?: return Dns.SYSTEM.lookup(hostname)
        return listOf(InetAddress.getByAddress(hostname, ip.split('.').map { it.toInt().toByte() }.toByteArray()))
    }

    const val RANK_SAME_SUBNET = 0
    const val RANK_PRIVATE = 1
    const val RANK_PUBLIC = 2
    const val RANK_CONTAINER = 3
}
