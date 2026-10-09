/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import android.content.Context
import kotlinx.coroutines.flow.first
import moe.rukamori.archivetune.constants.AiApiKeyKey
import moe.rukamori.archivetune.constants.AiCustomEndpointKey
import moe.rukamori.archivetune.constants.AiCustomModelKey
import moe.rukamori.archivetune.constants.AiProvider
import moe.rukamori.archivetune.constants.AiProviderKey
import moe.rukamori.archivetune.constants.AiSelectedModelKey
import moe.rukamori.archivetune.extensions.toEnum
import moe.rukamori.archivetune.playlist.CrossServicePlaylistImporter.ForeignTrack
import moe.rukamori.archivetune.utils.dataStore
import org.json.JSONObject

/** Turns a free-text description ("rainy night jazz") into a list of songs to look up. */
object AiPlaylistGenerator {
    data class Draft(
        val title: String,
        val tracks: List<ForeignTrack>,
    )

    suspend fun isConfigured(context: Context): Boolean = readConfig(context).canCallApi

    suspend fun generate(
        context: Context,
        description: String,
        trackCount: Int = DEFAULT_TRACK_COUNT,
    ): Draft {
        val config = readConfig(context)
        if (!config.canCallApi) throw AiServiceException("AI provider is not configured")

        val response =
            AiRateLimiter.withLimit(AiRateLimiter.Feature.AI_PLAYLIST) {
                AiTextService.complete(
                    config = config,
                    systemPrompt =
                        """
                        You are a music curator for ArchiveTune.
                        The user describes a mood, activity, era, genre or theme. Build a playlist of exactly $trackCount real, existing songs that fit it, ordered so it flows well from start to finish.
                        Only use songs you are sure exist and that are commonly available on streaming services. Never invent songs. Do not repeat a song. Mix well-known tracks with a few lesser-known ones, and avoid more than 2 songs by the same artist.
                        Give the playlist a short, appealing title (max 6 words) in the same language as the user's description.
                        Return JSON only, matching this schema: {"title":"Playlist title","tracks":[{"title":"Song Title","artist":"Artist Name"}]}.
                        """.trimIndent(),
                    userPrompt = description.trim().take(MAX_DESCRIPTION_LENGTH),
                    temperature = 0.7,
                    maxTokens = 3072,
                )
            }
        return parse(response, trackCount)
    }

    internal fun parse(
        response: String,
        trackCount: Int,
    ): Draft {
        val body = response.substringAfter('{', "").substringBeforeLast('}', "")
        val json =
            runCatching { JSONObject("{$body}") }
                .getOrElse { throw AiServiceException("The AI answer could not be read") }
        val array = json.optJSONArray("tracks")
        val tracks =
            buildList {
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val title = item.optString("title").trim()
                        val artist = item.optString("artist").trim()
                        if (title.isNotEmpty() && artist.isNotEmpty()) add(ForeignTrack(title = title, artist = artist))
                    }
                }
            }.distinctBy { "${it.artist?.lowercase()}|${it.title.lowercase()}" }
                .take(trackCount)
        if (tracks.isEmpty()) throw AiServiceException("The AI returned no songs")
        return Draft(title = json.optString("title").trim(), tracks = tracks)
    }

    private suspend fun readConfig(context: Context): AiServiceConfig {
        val prefs = context.dataStore.data.first()
        val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
        return AiServiceConfig(
            provider = provider,
            apiKey = prefs[AiApiKeyKey].orEmpty(),
            customEndpoint = prefs[AiCustomEndpointKey].orEmpty(),
            model =
                if (provider == AiProvider.CUSTOM) {
                    prefs[AiCustomModelKey].orEmpty()
                } else {
                    prefs[AiSelectedModelKey].orEmpty()
                },
        )
    }

    private const val DEFAULT_TRACK_COUNT = 25
    private const val MAX_DESCRIPTION_LENGTH = 400
}
