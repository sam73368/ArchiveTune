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
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.SongItem
import java.security.MessageDigest

private const val VERSION = "0.1.0"
private const val DEFAULT_PORT = 8780
private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private const val BROWSER_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

fun main() {
    val port = System.getenv("ARCHIVETUNE_PORT")?.toIntOrNull() ?: DEFAULT_PORT
    val token = System.getenv("ARCHIVETUNE_TOKEN")
    if (token.isNullOrBlank()) {
        System.err.println("ARCHIVETUNE_TOKEN is not set: the API is open to everything that can reach port $port")
    }
    embeddedServer(CIO, port = port, host = "0.0.0.0") { serverModule(token) }.start(wait = true)
}

fun Application.serverModule(token: String?) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                encodeDefaults = true
            },
        )
    }
    val startedAt = System.currentTimeMillis()
    val resolver = StreamResolver()
    val upstream = HttpClient(OkHttp)

    routing {
        get("/") {
            call.respondText(INDEX_HTML, ContentType.Text.Html)
        }

        get("/api/health") {
            call.respond(Health("ok", VERSION, (System.currentTimeMillis() - startedAt) / 1000))
        }

        get("/api/search") {
            if (!call.authorized(token)) return@get
            val query = call.request.queryParameters["q"]?.trim().orEmpty()
            if (query.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ErrorBody("missing q"))
                return@get
            }
            val type = if (call.request.queryParameters["type"] == "album") "album" else "song"
            val filter = if (type == "album") YouTube.SearchFilter.FILTER_ALBUM else YouTube.SearchFilter.FILTER_SONG
            val items =
                YouTube
                    .search(query, filter)
                    .getOrElse {
                        call.respond(HttpStatusCode.BadGateway, ErrorBody("search failed: ${it.message}"))
                        return@get
                    }.items
            call.respond(
                SearchResponse(
                    query = query,
                    type = type,
                    tracks =
                        items.filterIsInstance<SongItem>().map { song ->
                            TrackResult(
                                id = song.id,
                                title = song.title,
                                artists = song.artists.map { it.name },
                                album = song.album?.name,
                                albumId = song.album?.id,
                                durationSeconds = song.duration,
                                thumbnail = song.thumbnail,
                                explicit = song.explicit,
                            )
                        },
                    albums =
                        items.filterIsInstance<AlbumItem>().map { album ->
                            AlbumResult(
                                id = album.browseId,
                                title = album.title,
                                artists = album.artists.orEmpty().map { it.name },
                                year = album.year,
                                thumbnail = album.thumbnail,
                                explicit = album.explicit,
                            )
                        },
                ),
            )
        }

        get("/api/resolve") {
            if (!call.authorized(token)) return@get
            val id = call.request.queryParameters["id"].orEmpty()
            if (!VIDEO_ID.matches(id)) {
                call.respond(HttpStatusCode.BadRequest, ErrorBody("invalid id"))
                return@get
            }
            val stream =
                resolver.resolve(id).getOrElse {
                    call.respond(HttpStatusCode.BadGateway, ErrorBody("resolve failed: ${it.message}"))
                    return@get
                }
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

        get("/stream/{id}") {
            if (!call.authorized(token)) return@get
            val id = call.parameters["id"].orEmpty()
            if (!VIDEO_ID.matches(id)) {
                call.respond(HttpStatusCode.BadRequest, ErrorBody("invalid id"))
                return@get
            }
            val stream =
                resolver.resolve(id).getOrElse {
                    call.respond(HttpStatusCode.BadGateway, ErrorBody("resolve failed: ${it.message}"))
                    return@get
                }
            val range = call.request.header(HttpHeaders.Range)
            upstream
                .prepareGet(stream.url) {
                    header(HttpHeaders.UserAgent, BROWSER_USER_AGENT)
                    header(HttpHeaders.Origin, "https://music.youtube.com")
                    header(HttpHeaders.Referrer, "https://music.youtube.com/")
                    if (range != null) header(HttpHeaders.Range, range)
                }.execute { response ->
                    if (response.status.value >= 400) {
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
                                    if (contentRange != null) append(HttpHeaders.ContentRange, contentRange)
                                }

                            override fun readFrom(): ByteReadChannel = channel
                        },
                    )
                }
        }
    }
}

private suspend fun ApplicationCall.authorized(token: String?): Boolean {
    if (token.isNullOrBlank()) return true
    val bearer = request.header(HttpHeaders.Authorization)?.removePrefix("Bearer ")?.trim()
    val provided = bearer ?: request.queryParameters["token"]
    val accepted = provided != null && MessageDigest.isEqual(provided.toByteArray(), token.toByteArray())
    if (!accepted) respond(HttpStatusCode.Unauthorized, ErrorBody("invalid or missing token"))
    return accepted
}

private val INDEX_HTML =
    """
    <!doctype html>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>ArchiveTune Server</title>
    <body style="font-family:sans-serif;max-width:640px;margin:1rem auto;padding:0 1rem">
    <h1>ArchiveTune Server</h1>
    <form id="f"><input id="q" placeholder="Artiste ou chanson" style="width:70%"> <button>Chercher</button></form>
    <audio id="a" controls style="width:100%;margin:1rem 0"></audio>
    <ul id="r" style="list-style:none;padding:0"></ul>
    <script>
    var token = new URLSearchParams(location.search).get('token') || '';
    function withToken(path) {
      if (!token) return path;
      return path + (path.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token);
    }
    document.getElementById('f').onsubmit = function (e) {
      e.preventDefault();
      var q = document.getElementById('q').value;
      fetch(withToken('/api/search?q=' + encodeURIComponent(q)))
        .then(function (r) { return r.json(); })
        .then(function (data) {
          var list = document.getElementById('r');
          list.innerHTML = '';
          (data.tracks || []).forEach(function (t) {
            var li = document.createElement('li');
            li.style.margin = '.5rem 0';
            var b = document.createElement('button');
            b.textContent = t.title + ' - ' + t.artists.join(', ');
            b.onclick = function () {
              var a = document.getElementById('a');
              a.src = withToken('/stream/' + t.id);
              a.play();
            };
            li.appendChild(b);
            list.appendChild(li);
          });
        });
    };
    </script>
    </body>
    """.trimIndent()
