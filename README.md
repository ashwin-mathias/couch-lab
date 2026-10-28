# Sparky IPTV

A clean, minimal IPTV player for Google TV / Android TV (project name: Couch Lab).

- **Live TV** with channel groups, favorites, live preview, and now/next info
- **TV guide** grid
- **Movies** and **Series** from your provider, with resume and "next episode"
- **Search** across everything
- **Phone setup**: scan a QR code on the TV and type your login on your phone

Works with Xtream Codes logins (server URL + username + password) and plain M3U links.

## Install on Google TV

Every push to `main` builds a new app file and publishes it here:

```
https://github.com/ashwin-mathias/couch-lab/releases/latest/download/sparky-iptv.apk
```

1. On the TV: **Settings → System → About**, click **Android TV OS build** 7 times to turn on Developer mode.
2. Install **Downloader** (by AFTVnews) from the Play Store.
3. **Settings → Apps → Security & restrictions → Unknown sources** → turn on **Downloader**.
4. Open Downloader, type the code **6976634** (or the link above), and install.

To update, repeat step 4. The code always points to the newest build. Your login and favorites are kept.

## Remote controls

| Where | Button | Does |
| --- | --- | --- |
| Any list | Hold OK | Add / remove favorite |
| Full-screen live TV | ▲ / ▼ | Next / previous channel |
| Full-screen live TV | OK | Channel list over the video |
| Full-screen live TV | ◀ or Back | Back to the channel list |
| Full-screen live TV | ▶ | Show / hide channel info |
| Movies & episodes | OK | Show playback controls |

## Building

GitHub Actions builds it automatically (`.github/workflows/build.yml`). Locally: `./gradlew assembleRelease` with JDK 17 and the Android SDK.

The signing key in `keystore/` is only used so updates install over the previous version.
