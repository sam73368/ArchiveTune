/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.ui.tv.tvMenuFocusTrap

import android.annotation.SuppressLint
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutoLoadMoreKey
import moe.rukamori.archivetune.constants.ShowCodecOnPlayerKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.QueueEditLockKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.getQueueWindows
import moe.rukamori.archivetune.extensions.moveByKey
import moe.rukamori.archivetune.ui.component.KeepDraggedRowComposed
import moe.rukamori.archivetune.ui.component.ReorderDragTracker
import moe.rukamori.archivetune.ui.component.trackReorderPointer
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheet
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaMetadataListItem
import moe.rukamori.archivetune.ui.component.TextFieldDialog
import moe.rukamori.archivetune.ui.menu.AddToPlaylistDialog
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.utils.oem.SystemMediaControlResolver
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@SuppressLint("UnrememberedMutableState")
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Queue(
    state: BottomSheetState,
    playerBottomSheetState: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    backgroundColor: Color,
    onBackgroundColor: Color,
    TextBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onShowLyrics: () -> Unit = {},
    pureBlack: Boolean,
) {
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboard.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()

    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true

    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val selectedSongs = remember { mutableStateListOf<MediaMetadata>() }
    val selectedItems = remember { mutableStateListOf<Timeline.Window>() }
    var selection by remember { mutableStateOf(false) }

    fun clearSelection() {
        selection = false
        selectedSongs.clear()
        selectedItems.clear()
    }

    if (selection) {
        BackHandler {
            clearSelection()
        }
    }

    var locked by rememberPreference(QueueEditLockKey, defaultValue = true)
    var infiniteQueueEnabled by rememberPreference(AutoLoadMoreKey, defaultValue = true)
    val infiniteQueueLoading by playerConnection.service.infiniteQueueLoading.collectAsState()

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.APPLE_MUSIC,
    )

    val snackbarHostState = remember { SnackbarHostState() }
    var dismissJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    var showChoosePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateQueuePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    AddToPlaylistDialog(
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val songs =
                if (selectedSongs.isNotEmpty()) {
                    selectedSongs.toList()
                } else {
                    mediaMetadata?.let { listOf(it) } ?: emptyList()
                }
            songs.map {
                database.withTransaction {
                    insert(it)
                }
                it.id
            }
        },
        onDismiss = { showChoosePlaylistDialog = false },
        onAddComplete = { songCount, playlistNames ->
            val message =
                when {
                    songCount == 1 && playlistNames.size == 1 -> {
                        context.getString(R.string.added_to_playlist, playlistNames.first())
                    }

                    songCount > 1 && playlistNames.size == 1 -> {
                        context.getString(
                            R.string.added_n_songs_to_playlist,
                            songCount,
                            playlistNames.first(),
                        )
                    }

                    songCount == 1 -> {
                        context.getString(R.string.added_to_n_playlists, playlistNames.size)
                    }

                    else -> {
                        context.getString(R.string.added_n_songs_to_n_playlists, songCount, playlistNames.size)
                    }
                }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            clearSelection()
        },
    )

    if (showCreateQueuePlaylistDialog) {
        TextFieldDialog(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                )
            },
            title = { Text(text = stringResource(R.string.create_playlist)) },
            placeholder = { Text(text = stringResource(R.string.playlist_name)) },
            initialTextFieldValue = TextFieldValue(queueTitle ?: context.getString(R.string.queue)),
            isInputValid = { it.trim().isNotEmpty() && selectedSongs.isNotEmpty() },
            onDismiss = { showCreateQueuePlaylistDialog = false },
            onDone = onDone@{ rawPlaylistName ->
                val playlistName = rawPlaylistName.trim()
                val songs = selectedSongs.toList()
                if (playlistName.isEmpty() || songs.isEmpty()) return@onDone

                coroutineScope.launch(Dispatchers.IO) {
                    val playlist =
                        PlaylistEntity(
                            name = playlistName,
                            bookmarkedAt = LocalDateTime.now(),
                            isEditable = true,
                        )

                    database.withTransaction {
                        insert(playlist)
                        songs.forEachIndexed { index, song ->
                            insert(song)
                            insert(
                                PlaylistSongMap(
                                    playlistId = playlist.id,
                                    songId = song.id,
                                    position = index,
                                    setVideoId = song.setVideoId,
                                ),
                            )
                        }
                    }

                    withContext(Dispatchers.Main) {
                        val message =
                            if (songs.size == 1) {
                                context.getString(R.string.added_to_playlist, playlistName)
                            } else {
                                context.getString(R.string.added_n_songs_to_playlist, songs.size, playlistName)
                            }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        clearSelection()
                    }
                }
            },
        )
    }

    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindow =
        remember(currentWindowIndex, queueWindows) {
            queueWindows.getOrNull(currentWindowIndex)
        }

    val onRemoveWithUndo: (Timeline.Window) -> Unit = { window ->
        val index = window.firstPeriodIndex
        playerConnection.player.removeMediaItem(index)
        dismissJob?.cancel()
        dismissJob =
            coroutineScope.launch {
                val snackbarResult =
                    snackbarHostState.showSnackbar(
                        message =
                            context.getString(
                                R.string.removed_song_from_queue,
                                window.mediaItem.metadata?.title ?: context.getString(R.string.unknown),
                            ),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    playerConnection.player.addMediaItem(window.mediaItem)
                    playerConnection.player.moveMediaItem(
                        playerConnection.player.mediaItemCount - 1,
                        index,
                    )
                }
            }
    }

    val onRemoveMultipleWithUndo: (List<Timeline.Window>) -> Unit = { windows ->
        if (windows.isNotEmpty()) {
            val sortedWindows = windows.sortedBy { it.firstPeriodIndex }
            var i = 0
            sortedWindows.forEach { window ->
                playerConnection.player.removeMediaItem(window.firstPeriodIndex - i++)
            }
            dismissJob?.cancel()
            dismissJob =
                coroutineScope.launch {
                    val snackbarResult =
                        snackbarHostState.showSnackbar(
                            message =
                                if (windows.size == 1) {
                                    context.getString(
                                        R.string.removed_song_from_queue,
                                        windows
                                            .first()
                                            .mediaItem.metadata
                                            ?.title ?: context.getString(R.string.unknown),
                                    )
                                } else {
                                    context.getString(R.string.removed_n_songs_from_queue, windows.size)
                                },
                            actionLabel = context.getString(R.string.undo),
                            duration = SnackbarDuration.Short,
                        )
                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                        sortedWindows.forEach { window ->
                            playerConnection.player.addMediaItem(window.mediaItem)
                            playerConnection.player.moveMediaItem(
                                playerConnection.player.mediaItemCount - 1,
                                window.firstPeriodIndex,
                            )
                        }
                    }
                }
        }
    }

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var sleepTimerValue by remember { mutableStateOf(30f) }
    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd,
        ) {
            playerConnection.service.sleepTimer.isActive
        }
    var sleepTimerTimeLeft by remember { mutableStateOf(0L) }

    val (showCodecOnPlayer) =
        rememberPreference(
            key = ShowCodecOnPlayerKey,
            defaultValue = false,
        )

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration.let { total ->
                            if (total == androidx.media3.common.C.TIME_UNSET) 0L else (total - playerConnection.player.currentPosition).coerceAtLeast(0L)
                        }
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }
    var scrollToCurrentRequested by remember { mutableStateOf(true) }
    val openQueue =
        remember(playerBottomSheetState, state) {
            {
                scrollToCurrentRequested = true
                if (!playerBottomSheetState.isExpandedOrExpanding) {
                    playerBottomSheetState.expandSoft()
                }
                state.expandSoft()
            }
        }

    val isTvQueue = moe.rukamori.archivetune.ui.tv.LocalTvLayout.current
    BottomSheet(
        state = state,
        backgroundColor = { Color.Unspecified },
        modifier =
            modifier.then(
                if (isTvQueue && !state.isCollapsed) {
                    Modifier.tvMenuFocusTrap()
                } else {
                    Modifier
                },
            ),

        onCollapsedContentClick = {},
        collapsedFocusable = !isTvQueue,
        collapsedContent = collapsedQueue@{
            if (isTvQueue) return@collapsedQueue
            when (playerDesignStyle) {
                PlayerDesignStyle.V5 -> {
                    QueueCollapsedContentV3(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                        onMenuClick = {
                            menuState.showImmersive {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }

                PlayerDesignStyle.V4 -> {
                    QueueCollapsedContentV4(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        textButtonColor = textButtonColor,
                        iconButtonColor = iconButtonColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        mediaMetadata = mediaMetadata,
                        onExpandQueue = openQueue,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onShowLyrics = onShowLyrics,
                    )
                }

                PlayerDesignStyle.V9 -> {
                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
                    QueueCollapsedContentV9(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        onShuffleClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onMenuClick = {
                            menuState.showImmersive {
                                PlayerMenu(
                                    mediaMetadata = mediaMetadata,
                                    navController = navController,
                                    playerBottomSheetState = playerBottomSheetState,
                                    onShowDetailsDialog = {
                                        mediaMetadata?.id?.let {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(it)
                                            }
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                    )
                }

                PlayerDesignStyle.V10 -> {
                    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
                    QueueCollapsedContentV10(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        accent = TextBackgroundColor,
                        field = textButtonColor,
                        shuffleModeEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        currentSongLiked = currentSongLiked,
                        onExpandQueue = openQueue,
                        onShuffleClick = {
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        onRepeatModeClick = { playerConnection.player.toggleRepeatMode() },
                        onToggleLike = playerConnection::toggleLike,
                        onAddToPlaylistClick = { showChoosePlaylistDialog = true },
                    )
                }

                PlayerDesignStyle.V7 -> {
                    val audioDevice by playerConnection.service.activeAudioDevice.collectAsStateWithLifecycle()

                    val view = LocalView.current
                    DisposableEffect(view) {
                        val listener =
                            ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                                if (hasFocus) playerConnection.service.refreshActiveDevice()
                            }
                        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
                        onDispose { view.viewTreeObserver.removeOnWindowFocusChangeListener(listener) }
                    }

                    QueueCollapsedContentV7(
                        showCodecOnPlayer = showCodecOnPlayer,
                        currentFormat = currentFormat,
                        textBackgroundColor = TextBackgroundColor,
                        sleepTimerEnabled = sleepTimerEnabled,
                        sleepTimerTimeLeft = sleepTimerTimeLeft,
                        onExpandQueue = openQueue,
                        onShowLyrics = onShowLyrics,
                        onSleepTimerClick = {
                            if (sleepTimerEnabled) {
                                playerConnection.service.sleepTimer.clear()
                            } else {
                                showSleepTimerDialog = true
                            }
                        },
                        onDeviceClick = {
                            SystemMediaControlResolver.openMediaOutputSwitcher(context)
                        },
                        device = audioDevice,
                    )
                }

                PlayerDesignStyle.APPLE_MUSIC,
                PlayerDesignStyle.APPLE_MUSIC_CLASSIC,
                -> {
                }

                PlayerDesignStyle.BITCHORD -> {
                }

                PlayerDesignStyle.TIKTOK -> {
                }

                PlayerDesignStyle.SIMPMUSIC -> {
                }

                PlayerDesignStyle.SPATIALFLOW -> {
                }

                PlayerDesignStyle.LOOPER -> {
                }
            }

            if (showSleepTimerDialog) {
                SleepTimerDialog(
                    onDismiss = { showSleepTimerDialog = false },
                    onConfirm = { minutes ->
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(minutes)
                    },
                    onEndOfSong = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(-1)
                    },
                    initialValue = sleepTimerValue,
                )
            }
        },
    ) {
        val queueWindows by playerConnection.queueWindows.collectAsState()
        val mutableQueueWindows =
            remember {
                mutableStateListOf<Timeline.Window>().apply {
                    addAll(queueWindows)
                }
            }
        val queueLength by remember {
            derivedStateOf {
                queueWindows.sumOf { it.mediaItem.metadata?.duration ?: 0 }
            }
        }

        val lazyListState = rememberLazyListState()
        var draggedUid by remember { mutableStateOf<Any?>(null) }
        val reorderTracker = remember { ReorderDragTracker() }
        LaunchedEffect(reorderTracker, lazyListState) {
            snapshotFlow { lazyListState.isScrollInProgress }.collect { reorderTracker.onScrollStateChanged(it) }
        }

        val currentPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                if (currentWindowIndex in queueWindows.indices) {
                    queueWindows[currentWindowIndex].uid
                } else {
                    null
                }
            }
        val nextPlayingUid =
            remember(currentWindowIndex, queueWindows) {
                queueWindows.getOrNull(currentWindowIndex + 1)?.uid
            }

        val reorderableState =
            rememberReorderableLazyListState(
                lazyListState = lazyListState,
                scrollThresholdPadding =
                    WindowInsets.systemBars
                        .only(WindowInsetsSides.Bottom)
                        .add(
                            WindowInsets(
                                bottom = ListItemHeight,
                            ),
                        ).asPaddingValues(),
            ) onMove@{ from, to ->
                reorderTracker.onMoved(from.key, to.key, from.index, to.index)
                val movedWindow =
                    mutableQueueWindows.moveByKey(from.key, to.key) { it.queueItemKey } ?: return@onMove
                val draggedItemUid = movedWindow.uid
                draggedUid = draggedItemUid
                if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)

                if (selection && currentWindowIndex in mutableQueueWindows.indices) {
                    val currentItem = queueWindows.getOrNull(currentWindowIndex)

                    if (currentItem?.uid == draggedItemUid) {
                        selectedSongs.clear()
                        selectedItems.clear()
                        movedWindow.mediaItem.metadata?.let { metadata ->
                            selectedSongs.add(metadata)
                            selectedItems.add(movedWindow)
                        }
                    }
                }
            }

        LaunchedEffect(queueWindows, reorderableState.isAnyItemDragging) {
            if (reorderableState.isAnyItemDragging) return@LaunchedEffect
            reorderTracker.onDragStopped(lazyListState)
            val droppedKey = reorderTracker.draggedKey
            val edgeTarget =
                reorderTracker.dropEdgeTarget(
                    lazyListState = lazyListState,
                    firstKey = mutableQueueWindows.firstOrNull()?.queueItemKey,
                    lastKey = mutableQueueWindows.lastOrNull()?.queueItemKey,
                )
            reorderTracker.reset()
            if (droppedKey != null && edgeTarget != null) {
                mutableQueueWindows.moveByKey(droppedKey, edgeTarget) { it.queueItemKey }?.let { draggedUid = it.uid }
            }

            val completedDragUid = draggedUid
            var latestWindows = queueWindows
            if (completedDragUid != null) {
                draggedUid = null
                val reorder = resolveQueueReorder(queueWindows, mutableQueueWindows.toList(), completedDragUid)
                if (reorder != null) {
                    runCatching {
                        playerConnection.player.applyQueueReorder(playerConnection.localPlayer, queueWindows, reorder)
                        latestWindows = playerConnection.player.getQueueWindows()
                    }
                }
            }

            Snapshot.withMutableSnapshot {
                mutableQueueWindows.clear()
                mutableQueueWindows.addAll(latestWindows)
            }
        }

        LaunchedEffect(
            state.isCollapsed,
            scrollToCurrentRequested,
            currentPlayingUid,
            reorderableState.isAnyItemDragging,
        ) {
            if (
                !state.isCollapsed &&
                scrollToCurrentRequested &&
                currentPlayingUid != null &&
                !reorderableState.isAnyItemDragging
            ) {
                val indexInMutableList = mutableQueueWindows.indexOfFirst { it.uid == currentPlayingUid }
                if (indexInMutableList != -1) {
                    lazyListState.scrollToItem(indexInMutableList)
                    scrollToCurrentRequested = false
                }
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundColor),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                val queueShuffleEnabled by playerConnection.shuffleModeEnabled.collectAsState()
                CurrentSongHeader(
                    sheetState = state,
                    mediaMetadata = mediaMetadata,
                    liked = currentSongLiked,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = queueShuffleEnabled,
                    locked = locked,
                    songCount = queueWindows.size,
                    queueDuration = queueLength,
                    infiniteQueueEnabled = infiniteQueueEnabled,
                    infiniteQueueLoading = infiniteQueueLoading,
                    backgroundColor = backgroundColor,
                    onBackgroundColor = onBackgroundColor,
                    onToggleLike = {
                        playerConnection.service.toggleLike()
                    },
                    onMenuClick = {
                        menuState.showImmersive {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = playerBottomSheetState,
                                isQueueTrigger = true,
                                onRemoveFromQueue = {
                                    currentWindow?.let { onRemoveWithUndo(it) }
                                },
                                onShowDetailsDialog = {
                                    mediaMetadata?.id?.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    onClearQueueClick = {
                        val windowsToRemove =
                            if (currentWindowIndex in queueWindows.indices) {
                                queueWindows.filterIndexed { index, _ -> index != currentWindowIndex }
                            } else {
                                emptyList()
                            }

                        if (windowsToRemove.isNotEmpty()) {
                            onRemoveMultipleWithUndo(windowsToRemove)
                            selection = false
                            selectedSongs.clear()
                            selectedItems.clear()
                            playerConnection.service.dropQueueContinuation()
                        }

                        if (infiniteQueueEnabled) {
                            infiniteQueueEnabled = false
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                    onRepeatClick = { playerConnection.player.toggleRepeatMode() },
                    onShuffleClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            playerConnection.player.shuffleModeEnabled = !playerConnection.player.shuffleModeEnabled
                        }
                    },
                    onLockClick = {
                        locked = !locked
                    },
                    onInfiniteQueueClick = {
                        val nextInfiniteQueueEnabled = !infiniteQueueEnabled
                        infiniteQueueEnabled = nextInfiniteQueueEnabled
                        if (nextInfiniteQueueEnabled) {
                            playerConnection.service.onInfiniteQueueEnabled()
                        } else {
                            playerConnection.service.onInfiniteQueueDisabled()
                        }
                    },
                )

                val selectionSpacerHeight by animateDpAsState(
                    targetValue = if (selection) 48.dp else 0.dp,
                    label = "queueSelectionSpacer",
                )
                val isAnyQueueItemDragging = reorderableState.isAnyItemDragging
                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            .add(
                                WindowInsets(
                                    top = selectionSpacerHeight,
                                    bottom = ListItemHeight + if (selection) 88.dp else 8.dp,
                                ),
                            ).asPaddingValues(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .trackReorderPointer(reorderTracker)
                            .nestedScroll(state.preUpPostDownNestedScrollConnection),
                ) {
                    items(
                        items = mutableQueueWindows,
                        key = { item -> item.queueItemKey },
                        contentType = { "queue_item" },
                    ) { window ->
                        ReorderableItem(
                            state = reorderableState,
                            key = window.queueItemKey,
                        ) { isDragging ->
                            KeepDraggedRowComposed(isDragging, window.queueItemKey)
                            val dragElevation by animateDpAsState(
                                targetValue = if (isDragging) 8.dp else 0.dp,
                                label = "queueDragElevation",
                            )
                            val currentItem by rememberUpdatedState(window)
                            val isActive = window.uid == currentPlayingUid
                            val dismissBoxState =
                                rememberSwipeToDismissBoxState(
                                    positionalThreshold = { totalDistance -> totalDistance },
                                )

                            var processedDismiss by remember { mutableStateOf(false) }
                            LaunchedEffect(dismissBoxState.currentValue) {
                                val dv = dismissBoxState.currentValue
                                if (!processedDismiss && (
                                        dv == SwipeToDismissBoxValue.StartToEnd ||
                                            dv == SwipeToDismissBoxValue.EndToStart
                                    )
                                ) {
                                    processedDismiss = true
                                    onRemoveWithUndo(currentItem)
                                }
                                if (dv == SwipeToDismissBoxValue.Settled) {
                                    processedDismiss = false
                                }
                            }

                            val content: @Composable () -> Unit = {
                                Row(
                                    horizontalArrangement = Arrangement.Center,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .shadow(dragElevation),
                                ) {
                                    val shouldLoadImages by remember {
                                        derivedStateOf {
                                            state.value > state.collapsedBound + 80.dp
                                        }
                                    }

                                    val trackMetadata = window.mediaItem.metadata ?: return@Row
                                    val onPlayNextFromQueue =
                                        remember(
                                            window.uid,
                                            window.firstPeriodIndex,
                                            currentPlayingUid,
                                            nextPlayingUid,
                                        ) {
                                            if (window.uid != currentPlayingUid && window.uid != nextPlayingUid) {
                                                {
                                                    playerConnection.moveQueueItemToNext(window.firstPeriodIndex)
                                                }
                                            } else {
                                                null
                                            }
                                        }
                                    MediaMetadataListItem(
                                        mediaMetadata = trackMetadata,
                                        isSelected = selection && trackMetadata in selectedSongs,
                                        isActive = isActive,
                                        isPlaying = isPlaying && isActive,
                                        shouldLoadImage = shouldLoadImages,
                                        trailingContent = {
                                            IconButton(
                                                onClick = {
                                                    menuState.showImmersive {
                                                        PlayerMenu(
                                                            mediaMetadata = trackMetadata,
                                                            navController = navController,
                                                            playerBottomSheetState = playerBottomSheetState,
                                                            isQueueTrigger = true,
                                                            onPlayNextFromQueue = onPlayNextFromQueue,
                                                            onRemoveFromQueue = {
                                                                onRemoveWithUndo(window)
                                                            },
                                                            onShowDetailsDialog = {
                                                                window.mediaItem.mediaId.let {
                                                                    bottomSheetPageState.show {
                                                                        ShowMediaInfo(it)
                                                                    }
                                                                }
                                                            },
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.more_vert),
                                                    contentDescription = null,
                                                )
                                            }
                                            if (!locked) {
                                                IconButton(
                                                    onClick = { },
                                                    modifier =
                                                        Modifier.draggableHandle(
                                                            onDragStarted = {
                                                                reorderTracker.onDragStarted(window.queueItemKey)
                                                                if (enableHapticFeedback) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                                }
                                                            },
                                                            onDragStopped = {
                                                                reorderTracker.onHandleStopped(lazyListState)
                                                                if (enableHapticFeedback) {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                                                }
                                                            },
                                                        ),
                                                ) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.drag_handle),
                                                        contentDescription = null,
                                                    )
                                                }
                                            } else {
                                                IconButton(onClick = { locked = false }) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.drag_handle),
                                                        contentDescription = null,
                                                        modifier = Modifier.graphicsLayer { alpha = 0.45f },
                                                    )
                                                }
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .background(backgroundColor)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (selection) {
                                                            if (trackMetadata in selectedSongs) {
                                                                selectedSongs.remove(trackMetadata)
                                                                selectedItems.remove(currentItem)
                                                                if (selectedSongs.isEmpty()) {
                                                                    selection = false
                                                                }
                                                            } else {
                                                                selectedSongs.add(trackMetadata)
                                                                selectedItems.add(currentItem)
                                                            }
                                                        } else {
                                                            if (window.uid == currentPlayingUid) {
                                                                playerConnection.player.togglePlayPause()
                                                            } else {
                                                                playerConnection.player.seekToDefaultPosition(
                                                                    window.firstPeriodIndex,
                                                                )
                                                                playerConnection.player.playWhenReady = true
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        if (enableHapticFeedback) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        if (!selection) {
                                                            selection = true
                                                        }
                                                        selectedSongs.clear()
                                                        selectedItems.clear()
                                                        selectedSongs.add(trackMetadata)
                                                        selectedItems.add(currentItem)
                                                    },
                                                ),
                                    )
                                }
                            }

                            if (locked) {
                                content()
                            } else {
                                SwipeToDismissBox(
                                    state = dismissBoxState,
                                    backgroundContent = {},
                                    gesturesEnabled = !isAnyQueueItemDragging,
                                ) {
                                    content()
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier =
                        Modifier
                            .padding(
                                bottom =
                                    (if (selection) ListItemHeight * 2 + 16.dp else ListItemHeight) +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ).align(Alignment.BottomCenter),
                )

                AnimatedVisibility(
                    visible = selection,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom =
                                    ListItemHeight +
                                        WindowInsets.systemBars
                                            .asPaddingValues()
                                            .calculateBottomPadding(),
                            ),
                ) {
                    QueueSelectionFloatingToolbar(
                        allSelected = selectedSongs.size == mutableQueueWindows.size,
                        pureBlack = pureBlack,
                        onClose = ::clearSelection,
                        onToggleSelectAll = {
                            if (selectedSongs.size == mutableQueueWindows.size) {
                                clearSelection()
                            } else {
                                selectedSongs.clear()
                                selectedItems.clear()
                                mutableQueueWindows.forEach { window ->
                                    window.mediaItem.metadata?.let { metadata ->
                                        selectedSongs.add(metadata)
                                        selectedItems.add(window)
                                    }
                                }
                            }
                        },
                        onAddToPlaylist = { showChoosePlaylistDialog = true },
                        onCreatePlaylist = { showCreateQueuePlaylistDialog = true },
                        onDelete = {
                            onRemoveMultipleWithUndo(selectedItems.toList())
                            clearSelection()
                        },
                        modifier =
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

private val Timeline.Window.queueItemKey: Long
    get() =
        (uid.hashCode().toLong() shl Int.SIZE_BITS) xor
            (mediaItem.mediaId.hashCode().toLong() and UInt.MAX_VALUE.toLong())

@Composable
private fun QueueSelectionFloatingToolbar(
    allSelected: Boolean,
    pureBlack: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val toolbarContainerColor = if (pureBlack) Color.Black else colorScheme.surfaceContainerHigh
    val toolbarContentColor = if (pureBlack) Color.White else colorScheme.onSurface
    val fabContainerColor = if (pureBlack) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerHighest
    val fabContentColor = if (pureBlack) Color.White else colorScheme.onSurface

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier.widthIn(max = 420.dp),
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = onClose,
                containerColor = fabContainerColor,
                contentColor = fabContentColor,
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.close),
                    modifier = Modifier.size(22.dp),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = toolbarContainerColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueSelectionToolbarAction(
                icon = if (allSelected) R.drawable.deselect else R.drawable.select_all,
                contentDescription = null,
                tint = toolbarContentColor,
                onClick = onToggleSelectAll,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.playlist_add,
                contentDescription = stringResource(R.string.add_to_playlist),
                tint = colorScheme.primary,
                onClick = onAddToPlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.queue_music,
                contentDescription = stringResource(R.string.create_playlist),
                tint = colorScheme.primary,
                onClick = onCreatePlaylist,
            )

            QueueSelectionToolbarAction(
                icon = R.drawable.delete,
                contentDescription = stringResource(R.string.delete),
                tint = colorScheme.error,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun QueueSelectionToolbarAction(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
    }
}
