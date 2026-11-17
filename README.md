# Square-Windows

An independent Windows desktop port of [Square](https://github.com/Lelonio/Square), originally developed by Lelonio.

This repository is not a GitHub fork of the upstream project. It contains upstream source and a separate Windows desktop application under `desktop/`.

## Current status

The first Windows desktop UI shell has been launched, and a Windows `.exe` installer package has been generated successfully.

The current shell is a starting point, not a feature-complete music client. Anonymous YouTube Music song-catalogue search is now wired into the Search screen, but its live response still needs to be tested on Windows. Spotify authentication and playback, Google account sign-in, the YouTube Music library, stream resolution, and audio playback are **not implemented yet**.

## Run the desktop app

Requirements:
- Windows 10 or Windows 11 (64-bit)
- JDK 17 or newer
- Internet access for the first Gradle dependency download

From the repository root in PowerShell:

```powershell
.\gradlew.bat -p desktop run
```

## Build the Windows installer

```powershell
.\gradlew.bat -p desktop packageExe
```

The installer is written under:

```text
desktop/build/compose/binaries/main/exe/
```

## Project layout

- `desktop/` — isolated Kotlin/JVM desktop application and Windows packaging
- `app/`, `native/`, `innertube/` — upstream Android and supporting source retained for reference and future porting work
- `UPSTREAM_README.md` — original upstream README, including its feature description, credits, disclaimer, and licence notes
- `LICENSE` — GNU GPL version 3 licence text
- `docs/WINDOWS_PORT_PLAN.md` — porting milestones and technical constraints

## Licensing and attribution

The upstream Square project is licensed under **GPL-3.0-or-later**. The upstream README identifies the lyrics view as separately licensed under **AGPL-3.0** and lists additional third-party components and their licences. Preserve the applicable notices and review each dependency's licence before redistributing a build.

When distributing a covered modified binary, make the corresponding source available under the applicable licence terms. See [LICENSE](LICENSE) and [UPSTREAM_README.md](UPSTREAM_README.md).

## Disclaimer

This is an unofficial community project. It is not affiliated with, endorsed by, or connected to Spotify, Google, or the original Square developer. Provider access and service-policy requirements are separate from the source-code licence.
