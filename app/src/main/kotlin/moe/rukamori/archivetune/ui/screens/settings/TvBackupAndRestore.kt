/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.settings

import android.content.Intent
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.backup.TvBackupFile
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.component.SettingsPageTopBar
import moe.rukamori.archivetune.ui.menu.LoadingScreen
import moe.rukamori.archivetune.ui.screens.ScreenHeaderHaze
import moe.rukamori.archivetune.ui.screens.rememberScreenHeaderHaze
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.BackupCategory
import moe.rukamori.archivetune.viewmodels.BackupRestoreViewModel
import java.text.DateFormat
import java.util.Date

private val TvBackupCategories =
    listOf(
        BackupCategory.SETTINGS,
        BackupCategory.ACCOUNT,
        BackupCategory.LIBRARY,
        BackupCategory.LYRICS,
    )

@Composable
fun TvBackupAndRestore(
    navController: NavController,
    viewModel: BackupRestoreViewModel,
    scrollTo: String? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val backups by viewModel.tvBackups.collectAsStateWithLifecycle()
    val progress by viewModel.backupRestoreProgress.collectAsStateWithLifecycle()

    var showBackupOptions by rememberSaveable { mutableStateOf(false) }
    var selectedBackup by remember { mutableStateOf<TvBackupFile?>(null) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var restoreCategories by remember { mutableStateOf<Set<BackupCategory>>(emptySet()) }
    var showRestoreOptions by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val filePickerAvailable =
        remember {
            val probe =
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("*/*")
            probe.resolveActivity(context.packageManager) != null
        }

    fun startRestore(uri: android.net.Uri) {
        coroutineScope.launch {
            val result = viewModel.validateBackup(context, uri)
            if (result.isValid) {
                val available = result.availableCategories.filter { it in TvBackupCategories }
                restoreCategories = available.toSet()
                restoreUri = uri
                showRestoreOptions = available.isNotEmpty()
                if (available.isEmpty()) {
                    validationError = context.getString(R.string.restore_missing_content)
                }
            } else {
                validationError = result.errorMessage ?: context.getString(R.string.restore_corrupted)
            }
        }
    }

    val filePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) startRestore(uri)
        }

    LaunchedEffect(Unit) { viewModel.refreshTvBackups(context) }
    LaunchedEffect(Unit) {
        viewModel.backupEvent.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val headerHaze = rememberScreenHeaderHaze()
    val systemBarsTopPadding = LocalStableSystemBarsTopPadding.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            SettingsPageTopBar(
                titleText = stringResource(R.string.backup_restore),
                onBack = navController::navigateUp,
                onBackLongClick = navController::backToMain,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                        .padding(16.dp),
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
                    .padding(bottom = bottomPadding + SettingsDimensions.ScreenBottomPadding),
            ) {
                PreferenceGroup(
                    modifier = positions.modifierFor("backup", "restore"),
                    title = stringResource(R.string.tv_backup_group),
                ) {
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tv_backup_create)) },
                            description = stringResource(R.string.tv_backup_create_desc),
                            icon = { Icon(painterResource(R.drawable.backup), null) },
                            onClick = { showBackupOptions = true },
                        )
                    }
                    if (filePickerAvailable) {
                        item {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.tv_backup_pick_file)) },
                                description = stringResource(R.string.tv_backup_pick_file_desc),
                                icon = { Icon(painterResource(R.drawable.snippet_folder), null) },
                                onClick = { filePicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) },
                            )
                        }
                    }
                    item {
                        PreferenceEntry(
                            title = { Text(stringResource(R.string.tv_backup_only_tv_title)) },
                            description = stringResource(R.string.tv_backup_only_tv_desc),
                            icon = { Icon(painterResource(R.drawable.info), null) },
                        )
                    }
                }

                PreferenceGroup(
                    title = stringResource(R.string.tv_backup_saved_group),
                ) {
                    if (backups.isEmpty()) {
                        item {
                            PreferenceEntry(
                                title = { Text(stringResource(R.string.tv_backup_none_title)) },
                                description = stringResource(R.string.tv_backup_none_desc),
                                icon = { Icon(painterResource(R.drawable.restore), null) },
                            )
                        }
                    } else {
                        backups.forEach { backup ->
                            item {
                                PreferenceEntry(
                                    title = { Text(backup.name) },
                                    description = backupDescription(backup),
                                    icon = { Icon(painterResource(R.drawable.restore), null) },
                                    onClick = { selectedBackup = backup },
                                )
                            }
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

    if (showBackupOptions) {
        BackupOptionsDialog(
            title = stringResource(R.string.backup_options_title),
            confirmLabel = stringResource(R.string.action_backup),
            categories = TvBackupCategories,
            initialSelection = setOf(BackupCategory.SETTINGS),
            onConfirm = { categories ->
                showBackupOptions = false
                viewModel.backupTv(context, categories)
            },
            onDismiss = { showBackupOptions = false },
        )
    }

    selectedBackup?.let { backup ->
        DefaultDialog(
            onDismiss = { selectedBackup = null },
            title = { Text(backup.name) },
            buttons = {
                TextButton(
                    onClick = {
                        selectedBackup = null
                        startRestore(backup.uri)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.action_restore))
                }
                TextButton(
                    onClick = {
                        selectedBackup = null
                        viewModel.deleteTvBackup(context, backup)
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(R.string.tv_backup_delete))
                }
                TextButton(
                    onClick = { selectedBackup = null },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            Text(
                text = backupDescription(backup),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val pendingUri = restoreUri
    if (showRestoreOptions && pendingUri != null) {
        BackupOptionsDialog(
            title = stringResource(R.string.restore_options_title),
            confirmLabel = stringResource(R.string.action_restore),
            categories = TvBackupCategories.filter { it in restoreCategories },
            initialSelection = restoreCategories - BackupCategory.ACCOUNT,
            onConfirm = { categories ->
                showRestoreOptions = false
                restoreUri = null
                viewModel.restore(context, pendingUri, categories)
            },
            onDismiss = {
                showRestoreOptions = false
                restoreUri = null
            },
        )
    }

    validationError?.let { message ->
        DefaultDialog(
            onDismiss = { validationError = null },
            title = { Text(stringResource(R.string.restore_failed)) },
            buttons = {
                TextButton(onClick = { validationError = null }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    LoadingScreen(
        isVisible = progress != null,
        value = progress?.percent ?: 0,
        title = progress?.title,
        stepText = progress?.step.orEmpty(),
        indeterminate = progress?.indeterminate ?: false,
    )
}

@Composable
private fun backupDescription(backup: TvBackupFile): String {
    val context = LocalContext.current
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(backup.modifiedMillis))
    val size = Formatter.formatShortFileSize(context, backup.sizeBytes)
    val location =
        stringResource(
            if (backup.inDownloads) R.string.tv_backup_location_downloads else R.string.tv_backup_location_app,
        )
    return "$date · $size · $location"
}
