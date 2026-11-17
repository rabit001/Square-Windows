package dev.lelonio.square.desktop.ui

import dev.lelonio.square.desktop.SquarePage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import dev.lelonio.square.desktop.model.MusicTrack
import dev.lelonio.square.desktop.playback.PlaybackState
import java.util.Locale
import kotlin.math.roundToLong

@Composable
fun SquarePageHeader(
    eyebrow: String,
    title: String,
    subtitle: String? = null,
    darkTheme: Boolean,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 26.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                eyebrow.uppercase(Locale.ROOT),
                color = squareMuted(darkTheme),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                title,
                style = MaterialTheme.typography.displayMedium,
                color = squareInk(darkTheme),
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = squareMuted(darkTheme), fontSize = 13.sp)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SquareHomePage(
    recentTracks: List<MusicTrack>,
    darkTheme: Boolean,
    onPlay: (MusicTrack) -> Unit,
    onGoSearch: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        SquarePageHeader(
            eyebrow = "Square · YouTube Music",
            title = greeting(),
            subtitle = "Your music, your library, and the tracks you return to.",
            darkTheme = darkTheme,
            trailing = {
                ServiceMark(darkTheme = darkTheme)
            },
        )

        if (recentTracks.isEmpty()) {
            StartListeningCard(
                darkTheme = darkTheme,
                onSearch = onGoSearch,
                onLibrary = onOpenLibrary,
            )
        } else {
            SectionHeading(
                title = "Recently played",
                action = "Your library",
                onAction = onOpenLibrary,
                darkTheme = darkTheme,
            )
            RecentTrackGrid(
                tracks = recentTracks.take(6),
                darkTheme = darkTheme,
                onPlay = onPlay,
            )
        }

        SectionHeading(
            title = "Find your next favourite",
            action = "Search music",
            onAction = onGoSearch,
            darkTheme = darkTheme,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DiscoveryCard(
                modifier = Modifier.weight(1f),
                title = "Songs",
                subtitle = "Search the YouTube Music catalogue",
                icon = PhosphorIcons.Regular.MusicNotes,
                darkTheme = darkTheme,
                onClick = onGoSearch,
            )
            DiscoveryCard(
                modifier = Modifier.weight(1f),
                title = "Your library",
                subtitle = "Return to the tracks you played here",
                icon = PhosphorIcons.Regular.MusicNotes,
                darkTheme = darkTheme,
                onClick = onOpenLibrary,
            )
        }
    }
}

@Composable
fun SquareSearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MusicTrack>,
    isSearching: Boolean,
    errorMessage: String?,
    hasSearched: Boolean,
    hasMore: Boolean,
    darkTheme: Boolean,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        SquarePageHeader(
            eyebrow = "Explore the catalogue",
            title = "Search",
            subtitle = "Find songs and artists across YouTube Music.",
            darkTheme = darkTheme,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("What do you want to listen to?") },
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = squareInk(darkTheme),
                    unfocusedTextColor = squareInk(darkTheme),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = if (darkTheme) SquareColors.BorderDark else SquareColors.BorderLight,
                    focusedContainerColor = if (darkTheme) Color.White.copy(alpha = 0.045f) else Color.White.copy(alpha = 0.72f),
                    unfocusedContainerColor = if (darkTheme) Color.White.copy(alpha = 0.035f) else Color.White.copy(alpha = 0.65f),
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Button(
                onClick = onSearch,
                enabled = query.isNotBlank() && !isSearching,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 26.dp, vertical = 18.dp),
            ) {
                Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill(label = "All", selected = true, darkTheme = darkTheme)
            FilterPill(label = "Songs", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill(label = "Artists", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill(label = "Albums", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill(label = "Playlists", selected = false, darkTheme = darkTheme, enabled = false)
        }

        if (isSearching) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 35.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Searching YouTube Music…", color = squareMuted(darkTheme))
            }
        } else if (!errorMessage.isNullOrBlank()) {
            StatusCard(message = errorMessage, darkTheme = darkTheme)
        } else if (hasSearched && results.isEmpty()) {
            StatusCard(message = "No songs found for “$query”. Try another search.", darkTheme = darkTheme)
        } else if (!hasSearched) {
            SearchWelcome(darkTheme = darkTheme)
        } else {
            SectionHeading(
                title = "Songs",
                action = "${results.size} results",
                onAction = null,
                darkTheme = darkTheme,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                results.forEachIndexed { index, track ->
                    SquareTrackRow(
                        track = track,
                        index = index + 1,
                        darkTheme = darkTheme,
                        onPlay = { onPlay(track) },
                    )
                }
            }
            if (hasMore) {
                TextButton(onClick = onLoadMore, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Load more results")
                }
            }
        }
    }
}

@Composable
fun SquareLibraryPage(
    recentTracks: List<MusicTrack>,
    darkTheme: Boolean,
    onSearch: () -> Unit,
    onPlay: (MusicTrack) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SquarePageHeader(
            eyebrow = "Your collection",
            title = "Your Library",
            subtitle = "A place for the music you have played on this desktop.",
            darkTheme = darkTheme,
            trailing = {
                Button(onClick = onSearch, shape = RoundedCornerShape(15.dp)) {
                    Icon(PhosphorIcons.Regular.Plus, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Find music")
                }
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("All", selected = true, darkTheme = darkTheme)
            FilterPill("Playlists", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill("Albums", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill("Artists", selected = false, darkTheme = darkTheme, enabled = false)
            FilterPill("Downloads", selected = false, darkTheme = darkTheme, enabled = false)
        }

        if (recentTracks.isEmpty()) {
            EmptyLibraryCard(darkTheme = darkTheme, onSearch = onSearch)
        } else {
            SectionHeading(
                title = "Recently played",
                action = "${recentTracks.size} tracks",
                onAction = null,
                darkTheme = darkTheme,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recentTracks.forEachIndexed { index, track ->
                    SquareTrackRow(
                        track = track,
                        index = index + 1,
                        darkTheme = darkTheme,
                        onPlay = { onPlay(track) },
                    )
                }
            }
        }
    }
}

@Composable
fun SquareNewPage(
    darkTheme: Boolean,
    onSearch: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        SquarePageHeader(
            eyebrow = "The latest",
            title = "New",
            subtitle = "Fresh music and new discoveries.",
            darkTheme = darkTheme,
        )
        DiscoveryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "New music",
            subtitle = "Search for recently released tracks",
            icon = PhosphorIcons.Regular.Sparkle,
            darkTheme = darkTheme,
            onClick = { onSearch("new music 2026") },
        )
        DiscoveryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Trending now",
            subtitle = "Explore what listeners are searching for",
            icon = PhosphorIcons.Regular.Sparkle,
            darkTheme = darkTheme,
            onClick = { onSearch("trending songs 2026") },
        )
        Text(
            "These links use the live YouTube Music song search. A personalised New Releases feed will be ported with the catalogue and account layer.",
            color = squareMuted(darkTheme),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun SquareRadioPage(
    darkTheme: Boolean,
    onSearch: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(25.dp),
    ) {
        SquarePageHeader(
            eyebrow = "Keep the music going",
            title = "Radio",
            subtitle = "Start with an artist, song or mood.",
            darkTheme = darkTheme,
        )
        DiscoveryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Artist radio",
            subtitle = "Find an artist and explore related music",
            icon = PhosphorIcons.Regular.Broadcast,
            darkTheme = darkTheme,
            onClick = { onSearch("popular artists") },
        )
        DiscoveryCard(
            modifier = Modifier.fillMaxWidth(),
            title = "Your mood",
            subtitle = "Search for music to match the moment",
            icon = PhosphorIcons.Regular.Broadcast,
            darkTheme = darkTheme,
            onClick = { onSearch("chill music mix") },
        )
        Text(
            "Personalised stations and continuous radio queues are part of the full backend port; these starter actions use catalogue search for now.",
            color = squareMuted(darkTheme),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun SquarePlayerPage(
    playbackState: PlaybackState,
    darkTheme: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    val track = playbackState.trackOrNull()
    val position = when (playbackState) {
        is PlaybackState.Playing -> playbackState.positionMs
        is PlaybackState.Paused -> playbackState.positionMs
        else -> 0L
    }
    val duration = track?.durationMs ?: 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Icon(PhosphorIcons.Regular.ArrowLeft, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Back")
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = squareMuted(darkTheme), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onStop, enabled = track != null) {
                Text("Stop")
            }
        }

        SquareArtwork(
            url = track?.artworkUrl,
            modifier = Modifier
                .width(360.dp)
                .aspectRatio(1f),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                track?.title ?: "Nothing playing",
                color = squareInk(darkTheme),
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track?.artists?.joinToString(", ") ?: "Choose a song from Search",
                color = squareMuted(darkTheme),
                fontSize = 15.sp,
            )
        }

        Column(Modifier.width(560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Slider(
                value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                onValueChange = {},
                enabled = false,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(position), color = squareMuted(darkTheme), fontSize = 11.sp)
                Text(formatTime(duration), color = squareMuted(darkTheme), fontSize = 11.sp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            IconButton(onClick = {}, enabled = false, modifier = Modifier.size(46.dp)) {
                Icon(PhosphorIcons.Regular.CaretLeft, contentDescription = "Previous", tint = squareMuted(darkTheme))
            }
            IconButton(
                onClick = onPlayPause,
                enabled = track != null,
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            ) {
                val playing = playbackState is PlaybackState.Playing
                Icon(
                    if (playing) PhosphorIcons.Regular.Pause else PhosphorIcons.Regular.Play,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            IconButton(onClick = {}, enabled = false, modifier = Modifier.size(46.dp)) {
                Icon(PhosphorIcons.Regular.CaretRight, contentDescription = "Next", tint = squareMuted(darkTheme))
            }
        }
        if (playbackState is PlaybackState.Failed) {
            StatusCard(playbackState.message, darkTheme)
        }
    }
}

@Composable
fun SquareSettingsPage(
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 46.dp, vertical = 34.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SquarePageHeader(
            eyebrow = "Preferences",
            title = "Settings",
            subtitle = "Desktop appearance and connected music services.",
            darkTheme = darkTheme,
        )

        SettingRow(
            title = "Appearance",
            subtitle = if (darkTheme) "Dark theme" else "Light theme",
            trailing = {
                FilterPill(
                    label = if (darkTheme) "Dark" else "Light",
                    selected = true,
                    darkTheme = darkTheme,
                    onClick = { onDarkThemeChange(!darkTheme) },
                )
            },
            darkTheme = darkTheme,
        )
        SettingRow(
            title = "YouTube Music",
            subtitle = "Anonymous song search is connected. Account library and playback are still being ported.",
            trailing = { StatusPill("Connected", darkTheme) },
            darkTheme = darkTheme,
        )
        SettingRow(
            title = "Spotify",
            subtitle = "Desktop authentication and playback engine are not connected yet.",
            trailing = { StatusPill("In development", darkTheme) },
            darkTheme = darkTheme,
        )
        Text(
            "Square for Windows is an unofficial desktop port. It is separate from the Android app and keeps the original Android source unchanged.",
            color = squareMuted(darkTheme),
            fontSize = 12.sp,
        )
    }
}

@Composable
fun SquareBottomDock(
    selectedPage: SquarePage,
    playbackState: PlaybackState,
    darkTheme: Boolean,
    onNavigate: (SquarePage) -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
) {
    val track = playbackState.trackOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(
                    if (darkTheme) Color(0xCC1B1C22) else Color(0xDDFBFBFD),
                )
                .then(
                    Modifier.background(
                        Brush.verticalGradient(
                            if (darkTheme) {
                                listOf(Color.White.copy(alpha = 0.075f), Color.White.copy(alpha = 0.025f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.5f))
                            },
                        ),
                        RoundedCornerShape(30.dp),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            val tabs = listOf(
                SquarePage.Home to (PhosphorIcons.Regular.House to "Home"),
                SquarePage.New to (PhosphorIcons.Regular.SquaresFour to "New"),
                SquarePage.Radio to (PhosphorIcons.Regular.Broadcast to "Radio"),
                SquarePage.Library to (PhosphorIcons.Regular.MusicNotes to "Library"),
                SquarePage.Search to (PhosphorIcons.Regular.MagnifyingGlass to "Search"),
            )
            tabs.forEach { (page, data) ->
                DockTab(
                    icon = data.first,
                    label = data.second,
                    selected = selectedPage == page,
                    darkTheme = darkTheme,
                    onClick = { onNavigate(page) },
                )
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(1.dp)
                    .height(36.dp)
                    .background(if (darkTheme) Color.White.copy(alpha = 0.11f) else Color.Black.copy(alpha = 0.10f)),
            )

            Row(
                modifier = Modifier
                    .width(330.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(onClick = onOpenPlayer)
                    .padding(start = 7.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareArtwork(
                    url = track?.artworkUrl,
                    modifier = Modifier.size(42.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        track?.title ?: "Nothing playing",
                        color = squareInk(darkTheme),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artists?.joinToString(", ") ?: "Choose a song to start",
                        color = squareMuted(darkTheme),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onPlayPause, enabled = track != null, modifier = Modifier.size(34.dp)) {
                    Icon(
                        if (playbackState is PlaybackState.Playing) PhosphorIcons.Regular.Pause else PhosphorIcons.Regular.Play,
                        contentDescription = if (playbackState is PlaybackState.Playing) "Pause" else "Play",
                        tint = squareInk(darkTheme),
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DockTab(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    val tint = when {
        selected -> MaterialTheme.colorScheme.primary
        darkTheme -> SquareColors.MutedDark
        else -> SquareColors.MutedLight
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 19.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SquareTrackRow(
    track: MusicTrack,
    index: Int,
    darkTheme: Boolean,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onPlay)
            .background(Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            index.toString().padStart(2, '0'),
            color = squareMuted(darkTheme),
            fontSize = 11.sp,
            modifier = Modifier.width(32.dp),
        )
        SquareArtwork(track.artworkUrl, modifier = Modifier.size(48.dp))
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                track.title,
                color = squareInk(darkTheme),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artists.joinToString(", "),
                color = squareMuted(darkTheme),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            track.durationMs?.let(::formatTime) ?: "—",
            color = squareMuted(darkTheme),
            fontSize = 11.sp,
            modifier = Modifier.padding(end = 14.dp),
        )
        IconButton(onClick = onPlay, modifier = Modifier.size(34.dp)) {
            Icon(PhosphorIcons.Regular.Play, contentDescription = "Play ${track.title}", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RecentTrackGrid(
    tracks: List<MusicTrack>,
    darkTheme: Boolean,
    onPlay: (MusicTrack) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        tracks.take(4).forEach { track ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .clickable { onPlay(track) }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.66f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SquareArtwork(
                    url = track.artworkUrl,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    track.title,
                    color = squareInk(darkTheme),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artists.joinToString(", "),
                    color = squareMuted(darkTheme),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StartListeningCard(
    darkTheme: Boolean,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF272137),
                        Color(0xFF171821),
                        Color(0xFF12141A),
                    ),
                ),
            )
            .padding(30.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YOUR SOUNDTRACK STARTS HERE", color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text("Find something\nyou love.", color = Color.White, style = MaterialTheme.typography.displayLarge)
            Text("Search the catalogue and play a track to build your listening space.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onSearch, shape = RoundedCornerShape(15.dp)) {
                    Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Search music")
                }
                TextButton(onClick = onLibrary) { Text("Your Library", color = Color.White) }
            }
        }
        Box(Modifier.width(230.dp).padding(start = 28.dp), contentAlignment = Alignment.Center) {
            SquareArtwork(
                url = null,
                modifier = Modifier.size(205.dp),
            )
        }
    }
}

@Composable
private fun SearchWelcome(darkTheme: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 45.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(PhosphorIcons.Regular.MagnifyingGlass, contentDescription = null, tint = squareMuted(darkTheme), modifier = Modifier.size(38.dp))
        Text("Search for a song or artist", color = squareInk(darkTheme), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text("Results from YouTube Music appear here.", color = squareMuted(darkTheme), fontSize = 12.sp)
    }
}

@Composable
private fun EmptyLibraryCard(darkTheme: Boolean, onSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 50.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(PhosphorIcons.Regular.MusicNotes, contentDescription = null, tint = squareMuted(darkTheme), modifier = Modifier.size(42.dp))
        Text("Your library is waiting", color = squareInk(darkTheme), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text("Tracks you play on this desktop appear here for now.", color = squareMuted(darkTheme), fontSize = 12.sp)
        Button(onClick = onSearch) { Text("Find music") }
    }
}

@Composable
private fun StatusCard(message: String, darkTheme: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (darkTheme) SquareColors.BorderDark else SquareColors.BorderLight),
    ) {
        Text(message, color = squareMuted(darkTheme), fontSize = 13.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun StartListeningCardPlaceholder() {}

@Composable
private fun SectionHeading(
    title: String,
    action: String?,
    onAction: (() -> Unit)?,
    darkTheme: Boolean,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = squareInk(darkTheme), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        } else if (action != null) {
            Text(action, color = squareMuted(darkTheme), fontSize = 11.sp)
        }
    }
}

@Composable
private fun DiscoveryCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    darkTheme: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f).padding(horizontal = 13.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = squareInk(darkTheme), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = squareMuted(darkTheme), fontSize = 11.sp)
        }
        Icon(PhosphorIcons.Regular.CaretRight, contentDescription = null, tint = squareMuted(darkTheme))
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    darkTheme: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = squareInk(darkTheme), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(subtitle, color = squareMuted(darkTheme), fontSize = 11.sp)
        }
        trailing()
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    darkTheme: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        color = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (darkTheme) 0.22f else 0.12f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
        },
        shape = CircleShape,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else if (darkTheme) SquareColors.BorderDark else SquareColors.BorderLight,
        ),
    ) {
        Text(
            label,
            color = when {
                selected -> MaterialTheme.colorScheme.primary
                enabled -> squareMuted(darkTheme)
                else -> squareMuted(darkTheme).copy(alpha = 0.5f)
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun ServiceMark(darkTheme: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = CircleShape,
        border = BorderStroke(1.dp, if (darkTheme) SquareColors.BorderDark else SquareColors.BorderLight),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFF4CCB8A), CircleShape))
            Text("YouTube Music", color = squareInk(darkTheme), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatusPill(label: String, darkTheme: Boolean) {
    Text(
        label,
        color = if (label == "Connected") Color(0xFF4CCB8A) else squareMuted(darkTheme),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

private fun PlaybackState.trackOrNull(): MusicTrack? = when (this) {
    is PlaybackState.Buffering -> track
    is PlaybackState.Playing -> track
    is PlaybackState.Paused -> track
    is PlaybackState.Failed -> track
    PlaybackState.Idle -> null
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1_000L).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

private fun greeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Welcome back"
    }
}
