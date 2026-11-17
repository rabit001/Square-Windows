package dev.lelonio.square.desktop.model

/** The catalogue and playback source that owns a music item. */
enum class MusicProviderId(val displayName: String) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music"),
}

/**
 * A provider-neutral search result.
 *
 * [id] is stable within its provider. [providerUri] preserves the source's
 * native identifier so a provider adapter can resolve the item later.
 */
sealed interface MusicSearchItem {
    val id: String
    val title: String
    val artworkUrl: String?
    val providerUri: String
}

data class MusicTrack(
    override val id: String,
    override val title: String,
    val artists: List<String>,
    val albumTitle: String? = null,
    val durationMs: Long? = null,
    override val artworkUrl: String? = null,
    override val providerUri: String = id,
) : MusicSearchItem {
    init {
        require(id.isNotBlank()) { "Track id must not be blank" }
        require(title.isNotBlank()) { "Track title must not be blank" }
        require(durationMs == null || durationMs >= 0) { "Track duration cannot be negative" }
    }
}

data class MusicArtist(
    override val id: String,
    override val title: String,
    override val artworkUrl: String? = null,
    override val providerUri: String = id,
) : MusicSearchItem {
    init {
        require(id.isNotBlank()) { "Artist id must not be blank" }
        require(title.isNotBlank()) { "Artist name must not be blank" }
    }
}

data class MusicAlbum(
    override val id: String,
    override val title: String,
    val artists: List<String>,
    val trackCount: Int? = null,
    override val artworkUrl: String? = null,
    override val providerUri: String = id,
) : MusicSearchItem {
    init {
        require(id.isNotBlank()) { "Album id must not be blank" }
        require(title.isNotBlank()) { "Album title must not be blank" }
        require(trackCount == null || trackCount >= 0) { "Album track count cannot be negative" }
    }
}

data class MusicPlaylist(
    override val id: String,
    override val title: String,
    val ownerName: String? = null,
    val itemCount: Int? = null,
    override val artworkUrl: String? = null,
    override val providerUri: String = id,
) : MusicSearchItem {
    init {
        require(id.isNotBlank()) { "Playlist id must not be blank" }
        require(title.isNotBlank()) { "Playlist title must not be blank" }
        require(itemCount == null || itemCount >= 0) { "Playlist item count cannot be negative" }
    }
}

/** One page of provider search results. A null token means there is no next page. */
data class MusicSearchPage(
    val items: List<MusicSearchItem> = emptyList(),
    val nextPageToken: String? = null,
) {
    val hasMore: Boolean get() = !nextPageToken.isNullOrBlank()
}
