package dev.lelonio.square.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.GearSix
import dev.lelonio.square.desktop.backend.youtube.YouTubeMusicSearchProvider
import dev.lelonio.square.desktop.model.MusicDetail
import dev.lelonio.square.desktop.model.MusicSearchItem
import dev.lelonio.square.desktop.model.MusicTrack
import dev.lelonio.square.desktop.playback.PlaybackState
import dev.lelonio.square.desktop.playback.YouTubeMusicPlaybackEngine
import dev.lelonio.square.desktop.ui.SquareBottomDock
import dev.lelonio.square.desktop.ui.SquareDesktopTheme
import dev.lelonio.square.desktop.ui.SquareDetailPage
import dev.lelonio.square.desktop.ui.SquareHomePage
import dev.lelonio.square.desktop.ui.SquareLibraryPage
import dev.lelonio.square.desktop.ui.SquareNewPage
import dev.lelonio.square.desktop.ui.SquarePlayerPage
import dev.lelonio.square.desktop.ui.SquareRadioPage
import dev.lelonio.square.desktop.ui.SquareSearchPage
import dev.lelonio.square.desktop.ui.SquareSettingsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun main() = application {
    val playbackEngine = remember { YouTubeMusicPlaybackEngine() }
    Window(
        onCloseRequest = {
            playbackEngine.shutdown()
            exitApplication()
        },
        title = "Square",
        state = rememberWindowState(size = DpSize(1280.dp, 820.dp)),
    ) {
        SquareDesktopApp(playbackEngine)
    }
}

enum class SquarePage {
    Home,
    New,
    Radio,
    Library,
    Search,
    Detail,
    Player,
    Settings,
}

@Composable
private fun SquareDesktopApp(playbackEngine: YouTubeMusicPlaybackEngine) {
    var selectedPage by remember { mutableStateOf(SquarePage.Home) }
    var darkTheme by remember { mutableStateOf(true) }

    val playbackState by playbackEngine.state.collectAsState()
    val provider = remember { YouTubeMusicSearchProvider() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicSearchItem>>(emptyList()) }
    var nextPageToken by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var recentTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var detail by remember { mutableStateOf<MusicDetail?>(null) }
    var isLoadingDetail by remember { mutableStateOf(false) }
    var isLoadingMoreDetail by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }

    fun searchMusic(searchQuery: String = query, append: Boolean = false) {
        val trimmed = searchQuery.trim()
        if (trimmed.isBlank() || isSearching) return
        scope.launch {
            isSearching = true
            errorMessage = null
            hasSearched = true
            if (!append) {
                query = trimmed
                results = emptyList()
                nextPageToken = null
            }
            try {
                val page = provider.search(
                    query = trimmed,
                    pageToken = if (append) nextPageToken else null,
                )
                if (append) {
                    val existing = results.mapTo(mutableSetOf()) { "${it::class.qualifiedName}:${it.id}" }
                    results = results + page.items.filterNot { "${it::class.qualifiedName}:${it.id}" in existing }
                } else {
                    results = page.items
                }
                nextPageToken = page.nextPageToken
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                errorMessage = error.message ?: "YouTube Music search failed."
            } finally {
                isSearching = false
            }
        }
    }

    fun playTrack(track: MusicTrack) {
        recentTracks = (listOf(track) + recentTracks.filterNot { it.id == track.id }).take(24)
        scope.launch { playbackEngine.play(track) }
    }

    fun openSearch(term: String? = null) {
        selectedPage = SquarePage.Search
        if (!term.isNullOrBlank()) {
            query = term
            searchMusic(term)
        }
    }

    fun openDetail(item: MusicSearchItem) {
        selectedPage = SquarePage.Detail
        detail = null
        detailError = null
        isLoadingDetail = true
        scope.launch {
            try {
                detail = provider.browse(item)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                detailError = error.message ?: "Could not open this catalogue page."
            } finally {
                isLoadingDetail = false
            }
        }
    }

    fun loadMoreDetail() {
        val current = detail ?: return
        if (current.nextPageToken.isNullOrBlank() || isLoadingMoreDetail) return
        scope.launch {
            isLoadingMoreDetail = true
            detailError = null
            try {
                val next = provider.browseMore(current)
                detail = current.copy(
                    tracks = current.tracks + next.tracks,
                    shelves = current.shelves + next.shelves,
                    nextPageToken = next.nextPageToken,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                detailError = error.message ?: "Could not load more tracks."
            } finally {
                isLoadingMoreDetail = false
            }
        }
    }

    val currentTrack = when (val state = playbackState) {
        is PlaybackState.Buffering -> state.track
        is PlaybackState.Playing -> state.track
        is PlaybackState.Paused -> state.track
        is PlaybackState.Failed -> state.track
        PlaybackState.Idle -> null
    }

    fun playPause() {
        scope.launch {
            when (playbackState) {
                is PlaybackState.Playing -> playbackEngine.pause()
                is PlaybackState.Paused -> playbackEngine.resume()
                is PlaybackState.Buffering -> Unit
                is PlaybackState.Failed -> currentTrack?.let { playbackEngine.play(it) }
                PlaybackState.Idle -> currentTrack?.let { playbackEngine.play(it) }
            }
        }
    }

    SquareDesktopTheme(darkTheme = darkTheme) {
        val backgroundBrush = if (darkTheme) {
            Brush.verticalGradient(
                0f to Color(0xFF101116),
                0.58f to Color(0xFF0C0D12),
                1f to Color(0xFF11111A),
            )
        } else {
            Brush.verticalGradient(
                0f to Color(0xFFF7F7FA),
                0.58f to Color(0xFFEFF0F5),
                1f to Color(0xFFF6F6F9),
            )
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 38.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "S Q U A R E",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 2.5.sp,
                            )
                            Text(
                                "Desktop edition",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.68f),
                            shape = CircleShape,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(Modifier.size(7.dp).background(Color(0xFF45C98A), CircleShape))
                                Text("YouTube Music", color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { selectedPage = SquarePage.Settings }) {
                            Icon(PhosphorIcons.Regular.GearSix, contentDescription = "Settings")
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedPage) {
                            SquarePage.Home -> SquareHomePage(
                                recentTracks = recentTracks,
                                darkTheme = darkTheme,
                                onPlay = ::playTrack,
                                onGoSearch = { openSearch() },
                                onOpenLibrary = { selectedPage = SquarePage.Library },
                            )
                            SquarePage.New -> SquareNewPage(
                                darkTheme = darkTheme,
                                onSearch = { openSearch(it) },
                            )
                            SquarePage.Radio -> SquareRadioPage(
                                darkTheme = darkTheme,
                                onSearch = { openSearch(it) },
                            )
                            SquarePage.Library -> SquareLibraryPage(
                                recentTracks = recentTracks,
                                darkTheme = darkTheme,
                                onSearch = { openSearch() },
                                onPlay = ::playTrack,
                            )
                            SquarePage.Search -> SquareSearchPage(
                                query = query,
                                onQueryChange = { query = it },
                                results = results,
                                isSearching = isSearching,
                                errorMessage = errorMessage,
                                hasSearched = hasSearched,
                                hasMore = !nextPageToken.isNullOrBlank(),
                                darkTheme = darkTheme,
                                onSearch = { searchMusic() },
                                onLoadMore = { searchMusic(append = true) },
                                onPlay = ::playTrack,
                                onOpen = ::openDetail,
                            )
                            SquarePage.Detail -> SquareDetailPage(
                                detail = detail,
                                isLoading = isLoadingDetail,
                                errorMessage = detailError,
                                isLoadingMore = isLoadingMoreDetail,
                                darkTheme = darkTheme,
                                onBack = { selectedPage = SquarePage.Search },
                                onPlay = ::playTrack,
                                onOpen = ::openDetail,
                                onLoadMore = ::loadMoreDetail,
                            )
                            SquarePage.Player -> SquarePlayerPage(
                                playbackState = playbackState,
                                darkTheme = darkTheme,
                                onPlayPause = ::playPause,
                                onStop = { scope.launch { playbackEngine.stop() } },
                                onBack = { selectedPage = SquarePage.Home },
                            )
                            SquarePage.Settings -> SquareSettingsPage(
                                darkTheme = darkTheme,
                                onDarkThemeChange = { darkTheme = it },
                            )
                        }
                    }

                    if (selectedPage != SquarePage.Player && selectedPage != SquarePage.Settings) {
                        SquareBottomDock(
                            selectedPage = selectedPage,
                            playbackState = playbackState,
                            darkTheme = darkTheme,
                            onNavigate = { selectedPage = it },
                            onOpenPlayer = { selectedPage = SquarePage.Player },
                            onPlayPause = ::playPause,
                        )
                    } else if (selectedPage == SquarePage.Settings) {
                        TextButton(
                            onClick = { selectedPage = SquarePage.Home },
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp),
                        ) {
                            Text("Back to Square")
                        }
                    }
                }
            }
        }
    }
}
