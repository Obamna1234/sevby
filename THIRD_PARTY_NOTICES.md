# Third-party software and services

SEVBY itself is MIT licensed (see `LICENSE`). It would not exist without the projects below, which belong to their respective authors. Each keeps its own licence.

| Component | Author / project | Licence | How SEVBY uses it |
|---|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | yt-dlp contributors | Unlicense (public domain) | Imported as a library; does all searching/downloading from YouTube and Bandcamp |
| [FFmpeg](https://ffmpeg.org) | FFmpeg developers | LGPL 2.1+ or GPL 2+ (depends on the build) | Bundled as a **separate executable**; MP3 conversion, tags, cover art |
| [customtkinter](https://github.com/TomSchimansky/CustomTkinter) | Tom Schimansky | MIT | User interface |
| [tkinterdnd2](https://github.com/Eliav2/tkinterdnd2) | Eliav2 and contributors (tkdnd by Petasis) | MIT | Drag-and-drop |
| [PyInstaller](https://pyinstaller.org) | PyInstaller team | GPL-2.0 with a bootloader exception that allows distributing built apps under any licence | Build tool only |
| Python / Tk | Python Software Foundation / Tcl-Tk | PSF / Tcl licence | Runtime |

## FFmpeg

FFmpeg is a trademark of Fabrice Bellard. SEVBY includes an unmodified FFmpeg build as a separate program that SEVBY runs as a command-line tool.

- FFmpeg licence information: <https://ffmpeg.org/legal.html>
- FFmpeg source code: <https://ffmpeg.org/download.html#get-sources>
- **Build used in the release files:** <FILL IN: the exact build you bundle, e.g. gyan.dev "release essentials" or the BtbN / setup-ffmpeg build, with its version number and the link to that build's source/licence page>

If you bundle a GPL build of FFmpeg, say so here; if you want the LGPL terms, use an LGPL-licensed build.

## Services (not bundled, not affiliated)

SEVBY talks to these services but is not affiliated with, endorsed by, or sponsored by any of them. Their names and trademarks belong to their owners.

- **Spotify** — Web API, used only to read playlist details. Use of the API is governed by the [Spotify Developer Terms](https://developer.spotify.com/terms). Each user supplies their own free Client ID.
- **Bandcamp** — public track pages.
- **YouTube / Google** — public videos.
