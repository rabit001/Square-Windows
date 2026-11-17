# Square Desktop

A Windows desktop port of the original [Square](https://github.com/Lelonio/Square) music client.

This is an independent public repository with no GitHub fork relationship. The Android source remains intact under `app/`; the Windows application is built separately under `desktop/`.

## Windows preview

Requirements: Windows 10/11 x64 and JDK 17 or newer.

Run from the repository root:

```powershell
.\gradlew.bat -p desktop run
```

Build the Windows installer:

```powershell
.\gradlew.bat -p desktop packageExe
```

Run desktop tests:

```powershell
.\gradlew.bat -p desktop test
```

## Current Windows features

- Dark and light appearance.
- YouTube Music anonymous search across songs, videos, artists, albums, playlists, podcasts and episodes.
- Artist, album, playlist and podcast detail pages, including playlist continuation.
- YouTube Music audio resolution with a local HTTP media bridge. The bridge retrieves Googlevideo audio in bounded 512 KiB range requests instead of relying on one long request.
- Playback retry with a fresh stream URL, playback state reporting, seek, previous/next track controls and queue progression.
- Persistent recently played history in the user's `.square-windows/library/` folder.
- Windows installer packaging through Gradle.

## Not yet ported from Android Square

This is not yet full feature parity with the Android app. Spotify account authentication and playback, YouTube account sign-in and personal library, synced lyrics, full playlist editing, offline downloads, audio effects/equalizer, personalised Home/New/Radio feeds, crossfade and advanced queue management remain to be implemented. The New and Radio pages currently provide discovery searches rather than the original personalised service feeds.

Some YouTube access uses undocumented interfaces and is not an official YouTube API integration. Provider policies and permissions are separate from the source-code licence.

## Licensing

The upstream Square project is licensed under GPL-3.0-or-later. Preserve upstream copyright notices and applicable third-party licence files. The Instrument Serif font bundled with this desktop UI is licensed under the SIL Open Font License; see `licenses/InstrumentSerif-OFL.txt`.
