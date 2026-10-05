# SEVBY for Android

A native Android version of SEVBY, built on [youtubedl-android](https://github.com/yausername/youtubedl-android)
(which bundles yt-dlp, Python and FFmpeg). Work in progress, built in stages.

## Run it from Android Studio

1. **File → Open** and choose this `android` folder (not the repo root).
2. Let Gradle sync (first time downloads a lot).
3. Plug in the phone with USB debugging on, pick it in the device list, press **Run**.

## Get an APK from GitHub

Every push that changes `android/` runs the **Build Android APK** workflow.
Open the run under **Actions** and download the `SEVBY-android-…` artifact (a zip with the APKs):

| File | For |
|---|---|
| `…-arm64-v8a.apk` | almost every phone from the last ~7 years (smallest) |
| `…-armeabi-v7a.apk` | older 32-bit phones |
| `…-universal.apk` | any device, if unsure (largest) |

Pushing a tag like `v2.0.0` also attaches the APKs to that GitHub release.
A manual run with **publish** ticked adds them to the newest release.

## Signing key (do this once, so updates install over the old app)

Without a key, the workflow signs with a throwaway key and every new build has to be
installed after uninstalling the previous one. To fix that:

```bash
keytool -genkeypair -v -keystore sevby.jks -alias sevby -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 sevby.jks > sevby.jks.b64      # macOS: base64 -i sevby.jks -o sevby.jks.b64
```

In the repo: **Settings → Secrets and variables → Actions → New repository secret**, add:

- `SEVBY_KEYSTORE_BASE64` – the contents of `sevby.jks.b64`
- `SEVBY_KEYSTORE_PASSWORD` – the keystore password you chose
- `SEVBY_KEY_ALIAS` – `sevby`
- `SEVBY_KEY_PASSWORD` – the key password (same as the keystore password if you pressed Enter)

Keep `sevby.jks` and its password somewhere safe and **never commit it** (it's in `.gitignore`).
If you lose it, users have to uninstall before installing future versions.

Builds run from Android Studio use your local debug key, so switching between a Studio
build and a GitHub build on the same phone also needs an uninstall first.
