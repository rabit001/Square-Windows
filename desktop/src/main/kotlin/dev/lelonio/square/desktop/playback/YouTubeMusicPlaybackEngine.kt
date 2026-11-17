package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.backend.youtube.YouTubeAudioStreamResolver
import dev.lelonio.square.desktop.model.MusicTrack
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Windows playback engine for public YouTube Music audio.
 *
 * It resolves a stream URL on IO and gives JavaFX a loopback HTTP URL. The
 * proxy forwards byte ranges, allowing playback to start before the complete
 * song has downloaded.
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
    @Volatile private var runtimeStarted = false

    private suspend fun <T> onFxThread(block: () -> T): T {
        runtimeStarted = true
        return JavaFxMediaRuntime.onFxThread(block)
    }

    override suspend fun play(track: MusicTrack) {
        val requestId = playRequestGeneration.incrementAndGet()
        mutableState.value = PlaybackState.Buffering(track)

        try {
            val resolvedAudio = resolver.resolve(track)
            if (requestId != playRequestGeneration.get()) return

            onFxThread {
                if (requestId != playRequestGeneration.get()) return@onFxThread

                disposeCurrentPlayer()
                activeTrack = track
                lastReportedSecond = -1L

                val proxy = LocalAudioStreamProxy(resolvedAudio)
                activeStreamProxy = proxy

                val media = Media(proxy.mediaUri)
                media.setOnError {
                    failIfCurrent(
                        track,
                        media.error?.message ?: "Windows Media could not open the YouTube audio stream.",
                    )
                }

                val nextPlayer = MediaPlayer(media)
                player = nextPlayer

                nextPlayer.setOnReady {
                    if (player === nextPlayer) nextPlayer.play()
                }
                nextPlayer.setOnPlaying {
                    if (player === nextPlayer) {
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
                    if (player === nextPlayer) {
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
                            val message = "Audio ended after ${formatDuration(actualDurationMs!!)}, " +
                                "but the catalogue lists ${formatDuration(expectedDurationMs)}. " +
                                "The upstream stream ended early; try the track again."
                            System.err.println(
                                "[SquarePlayback] Early end: $message; stream=${resolvedAudio.streamId}; " +
                                    "bitrate=${resolvedAudio.streamBitrate}; format=${resolvedAudio.format}",
                            )
                            disposeCurrentPlayer()
                            mutableState.value = PlaybackState.Failed(message, track)
                        } else {
                            disposeCurrentPlayer()
                            mutableState.value = PlaybackState.Idle
                        }
                    }
                }
                nextPlayer.setOnError {
                    failIfCurrent(
                        track,
                        nextPlayer.error?.message ?: "Windows Media playback failed.",
                    )
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (requestId == playRequestGeneration.get()) {
                mutableState.value = PlaybackState.Failed(
                    error.message ?: "Could not start YouTube Music playback.",
                    track,
                )
            }
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
            player?.seek(Duration.millis(positionMs.coerceAtLeast(0).toDouble()))
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
        onFxThread {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Idle
        }
        JavaFxMediaRuntime.shutdown {}
    }

    /** Called synchronously while the Compose window is closing. */
    fun shutdown() {
        playRequestGeneration.incrementAndGet()
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

    private fun failIfCurrent(track: MusicTrack, message: String) {
        if (activeTrack?.id == track.id) {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Failed(message, track)
        }
    }
}
