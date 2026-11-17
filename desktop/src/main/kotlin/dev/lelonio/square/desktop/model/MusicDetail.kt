package dev.lelonio.square.desktop.model

/** A browsable artist, album, playlist or podcast page from a music provider. */
data class MusicDetail(
    val item: MusicSearchItem,
    val subtitle: String? = null,
    val description: String? = null,
    val tracks: List<MusicTrack> = emptyList(),
    val shelves: List<MusicDetailShelf> = emptyList(),
    /** Opaque provider token for the next page of tracks, when available. */
    val nextPageToken: String? = null,
)

/** A named shelf on a detail page, such as albums, singles or related artists. */
data class MusicDetailShelf(
    val title: String,
    val items: List<MusicSearchItem>,
)
