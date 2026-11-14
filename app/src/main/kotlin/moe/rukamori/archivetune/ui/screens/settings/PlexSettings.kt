/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlexAccountTokenKey
import moe.rukamori.archivetune.constants.PlexLastSyncKey
import moe.rukamori.archivetune.constants.PlexServerMachineIdKey
import moe.rukamori.archivetune.constants.PlexServerNameKey
import moe.rukamori.archivetune.constants.PlexServerTokenKey
import moe.rukamori.archivetune.constants.PlexServerUriKey
import moe.rukamori.archivetune.constants.PlexSyncedSongCountKey
import moe.rukamori.archivetune.plex.PlexClient
import moe.rukamori.archivetune.plex.PlexLibrarySync
import moe.rukamori.archivetune.plex.PlexServer
import moe.rukamori.archivetune.plex.PlexSession
import moe.rukamori.archivetune.plex.PlexSyncState
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.SettingsPageTopBar
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.screens.ScreenHeaderHaze
import moe.rukamori.archivetune.ui.screens.rememberScreenHeaderHaze
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.startActivitySafely

const val PLEX_SETTINGS_ROUTE = "settings/plex"

private const val PIN_POLL_INTERVAL_MS = 2_000L
private const val PIN_POLL_MAX_ATTEMPTS = 150

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlexSettings(
    navController: NavController,
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    val (accountToken, _) = rememberPreference(PlexAccountTokenKey, "")
    val (serverName, _) = rememberPreference(PlexServerNameKey, "")
    val (serverUri, _) = rememberPreference(PlexServerUriKey, "")
    val (lastSync, _) = rememberPreference(PlexLastSyncKey, 0L)
    val (songCount, _) = rememberPreference(PlexSyncedSongCountKey, 0)
    val syncState by PlexLibrarySync.state.collectAsState()
    val plexProblem by PlexSession.problem.collectAsState()

    var loginJob by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var serverChoices by remember { mutableStateOf<List<PlexServer>>(emptyList()) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val waitingText = stringResource(R.string.plex_status_waiting)
    val noServerText = stringResource(R.string.plex_status_no_server)
    val unreachableText = stringResource(R.string.plex_status_unreachable)
    val failedText = stringResource(R.string.plex_status_failed)
    val connectingText = stringResource(R.string.plex_status_connecting)

    suspend fun useServer(server: PlexServer) {
        status = connectingText
        val clientId = PlexSession.clientId(context)
        val uris = PlexClient.connectionUris(server)
        val uri = PlexClient.firstReachable(clientId, server.accessToken, uris)
        if (uri == null) {
            status = unreachableText
            return
        }
        context.dataStore.edit {
            it[PlexServerNameKey] = server.name
            it[PlexServerMachineIdKey] = server.machineId
            it[PlexServerTokenKey] = server.accessToken
            it[PlexServerUriKey] = uri
            it[moe.rukamori.archivetune.constants.PlexServerConnectionsKey] = uris.joinToString("\n")
        }
        PlexSession.invalidate()
        status = null
        PlexLibrarySync.start(context, database)
    }

    suspend fun loadServers(token: String) {
        val servers = PlexClient.servers(PlexSession.clientId(context), token)
        when {
            servers.isEmpty() -> status = noServerText
            servers.size == 1 -> useServer(servers.single())
            else -> serverChoices = servers
        }
    }

    fun signIn() {
        loginJob?.cancel()
        loginJob =
            scope.launch {
                try {
                    val clientId = PlexSession.clientId(context)
                    val pin = PlexClient.createPin(clientId)
                    context.startActivitySafely(Intent(Intent.ACTION_VIEW, Uri.parse(PlexClient.authUrl(clientId, pin.code))))
                    status = waitingText
                    var token: String? = null
                    var attempts = 0
                    while (token == null && attempts < PIN_POLL_MAX_ATTEMPTS) {
                        delay(PIN_POLL_INTERVAL_MS)
                        token = runCatching { PlexClient.pinToken(clientId, pin.id) }.getOrNull()
                        attempts++
                    }
                    if (token == null) {
                        status = failedText
                        return@launch
                    }
                    context.dataStore.edit { it[PlexAccountTokenKey] = token }
                    loadServers(token)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    status = failedText + " (" + (error.message ?: error.javaClass.simpleName) + ")"
                }
            }
    }

    val headerHaze = rememberScreenHeaderHaze()
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsPageTopBar(
                titleText = stringResource(R.string.plex_settings),
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            val bottomPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom)
                    .asPaddingValues()
                    .calculateBottomPadding()
            val scrollState = rememberScrollState()
            val positions = rememberPreferencePositions()
            LaunchedEffect(scrollTo) { positions.scrollToKey(scrollTo, scrollState) }

            Column(
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal))
                    .then(positions.containerModifier())
                    .verticalScroll(scrollState)
                    .hazeSource(headerHaze)
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(bottom = bottomPadding + 16.dp),
            ) {
                PreferenceGroup(title = stringResource(R.string.plex_server)) {
                    item {
                        Text(
                            text = stringResource(R.string.plex_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }

                    if (serverUri.isBlank()) {
                        item {
                            PreferenceEntry(
                                modifier = positions.modifierFor("plex_sign_in"),
                                title = { Text(stringResource(R.string.plex_sign_in)) },
                                description = status ?: stringResource(R.string.plex_sign_in_description),
                                icon = { Icon(painterResource(R.drawable.solar_server_linear), null) },
                                onClick = {
                                    if (accountToken.isNotBlank() && loginJob?.isActive != true) {
                                        loginJob =
                                            scope.launch {
                                                runCatching { loadServers(accountToken) }
                                                    .onFailure { signIn() }
                                            }
                                    } else {
                                        signIn()
                                    }
                                },
                            )
                        }
                    } else {
                        item {
                            PreferenceEntry(
                                modifier = positions.modifierFor("plex_server_status"),
                                title = { Text(serverName.ifBlank { "Plex" }) },
                                description =
                                    when (val state = syncState) {
                                        is PlexSyncState.Running ->
                                            stringResource(R.string.plex_syncing, state.done, state.total)
                                        else ->
                                            when (plexProblem) {
                                                PlexSession.Problem.OFFLINE -> stringResource(R.string.plex_server_offline)
                                                PlexSession.Problem.UNREACHABLE ->
                                                    stringResource(R.string.plex_status_unreachable)
                                                null -> null
                                            } ?: if (state is PlexSyncState.Failed) {
                                                stringResource(R.string.plex_sync_failed, state.message)
                                            } else
                                            if (lastSync > 0L) {
                                                stringResource(
                                                    R.string.plex_synced,
                                                    songCount,
                                                    DateUtils.getRelativeTimeSpanString(lastSync).toString(),
                                                )
                                            } else {
                                                stringResource(R.string.plex_never_synced)
                                            }
                                    },
                                icon = { Icon(painterResource(R.drawable.solar_server_linear), null) },
                            )
                        }
                        item {
                            PreferenceEntry(
                                modifier = positions.modifierFor("plex_sync"),
                                title = { Text(stringResource(R.string.plex_sync_now)) },
                                description = stringResource(R.string.plex_sync_now_description),
                                icon = { Icon(painterResource(R.drawable.sync), null) },
                                isEnabled = syncState !is PlexSyncState.Running,
                                onClick = {
                                    PlexSession.invalidate()
                                    PlexLibrarySync.start(context, database)
                                },
                            )
                        }
                        item {
                            PreferenceEntry(
                                modifier = positions.modifierFor("plex_sign_out"),
                                title = { Text(stringResource(R.string.plex_sign_out)) },
                                description = stringResource(R.string.plex_sign_out_description),
                                icon = { Icon(painterResource(R.drawable.logout), null) },
                                onClick = { confirmSignOut = true },
                            )
                        }
                    }
                }
            }

            ScreenHeaderHaze(
                hazeState = headerHaze,
                systemBarsTopPadding = systemBarsTopPadding,
            )
        }
    }

    if (serverChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { serverChoices = emptyList() },
            title = { Text(stringResource(R.string.plex_choose_server)) },
            text = {
                Column {
                    serverChoices.forEach { server ->
                        TextButton(
                            onClick = {
                                serverChoices = emptyList()
                                scope.launch { useServer(server) }
                            },
                        ) { Text(server.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { serverChoices = emptyList() }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.plex_sign_out)) },
            text = { Text(stringResource(R.string.plex_sign_out_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        scope.launch {
                            PlexLibrarySync.removeAll(context, database)
                            context.dataStore.edit {
                                it.remove(PlexAccountTokenKey)
                                it.remove(PlexServerNameKey)
                                it.remove(PlexServerMachineIdKey)
                                it.remove(PlexServerTokenKey)
                                it.remove(PlexServerUriKey)
                                it.remove(moe.rukamori.archivetune.constants.PlexServerConnectionsKey)
                            }
                        }
                    },
                ) { Text(stringResource(R.string.plex_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
