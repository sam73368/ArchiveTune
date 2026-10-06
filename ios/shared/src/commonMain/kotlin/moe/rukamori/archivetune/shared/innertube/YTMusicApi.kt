/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.innertube

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** One row of a YouTube Music search result. */
data class SearchResult(
    val title: String,
    val subtitle: String,
    /** Set for playable songs/videos. */
    val videoId: String?,
    /** Set for albums, artists and playlists. */
    val browseId: String?,
    val kind: Kind,
    val thumbnailUrl: String?,
) {
    enum class Kind { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, OTHER }
}

/**
 * Minimal InnerTube (YouTube Music) client for the iOS port, step 0.
 *
 * Mirrors the WEB_REMIX client of :core (YouTubeClient.WEB_REMIX); the full :core parsers will
 * be ported into this module in the next steps.
 */
class YTMusicApi(
    private val client: HttpClient = HttpClient(),
    private val hl: String = "fr",
    private val gl: String = "CA",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): List<SearchResult> {
        val response =
            client.post("${API_URL}search?prettyPrint=false") {
                contentType(ContentType.Application.Json)
                header("X-Goog-Api-Format-Version", "1")
                header("X-YouTube-Client-Name", CLIENT_ID)
                header("X-YouTube-Client-Version", CLIENT_VERSION)
                header("Origin", ORIGIN)
                header("Referer", "$ORIGIN/")
                header("User-Agent", USER_AGENT)
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", CLIENT_NAME)
                                put("clientVersion", CLIENT_VERSION)
                                put("hl", hl)
                                put("gl", gl)
                            }
                        }
                        put("query", query)
                    }.toString(),
                )
            }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            error("YouTube Music a répondu ${response.status.value}")
        }
        val renderers = mutableListOf<JsonObject>()
        collectRenderers(json.parseToJsonElement(body), "musicResponsiveListItemRenderer", renderers)
        return renderers.mapNotNull(::toSearchResult).distinctBy { it.videoId ?: it.browseId }
    }

    private fun collectRenderers(
        element: JsonElement,
        key: String,
        out: MutableList<JsonObject>,
    ) {
        when (element) {
            is JsonObject -> {
                element.forEach { (k, v) ->
                    if (k == key && v is JsonObject) out += v else collectRenderers(v, key, out)
                }
            }
            is JsonArray -> element.forEach { collectRenderers(it, key, out) }
            else -> Unit
        }
    }

    private fun toSearchResult(renderer: JsonObject): SearchResult? {
        val columns = renderer.arr("flexColumns") ?: return null
        val title = columns.columnRuns(0)?.firstOrNull()?.str("text") ?: return null
        val subtitle = columns.columnRuns(1)?.joinToString("") { it.str("text").orEmpty() }.orEmpty()
        val videoId =
            renderer.obj("playlistItemData")?.str("videoId")
                ?: renderer.path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint")
                    ?.str("videoId")
        val browseEndpoint = renderer.path("navigationEndpoint", "browseEndpoint")
        val pageType =
            browseEndpoint
                ?.path("browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig")
                ?.str("pageType")
                .orEmpty()
        val videoType =
            renderer
                .path("overlay", "musicItemThumbnailOverlayRenderer", "content", "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "watchEndpointMusicSupportedConfigs", "watchEndpointMusicConfig")
                ?.str("musicVideoType")
                .orEmpty()
        val kind =
            when {
                videoId != null && videoType == "MUSIC_VIDEO_TYPE_ATV" -> SearchResult.Kind.SONG
                videoId != null -> SearchResult.Kind.VIDEO
                "ALBUM" in pageType -> SearchResult.Kind.ALBUM
                "ARTIST" in pageType -> SearchResult.Kind.ARTIST
                "PLAYLIST" in pageType -> SearchResult.Kind.PLAYLIST
                else -> SearchResult.Kind.OTHER
            }
        val thumbnail =
            renderer
                .path("thumbnail", "musicThumbnailRenderer", "thumbnail")
                ?.arr("thumbnails")
                ?.lastOrNull()
                ?.jsonObject
                ?.str("url")
        return SearchResult(
            title = title,
            subtitle = subtitle,
            videoId = videoId,
            browseId = browseEndpoint?.str("browseId"),
            kind = kind,
            thumbnailUrl = thumbnail,
        )
    }

    private fun JsonArray.columnRuns(index: Int): List<JsonObject>? =
        getOrNull(index)
            ?.jsonObject
            ?.path("musicResponsiveListItemFlexColumnRenderer", "text")
            ?.arr("runs")
            ?.map { it.jsonObject }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.arr(key: String): JsonArray? = (this[key] as? JsonArray)

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.path(vararg keys: String): JsonObject? {
        var current: JsonObject = this
        for (key in keys) current = current.obj(key) ?: return null
        return current
    }

    private companion object {
        // Kept in sync with YouTubeClient.WEB_REMIX in :core.
        const val CLIENT_NAME = "WEB_REMIX"
        const val CLIENT_ID = "67"
        const val CLIENT_VERSION = "1.20260213.01.00"
        const val ORIGIN = "https://music.youtube.com"
        const val API_URL = "$ORIGIN/youtubei/v1/"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    }
}
