package dev.lelonio.square.desktop.backend.youtube

import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YouTubeAudioStreamSelectorTest {

    @Test
    fun prefersHighestBitrateM4aOverMp3() {
        val selected = selectJavaFxCompatibleAudioStream(
            listOf(
                stream("mp3", MediaFormat.MP3, 320_000),
                stream("m4a-low", MediaFormat.M4A, 128_000),
                stream("m4a-high", MediaFormat.M4A, 256_000),
            ),
        )

        assertEquals("m4a-high", selected?.id)
    }

    @Test
    fun fallsBackToMp3WhenNoM4aIsAvailable() {
        val selected = selectJavaFxCompatibleAudioStream(
            listOf(
                stream("mp3-low", MediaFormat.MP3, 96_000),
                stream("mp3-high", MediaFormat.MP3, 192_000),
                stream("opus", MediaFormat.OPUS, 320_000),
            ),
        )

        assertEquals("mp3-high", selected?.id)
    }

    @Test
    fun ignoresNonUrlAndUnsupportedStreams() {
        val selected = selectJavaFxCompatibleAudioStream(
            listOf(
                stream("opus", MediaFormat.OPUS, 320_000),
                stream("inline-m4a", MediaFormat.M4A, 256_000, isUrl = false),
            ),
        )

        assertNull(selected)
    }

    @Test
    fun mapsMediaFormatsToJavaFxCompatibleMimeTypes() {
        assertEquals("audio/mp4", audioContentType(MediaFormat.M4A))
        assertEquals("audio/mpeg", audioContentType(MediaFormat.MP3))
    }

    @Test
    fun mapsMediaFormatsToMediaUrlExtensions() {
        assertEquals(".m4a", audioFileSuffix(MediaFormat.M4A))
        assertEquals(".mp3", audioFileSuffix(MediaFormat.MP3))
    }

    @Test
    fun rejectsUnsupportedDesktopAudioFileFormats() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            audioContentType(MediaFormat.OPUS)
        }
    }

    private fun stream(
        id: String,
        format: MediaFormat,
        bitrate: Int,
        isUrl: Boolean = true,
    ): AudioStream =
        AudioStream.Builder()
            .setId(id)
            .setContent("https://audio.example.test/$id", isUrl)
            .setMediaFormat(format)
            .setAverageBitrate(bitrate)
            .build()
}
