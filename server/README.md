# ArchiveTune Server

Headless companion for the ArchiveTune Android app. It reuses the app's `core` module (the
YouTube Music client and the SimpMusic stream extractor) and exposes a small HTTP API, meant to
run in Docker on a home server and be driven by Home Assistant.

This directory is an independent Gradle build (`server/settings.gradle.kts`). The Android app
does not depend on it and never builds it.

## Endpoints

- `GET /` — test page: search a song and play it in the browser.
- `GET /api/health` — liveness probe, never requires the token.
- `GET /api/search?q=...&type=song|album` — search results as JSON.
- `GET /api/resolve?id=VIDEO_ID` — the audio format the server would stream.
- `GET /stream/VIDEO_ID` — audio proxy with `Range` support, usable as a Cast / DLNA media URL.

When `ARCHIVETUNE_TOKEN` is set, every endpoint except `/` and `/api/health` needs
`Authorization: Bearer <token>` or `?token=<token>` (Cast receivers cannot send headers).

## Run

```
docker compose up -d
```

Open `http://<server>:8780/?token=<token>`.

## Build locally

```
docker build -f server/Dockerfile -t archivetune-server .
```
