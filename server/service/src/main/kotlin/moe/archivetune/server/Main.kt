package moe.archivetune.server

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.YouTubeLocale
import java.security.MessageDigest

const val VERSION = "0.3.0"
private const val DEFAULT_PORT = 8780
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

fun main() {
    val port = System.getenv("ARCHIVETUNE_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val token = System.getenv("ARCHIVETUNE_TOKEN")?.takeIf { it.isNotBlank() }
    if (token == null) {
        System.err.println("ARCHIVETUNE_TOKEN is not set: the API is open to everything that can reach port $port")
    }
    YouTube.locale =
        YouTubeLocale(
            gl = System.getenv("ARCHIVETUNE_GL")?.takeIf { it.isNotBlank() } ?: "CA",
            hl = System.getenv("ARCHIVETUNE_HL")?.takeIf { it.isNotBlank() } ?: "fr",
        )
    runBlocking {
        withTimeoutOrNull(15_000) { YouTube.visitorData() }
            ?.onSuccess { YouTube.visitorData = it }
            ?.onFailure { System.err.println("visitorData lookup failed: $it") }
    }
    System.err.println("YouTube locale=${YouTube.locale} visitorData=${if (YouTube.visitorData != null) "set" else "missing"}")
    val discovery = CastDiscovery(System.getenv("ARCHIVETUNE_CAST_DEVICES"))
    discovery.start()
    embeddedServer(CIO, port = port, host = "0.0.0.0") {
        serverModule(
            token = token,
            port = port,
            discovery = discovery,
            publicBaseUrl = System.getenv("ARCHIVETUNE_BASE_URL")?.takeIf { it.isNotBlank() },
        )
    }.start(wait = true)
}

fun Application.serverModule(
    token: String?,
    port: Int,
    discovery: CastDiscovery,
    publicBaseUrl: String?,
) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                encodeDefaults = true
                ignoreUnknownKeys = true
            },
        )
    }
    val startedAt = System.currentTimeMillis()
    val resolver = StreamResolver()
    val library = Library()
    val cast = CastController(discovery)
    val player = PlayerService(library, cast, port, token, publicBaseUrl)
    val mcp = McpHandler(player, library, cast, VERSION)
    val upstream = HttpClient(OkHttp)

    routing {
        get("/") { call.respondText(WebAssets.indexHtml, ContentType.Text.Html) }
        get("/manifest.webmanifest") { call.respondText(WebAssets.manifest, ContentType.parse("application/manifest+json")) }
        get("/api/health") {
            call.respond(Health("ok", VERSION, (System.currentTimeMillis() - startedAt) / 1000))
        }
        get("/api/help") { call.respondText(AgentDocs.guide(mcp), ContentType.Text.Plain) }
        get("/llms.txt") { call.respondText(AgentDocs.guide(mcp), ContentType.Text.Plain) }
        get("/openapi.json") { call.respondText(AgentDocs.openApi, ContentType.Application.Json) }

        get("/api/search") {
            guarded(token) {
                val query = call.request.queryParameters["q"]?.trim().orEmpty()
                if (query.isEmpty()) throw BadRequest("missing q")
                val response =
                    when (val type = call.request.queryParameters["type"] ?: "song") {
                        "album" -> SearchResponse(query, type, albums = library.searchAlbums(query))
                        "artist" -> SearchResponse(query, type, artists = library.searchArtists(query))
                        else -> SearchResponse(query, "song", tracks = library.searchTracks(query))
                    }
                call.respond(response)
            }
        }

        get("/api/album/{id}") {
            guarded(token) { call.respond(library.album(call.parameters["id"].orEmpty())) }
        }

        get("/api/artist/{id}") {
            guarded(token) { call.respond(library.artist(call.parameters["id"].orEmpty())) }
        }

        get("/api/radio/{id}") {
            guarded(token) {
                val id = call.parameters["id"].orEmpty()
                val seed = library.track(id) ?: throw BadRequest("unknown song $id")
                call.respond(library.radio(seed))
            }
        }

        get("/api/devices") {
            guarded(token) {
                if (call.request.queryParameters["refresh"] == "true") discovery.refresh()
                call.respond(cast.devices())
            }
        }

        route("/api/play") {
            get {
                guarded(token) {
                    val p = call.request.queryParameters
                    call.respond(
                        player.play(
                            PlayRequest(
                                query = p["q"] ?: p["query"],
                                type = p["type"],
                                trackId = p["trackId"],
                                albumId = p["albumId"],
                                artistId = p["artistId"],
                                device = p["device"],
                                radio = p["radio"] != "false",
                                enqueue = p["enqueue"] == "true",
                            ),
                        ),
                    )
                }
            }
            post {
                guarded(token) { call.respond(player.play(call.receive<PlayRequest>())) }
            }
        }

        post("/api/play/tracks") {
            guarded(token) {
                val request = call.receive<PlayTracksRequest>()
                if (request.tracks.isEmpty()) throw BadRequest("tracks is empty")
                call.respond(player.playTracks(request.tracks, request.startIndex, request.device))
            }
        }

        get("/api/queue") {
            guarded(token) {
                val p = call.request.queryParameters
                call.respond(
                    player.buildQueue(
                        PlayRequest(
                            query = p["q"] ?: p["query"],
                            type = p["type"],
                            trackId = p["trackId"],
                            albumId = p["albumId"],
                            artistId = p["artistId"],
                            radio = p["radio"] != "false",
                        ),
                    ),
                )
            }
        }

        route("/api/control") {
            get {
                guarded(token) {
                    val p = call.request.queryParameters
                    call.respond(
                        player.control(
                            ControlRequest(
                                device = p["device"],
                                action = p["action"] ?: throw BadRequest("missing action"),
                                value = p["value"]?.toDoubleOrNull(),
                            ),
                        ),
                    )
                }
            }
            post {
                guarded(token) { call.respond(player.control(call.receive<ControlRequest>())) }
            }
        }

        get("/api/status") {
            guarded(token) { call.respond(player.status(call.request.queryParameters["device"])) }
        }

        route("/mcp") {
            post {
                if (!call.authorized(token)) return@post
                val response = mcp.handle(call.receiveText())
                if (response == null) {
                    call.respond(HttpStatusCode.Accepted, "")
                } else {
                    call.respondText(response.toString(), ContentType.Application.Json)
                }
            }
            get { call.respond(HttpStatusCode.MethodNotAllowed, ErrorBody("use POST (MCP streamable HTTP, JSON responses)")) }
            delete { call.respond(HttpStatusCode.OK, "") }
        }

        get("/api/resolve") {
            guarded(token) {
                val id = call.request.queryParameters["id"].orEmpty()
                if (!VIDEO_ID.matches(id)) throw BadRequest("invalid id")
                val stream = resolver.resolve(id).getOrThrow()
                call.respond(
                    ResolveResponse(
                        id = id,
                        itag = stream.itag,
                        mimeType = stream.mimeType,
                        bitrate = stream.bitrate,
                        contentLength = stream.contentLength,
                        durationMs = stream.durationMs,
                        streamPath = "/stream/$id",
                    ),
                )
            }
        }

        get("/stream/{id}") {
            if (!call.authorized(token)) return@get
            val id = call.parameters["id"].orEmpty().substringBefore('.')
            if (!VIDEO_ID.matches(id)) {
                call.respond(HttpStatusCode.BadRequest, ErrorBody("invalid id"))
                return@get
            }
            val stream =
                resolver.resolve(id).getOrElse {
                    logFailure("resolve $id", it)
                    call.respond(HttpStatusCode.BadGateway, ErrorBody("resolve failed: ${it.message}"))
                    return@get
                }
            val range = call.request.header(HttpHeaders.Range)
            upstream
                .prepareGet(stream.url) {
                    header(HttpHeaders.UserAgent, stream.userAgent ?: BROWSER_USER_AGENT)
                    if (stream.userAgent == null) {
                        header(HttpHeaders.Origin, "https://music.youtube.com")
                        header(HttpHeaders.Referrer, "https://music.youtube.com/")
                    }
                    if (range != null) header(HttpHeaders.Range, range)
                }.execute { response ->
                    if (response.status.value >= 400) {
                        System.err.println("stream $id: upstream answered ${response.status.value} for itag ${stream.itag}")
                        resolver.invalidate(id)
                        call.respond(HttpStatusCode.BadGateway, ErrorBody("upstream answered ${response.status.value}"))
                        return@execute
                    }
                    val channel = response.bodyAsChannel()
                    val contentRange = response.headers[HttpHeaders.ContentRange]
                    call.respond(
                        object : OutgoingContent.ReadChannelContent() {
                            override val status: HttpStatusCode = response.status
                            override val contentLength: Long? = response.contentLength()
                            override val contentType: ContentType? = response.contentType()
                            override val headers: Headers =
                                Headers.build {
                                    append(HttpHeaders.AcceptRanges, "bytes")
                                    append(HttpHeaders.AccessControlAllowOrigin, "*")
                                    if (contentRange != null) append(HttpHeaders.ContentRange, contentRange)
                                }

                            override fun readFrom(): ByteReadChannel = channel
                        },
                    )
                }
        }
    }
}

class BadRequest(
    message: String,
) : Exception(message)

private suspend fun RoutingContext.guarded(
    token: String?,
    block: suspend RoutingContext.() -> Unit,
) {
    if (!call.authorized(token)) return
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (bad: BadRequest) {
        call.respond(HttpStatusCode.BadRequest, ErrorBody(bad.message ?: "bad request"))
    } catch (error: Exception) {
        logFailure("${call.request.httpMethod.value} ${call.request.local.uri}", error)
        call.respond(HttpStatusCode.BadGateway, ErrorBody(error.message ?: error::class.simpleName ?: "error"))
    }
}

private fun logFailure(
    what: String,
    error: Throwable,
) {
    System.err.println("$what failed: $error")
    error.printStackTrace()
}

private suspend fun ApplicationCall.authorized(token: String?): Boolean {
    if (token.isNullOrBlank()) return true
    val bearer = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
    val provided = bearer ?: request.queryParameters["token"]
    val accepted = provided != null && MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())
    if (!accepted) respond(HttpStatusCode.Unauthorized, ErrorBody("invalid or missing token"))
    return accepted
}
