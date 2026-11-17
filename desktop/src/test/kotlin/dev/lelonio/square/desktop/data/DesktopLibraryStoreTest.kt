package dev.lelonio.square.desktop.data

import dev.lelonio.square.desktop.model.MusicTrack
import dev.lelonio.square.desktop.model.MusicTrackKind
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLibraryStoreTest {

    @Test
    fun persistsRecentTracksAcrossStoreInstances() {
        val directory = Files.createTempDirectory("square-library-test").toFile()
        try {
            val file = directory.resolve("recent-tracks.json")
            val store = DesktopLibraryStore(file)
            val tracks = listOf(
                MusicTrack(
                    id = "video-123",
                    title = "Test Track",
                    artists = listOf("Test Artist"),
                    durationMs = 184_000,
                    artworkUrl = "https://example.test/cover.jpg",
                    kind = MusicTrackKind.SONG,
                ),
            )

            store.saveRecentTracks(tracks)

            assertEquals(tracks, DesktopLibraryStore(file).loadRecentTracks())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun deduplicatesAndCapsRecentHistory() {
        val directory = Files.createTempDirectory("square-library-cap-test").toFile()
        try {
            val file = directory.resolve("recent-tracks.json")
            val tracks = (0..120).map {
                MusicTrack(id = "id-$it", title = "Track $it", artists = emptyList())
            } + MusicTrack(id = "id-120", title = "Track 120", artists = emptyList())

            DesktopLibraryStore(file).saveRecentTracks(tracks)

            val loaded = DesktopLibraryStore(file).loadRecentTracks()
            assertEquals(100, loaded.size)
            assertEquals(loaded.map { it.id }.distinct().size, loaded.size)
        } finally {
            directory.deleteRecursively()
        }
    }
}
