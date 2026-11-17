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
import java.util.concurrent.ConcurrentHashMap

data class ResolvedYouTubeAudio(
    val streamUrl: String,
    val format: MediaFormat,
    val contentType: String,
    val streamId: String,
    val streamBitrate: Int,
    val deliveryMethod: String,
    val expectedDurationMs: Long?,
)

/**
 * Resolves a playable public YouTube audio URL without downloading the song first.
 *
 * The Windows media player reads from a loopback proxy. This avoids waiting for
 * the entire track to be copied to disk before playback can begin.
 */
class YouTubeAudioStreamResolver {

    private val cache = ConcurrentHashMap<String, CachedStream>()
    private val locks = ConcurrentHashMap<String, Any>()

    suspend fun resolve(
        track: MusicTrack,
        forceRefresh: Boolean = false,
    ): ResolvedYouTubeAudio = withContext(Dispatchers.IO) {
        val videoId = track.id.trim()
        require(videoId.isNotEmpty() && videoId.all {
            it.isLetterOrDigit() || it == '-' || it == '_'
        }) {
            "The selected YouTube Music item has an invalid video ID."
        }

        val lock = locks.computeIfAbsent(videoId) { Any() }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (!forceRefresh) {
                cache[videoId]?.takeIf { now - it.createdAtMs < CACHE_TTL_MS }?.let {
                    return@withContext it.audio.copy(expectedDurationMs = track.durationMs)
                }
            }
            cache.remove(videoId)
            ensureExtractorInitialized()

        val encodedId = URLEncoder.encode(videoId, StandardCharsets.UTF_8.name())
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "$WATCH_URL$encodedId",
        )

        val stream = selectJavaFxCompatibleAudioStream(info.audioStreams)
            ?: throw IllegalStateException(
                "No progressive AAC/M4A or MP3 audio stream is available for this track.",
            )

        val format = stream.format
            ?: throw IllegalStateException("YouTube did not report an audio format.")

        val resolved = ResolvedYouTubeAudio(
            streamUrl = stream.content,
            format = format,
            contentType = audioContentType(format),
            streamId = stream.id,
            streamBitrate = stream.averageBitrate,
            deliveryMethod = stream.deliveryMethod.name,
            expectedDurationMs = track.durationMs,
        )

            cache[videoId] = CachedStream(now, resolved)
            if (cache.size > MAX_CACHE_ENTRIES) {
                cache.entries.minByOrNull { it.value.createdAtMs }?.let { cache.remove(it.key) }
            }
            System.err.println(
                "[SquarePlayback] Stream resolved: format=${resolved.format}, " +
                    "bitrate=${resolved.streamBitrate}, delivery=${resolved.deliveryMethod}, " +
                    "catalogueDurationMs=${resolved.expectedDurationMs}",
            )
            resolved
        }
    }

    private fun ensureExtractorInitialized() {
        synchronized(INIT_LOCK) {
            if (initialized) return
            NewPipe.init(DesktopNewPipeDownloader.create(), Localization.DEFAULT)
            initialized = true
        }
    }

    private data class CachedStream(
        val createdAtMs: Long,
        val audio: ResolvedYouTubeAudio,
    )

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v="
        private const val CACHE_TTL_MS = 45L * 60L * 1_000L
        private const val MAX_CACHE_ENTRIES = 64
        private val INIT_LOCK = Any()

        @Volatile
        private var initialized = false
    }
}

/** Prefer progressive HTTP audio formats supported by JavaFX on Windows. */
internal fun selectJavaFxCompatibleAudioStream(streams: List<AudioStream>): AudioStream? {
    val httpStreams = streams.filter { stream ->
        stream.isUrl &&
            stream.deliveryMethod.name == "PROGRESSIVE_HTTP" &&
            (
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

internal fun audioFileSuffix(format: MediaFormat): String = when (format) {
    MediaFormat.M4A -> ".m4a"
    MediaFormat.MP3 -> ".mp3"
    else -> throw IllegalArgumentException("Unsupported desktop audio format: $format")
}

internal fun audioContentType(format: MediaFormat): String = when (format) {
    MediaFormat.M4A -> "audio/x-m4a"
    MediaFormat.MP3 -> "audio/mpeg"
    else -> throw IllegalArgumentException("Unsupported desktop audio format: $format")
}
