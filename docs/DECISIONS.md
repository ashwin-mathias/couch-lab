# Sparky IPTV — Decisions and lessons

Why the app is the way it is. Read this before changing behaviour, so settled questions aren't reopened by accident. Newest first.

---

## Product decisions (as agreed with Ashwin)

| Version | Decision | Reason |
|---|---|---|
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
5. **Lazy list keys must be unique** even when providers duplicate IDs. Prefix them with the index.
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

## Known limitations (accepted)
- The EPG comes only from `get_short_epg` (12 entries per channel, fetched lazily). There's no XMLTV.
- The M3U playlist type has no EPG, movies or series.
- The LinkedIn URL in `assets/about.json` is empty until Ashwin provides it.
- Search matches raw names only (no fuzzy matching).
