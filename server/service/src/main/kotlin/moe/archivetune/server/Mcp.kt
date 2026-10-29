package moe.archivetune.server

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class McpHandler(
    private val player: PlayerService,
    private val library: Library,
    private val cast: CastController,
    private val version: String,
) {
    private val json =
        Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
            prettyPrint = false
        }

    val instructions =
        """
        ArchiveTune plays music from YouTube Music on the user's Google Cast speakers (Google Home, Nest, Chromecast, speaker groups).
        Typical flow: call list_devices once to learn speaker names, then play_music with a query and a device.
        play_music type: "song" (default, starts the song then similar songs), "album" (whole album in order), "artist" (the artist's top songs).
        Use control_playback for pause, resume, next, previous, stop, seek (seconds), volume (0.0 to 1.0), mute and unmute.
        Use now_playing to answer what is playing. Device names are matched loosely (accents and case ignored).
        """.trimIndent()

    suspend fun handle(body: String): JsonElement? {
        val parsed =
            runCatching { json.parseToJsonElement(body) }.getOrElse {
                return rpcError(JsonNull, -32700, "parse error")
            }
        if (parsed is JsonArray) {
            val responses = parsed.mapNotNull { (it as? JsonObject)?.let { message -> handleOne(message) } }
            return if (responses.isEmpty()) null else JsonArray(responses)
        }
        val message = parsed as? JsonObject ?: return rpcError(JsonNull, -32600, "invalid request")
        return handleOne(message)
    }

    private suspend fun handleOne(message: JsonObject): JsonElement? {
        val id = message["id"] ?: return null
        val method = message["method"]?.jsonPrimitive?.contentOrNull ?: return rpcError(id, -32600, "missing method")
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
        return when (method) {
            "initialize" ->
                result(
                    id,
                    buildJsonObject {
                        put("protocolVersion", params["protocolVersion"]?.jsonPrimitive?.contentOrNull ?: "2025-06-18")
                        putJsonObject("capabilities") { putJsonObject("tools") { put("listChanged", false) } }
                        putJsonObject("serverInfo") {
                            put("name", "archivetune")
                            put("title", "ArchiveTune")
                            put("version", version)
                        }
                        put("instructions", instructions)
                    },
                )
            "ping" -> result(id, JsonObject(emptyMap()))
            "tools/list" -> result(id, buildJsonObject { put("tools", tools()) })
            "tools/call" -> {
                val name = params["name"]?.jsonPrimitive?.contentOrNull ?: return rpcError(id, -32602, "missing tool name")
                val arguments = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                result(id, callTool(name, arguments))
            }
            else -> rpcError(id, -32601, "method not found: $method")
        }
    }

    suspend fun callTool(
        name: String,
        args: JsonObject,
    ): JsonObject =
        try {
            val payload: JsonElement =
                when (name) {
                    "search_music" -> {
                        val query = args.string("query") ?: error("query is required")
                        when (args.string("type") ?: "song") {
                            "album" -> json.encodeToJsonElement(library.searchAlbums(query).take(10))
                            "artist" -> json.encodeToJsonElement(library.searchArtists(query).take(10))
                            else -> json.encodeToJsonElement(library.searchTracks(query).take(10))
                        }
                    }
                    "list_devices" -> {
                        json.encodeToJsonElement(cast.devices())
                    }
                    "play_music" ->
                        json.encodeToJsonElement(
                            player.play(
                                PlayRequest(
                                    query = args.string("query"),
                                    type = args.string("type"),
                                    trackId = args.string("track_id"),
                                    albumId = args.string("album_id"),
                                    artistId = args.string("artist_id"),
                                    device = args.string("device"),
                                    radio = args.boolean("radio") ?: true,
                                    enqueue = args.boolean("enqueue") ?: false,
                                ),
                            ),
                        )
                    "control_playback" ->
                        json.encodeToJsonElement(
                            player.control(
                                ControlRequest(
                                    device = args.string("device"),
                                    action = args.string("action") ?: error("action is required"),
                                    value = args["value"]?.jsonPrimitive?.doubleOrNull,
                                ),
                            ),
                        )
                    "now_playing" -> json.encodeToJsonElement(player.status(args.string("device")))
                    "get_album" -> json.encodeToJsonElement(library.album(args.string("album_id") ?: error("album_id is required")))
                    "get_artist" -> json.encodeToJsonElement(library.artist(args.string("artist_id") ?: error("artist_id is required")))
                    else -> error("unknown tool $name")
                }
            toolResult(payload.toString(), isError = false)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Exception) {
            System.err.println("mcp $name failed: $failure")
            toolResult("Error: ${failure.message ?: failure::class.simpleName}", isError = true)
        }

    private fun toolResult(
        text: String,
        isError: Boolean,
    ) = buildJsonObject {
        putJsonArray("content") {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", text)
                },
            )
        }
        put("isError", isError)
    }

    fun tools(): JsonArray =
        buildJsonArray {
            add(
                tool(
                    "search_music",
                    "Search YouTube Music. Returns up to 10 songs, albums or artists with their ids.",
                    properties = {
                        stringProp("query", "Words to search, e.g. 'Nightmare Avenged Sevenfold'")
                        enumProp("type", "What to search for", listOf("song", "album", "artist"))
                    },
                    required = listOf("query"),
                ),
            )
            add(tool("list_devices", "List the Google Cast speakers and groups the server can play on.", properties = {}, required = emptyList()))
            add(
                tool(
                    "play_music",
                    "Play music on a speaker. Give a query (and a type) or an exact id from search_music.",
                    properties = {
                        stringProp("query", "What to play, e.g. 'Bat Country Avenged Sevenfold' or 'Nightmare Avenged Sevenfold'")
                        enumProp("type", "song (default: the song then similar songs), album (whole album) or artist (top songs)", listOf("song", "album", "artist"))
                        stringProp("track_id", "Exact song id from search_music")
                        stringProp("album_id", "Exact album id from search_music")
                        stringProp("artist_id", "Exact artist id from search_music")
                        stringProp("device", "Speaker or group name from list_devices")
                        boolProp("radio", "For a song: continue with similar songs (default true)")
                        boolProp("enqueue", "Add to the current queue instead of replacing it (default false)")
                    },
                    required = listOf("device"),
                ),
            )
            add(
                tool(
                    "control_playback",
                    "Control what is playing on a speaker.",
                    properties = {
                        stringProp("device", "Speaker or group name")
                        enumProp("action", "Action", listOf("pause", "resume", "stop", "next", "previous", "seek", "volume", "mute", "unmute"))
                        numberProp("value", "Seconds for seek, 0.0 to 1.0 for volume")
                    },
                    required = listOf("device", "action"),
                ),
            )
            add(
                tool(
                    "now_playing",
                    "What is playing on a speaker: title, artist, album, state, position, volume.",
                    properties = { stringProp("device", "Speaker or group name") },
                    required = listOf("device"),
                ),
            )
            add(
                tool(
                    "get_album",
                    "Track list of an album.",
                    properties = { stringProp("album_id", "Album id from search_music") },
                    required = listOf("album_id"),
                ),
            )
            add(
                tool(
                    "get_artist",
                    "Top songs, albums and singles of an artist.",
                    properties = { stringProp("artist_id", "Artist id from search_music") },
                    required = listOf("artist_id"),
                ),
            )
        }

    private class Props {
        val entries = LinkedHashMap<String, JsonObject>()

        fun stringProp(
            name: String,
            description: String,
        ) {
            entries[name] =
                buildJsonObject {
                    put("type", "string")
                    put("description", description)
                }
        }

        fun boolProp(
            name: String,
            description: String,
        ) {
            entries[name] =
                buildJsonObject {
                    put("type", "boolean")
                    put("description", description)
                }
        }

        fun numberProp(
            name: String,
            description: String,
        ) {
            entries[name] =
                buildJsonObject {
                    put("type", "number")
                    put("description", description)
                }
        }

        fun enumProp(
            name: String,
            description: String,
            values: List<String>,
        ) {
            entries[name] =
                buildJsonObject {
                    put("type", "string")
                    put("description", description)
                    putJsonArray("enum") { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
                }
        }
    }

    private fun tool(
        name: String,
        description: String,
        properties: Props.() -> Unit,
        required: List<String>,
    ): JsonObject {
        val props = Props().apply(properties)
        return buildJsonObject {
            put("name", name)
            put("description", description)
            putJsonObject("inputSchema") {
                put("type", "object")
                put("properties", JsonObject(props.entries))
                putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.booleanOrNull

    private fun result(
        id: JsonElement,
        value: JsonElement,
    ) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", value)
    }

    private fun rpcError(
        id: JsonElement,
        code: Int,
        message: String,
    ) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        putJsonObject("error") {
            put("code", code)
            put("message", message)
        }
    }
}
