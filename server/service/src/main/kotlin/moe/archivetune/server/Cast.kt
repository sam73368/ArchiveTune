package moe.archivetune.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class CastMessage(
    val sourceId: String,
    val destinationId: String,
    val namespace: String,
    val payload: String,
)

object CastCodec {
    fun encode(message: CastMessage): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarintField(out, 1, 0)
        writeStringField(out, 2, message.sourceId)
        writeStringField(out, 3, message.destinationId)
        writeStringField(out, 4, message.namespace)
        writeVarintField(out, 5, 0)
        writeStringField(out, 6, message.payload)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): CastMessage {
        var index = 0
        val strings = HashMap<Int, String>()

        fun readVarint(): Long {
            var shift = 0
            var result = 0L
            while (true) {
                val b = bytes[index++].toInt() and 0xFF
                result = result or ((b and 0x7F).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        while (index < bytes.size) {
            val key = readVarint().toInt()
            val field = key ushr 3
            when (key and 7) {
                0 -> readVarint()
                2 -> {
                    val length = readVarint().toInt()
                    strings[field] = String(bytes, index, length, Charsets.UTF_8)
                    index += length
                }
                1 -> index += 8
                5 -> index += 4
                else -> error("unsupported wire type in cast message")
            }
        }
        return CastMessage(
            sourceId = strings[2].orEmpty(),
            destinationId = strings[3].orEmpty(),
            namespace = strings[4].orEmpty(),
            payload = strings[6].orEmpty(),
        )
    }

    private fun writeVarint(
        out: ByteArrayOutputStream,
        value: Long,
    ) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                out.write(v.toInt())
                return
            }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    private fun writeVarintField(
        out: ByteArrayOutputStream,
        field: Int,
        value: Long,
    ) {
        writeVarint(out, (field shl 3).toLong())
        writeVarint(out, value)
    }

    private fun writeStringField(
        out: ByteArrayOutputStream,
        field: Int,
        value: String,
    ) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarint(out, ((field shl 3) or 2).toLong())
        writeVarint(out, bytes.size.toLong())
        out.write(bytes)
    }
}

data class CastTrack(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String?,
    val thumbnail: String?,
    val durationSeconds: Int?,
)

@kotlinx.serialization.Serializable
data class CastStatus(
    val device: String,
    val connected: Boolean,
    val playerState: String?,
    val title: String?,
    val artist: String?,
    val album: String?,
    val thumbnail: String?,
    val currentTimeSeconds: Double?,
    val durationSeconds: Double?,
    val volume: Double?,
    val muted: Boolean?,
    val queueIndex: Int?,
    val queueSize: Int?,
)

private const val NS_CONNECTION = "urn:x-cast:com.google.cast.tp.connection"
private const val NS_HEARTBEAT = "urn:x-cast:com.google.cast.tp.heartbeat"
private const val NS_RECEIVER = "urn:x-cast:com.google.cast.receiver"
private const val NS_MEDIA = "urn:x-cast:com.google.cast.media"
private const val DEFAULT_MEDIA_RECEIVER = "CC1AD845"
private const val SENDER_ID = "sender-archivetune"
private const val RECEIVER_ID = "receiver-0"

class CastSession(
    val device: CastDevice,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requestIds = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
    private val writeLock = Any()
    private var socket: SSLSocket? = null
    private var output: DataOutputStream? = null
    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile var transportId: String? = null
        private set

    @Volatile private var mediaSessionId: Long? = null

    @Volatile private var lastMediaStatus: JsonObject? = null

    @Volatile private var lastReceiverStatus: JsonObject? = null

    @Volatile private var queueItemIds: List<Int> = emptyList()

    @Volatile var localAddress: String? = null
        private set

    val isConnected: Boolean get() = socket?.isClosed == false && readerJob?.isActive == true

    suspend fun connect() =
        withContext(Dispatchers.IO) {
            if (isConnected) return@withContext
            close()
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf<TrustManager>(TrustAll), SecureRandom())
            val raw = context.socketFactory.createSocket() as SSLSocket
            raw.connect(InetSocketAddress(device.host, device.port), 5_000)
            raw.soTimeout = 0
            raw.startHandshake()
            socket = raw
            localAddress = raw.localAddress.hostAddress
            output = DataOutputStream(raw.outputStream)
            val input = DataInputStream(raw.inputStream)
            readerJob =
                scope.launch {
                    try {
                        while (isActive) {
                            val length = input.readInt()
                            val bytes = ByteArray(length)
                            input.readFully(bytes)
                            handle(CastCodec.decode(bytes))
                        }
                    } catch (error: Exception) {
                        System.err.println("cast ${device.name}: connection closed ($error)")
                    } finally {
                        transportId = null
                        mediaSessionId = null
                        pending.values.forEach { it.cancel() }
                        pending.clear()
                    }
                }
            send(RECEIVER_ID, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") })
            heartbeatJob =
                scope.launch {
                    while (isActive) {
                        delay(5_000)
                        runCatching { send(RECEIVER_ID, NS_HEARTBEAT, buildJsonObject { put("type", "PING") }) }
                    }
                }
        }

    fun close() {
        heartbeatJob?.cancel()
        readerJob?.cancel()
        runCatching { socket?.close() }
        socket = null
        transportId = null
        mediaSessionId = null
    }

    private fun send(
        destination: String,
        namespace: String,
        payload: JsonObject,
    ) {
        val bytes = CastCodec.encode(CastMessage(SENDER_ID, destination, namespace, payload.toString()))
        synchronized(writeLock) {
            val out = output ?: error("not connected")
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
        }
    }

    private suspend fun request(
        destination: String,
        namespace: String,
        payload: JsonObject,
        timeoutMs: Long = 10_000,
    ): JsonObject? {
        val id = requestIds.getAndIncrement()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        val withId = JsonObject(payload + ("requestId" to JsonPrimitive(id)))
        send(destination, namespace, withId)
        return withTimeoutOrNull(timeoutMs) { deferred.await() }.also { pending.remove(id) }
    }

    private fun handle(message: CastMessage) {
        val payload = runCatching { json.parseToJsonElement(message.payload).jsonObject }.getOrNull() ?: return
        val type = payload["type"]?.jsonPrimitive?.contentOrNull
        when (message.namespace) {
            NS_HEARTBEAT -> if (type == "PING") send(message.sourceId, NS_HEARTBEAT, buildJsonObject { put("type", "PONG") })
            NS_CONNECTION -> if (type == "CLOSE" && message.sourceId == transportId) {
                transportId = null
                mediaSessionId = null
            }
            NS_RECEIVER -> if (type == "RECEIVER_STATUS") lastReceiverStatus = payload
            NS_MEDIA -> if (type == "MEDIA_STATUS") {
                val status = payload["status"]?.jsonArray?.firstOrNull()?.jsonObject
                if (status != null) {
                    mediaSessionId = status["mediaSessionId"]?.jsonPrimitive?.longOrNull ?: mediaSessionId
                    lastMediaStatus = mergeMediaStatus(lastMediaStatus, status)
                    status["items"]?.jsonArray?.let { items ->
                        queueItemIds = items.mapNotNull { it.jsonObject["itemId"]?.jsonPrimitive?.intOrNull }
                    }
                } else {
                    mediaSessionId = null
                    lastMediaStatus = null
                }
            }
        }
        payload["requestId"]?.jsonPrimitive?.intOrNull?.let { id -> pending.remove(id)?.complete(payload) }
    }

    private fun mergeMediaStatus(
        previous: JsonObject?,
        next: JsonObject,
    ): JsonObject {
        if (previous == null || next.containsKey("media")) return next
        val media = previous["media"] ?: return next
        return JsonObject(next + ("media" to media))
    }

    private suspend fun ensureMediaApp(): String {
        connect()
        transportId?.let { return it }
        val status =
            request(RECEIVER_ID, NS_RECEIVER, buildJsonObject { put("type", "GET_STATUS") })
                ?: error("${device.name} does not answer")
        val running = runningMediaApp(status)
        val app =
            running ?: run {
                val launched =
                    request(
                        RECEIVER_ID,
                        NS_RECEIVER,
                        buildJsonObject {
                            put("type", "LAUNCH")
                            put("appId", DEFAULT_MEDIA_RECEIVER)
                        },
                        timeoutMs = 20_000,
                    ) ?: error("${device.name} did not start the media receiver")
                runningMediaApp(launched) ?: error("media receiver missing after launch")
            }
        send(app, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") })
        transportId = app
        return app
    }

    private fun runningMediaApp(receiverStatus: JsonObject): String? =
        receiverStatus["status"]
            ?.jsonObject
            ?.get("applications")
            ?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull { it["appId"]?.jsonPrimitive?.contentOrNull == DEFAULT_MEDIA_RECEIVER }
            ?.get("transportId")
            ?.jsonPrimitive
            ?.contentOrNull

    suspend fun playQueue(
        tracks: List<CastTrack>,
        startIndex: Int,
        streamUrl: (CastTrack, String) -> String,
    ) {
        require(tracks.isNotEmpty())
        val transport = ensureMediaApp()
        val host = localAddress ?: error("unknown local address")
        val items =
            buildJsonArray {
                tracks.forEach { track ->
                    add(
                        buildJsonObject {
                            put("media", mediaInfo(track, streamUrl(track, host)))
                            put("autoplay", true)
                            put("preloadTime", 15)
                        },
                    )
                }
            }
        val response =
            request(
                transport,
                NS_MEDIA,
                buildJsonObject {
                    put("type", "QUEUE_LOAD")
                    put("items", items)
                    put("startIndex", startIndex.coerceIn(0, tracks.lastIndex))
                    put("repeatMode", "REPEAT_OFF")
                },
                timeoutMs = 20_000,
            )
        val type = response?.get("type")?.jsonPrimitive?.contentOrNull
        if (type == "MEDIA_STATUS") return
        System.err.println("cast ${device.name}: QUEUE_LOAD answered $type, falling back to LOAD")
        val track = tracks[startIndex.coerceIn(0, tracks.lastIndex)]
        val load =
            request(
                transport,
                NS_MEDIA,
                buildJsonObject {
                    put("type", "LOAD")
                    put("media", mediaInfo(track, streamUrl(track, host)))
                    put("autoplay", true)
                    put("currentTime", 0)
                },
                timeoutMs = 20_000,
            )
        val loadType = load?.get("type")?.jsonPrimitive?.contentOrNull
        if (loadType != "MEDIA_STATUS") error("${device.name} refused the media ($loadType)")
    }

    suspend fun enqueue(
        tracks: List<CastTrack>,
        streamUrl: (CastTrack, String) -> String,
    ) {
        val transport = ensureMediaApp()
        val session = mediaSessionId ?: error("nothing is playing on ${device.name}")
        val host = localAddress ?: error("unknown local address")
        request(
            transport,
            NS_MEDIA,
            buildJsonObject {
                put("type", "QUEUE_INSERT")
                put("mediaSessionId", session)
                put(
                    "items",
                    buildJsonArray {
                        tracks.forEach { track ->
                            add(
                                buildJsonObject {
                                    put("media", mediaInfo(track, streamUrl(track, host)))
                                    put("autoplay", true)
                                    put("preloadTime", 15)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    private fun mediaInfo(
        track: CastTrack,
        url: String,
    ): JsonObject =
        buildJsonObject {
            put("contentId", url)
            put("contentUrl", url)
            put("streamType", "BUFFERED")
            put("contentType", "audio/mp4")
            track.durationSeconds?.let { put("duration", it) }
            put(
                "metadata",
                buildJsonObject {
                    put("metadataType", 3)
                    put("title", track.title)
                    put("artist", track.artists.joinToString(", "))
                    track.album?.let { put("albumName", it) }
                    track.thumbnail?.let { thumb ->
                        put("images", buildJsonArray { add(buildJsonObject { put("url", thumb) }) })
                    }
                },
            )
        }

    suspend fun control(
        action: String,
        value: Double? = null,
    ) {
        if (action == "volume" || action == "mute" || action == "unmute") {
            connect()
            val volume =
                buildJsonObject {
                    when (action) {
                        "volume" -> put("level", (value ?: 0.5).coerceIn(0.0, 1.0))
                        "mute" -> put("muted", true)
                        else -> put("muted", false)
                    }
                }
            request(
                RECEIVER_ID,
                NS_RECEIVER,
                buildJsonObject {
                    put("type", "SET_VOLUME")
                    put("volume", volume)
                },
            )
            return
        }
        if (action == "stop_app") {
            connect()
            val session = lastReceiverStatus?.get("status")?.jsonObject?.get("applications")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("sessionId")?.jsonPrimitive?.contentOrNull
            if (session != null) {
                request(RECEIVER_ID, NS_RECEIVER, buildJsonObject {
                    put("type", "STOP")
                    put("sessionId", session)
                })
            }
            transportId = null
            mediaSessionId = null
            return
        }
        val transport = ensureMediaApp()
        if (mediaSessionId == null) {
            request(transport, NS_MEDIA, buildJsonObject { put("type", "GET_STATUS") })
        }
        val session = mediaSessionId ?: error("nothing is playing on ${device.name}")
        val payload =
            buildJsonObject {
                when (action) {
                    "pause" -> put("type", "PAUSE")
                    "resume", "play" -> put("type", "PLAY")
                    "stop" -> put("type", "STOP")
                    "next" -> {
                        put("type", "QUEUE_UPDATE")
                        put("jump", 1)
                    }
                    "previous" -> {
                        put("type", "QUEUE_UPDATE")
                        put("jump", -1)
                    }
                    "seek" -> {
                        put("type", "SEEK")
                        put("currentTime", value ?: 0.0)
                    }
                    else -> error("unknown action $action")
                }
                put("mediaSessionId", session)
            }
        request(transport, NS_MEDIA, payload)
    }

    suspend fun status(): CastStatus {
        runCatching {
            connect()
            request(RECEIVER_ID, NS_RECEIVER, buildJsonObject { put("type", "GET_STATUS") }, timeoutMs = 4_000)
            lastReceiverStatus?.let { runningMediaApp(it) }?.let { app ->
                if (transportId != app) {
                    send(app, NS_CONNECTION, buildJsonObject { put("type", "CONNECT") })
                    transportId = app
                }
                request(app, NS_MEDIA, buildJsonObject { put("type", "GET_STATUS") }, timeoutMs = 4_000)
            }
        }
        val media = lastMediaStatus
        val metadata = media?.get("media")?.jsonObject?.get("metadata")?.jsonObject
        val volume = lastReceiverStatus?.get("status")?.jsonObject?.get("volume")?.jsonObject
        val currentItemId = media?.get("currentItemId")?.jsonPrimitive?.intOrNull
        val index = currentItemId?.let { queueItemIds.indexOf(it) }?.takeIf { it >= 0 }
        return CastStatus(
            device = device.name,
            connected = isConnected,
            playerState = media?.get("playerState")?.jsonPrimitive?.contentOrNull,
            title = metadata?.get("title")?.jsonPrimitive?.contentOrNull,
            artist = metadata?.get("artist")?.jsonPrimitive?.contentOrNull,
            album = metadata?.get("albumName")?.jsonPrimitive?.contentOrNull,
            thumbnail =
                (metadata?.get("images") as? JsonArray)
                    ?.firstOrNull()
                    ?.jsonObject
                    ?.get("url")
                    ?.jsonPrimitive
                    ?.contentOrNull,
            currentTimeSeconds = media?.get("currentTime")?.jsonPrimitive?.doubleOrNull,
            durationSeconds = media?.get("media")?.jsonObject?.get("duration")?.jsonPrimitive?.doubleOrNull,
            volume = volume?.get("level")?.jsonPrimitive?.doubleOrNull,
            muted = volume?.get("muted")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            queueIndex = index,
            queueSize = queueItemIds.size.takeIf { it > 0 },
        )
    }

    private object TrustAll : X509TrustManager {
        override fun checkClientTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun checkServerTrusted(
            chain: Array<out X509Certificate>?,
            authType: String?,
        ) = Unit

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

class CastController(
    private val discovery: CastDiscovery,
) {
    private val sessions = ConcurrentHashMap<String, CastSession>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun devices(): List<CastDevice> = discovery.devices()

    fun find(name: String?): CastDevice? {
        val all = devices()
        if (name.isNullOrBlank()) return all.singleOrNull()
        val wanted = normalizeName(name)
        return all.firstOrNull { normalizeName(it.name) == wanted || it.id == name }
            ?: all.firstOrNull { normalizeName(it.name).contains(wanted) || wanted.contains(normalizeName(it.name)) }
    }

    suspend fun <T> withSession(
        device: CastDevice,
        block: suspend (CastSession) -> T,
    ): T {
        val lock = locks.getOrPut(device.id) { Mutex() }
        return lock.withLock {
            val session =
                sessions[device.id]?.takeIf { it.device.host == device.host && it.device.port == device.port }
                    ?: CastSession(device).also { sessions[device.id] = it }
            try {
                block(session)
            } catch (error: Exception) {
                session.close()
                sessions.remove(device.id)
                throw error
            }
        }
    }

    private fun normalizeName(value: String): String =
        java.text.Normalizer
            .normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
}
