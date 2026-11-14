/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class PlexPin(
    val id: Long,
    val code: String,
)

data class PlexConnection(
    val uri: String,
    val local: Boolean,
    val relay: Boolean,
)

data class PlexServer(
    val name: String,
    val machineId: String,
    val accessToken: String,
    val owned: Boolean,
    val connections: List<PlexConnection>,
)

data class PlexSection(
    val key: String,
    val title: String,
)

data class PlexTrack(
    val ratingKey: String,
    val title: String,
    val artist: String,
    val artistKey: String?,
    val artistThumb: String?,
    val album: String?,
    val albumKey: String?,
    val albumThumb: String?,
    val year: Int?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long?,
    val partKey: String?,
    val container: String?,
    val audioCodec: String?,
    val bitrateKbps: Int?,
    val sizeBytes: Long?,
    val albumArtist: String?,
)

class PlexTrackPage(
    val tracks: List<PlexTrack>,
    val totalSize: Int,
)

object PlexClient {
    const val PRODUCT = "ArchiveTune"
    private const val PLEX_TV = "https://plex.tv"
    private const val PAGE_SIZE = 500
    private const val PROBE_GRACE_MS = 700L

    private val json = Json { ignoreUnknownKeys = true }

    val http: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val streamHttp: OkHttpClient by lazy {
        http
            .newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val probeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val probeHttp: OkHttpClient by lazy {
        http
            .newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    fun authUrl(
        clientId: String,
        code: String,
    ): String =
        "https://app.plex.tv/auth#?clientID=${enc(clientId)}&code=${enc(code)}" +
            "&context%5Bdevice%5D%5Bproduct%5D=${enc(PRODUCT)}"

    suspend fun createPin(clientId: String): PlexPin =
        withContext(Dispatchers.IO) {
            val body =
                call(
                    request(clientId, null, "$PLEX_TV/api/v2/pins?strong=true")
                        .post(ByteArray(0).toRequestBody())
                        .build(),
                ).jsonObject
            PlexPin(
                id = body.long("id") ?: throw IOException("Plex PIN without id"),
                code = body.string("code") ?: throw IOException("Plex PIN without code"),
            )
        }

    suspend fun pinToken(
        clientId: String,
        pinId: Long,
    ): String? =
        withContext(Dispatchers.IO) {
            call(request(clientId, null, "$PLEX_TV/api/v2/pins/$pinId").get().build())
                .jsonObject
                .string("authToken")
                ?.takeIf { it.isNotBlank() }
        }

    suspend fun servers(
        clientId: String,
        accountToken: String,
    ): List<PlexServer> =
        withContext(Dispatchers.IO) {
            call(
                request(clientId, accountToken, "$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1")
                    .get()
                    .build(),
            ).jsonArray
                .mapNotNull { it as? JsonObject }
                .filter { resource -> resource.string("provides").orEmpty().split(',').contains("server") }
                .mapNotNull { resource ->
                    val token = resource.string("accessToken") ?: return@mapNotNull null
                    PlexServer(
                        name = resource.string("name").orEmpty().ifBlank { "Plex" },
                        machineId = resource.string("clientIdentifier") ?: return@mapNotNull null,
                        accessToken = token,
                        owned = resource.bool("owned") ?: false,
                        connections =
                            (resource["connections"] as? JsonArray)
                                .orEmpty()
                                .mapNotNull { it as? JsonObject }
                                .mapNotNull { connection ->
                                    val uri = connection.string("uri")?.trimEnd('/') ?: return@mapNotNull null
                                    if (!uri.startsWith("https://")) return@mapNotNull null
                                    PlexConnection(
                                        uri = uri,
                                        local = connection.bool("local") ?: false,
                                        relay = connection.bool("relay") ?: false,
                                    )
                                },
                    )
                }.filter { it.connections.isNotEmpty() }
                .sortedByDescending { it.owned }
        }

    fun connectionUris(server: PlexServer): List<String> =
        server.connections
            .sortedWith(compareBy<PlexConnection> { it.relay }.thenBy { !it.local })
            .map { it.uri }
            .distinct()

    private fun probe(
        clientId: String,
        token: String,
        uri: String,
    ): Boolean =
        runCatching {
            probeHttp
                .newCall(request(clientId, token, "$uri/identity").get().build())
                .execute()
                .use { response -> response.isSuccessful }
        }.getOrDefault(false)

    suspend fun firstReachable(
        clientId: String,
        token: String,
        uris: List<String>,
    ): String? {
        if (uris.isEmpty()) return null
        val results = java.util.concurrent.atomic.AtomicReferenceArray<Boolean?>(uris.size)
        val finished = kotlinx.coroutines.channels.Channel<Int>(uris.size)
        uris.forEachIndexed { index, uri ->
            probeScope.launch {
                results.set(index, probe(clientId, token, uri))
                finished.trySend(index)
            }
        }
        var firstSuccessAt = -1L
        var received = 0
        while (received < uris.size) {
            val best = uris.indices.firstOrNull { results.get(it) == true }
            if (best != null && (0 until best).all { results.get(it) != null }) break
            val index =
                if (firstSuccessAt < 0L) {
                    finished.receive()
                } else {
                    val left = PROBE_GRACE_MS - (System.currentTimeMillis() - firstSuccessAt)
                    if (left <= 0L) break
                    withTimeoutOrNull(left) { finished.receive() } ?: break
                }
            received++
            if (results.get(index) == true && firstSuccessAt < 0L) firstSuccessAt = System.currentTimeMillis()
        }
        return uris.indices.firstOrNull { results.get(it) == true }?.let { uris[it] }
    }

    suspend fun musicSections(
        clientId: String,
        baseUri: String,
        token: String,
    ): List<PlexSection> =
        withContext(Dispatchers.IO) {
            container(call(request(clientId, token, "$baseUri/library/sections").get().build()))
                ?.get("Directory")
                ?.let { it as? JsonArray }
                .orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it.string("type") == "artist" }
                .mapNotNull { directory ->
                    PlexSection(
                        key = directory.string("key") ?: return@mapNotNull null,
                        title = directory.string("title").orEmpty(),
                    )
                }
        }

    suspend fun trackPage(
        clientId: String,
        baseUri: String,
        token: String,
        sectionKey: String,
        start: Int,
    ): PlexTrackPage =
        withContext(Dispatchers.IO) {
            val url =
                "$baseUri/library/sections/${enc(sectionKey)}/all?type=10" +
                    "&X-Plex-Container-Start=$start&X-Plex-Container-Size=$PAGE_SIZE"
            val mediaContainer = container(call(request(clientId, token, url).get().build()))
            val tracks =
                (mediaContainer?.get("Metadata") as? JsonArray)
                    .orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .mapNotNull(::parseTrack)
            PlexTrackPage(
                tracks = tracks,
                totalSize = mediaContainer?.int("totalSize") ?: (start + tracks.size),
            )
        }

    fun partKeyBlocking(
        clientId: String,
        baseUri: String,
        token: String,
        ratingKey: String,
    ): String? {
        val metadata =
            container(call(request(clientId, token, "$baseUri/library/metadata/${enc(ratingKey)}").get().build()))
                ?.get("Metadata") as? JsonArray
        return (metadata?.firstOrNull() as? JsonObject)?.let(::parseTrack)?.partKey
    }

    fun thumbUrl(
        baseUri: String,
        token: String,
        path: String?,
        size: Int = 544,
    ): String? {
        val thumb = path?.takeIf { it.isNotBlank() } ?: return null
        return "$baseUri/photo/:/transcode?width=$size&height=$size&minSize=1&upscale=1" +
            "&url=${enc(thumb)}&X-Plex-Token=${enc(token)}"
    }

    fun streamUrl(
        baseUri: String,
        token: String,
        partKey: String,
    ): String {
        val separator = if (partKey.contains('?')) '&' else '?'
        return "$baseUri$partKey${separator}X-Plex-Token=${enc(token)}"
    }

    fun request(
        clientId: String,
        token: String?,
        url: String,
    ): Request.Builder =
        Request
            .Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("X-Plex-Product", PRODUCT)
            .header("X-Plex-Client-Identifier", clientId)
            .header("X-Plex-Platform", "Android")
            .header("X-Plex-Device-Name", PRODUCT)
            .apply { if (!token.isNullOrBlank()) header("X-Plex-Token", token) }

    private fun parseTrack(item: JsonObject): PlexTrack? {
        val ratingKey = item.string("ratingKey") ?: return null
        val part =
            (item["Media"] as? JsonArray)
                ?.firstOrNull()
                ?.let { it as? JsonObject }
        val firstPart = (part?.get("Part") as? JsonArray)?.firstOrNull() as? JsonObject
        val albumArtist = item.string("grandparentTitle")?.takeIf { it.isNotBlank() }
        return PlexTrack(
            ratingKey = ratingKey,
            title = item.string("title").orEmpty().ifBlank { "Track $ratingKey" },
            artist = item.string("originalTitle")?.takeIf { it.isNotBlank() } ?: albumArtist ?: "Unknown artist",
            artistKey = item.string("grandparentRatingKey"),
            artistThumb = item.string("grandparentThumb"),
            album = item.string("parentTitle")?.takeIf { it.isNotBlank() },
            albumKey = item.string("parentRatingKey"),
            albumThumb = item.string("parentThumb") ?: item.string("thumb"),
            year = item.int("parentYear") ?: item.int("year"),
            trackNumber = item.int("index"),
            discNumber = item.int("parentIndex"),
            durationMs = item.long("duration"),
            partKey = firstPart?.string("key"),
            container = firstPart?.string("container") ?: part?.string("container"),
            audioCodec = part?.string("audioCodec"),
            bitrateKbps = part?.int("bitrate"),
            sizeBytes = firstPart?.long("size"),
            albumArtist = albumArtist,
        )
    }

    private fun call(request: Request): JsonElement =
        http.newCall(request).execute().use { response ->
            if (response.code == 401) throw PlexUnauthorizedException()
            if (!response.isSuccessful) throw IOException("Plex HTTP ${response.code}")
            json.parseToJsonElement(response.body?.string().orEmpty())
        }

    private fun container(element: JsonElement): JsonObject? = (element as? JsonObject)?.get("MediaContainer") as? JsonObject

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun JsonObject.string(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonObject.long(key: String): Long? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull
}

class PlexUnauthorizedException : IOException("Plex token rejected")
