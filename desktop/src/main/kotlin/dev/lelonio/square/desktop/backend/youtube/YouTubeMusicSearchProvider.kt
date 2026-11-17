package dev.lelonio.square.desktop.backend.youtube

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YouTubeLocale
import dev.lelonio.square.desktop.backend.MusicProvider
import dev.lelonio.square.desktop.backend.ProviderAuthState
import dev.lelonio.square.desktop.model.MusicProviderId
import dev.lelonio.square.desktop.model.MusicSearchPage
import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Anonymous YouTube Music song search backed by Square's vendored InnerTube client.
 *
 * This intentionally implements catalogue search only. Google account sign-in,
 * library access, media stream resolution and playback need separate desktop
 * adapters and are not implied by this class.
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

        return withContext(Dispatchers.IO) {
            val result = if (pageToken.isNullOrBlank()) {
                YouTube.search(normalizedQuery, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
            } else {
                YouTube.searchContinuation(pageToken).getOrThrow()
            }

            MusicSearchPage(
                items = result.items
                    .filterIsInstance<SongItem>()
                    .map { it.toDesktopMusicTrack() },
                nextPageToken = result.continuation,
            )
        }
    }

    companion object {
        private fun defaultCacheDirectory(): File {
            val userHome = System.getProperty("user.home")
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return File(userHome, ".square-windows/cache/innertube")
        }
    }
}

/** Convert the upstream InnerTube song model to the provider-neutral desktop model. */
internal fun SongItem.toDesktopMusicTrack(): MusicTrack = MusicTrack(
    id = id,
    title = title,
    artists = artists.map { it.name },
    albumTitle = album?.name,
    durationMs = duration?.toLong()?.times(1_000L),
    artworkUrl = thumbnail,
    providerUri = shareLink,
)
