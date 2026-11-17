package dev.lelonio.square.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.lelonio.square.desktop.backend.youtube.YouTubeMusicSearchProvider
import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private val Background = Color(0xFF09090B)
private val Panel = Color(0xFF141418)
private val Muted = Color(0xFF9A9AA4)
private val Accent = Color(0xFFB9A7FF)

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Square Desktop",
    ) {
        SquareDesktopApp()
    }
}

@Composable
private fun SquareDesktopApp() {
    var selectedSection by remember { mutableStateOf("Home") }

    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .width(208.dp)
                            .fillMaxSize()
                            .background(Panel)
                            .padding(horizontal = 16.dp, vertical = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Square",
                            color = Color.White,
                            fontSize = 25.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "DESKTOP PREVIEW",
                            color = Muted,
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(modifier = Modifier.size(16.dp))

                        listOf("Home", "Search", "Your Library", "Downloads").forEach { item ->
                            TextButton(
                                onClick = { selectedSection = item },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedSection == item) Color.White else Muted,
                                    backgroundColor = if (selectedSection == item) Color(0xFF292532) else Color.Transparent,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(
                                    text = item,
                                    modifier = Modifier.fillMaxWidth(),
                                    fontWeight = if (selectedSection == item) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(32.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = selectedSection,
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Your music, brought to the desktop.",
                            color = Muted,
                            fontSize = 15.sp,
                        )
                        Spacer(modifier = Modifier.size(12.dp))

                        if (selectedSection == "Search") {
                            YouTubeMusicSearchPanel()
                        } else {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Panel,
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(
                                        text = "Windows foundation is ready",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = "The first catalogue integration is being added. Spotify, account sign-in and audio playback are not connected yet.",
                                        color = Muted,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        text = "Open Search to try the YouTube Music song search preview.",
                                        color = Accent,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Panel)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Nothing playing", color = Color.White, fontWeight = FontWeight.Medium)
                        Text("Playback engine is not connected yet", color = Muted, fontSize = 12.sp)
                    }
                    Text("YouTube Music search preview", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun YouTubeMusicSearchPanel() {
    val provider = remember { YouTubeMusicSearchProvider() }
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var nextPageToken by remember { mutableStateOf<String?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    fun loadPage(loadNextPage: Boolean) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() || isSearching) return
        if (loadNextPage && nextPageToken.isNullOrBlank()) return

        scope.launch {
            isSearching = true
            errorMessage = null
            hasSearched = true
            if (!loadNextPage) {
                results = emptyList()
                nextPageToken = null
            }

            try {
                val page = provider.search(
                    query = trimmedQuery,
                    pageToken = if (loadNextPage) nextPageToken else null,
                )
                val tracks = page.items.filterIsInstance<MusicTrack>()
                results = if (loadNextPage) results + tracks else tracks
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "YouTube Music",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Anonymous song catalogue search. Google sign-in and audio playback are not connected yet.",
                    color = Muted,
                    fontSize = 13.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search songs or artists") },
                    )
                    Button(
                        onClick = { loadPage(false) },
                        enabled = query.isNotBlank() && !isSearching,
                    ) {
                        Text("Search")
                    }
                }
                if (isSearching) {
                    Text("Searching YouTube Music…", color = Accent, fontSize = 13.sp)
                }
                errorMessage?.let {
                    Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp)
                }
                if (hasSearched && !isSearching && errorMessage == null && results.isEmpty()) {
                    Text("No songs found for this search.", color = Muted, fontSize = 13.sp)
                }
            }
        }

        results.forEachIndexed { index, track ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Panel,
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        color = Muted,
                        modifier = Modifier.width(28.dp),
                        fontSize = 12.sp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(
                            track.artists.joinToString(", ").ifBlank { "Unknown artist" },
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    track.durationMs?.let {
                        Text(formatDuration(it), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }

        if (!nextPageToken.isNullOrBlank()) {
            TextButton(
                onClick = { loadPage(true) },
                enabled = !isSearching,
            ) {
                Text("Load more results", color = Accent)
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1_000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
