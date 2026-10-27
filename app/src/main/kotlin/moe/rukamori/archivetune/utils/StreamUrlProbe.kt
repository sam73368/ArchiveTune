/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.innertube.YouTube
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object StreamUrlProbe {
    enum class Result { PLAYABLE, REJECTED, UNKNOWN }

    private const val TAG = "StreamUrlProbe"
    private const val PROBE_TIMEOUT_MS = 7_000L
    private val rejectedCodes = setOf(401, 403, 404, 410)
    private val potPattern = Regex("""([?&])pot=[^&]*""")

    private val baseClient: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    fun classify(code: Int): Result =
        when {
            code in 200..299 -> Result.PLAYABLE
            code in rejectedCodes -> Result.REJECTED
            else -> Result.UNKNOWN
        }

    fun isProbeable(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        if (!httpUrl.host.endsWith("googlevideo.com")) return false
        if (httpUrl.host.startsWith("manifest.")) return false
        return !httpUrl.encodedPath.contains("/manifest/")
    }

    fun probeUrlFor(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        if (httpUrl.queryParameter("range") == null) return url
        return Regex("""([?&])range=[^&]*""").replace(url) { match -> "${match.groupValues[1]}range=0-1" }
    }

    fun potOf(url: String): String? = url.toHttpUrlOrNull()?.queryParameter("pot")?.takeIf { it.isNotBlank() }

    fun withPoToken(
        url: String,
        token: String,
    ): String {
        val encoded = URLEncoder.encode(token, "UTF-8")
        if (potPattern.containsMatchIn(url)) {
            return potPattern.replace(url) { match -> "${match.groupValues[1]}pot=$encoded" }
        }
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}pot=$encoded"
    }

    fun withoutPoToken(url: String): String {
        val removed = potPattern.replace(url) { match -> if (match.groupValues[1] == "?") "?" else "" }
        return removed.replace("?&", "?").removeSuffix("?")
    }

    suspend fun probe(url: String): Result {
        if (!isProbeable(url)) return Result.UNKNOWN
        val target = probeUrlFor(url).toHttpUrlOrNull() ?: return Result.UNKNOWN
        val request =
            StreamClientUtils
                .applyRequestProfile(
                    Request.Builder().url(target).header("Range", "bytes=0-1"),
                    StreamClientUtils.resolveRequestProfile(target),
                ).get()
                .build()
        return withContext(Dispatchers.IO) {
            try {
                baseClient
                    .newBuilder()
                    .proxy(YouTube.streamOkHttpProxy)
                    .build()
                    .newCall(request)
                    .execute()
                    .use { response ->
                        classify(response.code).also { result ->
                            Timber.tag(TAG).i(
                                "Probe c=%s itag=%s pot=%b -> HTTP %d (%s)",
                                target.queryParameter("c"),
                                target.queryParameter("itag"),
                                target.queryParameter("pot") != null,
                                response.code,
                                result,
                            )
                        }
                    }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "Stream URL probe could not complete; keeping the URL")
                Result.UNKNOWN
            }
        }
    }

    suspend fun firstPlayable(
        url: String,
        poTokens: List<String?>,
    ): String? {
        if (probe(url) != Result.REJECTED) return url
        val currentPot = potOf(url)
        val variants =
            buildList {
                poTokens
                    .filterNotNull()
                    .filter { it.isNotBlank() && it != currentPot }
                    .distinct()
                    .forEach { add(withPoToken(url, it)) }
                if (currentPot != null) add(withoutPoToken(url))
            }.distinct()
        for ((index, variant) in variants.withIndex()) {
            if (probe(variant) != Result.REJECTED) {
                Timber.tag(TAG).i("Stream URL accepted with PO token variant %d of %d", index + 1, variants.size)
                return variant
            }
        }
        return null
    }
}
