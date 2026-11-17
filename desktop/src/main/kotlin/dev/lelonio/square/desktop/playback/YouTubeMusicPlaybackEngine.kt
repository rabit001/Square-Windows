package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.backend.youtube.YouTubeAudioStreamResolver
import dev.lelonio.square.desktop.model.MusicTrack
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * First Windows playback spike for YouTube Music.
 *
 * Stream resolution is done on an IO dispatcher; JavaFX MediaPlayer creation
 * and control are confined to the JavaFX Application Thread.
 */
class YouTubeMusicPlaybackEngine(
    private val resolver: YouTubeAudioStreamResolver = YouTubeAudioStreamResolver(),
) : PlaybackEngine {

    private val mutableState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()

    // Accessed only on the JavaFX Application Thread.
    private var player: MediaPlayer? = null
    private var activeTrack: MusicTrack? = null
    private var lastReportedSecond = -1L

    override suspend fun play(track: MusicTrack) {
        mutableState.value = PlaybackState.Buffering(track)

        try {
            val streamUrl = resolver.resolve(track)
            JavaFxMediaRuntime.onFxThread {
                disposeCurrentPlayer()
                activeTrack = track
                lastReportedSecond = -1L

                val media = Media(streamUrl)
                media.setOnError {
                    failIfCurrent(
                        track,
                        media.error?.message ?: "Windows Media could not open the audio stream.",
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
                        disposeCurrentPlayer()
                        mutableState.value = PlaybackState.Idle
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
            mutableState.value = PlaybackState.Failed(
                error.message ?: "Could not start YouTube Music playback.",
                track,
            )
        }
    }

    override suspend fun pause() {
        JavaFxMediaRuntime.onFxThread { player?.pause() }
    }

    override suspend fun resume() {
        JavaFxMediaRuntime.onFxThread { player?.play() }
    }

    override suspend fun seekTo(positionMs: Long) {
        JavaFxMediaRuntime.onFxThread {
            player?.seek(Duration.millis(positionMs.coerceAtLeast(0).toDouble()))
        }
    }

    override suspend fun stop() {
        JavaFxMediaRuntime.onFxThread {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Idle
        }
    }

    override suspend fun release() {
        JavaFxMediaRuntime.onFxThread {
            disposeCurrentPlayer()
            mutableState.value = PlaybackState.Idle
        }
        JavaFxMediaRuntime.shutdown {}
    }

    /** Called synchronously while the Compose window is closing. */
    fun shutdown() {
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
        lastReportedSecond = -1L
    }

    private fun currentPosition(mediaPlayer: MediaPlayer): Long =
        mediaPlayer.currentTime.toMillis().toLong().coerceAtLeast(0)

    private fun failIfCurrent(track: MusicTrack, message: String) {
        if (activeTrack?.id == track.id) {
            mutableState.value = PlaybackState.Failed(message, track)
        }
    }
}
