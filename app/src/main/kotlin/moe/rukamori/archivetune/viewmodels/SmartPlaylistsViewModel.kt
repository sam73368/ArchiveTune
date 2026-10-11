/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.SmartPlaylistsKey
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.smartplaylist.SmartPlaylists
import moe.rukamori.archivetune.smartplaylist.SmartRule
import moe.rukamori.archivetune.utils.dataStore
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SmartPlaylistsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) : ViewModel() {
        /** Built-in presets followed by the user's own rules. */
        val rules: StateFlow<List<SmartRule>> =
            context.dataStore.data
                .map { prefs -> SmartPlaylists.decode(prefs[SmartPlaylistsKey].orEmpty()) }
                .distinctUntilChanged()
                .map { custom -> SmartPlaylists.presets + custom }
                .stateIn(viewModelScope, SharingStarted.Eagerly, SmartPlaylists.presets)

        fun songsFor(ruleId: String): Flow<List<Song>?> =
            combine(
                rules,
                database.allSongs(),
                database.songPlayStats(),
                context.dataStore.data.map { it[HideExplicitKey] ?: false }.distinctUntilChanged(),
            ) { allRules, songs, stats, hideExplicit ->
                val rule = allRules.firstOrNull { it.id == ruleId } ?: return@combine null
                val effective = if (hideExplicit) rule.copy(allowExplicit = false) else rule
                SmartPlaylists.evaluate(effective, songs, stats.associateBy { it.songId })
            }.flowOn(Dispatchers.Default)

        fun newRule(): SmartRule = SmartRule(id = UUID.randomUUID().toString(), name = "")

        fun save(rule: SmartRule) {
            if (rule.preset) return
            viewModelScope.launch {
                val custom = rules.value.filterNot { it.preset }
                val updated =
                    if (custom.any { it.id == rule.id }) {
                        custom.map { if (it.id == rule.id) rule else it }
                    } else {
                        custom + rule
                    }
                persist(updated)
            }
        }

        fun delete(ruleId: String) {
            viewModelScope.launch { persist(rules.value.filterNot { it.preset || it.id == ruleId }) }
        }

        private suspend fun persist(custom: List<SmartRule>) {
            context.dataStore.edit { it[SmartPlaylistsKey] = SmartPlaylists.encode(custom) }
        }
    }
