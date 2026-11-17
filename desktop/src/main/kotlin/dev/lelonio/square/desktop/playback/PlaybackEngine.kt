package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.coroutines.flow.StateFlow

/** Playback status shared by the UI and any future Windows audio engine. */
sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Buffering(val track: MusicTrack) : PlaybackState
    data class Playing(
        val track: MusicTrack,
        val positionMs: Long,
    ) : PlaybackState
    data class Paused(
        val track: MusicTrack,
        val positionMs: Long,
    ) : PlaybackState
    data class Completed(val track: MusicTrack) : PlaybackState
    data class Failed(
        val message: String,
        val track: MusicTrack? = null,
    ) : PlaybackState
}

/** Platform-neutral playback controls. */
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>

    suspend fun play(track: MusicTrack)
    suspend fun pause()
    suspend fun resume()
    suspend fun seekTo(positionMs: Long)
    suspend fun stop()
    suspend fun release()
}
