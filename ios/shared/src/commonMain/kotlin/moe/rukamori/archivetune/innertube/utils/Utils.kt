/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

// Multiplatform counterpart of :core's innertube/utils/Utils.kt (which depends on the JVM-only
// YouTube facade and java.security). The parsers compiled from :core only need parseTime().

package moe.rukamori.archivetune.innertube.utils

fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    return buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xFF
            append(digits[value ushr 4])
            append(digits[value and 0x0F])
        }
    }
}

/** SHA-1 (used for the SAPISIDHASH auth header). Pure Kotlin so it runs on iOS. */
fun sha1(str: String): String = Sha1.digest(str.encodeToByteArray()).toHex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split(";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapNotNull { part ->
            val splitIndex = part.indexOf('=')
            if (splitIndex == -1) {
                null
            } else {
                val key = part.substring(0, splitIndex).trim()
                if (key.isEmpty()) null else key to part.substring(splitIndex + 1).trim()
            }
        }.toMap()

fun hasYouTubeLoginCookie(cookie: String?): Boolean = youtubeLoginCookieValue(cookie) != null

fun youtubeLoginCookieValue(cookie: String?): String? {
    val cookieMap = cookie?.let(::parseCookieString).orEmpty()
    return YOUTUBE_LOGIN_COOKIE_NAMES.firstNotNullOfOrNull { cookieName ->
        cookieMap[cookieName]?.takeIf(String::isNotBlank)
    }
}

private val YOUTUBE_LOGIN_COOKIE_NAMES =
    listOf(
        "SAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PAPISID",
    )

fun String.parseTime(): Int? {
    val normalized =
        buildString(length) {
            for (char in this@parseTime) {
                val digit = char.digitToIntOrNull() ?: -1
                when {
                    digit >= 0 -> append(digit)
                    char.isDurationSeparator() -> append(':')
                    char.isIgnorableDurationChar() -> Unit
                    else -> return null
                }
            }
        }

    val parts = normalized.split(':')
    if (parts.any { it.isBlank() || it.length > 3 }) return null
    if (parts.size !in 2..3) return null
    if (parts.drop(1).any { it.length !in 1..2 }) return null

    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.drop(1).any { it !in 0..59 }) return null

    return when (values.size) {
        2 -> values[0] * 60 + values[1]
        3 -> values[0] * 3600 + values[1] * 60 + values[2]
        else -> null
    }
}

private fun Char.isDurationSeparator(): Boolean =
    this == ':' ||
        this == '.' ||
        this == ',' ||
        this == '：' ||
        this == '．' ||
        this == '﹕' ||
        this == '꞉' ||
        this == '∶' ||
        this == '٫'

private fun Char.isIgnorableDurationChar(): Boolean = isWhitespace() || category == CharCategory.FORMAT

fun isPrivateId(browseId: String): Boolean = browseId.contains("privately")

/** Minimal SHA-1 (FIPS 180-4). */
private object Sha1 {
    fun digest(message: ByteArray): ByteArray {
        var h0 = 0x67452301
        var h1 = 0xEFCDAB89.toInt()
        var h2 = 0x98BADCFE.toInt()
        var h3 = 0x10325476
        var h4 = 0xC3D2E1F0.toInt()

        val bitLength = message.size.toLong() * 8
        val paddedLength = ((message.size + 8) / 64 + 1) * 64
        val padded = ByteArray(paddedLength)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) {
            padded[paddedLength - 1 - i] = (bitLength ushr (8 * i)).toByte()
        }

        val w = IntArray(80)
        for (chunk in 0 until paddedLength / 64) {
            val offset = chunk * 64
            for (i in 0 until 16) {
                w[i] = ((padded[offset + 4 * i].toInt() and 0xFF) shl 24) or
                    ((padded[offset + 4 * i + 1].toInt() and 0xFF) shl 16) or
                    ((padded[offset + 4 * i + 2].toInt() and 0xFF) shl 8) or
                    (padded[offset + 4 * i + 3].toInt() and 0xFF)
            }
            for (i in 16 until 80) {
                w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotateLeft(1)
            }
            var a = h0
            var b = h1
            var c = h2
            var d = h3
            var e = h4
            for (i in 0 until 80) {
                val (f, k) =
                    when (i) {
                        in 0..19 -> ((b and c) or (b.inv() and d)) to 0x5A827999
                        in 20..39 -> (b xor c xor d) to 0x6ED9EBA1
                        in 40..59 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                        else -> (b xor c xor d) to 0xCA62C1D6.toInt()
                    }
                val temp = a.rotateLeft(5) + f + e + k + w[i]
                e = d
                d = c
                c = b.rotateLeft(30)
                b = a
                a = temp
            }
            h0 += a
            h1 += b
            h2 += c
            h3 += d
            h4 += e
        }

        val out = ByteArray(20)
        intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { index, value ->
            out[index * 4] = (value ushr 24).toByte()
            out[index * 4 + 1] = (value ushr 16).toByte()
            out[index * 4 + 2] = (value ushr 8).toByte()
            out[index * 4 + 3] = value.toByte()
        }
        return out
    }
}
