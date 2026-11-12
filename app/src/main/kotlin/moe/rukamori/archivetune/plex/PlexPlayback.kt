/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import moe.rukamori.archivetune.constants.PlexClientIdKey
import moe.rukamori.archivetune.constants.PlexServerTokenKey
import moe.rukamori.archivetune.constants.PlexServerUriKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get

const val PLEX_MEDIA_ID_SCHEME = "plex"
private const val PLEX_TRACK_PREFIX = "$PLEX_MEDIA_ID_SCHEME://track/"
const val PLEX_ARTIST_ID_PREFIX = "LAplex"
const val PLEX_ALBUM_ID_PREFIX = "PLEXALBUM"

fun plexTrackMediaId(
    machineId: String,
    ratingKey: String,
): String = "$PLEX_TRACK_PREFIX$machineId/$ratingKey"

fun plexRatingKeyOf(mediaId: String): String? =
    mediaId
        .takeIf { it.startsWith(PLEX_TRACK_PREFIX) }
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }

fun String.isPlexMediaId(): Boolean = startsWith(PLEX_TRACK_PREFIX) && plexRatingKeyOf(this) != null

object PlexSession {
    private val partKeys = ConcurrentHashMap<String, String>()

    fun clientId(context: Context): String {
        context.dataStore.get(PlexClientIdKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = "archivetune-" + UUID.randomUUID().toString()
        kotlinx.coroutines.runBlocking {
            context.dataStore.edit { it[PlexClientIdKey] = created }
        }
        return created
    }

    fun rememberPartKey(
        ratingKey: String,
        partKey: String,
    ) {
        partKeys[ratingKey] = partKey
    }

    fun forget() {
        partKeys.clear()
    }

    fun streamUrlBlocking(
        context: Context,
        mediaId: String,
    ): String {
        val ratingKey = plexRatingKeyOf(mediaId) ?: throw IOException("Not a Plex track: $mediaId")
        val baseUri = context.dataStore.get(PlexServerUriKey)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Plex server not connected")
        val token = context.dataStore.get(PlexServerTokenKey)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Plex server not connected")
        val partKey =
            partKeys[ratingKey]
                ?: PlexClient.partKeyBlocking(clientId(context), baseUri, token, ratingKey)?.also {
                    partKeys[ratingKey] = it
                }
                ?: throw IOException("Plex track $ratingKey has no playable file")
        return PlexClient.streamUrl(baseUri, token, partKey)
    }
}

@UnstableApi
class PlexDataSource(
    private val context: Context,
    private val upstream: DataSource,
) : DataSource {
    private var opened: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val mediaId = dataSpec.uri.toString()
        val url = PlexSession.streamUrlBlocking(context, mediaId)
        opened = upstream
        return upstream.open(dataSpec.buildUpon().setUri(Uri.parse(url)).build())
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int = checkNotNull(opened).read(buffer, offset, length)

    override fun getUri(): Uri? = opened?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = opened?.responseHeaders ?: emptyMap()

    override fun close() {
        opened?.close()
        opened = null
    }

    class Factory(
        private val context: Context,
    ) : DataSource.Factory {
        private val upstreamFactory = OkHttpDataSource.Factory(PlexClient.http)

        override fun createDataSource(): DataSource = PlexDataSource(context.applicationContext, upstreamFactory.createDataSource())
    }
}
