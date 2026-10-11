/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import androidx.compose.runtime.mutableStateMapOf

/** Ids of songs Smart Shuffle slipped into the queue, so the queue can label them. */
object SmartShuffleMarks {
    val ids = mutableStateMapOf<String, Boolean>()

    fun mark(mediaId: String) {
        ids[mediaId] = true
    }

    fun clear() {
        ids.clear()
    }
}
