## What's new in v1.0.4

**Android**
- Long song names in non-Latin scripts (e.g. Japanese) are saved properly instead of being reported "already downloaded" and skipped.
- One badly formatted Bandcamp or Apple Music page can no longer stop a whole run; importing a huge file shows a message instead of crashing.
- Updates are only ever downloaded from this project's GitHub releases.
- The update banner and **About & updates** now check GitHub again before downloading, so they always offer the newest version (before, they could offer an older one for up to a day and fail with "App not installed").
- The app now finds updates in these all-in-one releases too, not only in the old Android-only ones.

**Windows / Linux / macOS**
- Security and reliability fixes from a full review:
  - Your Spotify Client Secret is no longer written into the settings file when you queue a playlist (it stays in your system's password store).
  - Settings are saved safely: they can't be wiped or half-written if two things save at the same time.
  - Song names with unusual characters, very long non-Latin names or names like "CON" / "NUL" now save correctly on every system.
  - Apple Music links are checked properly, cover pictures are only downloaded over https with a size limit, and the temporary cover images are cleaned up after each song.
  - The update button only ever opens this project's GitHub page.
  - Running from source with only the yt-dlp program (not the Python module): downloads no longer report a false "FAIL".
  - Command-line version: odd characters in a Windows console or one broken song no longer stop the whole run.

---

## Install (Windows / Linux / macOS)

Everything is included (downloader engine, ffmpeg); nothing else to install.

1. Under **Assets** below, download **SEVBY-Windows.zip**, **SEVBY-Linux.zip**, or **SEVBY-macOS.zip**.
2. Extract it and run the **SEVBY** program inside.
   - Windows: if SmartScreen warns, **More info → Run anyway** (the app isn't code-signed yet).
   - macOS: right-click → **Open** the first time to get past Gatekeeper.
3. Your settings are kept in your home folder between versions.

## Install (Android)

Under **Assets** below, download the APK for your phone — **`SEVBY-…-arm64-v8a.apk`** for most phones
(or `…-universal.apk` if unsure). Open it, allow installing unknown apps if prompted, then **Install**.
Allow music/audio and notifications on first launch.
