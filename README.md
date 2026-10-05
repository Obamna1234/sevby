***Beta: Linux/Mac builds untested. The Android app is new (beta).***

# SEVBY: Playlist to MP3

<img width="677" height="827" alt="image" src="https://github.com/user-attachments/assets/67837605-dd38-4f90-9a44-0b76e37801aa" />

*SEVBY running a .txt file (screenshot of the previous window; the new layout is described below)*

<img width="677" height="828" alt="image" src="https://github.com/user-attachments/assets/88569e0c-0281-4825-beb7-12afde1dda84" />

*SEVBY running a Spotify playlist*


Turn a Spotify playlist (or a plain text list of songs) into tagged MP3s on your own computer.
SEVBY looks for each song on **Bandcamp** first, and falls back to **YouTube** for anything it can't find.

> **Looking for better quality?** There is also **SEVBY HQ**, an audiophile edition that keeps YouTube's original audio (M4A), checks Jamendo and the Internet Archive for free lossless files first, and labels the quality of every song: <https://github.com/Obamna1234/sevby-hq>. SEVBY (this one) is the simple, everything-as-MP3 version.

## Ways to run SEVBY

| Where | How |
|---|---|
| **Windows** | Download the `.exe` from the **Releases** page and double-click it |
| **Android** | Download the APK from the **SEVBY for Android** release on the Releases page (marked *Pre-release*; see [Android](#android) below) |
| **Linux / macOS** | Download the build from the Releases page (beta, untested), or run from source |
| **Any computer** | Run from source with Python (see [Quick start](#quick-start)) |

## What it does

- Paste a Spotify playlist link (public or private; needs a Spotify Client ID, see below), **or** a list / `.txt` file of `Artist - Title` lines (CSV and M3U files work too), **or** a shared **Apple Music playlist link** with the *Apple Music link* button (no sign-in; public playlists only, and it reads Apple's public web page, so it may stop working if Apple changes it)
- Bandcamp first, YouTube for the rest (or choose *Bandcamp only* / *YouTube only*)
- MP3 files with title, artist, album, year, track number and square cover art
- YouTube matching picks the version with the right length and avoids live / remix / sped-up uploads
- Smarter matching: prefers the artist's own release over compilations, live albums and "Expanded Edition" reissues, and fills in album info from iTunes when Bandcamp has none
- Pause, Stop and **Resume** (a stopped run carries on where it left off, finished songs show as done), *Retry failed (N)* (re-runs only the failed songs, into the right folders), *Open folder*, and a sound when a run finishes
- **Queue**: add several playlists / song lists, press *Start Download*, and walk away. They run strictly one after another, each in its own folder named after the playlist. The queue is remembered if you close the app
- A tidy, collapsible **song log**: one entry per song with where it came from, plus a *Details* view and *Copy log* for bug reports
- Songs already in the folder are skipped (even after an interrupted run); a song listed twice is only downloaded once
- Retries automatically when the internet drops for a moment
- **About & updates**: shows the yt-dlp version, checks once a day and updates it with one click (restart afterwards), plus diagnostics and a network test
- Remembers your folder, mode and source
- Runs locally. No account with SEVBY, nothing is uploaded anywhere

## Quick start

Download the build for your system from the **Releases** page (Windows `.exe`, Linux and macOS builds from the GitHub Actions workflow), or run from source:

```bash
pip install -r requirements.txt
python sevby_app.py
```

From source you also need **ffmpeg** (put `ffmpeg.exe` / `ffmpeg` next to `sevby_app.py`, or install it so it's on your PATH).

**Android:** there is a proper app now, see [Android](#android) below.

## Android

**SEVBY for Android** is a proper app (no Termux needed). Get the APK from the **Releases** page: look for the newest
release named *SEVBY for Android* (tag `android-v…`) and download `SEVBY-…-arm64-v8a.apk` (or `…-universal.apk` if
unsure). Open it on your phone and allow installing apps from your browser or Files app when asked. Android 7.0+.

- Paste or load a `.txt` song list (`Artist - Title` lines), pick a folder, press **Start Download**
- Bandcamp first, YouTube for the rest, with album details from Bandcamp or Apple's iTunes Search
- Queue, Pause / Resume / Stop (also from the notification), *Retry failed*, skips songs already in the folder
- Keeps running in the background; update the downloader from **About & updates** when YouTube changes
- **Apple Music:** tap *Apple Music* and paste a public playlist or album link; SEVBY reads the song list itself (no sign-in)
- Spotify playlists: no Spotify login on Android yet. Tap **Open Chosic** under the song box, paste the playlist link into [Chosic](https://www.chosic.com/spotify-playlist-exporter/), then paste or load the song list in SEVBY

The APK bundles youtubedl-android (GPL v3), so the APK as a whole is distributed under the GPL v3; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Developer notes: [android/README.md](android/README.md).
There is also the older command-line version for Termux: [TERMUX.md](TERMUX.md).

## Using a Spotify playlist (one-time setup)

Spotify requires every app to have its own Client ID. You only do this once. **Spotify currently requires a Premium account to use its Web API, so a free Spotify account may not be able to create one.** If you don't have Premium, use the text-list method below instead.

1. Go to <https://developer.spotify.com/dashboard> and log in.
2. Click **Create app**. Use any name and description.
3. Under **Redirect URIs** add exactly `http://127.0.0.1:8888` and click **Add**.
4. Tick **Web API**, agree to the terms, and save.
5. Open the app's **Settings** and copy the **Client ID**.
6. In SEVBY choose *Spotify playlist link*, paste the playlist link and the Client ID, pick a save folder and press **Start**.

The first time, your browser opens so you can log in to Spotify and approve access (this is what allows private playlists). It's remembered afterwards. No Client Secret is needed. You can use the same Client ID on other devices, such as your phone.

**No Premium, or don't want to set this up?** Export the playlist to text with [Chosic](https://www.chosic.com/spotify-playlist-exporter/) and use *Song list / .txt file*. That works with any Spotify account.

## Queue (many playlists, hands-off)

1. Choose your save folder and source.
2. Paste a Spotify link (or a song list / load a `.txt`) and press **Add to queue**. The inputs clear so you can add the next one.
3. Repeat, then press **Start Download (N lists)**.

Items run one at a time, never in parallel. Each is saved in its own sub-folder (the Spotify playlist's name, or the `.txt` file's name). Items that finish leave the queue; if you press Stop, the rest stay queued. Songs already downloaded are skipped, so running again is always safe.

## Song list format

One song per line:

```
Perturbator - Future Club
Carpenter Brut - Turbo Killer
```

## Building it yourself

```bash
pip install -r requirements.txt pyinstaller
python build.py
```

The `dist/` folder then contains `SEVBY` (or `SEVBY.exe`). The GitHub Actions workflow in `.github/workflows/build.yml` builds Windows, Linux and macOS versions with the latest yt-dlp.

> **Keep yt-dlp fresh.** YouTube changes often. The packaged app contains the yt-dlp version from build time. Open **About & updates** to update it with one click, or rebuild with `pip install -U yt-dlp` if you build it yourself.

## Troubleshooting

- **Downloads suddenly stop working (especially from YouTube)** - the sites change often, and the downloader inside SEVBY (yt-dlp) needs updating. Open **About & updates** and press the update button, then restart. The files on the **Releases** page are also rebuilt automatically every month with the newest yt-dlp. If it still doesn't work, please open an Issue. This is a hobby project maintained when time allows, so fixes are not guaranteed. Termux and source users can instead run `pip install -U yt-dlp`.
- **"Bandcamp blocked / Client Challenge"** - Bandcamp sometimes shows a bot check. SEVBY then tries each artist's own Bandcamp page and uses YouTube for the rest.
- **YouTube 403 errors** - update yt-dlp (see above).
- **A copy of each run's log** is saved to `~/.sevby_log.txt` - attach it to bug reports.

## Credits - what SEVBY is built on

SEVBY is a small interface that glues together some excellent open-source projects. The heavy lifting is theirs:

- **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** - finds and downloads the audio from Bandcamp and YouTube (Unlicense)
- **[FFmpeg](https://ffmpeg.org)** - converts audio to MP3 and writes tags and cover art (GPL v3 in the Windows build, bundled as a separate program)
- **[customtkinter](https://github.com/TomSchimansky/CustomTkinter)** by Tom Schimansky - the user interface (MIT)
- **[tkinterdnd2](https://github.com/Eliav2/tkinterdnd2)** - drag and drop (MIT)
- **[PyInstaller](https://pyinstaller.org)** - packages the app (GPL-2.0 with bootloader exception)
- **[youtubedl-android](https://github.com/JunkFood02/youtubedl-android)** - runs yt-dlp, Python and FFmpeg inside the Android app (GPL v3)
- **[Spotify Web API](https://developer.spotify.com/documentation/web-api)** - only used to read playlist track names and details (no audio comes from Spotify)
- **[iTunes Search API](https://performance-partners.apple.com/search-api)** - only used to look up album name, year, track number and cover (no audio comes from Apple)
- **[Bandcamp](https://bandcamp.com)** - please support artists by buying their music there when you can
- The "Artist - Title" list idea owes a nod to **[Chosic's playlist exporter](https://www.chosic.com/spotify-playlist-exporter/)**, which SEVBY links to as an optional alternative

Full licence details: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Antivirus note
Antivirus note: SEVBY is unsigned and built with PyInstaller, so some antivirus programs (e.g. AVG/Avast) may flag or delay it as an unknown file. The full source code is in this repo and the release files are built automatically by the GitHub Actions workflow. You can also run it from source (see Quick start).

<img width="2475" height="1223" alt="image" src="https://github.com/user-attachments/assets/7602f689-6537-4b33-a1dc-c0a382ddda59" />
https://www.virustotal.com/gui/file/348bacf093abe15f77455337616c0d77955364851724d54a4b4426fb94414a2c/detection

## Disclaimer

SEVBY is a tool for personal use. Only download music you have the right to download, such as music you own, that is free to download, or that the artist allows. Downloading copyrighted material without permission may be illegal where you live, and may break the terms of service of YouTube, Bandcamp or Spotify. You are responsible for how you use it. SEVBY is not affiliated with or endorsed by Spotify, Bandcamp, YouTube, Google or Apple. The software is provided "as is", without warranty (see [LICENSE](LICENSE)).

Third-party components are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
