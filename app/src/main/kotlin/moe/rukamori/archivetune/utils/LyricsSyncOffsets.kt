/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The lyrics sync offset chosen for a song (lyrics menu > "Sync offset") is remembered per song, so
 * it survives leaving the song, restarting the app and coming back later. Stored as one line per
 * song ("mediaId<TAB>offsetMs"), most recently changed last, capped at [MAX_ENTRIES].
 */
object LyricsSyncOffsets {
    private val Key = stringPreferencesKey("lyricsSyncOffsetsBySong")

    const val MAX_ENTRIES = 400
    const val MAX_OFFSET_MS = 3000

    private const val SEPARATOR = '\t'

    fun read(
        raw: String?,
        mediaId: String,
    ): Int = parse(raw)[mediaId] ?: 0

    /** [offsetMs] = 0 removes the song's entry. */
    fun update(
        raw: String?,
        mediaId: String,
        offsetMs: Int,
    ): String {
        val entries = parse(raw)
        entries.remove(mediaId)
        val clamped = offsetMs.coerceIn(-MAX_OFFSET_MS, MAX_OFFSET_MS)
        if (clamped != 0) entries[mediaId] = clamped
        return entries.entries
            .toList()
            .takeLast(MAX_ENTRIES)
            .joinToString("\n") { (id, value) -> "$id$SEPARATOR$value" }
    }

    private fun parse(raw: String?): LinkedHashMap<String, Int> {
        val result = LinkedHashMap<String, Int>()
        raw?.lineSequence()?.forEach { line ->
            val cut = line.lastIndexOf(SEPARATOR)
            if (cut <= 0) return@forEach
            val value = line.substring(cut + 1).toIntOrNull() ?: return@forEach
            result[line.substring(0, cut)] = value
        }
        return result
    }

    suspend fun load(
        context: Context,
        mediaId: String,
    ): Int = read(context.dataStore.data.map { it[Key] }.first(), mediaId)

    suspend fun save(
        context: Context,
        mediaId: String,
        offsetMs: Int,
    ) {
        context.dataStore.edit { it[Key] = update(it[Key], mediaId, offsetMs) }
    }
}

/**
 * Sync offset of [mediaId], loaded when the song changes and saved whenever it is changed. Writes
 * wait for the stored value to be loaded, so a freshly opened song never overwrites it with 0.
 */
@Composable
fun rememberPersistedLyricsSyncOffset(mediaId: String?): MutableState<Int> {
    val context = LocalContext.current.applicationContext
    val offset = remember(mediaId) { mutableStateOf(0) }
    var loadedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mediaId) {
        if (mediaId == null) return@LaunchedEffect
        offset.value = runCatching { LyricsSyncOffsets.load(context, mediaId) }.getOrDefault(0)
        loadedFor = mediaId
    }
    LaunchedEffect(mediaId, offset.value) {
        if (mediaId == null || loadedFor != mediaId) return@LaunchedEffect
        runCatching { LyricsSyncOffsets.save(context, mediaId, offset.value) }
    }
    return offset
}
