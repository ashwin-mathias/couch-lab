# Sparky IPTV

A clean, minimal IPTV player for Google TV / Android TV, Windows 11 and Mac (project name: Couch Lab).

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

## Install on Windows 11 (x64)

- **Microsoft Store**: Sparky IPTV (Store ID `9P01WT4DC59W`, once published). The Store keeps it up to date.
- **For testing**: download `sparky-iptv-windows-x64.zip` from the [latest release](https://github.com/ashwin-mathias/couch-lab/releases/latest), unzip it anywhere, and run `Sparky IPTV.exe`. If Windows asks about network access, allow it on private networks (it's for the phone setup page).

## Install on Mac (Apple Silicon)

1. Download `sparky-iptv-mac-arm64.dmg` from the [latest release](https://github.com/ashwin-mathias/couch-lab/releases/latest), open it, and drag **Sparky IPTV** to **Applications**.
2. The first time you open it, macOS says it can't verify the app (it isn't notarized yet). Open **System Settings → Privacy & Security**, scroll down, and click **Open Anyway**. After that it opens normally.
3. To update, download the newest DMG and replace the app. Your login and favorites are kept.

## Keyboard and mouse (Windows and Mac)

| Key | Does |
|---|---|
| Arrows | Move, like the remote's D-pad |
| Enter | OK (hold for a second = hold OK, e.g. add a favorite) |
| Esc | Back (also leaves full screen) |
| Space | Play / pause |
| Page Up / Page Down | Next / previous channel |
| M | Audio & subtitles |
| L | Last channel |
| F11 | Full screen on / off |
| Mouse | Hover to move focus, click to select, press and hold for the long press |

## Remote controls

| Where | Button | Does |
| --- | --- | --- |
| Any list | Hold OK | Add a favorite; on a favorite: menu with Move · Folders… · Remove |
| Moving a favorite | Arrows / OK / Back | Move it / drop it / put it back |
| Live TV groups | ▶ or OK | Go to the channels (moving between groups never jumps there by itself) |
| Live TV groups | OK on a region heading | Open or close the region |
| Live TV channels | ◀ | Back to the selected group |
| Full-screen live TV | ▲ / ▼ | Next / previous channel |
| Full-screen live TV | OK | Pause / play (catch-up channels resume from where you paused) |
| Full-screen live TV or movie | OK after "Your plan allows one stream" | Try again (Back leaves) |
| Full-screen live TV | ◀ or Back | Back to the page you came from |
| Full-screen live TV | ▶ | Audio & subtitles |
| Full-screen live TV | Hold OK | Add a favorite; on a favorite: menu with Folders… · Remove |
| Live TV, paused or behind live | ◀ / ▶ | Rewind / forward (hold to go faster) |
| Live TV, paused or behind live | ▼ | Buttons: Start of show, Go live, Audio & subtitles, Sleep |
| Live TV, paused or behind live | Back | Leave |
| TV guide | ▲ / ▼ | Next / previous channel, at the same time (headings skipped) |
| TV guide | ◀ on the first show | Go back in time (catch-up channels), ↺ shows can be replayed |
| Movies & episodes | OK | Play / pause |
| Movies & episodes | ◀ / ▶ | Back / forward 10 seconds (hold to go faster) |
| Movies & episodes | ▼ | Buttons: audio & subtitles, sleep, next episode, start over |
| Any player | Sleep, or ▶ / MENU → Sleep timer… | Stop playback after 30, 60 or 90 min; any key in the last minute keeps watching |
| Movies & episodes | Back | Hide the controls, then leave (your place is saved) |

## Documentation

Full specification, design system, architecture, test checklist and a rebuild prompt live in [docs/](docs/README.md).

## Building

GitHub Actions builds it automatically (`.github/workflows/build.yml`). Locally: `./gradlew assembleRelease` with JDK 17 and the Android SDK.

The signing key is stored in GitHub Actions secrets (not in the repo) and is only used so updates install over the previous version.
