package dev.lelonio.square.desktop.backend.youtube

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals

class YouTubeMusicSearchProviderTest {
    @Test
    fun mapsInnerTubeSongToDesktopTrack() {
        val source = SongItem(
            id = "video123",
            title = "Test Track",
            artists = listOf(Artist(name = "First Artist", id = "artist123")),
            album = Album(name = "Test Album", id = "album123"),
            duration = 187,
            thumbnail = "https://example.test/artwork.jpg",
        )

        val track = source.toDesktopMusicTrack()

        assertEquals("video123", track.id)
        assertEquals("Test Track", track.title)
        assertEquals(listOf("First Artist"), track.artists)
        assertEquals("Test Album", track.albumTitle)
        assertEquals(187_000L, track.durationMs)
        assertEquals("https://example.test/artwork.jpg", track.artworkUrl)
        assertEquals("https://music.youtube.com/watch?v=video123", track.providerUri)
    }

    @Test
    fun mapsMissingAlbumAndDuration() {
        val source = SongItem(
            id = "video456",
            title = "Another Track",
            artists = emptyList(),
            thumbnail = "https://example.test/cover.jpg",
        )

        val track = source.toDesktopMusicTrack()

        assertEquals(null, track.albumTitle)
        assertEquals(null, track.durationMs)
    }
}
