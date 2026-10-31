# Sparky IPTV — Acceptance tests

A build is "done" only when every check below passes on a real **Google TV (TCL, 4K dongle)** and an **NVIDIA Shield**, using a large Xtream account (tens of thousands of channels, 100k+ movies and series). Section R lists bugs we have already shipped and fixed; each one is a regression test.

Tick each box. Note the build number at the top of the run.

---

## A. Install and update
- [ ] A1. Downloader code 6976634 installs the latest release. The app appears as **Sparky IPTV** with the banner on the Google TV home screen.
- [ ] A2. A newer build installs **over** the old one. Login, favourites and history are kept.
- [ ] A3. With a newer GitHub release: the toast "Update available — open Settings to install" appears, Settings shows the highlighted row, and the rail Settings icon shows the accent dot.
- [ ] A4. The first install attempt opens the "install unknown apps" screen. After allowing it and pressing Back, install downloads (showing MB) and opens Android's installer.
- [ ] A6. (2.5.2) An update installs only when the release has `sparky-iptv.apk.sha256`. Remove that file from a test release: no update toast. Edit the file's contents: Install shows "Update failed: The update failed its integrity check…" and nothing installs.
- [ ] A5. Check for updates on the latest build shows "You have the latest version".

## B. Setup
- [ ] B1. A fresh install opens Setup with a QR code and an address. The phone camera opens the form.
- [ ] B2. A wrong password shows an error on the phone with the fields kept. The TV stays on Setup.
- [ ] B3. A URL pasted with `/player_api.php?…` or without `http://` still works.
- [ ] B4. A correct login shows "You're connected" on the phone. The TV starts Loading by itself.
- [ ] B5. The M3U tab works with a valid M3U link (live channels only).
- [ ] B6. Settings → Change login → Back keeps the current login.

## C. Loading and performance
- [ ] C1. The first load shows five steps with MB downloaded and counts. The percentage never sits at 100% while work continues ("Preparing your library").
- [ ] C2. If one section fails (for example, series), the others still load. The step shows failed and the app opens.
- [ ] C3. With Wi-Fi off: "This TV isn't connected to the internet…", with Try again / Change login / Diagnostics.
- [ ] C4. **A second launch reaches Home in under 5 s** on the dongle with a large catalog. Movies and series fill in afterwards ("Loading movies… N%").
- [ ] C5. No crash or app exit at any point during first load or later loads with the largest catalog.
- [ ] C6. With the last refresh more than 24 h ago, opening the app refreshes in the background ("Updating playlist…"). Settings then shows "Updated just now".

## D. Navigation and focus (on every screen)
- [ ] D1. Something is always visibly focused. No key press leaves focus invisible or stuck.
- [ ] D2. The nav rail expands on focus. Every section opens. Back from any section goes to Home; Back twice on Home exits.
- [ ] D3. Nothing is clipped at the screen edges on the TCL TV (safe area).
- [ ] D4. Returning from full screen lands on the channel just watched.

## E. Regions
- [ ] E1. The first successful load with 4+ regions shows "Which regions do you watch?" once only.
- [ ] E2. Choosing US + IN limits Live groups, the Guide, Search and Movies/Series categories. Favourites still show everything.
- [ ] E3. "Show all regions" at the bottom of the group list toggles temporarily. Settings → My regions → Show all regions clears the choice.
- [ ] E4. Channel and group names show without the "US|" prefix. `##### NEWS #####` rows show as headings and are never playable.

## F. Live TV
- [ ] F1. Moving onto a group shows its channels after a short pause, without OK; focus stays on the group (2.7). The counts are right.
- [ ] F2. Each channel row shows the now programme, time left and progress (when the provider has EPG).
- [ ] F3. **The preview plays with sound** about 0.7 s after resting on a channel. It stops when leaving Live TV.
- [ ] F4. OK goes to full screen **without the stream restarting** (handoff).
- [ ] F5. Hold OK on a channel adds it ("Added to Favorites · hold OK again for folders"); hold OK on a favourite opens the menu (2.7). A short OK in full screen pauses (2.5).
- [ ] F6. In full screen: ▲/▼ change channel, skipping dividers and wrapping; CH+/CH− do the same; the info bar shows for 5 s.
- [ ] F7. In full screen, ◀ and Back both return to the previous page, and the preview keeps playing there.
- [ ] F8. ▶ opens Audio & subtitles. Choosing a subtitle language works. Leaving and coming back to that channel restores it.
- [ ] F9. A broken channel tries the other format once, then shows "This channel isn't playing right now."
- [ ] F10. Tiles/List toggle on the Favorites and Recently watched groups. The choice is remembered.

## G. Guide
- [ ] G1. The grid starts at the current half hour and covers 2.5 h. The accent now line is at the right time.
- [ ] G2. Focusing a cell updates the header (title, times, time left, description).
- [ ] G3. The Favorites pill toggles. The Group picker opens on the current group, and choosing one changes the Live TV group too.
- [ ] G4. Rows without EPG are compact and read "no guide information".
- [ ] G5 (2.6). In a region with divider rows (News, Sports, Movies…), hold ▼ from the first row to the last: focus visits every playable row in order, never jumps, and stays at the same time. ▼ on the last row does nothing.
- [ ] G6 (2.6). Hold ▲ back to the top: same, then ▲ on the top row goes to the header pills.
- [ ] G7 (2.6). Group picker: hold ▼ through all groups across several region headings, no jumps; "Show all regions" can be reached and pressed.
- [ ] G8 (2.6). Open a group whose rows still say "Loading…" and leave focus on one: when its guide data arrives, focus stays on that row.
- [ ] G9 (2.6). Move ▶ to a later show, then ▼ several times: focus stays at that later time in each row.
- [ ] G10 (2.6). Diagnostics has no "Guide focus: expected row" warnings after G5–G9.

## F2. Live TV lists (2.6)
- [ ] F2a. In a group with divider rows, hold ▼ through the channel list: every channel in order, headings skipped, no jumps.
- [ ] F2b. Hold ▼ through the Groups column across region headings: no jumps; the channel list follows each group.

## F3. Live TV navigation (2.7)
- [ ] F3a. Hold ▼ through all groups, and pause 2 s on several: **focus never leaves the Groups pane**; the channel pane follows each group.
- [ ] F3b. ▶ and OK on a group enter the channel list at the last-watched channel in that group, otherwise the first. In a group you scrolled earlier, focus lands on a channel on screen.
- [ ] F3c. ◀ from any channel (list) or a first-column tile returns to the selected group.
- [ ] F3d. Back from full screen returns focus to the channel just watched.
- [ ] F3e. OK on a region heading closes it (▸); ▲/▼ then skip its groups; OK opens it again (▾). ▶ opens a closed heading; on an open one ▶ goes to its first group.
- [ ] F3f. Open/closed headings survive leaving Live TV and a restart, and match in the Guide's group picker.
- [ ] F3g. Close the region of the selected group: focus stays on the heading, the heading shows as selected, and the channel pane doesn't change. ◀ from a channel then lands on that heading.

## F4. Favorites order and folders (2.7)
- [ ] F4a. Upgrade from 2.6: favorites appear in the same order as before, nothing lost (Live TV, Home, Favorites page).
- [ ] F4b. Live TV → Favorites: hold OK on the 8th channel → Move → ▲ six times → OK: "Moved … to position 2". The order holds after leaving, after a restart, on Home, on the Favorites page and in the Guide.
- [ ] F4c. Back during Move puts the item back ("Move cancelled"). While moving, no other key does anything.
- [ ] F4d. The menu opens only after OK is released; the release doesn't press a menu row. Remove from favorites removes it (two presses in total).
- [ ] F4e. Favorites page: Move in the list (▲▼), in tiles (◀▶ by one, ▲▼ by a row), and a movie in the poster row (◀▶). Home Favorites row: ◀▶.
- [ ] F4f. Hold OK on a favorite channel → Folders… → New folder… "US News" → Create: it shows under Favorites in Live TV (count 1), in the Guide picker, and as a Home row. Add 2 more channels via Folders…: count 3.
- [ ] F4g. A channel in two folders shows in both; removing it from Favorites removes it from both.
- [ ] F4h. Full screen from a folder: ▲/▼ stay inside the folder, in its order.
- [ ] F4i. Settings → Favorite folders: create, rename ("There's already a folder called …" for a duplicate), Move, and Delete (two presses; channels stay in Favorites). Home rows follow the folder order.
- [ ] F4j. An empty folder has no Home row but shows in Live TV with count 0 and its empty text.
- [ ] F4k. Phone page (Favorite folders QR): create "India News", add channels with the search box, reorder with ↑ ↓, reorder All favorite channels, Save: the TV shows "Favorites updated from your phone" and updates without a restart. A blank or duplicate name shows an error on the phone and changes nothing.

## F5. Backup and restore (2.7) — TCL dongle, iPhone Safari, an Android phone
- [ ] F5a. Settings → Back up settings, scan, all boxes ticked, passphrase twice → Download backup: the file `sparky-backup-YYYY-MM-DD.json` saves (Files on iPhone, Downloads on Android); the TV shows "Backup saved to your phone".
- [ ] F5b. Opened in a text editor, the encrypted file shows no readable password, username or server.
- [ ] F5c. Passphrase checks: under 6 characters, and two different passphrases, show their errors. Unticking Login hides the passphrase fields, and the file is plain JSON without a login.
- [ ] F5d. Fresh install (uninstall, reinstall) → setup page → **Restore from a backup file** → file + passphrase → Restore: the TV logs in, loads, and opens Home with "Restored · N favorites · settings from …". Favorites (order and folders), regions (and open/closed headings), subtitle style, sleep timer default and track choices are back, with no typing on the TV.
- [ ] F5e. Fresh install with a backup **without** the login: the phone returns to the login form with "Backup ready: …"; after Send to TV everything else is restored.
- [ ] F5f. Wrong passphrase: "Wrong passphrase · 2 tries left", then 1; the third locks the page ("… scan the new QR code").
- [ ] F5g. Existing install, **Add**: favorites merged with no duplicates, folders with the same name merged. **Replace**: favorites and folders match the file exactly.
- [ ] F5h. A backup with a different login asks "This backup uses a different login (…)" and only replaces it after **Restore anyway**.
- [ ] F5i. A favorite whose ID changed but whose name exists is restored by name; a gone one is reported: "41 of 43 favorites restored · 2 not found: …".
- [ ] F5j. A damaged file, a non-Sparky JSON, and a file with `"version": 2` are refused with their messages; nothing changes.
- [ ] F5k. The page stops working after leaving the TV screen, and after 10 minutes. Another device on the Wi-Fi without the key gets 401.

## H. Home, Favorites, Recent
- [ ] H1. Home rows appear in the order Favorites, your folders (2.7), Recently watched, Recently added. Empty rows are hidden. The empty state appears with no data.
- [ ] H2. Home channel tiles play live with sound after 1 s.
- [ ] H3. The Favorites page shows channels (tiles or list) plus movie/series posters, with a live preview pane on the right.
- [ ] H4. The Recently watched page shows the last 12 items newest first. A channel appears after 10 s of watching.

## I. Movies and Series
- [ ] I1. The chips work. Continue watching shows on Recently added after a movie is part-watched.
- [ ] I2. Movie detail shows Resume and Start over when in progress, and favourites toggle.
- [ ] I3. Series detail resumes the last episode. The seasons switch on focus. The episode progress bars are right.
- [ ] I4. Hold OK on any poster toggles the favourite.

## J. Movie and episode player (2.3)
- [ ] J1. The bar shows on start and hides after 5 s. It stays while paused.
- [ ] J2. **With the bar hidden: OK pauses/plays, ◀/▶ jump 10 s, ▲/▼ bring the bar back.** (This was the 2.2 defect.)
- [ ] J3. Holding ▶ accelerates (10 s, then 30 s, then 60 s steps). Rapid presses add up. Seeking never passes the end.
- [ ] J4. ▼ moves to the buttons; ◀/▶ move between them; OK presses; ▲ returns to the timeline.
- [ ] J5. Audio & subtitles opens from its button and from MENU/CC. Back closes the panel, then the bar, then leaves.
- [ ] J6. Picking a subtitle language applies to the **next** movie automatically (the general setting).
- [ ] J7. The Next episode button only appears for series with a next episode. At the end of an episode, the next one starts automatically.
- [ ] J8. Leaving at 20 min and reopening offers Resume at about 20 min. Finishing clears the resume point.
- [ ] J9. Remote media keys (play/pause, rewind, fast forward, next) work.

## J2. Subtitle style (2.4)
- [ ] J2-1. Settings → Subtitle style shows five options and a preview. ◀/▶ change the value; the preview updates at once.
- [ ] J2-2. The default is Medium · White · Drop shadow · Semi-transparent box, and it already applies to existing users after the update.
- [ ] J2-3. Each size, colour, background and edge shows correctly on a playing movie with subtitles on. Raised clearly lifts the text.
- [ ] J2-4. While watching: Audio & subtitles → Subtitle style… changes the live subtitles. Back returns to the track list, then closes the panel.
- [ ] J2-5. Subtitles with their own colours (styled SSA/WebVTT) still use our style.
- [ ] J2-6. The style survives an app restart and Clear watch history. Reset to default restores the defaults.
- [ ] J2-7. Live TV subtitles use the same style.

## J3. Channel switching (2.5)
- [ ] J3-1. ▲▼ in full-screen live updates the info bar instantly. The picture follows in **under 2 s** on the TCL dongle and the Shield (see "Channel switch: average" in Diagnostics).
- [ ] J3-2. Pressing ▲ five times quickly loads only the last channel (the log shows one switch, not five).
- [ ] J3-3. Never more than one stream: with a second device playing on the same account, Sparky shows "Provider says too many connections (…)". It doesn't hammer the provider with format retries.

## J4. Pause, rewind and catch-up (2.5)
- [ ] J4-0. Diagnostics → Test catch-up reports "Catch-up works (…)". If it fails, record the reason here.
- [ ] J4-1. OK on live pauses: the picture freezes, the PAUSED chip shows, and the bar shows with Play, Start of show, Go live, Audio & subtitles.
- [ ] J4-2. On a catch-up channel, resume after 2 minutes continues from the pause moment, not live. The chip shows "BEHIND LIVE 2:0x".
- [ ] J4-3. ◀ rewinds 10 s per press; holding goes faster; ▶ forward. Forward to within 30 s of now goes live.
- [ ] J4-4. Start of show plays from the programme's start. Go live returns to live.
- [ ] J4-5. On a channel without catch-up: pause works, with the hint "Pause only · no rewind". After a long pause, resume goes live with the toast.
- [ ] J4-6. Back always leaves, from any pause or behind-live state. CH± changes channel and starts it live.
- [ ] J4-7. Hold OK still toggles the favourite in every state.
- [ ] J4-8. Guide: ◀ on the first cell of a catch-up channel moves 90 min back, repeatedly, up to 7 days. Replayable cells show ↺; the Now pill returns.
- [ ] J4-9. OK on a ↺ show plays it from its start in the movie player, with seek, pause and Audio & subtitles. No resume point appears on Movies.
- [ ] J4-10. A past show outside catch-up shows the toast "This show can't be replayed".
- [ ] J4-11. The recording's start time is right (the show begins at its start, not hours off). This checks the server time-zone handling.

## J5. About (2.5)
- [ ] J5-1. LinkedIn is gone. The Sparky card shows her photo, the note with "Sparky" in bold, and the two QR codes, all inside the safe area.
- [ ] J5-2. Scanning the Venmo code opens Ashwin's Venmo; scanning the Zelle code opens Zelle for Ashwin's email.

## J6. Sleep timer (2.6)
- [ ] J6a. On live TV, pause (OK), ▼ to the buttons: Sleep is after Audio & subtitles. In a movie, Sleep is after Audio & subtitles too.
- [ ] J6b. Sleep opens "Sleep timer" with focus on 90 min (no timer running). Back closes with no change.
- [ ] J6c. Choose 30 min: toast "Sleep timer: 30 min"; the clock shows "Sleep in 30 min". Reopen: focus is on 30 min with ✓.
- [ ] J6d. At live, ▶ opens Audio & subtitles; its last row "Sleep timer…" shows "Sleep in 29 min" and opens the same panel.
- [ ] J6e. Change channels, and go from live TV to a movie: the timer keeps counting.
- [ ] J6f. With 1 minute left: toast "Sleeping in 1 minute · press any key to keep watching". Press any key: "Sleep timer: 30 min", and the key does nothing else (no pause, no channel change).
- [ ] J6g. Let it run out: playback stops, the player closes to the page it came from, toast "Sleep timer stopped playback". The Live TV preview doesn't restart until a key is pressed. Diagnostics shows "Stream: none" and the log line "Sleep timer: stopped playback after 30 min".
- [ ] J6h. Off cancels: toast "Sleep timer off"; the clock no longer shows "Sleep in".
- [ ] J6i. Set Settings → Sleep timer default to 60: the panel then opens on 60 min.
- [ ] J6j. Start a timer, close the app (Back twice from Home), reopen: no timer runs.

## K. Search and Settings
- [ ] K1. Typing 2+ letters shows channel, movie and series rows. Region filtering applies; favourites are always included.
- [ ] K2. Settings rows all work. Playlist info is correct (status, expiry, counts).
- [ ] K3. Clear watch history removes resume points **and** saved audio/subtitle choices. Favourites and recents stay.
- [ ] K4 (2.6). Settings shows PLAYBACK, LIBRARY, ACCOUNT and APP with single-line rows (value on the right) and fits on screen without scrolling (no update pending). All rows are reachable with ▲/▼ in order, starting on Subtitle style.
- [ ] K5 (2.6). Sleep timer default: ◀ ▶ change 30 / 60 / 90 (no wrap; focus stays on the row); OK steps 30 → 60 → 90 → 30. The value is kept after restarting the app.
- [ ] K6 (2.6). Clear watch history: the first OK shows "Press OK again to clear", which goes back after 3 s; two OKs within 3 s clear with "Watch history cleared".
- [ ] K7 (2.6). Version shows "2.7 (build N)"; OK checks for updates with the usual toasts. With an update pending, the update row is on top and focused first.

## L0. Phone pages and stored login (2.5.2)
- [ ] L0a. Scanning the Setup QR opens "Connect your TV". Typing the IP and port without `?k=…`, or with a wrong key, shows "Scan the QR code on your TV to open this page." (403). Same for the Diagnostics QR.
- [ ] L0b. Leaving Setup and returning gives a new QR; the old link no longer works.
- [ ] L0c. After upgrading from 2.5.1, the login still works with no re-entry. `adb shell run-as com.sparky.iptv cat shared_prefs/sparky.xml` shows `playlist_enc` and no `playlist`, and no readable password.
- [ ] L0d. `adb backup` of the app produces no data (backup disabled).

## L. Diagnostics and About
- [ ] L1. The Diagnostics QR opens the log on the phone. Copy everything works. **No password or username appears anywhere in the log.**
- [ ] L2. Diagnostics shows the catch-up line.
- [ ] L3. After a forced crash, the next launch shows the red "closed unexpectedly" box once. Diagnostics shows the last crash.
- [ ] L4. About shows the avatar, name, LinkedIn placeholder, version and release history (LATEST on the first card). The cards scroll with the remote.

## M. Lifecycle
- [ ] M1. Pressing Home on the remote pauses playback; returning resumes it.
- [ ] M2. The app survives being left on full-screen live for 2+ hours without slowing down.

---

## R. Regression tests (bugs we have shipped and fixed)

| # | Version | Bug | Check |
|---|---|---|---|
| R1 | 1.0 | App closed while loading a huge catalog (out of memory from reading whole JSON into memory) | C5 with the largest account; memory in Diagnostics stays well under the maximum |
| R2 | 1.0 | Loading looked stuck at 100% while still parsing | C1 |
| R3 | 1.1 | "Problem parsing the package" when Downloader saved only part of the file (a retry worked) | A1/A4. The in-app installer must only open after the download completes |
| R4 | 2.0 | ~40 s startup from re-parsing JSON every launch | C4 |
| R5 | 2.1 dev | Crash on start: `init {}` ran before properties were initialised | Cold start after clearing data and with saved data |
| R6 | 2.1 | Nav rail overlapped tiles; edges cut off on TV | D3 |
| R7 | 2.1 | Back/◀ from full screen went to the wrong page; preview restarted | F4, F7 |
| R8 | 2.1 | Duplicate provider IDs crashed lazy lists ("key already used") | Lists use stable id keys with `#2` added to repeats (2.6; position-prefixed keys before); scroll every list fully |
| R9 | 2.2 | Build broke: Media3 `C` vs theme `C` ambiguity | Clean build passes; `MediaC` alias used |
| R10 | 2.2 | **VOD controls: after the overlay hid, no remote key brought it back; the gear UI was poor** | J1–J5 |
| R11 | 2.4 | Diagnostics showed "(build 14) (build 14)" | Diagnostics header shows the build once |
| R12 | 2.4 | First refresh logged "last update 498100h ago" | Clear data, set up, open Diagnostics: "Daily refresh: first update" |
| R13 | 2.4 | A second Install press downloaded the update twice | Press Install twice quickly: one "Downloaded update" log line |
| R14 | 2.4 | Cancelled loads (e.g. a detail page closed early) were logged as errors | Open and quickly close several movie details: no ERROR lines |
| R16 | 2.5 | Test catch-up said "not a video stream (65536 bytes)" for real recordings | Test catch-up reports "Catch-up works …" with per-format results |
| R17 | 2.5 | Resuming after a 1-minute pause sat there doing nothing | Pause 1 min on a catch-up channel, resume: it plays from the pause, or within 10 s goes live with a message; the log explains |
| R18 | 2.5 | Copy everything did nothing on the phone (http page) | Copy pastes the full log; Select all works |
| R19 | 2.5.2 | Setup and log pages open to anyone on the Wi-Fi; login in plain text; update not verified | See L0a–L0d and A6 |
| R20 | 2.5 | **TV Guide: scrolling down with ▼, focus sometimes jumped to an unexpected place** (across divider rows, or to rows not yet on screen) | G5–G8, F2a, F2b |
| R21 | 2.6 | **Live TV: scrolling down the Groups pane, focus sometimes jumped to the channel pane** (the channel list took focus 150 ms after each group change) | F3a, F3g |
| R15 | 2.4 | Vague "This channel isn't playing right now." with no reason | Play on 2 devices: the message names the code (509) and the fix |
