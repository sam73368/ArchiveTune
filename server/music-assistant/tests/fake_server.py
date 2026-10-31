from aiohttp import web

TRACK_A = {
    "id": "aaaaaaaaaaa",
    "title": "Alpha (Live)",
    "artists": ["Daft Punk"],
    "album": "Discovery",
    "albumId": "MPREb_disc",
    "durationSeconds": 200,
    "thumbnail": "https://img/a.jpg",
    "explicit": False,
}
TRACK_B = {
    "id": "bbbbbbbbbbb",
    "title": "Beta",
    "artists": ["Daft Punk", "Pharrell"],
    "album": None,
    "albumId": None,
    "durationSeconds": None,
    "thumbnail": None,
    "explicit": True,
}
ALBUM = {
    "id": "MPREb_disc",
    "title": "Discovery",
    "artists": ["Daft Punk"],
    "year": 2001,
    "thumbnail": "https://img/d.jpg",
    "explicit": False,
}
SINGLE = {**ALBUM, "id": "MPREb_single", "title": "One More Time", "year": 2000}
ARTIST = {"id": "UCdaftpunk", "name": "Daft Punk", "thumbnail": "https://img/dp.jpg", "subtitle": "Artiste"}


class FakeServer:
    def __init__(self, token=""):
        self.token = token
        self.calls = []
        self.stream_codec = 'audio/webm; codecs="opus"'
        self.fail_resolve = False
        self.runner = None
        self.url = ""

    def _authorized(self, request):
        if not self.token:
            return True
        bearer = request.headers.get("Authorization", "").removeprefix("Bearer ").strip()
        return (bearer or request.query.get("token")) == self.token

    def _guard(self, request):
        self.calls.append((request.path, dict(request.query)))
        if not self._authorized(request):
            return web.json_response({"error": "invalid or missing token"}, status=401)
        return None

    async def health(self, request):
        self.calls.append((request.path, dict(request.query)))
        return web.json_response({"status": "ok", "version": "0.4.0", "uptimeSeconds": 5})

    async def devices(self, request):
        return self._guard(request) or web.json_response([])

    async def search(self, request):
        denied = self._guard(request)
        if denied:
            return denied
        query = request.query.get("q", "")
        kind = request.query.get("type", "song")
        if kind == "artist":
            artists = [ARTIST] if "daft" in query.lower() else []
            return web.json_response({"query": query, "type": kind, "artists": artists})
        if kind == "album":
            return web.json_response({"query": query, "type": kind, "albums": [ALBUM]})
        if kind == "all":
            return web.json_response(
                {
                    "query": query,
                    "top": None,
                    "tracks": [TRACK_A, TRACK_B],
                    "albums": [ALBUM],
                    "artists": [ARTIST],
                }
            )
        return web.json_response({"query": query, "type": "song", "tracks": [TRACK_A, TRACK_B]})

    async def album(self, request):
        denied = self._guard(request)
        if denied:
            return denied
        return web.json_response({"album": ALBUM, "tracks": [{**TRACK_A, "albumId": None, "album": None}, TRACK_B]})

    async def artist(self, request):
        denied = self._guard(request)
        if denied:
            return denied
        return web.json_response(
            {
                "artist": ARTIST,
                "description": "French house duo",
                "topTracks": [TRACK_A],
                "albums": [ALBUM],
                "singles": [SINGLE],
                "shelves": [],
            }
        )

    async def radio(self, request):
        denied = self._guard(request)
        if denied:
            return denied
        seed = request.match_info["id"]
        if seed == "unknownxxxx":
            return web.json_response({"error": "unknown song unknownxxxx"}, status=400)
        if seed == TRACK_B["id"]:
            return web.json_response([TRACK_B, TRACK_A])
        return web.json_response([TRACK_A, TRACK_B])

    async def resolve(self, request):
        denied = self._guard(request)
        if denied:
            return denied
        if self.fail_resolve:
            return web.json_response({"error": "no playable format"}, status=502)
        track_id = request.query.get("id", "")
        return web.json_response(
            {
                "id": track_id,
                "itag": 251,
                "mimeType": self.stream_codec,
                "bitrate": 131072,
                "contentLength": 4_000_000,
                "durationMs": 200_500,
                "streamPath": f"/stream/{track_id}",
            }
        )

    async def start(self):
        app = web.Application()
        app.add_routes(
            [
                web.get("/api/health", self.health),
                web.get("/api/devices", self.devices),
                web.get("/api/search", self.search),
                web.get("/api/album/{id}", self.album),
                web.get("/api/artist/{id}", self.artist),
                web.get("/api/radio/{id}", self.radio),
                web.get("/api/resolve", self.resolve),
            ]
        )
        self.runner = web.AppRunner(app)
        await self.runner.setup()
        site = web.TCPSite(self.runner, "127.0.0.1", 0)
        await site.start()
        port = self.runner.addresses[0][1]
        self.url = f"http://127.0.0.1:{port}"

    async def stop(self):
        await self.runner.cleanup()
