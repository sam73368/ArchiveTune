package moe.archivetune.server

object AgentDocs {
    fun guide(mcp: McpHandler): String =
        """
        # ArchiveTune Server $VERSION

        INSTRUCTIONS_PLACEHOLDER

        ## Authentication
        Every endpoint except /, /api/health, /api/help, /llms.txt and /openapi.json needs the server token,
        either as the header `Authorization: Bearer <token>` or as the query parameter `?token=<token>`.

        ## MCP (recommended for AI agents)
        Endpoint: POST /mcp  (Model Context Protocol, streamable HTTP transport, JSON responses)
        Example URL for a client that cannot send headers: http://<server>:<port>/mcp?token=<token>
        Legacy SSE transport (used by Home Assistant's "Model Context Protocol" integration):
          GET http://<server>:<port>/sse?token=<token>
        Tools: search_music, list_devices, play_music, control_playback, now_playing, get_album, get_artist

        ## REST (simple GET calls also work, handy for Home Assistant rest_command)
        GET  /api/devices                                   speakers and groups
        GET  /api/search?q=<words>&type=song|album|artist   search
        GET  /api/play?q=<words>&type=song|album|artist&device=<name>[&enqueue=true][&radio=false]
        POST /api/play  {"query":"...","type":"album","device":"Salon"}
        GET  /api/control?device=<name>&action=pause|resume|stop|next|previous|seek|volume|mute|unmute[&value=0.4]
        GET  /api/status?device=<name>                     now playing
        GET  /api/album/<albumId>   GET /api/artist/<artistId>   GET /api/radio/<songId>
        GET  /stream/<songId>                                audio stream (used by the speakers)

        ## Examples
        Play an album:  GET /api/play?q=Nightmare%20Avenged%20Sevenfold&type=album&device=Salon
        Pause:          GET /api/control?device=Salon&action=pause
        Volume 40 %:    GET /api/control?device=Salon&action=volume&value=0.4
        """.trimIndent().replace("INSTRUCTIONS_PLACEHOLDER", mcp.instructions)

    val openApi: String =
        """
        {
          "openapi": "3.1.0",
          "info": {"title": "ArchiveTune Server", "version": "$VERSION", "description": "Search YouTube Music and play it on Google Cast speakers."},
          "components": {"securitySchemes": {"bearer": {"type": "http", "scheme": "bearer"}, "query": {"type": "apiKey", "in": "query", "name": "token"}}},
          "security": [{"bearer": []}, {"query": []}],
          "paths": {
            "/api/devices": {"get": {"operationId": "listDevices", "summary": "List Google Cast speakers and groups"}},
            "/api/search": {"get": {"operationId": "search", "summary": "Search songs, albums or artists",
              "parameters": [{"name": "q", "in": "query", "required": true, "schema": {"type": "string"}},
                             {"name": "type", "in": "query", "schema": {"type": "string", "enum": ["song", "album", "artist"]}}]}},
            "/api/play": {"get": {"operationId": "play", "summary": "Play a song (then similar songs), an album or an artist on a speaker",
              "parameters": [{"name": "q", "in": "query", "schema": {"type": "string"}},
                             {"name": "type", "in": "query", "schema": {"type": "string", "enum": ["song", "album", "artist"]}},
                             {"name": "device", "in": "query", "required": true, "schema": {"type": "string"}},
                             {"name": "trackId", "in": "query", "schema": {"type": "string"}},
                             {"name": "albumId", "in": "query", "schema": {"type": "string"}},
                             {"name": "artistId", "in": "query", "schema": {"type": "string"}},
                             {"name": "enqueue", "in": "query", "schema": {"type": "boolean"}},
                             {"name": "radio", "in": "query", "schema": {"type": "boolean"}}]}},
            "/api/control": {"get": {"operationId": "control", "summary": "Pause, resume, skip, seek or change the volume",
              "parameters": [{"name": "device", "in": "query", "required": true, "schema": {"type": "string"}},
                             {"name": "action", "in": "query", "required": true, "schema": {"type": "string", "enum": ["pause", "resume", "stop", "next", "previous", "seek", "volume", "mute", "unmute"]}},
                             {"name": "value", "in": "query", "schema": {"type": "number"}}]}},
            "/api/status": {"get": {"operationId": "nowPlaying", "summary": "What is playing on a speaker",
              "parameters": [{"name": "device", "in": "query", "required": true, "schema": {"type": "string"}}]}},
            "/api/album/{id}": {"get": {"operationId": "album", "summary": "Album track list",
              "parameters": [{"name": "id", "in": "path", "required": true, "schema": {"type": "string"}}]}},
            "/api/artist/{id}": {"get": {"operationId": "artist", "summary": "Artist top songs, albums and singles",
              "parameters": [{"name": "id", "in": "path", "required": true, "schema": {"type": "string"}}]}}
          }
        }
        """.trimIndent()
}
