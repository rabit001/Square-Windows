# Square Windows Port Plan

## Project boundary

The upstream project is an Android app. This is an independent public repository containing a copy of the upstream source; it has no GitHub fork relationship with the upstream repository. The Windows port lives in a separate `desktop/` Gradle build. Keep the existing Android app as the reference implementation and avoid changing its build configuration for Windows-specific work.

## Confirmed source architecture

The Android app has a `MusicBackend` abstraction, separate Spotify and YouTube Music backends, a Rust `librespot` core, and Android Media3/ExoPlayer playback. The current Rust bridge is JNI-based and its audio sink writes to Android AudioTrack. These are useful seams, but they are not a ready-made Windows desktop application.

## Initial technology choice

Use Kotlin/JVM with Compose Multiplatform's desktop target for the first Windows shell. This preserves the existing Kotlin language and gives us a practical route to port visual components and selected JVM-compatible business logic without replacing the whole project with a web stack.

Do not assume that Android classes are portable. In particular:

- Android Media3 `Player`, `MediaSessionService`, `Context`, Android storage, notifications and Android AudioTrack need desktop equivalents.
- The current Rust JNI entry points need a Windows-facing interface. Prefer a small C ABI boundary and a Windows audio backend rather than trying to load Android JNI/AudioTrack code.
- Spotify catalogue/authentication logic should be separated from Android lifecycle and storage before reuse.
- YouTube stream extraction and signed-in library access must be tested on a plain desktop JVM. Playback must use a desktop media engine rather than ExoPlayer.
- Android Auto and other phone-only integrations are not Windows features; map relevant controls to Windows media keys/session APIs where possible.

## Milestones

1. **Desktop shell:** launch the independent Windows Compose application without changing Android build files.
2. **Domain extraction:** identify and isolate data models, networking and account logic that do not depend on Android.
3. **Spotify spike:** compile the patched Rust core for Windows x64, expose a stable desktop boundary, and prove login plus audio output.
4. **YouTube Music spike:** prove search, stream resolution and playback on Windows, including the signed-in library path.
5. **Desktop UI:** port Home, Search, Library, Album, Artist, Player, Lyrics and Settings to a resizable desktop layout.
6. **Feature parity:** implement downloads, offline playback, queue, effects, history, sleep timer, Connect and Windows media controls as applicable.
7. **Packaging and QA:** create a signed/unsigned test installer, test clean install/update/uninstall, and run 4 GB RAM performance checks.

## 4 GB RAM compatibility target

Treat 4 GB RAM as a real supported-device target, not just a label. Start with these design constraints:

- Load artwork only for visible and near-visible items; use a bounded LRU image cache.
- Avoid preloading full-resolution artwork, Canvas video or large playlist pages.
- Keep pagination bounded and release off-screen screen state.
- Use streaming audio buffers; do not hold whole tracks in memory.
- Make glass blur/refraction, background motion and visualisers adaptive. Low-memory mode starts with reduced blur and no decorative continuous animation.
- Keep downloads on disk and stream/decode them incrementally.
- Avoid background polling when the app is idle; use cancellation and bounded queues.
- Measure process working set and responsiveness during idle, search, scrolling and playback on a 4 GB Windows test machine. Do not claim support until this test passes.

## Build-machine constraints

The development machine has 8 GB RAM. The separate desktop Gradle build is configured with one worker and a capped Gradle/Kotlin daemon heap to avoid competing with the Android build. Avoid running Android Studio, emulators and desktop packaging tasks simultaneously on this machine.

## Licensing and attribution

Preserve the upstream GPL-3.0-or-later licence and notices. Audit every dependency before bundling it. In particular, the upstream repository identifies its lyrics view as AGPL-3.0 and documents additional third-party components and service terms. Keep required source notices and corresponding source available when distributing a covered binary. This project is an unofficial community port and is not affiliated with Spotify or Google. InnerTube search and extracted stream URLs use undocumented provider interfaces and may change or become unavailable. This is not an official YouTube API integration.

## Status

- [x] Imported upstream source into this independent repository
- [x] Created isolated desktop build and first UI shell
- [x] Launched the desktop shell on Windows
- [x] Generated the Windows installer package on Windows
- [x] Added an Android-independent desktop provider and playback contract
- [x] Added anonymous YouTube Music song-search adapter using the vendored InnerTube client
- [x] Added a Windows AAC/MP3 playback spike using NewPipeExtractor and JavaFX Media, with bounded temporary-file staging to handle the M4A MIME mismatch
- [ ] Run the corrected temporary-file playback path on Windows and verify real track playback
- [ ] Establish baseline memory measurements
- [ ] Implement reliable YouTube Music stream handling, queue and media controls
- [ ] Implement YouTube Music account/library support where permitted
- [ ] Implement Spotify Windows playback spike
