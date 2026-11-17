package dev.lelonio.square.desktop.backend.youtube

import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Resolves a YouTube Music catalogue item into a playable audio URL.
 *
 * This uses NewPipeExtractor for public stream metadata and only selects
 * AAC/M4A or MP3, formats supported by the JavaFX Media runtime. No account
 * cookie is supplied; account-only or otherwise unavailable content is not
 * worked around here.
 */
class YouTubeAudioStreamResolver {

    suspend fun resolve(track: MusicTrack): String = withContext(Dispatchers.IO) {
        val videoId = track.id.trim()
        require(videoId.isNotEmpty() && videoId.all {
            it.isLetterOrDigit() || it == '-' || it == '_'
        }) {
            "The selected YouTube Music item has an invalid video ID."
        }

        ensureExtractorInitialized()

        val encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name())
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "$WATCH_URL$encodedId",
        )

        val stream = selectJavaFxCompatibleAudioStream(info.audioStreams)
            ?: throw IllegalStateException(
                "No AAC/M4A or MP3 audio stream is available for this track.",
            )

        stream.content
    }

    private fun ensureExtractorInitialized() {
        synchronized(INIT_LOCK) {
            if (initialized) return
            NewPipe.init(DesktopNewPipeDownloader.create(), Localization.DEFAULT)
            initialized = true
        }
    }

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v="
        private val INIT_LOCK = Any()

        @Volatile
        private var initialized = false
    }
}

/**
 * JavaFX Media supports AAC in MP4/M4A and MP3. Prefer AAC/M4A at the
 * highest available bitrate, then fall back to MP3.
 */
internal fun selectJavaFxCompatibleAudioStream(streams: List<AudioStream>): AudioStream? {
    val httpStreams = streams.filter { stream ->
        stream.isUrl && (
            stream.content.startsWith("https://", ignoreCase = true) ||
                stream.content.startsWith("http://", ignoreCase = true)
            )
    }

    return httpStreams
        .filter { it.format == MediaFormat.M4A }
        .maxByOrNull { it.averageBitrate }
        ?: httpStreams
            .filter { it.format == MediaFormat.MP3 }
            .maxByOrNull { it.averageBitrate }
}
