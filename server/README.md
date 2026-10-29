# ArchiveTune Server

Headless companion for the ArchiveTune Android app. It reuses the app's `core` module (the
YouTube Music client and the SimpMusic stream extractor) and exposes a small HTTP API, meant to
run in Docker on a home server and be driven by Home Assistant.

This directory is an independent Gradle build (`server/settings.gradle.kts`). The Android app
does not depend on it and never builds it.

## For AI agents

- MCP (Model Context Protocol, streamable HTTP, JSON responses): `POST /mcp` (token as Bearer header or `?token=`).
  Tools: `search_music`, `list_devices`, `play_music`, `control_playback`, `now_playing`, `get_album`, `get_artist`.
- Plain-text guide: `GET /api/help` (also `/llms.txt`), OpenAPI: `GET /openapi.json`.

## Google Cast

Speakers are discovered with mDNS, so the container must use `network_mode: host`. Devices that mDNS cannot see can be
listed in `ARCHIVETUNE_CAST_DEVICES` (`Salon=192.168.0.50,Cuisine=192.168.0.51:8009`). The speakers fetch audio from
`http://<server-ip>:<port>/stream/<id>`; set `ARCHIVETUNE_BASE_URL` if that address must be forced.

## Endpoints

- `GET /` — test page: search a song and play it in the browser.
- `GET /api/health` — liveness probe, never requires the token.
- `GET /api/search?q=...&type=song|album` — search results as JSON.
- `GET /api/resolve?id=VIDEO_ID` — the audio format the server would stream.
- `GET /stream/VIDEO_ID` — audio proxy with `Range` support, usable as a Cast / DLNA media URL.

When `ARCHIVETUNE_TOKEN` is set, every endpoint except `/` and `/api/health` needs
`Authorization: Bearer <token>` or `?token=<token>` (Cast receivers cannot send headers).

## Run

The compose file uses `network_mode: host` so speakers on the LAN can reach `/stream` directly.

```
docker compose up -d
```

Open `http://<server>:8780/?token=<token>`.

## Build locally

```
docker build -f server/Dockerfile -t archivetune-server .
```
