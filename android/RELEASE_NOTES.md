**First beta of SEVBY for Android** – a proper app (no Termux needed).

Paste or load a `.txt` list of `Artist - Title` lines, pick a folder, press Start. SEVBY looks for each song on
**Bandcamp** first and uses **YouTube** for the rest, then saves a 128 kbps MP3 with title, artist, album, year,
track number and square cover art (album details from Bandcamp or Apple's iTunes Search).

- Queue several lists; each goes into its own sub-folder
- Pause / Resume / Stop, also from the notification; keeps running in the background
- Skips songs already in the folder, never makes "(1)" copies, *Retry failed* for the misses
- Update the downloader (yt-dlp) from **About & updates** when YouTube changes – no reinstall needed
- Got a Spotify playlist? The **Open Chosic** button turns it into a song list to paste
- If Bandcamp blocks your connection (some mobile networks and hotspots do), it falls back to YouTube

**Which file?**

| File | For |
|---|---|
| `SEVBY-…-arm64-v8a.apk` | almost every phone from the last ~7 years |
| `SEVBY-…-armeabi-v7a.apk` | older 32-bit phones |
| `SEVBY-…-universal.apk` | if unsure (bigger) |

Open the APK on your phone and allow "install unknown apps" for your browser or Files app when asked.
Android 7.0 or newer.

This is a beta and has been tested mainly on an emulator and one phone. Please open an Issue with the
copied log (**Copy log** button) if something goes wrong.

The APK includes youtubedl-android, Python, yt-dlp and FFmpeg; as a whole it is distributed under the GNU GPL v3.
See THIRD_PARTY_NOTICES.md. Only download music you have the right to download.
