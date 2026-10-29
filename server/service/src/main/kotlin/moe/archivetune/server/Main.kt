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
                        logFailure("search '$query'", it)
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
                    logFailure("resolve $id", it)
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
                    logFailure("resolve $id", it)
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
                                    if (contentRange != null) append(HttpHeaders.ContentRange, contentRange)
                                }

                            override fun readFrom(): ByteReadChannel = channel
                        },
                    )
                }
        }
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

private val INDEX_HTML =
    """
    <!doctype html>
    <html lang="fr">
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>ArchiveTune Server</title>
    <style>
    body{font-family:system-ui,sans-serif;max-width:680px;margin:1rem auto;padding:0 1rem;background:#111;color:#eee}
    input,button,select{font-size:1rem;padding:.5rem;border-radius:8px;border:1px solid #444;background:#222;color:#eee}
    button{cursor:pointer}
    .row{display:flex;gap:.5rem;margin:.5rem 0;flex-wrap:wrap}
    .row input{flex:1;min-width:0}
    #status{min-height:1.5rem;color:#9cf;margin:.5rem 0;white-space:pre-wrap}
    #status.err{color:#f88}
    li{display:flex;gap:.75rem;align-items:center;margin:.5rem 0;padding:.5rem;background:#1b1b1b;border-radius:8px}
    li img{width:48px;height:48px;border-radius:6px;object-fit:cover}
    li .t{flex:1;min-width:0}
    li .t small{color:#aaa}
    audio{width:100%;margin:.5rem 0}
    </style>
    </head>
    <body>
    <h1>ArchiveTune Server</h1>
    <div class="row"><input id="token" type="password" placeholder="Jeton (ARCHIVETUNE_TOKEN)"><button id="save">Enregistrer</button></div>
    <form id="f" class="row"><input id="q" placeholder="Artiste, chanson ou album"><select id="type"><option value="song">Chansons</option><option value="album">Albums</option></select><button>Chercher</button></form>
    <div id="status"></div>
    <div id="now"></div>
    <audio id="a" controls></audio>
    <ul id="r" style="list-style:none;padding:0"></ul>
    <script>
    var tokenInput = document.getElementById('token');
    var statusBox = document.getElementById('status');
    var audio = document.getElementById('a');
    var params = new URLSearchParams(location.search);
    var token = params.get('token') || '';
    try { token = token || localStorage.getItem('archivetuneToken') || ''; } catch (e) {}
    tokenInput.value = token;
    document.getElementById('save').onclick = function () {
      token = tokenInput.value.trim();
      try { localStorage.setItem('archivetuneToken', token); } catch (e) {}
      setStatus('Jeton enregistré.');
    };
    function setStatus(text, isError) {
      statusBox.textContent = text;
      statusBox.className = isError ? 'err' : '';
    }
    function withToken(path) {
      if (!token) return path;
      return path + (path.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(token);
    }
    function getJson(path) {
      return fetch(withToken(path)).then(function (r) {
        return r.text().then(function (body) {
          var data = null;
          try { data = JSON.parse(body); } catch (e) {}
          if (!r.ok) {
            var reason = data && data.error ? data.error : body;
            if (r.status === 401) reason = 'jeton invalide ou manquant';
            throw new Error('HTTP ' + r.status + ' : ' + reason);
          }
          return data;
        });
      });
    }
    function playTrack(t) {
      setStatus('Préparation de « ' + t.title + ' »...');
      getJson('/api/resolve?id=' + encodeURIComponent(t.id)).then(function (info) {
        document.getElementById('now').textContent = 'En lecture : ' + t.title + ' - ' + t.artists.join(', ') + ' (' + info.mimeType.split(';')[0] + ', ' + Math.round(info.bitrate / 1000) + ' kb/s)';
        audio.src = withToken(info.streamPath);
        return audio.play();
      }).then(function () {
        setStatus('');
      }).catch(function (e) {
        setStatus('Lecture impossible : ' + e.message, true);
      });
    }
    audio.addEventListener('error', function () {
      setStatus('Le navigateur ne peut pas lire le flux (code ' + (audio.error ? audio.error.code : '?') + '). Regarde docker logs archivetune-server.', true);
    });
    function item(title, subtitle, thumb, actionLabel, action) {
      var li = document.createElement('li');
      if (thumb) { var img = document.createElement('img'); img.src = thumb; img.loading = 'lazy'; li.appendChild(img); }
      var t = document.createElement('div'); t.className = 't';
      var strong = document.createElement('div'); strong.textContent = title; t.appendChild(strong);
      var small = document.createElement('small'); small.textContent = subtitle; t.appendChild(small);
      li.appendChild(t);
      var b = document.createElement('button'); b.textContent = actionLabel; b.onclick = action; li.appendChild(b);
      return li;
    }
    document.getElementById('f').onsubmit = function (e) {
      e.preventDefault();
      var q = document.getElementById('q').value.trim();
      var type = document.getElementById('type').value;
      if (!q) return;
      var list = document.getElementById('r');
      list.innerHTML = '';
      setStatus('Recherche...');
      getJson('/api/search?type=' + type + '&q=' + encodeURIComponent(q)).then(function (data) {
        var tracks = data.tracks || [];
        var albums = data.albums || [];
        tracks.forEach(function (t) {
          list.appendChild(item(t.title, t.artists.join(', ') + (t.album ? ' - ' + t.album : ''), t.thumbnail, 'Écouter', function () { playTrack(t); }));
        });
        albums.forEach(function (a) {
          list.appendChild(item(a.title, a.artists.join(', ') + (a.year ? ' - ' + a.year : ''), a.thumbnail, 'Chercher', function () {
            document.getElementById('q').value = a.title + ' ' + a.artists.join(' ');
            document.getElementById('type').value = 'song';
          }));
        });
        setStatus(tracks.length + albums.length === 0 ? 'Aucun résultat.' : '');
      }).catch(function (e) {
        setStatus('Recherche impossible : ' + e.message, true);
      });
    };
    </script>
    </body>
    </html>
    """.trimIndent()
