package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.backend.youtube.YouTubeAudioStreamResolver
import dev.lelonio.square.desktop.model.MusicTrack
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Windows playback engine for public YouTube Music audio.
 *
 * Audio is resolved on IO, then fetched in bounded byte ranges by a local
 * loopback proxy. JavaFX handles decoding and audio output.
 */
class YouTubeMusicPlaybackEngine(
    private val resolver: YouTubeAudioStreamResolver = YouTubeAudioStreamResolver(),
) : PlaybackEngine {

    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    // Accessed only on the JavaFX Application Thread.
    private var player: MediaPlayer? = null
    private var activeTrack: MusicTrack? = null
    private var activeStreamProxy: LocalAudioStreamProxy? = null
    private var lastReportedSecond = -1L

    private val playRequestGeneration = AtomicLong(0)
    private val retryAttempts = ConcurrentHashMap<String, Int>()
    private val retryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var runtimeStarted = false

    private suspend fun <T> onFxThread(block: () -> T): T {
        runtimeStarted = true
        return JavaFxMediaRuntime.onFxThread(block)
    }

    override suspend fun play(track: MusicTrack) {
        val requestId = playRequestGeneration.incrementAndGet()
        retryAttempts.remove(track.id)
        mutableState.value = PlaybackState.Buffering(track)
        startPlayback(track, requestId, forceRefresh = false)
    }

    private suspend fun startPlayback(
        track: MusicTrack,
        requestId: Long,
        forceRefresh: Boolean,
    ) {
        try {
            val resolvedAudio = resolver.resolve(track, forceRefresh)
            if (requestId != playRequestGeneration.get()) return

            onFxThread {
                if (requestId != playRequestGeneration.get()) return@onFxThread
                createPlayer(track, requestId, resolvedAudio)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requestId == playRequestGeneration.get()) {
                val message = error.message ?: "Could not resolve the YouTube audio stream."
                runCatching {
                    onFxThread { scheduleRetryOrFail(track, requestId, message) }
                }.onFailure {
                    mutableState.value = PlaybackState.Failed(message, track)
                }
            }
        }
    }

    private fun createPlayer(
        track: MusicTrack,
        requestId: Long,
        resolvedAudio: dev.lelonio.square.desktop.backend.youtube.ResolvedYouTubeAudio,
    ) {
        disposeCurrentPlayer()
        activeTrack = track
        lastReportedSecond = -1L

        val proxy = LocalAudioStreamProxy(resolvedAudio)
        activeStreamProxy = proxy

        val media = Media(proxy.mediaUri)
        media.setOnError {
            if (player == null || activeTrack?.id == track.id) {
                scheduleRetryOrFail(
                    track,
                    requestId,
                    media.error?.message ?: "Windows Media could not open the audio stream.",
                )
            }
        }

        val nextPlayer = MediaPlayer(media)
        player = nextPlayer

        nextPlayer.setOnReady {
            if (player === nextPlayer && requestId == playRequestGeneration.get()) {
                System.err.println(
                    "[SquarePlayback] Media ready: durationMs=${nextPlayer.totalDuration.toMillis()}, " +
                        "stream=${resolvedAudio.streamId}",
                )
                nextPlayer.play()
            }
        }
        nextPlayer.setOnPlaying {
            if (player === nextPlayer && requestId == playRequestGeneration.get()) {
                mutableState.value = PlaybackState.Playing(track, currentPosition(nextPlayer))
            }
        }
        nextPlayer.setOnPaused {
            if (player === nextPlayer) {
                mutableState.value = PlaybackState.Paused(track, currentPosition(nextPlayer))
            }
        }
        nextPlayer.setOnStalled {
            if (player === nextPlayer) {
                mutableState.value = PlaybackState.Buffering(track)
            }
        }
        nextPlayer.setOnEndOfMedia {
            if (player === nextPlayer && requestId == playRequestGeneration.get()) {
                val actualDurationMs = nextPlayer.totalDuration.toMillis()
                    .takeIf { it.isFinite() && it > 0 }
                    ?.toLong()
                val expectedDurationMs = resolvedAudio.expectedDurationMs
                val endedEarly = actualDurationMs != null &&
                    expectedDurationMs != null &&
                    expectedDurationMs > 10_000L &&
                    actualDurationMs < expectedDurationMs * 0.75 &&
                    expectedDurationMs - actualDurationMs > 10_000L

                if (endedEarly) {
                    scheduleRetryOrFail(
                        track,
                        requestId,
                        "Audio ended after ${formatDuration(actualDurationMs!!)}; " +
                            "the catalogue lists ${formatDuration(expectedDurationMs)}.",
                    )
                } else {
                    disposeCurrentPlayer()
                    mutableState.value = PlaybackState.Completed(track)
                }
            }
        }
        nextPlayer.setOnError {
            if (player === nextPlayer && requestId == playRequestGeneration.get()) {
                scheduleRetryOrFail(
                    track,
                    requestId,
                    nextPlayer.error?.message ?: "Windows Media playback failed.",
                )
            }
        }
        nextPlayer.currentTimeProperty().addListener { _, _, time ->
            if (player === nextPlayer && nextPlayer.status == MediaPlayer.Status.PLAYING) {
                val positionMs = time.toMillis().toLong().coerceAtLeast(0)
                val second = positionMs / 1_000
                if (second != lastReportedSecond) {
                    lastReportedSecond = second
                    mutableState.value = PlaybackState.Playing(track, positionMs)
                }
            }
        }
    }

    private fun scheduleRetryOrFail(track: MusicTrack, requestId: Long, message: String) {
        if (requestId != playRequestGeneration.get()) return
        val attempt = retryAttempts.merge(track.id, 1) { previous, _ -> previous + 1 } ?: 1
        disposeCurrentPlayer()

        if (attempt <= MAX_RETRIES) {
            mutableState.value = PlaybackState.Buffering(track)
            System.err.println(
                "[SquarePlayback] Playback attempt $attempt failed: $message; refreshing stream URL.",
            )
            retryScope.launch {
                delay(RETRY_DELAY_MS * attempt)
                if (requestId == playRequestGeneration.get()) {
                    startPlayback(track, requestId, forceRefresh = true)
                }
            }
        } else {
            System.err.println("[SquarePlayback] Playback failed after retries: $message")
            mutableState.value = PlaybackState.Failed(
                message = "$message (after $MAX_RETRIES automatic retries)",
                track = track,
            )
        }
    }

    override suspend fun pause() {
        onFxThread { player?.pause() }
    }

    override suspend fun resume() {
        onFxThread { player?.play() }
    }

    override suspend fun seekTo(positionMs: Long) {
        onFxThread {
            val duration = player?.totalDuration?.toMillis()?.takeIf { it.isFinite() && it > 0 }
            val target = positionMs.coerceAtLeast(0).toDouble()
            player?.seek(Duration.millis(if (duration == null) target else target.coerceAtMost(duration)))
        }
    }

    override suspend fun stop() {
        playRequestGeneration.incrementAndGet()
        onFxThread {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Idle
        }
    }

    override suspend fun release() {
        playRequestGeneration.incrementAndGet()
        retryScope.cancel()
        onFxThread {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Idle
        }
        JavaFxMediaRuntime.shutdown {}
    }

    /** Called synchronously while the Compose window is closing. */
    fun shutdown() {
        playRequestGeneration.incrementAndGet()
        retryScope.cancel()
        if (!runtimeStarted) return
        JavaFxMediaRuntime.shutdown {
            disposeCurrentPlayer()
        }
    }

    private fun disposeCurrentPlayer() {
        player?.let { current ->
            runCatching { current.stop() }
            current.dispose()
        }
        player = null
        activeTrack = null
        activeStreamProxy?.close()
        activeStreamProxy = null
        lastReportedSecond = -1L
    }

    private fun currentPosition(mediaPlayer: MediaPlayer): Long =
        mediaPlayer.currentTime.toMillis().toLong().coerceAtLeast(0)

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1_000
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 800L
    }
}
