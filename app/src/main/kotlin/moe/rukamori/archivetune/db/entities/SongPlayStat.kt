/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable

/** Number of recorded listens of a song and when the latest one happened (epoch millis, see Converters). */
@Immutable
data class SongPlayStat(
    val songId: String,
    val plays: Int,
    val lastPlayed: Long,
)
