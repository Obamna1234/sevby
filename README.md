***Beta: Linux/Mac builds untested. The Android app is new (beta).***

# SEVBY: Playlist to MP3

<img width="678" height="829" alt="image" src="https://github.com/user-attachments/assets/e6911543-e5e3-4808-8b6a-baf978867f6e" />


*SEVBY running a .txt file*



<img width="680" height="829" alt="image" src="https://github.com/user-attachments/assets/2946097a-d7a3-473a-a860-c484c7692373" />


*SEVBY's Spotify playlist mode (needs a Spotify Client ID, see below)*


Turn a Spotify playlist (or a plain text list of songs) into tagged MP3s on your own computer.
SEVBY looks for each song on **Bandcamp** first, and falls back to **YouTube** for anything it can't find.

- Paste a Spotify playlist link (public or private; needs a Spotify Client ID, see below) **or** a list / `.txt` file of `Artist - Title` lines
- Bandcamp first, YouTube for the rest (or choose *Bandcamp only* / *YouTube only*)
- MP3 files with title, artist, album, year, track number and square cover art
- YouTube matching picks the version with the right length and avoids live / remix / sped-up uploads
- Pause, Stop, *Retry failed*, *Open folder*, and a sound when a run finishes
- **Queue**: add several playlists / song lists, press *Start queue*, and walk away. They run strictly one after another, each in its own folder named after the playlist
- Remembers your folder, mode and source
- Runs locally. No account with SEVBY, nothing is uploaded anywhere

## Quick start

Download the build for your system from the **Releases** page (Windows `.exe`, Linux and macOS builds from the GitHub Actions workflow), or run from source:

```bash
pip install -r requirements.txt
python sevby_app.py
```

From source you also need **ffmpeg** (put `ffmpeg.exe` / `ffmpeg` next to `sevby_app.py`, or install it so it's on your PATH).

## Android

**SEVBY for Android** is a proper app (no Termux needed). Get the APK from the **Releases** page: look for the newest
release named *SEVBY for Android* (tag `android-v…`) and download `SEVBY-…-arm64-v8a.apk` (or `…-universal.apk` if
unsure). Open it on your phone and allow installing apps from your browser or Files app when asked. Android 7.0+.

- Paste or load a `.txt` song list (`Artist - Title` lines), pick a folder, press **Start**
- Bandcamp first, YouTube for the rest, with album details from Bandcamp or Apple's iTunes Search
- Queue, Pause / Resume / Stop (also from the notification), *Retry failed*, skips songs already in the folder
- Keeps running in the background; update the downloader from **About & updates** when YouTube changes
- Spotify playlists: no Spotify login on Android yet. Tap **Open Chosic** under the song box, paste the playlist link into [Chosic](https://www.chosic.com/spotify-playlist-exporter/), then paste or load the song list in SEVBY

The APK bundles youtubedl-android (GPL v3), so the APK as a whole is distributed under the GPL v3; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Developer notes: [android/README.md](android/README.md).
There is also the older command-line version for Termux: [TERMUX.md](TERMUX.md).

## Using a Spotify playlist (one-time setup)

Spotify requires every app to have its own Client ID. You only do this once. **Spotify currently requires a Premium account to use its Web API, so a free Spotify account may not be able to create one.** If you don't have Premium, use the text-list method below instead.

Note: I can't create a Client ID myself (no Premium account), so the Spotify mode has had little testing. The song-list (.txt) mode is the one I use and have tested. If Spotify mode doesn't work for you, please open an Issue with the error message.

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
3. Repeat, then press **Start queue (N)**.

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

> **Keep yt-dlp fresh.** YouTube changes often. The packaged app contains the yt-dlp version from build time, and SEVBY warns you when it is more than 90 days old. Rebuild with `pip install -U yt-dlp` if downloads start failing (for example with HTTP 403).

## Troubleshooting

- **Downloads suddenly stop working (especially from YouTube)** - the sites change often, and the downloader inside SEVBY (yt-dlp) needs updating. The files on the **Releases** page are rebuilt automatically every month with the newest yt-dlp, so download the newest zip for your system and use it in place of the old one. No terminal needed. If it still doesn't work after a fresh download, please open an Issue. This is a hobby project maintained when time allows, so fixes are not guaranteed. Termux and source users can instead run `pip install -U yt-dlp`.
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
- **[Apple iTunes Search API](https://performance-partners.apple.com/search-api)** - album details and covers in the Android app
- **[Spotify Web API](https://developer.spotify.com/documentation/web-api)** - only used to read playlist track names and details (no audio comes from Spotify)
- **[Bandcamp](https://bandcamp.com)** - please support artists by buying their music there when you can
- The "Artist - Title" list idea owes a nod to **[Chosic's playlist exporter](https://www.chosic.com/spotify-playlist-exporter/)**, which SEVBY links to as an optional alternative

Full licence details: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Antivirus note
SEVBY is unsigned and built with PyInstaller, so some antivirus programs (for example Microsoft Defender or McAfee) may flag or delay it as an unknown file. The full source code is in this repo and the release files are built automatically by the GitHub Actions workflow. You can also run it from source (see Quick start). If your antivirus blocks the download, you can run it from source instead.

<img width="2475" height="1223" alt="image" src="https://github.com/user-attachments/assets/7602f689-6537-4b33-a1dc-c0a382ddda59" />
Scan of the original v0.9.0-beta Windows file on 2026-10-04: 3 of 69 engines flagged it (Bkav, McAfee and Zillya). That file has since been replaced by the current release, which is scanned below.


<img width="2529" height="1209" alt="image" src="https://github.com/user-attachments/assets/08b75d06-7340-4f64-9905-e04055f14b2a" />
Scan of the v0.9.1-beta Windows file on 2026-10-05: 4 of 69 engines flag it (Microsoft Trojan:Win32/Wacatac.C!ml, McAfee, Zillya and Bkav). These are generic detections that commonly hit unsigned PyInstaller apps, and I believe they are false positives. I have submitted the file to the vendors for review. The full source code is in this repo and the release files are built by the GitHub Actions workflow, so you can check them yourself or run SEVBY from source instead. Full scan: <https://www.virustotal.com/gui/file/a30f7170a89658d3fb41d5dd2c4f134d4bf26a7996d0302d3e7d01f3a5a44d89>



<img width="598" height="356" alt="image" src="https://github.com/user-attachments/assets/9f4c0a2a-5d83-49ec-a900-8f471ab27aba" />
*What AVG/Avast shows the first time it sees a new file: it sends the file to its lab and holds it for a few hours. This is normal for unknown, unsigned programs and is not a detection of malware.*

## Disclaimer

SEVBY is a tool for personal use. Only download music you have the right to download, such as music you own, that is free to download, or that the artist allows. Downloading copyrighted material without permission may be illegal where you live, and may break the terms of service of YouTube, Bandcamp or Spotify. You are responsible for how you use it. SEVBY is not affiliated with or endorsed by Spotify, Bandcamp, YouTube, Google or Apple. The software is provided "as is", without warranty (see [LICENSE](LICENSE)).

Third-party components are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
