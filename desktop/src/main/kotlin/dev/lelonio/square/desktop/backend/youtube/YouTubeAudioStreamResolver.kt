package dev.lelonio.square.desktop.backend.youtube

import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class ResolvedYouTubeAudio(
    val file: File,
    val format: MediaFormat,
    val responseContentType: String?,
    val byteCount: Long,
    val expectedByteCount: Long?,
    val httpStatusCode: Int,
    val contentRange: String?,
    val streamId: String,
    val streamBitrate: Int,
    val deliveryMethod: String,
)

/**
 * Resolves and downloads one public YouTube audio stream to a temporary file.
 *
 * Some Googlevideo responses are byte ranges (HTTP 206). Each returned range
 * is appended in order until Content-Range confirms that the full resource has
 * arrived. A partial range is never silently treated as a complete song.
 *
 * The response body is copied in bounded chunks to disk, never loaded wholly
 * into memory. No account cookie is supplied; account-only or otherwise
 * unavailable content is not worked around here.
 */
class YouTubeAudioStreamResolver(
    private val httpClient: OkHttpClient = createHttpClient(),
) {

    suspend fun resolve(track: MusicTrack): ResolvedYouTubeAudio = withContext(Dispatchers.IO) {
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

        downloadToTemporaryFile(track, stream)
    }

    private fun downloadToTemporaryFile(
        track: MusicTrack,
        stream: AudioStream,
    ): ResolvedYouTubeAudio {
        val format = stream.format
            ?: throw IllegalStateException("YouTube did not report an audio format.")

        val suffix = audioFileSuffix(format)
        val safeTrackId = track.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .take(16)
            .ifBlank { "track" }
        val tempFile = File.createTempFile("square-$safeTrackId-", suffix).apply { deleteOnExit() }

        try {
            val baseRequest = Request.Builder()
                .url(stream.content)
                .header("User-Agent", STREAM_USER_AGENT)
                .header("Accept", "*/*")
                .build()

            var request = baseRequest
            var totalBytes = 0L
            var expectedTotalBytes: Long? = null
            var lastStatusCode = 0
            var lastContentRange: String? = null
            var lastContentType: String? = null
            var lastDeclaredLength = -1L
            var rangeRequests = 0

            while (true) {
                rangeRequests += 1
                if (rangeRequests > MAX_RANGE_REQUESTS) {
                    throw IOException("YouTube audio required too many byte-range requests.")
                }

                val shouldAppend = totalBytes > 0
                val responseCompleted = httpClient.newCall(request).execute().use { response ->
                    lastStatusCode = response.code
                    lastContentRange = response.header("Content-Range")

                    if (!response.isSuccessful) {
                        throw IOException("YouTube audio request failed with HTTP ${response.code}.")
                    }

                    val body = response.body
                        ?: throw IOException("YouTube returned an empty audio response.")
                    lastContentType = body.contentType()?.toString()
                    lastDeclaredLength = body.contentLength()

                    val normalizedType = lastContentType
                        ?.substringBefore(';')
                        ?.trim()
                        ?.lowercase()

                    if (normalizedType == "text/html" ||
                        normalizedType == "application/json" ||
                        normalizedType == "text/plain"
                    ) {
                        throw IOException(
                            "YouTube returned $normalizedType instead of an audio stream.",
                        )
                    }

                    if (lastDeclaredLength == 0L) {
                        throw IOException("YouTube returned an empty audio response.")
                    }
                    if (lastDeclaredLength > MAX_AUDIO_BYTES) {
                        throw IOException("The selected audio stream exceeds the 100 MB safety limit.")
                    }

                    val range = if (response.code == HTTP_PARTIAL_CONTENT) {
                        parseContentRange(lastContentRange)
                            ?: throw IOException(
                                "YouTube returned HTTP 206 without a valid Content-Range header.",
                            )
                    } else {
                        if (shouldAppend) {
                            throw IOException(
                                "YouTube ignored a continuation range and restarted the audio response.",
                            )
                        }
                        null
                    }

                    if (range != null) {
                        if (range.start != totalBytes) {
                            throw IOException(
                                "YouTube returned a non-contiguous audio range: expected byte $totalBytes, got ${range.start}.",
                            )
                        }
                        val total = range.total
                            ?: throw IOException(
                                "YouTube returned a partial audio range without the total resource length.",
                            )
                        if (range.end >= total || total <= 0) {
                            throw IOException("YouTube returned an invalid audio Content-Range.")
                        }
                        expectedTotalBytes = total
                    }

                    var bytesThisResponse = 0L
                    BufferedInputStream(body.byteStream()).use { input ->
                        BufferedOutputStream(FileOutputStream(tempFile, shouldAppend)).use { output ->
                            val buffer = ByteArray(COPY_BUFFER_BYTES)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                bytesThisResponse += count
                                totalBytes += count
                                if (totalBytes > MAX_AUDIO_BYTES) {
                                    throw IOException("The selected audio stream exceeds the 100 MB safety limit.")
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }

                    if (bytesThisResponse == 0L) {
                        throw IOException("YouTube returned an empty audio range.")
                    }

                    if (range != null) {
                        val expectedRangeBytes = range.end - range.start + 1
                        if (bytesThisResponse != expectedRangeBytes) {
                            throw IOException(
                                "YouTube audio range was truncated: expected $expectedRangeBytes bytes, received $bytesThisResponse.",
                            )
                        }
                        val total = range.total!!
                        if (totalBytes < total) {
                            request = baseRequest.newBuilder()
                                .header("Range", "bytes=$totalBytes-")
                                .build()
                            false
                        } else {
                            true
                        }
                    } else {
                        if (lastDeclaredLength > 0 && bytesThisResponse != lastDeclaredLength) {
                            throw IOException(
                                "YouTube audio download was truncated: expected $lastDeclaredLength bytes, received $bytesThisResponse.",
                            )
                        }
                        true
                    }
                }

                if (responseCompleted) break
            }

            if (totalBytes == 0L) {
                throw IOException("YouTube returned an empty audio file.")
            }

            val expectedBytes = estimateExpectedBytes(track.durationMs, stream.averageBitrate)
            if (expectedBytes != null && totalBytes < expectedBytes * MIN_EXPECTED_SIZE_RATIO) {
                throw IOException(
                    "YouTube returned an incomplete audio file: received ${formatBytes(totalBytes)}, " +
                        "but this ${formatDuration(track.durationMs)} track at the selected bitrate " +
                        "should be roughly ${formatBytes(expectedBytes)}. HTTP $lastStatusCode; " +
                        "Content-Range: ${lastContentRange ?: "not provided"}.",
                )
            }

            val resolved = ResolvedYouTubeAudio(
                file = tempFile,
                format = format,
                responseContentType = lastContentType,
                byteCount = totalBytes,
                expectedByteCount = expectedBytes,
                httpStatusCode = lastStatusCode,
                contentRange = lastContentRange,
                streamId = stream.id,
                streamBitrate = stream.averageBitrate,
                deliveryMethod = stream.deliveryMethod.name,
            )
            System.err.println(
                "[SquarePlayback] Download complete: format=${resolved.format}, " +
                    "bytes=${resolved.byteCount}, expectedBytes=${resolved.expectedByteCount}, " +
                    "http=${resolved.httpStatusCode}, contentRange=${resolved.contentRange}, " +
                    "contentType=${resolved.responseContentType}, stream=${resolved.streamId}, " +
                    "bitrate=${resolved.streamBitrate}, delivery=${resolved.deliveryMethod}",
            )
            resolved
        } catch (error: Exception) {
            tempFile.delete()
            throw error
        }
    }

    private fun ensureExtractorInitialized() {
        synchronized(INIT_LOCK) {
            if (initialized) return
            NewPipe.init(DesktopNewPipeDownloader.create(), Localization.DEFAULT)
            initialized = true
        }
    }

    private data class ByteRange(
        val start: Long,
        val end: Long,
        val total: Long?,
    )

    private fun parseContentRange(value: String?): ByteRange? {
        if (value == null) return null
        val match = CONTENT_RANGE_REGEX.matchEntire(value.trim()) ?: return null
        return ByteRange(
            start = match.groupValues[1].toLongOrNull() ?: return null,
            end = match.groupValues[2].toLongOrNull() ?: return null,
            total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull(),
        )
    }

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v="
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_AUDIO_BYTES = 100L * 1024L * 1024L
        private const val MAX_RANGE_REQUESTS = 128
        private const val HTTP_PARTIAL_CONTENT = 206
        private const val MIN_EXPECTED_SIZE_RATIO = 0.25
        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
        private val INIT_LOCK = Any()

        @Volatile
        private var initialized = false

        private fun createHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(3, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .build()

        private fun estimateExpectedBytes(durationMs: Long?, bitrate: Int): Long? {
            if (durationMs == null || durationMs <= 0 || bitrate <= 0) return null
            return (durationMs.toDouble() * bitrate.toDouble() / 8_000.0).toLong()
        }

        private fun formatBytes(bytes: Long): String =
            String.format(java.util.Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0))

        private fun formatDuration(durationMs: Long?): String {
            if (durationMs == null || durationMs <= 0) return "unknown-length"
            val seconds = durationMs / 1_000
            return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
        }
    }
}

/** JavaFX 17 recognises MP4/AAC M4A files and MP3 files by their extensions. */
internal fun audioFileSuffix(format: MediaFormat): String = when (format) {
    MediaFormat.M4A -> ".m4a"
    MediaFormat.MP3 -> ".mp3"
    else -> throw IllegalArgumentException("Unsupported desktop audio format: $format")
}

/**
 * Prefer ordinary URL-backed audio streams; skip adaptive/OTF delivery modes.
 * JavaFX's first playback backend consumes one complete progressive audio file.
 */
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
