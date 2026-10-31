# Sparky IPTV — Decisions and lessons

Why the app is the way it is. Read this before changing behaviour, so settled questions aren't reopened by accident. Newest first.

---

## Product decisions (as agreed with Ashwin)

| Version | Decision | Reason |
|---|---|---|
| 2.7.1 | **The squirrel on the TV in the icon is black**, not brown (launcher icon, banner, in-app logo) | Ashwin's request |
| 2.8 | **Code review applied: smaller, faster, safer, behaviour unchanged** (R8, trimmed fonts, WebP; cached login; background search; shared connection pool; restore limits; shared phone server) | Ashwin asked for all review items in one release |
| 2.8 | **Backup passphrase at least 10 characters** (was 6) | A backup in iCloud or Files can be guessed offline; Ashwin accepted the recommendation. PBKDF2 rounds stay 210,000 so older backups still open without a format change |
| 2.8 | **R8 without renaming** (`-dontobfuscate`) | Readable crash reports and logs matter more than the extra few hundred KB |
| 2.8 | **Fonts keep every character Inter covers**; only unused OpenType features and private-use glyphs removed | A Latin-only subset would save more, but Cyrillic or Greek channel names would switch to the system font |
| 2.8 | Setup and Diagnostics QR keys expire after 10 minutes without use, with a new QR shown automatically | Same protection as the 2.7 pages, without making anyone press anything |
| 2.7 | **The Groups pane keeps focus**: moving between groups shows the channels after 300 ms but never moves focus; ▶ / OK enters the channel list, ◀ comes back to the selected group | Pausing on a group jumped focus to the channel pane (Ashwin's screenshots) |
| 2.7 | **Collapsible region headings** (OK opens or closes; remembered; shared with the Guide picker) | Ashwin chose this over a separate Regions column or a flat list; it keeps the current layout |
| 2.7 | **Favorites in your own order**, reordered on the TV with **Hold OK → Move**; first start keeps the provider's order | Ashwin wanted to reorder favorites |
| 2.7 | **Favorites folders for channels only**; a channel can be in **several folders**; named on the TV and on the phone; shown in Live TV, the Guide picker and as **Home rows** | Ashwin's answers: US News, India News, India Movies… Movies and series get reordering only |
| 2.7 | **Hold OK on a favorite opens a menu** (Move · Folders… · Remove) instead of removing at once | Removing takes two presses, so no accidental removals; Ashwin agreed |
| 2.7 | **Backup and restore as a file on the phone** (QR → phone page), not a code or the cloud. Login encrypted with a passphrase; watch history a ticked checkbox; restore on an existing install defaults to **Add**; no backup reminders | A short code needs a server (Firebase later); the TV has no camera; a file can be kept. Ashwin agreed all recommendations |
| 2.7 | No 2.6.1: the focus fix waited for 2.7 | Ashwin's call, with the other features |
| 2.7 | Phone favorites page reorders with ↑ ↓ buttons, not drag | Dragging in phone browsers (especially iOS Safari) is unreliable without a library |
| 2.6 | **Guide ▲/▼ move strictly by playable row, keeping the time position**; same for the group picker and the Live TV lists | Fixes focus jumping across divider rows when scrolling down the guide |
| 2.6 | **Sleep timer**: Off · 30 · 60 · 90 min, default 90 (set in Settings), off unless chosen, **not restored after a restart**, any key in the last minute extends it; at the end the player is released so the stream closes | Falling asleep with the TV on kept the one allowed connection busy all night. Ashwin chose 90 min and no restore |
| 2.6 | Sleep timer also reachable from the Audio & subtitles panel | The live buttons row only shows when paused or behind live, so without it the timer would need a pause first |
| 2.6 | **Settings grouped into Playback, Library, Account, App**, single-line rows with the value on the right; Clear watch history needs a second press; the version is a row that checks for updates | The list had become crowded |
| 2.6 | **Phone remote shelved**; **backup and restore planned for 2.7**; Firebase sync later; failover not scheduled | The Google TV remote app already covers a phone remote |
| 2.5.2 | **Security hardening:** one-time key on the phone setup and log pages; login encrypted with the Android Keystore and `allowBackup=false`; updates limited to this repo's https releases and verified against a published SHA-256 | A security audit found the pages open to anyone on the Wi-Fi, the password in plain text in backups, and no check on the downloaded update. Ashwin asked for these three fixes. Not done yet: pinning Actions to commit SHAs, Dependabot, upgrading NanoHTTPD |
| 2.5.1 | **Signing key moved out of the repo into GitHub Actions secrets, and rotated** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) | The old keystore and its password were committed to a public repo, so anyone could sign an APK that installs over the app. Rotating needs a one-time uninstall and reinstall on each device |
| 2.5 | **Faster channel switching with one stream only**: debounce 300 ms, start after about 1 s of buffer, kept-open connection, timing in Diagnostics | Plan allows **1 connection**; no bandwidth wasted |
| 2.5 | **Never preload neighbouring channels** | Would need a 2nd connection (plan has 1) and doubles data |
| 2.5 | **Short OK = pause** on live TV (reverses 2.1's "short OK does nothing"); hold OK stays favourite | The Chromecast remote has no play/pause key |
| 2.5 | Paused or behind live: ◀/▶ rewind and forward, ▲/▼ work the bar, and **Back always leaves**; at live, ◀ still goes back | Agreed with Ashwin |
| 2.5 | Catch-up: pause/resume from the recording, Start of show, Go live, and **past shows in the Guide** all in 2.5 | Provider has 2,347 catch-up channels × 7 days |
| 2.5 | **Failover moves to 2.6** | Keep 2.5 focused |
| 2.5 | About: LinkedIn removed; **Sparky card** with her photo, the note, and Venmo/Zelle "Buy Sparky a treat" QR codes, regenerated cleanly from the decoded links | Prettier; no account digits or face photo; Ashwin OK'd them being public |
| 2.5 | **Show the provider's actual error code** with a plain message, and **don't log cancelled loads as errors** | Make problems clear and the log only real |
| 2.4 | **Subtitle style** setting: size, text colour, background, edge, position. Default: Medium, white, drop shadow, semi-transparent box (picked from six samples on a real frame) | The stock solid black box was heavy; style 3 reads well on bright scenes with the picture showing through |
| 2.4 | The style is one global setting, reachable from Settings and from the player panel, and isn't cleared with watch history | Appearance, not history; adjust while watching |
| 2.3 | Movies/episodes: **◀ = back 10 s, ▶ = forward 10 s**, accelerating when held; OK = play/pause; ▼ = buttons | TV-standard behaviour; replaces the stock controls that lost focus |
| 2.3 | **One general** audio/subtitle setting for all movies and series. A choice made in the panel becomes the new general setting; nothing is stored per title | Per-title storage would grow forever and need cleanup |
| 2.3 | Live per-channel choices are kept, but **capped at the 200 most recently changed** | Small, bounded storage |
| 2.3 | Clear watch history also clears saved audio/subtitle choices | One place to reset |
| 2.2 | Audio & subtitles: yes. Other proposed features (more complexity): no | Keep the app simple |
| 2.2 | Catch-up: report only (count and days in Diagnostics); no playback yet | Check first whether Primestelly offers it |
| 2.1 | Full-screen live: **short OK does nothing**; **hold OK = favourite**; ◀ / Back return to **the page you came from** | Avoid accidental overlays; predictable Back |
| 2.1 | Previews in Favorites/Recent (list and tile), with **sound** | Matches Live TV |
| 2.1 | Home order: **Favorites → Recently watched → Recently added** | What's used most comes first |
| 2.1 | Fix the 40 s start with a binary snapshot and staged loading | Speed |
| 2.0 | Regions chosen **once** (prompted on first run), changeable/resettable in Settings; not required | Huge multi-country catalogs |
| 2.0 | Daily background playlist refresh (once a day) | Fresh lists without waiting |
| 2.0 | Heart icon for favourites (rail, badges); Recent has its own rail icon | Clearer meaning |
| 2.0 | Tile/list toggle on Favorites/Recent | Personal preference |
| 1.1 | In-app updates from GitHub releases | No re-typing the Downloader code |
| 1.0 | Setup from the phone by QR (no remote typing) | Remote typing is painful |
| 1.0 | Public GitHub repo + Actions build + Downloader short code 6976634 | Free, simple install on TV |
| — | Google TV home-screen "Continue watching" row | Discussed; deferred |
| — | iOS version | Discussed; open questions remain (not started) |

## Engineering lessons (don't repeat these)

1. **Never put the stock ExoPlayer controller on a TV screen you also drive with Compose.** When it auto-hides, it keeps focus, and keys stop doing anything. Use one focusable Compose box that handles every key, with virtual focus for bar buttons.
2. **Never load provider lists into memory as strings or JSONObjects.** Stream to disk, parse with `JsonReader`, keep only the needed fields, and build URLs on demand.
3. **Never re-parse JSON on every launch.** Keep a binary snapshot and load live channels first.
4. **`init {}` goes last** in the ViewModel.
5. **Lazy list keys must be unique and stable.** Use the item id, adding `#2`, `#3` only to repeats (`stableKeys`). Index-prefixed keys (`"$index:$id"`, used until 2.5) are unique but change whenever rows move, so focus can drop when the list recomposes.
6. **The `C` name clash**: alias Media3's `C` as `MediaC`.
7. **One shared player** for preview and full screen, with a handoff flag. Otherwise streams restart and providers may count two connections.
8. **Safe area**: pad the shell (36/24/16/16 px), or TVs crop the edges.
9. **Redact secrets** in every log line. Users send logs to Claude.
10. **A section that fails must not fail the whole load.** Keep last time's copy.
11. **Show progress honestly**: MB downloaded, item counts, a separate "Preparing" step, and an elapsed timer.
12. **CI errors are invisible without annotations.** Keep the `::error::` step in the workflow.
13. **Catch-up start times are in the server's local time,** not UTC or the TV's zone. Measure the offset from `server_info` at login.
14. **Don't retry another stream format for login or connection-limit errors** (401/403/429/458/509). It can't help, and it uses the one allowed connection again.
15. **Version names already contain the build.** Don't append it again (the 2.4 Diagnostics bug).
16. **Don't trust one magic byte to recognise video.** Providers' recordings can start mid-packet or use 192-byte packets. Sniff for repeated sync bytes, HLS and MP4, and log what was actually received (the 2.5 test said "not video" for real recordings).
17. **A player must never just sit there.** Every resume or recording start has a 10 s watchdog with a fallback and a message.
18. **The clipboard API needs https.** Pages served over plain http on the home network need the execCommand fallback.
19. **Never commit signing keys or passwords**, even to a private repo. Keep them in GitHub secrets and keep an offline backup. Deleting a file does not remove it from git history, so a leaked key must be rotated.
23. **Don't decrypt on a hot path.** A property getter that goes through the Android Keystore (the saved login) was read for every row while scrolling. Cache secrets in memory after the first read.
24. **Turning on R8 needs its own check on the TV.** Shrinking can remove code reached only by reflection; build it as a separate step and run the acceptance list.
21. **Never move focus from an effect keyed on data.** `LaunchedEffect(channels) { requestFocus() }` in the channel list fired every time the Groups pane changed the group, so focus jumped panes (the 2.6 bug). Move focus only on an explicit user action or on first entry.
22. **When a key opens a dialog on long press, open it on the key's release.** Otherwise the release (and repeats) arrive in the dialog and can press its first row.
20. **On TV, never rely on the default ▲/▼ focus search across non-focusable rows in a lazy list.** Headings between rows, and rows that aren't composed yet, make it jump to a far cell, a header pill or another row (the 2.5 guide bug). Scroll to the target first, then request focus on it (`ListNav`).

## Known limitations (accepted)
- The EPG comes only from `get_short_epg` (12 entries per channel, fetched lazily). There's no XMLTV.
- The M3U playlist type has no EPG, movies or series.
- The LinkedIn URL in `assets/about.json` is empty until Ashwin provides it.
- Search matches raw names only (no fuzzy matching).
