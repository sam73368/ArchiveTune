/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.component

import android.app.Activity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.LocalLyricsScrollListener
import moe.rukamori.archivetune.constants.LyricsClickKey
import moe.rukamori.archivetune.constants.EnhancedLyricsStyle
import moe.rukamori.archivetune.constants.EnhancedLyricsStyleKey
import moe.rukamori.archivetune.constants.LyricsHoldManualScrollKey
import moe.rukamori.archivetune.constants.LyricsLineBlurKey
import moe.rukamori.archivetune.constants.LyricsScrollKey
import moe.rukamori.archivetune.constants.LyricsRomanizeChineseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeHindiKey
import moe.rukamori.archivetune.constants.LyricsRomanizeJapaneseKey
import moe.rukamori.archivetune.constants.LyricsRomanizeKoreanKey
import moe.rukamori.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import moe.rukamori.archivetune.constants.LyricsTextSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import moe.rukamori.archivetune.lyrics.AiLyricsRomanization
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsRomanizationPreferences
import moe.rukamori.archivetune.lyrics.LyricsUtils.hasTrueWordSync
import moe.rukamori.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils.isTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseLyrics
import moe.rukamori.archivetune.lyrics.LyricsUtils.parseTtml
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedRomanizedWordsForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.providedTranslationTextForEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeLyricsLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.romanizeWordsForLine
import moe.rukamori.archivetune.lyrics.LyricsUtils.shouldRomanizeLyricsLine
import moe.rukamori.archivetune.lyrics.WordTimestamp
import moe.rukamori.archivetune.lastfm.CatalogueCoverProvider
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue

private const val LYRIC_SYNC_LEAD_MS = 120L
private const val LRC_LEAD_MS = LYRIC_SYNC_LEAD_MS

private const val TTML_LEAD_MS = 0L
private const val LYRIC_VISUAL_TUNING_OFFSET_MS = 0L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_HOLD_SETTLE_MS = 120L
private const val LYRICS_POSITION_PUBLISH_MIN_DELTA_MS = 10L
private const val LYRIC_SMOOTH_RETURN_MAX_LINES = 5
private const val LYRIC_RETURN_FADE_OUT_MS = 110
private const val LYRIC_RETURN_SETTLE_MS = 500L
private const val LYRIC_RETURN_FADE_IN_MS = 180
private const val LYRIC_ANCHOR_SCREEN_RATIO = 0.11f


private const val SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS = 80L
private const val SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS = 180L
private const val SMOOTH_PLAYBACK_DRIFT_CORRECTION = 0.55f


private const val LYRIC_FIRST_FOCUS_FADE_MS = 200

private const val LYRIC_FIRST_FOCUS_TIMEOUT_MS = 400L

private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

private const val LINE_SYNCED_TRAILING_LINE_DURATION_MS = 4_000L

private const val POSITION_RESET_BACKWARD_THRESHOLD_MS = 1000L

private const val ROMANIZATION_FIRST_BUILD_GRACE_MS = 700L

private data class KaraokeBuild(
    val lyrics: SyncedLyrics,
    val romanization: Map<Int, List<String?>>,
    val generation: Int,
    /** The parsed lines this build was made from: until it matches, the loading placeholder shows. */
    val forEntries: List<LyricsEntry>? = null,
)

private fun Map<Int, List<String?>>.renderedRomanization(): Map<Int, List<String?>> =
    filterValues { values -> values.any { !it.isNullOrBlank() } }

internal fun extractTtmlWriters(lyrics: String?): String {
    if (lyrics.isNullOrBlank()) return ""
    val writerTagPattern =
        Regex(
            pattern = "<(songwriter|composer|lyricist|writer|ar)(?:\\s[^>]*)?>([^<]+)</\\1>",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    val writers = LinkedHashSet<String>()
    writerTagPattern.findAll(lyrics).forEach { match ->
        val name = match.groupValues.getOrNull(2)?.trim()
        if (!name.isNullOrBlank()) writers.add(name)
    }
    return writers.joinToString(", ").trim()
}

@Composable
fun LyricsEnhanced(
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    lyricsLineBlurOverride: Boolean? = null,

    textSizeOverride: Float? = null,
    singleActiveLine: Boolean = false,
    translationVisibleOverride: Boolean? = null,
    phoneticVisibleOverride: Boolean? = null,
    browseMode: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val animationsDisabled = LocalAnimationsDisabled.current

    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val playbackParameters by playerConnection.playbackParameters.collectAsStateWithLifecycle()

    val (lyricsClick) = rememberPreference(LyricsClickKey, defaultValue = true)
    val (lyricsAutoScroll) = rememberPreference(LyricsScrollKey, defaultValue = true)
    val (holdManualScroll) = rememberPreference(LyricsHoldManualScrollKey, defaultValue = true)
    val (enhancedLyricsStyle) = rememberEnumPreference(EnhancedLyricsStyleKey, defaultValue = EnhancedLyricsStyle.ACCOMPANIST)
    val (lyricsTextSizePreference) = rememberPreference(LyricsTextSizeKey, defaultValue = 26f)
    val lyricsTextSize = textSizeOverride ?: lyricsTextSizePreference

    val (lyricsLineBlurPreference) = rememberPreference(LyricsLineBlurKey, defaultValue = false)
    val (romanizeChinese) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (romanizeHindi) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (romanizeJapanese) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (romanizeKorean) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (romanizeOtherLanguages) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)

    val aiRomanizationSettings = AiLyricsRomanization.rememberSettings()

    val romanizationPreferences =
        remember(
            romanizeJapanese,
            romanizeKorean,
            romanizeChinese,
            romanizeHindi,
            romanizeOtherLanguages,
            aiRomanizationSettings.active,
        ) {
            LyricsRomanizationPreferences(
                romanizeJapanese = romanizeJapanese,
                romanizeKorean = romanizeKorean,
                romanizeChinese = romanizeChinese,
                romanizeHindi = romanizeHindi,
                romanizeOther = romanizeOtherLanguages,
                aiHandled = aiRomanizationSettings.active,
            )
        }

    val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()

    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)

    val textColor = textColorOverride ?: Color.White
    val lyricsLineBlur = lyricsLineBlurOverride ?: lyricsLineBlurPreference

    var isSelectionModeActive by rememberSaveable { mutableStateOf(false) }
    val selectedLineKeys = remember { mutableStateListOf<String>() }
    var showMaxSelectionToast by remember { mutableStateOf(false) }
    val maxSelectionLimit = 7
    var showShareDialog by remember { mutableStateOf(false) }
    var shareDialogData by remember { mutableStateOf<LyricsSharePayload?>(null) }
    var showShareImageDialog by remember { mutableStateOf(false) }

    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyrics =
        remember(currentLyrics, mediaMetadata?.id) {
            currentLyrics
                ?.takeIf { lyricsEntity -> lyricsEntity.id == mediaMetadata?.id }
                ?.lyrics
        }
    val showTranslations =
        remember(currentLyrics?.source, romanizationPreferences.showsRomanization, translationVisibleOverride) {
            val base =
                currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value ||
                    romanizationPreferences.showsRomanization
            base && (translationVisibleOverride ?: true)
        }
    val showPhoneticLines = phoneticVisibleOverride ?: true

    val playbackState by playerConnection.playbackState.collectAsState()
    var restartTick by remember { mutableIntStateOf(0) }
    var lastPlaybackState by remember { mutableStateOf(Player.STATE_READY) }
    LaunchedEffect(mediaMetadata?.id, playbackState) {
        if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_ENDED &&
            player.currentPosition < 1_000L
        ) {
            restartTick++
        } else if (playbackState == Player.STATE_READY &&
            lastPlaybackState == Player.STATE_BUFFERING &&
            player.currentPosition < 500L &&
            restartTick > 0
        ) {
            restartTick++
        }
        lastPlaybackState = playbackState
    }
    val lyricsSessionKey =
        remember(mediaMetadata?.id, lyrics, restartTick) {
            Triple(mediaMetadata?.id.orEmpty(), lyrics, restartTick)
        }

    val isSynced = remember(lyrics) { lyrics != null && (isLineSyncedLrc(lyrics!!) || isTtml(lyrics!!)) }
    val isTtmlFormat = remember(lyrics) { lyrics != null && isTtml(lyrics!!) }

    var parsedEntries by remember(lyrics) { mutableStateOf<List<LyricsEntry>?>(null) }
    LaunchedEffect(lyrics) {
        val text = lyrics
        if (text == null || text == LYRICS_NOT_FOUND) {
            parsedEntries = emptyList()
            return@LaunchedEffect
        }
        parsedEntries =
            withContext(Dispatchers.Default) {
                when {
                    isTtml(text) -> parseTtml(text)
                    isLineSyncedLrc(text) -> parseLyrics(text)
                    else ->
                        text
                            .lines()
                            .filter { it.isNotBlank() }
                            .map { line -> LyricsEntry(time = -1L, text = line.trim()) }
                }
            }
    }
    val lyricsEntries: List<LyricsEntry> = parsedEntries.orEmpty()

    val lyricsProviderNameRaw = currentLyrics?.providerName.orEmpty()
    val lyricsSourceLabel = stringResource(R.string.lyrics_from_source, lyricsProviderNameRaw)
    val lyricsProviderLabel =
        lyricsSourceLabel.takeIf { lyricsProviderNameRaw.isNotBlank() }

    var resolvedWriters by remember(mediaMetadata?.id) { mutableStateOf<String?>(null) }
    val writersLookupTitle = mediaMetadata?.title.orEmpty()
    val writersLookupArtist =
        mediaMetadata
            ?.artists
            ?.takeIf { it.isNotEmpty() }
            ?.joinToString { it.name }
            ?.trim()
            .orEmpty()
    LaunchedEffect(mediaMetadata?.id, writersLookupTitle, writersLookupArtist) {
        if (writersLookupTitle.isBlank()) return@LaunchedEffect

        val writers = CatalogueCoverProvider.resolveSongwriters(writersLookupTitle, writersLookupArtist)
        resolvedWriters = writers?.takeIf { it.isNotEmpty() }?.joinToString()
    }
    val ttmlWriters = lyrics?.let(::extractTtmlWriters).orEmpty()
    val composerWritersRaw =
        resolvedWriters?.takeIf { it.isNotBlank() }
            ?: ttmlWriters.takeIf { it.isNotBlank() }
            ?: mediaMetadata
                ?.artists
                ?.takeIf { it.isNotEmpty() }
                ?.joinToString { it.name }
                ?.trim()
                .orEmpty()
    val composerFooterLabel =
        stringResource(R.string.written_by, composerWritersRaw)
            .takeIf { composerWritersRaw.isNotBlank() }

    var karaokeBuild by remember(lyrics, isTtmlFormat) {
        mutableStateOf(KaraokeBuild(SyncedLyrics(emptyList()), emptyMap(), generation = 0))
    }
    val syncedLyrics = karaokeBuild.lyrics

    val karaokeGeneration = karaokeBuild.generation

    val aiRomanizationSessionKey =
        remember(mediaMetadata?.id, lyrics) { AiLyricsRomanization.sessionKey(mediaMetadata?.id, lyrics) }
    val aiRomanizationResult by AiLyricsRomanization.results.collectAsStateWithLifecycle()

    val aiRomanizedLines: List<String?> =
        remember(aiRomanizationResult, aiRomanizationSessionKey, aiRomanizationSettings.active, aiRomanizationSettings.configKey, lyricsEntries) {
            if (!aiRomanizationSettings.active) {
                emptyList()
            } else {
                AiLyricsRomanization.linesFor(
                    aiRomanizationSessionKey,
                    lyricsEntries.map { it.text },
                    aiRomanizationSettings,
                )
            }
        }
    LaunchedEffect(aiRomanizationSessionKey, lyricsEntries, aiRomanizationSettings) {
        if (!aiRomanizationSettings.active || !aiRomanizationSettings.auto) return@LaunchedEffect
        if (lyricsEntries.isEmpty()) return@LaunchedEffect
        AiLyricsRomanization.request(
            sessionKey = aiRomanizationSessionKey,
            lines = lyricsEntries.map { it.text },
            settings = aiRomanizationSettings,
        )
    }

    LaunchedEffect(lyricsEntries, romanizationPreferences, aiRomanizedLines, lyricsProviderLabel, composerFooterLabel, mediaMetadata?.id, singleActiveLine) {
        withContext(Dispatchers.Default) {
        fun publish(romanization: Map<Int, List<String?>>) {
            val previous = karaokeBuild
            val changesVisibleLines =
                previous.lyrics.lines.isNotEmpty() &&
                    romanization.renderedRomanization() != previous.romanization.renderedRomanization()
            // A line the karaoke builder cannot handle must not take the whole screen down.
            val built =
                try {
                    buildSyncedLyrics(
                        entries = lyricsEntries,
                        romanizationMap = romanization,
                        providerHeader = lyricsProviderLabel,
                        composerFooter = composerFooterLabel,
                        compactTranslation = singleActiveLine,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    SyncedLyrics(emptyList())
                }
            karaokeBuild =
                KaraokeBuild(
                    lyrics = built,
                    romanization = romanization,
                    generation = if (changesVisibleLines) previous.generation + 1 else previous.generation,
                    forEntries = lyricsEntries,
                )
        }

        val aiMap = aiRomanizationMap(lyricsEntries, aiRomanizedLines)

        val toRomanize: List<Pair<Int, LyricsEntry>> =
            if (!romanizationPreferences.isEnabled) {
                emptyList()
            } else {
                lyricsEntries.mapIndexedNotNull { index, entry ->
                    val hasProviderRomanization =
                        providedRomanizedTextForEntry(entry, romanizationPreferences) != null
                    if (hasProviderRomanization || shouldRomanizeLyricsLine(entry.text, romanizationPreferences)) {
                        index to entry
                    } else {
                        null
                    }
                }
            }
        if (toRomanize.isEmpty()) {
            publish(aiMap)
            return@withContext
        }

        val romanization =
            async {
                val jobs =
                    toRomanize.map { (index, entry) ->
                        async {
                            val romanized: List<String?> =
                                try {
                                    if (entry.words != null) {
                                        val mainWordCount = entry.words!!.count { !it.isBackground }
                                        providedRomanizedWordsForEntry(entry, mainWordCount, romanizationPreferences)
                                            ?: romanizeWordsForLine(

                                                words = entry.words!!.filter { !it.isBackground }.map { it.text },
                                                lineText = entry.text,
                                                preferences = romanizationPreferences,
                                            )
                                    } else {
                                        listOf(
                                            providedRomanizedTextForEntry(entry, romanizationPreferences)
                                                ?: romanizeLyricsLine(entry.text, romanizationPreferences),
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    reportException(e)
                                    if (entry.words != null) {
                                        List(entry.words!!.count { !it.isBackground }) { null }
                                    } else {
                                        listOf(null)
                                    }
                                }
                            index to romanized
                        }
                    }
                jobs.awaitAll().toMap()
            }

        val quickRomanization = withTimeoutOrNull(ROMANIZATION_FIRST_BUILD_GRACE_MS) { romanization.await() }
        if (quickRomanization != null) {
            publish(quickRomanization)
            return@withContext
        }
        publish(aiMap)
        publish(romanization.await())
        }
    }

    val leadMs = if (isTtmlFormat) TTML_LEAD_MS else LRC_LEAD_MS

    val latestSliderPositionProvider = rememberUpdatedState(sliderPositionProvider)
    val latestLyricsSyncOffset = rememberUpdatedState(lyricsSyncOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(playbackParameters.speed)
    val playbackPositionMs =
        remember(player) {
            mutableLongStateOf(player.currentPosition.coerceAtLeast(0L))
        }
    val playbackSyncPosition: () -> Int =
        remember {
            {
                (
                    playbackPositionMs.longValue +
                        latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value +
                        LYRIC_VISUAL_TUNING_OFFSET_MS
                ).coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        }
    val currentLineIndexState = remember { mutableIntStateOf(-1) }

    // Lines are timed in "lyrics time" = player position + sync offset + lead. Tapping a line used to
    // seek the player to the lyrics time, which with a sync offset landed up to 3 s away from the line.
    val playerPositionForLyricTime: (Int) -> Long =
        remember {
            { lyricTimeMs ->
                (lyricTimeMs.toLong() - latestLyricsSyncOffset.value - latestLeadMs.value - LYRIC_VISUAL_TUNING_OFFSET_MS)
                    .coerceAtLeast(0L)
            }
        }

    val activeKaraokeLine =
        remember(syncedLyrics, currentLineIndexState.intValue, singleActiveLine) {
            if (!singleActiveLine) {
                null
            } else {
                val index = currentLineIndexState.intValue
                val line = syncedLyrics.lines.getOrNull(index)
                if (line != null && line.start >= 0 && line.start < 86_400_000) line else null
            }
        }

    val latestSyncedLyrics = rememberUpdatedState(syncedLyrics)

    var positionResetCounter by remember { mutableIntStateOf(0) }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    val listState = key(lyricsSessionKey, positionResetCounter, karaokeGeneration) { rememberLazyListState() }

    var awaitingFirstFocus by
        remember(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
            mutableStateOf(isSynced)
        }

    // While the user browses (or auto-scroll is off, or lines are being selected) the list is held so
    // the karaoke view's own auto-scroll cannot pull it back: see LyricsScrollGuard.
    val holdLyricsList =
        isSynced && !singleActiveLine &&
            (isManualScrolling || !lyricsAutoScroll || isSelectionModeActive)
    val holdLyricsListState = rememberUpdatedState(holdLyricsList)
    val lyricsGuardScope = rememberCoroutineScope()
    val lyricsFlingBehavior = ScrollableDefaults.flingBehavior()
    val scrollGuard =
        remember(listState) { LyricsScrollGuard(listState, lyricsGuardScope, holdLyricsListState) }
    scrollGuard.flingBehavior = lyricsFlingBehavior
    val latestScrollGuard = rememberUpdatedState(scrollGuard)

    LaunchedEffect(scrollGuard, holdLyricsList) {
        if (!holdLyricsList) {
            scrollGuard.release()
            return@LaunchedEffect
        }
        while (isActive) {
            // Never grab the list in the middle of a gesture or fling: wait until it has settled.
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            delay(LYRIC_HOLD_SETTLE_MS)
            if (!listState.isScrollInProgress) scrollGuard.holdIfIdle()
            scrollGuard.awaitHold()
            if (!scrollGuard.isHolding) delay(LYRIC_HOLD_SETTLE_MS)
        }
    }

    // Multiplies the list alpha while it jumps far back to the sung line (see the resync effect).
    val returnVeil = remember(listState) { Animatable(1f) }

    LaunchedEffect(awaitingFirstFocus) {
        if (!awaitingFirstFocus) return@LaunchedEffect
        delay(LYRIC_FIRST_FOCUS_TIMEOUT_MS)
        awaitingFirstFocus = false
    }

    val firstFocusAlpha =
        animateFloatAsState(
            targetValue = if (awaitingFirstFocus) 0f else 1f,
            animationSpec =
                tween(
                    durationMillis = if (awaitingFirstFocus) 0 else LYRIC_FIRST_FOCUS_FADE_MS,
                    easing = LinearEasing,
                ),
            label = "lyrics-first-focus-alpha",
        )

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
        isSelectionModeActive = false
        selectedLineKeys.clear()

        currentLineIndexState.intValue = -1
    }

    LaunchedEffect(player, lyricsSessionKey, animationsDisabled, playbackParameters.speed) {
        var wasSliderActive = false
        var anchorPlayerPositionMs = player.currentPosition.coerceAtLeast(0L)
        var anchorFrameNanos = 0L
        var lastRawPositionMs = player.currentPosition.coerceAtLeast(0L)

        // Publishing every frame recomposes the whole karaoke view; small steps are skipped, seeks are not.
        var lastPublishedPositionMs = playbackPositionMs.longValue
        fun publishPosition(nextPosition: Long) {
            val delta = nextPosition - lastPublishedPositionMs
            if (delta >= LYRICS_POSITION_PUBLISH_MIN_DELTA_MS || delta < 0) {
                lastPublishedPositionMs = nextPosition
                if (playbackPositionMs.longValue != nextPosition) {
                    playbackPositionMs.longValue = nextPosition
                }
            }
        }

        var lastLyricsRef: SyncedLyrics? = null
        var cachedLineIdx = -1
        var cachedCurrentLineStart = Int.MIN_VALUE
        var cachedNextLineStart = Int.MAX_VALUE
        while (isActive) {
            val sliderPosition = latestSliderPositionProvider.value()
            val isSliderActive = sliderPosition != null
            if (isSliderActive && !wasSliderActive) {
                isManualScrolling = false
            }
            wasSliderActive = isSliderActive

            val rawPosition = (sliderPosition ?: player.currentPosition).coerceAtLeast(0L)

            val rawPlayerPosition = player.currentPosition.coerceAtLeast(0L)
            if (lastRawPositionMs - rawPlayerPosition > POSITION_RESET_BACKWARD_THRESHOLD_MS) {
                positionResetCounter += 1
                currentLineIndexState.intValue = -1
                // Force a new search: a jump back inside the same long line left the index at -1
                // (no highlighted line) until the next line started.
                cachedLineIdx = -1
            }
            lastRawPositionMs = rawPlayerPosition

            val effectivePositionMs: Long
            if (sliderPosition != null || !player.isPlaying || animationsDisabled) {
                anchorPlayerPositionMs = rawPosition
                anchorFrameNanos = 0L
                publishPosition(rawPosition)
                effectivePositionMs =
                    (rawPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
                if (sliderPosition == null) {
                    delay(if (player.isPlaying) 50L else 100L)
                } else {
                    withFrameNanos { }
                }
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }
                if (anchorFrameNanos == 0L) {
                    anchorFrameNanos = frameNanos
                    anchorPlayerPositionMs = rawPosition
                }

                val elapsedMs = ((frameNanos - anchorFrameNanos) / 1_000_000f) * latestPlaybackSpeed.value
                val projectedPosition = anchorPlayerPositionMs + elapsedMs.roundToLong()
                val driftMs = rawPosition - projectedPosition
                val nextPosition =
                    when {
                        driftMs > SMOOTH_PLAYBACK_MAX_FORWARD_DRIFT_MS ||
                            driftMs < -SMOOTH_PLAYBACK_MAX_BACKWARD_DRIFT_MS -> {
                            anchorPlayerPositionMs = rawPosition
                            anchorFrameNanos = frameNanos
                            rawPosition
                        }

                        driftMs != 0L -> {
                            projectedPosition + (driftMs * SMOOTH_PLAYBACK_DRIFT_CORRECTION).roundToLong()
                        }

                        else -> {
                            projectedPosition
                        }
                    }.coerceAtLeast(0L)

                publishPosition(nextPosition)
                effectivePositionMs =
                    (nextPosition + latestLyricsSyncOffset.value.toLong() +
                        latestLeadMs.value + LYRIC_VISUAL_TUNING_OFFSET_MS)
                        .coerceIn(0L, Int.MAX_VALUE.toLong())
            }

            val syncedLyricsNow = latestSyncedLyrics.value
            if (syncedLyricsNow.lines.isNotEmpty()) {
                val lyricsChanged = syncedLyricsNow !== lastLyricsRef
                if (lyricsChanged) {
                    lastLyricsRef = syncedLyricsNow
                    cachedLineIdx = -1
                }
                val pos = effectivePositionMs.toInt()
                val needsResearch =
                    cachedLineIdx == -1 ||
                        pos >= cachedNextLineStart ||
                        pos < cachedCurrentLineStart
                if (needsResearch) {
                    val newLineIdx = syncedLyricsNow.findLastStartedLineIndex(pos)
                    cachedLineIdx = newLineIdx
                    cachedCurrentLineStart =
                        if (newLineIdx >= 0) syncedLyricsNow.lines[newLineIdx].start else Int.MIN_VALUE
                    cachedNextLineStart =
                        syncedLyricsNow.lines.getOrNull(newLineIdx + 1)?.start ?: Int.MAX_VALUE
                    if (newLineIdx != currentLineIndexState.intValue) {
                        currentLineIndexState.intValue = newLineIdx
                    }
                }
            }
        }
    }

    val nestedScrollConnection =
        remember {
            var lastUserScrollEventMs = 0L
            object : NestedScrollConnection {
                private fun markManualScroll() {
                    val now = System.currentTimeMillis()
                    if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                        isManualScrolling = true
                        lastManualScrollTime = now
                        lastUserScrollEventMs = now
                    }
                }

                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (!isSelectionModeActive && source == NestedScrollSource.UserInput) {
                        markManualScroll()
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    // The fling is run by the guard at user priority, so the karaoke view's auto-scroll
                    // cannot stop it halfway and snap back; the list is then held where it lands.
                    if (isSynced && !singleActiveLine && isManualScrolling &&
                        latestScrollGuard.value.flingAndHold(-available.y)
                    ) {
                        lastManualScrollTime = System.currentTimeMillis()
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (!isSelectionModeActive && isManualScrolling) {
                        lastManualScrollTime = System.currentTimeMillis()
                    }
                    return Velocity.Zero
                }
            }
        }

    // By default the position chosen by hand is kept until "Resync" is pressed; with the setting off the
    // lyrics come back to the sung line after a few idle seconds.
    LaunchedEffect(isManualScrolling, lastManualScrollTime, holdManualScroll) {
        if (isManualScrolling && !holdManualScroll) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    LaunchedEffect(browseMode, isManualScrolling) {
        if (browseMode && !isManualScrolling) {
            isManualScrolling = true
            lastManualScrollTime = System.currentTimeMillis()
        }
    }

    val onLyricsScroll = LocalLyricsScrollListener.current
    LaunchedEffect(isManualScrolling) {
        onLyricsScroll(isManualScrolling)
    }

    val latestSyncedLyricsForScroll = rememberUpdatedState(syncedLyrics)
    val lyricsAutoScrollState = rememberUpdatedState(lyricsAutoScroll)

    // Following the sung line is left to the karaoke view itself (it anchors the line and animates the
    // other lines with springs). A second scroller here aimed at another point of the screen and the two
    // fought on every line. Only the first placement and the return after browsing are done here, at the
    // view's own anchor (item offset 0).
    LaunchedEffect(lyricsSessionKey, isSynced, positionResetCounter, karaokeGeneration) {
        if (!isSynced || singleActiveLine || !lyricsAutoScrollState.value) {
            awaitingFirstFocus = false
            return@LaunchedEffect
        }

        snapshotFlow { latestSyncedLyricsForScroll.value.lines.isNotEmpty() }.first { it }
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }

        val index =
            withTimeoutOrNull(LYRIC_FIRST_FOCUS_TIMEOUT_MS) {
                snapshotFlow { currentLineIndexState.intValue }.first { it >= 0 }
            } ?: -1
        val lines = latestSyncedLyricsForScroll.value.lines
        if (index >= 0) {
            val target = mainLyricLineIndex(lines, index)
            if (target in 0 until listState.layoutInfo.totalItemsCount) {
                runCatching { listState.scrollToItem(target, 0) }
            }
        }
        awaitingFirstFocus = false
    }

    var resyncRequests by remember { mutableIntStateOf(0) }
    LaunchedEffect(browseMode) {
        if (!browseMode && isManualScrolling) {
            isManualScrolling = false
            resyncRequests++
        }
    }
    // Line to bring back to the anchor (a tapped line); -1 = the sung line.
    var resyncTarget by remember { mutableIntStateOf(-1) }
    val resyncHandled = remember { intArrayOf(0) }
    val wasBrowsing = remember { booleanArrayOf(false) }
    LaunchedEffect(isManualScrolling, resyncRequests) {
        val requested = resyncRequests != resyncHandled[0]
        resyncHandled[0] = resyncRequests
        val stoppedBrowsing = wasBrowsing[0] && !isManualScrolling
        wasBrowsing[0] = isManualScrolling
        if (isManualScrolling) return@LaunchedEffect
        if (!requested && !(stoppedBrowsing && lyricsAutoScrollState.value)) return@LaunchedEffect
        if (!isSynced || singleActiveLine) return@LaunchedEffect

        latestScrollGuard.value.release()
        val lines = latestSyncedLyricsForScroll.value.lines
        val tapped = resyncTarget
        resyncTarget = -1
        val index = if (tapped >= 0) tapped else currentLineIndexState.intValue
        if (lines.isEmpty()) return@LaunchedEffect
        val target = if (index < 0) 0 else mainLyricLineIndex(lines, index)
        if (target in 0 until listState.layoutInfo.totalItemsCount) {
            val distance = abs(target - listState.firstVisibleItemIndex)
            if (distance <= LYRIC_SMOOTH_RETURN_MAX_LINES) {
                listState.animateScrollToItem(target, 0)
            } else {
                // A long way back: the karaoke view springs every line from its old place after such a
                // jump, which shows as lines piling up on each other. Hide the jump instead.
                try {
                    returnVeil.animateTo(0f, tween(LYRIC_RETURN_FADE_OUT_MS, easing = LinearEasing))
                    listState.scrollToItem(target, 0)
                    delay(LYRIC_RETURN_SETTLE_MS)
                } finally {
                    withContext(NonCancellable) {
                        returnVeil.animateTo(1f, tween(LYRIC_RETURN_FADE_IN_MS, easing = LinearEasing))
                    }
                }
            }
        }
    }

    BackHandler(enabled = isSelectionModeActive) {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }

    LaunchedEffect(showMaxSelectionToast) {
        if (showMaxSelectionToast) {
            Toast
                .makeText(
                    context,
                    context.getString(R.string.max_selection_limit, maxSelectionLimit),
                    Toast.LENGTH_SHORT,
                ).show()
            showMaxSelectionToast = false
        }
    }

    val activity = context as? Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val typography = MaterialTheme.typography
    val normalTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.headlineMedium.copy(
                fontSize = lyricsTextSize.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = lyricsFontFamily ?: typography.headlineMedium.fontFamily,
            )
        }
    val accompanimentTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.82f).sp,
                fontFamily = lyricsFontFamily ?: typography.titleLarge.fontFamily,
            )
        }
    val translationTextStyle =
        remember(typography, lyricsTextSize, lyricsFontFamily) {
            typography.titleLarge.copy(
                fontSize = (lyricsTextSize * 0.72f).sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = lyricsFontFamily ?: typography.titleLarge.fontFamily,
            )
        }
    val phoneticTextStyle =
        remember(typography, lyricsTextSize) {
            typography.bodyMedium.copy(
                fontSize = (lyricsTextSize * 0.55f).sp,
                fontWeight = FontWeight.Normal,
            )
        }
    val plainLyrics =
        remember(lyricsEntries, isSynced, lyricsProviderLabel, composerFooterLabel) {
            val lyricItems =
                if (isSynced) {
                    emptyList()
                } else {
                    lyricsEntries.mapIndexedNotNull { index, entry ->
                        val text = entry.text.trim()
                        if (text.isBlank()) {
                            null
                        } else {
                            val selectionId = "plain:$index:${text.hashCode()}"
                            PlainLyricLine(
                                itemId = "$selectionId#$index",
                                selectionId = selectionId,
                                text = text,
                            )
                        }
                    }
                }
            val providerHeaderLine =
                lyricsProviderLabel
                    ?.takeIf { lyricItems.isNotEmpty() }
                    ?.let { text ->
                        PlainLyricLine(
                            itemId = "provider_header",
                            selectionId = "plain:provider_header:${text.hashCode()}",
                            text = text,
                            isMetadata = true,
                        )
                    }
            val composerFooterLine =
                composerFooterLabel
                    ?.takeIf { lyricItems.isNotEmpty() }
                    ?.let { text ->
                        PlainLyricLine(
                            itemId = "composer_footer",
                            selectionId = "plain:composer_footer:${text.hashCode()}",
                            text = text,
                            isMetadata = true,
                        )
                    }
            PlainLyrics(
                items =
                    buildList {
                        if (providerHeaderLine != null) add(providerHeaderLine)
                        addAll(lyricItems)
                        if (composerFooterLine != null) add(composerFooterLine)
                    },
            )
        }
    val selectionLines =
        remember(isSynced, syncedLyrics, plainLyrics) {
            if (isSynced) {
                syncedLyrics.lines.mapIndexedNotNull { index, line ->
                    val text = line.lineText()
                    if (text.isBlank()) {
                        null
                    } else {
                        val isMetadataLine = line.start < 0 || line.start >= 86_400_000
                        if (isMetadataLine) {
                            null
                        } else {
                            val selectionId = line.selectionKey(text)
                            val (translation, romanisation) = line.shareTranslationParts()
                            LyricSelectionLine(
                                itemId = "$selectionId#$index",
                                selectionId = selectionId,
                                text = text,
                                translation = translation,
                                romanisation = romanisation,
                            )
                        }
                    }
                }
            } else {
                plainLyrics.items.mapNotNull { line ->

                    if (line.isMetadata) {
                        null
                    } else {
                        LyricSelectionLine(
                            itemId = line.itemId,
                            selectionId = line.selectionId,
                            text = line.text,
                        )
                    }
                }
            }
        }
    val selectedLineKeySnapshot = selectedLineKeys.toList()
    val selectedLineKeySet = remember(selectedLineKeySnapshot) { selectedLineKeySnapshot.toSet() }
    val dismissSelection = {
        isSelectionModeActive = false
        selectedLineKeys.clear()
    }
    val toggleSelectedLine: (String) -> Unit = { lineKey ->
        if (selectedLineKeys.contains(lineKey)) {
            selectedLineKeys.remove(lineKey)
            if (selectedLineKeys.isEmpty()) isSelectionModeActive = false
        } else if (selectedLineKeys.size < maxSelectionLimit) {
            selectedLineKeys.add(lineKey)
        } else {
            showMaxSelectionToast = true
        }
    }
    val shareSelectedLyrics: () -> Unit = {
        val metadata = mediaMetadata
        if (metadata != null) {
            val selectedEntries =
                selectionLines.filter { line -> line.selectionId in selectedLineKeySet }
            val selectedLyricsText =
                selectedEntries.joinToString("\n") { line -> line.text }
            if (selectedLyricsText.isNotBlank()) {
                shareDialogData =
                    LyricsSharePayload(
                        lyricsText = selectedLyricsText,
                        songTitle = metadata.title,
                        artists = metadata.artists.joinToString { it.name },
                        lines = selectedEntries.map { line ->
                            LyricsShareLine(
                                text = line.text,
                                translation = line.translation,
                                romanisation = line.romanisation,
                            )
                        },
                    )
                showShareDialog = true
            }
        }
        dismissSelection()
    }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = 12.dp)

                .graphicsLayer { alpha = firstFocusAlpha.value * returnVeil.value },
    ) {
        when {
            lyrics == LYRICS_NOT_FOUND -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            lyrics == null || parsedEntries == null -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            isSynced && lyricsEntries.isNotEmpty() && karaokeBuild.forEntries !== lyricsEntries -> {
                ShimmerHost {
                    repeat(6) { TextPlaceholder() }
                }
            }

            isSynced && syncedLyrics.lines.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced && plainLyrics.items.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.lyrics_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            !isSynced -> {
                PlainLyricsView(
                    lines = plainLyrics,
                    listState = listState,
                    selectedLineKeys = selectedLineKeySet,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                    onLineClicked = { lineKey ->
                        if (isSelectionModeActive) toggleSelectedLine(lineKey)
                    },
                    onLinePressed = { lineKey ->
                        if (!isSelectionModeActive) {
                            isSelectionModeActive = true
                            if (!selectedLineKeys.contains(lineKey)) {
                                selectedLineKeys.add(lineKey)
                            }
                        } else if (!selectedLineKeys.contains(lineKey)) {
                            toggleSelectedLine(lineKey)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            singleActiveLine && isSynced -> {
                key(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalTextStyle provides phoneticTextStyle,
                    ) {
                        SingleActiveKaraokeLine(
                            activeLine = activeKaraokeLine,
                            currentPosition = playbackSyncPosition,
                            textColor = textColor,
                            normalTextStyle = normalTextStyle,
                            accompanimentTextStyle = accompanimentTextStyle,
                            phoneticTextStyle = phoneticTextStyle,
                            showTranslation = showTranslations,
                            onLineClick = { line ->
                                if (lyricsClick && line.isTimedLyricLine()) {
                                    player.seekTo(playerPositionForLyricTime(line.start))
                                    isManualScrolling = false
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            else -> {
                BoxWithConstraints(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .nestedScroll(nestedScrollConnection),
                ) {
                    // Must not follow the height of this box: in the players the playback controls
                    // appear under the lyrics on every touch and hide again a few seconds later, which
                    // resizes this box. The karaoke view restarts its auto-scroll whenever `offset`
                    // changes, so a height-based offset sent the list back to the sung line on every
                    // touch. The screen height is stable (it only changes on rotation).
                    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                    val lyricsViewportOffset =
                        remember(screenHeight) {
                            val proportional = screenHeight * LYRIC_ANCHOR_SCREEN_RATIO
                            if (proportional > 112.dp) proportional else 112.dp
                        }

                    key(lyricsSessionKey, positionResetCounter, karaokeGeneration) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            androidx.compose.material3.LocalTextStyle provides phoneticTextStyle,
                        ) {
                            if (enhancedLyricsStyle == EnhancedLyricsStyle.SPICY_MOBILE) {
                                SpicyLyricsView(
                                    lyrics = syncedLyrics,
                                    currentPosition = playbackSyncPosition,
                                    activeLineIndex = currentLineIndexState.intValue,
                                    onLineClicked = { line ->
                                        if (isSelectionModeActive) {
                                            toggleSelectedLine(line.selectionKey())
                                        } else if (lyricsClick && isSynced && line.isTimedLyricLine()) {
                                            player.seekTo(playerPositionForLyricTime(line.start))
                                            if (isManualScrolling) {
                                                resyncTarget = syncedLyrics.lines.indexOf(line)
                                                resyncRequests++
                                            }
                                            isManualScrolling = false
                                        }
                                    },
                                    onLinePressed = { line ->
                                        val lineKey = line.selectionKey()
                                        if (!isSelectionModeActive) {
                                            isSelectionModeActive = true
                                            if (!selectedLineKeys.contains(lineKey)) {
                                                selectedLineKeys.add(lineKey)
                                            }
                                        } else if (!selectedLineKeys.contains(lineKey)) {
                                            toggleSelectedLine(lineKey)
                                        }
                                    },
                                    textColor = textColor,
                                    normalTextStyle = normalTextStyle,
                                    accompanimentTextStyle = accompanimentTextStyle,
                                    translationTextStyle = translationTextStyle,
                                    phoneticTextStyle = phoneticTextStyle,
                                    useBlurEffect = lyricsLineBlur && !animationsDisabled,
                                    showTranslation = showTranslations,
                                    showPhonetic = showPhoneticLines,
                                    anchorTopPadding = lyricsViewportOffset,
                                    listState = listState,
                                    manualScrolling = isManualScrolling,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                KaraokeLyricsView(
                                    listState = listState,
                                    lyrics = syncedLyrics,
                                    currentPosition = playbackSyncPosition,
                                    onLineClicked = { line ->
                                        if (isSelectionModeActive) {
                                            toggleSelectedLine(line.selectionKey())
                                        } else if (lyricsClick && isSynced && line.isTimedLyricLine()) {
                                            player.seekTo(playerPositionForLyricTime(line.start))
                                            if (isManualScrolling) {
                                                resyncTarget = syncedLyrics.lines.indexOf(line)
                                                resyncRequests++
                                            }
                                            isManualScrolling = false
                                        }
                                    },
                                    onLinePressed = { line ->
                                        val lineKey = line.selectionKey()
                                        if (!isSelectionModeActive) {
                                            isSelectionModeActive = true
                                            if (!selectedLineKeys.contains(lineKey)) {
                                                selectedLineKeys.add(lineKey)
                                            }
                                        } else if (!selectedLineKeys.contains(lineKey)) {
                                            toggleSelectedLine(lineKey)
                                        }
                                    },
                                    textColor = textColor,
                                    normalLineTextStyle = normalTextStyle,
                                    accompanimentLineTextStyle = accompanimentTextStyle,
                                    phoneticTextStyle = phoneticTextStyle,
                                    blendMode = BlendMode.SrcOver,

                                    useBlurEffect = lyricsLineBlur && !animationsDisabled,
                                    showTranslation = showTranslations,

                                    showPhonetic = showPhoneticLines,
                                    offset = lyricsViewportOffset,

                                    keepAliveZone = 8.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }

                    val resyncDirection by rememberLyricsResyncDirection(listState) { currentLineIndexState.intValue }
                    LyricsResyncButton(
                        visible = isManualScrolling && isSynced && !isSelectionModeActive,
                        direction = resyncDirection,
                        onClick = {
                            isManualScrolling = false
                            resyncRequests++
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 16.dp),
                    )
                }
            }
        }

    }

    if (isSelectionModeActive && selectionLines.isNotEmpty()) {
        LyricsSelectionBottomSheet(
            lines = selectionLines,
            selectedLineKeys = selectedLineKeySet,
            onToggleLine = toggleSelectedLine,
            onDismissRequest = dismissSelection,
            onShareSelected = shareSelectedLyrics,
        )
    }

    if (showShareDialog && shareDialogData != null) {
        val sharePayload = shareDialogData!!
        val lyricsText = sharePayload.lyricsText
        val songTitle = sharePayload.songTitle
        val artists = sharePayload.artists
        BasicAlertDialog(onDismissRequest = { showShareDialog = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier =
                    Modifier
                        .padding(16.dp)
                        .fillMaxWidth(0.85f),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.share_lyrics),
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    shareLyricsAsText(
                                        context = context,
                                        payload = LyricsSharePayload(lyricsText, songTitle, artists),
                                        songId = mediaMetadata?.id,
                                    )
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_text),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showShareImageDialog = true
                                    showShareDialog = false
                                }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.share),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.share_as_image),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium,
                            modifier =
                                Modifier
                                    .clickable { showShareDialog = false }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }
            }
        }
    }

    if (showShareImageDialog && shareDialogData != null) {
        LyricsShareImageDialog(
            mediaMetadata = mediaMetadata,
            payload = shareDialogData!!,
            onDismissRequest = { showShareImageDialog = false },
        )
    }
}

@Immutable
private data class PlainLyrics(
    val items: List<PlainLyricLine>,
)

@Immutable
private data class PlainLyricLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
    val isMetadata: Boolean = false,
)

@Immutable
private data class LyricSelectionLine(
    val itemId: String,
    val selectionId: String,
    val text: String,
    val translation: String? = null,
    val romanisation: String? = null,
)

private fun ISyncedLine.shareTranslationParts(): Pair<String?, String?> {
    val raw = (this as? SyncedLine)?.translation?.trim()?.takeIf { it.isNotEmpty() } ?: return null to null
    val parts = raw.split("\n\n")
    // Built as "romanised\n\ntranslation" (or "romanised\ntranslation" in compact mode).
    if (parts.size >= 2) {
        return parts.last().trim() to parts.first().trim()
    }
    val compactParts = raw.split('\n')
    if (compactParts.size >= 2) {
        return compactParts.last().trim() to compactParts.first().trim()
    }
    return null to raw
}

@Composable
private fun PlainLyricsView(
    lines: PlainLyrics,
    listState: LazyListState,
    selectedLineKeys: Set<String>,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentPadding =
        remember {
            PaddingValues(
                start = 12.dp,
                top = 120.dp,
                end = 12.dp,
                bottom = 96.dp,
            )
        }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = lines.items,
            key = { line -> line.itemId },
            contentType = { "plain_lyric_line" },
        ) { line ->
            PlainLyricLineItem(
                line = line,
                selected = line.selectionId in selectedLineKeys,
                textColor = textColor,
                textStyle = textStyle,
                onLineClicked = onLineClicked,
                onLinePressed = onLinePressed,
            )
        }
    }
}

@Composable
private fun PlainLyricLineItem(
    line: PlainLyricLine,
    selected: Boolean,
    textColor: Color,
    textStyle: TextStyle,
    onLineClicked: (String) -> Unit,
    onLinePressed: (String) -> Unit,
) {
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            textColor
        }

    Text(
        text = line.text,
        style =
            if (line.isMetadata) {
                textStyle.copy(
                    fontSize = (textStyle.fontSize.value * 0.38f).sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            } else {
                textStyle
            },
        color = contentColor,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .combinedClickable(
                    onClick = { onLineClicked(line.selectionId) },
                    onLongClick = { onLinePressed(line.selectionId) },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun LyricsSelectionBottomSheet(
    lines: List<LyricSelectionLine>,
    selectedLineKeys: Set<String>,
    onToggleLine: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onShareSelected: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val firstSelectedIndex =
        remember(lines, selectedLineKeys) {
            lines
                .indexOfFirst { line -> line.selectionId in selectedLineKeys }
                .coerceAtLeast(0)
        }
    val sheetListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = firstSelectedIndex,
        )
    val selectedCount = selectedLineKeys.size

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        KeepStatusBarHiddenInDialog()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.share_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_element, selectedCount, selectedCount),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.close))
                }
            }

            LazyColumn(
                state = sheetListState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = lines,
                    key = { line -> line.itemId },
                    contentType = { "lyric_selection_line" },
                ) { line ->
                    LyricsSelectionLineItem(
                        line = line,
                        selected = line.selectionId in selectedLineKeys,
                        onClick = { onToggleLine(line.selectionId) },
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(R.string.cancel))
                }
                Button(
                    onClick = onShareSelected,
                    enabled = selectedCount > 0,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = stringResource(R.string.share_selected))
                }
            }
        }
    }
}

@Composable
private fun LyricsSelectionLineItem(
    line: LyricSelectionLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineSmall,
                color = contentColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

/** The karaoke view anchors main lines: a background-vocal line is placed with the main line before it. */
private fun mainLyricLineIndex(
    lines: List<ISyncedLine>,
    index: Int,
): Int {
    if (lines.isEmpty()) return -1
    var target = index.coerceIn(0, lines.lastIndex)
    while (target > 0 && lines[target] is KaraokeLine.AccompanimentKaraokeLine) target--
    return target
}

private fun ISyncedLine.lineText(): String =
    when (this) {
        is KaraokeLine -> syllables.joinToString("") { it.content }
        is SyncedLine -> content
        else -> ""
    }

private fun ISyncedLine.selectionKey(text: String = lineText()): String = "$start:$end:${text.hashCode()}"

private fun SyncedLyrics.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (lines[mid].start < 0) {
            low = mid + 1
        } else if (lines[mid].start <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

private fun List<WordTimestamp>.toKaraokeSyllables(phonetics: List<String?>): List<KaraokeSyllable> =
    mapIndexed { index, word ->
        val start = word.startTime.toMilliseconds()
        val nextStart = getOrNull(index + 1)?.startTime?.toMilliseconds()
        val rawEnd = word.endTime.toMilliseconds()
        val end =
            nextStart
                ?.let { minOf(rawEnd, it) }
                ?: rawEnd

        val rawPhonetic = phonetics.getOrNull(index)
        val paddedPhonetic =
            if (rawPhonetic.isNullOrEmpty()) {
                rawPhonetic
            } else {
                "$rawPhonetic "
            }

        KaraokeSyllable(
            content = word.text,
            start = start,
            end = end.coerceAtLeast(start + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = paddedPhonetic,
        )
    }

private fun Double.toMilliseconds(): Int = (this * 1000.0).roundToInt().coerceAtLeast(0)

private fun aiRomanizationMap(
    entries: List<LyricsEntry>,
    aiLines: List<String?>,
): Map<Int, List<String?>> {
    if (aiLines.isEmpty() || entries.isEmpty()) return emptyMap()
    val map = mutableMapOf<Int, List<String?>>()
    entries.forEachIndexed { index, entry ->
        val romanized = aiLines.getOrNull(index)?.trim()?.takeIf { it.isNotEmpty() } ?: return@forEachIndexed
        val words = entry.words?.filter { !it.isBackground }
        map[index] =
            if (!words.isNullOrEmpty()) {
                distributePhonetics(words.map { it.text }, romanized)
            } else {
                listOf(romanized)
            }
    }
    return map
}

private fun distributePhonetics(
    words: List<String>,
    romanized: String,
): List<String?> {
    if (words.isEmpty()) return emptyList()
    val tokens = romanized.split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return List(words.size) { null }
    if (words.size == 1) return listOf(tokens.joinToString(" "))

    val out = MutableList<String?>(words.size) { null }
    tokens.forEachIndexed { tokenIndex, token ->
        val anchor = (tokenIndex * words.size / tokens.size).coerceIn(0, words.size - 1)
        out[anchor] = listOfNotNull(out[anchor], token).joinToString(" ")
    }
    return out
}

private fun buildSyncedLyrics(
    entries: List<LyricsEntry>,
    romanizationMap: Map<Int, List<String?>>,
    providerHeader: String?,
    composerFooter: String? = null,
    compactTranslation: Boolean = false,
): SyncedLyrics {
    if (entries.isEmpty()) return SyncedLyrics(emptyList())
    val lines = mutableListOf<ISyncedLine>()

    if (!providerHeader.isNullOrBlank()) {
        lines.add(
            SyncedLine(
                content = providerHeader,
                translation = null,
                start = -2,
                end = -1,
            ),
        )
    }

    entries.forEachIndexed { index, entry ->
        if (entry.time < 0L) return@forEachIndexed
        if (entry.isInstrumental) return@forEachIndexed
        if (entry.text.isBlank() && entry.words.isNullOrEmpty()) return@forEachIndexed

        if (entry.words != null && hasTrueWordSync(entry)) {
            val translation = providedTranslationTextForEntry(entry)
            val mainWords = entry.words!!.filter { !it.isBackground }
            val bgWords = entry.words!!.filter { it.isBackground }
            val alignment =
                when (entry.agent?.lowercase()) {
                    "v2" -> KaraokeAlignment.End
                    else -> KaraokeAlignment.Start
                }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else entry.words!!
            val wordPhonetics = romanizationMap[index] ?: emptyList()
            val mainSyllables = wordsForMain.toKaraokeSyllables(wordPhonetics)

            val lineStart = mainSyllables.first().start
            val lineEnd = mainSyllables.last().end
            if (lineEnd <= lineStart) {
                // Broken word timings: keep the line, timed as a whole, instead of dropping its text.
                lines.add(
                    buildLineSyncedLrcLine(
                        entry = entry,
                        romanizedText = romanizationMap[index]?.filterNotNull()?.joinToString(" "),
                        start = entry.time.toInt(),
                        end = lineSyncedEnd(entries, index),
                        compactTranslation = compactTranslation,
                    ),
                )
                return@forEachIndexed
            }

            val lineTranslation = translation

            val accompanimentLines =
                if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                    val bgSyllables = bgWords.toKaraokeSyllables(emptyList())
                    val bgStart = bgSyllables.first().start
                    val bgEnd = bgSyllables.last().end
                    if (bgEnd > bgStart) {
                        listOf(
                            KaraokeLine.AccompanimentKaraokeLine(
                                syllables = bgSyllables,
                                translation = null,
                                alignment = alignment,
                                start = bgStart,
                                end = bgEnd,
                                phonetic = null,
                            ),
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }

            lines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = lineTranslation,
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = null,
                    accompanimentLines = accompanimentLines,
                ),
            )
        } else {
            val lineEnd = lineSyncedEnd(entries, index)
            lines.add(
                buildLineSyncedLrcLine(
                    entry = entry,
                    romanizedText = romanizationMap[index]?.firstOrNull(),
                    start = entry.time.toInt(),
                    end = lineEnd,
                    compactTranslation = compactTranslation,
                ),
            )
        }
    }

    if (!composerFooter.isNullOrBlank()) {
        lines.add(
            SyncedLine(
                content = composerFooter,
                translation = null,
                start = 86_400_000,
                end = 86_400_001,
            ),
        )
    }

    // The current line is found by a binary search on `start`: word-timed lines take their start from
    // their first word, which can come slightly before the previous line's start. Keep them ordered
    // (stable sort: the header stays first, the credits last).
    return SyncedLyrics(lines = lines.sortedBy { it.start })
}

/** End of a line-timed line: the next line that starts later, or a few seconds when there is none. */
private fun lineSyncedEnd(
    entries: List<LyricsEntry>,
    index: Int,
): Int {
    val entry = entries[index]
    val nextStart =
        entries
            .asSequence()
            .drop(index + 1)
            .firstOrNull { it.time > entry.time }
            ?.time
    return (nextStart ?: (entry.time + LINE_SYNCED_TRAILING_LINE_DURATION_MS)).toInt()
}

/** A sung line (not the provider header or the credits, which use negative / far-away times). */
private fun ISyncedLine.isTimedLyricLine(): Boolean = start in 0 until 86_400_000

private fun buildLineSyncedLrcLine(
    entry: LyricsEntry,
    romanizedText: String?,
    start: Int,
    end: Int,
    compactTranslation: Boolean = false,
): ISyncedLine {
    val translation = providedTranslationTextForEntry(entry)
    val normalizedRomanizedText = romanizedText?.trim()?.takeIf { it.isNotEmpty() }

    if (normalizedRomanizedText == null) {
        return SyncedLine(
            content = entry.text,
            translation = translation,
            start = start,
            end = end,
        )
    }

    val combinedTranslation =
        when {
            translation.isNullOrBlank() -> normalizedRomanizedText
            compactTranslation -> "$normalizedRomanizedText\n$translation"
            else -> "$normalizedRomanizedText\n\n$translation"
        }

    return SyncedLine(
        content = entry.text,
        translation = combinedTranslation,
        start = start,
        end = end,
    )
}

@Composable
private fun SingleActiveKaraokeLine(
    activeLine: ISyncedLine?,
    currentPosition: () -> Int,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    showTranslation: Boolean,
    onLineClick: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = activeLine,
        transitionSpec = {
            (fadeIn(tween(durationMillis = 240, easing = LinearEasing)) +
                slideInVertically(
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 5 },
                )).togetherWith(fadeOut(tween(durationMillis = 160, easing = LinearEasing)))
        },
        contentAlignment = Alignment.BottomStart,
        label = "single-active-lyric-line",
        modifier = modifier,
    ) { line ->
        if (line != null) {
            SingleActiveLineCluster(
                line = line,
                currentPosition = currentPosition,
                textColor = textColor,
                normalTextStyle = normalTextStyle,
                accompanimentTextStyle = accompanimentTextStyle,
                phoneticTextStyle = phoneticTextStyle,
                showTranslation = showTranslation,
                onLineClick = onLineClick,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SingleActiveLineCluster(
    line: ISyncedLine,
    currentPosition: () -> Int,
    textColor: Color,
    normalTextStyle: TextStyle,
    accompanimentTextStyle: TextStyle,
    phoneticTextStyle: TextStyle,
    showTranslation: Boolean,
    onLineClick: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.clickable(
                enabled = line.start > 0,
            ) { onLineClick(line) },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        when (line) {
            is KaraokeLine -> {
                (line as? KaraokeLine.MainKaraokeLine)?.accompanimentLines?.forEach { accompaniment ->
                    val accompanimentText = (accompaniment as? KaraokeLine)?.syllables?.joinSyllableContents().orEmpty()
                    if (accompanimentText.isNotBlank()) {
                        Text(
                            text = accompanimentText,
                            style = accompanimentTextStyle,
                            color = textColor.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                val phonetic = line.syllables.mapNotNull { it.phonetic?.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                if (phonetic.isNotEmpty()) {
                    Text(
                        text = phonetic,
                        style = phoneticTextStyle,
                        color = textColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                KaraokeSweepText(
                    syllables = line.syllables,
                    currentPosition = currentPosition,
                    textColor = textColor,
                    textStyle = normalTextStyle,
                )

                if (showTranslation) {
                    TranslationStack(
                        translation = line.translation,
                        textColor = textColor,
                        phoneticTextStyle = phoneticTextStyle,
                    )
                }
            }

            else -> {
                Text(
                    text = line.lineText(),
                    style = normalTextStyle,
                    color = textColor,
                )
                if (showTranslation) {
                    TranslationStack(
                        translation = (line as? SyncedLine)?.translation,
                        textColor = textColor,
                        phoneticTextStyle = phoneticTextStyle,
                    )
                }
            }
        }
    }
}

@Composable
private fun KaraokeSweepText(
    syllables: List<KaraokeSyllable>,
    currentPosition: () -> Int,
    textColor: Color,
    textStyle: TextStyle,
) {
    val position = currentPosition()
    val unsungColor = textColor.copy(alpha = 0.45f)
    val annotated = buildAnnotatedString {
        syllables.forEachIndexed { index, syllable ->
            val span = (syllable.end - syllable.start).coerceAtLeast(1)
            val progress = ((position - syllable.start).toFloat() / span).coerceIn(0f, 1f)
            val color = lerp(unsungColor, textColor, progress)
            withStyle(SpanStyle(color = color)) {
                append(syllable.content)
                val next = syllables.getOrNull(index + 1)
                if (next != null && needsLatinWordGap(syllable.content, next.content)) {
                    append(" ")
                }
            }
        }
    }
    Text(text = annotated, style = textStyle)
}

@Composable
private fun TranslationStack(
    translation: String?,
    textColor: Color,
    phoneticTextStyle: TextStyle,
) {
    if (translation.isNullOrBlank()) return
    translation
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { row ->
            Text(
                text = row,
                style = phoneticTextStyle,
                color = textColor.copy(alpha = 0.85f),
            )
        }
}

private fun needsLatinWordGap(previous: String, next: String): Boolean {
    val prevTail = previous.lastOrNull()
    val nextHead = next.firstOrNull()
    return isLatinWordChar(prevTail) && isLatinWordChar(nextHead)
}

private fun isLatinWordChar(ch: Char?): Boolean {
    if (ch == null) return false
    return ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '\'' || ch == ','
}

private fun List<KaraokeSyllable>.joinSyllableContents(): String {
    val builder = StringBuilder()
    forEachIndexed { index, syllable ->
        builder.append(syllable.content)
        val next = getOrNull(index + 1)
        if (next != null && needsLatinWordGap(syllable.content, next.content)) {
            builder.append(' ')
        }
    }
    return builder.toString()
}
