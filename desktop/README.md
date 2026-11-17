# Square Desktop

Windows desktop port workspace for the upstream [Square](https://github.com/Lelonio/Square) Android music client.

This is a separate Gradle build. It does not alter the Android app's Gradle modules or Android build configuration.

## Target

- Windows 11 and Windows 10, 64-bit
- 4 GB RAM compatibility target, with a low-memory operating mode
- Spotify and YouTube Music support
- Desktop installer output: Windows `.exe`
- Existing Square visual language and feature set, adapted for keyboard, mouse and desktop layouts

The 4 GB figure is a compatibility goal, not a performance guarantee. We will profile idle, browsing and playback memory on constrained hardware before calling the target supported.

## Build the desktop preview

Requirements:

- 64-bit Windows
- JDK 17 or newer
- Internet access for the first Gradle dependency download

From the repository root in PowerShell:

```powershell
.\gradlew.bat -p desktop run
```

Create the Windows installer:

```powershell
.\gradlew.bat -p desktop packageExe
```

Run desktop unit tests:

```powershell
.\gradlew.bat -p desktop test
```

## Current integrations

- Anonymous YouTube Music song-catalogue search using the vendored InnerTube client.
- A first audio playback spike that resolves an item through NewPipeExtractor and plays AAC/M4A or MP3 through JavaFX Media on Windows.

The playback spike downloads the selected public audio stream in bounded chunks to a temporary `.m4a` or `.mp3` file, then opens that local file with JavaFX Media. This avoids a mismatch between NewPipe's `audio/mp4` MIME label and JavaFX's documented M4A file recognition. The local file is deleted when playback is stopped, replaced or the player is closed. Real playback still needs Windows verification. This uses undocumented YouTube interfaces and may stop working when the provider changes them. It does not provide Google account sign-in, the YouTube Music library, paid/account-only content, or a guarantee of service availability. Spotify authentication and playback are not connected yet.

## Desktop backend boundary

- `model/` contains provider-neutral catalogue types.
- `backend/MusicProvider.kt` defines desktop account and search operations.
- `backend/MusicProviderRegistry.kt` owns registered providers and source selection.
- `backend/youtube/YouTubeMusicSearchProvider.kt` adapts anonymous song search to the desktop contract.
- `backend/youtube/YouTubeAudioStreamResolver.kt` resolves supported audio stream URLs.
- `playback/PlaybackEngine.kt` defines playback state and controls without Android Media3.
- `playback/YouTubeMusicPlaybackEngine.kt` uses JavaFX Media as the initial desktop output engine.

The desktop build compiles the upstream `innertube/src/main/kotlin` sources directly as JVM sources. The Android `innertube` module and its build configuration remain unchanged. A small JVM logging bridge substitutes for the Android Timber logger at desktop compile/runtime.

Provider adapters must use desktop-compatible networking, authentication and secure storage. Playback engines must use a Windows-compatible audio output. Do not import Android `Context`, Android Media3 or Android services into the desktop module.

See [the Windows port plan](../docs/WINDOWS_PORT_PLAN.md) for the staged implementation and memory constraints.
