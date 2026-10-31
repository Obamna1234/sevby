## What's new in v1.0.2 (security hardening)

Small, behaviour-preserving security fixes from the October 2026 code review. **No feature changes.**

- Download engine: a crafted or tampered search result can no longer slip options into the downloader
  (yt-dlp option-injection guard + URL validation).
- Your config file (`~/.sevby_config.json`) is kept private to your user account.
- Temporary cover/thumbnail files use unpredictable names.

No critical or high-severity issues were found. See `SECURITY.md`. Earlier releases remain available as fallbacks.

---

## Install (Windows / Linux / macOS)

Everything is included (downloader engine, ffmpeg); nothing else to install.

1. Under **Assets** below, download the file for your system:
   **SEVBY-Windows.zip**, **SEVBY-Linux.zip**, or **SEVBY-macOS.zip**.
2. Extract it and run the **SEVBY** program inside.
   - Windows: if SmartScreen warns, **More info → Run anyway** (the app isn't code-signed yet).
   - macOS: right-click → **Open** the first time to get past Gatekeeper.
3. Your settings are kept in your home folder between versions.

## Install (Android)

Download **SEVBY-Android.apk** from the Android release (tagged `android-v…`), open it, allow installing
unknown apps if prompted, then **Install**. Allow music/audio and notifications on first launch.
