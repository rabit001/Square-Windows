package dev.lelonio.square.desktop.backend.youtube

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.pages.SearchResult
import dev.lelonio.square.desktop.backend.MusicProvider
import dev.lelonio.square.desktop.backend.ProviderAuthState
import dev.lelonio.square.desktop.model.MusicAlbum
import dev.lelonio.square.desktop.model.MusicArtist
import dev.lelonio.square.desktop.model.MusicDetail
import dev.lelonio.square.desktop.model.MusicDetailShelf
import dev.lelonio.square.desktop.model.MusicPlaylist
import dev.lelonio.square.desktop.model.MusicProviderId
import dev.lelonio.square.desktop.model.MusicSearchItem
import dev.lelonio.square.desktop.model.MusicSearchPage
import dev.lelonio.square.desktop.model.MusicTrack
import dev.lelonio.square.desktop.model.MusicTrackKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale

/**
 * Anonymous YouTube Music catalogue backed by Square's vendored InnerTube client.
 *
 * Search follows the Android backend: each catalogue kind is requested with
 * its own filter and all first pages are fetched concurrently. Continuation
 * tokens remain separate for each kind inside one opaque desktop page token.
 *
 * Google account sign-in, saved library access and account-specific shelves
 * still require their own desktop adapters.
 */
class YouTubeMusicSearchProvider(
    private val cacheDirectory: File = defaultCacheDirectory(),
) : MusicProvider {
    private val mutableAuthState =
        MutableStateFlow<ProviderAuthState>(ProviderAuthState.SignedOut)

    override val id: MusicProviderId = MusicProviderId.YOUTUBE_MUSIC
    override val authState: StateFlow<ProviderAuthState> = mutableAuthState.asStateFlow()

    init {
        if (!cacheDirectory.exists() && !cacheDirectory.mkdirs()) {
            throw IllegalStateException("Could not create YouTube Music cache directory: $cacheDirectory")
        }
        require(cacheDirectory.isDirectory) {
            "YouTube Music cache path is not a directory: $cacheDirectory"
        }

        val locale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = locale.country.ifBlank { "US" },
            hl = locale.toLanguageTag().ifBlank { "en-US" },
        )
        // InnerTube creates its HTTP client when its singleton is initialized.
        // Set the directory, then recreate the client so the disk cache is applied.
        YouTube.cacheDir = cacheDirectory
        YouTube.proxy = null
        YouTube.cookie = null
        YouTube.dataSyncId = null
        YouTube.visitorData = null
        YouTube.useLoginForBrowse = false
    }

    override suspend fun signIn() {
        val message = "Google account sign-in is not implemented in this desktop build yet."
        mutableAuthState.value = ProviderAuthState.Failed(message)
        throw UnsupportedOperationException(message)
    }

    override suspend fun signOut() {
        YouTube.cookie = null
        YouTube.dataSyncId = null
        YouTube.visitorData = null
        YouTube.useLoginForBrowse = false
        mutableAuthState.value = ProviderAuthState.SignedOut
    }

    override suspend fun search(query: String, pageToken: String?): MusicSearchPage {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return MusicSearchPage()

        require(normalizedQuery.length <= 250) { "Search query is too long." }

        val continuation = pageToken?.takeIf { it.isNotBlank() }?.let(::decodeContinuation)
        require(continuation == null || continuation.query == normalizedQuery) {
            "Search continuation belongs to a different query. Start a new search."
        }

        return withContext(Dispatchers.IO) {
            val responses = coroutineScope {
                SEARCH_BUCKETS.map { bucket ->
                    async {
                        bucket.key to runCatching {
                            val token = continuation?.bucketTokens?.get(bucket.key)
                            if (token.isNullOrBlank()) {
                                YouTube.search(normalizedQuery, bucket.filter).getOrThrow()
                            } else {
                                YouTube.searchContinuation(token).getOrThrow()
                            }
                        }
                    }
                }.awaitAll()
            }

            val successful = responses.mapNotNull { (key, result) ->
                result.getOrNull()?.let { key to it }
            }
            if (successful.isEmpty()) {
                throw responses.firstNotNullOfOrNull { (_, result) -> result.exceptionOrNull() }
                    ?: IllegalStateException("YouTube Music search failed.")
            }

            val items = successful
                .flatMap { (bucket, result) ->
                    result.items.mapNotNull { item -> item.toDesktopSearchItem(bucket) }
                }
                .distinctBy { "${it::class.qualifiedName}:${it.id}" }

            val nextTokens = successful.mapNotNull { (key, result) ->
                result.continuation?.takeIf(String::isNotBlank)?.let { key to it }
            }.toMap()

            MusicSearchPage(
                items = items,
                nextPageToken = nextTokens.takeIf { it.isNotEmpty() }?.let {
                    encodeContinuation(SearchContinuation(normalizedQuery, it))
                },
            )
        }
    }


    override suspend fun browse(item: MusicSearchItem): MusicDetail =
        withContext(Dispatchers.IO) {
            when (item) {
                is MusicArtist -> {
                    // Library-followed artist IDs use an MPLA prefix; public
                    // artist pages are addressed by their channel/browse ID.
                    val artistId = item.id.removePrefix("MPLA")
                    val page = YouTube.artist(artistId).getOrThrow()
                    val topSongsSection = page.sections.firstOrNull { section ->
                        section.items.isNotEmpty() && section.items.all { it is SongItem }
                    }
                    val shelves = page.sections
                        .filterNot { it === topSongsSection }
                        .mapNotNull { section ->
                            val shelfItems = section.items.mapNotNull { source ->
                                if (source is SongItem) {
                                    source.toDesktopMusicTrack(MusicTrackKind.VIDEO)
                                } else {
                                    source.toDesktopSearchItem()
                                }
                            }
                            shelfItems.takeIf { it.isNotEmpty() }?.let { items ->
                                MusicDetailShelf(
                                    title = section.title.ifBlank { "More from ${page.artist.title}" },
                                    items = items,
                                )
                            }
                        }
                    val artist = item.copy(
                        title = page.artist.title,
                        artworkUrl = page.artist.thumbnail ?: item.artworkUrl,
                        providerUri = page.artist.shareLink,
                    )
                    val audience = listOfNotNull(
                        page.monthlyListenerCount?.takeIf { it.isNotBlank() },
                        page.subscriberCountText?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ").ifBlank { null }

                    MusicDetail(
                        item = artist,
                        subtitle = audience,
                        description = page.description?.takeIf { it.isNotBlank() },
                        tracks = topSongsSection?.items
                            .orEmpty()
                            .filterIsInstance<SongItem>()
                            .map { it.toDesktopMusicTrack() },
                        shelves = shelves,
                    )
                }

                is MusicAlbum -> {
                    val page = YouTube.album(item.id).getOrThrow()
                    val album = page.album.toDesktopSearchItem() as? MusicAlbum ?: item
                    val subtitle = listOfNotNull(
                        album.artists.joinToString(", ").takeIf { it.isNotBlank() },
                        album.year?.toString(),
                    ).joinToString(" · ").ifBlank { "Album" }
                    val otherVersions = page.otherVersions.mapNotNull { it.toDesktopSearchItem() }

                    MusicDetail(
                        item = album,
                        subtitle = subtitle,
                        tracks = page.songs.map { it.toDesktopMusicTrack() },
                        shelves = otherVersions.takeIf { it.isNotEmpty() }
                            ?.let { listOf(MusicDetailShelf("Other versions", it)) }
                            .orEmpty(),
                    )
                }

                is MusicPlaylist -> {
                    val page = YouTube.playlist(item.id).getOrThrow()
                    val playlist = item.copy(
                        title = page.playlist.title,
                        ownerName = page.playlist.author?.name ?: item.ownerName,
                        itemCount = page.playlist.songCountText.toItemCount() ?: item.itemCount,
                        artworkUrl = page.playlist.thumbnail ?: item.artworkUrl,
                        isPodcast = item.isPodcast || page.playlist.isPodcast,
                    )
                    val subtitle = listOfNotNull(
                        if (playlist.isPodcast) "Podcast" else "Playlist",
                        playlist.ownerName?.takeIf { it.isNotBlank() },
                        page.playlist.songCountText?.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")

                    MusicDetail(
                        item = playlist,
                        subtitle = subtitle,
                        description = page.playlist.description?.takeIf { it.isNotBlank() },
                        tracks = page.songs.map { it.toDesktopMusicTrack() },
                        nextPageToken = page.songsContinuation ?: page.continuation,
                    )
                }

                else -> throw IllegalArgumentException("This result does not have a browsable detail page.")
            }
        }

    override suspend fun browseMore(detail: MusicDetail): MusicDetail =
        withContext(Dispatchers.IO) {
            val continuation = detail.nextPageToken
                ?: return@withContext detail.copy(tracks = emptyList(), shelves = emptyList(), nextPageToken = null)
            require(detail.item is MusicPlaylist) {
                "Only playlist and podcast pages support track continuation."
            }

            val page = YouTube.playlistContinuation(continuation).getOrThrow()
            MusicDetail(
                item = detail.item,
                subtitle = detail.subtitle,
                description = detail.description,
                tracks = page.songs.map { it.toDesktopMusicTrack() },
                nextPageToken = page.continuation,
            )
        }

    private fun decodeContinuation(token: String): SearchContinuation {
        require(token.length <= MAX_CONTINUATION_LENGTH) { "Search continuation is too large." }
        return runCatching { Json.decodeFromString<SearchContinuation>(token) }
            .getOrElse { throw IllegalArgumentException("Search continuation is invalid.", it) }
    }

    private fun encodeContinuation(token: SearchContinuation): String =
        Json.encodeToString(token)

    companion object {
        private const val MAX_CONTINUATION_LENGTH = 64 * 1024

        private data class SearchBucket(
            val key: String,
            val filter: YouTube.SearchFilter,
        )

        private val SEARCH_BUCKETS = listOf(
            SearchBucket("songs", YouTube.SearchFilter.FILTER_SONG),
            SearchBucket("videos", YouTube.SearchFilter.FILTER_VIDEO),
            SearchBucket("albums", YouTube.SearchFilter.FILTER_ALBUM),
            SearchBucket("artists", YouTube.SearchFilter.FILTER_ARTIST),
            SearchBucket("playlists", YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST),
            SearchBucket("podcasts", YouTube.SearchFilter.FILTER_PODCAST),
            SearchBucket("episodes", YouTube.SearchFilter.FILTER_EPISODE),
        )

        private fun defaultCacheDirectory(): File {
            val userHome = System.getProperty("user.home")
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return File(userHome, ".square-windows/cache/innertube")
        }
    }
}

@Serializable
private data class SearchContinuation(
    val query: String,
    val bucketTokens: Map<String, String>,
)

/** Map every result type used by Square's Android search backend. */
internal fun YTItem.toDesktopSearchItem(bucket: String? = null): MusicSearchItem? = when (this) {
    is SongItem -> when (bucket) {
        "videos" -> toDesktopMusicTrack(MusicTrackKind.VIDEO)
        "episodes" -> toDesktopMusicTrack(MusicTrackKind.EPISODE)
        else -> toDesktopMusicTrack()
    }
    is AlbumItem -> MusicAlbum(
        id = browseId,
        title = title,
        artists = artists.orEmpty().map { it.name },
        year = year,
        artworkUrl = thumbnail,
        providerUri = shareLink,
    )
    is ArtistItem -> MusicArtist(
        id = id,
        title = title,
        artworkUrl = thumbnail,
        providerUri = shareLink,
        isProfile = isProfile,
    )
    is PlaylistItem -> MusicPlaylist(
        id = id,
        title = title,
        ownerName = author?.name,
        itemCount = songCountText.toItemCount(),
        artworkUrl = thumbnail,
        providerUri = shareLink,
    )
    is PodcastItem -> MusicPlaylist(
        id = id,
        title = title,
        ownerName = author?.name,
        itemCount = episodeCountText.toItemCount(),
        artworkUrl = thumbnail,
        providerUri = shareLink,
        isPodcast = true,
    )
    is EpisodeItem -> MusicTrack(
        id = id,
        title = title,
        artists = listOfNotNull(author?.name),
        albumTitle = podcast?.name,
        durationMs = duration?.toLong()?.times(1_000L),
        artworkUrl = thumbnail,
        providerUri = shareLink,
        kind = MusicTrackKind.EPISODE,
        explicit = explicit,
    )
}

/** Convert an InnerTube song/video to the provider-neutral playable model. */
internal fun SongItem.toDesktopMusicTrack(
    kind: MusicTrackKind = when {
        isEpisode -> MusicTrackKind.EPISODE
        isVideoSong -> MusicTrackKind.VIDEO
        else -> MusicTrackKind.SONG
    },
): MusicTrack = MusicTrack(
    id = id,
    title = title,
    artists = artists.map { it.name },
    albumTitle = album?.name,
    durationMs = duration?.toLong()?.times(1_000L),
    artworkUrl = thumbnail,
    providerUri = shareLink,
    kind = kind,
    explicit = explicit,
)

private fun String?.toItemCount(): Int? {
    if (isNullOrBlank()) return null
    val digits = Regex("""[0-9][0-9,]*""").find(this)?.value ?: return null
    return digits.replace(",", "").toIntOrNull()
}
