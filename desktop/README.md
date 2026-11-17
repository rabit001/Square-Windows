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

## UI port status

The desktop UI follows Square's main navigation model: Home, New, Radio, Library and Search, with an integrated mini-player dock and an expanded player screen. It uses the original Square neutral palette, violet accent, Instrument Serif display face and Phosphor icon family.

The current Home, Library and Search pages use desktop state and the YouTube Music search provider. New and Radio provide entry points into catalogue searches. The full Android account state, Spotify integration, personalised catalogue shelves, playlists, lyrics, downloads, queue, EQ/effects and advanced player UI remain to be ported.

## Playback and provider status

- Anonymous YouTube Music song search uses Square's vendored InnerTube source.
- Windows playback resolves public AAC/M4A or MP3 stream URLs and relays them through a loopback HTTP proxy. JavaFX receives an explicit audio MIME type and its byte-range requests are forwarded upstream, so playback can begin before the whole track has downloaded.
- Google account sign-in and the YouTube Music library are not connected.
- Spotify desktop authentication and playback are not connected.

Some YouTube access uses undocumented interfaces and is not an official YouTube API integration. Provider policies and permissions are separate from the source-code licence.

## Licensing

The upstream Square project is licensed under GPL-3.0-or-later. Preserve upstream copyright notices and applicable third-party licence files. The Instrument Serif font bundled with this desktop UI is licensed under the SIL Open Font License; see `licenses/InstrumentSerif-OFL.txt`.
