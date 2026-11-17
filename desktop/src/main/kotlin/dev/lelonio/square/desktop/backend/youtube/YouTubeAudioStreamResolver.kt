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
)

/**
 * Resolves and downloads one public YouTube audio stream to a temporary file.
 *
 * NewPipe's M4A stream metadata reports the MIME type as audio/mp4, while the
 * JavaFX 17 media table identifies M4A files by audio/x-m4a and the .m4a
 * extension. The local file gives JavaFX a conventional extension and avoids
 * relying on the Googlevideo response MIME type.
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
        val prefix = "square-$safeTrackId-"
        val tempFile = File.createTempFile(prefix, suffix).apply { deleteOnExit() }

        try {
            val request = Request.Builder()
                .url(stream.content)
                .header("User-Agent", STREAM_USER_AGENT)
                .header("Accept", "*/*")
                .build()

            return httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "YouTube audio request failed with HTTP ${response.code}.",
                    )
                }

                val body = response.body
                    ?: throw IOException("YouTube returned an empty audio response.")

                val responseType = body.contentType()?.toString()
                val normalizedType = responseType
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

                val declaredLength = body.contentLength()
                if (declaredLength == 0L) {
                    throw IOException("YouTube returned an empty audio file.")
                }
                if (declaredLength > MAX_AUDIO_BYTES) {
                    throw IOException("The selected audio stream exceeds the 100 MB safety limit.")
                }

                var totalBytes = 0L
                BufferedInputStream(body.byteStream()).use { input ->
                    BufferedOutputStream(FileOutputStream(tempFile)).use { output ->
                        val buffer = ByteArray(COPY_BUFFER_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            if (totalBytes > MAX_AUDIO_BYTES) {
                                throw IOException("The selected audio stream exceeds the 100 MB safety limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }

                if (totalBytes == 0L) {
                    throw IOException("YouTube returned an empty audio file.")
                }

                ResolvedYouTubeAudio(
                    file = tempFile,
                    format = format,
                    responseContentType = responseType,
                    byteCount = totalBytes,
                )
            }
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

    companion object {
        private const val WATCH_URL = "https://www.youtube.com/watch?v="
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_AUDIO_BYTES = 100L * 1024L * 1024L
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
    }
}

/** JavaFX 17 recognises MP4/AAC M4A files and MP3 files by their extensions. */
internal fun audioFileSuffix(format: MediaFormat): String = when (format) {
    MediaFormat.M4A -> ".m4a"
    MediaFormat.MP3 -> ".mp3"
    else -> throw IllegalArgumentException("Unsupported desktop audio format: $format")
}

/**
 * Prefer M4A AAC, then MP3. These are the audio formats used by the initial
 * JavaFX desktop playback spike.
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
