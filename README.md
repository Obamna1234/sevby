***Beta: Linux/Mac builds untested. The Android version is command-line only and lightly tested.***

# SEVBY: Playlist to MP3

<img width="677" height="827" alt="image" src="https://github.com/user-attachments/assets/67837605-dd38-4f90-9a44-0b76e37801aa" />

*SEVBY running a .txt file*



<img width="677" height="828" alt="image" src="https://github.com/user-attachments/assets/88569e0c-0281-4825-beb7-12afde1dda84" />

*SEVBY running a Spotify playlist*


Turn a Spotify playlist (or a plain text list of songs) into tagged MP3s on your own computer.
SEVBY looks for each song on **Bandcamp** first, and falls back to **YouTube** for anything it can't find.

- Paste a Spotify playlist link (public or private) **or** a list / `.txt` file of `Artist - Title` lines
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

**Android:** there is a command-line version for Termux (no window). See [TERMUX.md](TERMUX.md). It is a beta.

## Using a Spotify playlist (one-time setup, free)

Spotify requires every app to have a free Client ID. You only do this once:

1. Go to <https://developer.spotify.com/dashboard> and log in.
2. **Create app** - any name. Under **Redirect URIs** add exactly `http://127.0.0.1:8888` and save.
3. Open the app's **Settings** and copy the **Client ID**.
4. In SEVBY choose *Spotify playlist link*, paste the playlist link and the Client ID, pick a save folder and press **Start**.

The first time, your browser opens so you can log in to Spotify and approve access (this is what allows private playlists). It's remembered afterwards. No Client Secret is needed.

Don't want to set that up? Export the playlist to text with [Chosic](https://www.chosic.com/spotify-playlist-exporter/) and use *Song list / .txt file*.

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
- **[Spotify Web API](https://developer.spotify.com/documentation/web-api)** - only used to read playlist track names and details (no audio comes from Spotify)
- **[Bandcamp](https://bandcamp.com)** - please support artists by buying their music there when you can
- The "Artist - Title" list idea owes a nod to **[Chosic's playlist exporter](https://www.chosic.com/spotify-playlist-exporter/)**, which SEVBY links to as an optional alternative

Full licence details: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Antivirus note
Antivirus note: SEVBY is unsigned and built with PyInstaller, so some antivirus programs (e.g. AVG/Avast) may flag or delay it as an unknown file. The full source code is in this repo and the release files are built automatically by the GitHub Actions workflow. You can also run it from source (see Quick start).

<img width="2475" height="1223" alt="image" src="https://github.com/user-attachments/assets/7602f689-6537-4b33-a1dc-c0a382ddda59" />
https://www.virustotal.com/gui/file/348bacf093abe15f77455337616c0d77955364851724d54a4b4426fb94414a2c/detection

## Disclaimer

SEVBY is a tool for personal use. Only download music you have the right to download, such as music you own, that is free to download, or that the artist allows. Downloading copyrighted material without permission may be illegal where you live, and may break the terms of service of YouTube, Bandcamp or Spotify. You are responsible for how you use it. SEVBY is not affiliated with or endorsed by Spotify, Bandcamp, YouTube or Google. The software is provided "as is", without warranty (see [LICENSE](LICENSE)).

Third-party components are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
