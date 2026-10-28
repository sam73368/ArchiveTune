/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.appfunctions

import android.content.ComponentName
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.playback.MusicService
import timber.log.Timber

/**
 * What ArchiveTune is playing right now.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class NowPlaying(
    /** True when a track is currently playing, false when paused, stopped or the queue is empty. */
    val isPlaying: Boolean,
    /** Title of the current track, or null when nothing is loaded. */
    val title: String?,
    /** Artist of the current track, or null when unknown or nothing is loaded. */
    val artist: String?,
    /** Album of the current track, or null when unknown or nothing is loaded. */
    val album: String?,
)

/**
 * Playback actions that assistants (for example Gemini) can run in ArchiveTune.
 *
 * Every function talks to the app's media session, exactly like a Bluetooth remote or Android Auto
 * would, so the queue, crossfade and stream resolution behave as they do from the app itself.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "ArchiveTuneAppFunctionService",
    appFunctionXmlFileName = "archivetune_app_function_service",
)
abstract class BaseArchiveTuneAppFunctionService : AppFunctionService() {
    /**
     * Search for a song, album or artist and start playing the best match.
     *
     * @param query What to play, for example "Fuck You Dr. Dre" or "Eminem The Eminem Show". Must not be blank.
     * @return The track that started, or the player state if nothing started within 25 seconds.
     * @throws AppFunctionInvalidArgumentException If the query is blank. If thrown, ask the user what to play.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playMusic(query: String): NowPlaying {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            throw AppFunctionInvalidArgumentException("Query cannot be empty")
        }
        return withController { controller ->
            val request =
                MediaItem
                    .Builder()
                    .setMediaId("appfunction_search")
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(trimmed).build())
                    .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery(trimmed).build())
                    .build()
            controller.setMediaItem(request)
            controller.prepare()
            controller.play()
            withTimeoutOrNull(PLAY_WAIT_MS) {
                while (controller.currentMediaItem.let { it == null || it.mediaId == request.mediaId }) {
                    delay(POLL_MS)
                }
                while (!controller.isPlaying && controller.playbackState != Player.STATE_ENDED) {
                    delay(POLL_MS)
                }
            }
            controller.snapshot()
        }
    }

    /**
     * Pause the music.
     *
     * @return The player state after pausing.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun pauseMusic(): NowPlaying =
        withController { controller ->
            controller.pause()
            controller.snapshot()
        }

    /**
     * Resume the music that was paused.
     *
     * @return The player state after resuming.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun resumeMusic(): NowPlaying =
        withController { controller ->
            controller.play()
            controller.snapshot()
        }

    /**
     * Skip to the next track in the queue.
     *
     * @return The player state after skipping.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun skipToNext(): NowPlaying =
        withController { controller ->
            controller.seekToNextMediaItem()
            delay(SETTLE_MS)
            controller.snapshot()
        }

    /**
     * Go back to the previous track in the queue.
     *
     * @return The player state after going back.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun skipToPrevious(): NowPlaying =
        withController { controller ->
            controller.seekToPreviousMediaItem()
            delay(SETTLE_MS)
            controller.snapshot()
        }

    /**
     * Tell what ArchiveTune is playing right now.
     *
     * @return The current track and whether it is playing.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getNowPlaying(): NowPlaying = withController { it.snapshot() }

    private suspend fun <T> withController(block: suspend (MediaController) -> T): T =
        withContext(Dispatchers.Main.immediate) {
            var controller: MediaController? = null
            try {
                val token = SessionToken(applicationContext, ComponentName(applicationContext, MusicService::class.java))
                controller =
                    MediaController
                        .Builder(applicationContext, token)
                        .setApplicationLooper(Looper.getMainLooper())
                        .buildAsync()
                        .await()
                block(controller)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (invalid: AppFunctionInvalidArgumentException) {
                throw invalid
            } catch (error: Exception) {
                Timber.tag(TAG).w(error, "AppFunction could not reach the playback service")
                throw AppFunctionAppUnknownException("ArchiveTune playback service is not available: ${error.message}")
            } finally {
                controller?.release()
            }
        }

    private fun MediaController.snapshot(): NowPlaying {
        val metadata = currentMediaItem?.mediaMetadata
        return NowPlaying(
            isPlaying = isPlaying,
            title = metadata?.title?.toString()?.takeIf { it.isNotBlank() },
            artist = (metadata?.artist ?: metadata?.subtitle)?.toString()?.takeIf { it.isNotBlank() },
            album = metadata?.albumTitle?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    private companion object {
        const val TAG = "ArchiveTuneAppFunctions"
        const val PLAY_WAIT_MS = 25_000L
        const val POLL_MS = 250L
        const val SETTLE_MS = 400L
    }
}
