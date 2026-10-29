package moe.archivetune.server

import kotlinx.serialization.Serializable
import java.net.Inet4Address
import java.util.concurrent.ConcurrentHashMap
import javax.jmdns.JmmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener

@Serializable
data class CastDevice(
    val id: String,
    val name: String,
    val model: String?,
    val host: String,
    val port: Int,
    val isGroup: Boolean,
    val source: String,
)

class CastDiscovery(
    staticDevices: String?,
) {
    private val found = ConcurrentHashMap<String, CastDevice>()
    private val static: List<CastDevice> = parseStatic(staticDevices)
    private var jmdns: JmmDNS? = null

    fun start() {
        try {
            val instance = JmmDNS.Factory.getInstance()
            instance.addServiceListener(
                SERVICE_TYPE,
                object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        instance.requestServiceInfo(event.type, event.name, 3_000)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        found.remove(event.name)
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        toDevice(event.info)?.let { device ->
                            if (found.put(event.name, device) == null) {
                                System.err.println("cast: found '${device.name}' (${device.model}) at ${device.host}:${device.port}")
                            }
                        }
                    }
                },
            )
            jmdns = instance
        } catch (error: Throwable) {
            System.err.println("cast: mDNS discovery unavailable ($error); only ARCHIVETUNE_CAST_DEVICES will be used")
        }
    }

    fun refresh() {
        val instance = jmdns ?: return
        runCatching {
            instance.list(SERVICE_TYPE, 2_000).forEach { info ->
                toDevice(info)?.let { found[info.name] = it }
            }
        }
    }

    fun devices(): List<CastDevice> {
        val byName = LinkedHashMap<String, CastDevice>()
        static.forEach { byName[it.name.lowercase()] = it }
        found.values.sortedBy { it.name.lowercase() }.forEach { byName.putIfAbsent(it.name.lowercase(), it) }
        return byName.values.toList()
    }

    private fun toDevice(info: ServiceInfo?): CastDevice? {
        info ?: return null
        val host =
            info.inet4Addresses.firstOrNull()?.hostAddress
                ?: info.inetAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                ?: return null
        val friendly = info.getPropertyString("fn")?.takeIf { it.isNotBlank() } ?: info.name
        val model = info.getPropertyString("md")
        val id = info.getPropertyString("id")?.takeIf { it.isNotBlank() } ?: "$host:${info.port}"
        return CastDevice(
            id = id,
            name = friendly,
            model = model,
            host = host,
            port = info.port,
            isGroup = model.equals("Google Cast Group", ignoreCase = true),
            source = "mdns",
        )
    }

    private fun parseStatic(value: String?): List<CastDevice> =
        value
            .orEmpty()
            .split(',', ';')
            .mapNotNull { entry ->
                val parts = entry.split('=', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val name = parts[0].trim()
                val address = parts[1].trim()
                if (name.isEmpty() || address.isEmpty()) return@mapNotNull null
                val host = address.substringBefore(':')
                val port = address.substringAfter(':', "8009").toIntOrNull() ?: 8009
                CastDevice(
                    id = "static:$host:$port",
                    name = name,
                    model = null,
                    host = host,
                    port = port,
                    isGroup = false,
                    source = "static",
                )
            }

    private companion object {
        const val SERVICE_TYPE = "_googlecast._tcp.local."
    }
}
