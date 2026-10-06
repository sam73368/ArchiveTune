/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.innertube

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.models.YouTubeClient
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import moe.rukamori.archivetune.innertube.models.body.BrowseBody
import moe.rukamori.archivetune.innertube.models.body.GetQueueBody
import moe.rukamori.archivetune.innertube.models.body.GetSearchSuggestionsBody
import moe.rukamori.archivetune.innertube.models.body.NextBody
import moe.rukamori.archivetune.innertube.models.body.SearchBody
import moe.rukamori.archivetune.innertube.utils.sha1
import moe.rukamori.archivetune.innertube.utils.youtubeLoginCookieValue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Multiplatform port of :core's InnerTube request layer (JVM-only there because of OkHttp,
 * proxies and DNS-over-HTTPS). Same headers, bodies and retry policy; the HTTP engine is the
 * platform default (Darwin on iOS, OkHttp on the JVM test target).
 */
class KmpInnerTube(
    private val httpClient: HttpClient = createDefaultClient(),
) {
    var locale = YouTubeLocale(gl = "CA", hl = "fr")
    private val queueLocale = YouTubeLocale(gl = "US", hl = "en")

    var visitorData: String? = null
    var dataSyncId: String? = null
    var cookie: String? = null
    var useLoginForBrowse: Boolean = false

    @OptIn(ExperimentalTime::class)
    private fun HttpRequestBuilder.ytClient(
        client: YouTubeClient,
        setLogin: Boolean = false,
        includeVisitorData: Boolean = true,
    ) {
        val requestOrigin = client.requestOrigin()
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientId)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("X-Origin", requestOrigin)
            append("Referer", client.requestReferer())
            if (includeVisitorData) {
                visitorData?.let { append("X-Goog-Visitor-Id", it) }
            }
            if (setLogin && client.supportsCookieAuthentication) {
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    val loginCookieValue = youtubeLoginCookieValue(cookie) ?: return@let
                    val currentTime = Clock.System.now().epochSeconds
                    val sapisidHash = sha1("$currentTime $loginCookieValue $requestOrigin")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                    append("X-Goog-AuthUser", "0")
                }
            }
        }
        userAgent(client.userAgent)
        parameter("prettyPrint", false)
    }

    private suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        initialDelay: Long = 500L,
        block: suspend () -> T,
    ): T {
        var currentDelay = initialDelay
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (e: Throwable) {
                if (e is CancellationException || !e.isTransientNetworkFailure()) throw e
                attempt++
                if (attempt >= maxAttempts) throw e
                delay(currentDelay)
                currentDelay *= 2
            }
        }
    }

    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is IOException || current is HttpRequestTimeoutException) return true
            current = current.cause
        }
        return false
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ): HttpResponse =
        withRetry {
            httpClient.post("search") {
                ytClient(client = client, setLogin = useLoginForBrowse)
                setBody(
                    SearchBody(
                        context = client.toContext(locale, visitorData, if (useLoginForBrowse) dataSyncId else null),
                        query = query,
                        params = params,
                    ),
                )
                parameter("continuation", continuation)
                parameter("ctoken", continuation)
            }
        }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ): HttpResponse =
        withRetry {
            httpClient.post("browse") {
                val shouldUseLogin = setLogin || useLoginForBrowse
                ytClient(client = client, setLogin = shouldUseLogin)
                setBody(
                    BrowseBody(
                        context = client.toContext(locale, visitorData, if (shouldUseLogin) dataSyncId else null),
                        browseId = browseId,
                        params = params,
                        continuation = continuation,
                    ),
                )
            }
        }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ): HttpResponse =
        withRetry {
            httpClient.post("next") {
                ytClient(client, setLogin = true)
                setBody(
                    NextBody(
                        context = client.toContext(queueLocale, visitorData, dataSyncId),
                        videoId = videoId,
                        playlistId = playlistId,
                        playlistSetVideoId = playlistSetVideoId,
                        index = index,
                        params = params,
                        continuation = continuation,
                    ),
                )
            }
        }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ): HttpResponse =
        withRetry {
            httpClient.post("music/get_search_suggestions") {
                ytClient(client)
                setBody(
                    GetSearchSuggestionsBody(
                        context = client.toContext(locale, visitorData, null),
                        input = input,
                    ),
                )
            }
        }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ): HttpResponse =
        withRetry {
            httpClient.post("music/get_queue") {
                ytClient(client)
                setBody(
                    GetQueueBody(
                        context = client.toContext(locale, visitorData, null),
                        videoIds = videoIds,
                        playlistId = playlistId,
                    ),
                )
            }
        }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun createDefaultClient(): HttpClient =
            HttpClient {
                expectSuccess = true
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                            encodeDefaults = true
                        },
                    )
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 15000
                    connectTimeoutMillis = 10000
                    socketTimeoutMillis = 15000
                }
                defaultRequest {
                    url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
                }
            }
    }
}
