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

The initial app is only a UI shell. It does not yet authenticate with Spotify or YouTube Music and does not play audio.

## Architecture rule

Desktop code stays under this directory until a piece is proven platform-independent. Do not add Windows-specific imports to the Android app module. Shared code should be extracted deliberately, with Android adapters left intact.

See [the Windows port plan](../docs/WINDOWS_PORT_PLAN.md) for the staged implementation and memory constraints.
