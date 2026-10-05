# Third-party software and services

SEVBY itself is MIT licensed (see `LICENSE`). The Android APK is the exception: see *Android app* below. It would not exist without the projects below, which belong to their respective authors. Each keeps its own licence.

| Component | Author / project | Licence | How SEVBY uses it |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | yt-dlp contributors | Unlicense (public domain) | Imported as a library; does all searching/downloading from YouTube and Bandcamp |
| [FFmpeg](https://ffmpeg.org) | FFmpeg developers | LGPL 2.1+ or GPL 2+ (GPL v3 in the Windows build) | Bundled as a **separate executable**; MP3 conversion, tags, cover art |
| [customtkinter](https://github.com/TomSchimansky/CustomTkinter) | Tom Schimansky | MIT | User interface |
| [tkinterdnd2](https://github.com/Eliav2/tkinterdnd2) | Eliav2 and contributors (tkdnd by Petasis) | MIT | Drag-and-drop |
| [PyInstaller](https://pyinstaller.org) | PyInstaller team | GPL-2.0 with a bootloader exception that allows distributing built apps under any licence | Build tool only |
| Python / Tk | Python Software Foundation / Tcl-Tk | PSF / Tcl licence | Runtime |

## Android app (`android/`)

The SEVBY source code in `android/` is MIT licensed like the rest of this repo. The Android **APK** also contains
the libraries below. Because youtubedl-android is licensed under the GNU GPL v3, **the APK as a whole is distributed
under the GNU GPL v3** (<https://www.gnu.org/licenses/gpl-3.0.html>). The complete source code for every APK is
this repository at the matching `android-v…` tag; the libraries' own sources are linked below.

| Component | Author / project | Licence | How SEVBY uses it |
|---|---|---|---|
| [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) (fork of [yausername/youtubedl-android](https://github.com/yausername/youtubedl-android)) | yausername, JunkFood02 and contributors | GPL-3.0 | Runs yt-dlp, Python and FFmpeg on Android |
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) (+ yt-dlp-ejs) | yt-dlp contributors | Unlicense | Searching and downloading (updatable from the app) |
| [Python](https://www.python.org) | Python Software Foundation | PSF License | Runs yt-dlp (bundled by youtubedl-android) |
| [FFmpeg](https://ffmpeg.org) | FFmpeg developers | LGPL 2.1+ / GPL (see https://ffmpeg.org/legal.html) | MP3 conversion (bundled by youtubedl-android) |
| [QuickJS](https://bellard.org/quickjs/) | Fabrice Bellard, Charlie Gordon | MIT | JavaScript for YouTube (bundled by youtubedl-android) |
| [OpenSSL](https://www.openssl.org) | OpenSSL Project | Apache-2.0 | HTTPS inside Python (bundled by youtubedl-android) |
| [mutagen](https://github.com/quodlibet/mutagen), [pycryptodomex](https://github.com/Legrandin/pycryptodome) | their authors | GPL-2.0+, BSD / public domain | Python libraries bundled by youtubedl-android |
| Kotlin, kotlinx.coroutines, AndroidX, Material Components | JetBrains, Google | Apache-2.0 | App framework and user interface |

## FFmpeg

FFmpeg is a trademark of Fabrice Bellard. SEVBY includes an unmodified FFmpeg build as a separate program that SEVBY runs as a command-line tool.

- FFmpeg licence information: https://ffmpeg.org/legal.html
- FFmpeg source code: https://ffmpeg.org/download.html#get-sources
- **Windows build:** FFmpeg git build 2026-10-01 (essentials) from gyan.dev, https://www.gyan.dev/ffmpeg/builds/ . This build is licensed under the GNU GPL v3 (it was configured with --enable-gpl --enable-version3). Source code: https://ffmpeg.org/download.html#get-sources and the source link on the gyan.dev builds page.
- **Linux and macOS builds:** static FFmpeg binaries fetched by the build workflow (FedericoCarboni/setup-ffmpeg). Their licences and sources are listed on the download pages linked from that project.

## Services (not bundled, not affiliated)

SEVBY talks to these services but is not affiliated with, endorsed by, or sponsored by any of them. Their names and trademarks belong to their owners.

- **Spotify**: Web API, used only to read playlist details. Use of the API is governed by the [Spotify Developer Terms](https://developer.spotify.com/terms). Each user supplies their own Client ID.
- **Bandcamp**: public track pages.
- **YouTube / Google**: public videos.
- **Apple iTunes Search API**: public search, used only to look up album name, year, track number and cover for a song when Bandcamp doesn't provide them. Not affiliated with Apple.
- **GitHub**: the "About & updates" screen asks GitHub for the newest yt-dlp release and, if you press Update, downloads that file from the official yt-dlp releases page into your user folder.
