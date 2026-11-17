package dev.lelonio.square.desktop.data

import dev.lelonio.square.desktop.model.MusicTrack
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Small local library store. It persists only recent playback metadata;
 * provider URLs and authentication data are deliberately not stored.
 */
class DesktopLibraryStore(
    private val file: File = defaultFile(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Synchronized
    fun loadRecentTracks(): List<MusicTrack> {
        if (!file.isFile) return emptyList()
        return runCatching {
            json.decodeFromString<RecentTrackSnapshot>(file.readText()).recentTracks
                .distinctBy { it.id }
                .take(MAX_RECENT_TRACKS)
        }.getOrElse { error ->
            System.err.println("[SquareLibrary] Could not read local history: ${error.message}")
            emptyList()
        }
    }

    @Synchronized
    fun saveRecentTracks(tracks: List<MusicTrack>) {
        val snapshot = RecentTrackSnapshot(
            version = FORMAT_VERSION,
            recentTracks = tracks.distinctBy { it.id }.take(MAX_RECENT_TRACKS),
        )
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("Could not create the Square library folder.")
        }

        val temporary = File(parent, "${file.name}.tmp")
        try {
            temporary.writeText(json.encodeToString(snapshot))
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    companion object {
        private const val FORMAT_VERSION = 1
        private const val MAX_RECENT_TRACKS = 100

        private fun defaultFile(): File {
            val home = System.getProperty("user.home")
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return File(home, ".square-windows/library/recent-tracks.json")
        }
    }
}

@Serializable
private data class RecentTrackSnapshot(
    val version: Int,
    val recentTracks: List<MusicTrack>,
)
