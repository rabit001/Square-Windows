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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

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
    var isPlaying by remember { mutableStateOf(false) }

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
                            .padding(32.dp),
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
                                    text = "This is the first desktop shell. Spotify, YouTube Music, accounts and playback are not connected yet.",
                                    color = Muted,
                                    fontSize = 14.sp,
                                )
                                Text(
                                    text = "The next milestone is the platform-independent music and playback layer.",
                                    color = Accent,
                                    fontSize = 13.sp,
                                )
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
                        Text("Choose a source to get started", color = Muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { isPlaying = !isPlaying }) {
                        Text(if (isPlaying) "Pause" else "Play", color = Color.White)
                    }
                }
            }
        }
    }
}
