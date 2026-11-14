/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.plex

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.SystemClock
import androidx.datastore.preferences.core.edit
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.constants.PlexAccountTokenKey
import moe.rukamori.archivetune.constants.PlexClientIdKey
import moe.rukamori.archivetune.constants.PlexServerConnectionsKey
import moe.rukamori.archivetune.constants.PlexServerMachineIdKey
import moe.rukamori.archivetune.constants.PlexServerTokenKey
import moe.rukamori.archivetune.constants.PlexServerUriKey
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import timber.log.Timber

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
    private const val TAG = "PlexSession"
    private const val VERIFY_TTL_MS = 5 * 60_000L
    private const val REFRESH_MIN_INTERVAL_MS = 30_000L
    private const val FAILURE_HOLD_MS = 8_000L

    private class ActiveBase(
        val uri: String,
        val networkHandle: Long,
        val verifiedAt: Long,
    )

    private val partKeys = ConcurrentHashMap<String, String>()
    private val lock = Any()

    @Volatile
    private var active: ActiveBase? = null
    private var lastRefreshAt = 0L
    private var failedUntil = 0L
    private var failedNetworkHandle = 0L
    private var failedMessage = ""

    enum class Problem { OFFLINE, UNREACHABLE }

    @Volatile
    private var serverOnline: Boolean? = null
    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem.asStateFlow()

    fun clientId(context: Context): String {
        context.dataStore.get(PlexClientIdKey)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = "archivetune-" + UUID.randomUUID().toString()
        runBlocking {
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
        active = null
    }

    fun invalidate() {
        active = null
        failedUntil = 0L
    }

    fun isConfigured(context: Context): Boolean = !context.dataStore.get(PlexServerTokenKey).isNullOrBlank()

    private fun networkHandle(context: Context): Long =
        runCatching {
            context.getSystemService(ConnectivityManager::class.java)?.activeNetwork?.networkHandle
        }.getOrNull() ?: 0L

    private fun storedCandidates(context: Context): List<String> =
        (
            context.dataStore
                .get(PlexServerConnectionsKey)
                .orEmpty()
                .split('\n') + context.dataStore.get(PlexServerUriKey).orEmpty()
        ).map { it.trim() }.filter { it.startsWith("https://") }.distinct()

    private fun refreshConnectionsBlocking(context: Context): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (lastRefreshAt != 0L && now - lastRefreshAt < REFRESH_MIN_INTERVAL_MS) return false
        lastRefreshAt = now
        val accountToken = context.dataStore.get(PlexAccountTokenKey)?.takeIf { it.isNotBlank() } ?: return false
        val machineId = context.dataStore.get(PlexServerMachineIdKey)?.takeIf { it.isNotBlank() } ?: return false
        val server =
            runCatching {
                runBlocking { PlexClient.servers(clientId(context), accountToken) }
            }.onFailure { Timber.tag(TAG).w(it, "Plex connection refresh failed") }
                .getOrNull()
                ?.firstOrNull { it.machineId == machineId } ?: return false
        serverOnline = server.online
        val uris = PlexClient.connectionUris(server)
        if (uris.isEmpty()) return false
        runBlocking {
            context.dataStore.edit {
                it[PlexServerConnectionsKey] = uris.joinToString("\n")
                it[PlexServerTokenKey] = server.accessToken
            }
        }
        Timber.tag(TAG).i("Plex connections refreshed: %d addresses", uris.size)
        return true
    }

    fun baseUriBlocking(
        context: Context,
        force: Boolean = false,
    ): String {
        val handle = networkHandle(context)
        fun fresh(): String? =
            active
                ?.takeIf {
                    !force &&
                        it.networkHandle == handle &&
                        SystemClock.elapsedRealtime() - it.verifiedAt < VERIFY_TTL_MS
                }?.uri
        fresh()?.let { return it }
        synchronized(lock) {
            fresh()?.let { return it }
            if (!isConfigured(context)) throw IOException("Plex server not connected")
            if (failedNetworkHandle == handle && SystemClock.elapsedRealtime() < failedUntil) {
                throw PlexUnreachableException(failedMessage)
            }
            val client = clientId(context)
            if (context.dataStore.get(PlexServerConnectionsKey).isNullOrBlank()) refreshConnectionsBlocking(context)
            fun pick(): String? {
                val token = context.dataStore.get(PlexServerTokenKey).orEmpty()
                return runBlocking { PlexClient.firstReachable(client, token, storedCandidates(context)) }
            }
            val picked =
                pick()
                    ?: if (refreshConnectionsBlocking(context)) pick() else null
            if (picked == null) {
                active = null
                val offline = serverOnline == false
                Timber.tag(TAG).w(
                    "No Plex address reachable (%d known, plex.tv says online=%s)",
                    storedCandidates(context).size,
                    serverOnline,
                )
                _problem.value = if (offline) Problem.OFFLINE else Problem.UNREACHABLE
                failedMessage =
                    if (offline) {
                        "Plex server is offline according to plex.tv"
                    } else {
                        "Plex server unreachable on this network"
                    }
                failedNetworkHandle = handle
                failedUntil = SystemClock.elapsedRealtime() + FAILURE_HOLD_MS
                throw PlexUnreachableException(failedMessage)
            }
            _problem.value = null
            if (picked != active?.uri) Timber.tag(TAG).i("Plex address in use: %s", Uri.parse(picked).host)
            if (picked != context.dataStore.get(PlexServerUriKey)) {
                runBlocking { context.dataStore.edit { it[PlexServerUriKey] = picked } }
            }
            active = ActiveBase(picked, handle, SystemClock.elapsedRealtime())
            return picked
        }
    }

    fun warmUp(context: Context) {
        if (!isConfigured(context)) return
        runCatching { baseUriBlocking(context) }
    }

    val imageHostInterceptor =
        Interceptor { chain ->
            val request = chain.request()
            val url = request.url
            val base = active?.uri?.toHttpUrlOrNull()
            val suffix = url.host.substringAfter('.', "")
            if (base != null &&
                url.host.endsWith(".plex.direct") &&
                base.host.substringAfter('.', "") == suffix &&
                (url.host != base.host || url.port != base.port)
            ) {
                chain.proceed(
                    request
                        .newBuilder()
                        .url(url.newBuilder().scheme(base.scheme).host(base.host).port(base.port).build())
                        .build(),
                )
            } else {
                chain.proceed(request)
            }
        }

    fun streamUrlBlocking(
        context: Context,
        mediaId: String,
        force: Boolean = false,
    ): String {
        val ratingKey = plexRatingKeyOf(mediaId) ?: throw IOException("Not a Plex track: $mediaId")
        val baseUri = baseUriBlocking(context, force)
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

    private fun openWith(
        dataSpec: DataSpec,
        force: Boolean,
    ): Long {
        val url = PlexSession.streamUrlBlocking(context, dataSpec.uri.toString(), force)
        opened = upstream
        return upstream.open(dataSpec.buildUpon().setUri(Uri.parse(url)).build())
    }

    override fun open(dataSpec: DataSpec): Long =
        try {
            openWith(dataSpec, force = false)
        } catch (error: IOException) {
            if (error is HttpDataSource.InvalidResponseCodeException ||
                error is PlexUnauthorizedException ||
                error is PlexUnreachableException
            ) {
                throw error
            }
            Timber.tag("PlexSession").w("Plex stream open failed (%s); trying another address", error.message)
            runCatching { upstream.close() }
            PlexSession.invalidate()
            openWith(dataSpec, force = true)
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
        private val upstreamFactory = OkHttpDataSource.Factory(PlexClient.streamHttp)

        override fun createDataSource(): DataSource = PlexDataSource(context.applicationContext, upstreamFactory.createDataSource())
    }
}
