package dev.lelonio.square.desktop.backend.youtube

import com.metrolist.innertube.models.Album
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.Artist
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import dev.lelonio.square.desktop.model.MusicAlbum
import dev.lelonio.square.desktop.model.MusicArtist
import dev.lelonio.square.desktop.model.MusicPlaylist
import dev.lelonio.square.desktop.model.MusicTrackKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        assertEquals(MusicTrackKind.SONG, track.kind)
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

    @Test
    fun mapsArtistsAlbumsAndPlaylistsFromInnerTube() {
        val artist = ArtistItem(
            id = "UC123",
            title = "Artist Name",
            thumbnail = "https://example.test/artist.jpg",
            shuffleEndpoint = null,
            radioEndpoint = null,
        ).toDesktopSearchItem()
        val album = AlbumItem(
            browseId = "MPRE123",
            playlistId = "OLAK123",
            title = "Album Name",
            artists = listOf(Artist("Artist Name", "UC123")),
            year = 2026,
            thumbnail = "https://example.test/album.jpg",
        ).toDesktopSearchItem()
        val playlist = PlaylistItem(
            id = "PL123",
            title = "Playlist Name",
            author = Artist("Owner", "UC999"),
            songCountText = "18 songs",
            thumbnail = "https://example.test/playlist.jpg",
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        ).toDesktopSearchItem()

        assertIs<MusicArtist>(artist)
        assertEquals("Artist Name", artist.title)
        assertEquals("https://music.youtube.com/channel/UC123", artist.providerUri)

        assertIs<MusicAlbum>(album)
        assertEquals("Album Name", album.title)
        assertEquals(listOf("Artist Name"), album.artists)
        assertEquals(2026, album.year)

        assertIs<MusicPlaylist>(playlist)
        assertEquals("Owner", playlist.ownerName)
        assertEquals(18, playlist.itemCount)
    }

    @Test
    fun mapsMusicVideosAsVideoTracks() {
        val source = SongItem(
            id = "video789",
            title = "Official Video",
            artists = listOf(Artist(name = "Artist", id = "UC1")),
            duration = 200,
            musicVideoType = "MUSIC_VIDEO_TYPE_OMV",
            thumbnail = "https://example.test/video.jpg",
        )

        assertEquals(MusicTrackKind.VIDEO, source.toDesktopMusicTrack().kind)
    }
}
