# Sparky IPTV — Product specification

Version described: **2.5.2** (October 2026). This file says **what the app does**. How it looks is in [DESIGN.md](DESIGN.md), how it's built in [ARCHITECTURE.md](ARCHITECTURE.md), and how to prove it works in [ACCEPTANCE.md](ACCEPTANCE.md).

Words used: in quoted on-screen text, `␣` marks a space that must be kept (hint lines use three spaces around each `·`; meta lines join with `   ·   `). **must** = required; **should** = strong default. "OK" = the remote's centre button. ◀ ▶ ▲ ▼ = D-pad. "Hold OK" = press longer than 600 ms.

---

## 1. Purpose and users

- A calm, minimal IPTV player for **Android TV / Google TV**, in the spirit of TiviMate.
- Used by one household on a **Google TV 4K dongle (TCL TV)** and an **NVIDIA Shield TV**, controlled only with a TV remote.
- Content comes from the user's provider (Primestelly) through an **Xtream Codes login** (server URL, username, password). Plain **M3U links** also work for live TV.
- App name on the TV: **Sparky IPTV**. Package `com.sparky.iptv`. Project/repo name: Couch Lab (`ashwin-mathias/couch-lab`).

## 2. Principles

1. **Remote first.** Every screen works with D-pad, OK and Back. Something is always focused. Focus is never lost or trapped.
2. **Fast.** After the first load, Home appears within a few seconds, even with hundreds of thousands of titles.
3. **Never crash on big catalogs.** Large lists stream from disk; memory stays low.
4. **Quiet design.** Dark, warm neutrals, one accent colour, no clutter. Focus is shown by a light fill with dark text.
5. **Private.** The login is stored only on the TV and sent only to the provider. Passwords never appear in logs.
6. **Plain language.** All text in the app is short and friendly ("Couldn't reach your provider"), never technical.

## 3. App flow

```
First launch ──► Setup (QR) ──► Loading ──► [Regions prompt, once] ──► Home
Later launches ──────────────► Loading (saved copy, seconds) ──────────► Home
```

- Navigation is a **stack** of screens: Setup, Loading, Home, About, Diagnostics, Regions, Movie detail, Series detail, Player. Back pops the stack.
- **Home** holds a left **nav rail** and a content area that switches between sections: Home, Live TV, TV Guide, Favorites, Recently watched, Movies, Series, Search, and Settings (pinned at the bottom).
- On Home with a section other than Home selected, Back returns to the Home section. On the Home section, Back shows the toast "Press back again to exit"; a second Back within 2 s closes the app.

## 4. Setup from the phone

- The Setup screen shows a **QR code** for `http://<TV-IP>:<port>/?k=<key>`, with the hint "Scan with your phone's camera while on this Wi-Fi". The port is the first free one from 8080–8090. The key is 128 random bits, new each time the screen opens.
- Any request without the right `k` gets HTTP 403 "Scan the QR code on your TV to open this page." The form posts to `/save?k=<key>`. (2.5.2)
- Scanning opens a phone web page (served by the TV) titled **"Connect your TV"**, with two tabs:
  - **Login**: Playlist name (default "Home"), Server URL, Username, Password (with a Show/Hide toggle).
  - **M3U link**: Playlist name, playlist link.
- **Send to TV** checks the login on the TV:
  - Xtream: calls `player_api.php`; requires `user_info.auth == 1` and status Active. It picks the live format: `m3u8` if allowed (or if no list is given), otherwise `ts`, otherwise the first one listed.
  - M3U: the first 4 KB must contain `#EXTM3U` or `#EXTINF`.
  - On failure, the page shows the form again with the error and the values kept.
  - On success, the phone shows "You're connected". The TV saves the login, clears saved data, and starts Loading. The server stops 3 s later.
- The URL is normalised: `http://` is added if missing, the trailing `/` is removed, and anything from `/player_api.php`, `/get.php` or `/panel_api.php` onward is cut off.
- The TV footer shows "Waiting for your phone…" (green dot), or "Checking your login…" (accent dot) while checking.
  - If a login already exists: "Press Back to keep your current login", and Back returns.
  - Otherwise: "Your login stays on your home network".
- With no Wi-Fi, the QR box shows "No Wi-Fi" and the text "Connect this TV to Wi-Fi to continue".
- **Settings → Change login** opens the same screen.

## 5. Loading

### 5.1 First load (no saved copy)
- Steps shown, each with a status (pending, active, done, failed) and a detail:
  1. Connecting to your provider → "Signing in…" → the account status.
  2. Live TV → "Downloading · 12.3 MB" → "Reading 12.3 MB…" → "4,321 channels".
  3. Movies → … → "N movies".
  4. Series → … → "N series".
  5. Preparing your library → "Organizing…" → "N items".
- For M3U there are two steps: "Connecting to your playlist", then "Channels".
- Each section downloads on its own. **A failed section keeps last time's copy** and the others continue. Only if every section fails does loading stop with an error.
- The progress percentage counts finished steps fully and the active step as half, animated over 600 ms. An "m:ss elapsed" timer runs. The bar has a moving sheen while running.
- The left side shows the logo, "Loading your channels", the line "The first load can take a minute for large playlists. Next time the app opens instantly.", and an internet status line ("Connected to the internet · <IP>" or "No internet connection").
- **On error**: the title becomes "Couldn't load your playlist" and the message is shown. The percentage reads "Stopped" and the bar turns accent. Buttons: **Try again** (focused), **Change login**, **Diagnostics**.
  - Not online → "This TV isn't connected to the internet. Check Wi-Fi and try again."
  - Out of memory → "Your provider's catalog was too large to load on this device."
- If the app crashed last time, a red box appears: "The app closed unexpectedly last time" with a 2-line summary. It shows once per crash.

### 5.2 Later loads (saved copy)
- One step, "Opening your saved channels", with a percentage read from the saved file.
- **Live channels load first and Home opens immediately.** Movies and series then load in the background. While they load, Movies, Series and Home's "Recently added" row show "Loading movies… Movies 40%".
- If a fresh download finished meanwhile, the background load must not overwrite it.
- After opening, the app checks for an app update, shows the Regions prompt if it's due, and runs the daily refresh if due.

### 5.3 Daily refresh
- If the last refresh was more than 24 h ago, the playlist re-downloads silently in the background when Home opens or the app returns to the foreground.
- The Home header and preview panes show "Updating playlist…" / "Updating…" while it runs.
- **Settings → Update playlist now** does the same, with a toast "Playlist updated" or "Couldn't update: <reason>".
- One-off: if catch-up info has never been read (`catchupScanned` is false) and the login is Xtream, a refresh runs once.

## 6. Regions

- Providers prefix names with regions: `US| ABC NEWS`, `UK: …`, `IN | …`. The region code is 1–6 letters, digits, `+` or `-`, optionally followed by a second word, then `|`, `│` or `┃`. Codes map to friendly labels (US → United States, IN → India, EX-YU → Ex-Yugoslavia, 4K → 4K / UHD, and so on; see `Names.kt`). Unknown codes show as-is. No prefix → **Other channels**.
- The channel and group name shown is the name **without** the prefix. Event prefixes (`Live |`, `Next |`, `Soon |`, `Now |`) are not regions; they become badges (§7.4).
- **Regions screen**:
  - Title "Which regions do you watch?" (first run) or "My regions".
  - Text: "Only these regions appear in Live TV, the guide, search and movies. Favorites always show. You can change this anytime in Settings."
  - The top right shows "N selected" (or "Showing all regions") and the channel count.
  - Buttons: **Done** (focused) and **Show all regions** (clears the choice).
  - A 4-column grid of region cards: a checkbox, the label, and "US · 1,234 channels". OK toggles.
- **First-run prompt**: shown once after the first successful load, only if there are 4 or more regions.
- **Effect**: with regions chosen, Live TV, the Guide, Search, and Movies/Series categories and "All" lists show only those regions. Favorites always show. Movies and series whose category has no region prefix always show.
- At the bottom of the group list, **Show all regions / Show only my regions** temporarily overrides the filter. It isn't saved.
- If the selected live group's region is turned off, the group falls back to All channels.

## 7. Live TV

### 7.1 Layout
Three columns: **Groups** (270) | **Channels** (470) | **Preview pane** (the rest).

### 7.2 Group list
- Order: **Favorites**, **Recently watched**, **All channels** (labelled "All my channels" when regions are filtered), then each visible region as a heading (label + channel count) followed by its groups (cleaned names + counts). Your chosen regions come first, then the rest by size. Groups with 0 channels are hidden.
- Moving focus onto a group **selects it immediately**; there's no need to press OK.
- On first launch, the selected group is Favorites if any live favorites exist, otherwise All channels.

### 7.3 Channel list
- Each row (64 high): number, logo (or monogram), name, a ♥ if it's a favourite, "42m left", the current programme title, and a progress bar.
- **Divider rows** from the provider (e.g. `##### NEWS #####`, made of 2+ `# = * ~ • ★` at both ends) show as small headings with lines. They are never playable and are skipped when changing channel in full screen.
- Focus starts on the **last watched channel** if it's in the list, otherwise the first channel.
- **OK** plays full screen. **Hold OK** toggles the favourite with the toast "Added ABC NEWS to Favorites" or "Removed … from Favorites".
- For Favorites and Recently watched groups, a **Tiles / List** toggle appears in the header (default: list for Favorites, tiles for Recently watched; remembered per group). Other groups show the hint "Hold OK to ♥".
- Empty texts:
  - Favorites: "No favorites yet. Hold OK on any channel to add it here."
  - Recently watched: "Channels you watch will appear here."
  - Other groups: "No channels in this group."

### 7.4 Event badges
- Names like `Live | Lakers vs Suns` show a small badge (**LIVE** in accent; NEXT, SOON and NOW in grey) followed by the event title.

### 7.5 Preview pane
- Shows the clock, then a 16:9 video box with a "● LIVE" pill.
  - When a channel is focused, it **plays that channel with sound** after the focus has rested **700 ms**.
  - With nothing focused, it shows the label "LIVE PREVIEW".
- Below the video: "Now · 7:00 – 8:00 PM", the title (24 Bold, 2 lines), a progress bar, and a description (3 lines). Without guide data: "No guide information for this channel."
- At the bottom: "Next · 8:00 PM" with the next title, and the hint "OK to watch".
- Leaving the page stops the preview, **unless** full screen is taking over the same stream (handoff, so playback doesn't restart).

### 7.6 Full-screen live player
| Key | Action |
|---|---|
At live (the normal case):

| Key | Action |
|---|---|
| ▲ / CH+ | Next channel (wraps) |
| ▼ / CH− | Previous channel (wraps) |
| LAST_CHANNEL | Jump to the previous channel |
| ◀ or Back | Return to the page you came from (Live, Favorites or Recent keep the preview playing) |
| ▶, MENU, CAPTIONS | Open the Audio & subtitles panel |
| OK (short), PLAY_PAUSE, PAUSE | **Pause** (2.5; before 2.5 a short OK did nothing) |
| REWIND | Rewind into the recording (catch-up channels) |
| Hold OK | Toggle favourite |

Paused or behind live (§7.7):

| Key | Action |
|---|---|
| OK (short), PLAY_PAUSE | Play / pause (on the buttons row: press the button) |
| ◀ / ▶ | Rewind / forward 10 s on catch-up channels; holding speeds up (30 s after 6 repeats, 60 s after 20). Moving forward to within 30 s of now goes live. On the buttons row: move between buttons |
| ▼ / ▲ | Show the bar; move between the timeline and the buttons rows (they do **not** change channel here) |
| CH+ / CH− / LAST_CHANNEL | Change channel (drops the recording; the new channel starts live) |
| MENU, CAPTIONS | Audio & subtitles |
| Back | **Always leaves** (after closing the panel) |
| Hold OK | Toggle favourite |

- The info bar appears for 5 s on start and on channel change:
  - Logo, number, name, ♥, "N of M".
  - The programme title (24 Bold) with start and end times and a progress bar.
  - On catch-up channels, a small "CATCH-UP 7D" tag after the name.
  - "Next · time title" and the hint "▲▼ Channel␣␣␣·␣␣␣OK Pause␣␣␣·␣␣␣◀ Back␣␣␣·␣␣␣▶ Audio & subtitles␣␣␣·␣␣␣Hold OK ♥".
  - The clock, top right.
- A channel counts as **recently watched after 10 s**. The last channel is remembered.
- **Channel switching (2.5)**: one stream at a time, always. The account allows **1 connection**, so neighbouring channels are never preloaded.
  - The info bar updates the moment ▲/▼ is pressed. The stream starts only once no channel key has been pressed for **300 ms**, so scrolling past channels opens no streams.
  - The player starts after about 1 s of buffered video (2 s after a stall), over one kept-open connection.
  - Each switch's time, from key press to first picture, is logged, and Diagnostics shows the average of the last 10. The target is under 2 s.
- **Playback fallback**: if a stream fails, try once with the other extension (`.m3u8` ↔ `.ts`). Skip this for login or connection-limit errors (HTTP 401, 403, 429, 458, 509), where it can't help. If it still fails, show the clear error message (§7.8) in the info bar.
- **Audio & subtitles panel** (right side, 380 wide):
  - Audio tracks are listed if there is more than one. With one track: "English · Stereo (only track)".
  - Subtitles: "Off", then the tracks. With none: "This channel has no subtitles."
  - The note reads "Remembered for this channel.␣␣Back to close." (two spaces)
  - The choice is saved **per channel** by language and re-applied whenever that channel's tracks load.
  - Only the **200 most recently changed channels** are kept.
  - The last row is **Subtitle style…** with the current style summary below it. It opens the style options inside the same panel (§14.1); Back returns to the track list.

### 7.7 Pause, rewind and catch-up (2.5)
- The provider keeps recordings of **2,347 channels for up to 7 days** (Diagnostics, Oct 2026). A channel offers catch-up when its `tv_archive` is 1; `archiveDays` is `tv_archive_duration`. M3U channels never do.
- **Pause** (OK): the picture freezes, a "PAUSED 0:42" chip shows at top left, and the **control bar** appears (§12.2 style).
  - The bar's heading is "number␣␣channel␣␣␣·␣␣␣programme".
  - The timeline shows the clock time on screen (left), the progress through the current programme, and "−12:34 behind live" (right).
  - Buttons: **Play/Pause**, **Start of show** (catch-up channels, when the programme is known), **Go live**, **Audio & subtitles**.
  - The bar stays while paused and hides after 5 s while playing. The chip changes to "BEHIND LIVE 12:34" and stays visible.
- **Resume**:
  - On a catch-up channel paused for more than 15 s, playback continues from the recording at the moment of the pause.
  - Otherwise playback simply continues. On a channel without catch-up, if the stream has moved on: go live with the toast "This channel can't pause that long — back to live".
- **Rewind / forward** (catch-up channels): a quick run of presses adds up and becomes one jump 0.6 s after the last press. It seeks inside the recording if possible, otherwise it re-opens the recording at the target minute. It can't go back past the channel's catch-up days. Moving to within 30 s of now goes live.
- **Start of show** plays the recording from the current programme's start time. **Go live** returns to the live stream.
- Channels without catch-up show the hint "Pause only · no rewind␣␣␣·␣␣␣OK Play / pause␣␣␣·␣␣␣▼ Buttons" and ◀/▶ do nothing.
- **Recording link formats** (2.5.1), tried in order with the remembered working one first:
  1. `.ts` path.
  2. `.m3u8` path (`hls`).
  3. `timeshift.php`.
  - If a recording errors, try the next format. After the last one, retry once more from the same moment, then go live with the clear error as a toast.
  - The format that reaches "ready" is remembered.
- **Never just sit there** (2.5.1): if a recording, or a resumed live pause, isn't playing 10 s after starting, log "stalled" with the player state and try the next format.
  - After the last format, go live with the toast "Couldn't play the recording (still buffering) — back to live", or "Couldn't resume (…) — back to live".
- Pauses and resumes are logged ("Paused live on channel 4130 (catch-up)", "Resumed live … after 64 s", "Catch-up: timeshift .ts from 2 min ago …").
- When a recording reaches its end, request the next part (or go live).
- **Too many connections** (HTTP 429/458/509) on live or movies: wait 3 s and try once more (the provider may still be counting the stream that just closed), then show the clear message.
- Changing channel or leaving drops the recording. Recordings don't create resume points or history entries.
- **Recording link**: `{server}/timeshift/{user}/{pass}/{minutes}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts`, or `{server}/streaming/timeshift.php?username=…&password=…&stream=…&start=…&duration=…` if Diagnostics' test found that format works.
  - The start time is in the **server's local time**: the offset is measured at login from `server_info.time_now` vs `timestamp_now` (rounded to 15 min), then from `server_info.timezone`, then this TV's zone.
  - Minutes = from the start to now + 60, clamped to 30–1440.

### 7.8 Clear error messages (2.5)
Errors show the provider's actual code and what to do. The same wording is used for live, movies and recordings (`<thing>` = channel, title or recording).

| Cause | Message |
|---|---|
| HTTP 509, 429, 458 | "Provider says too many connections (509): stop playback on another device" |
| HTTP 401, 403 | "Provider refused access (403): check your subscription is active" |
| HTTP 404, 410 | "Provider says this <thing> isn't available (404)" |
| HTTP 5xx | "Provider's server had a problem (502): try again in a minute" |
| Other HTTP | "Provider returned an error (418)" |
| Connection timeout | "Provider didn't respond in time: check your internet connection" |
| Connection failed | "Couldn't connect to your provider: check your internet connection" |
| Damaged stream | "The stream from your provider is damaged (ERROR_CODE_…)" |
| Anything else | "This <thing> isn't playing right now (ERROR_CODE_…)" |

- The log line carries the details, e.g. "Live playback failed on channel 101 CNN: HTTP 509 · ERROR_CODE_IO_BAD_HTTP_STATUS".

## 8. TV Guide

- Header (150 high):
  - "GUIDE" and the day ("Wednesday, Oct 28").
  - The focused programme's title (30 Bold); "number · channel", times, and "42m left"; the description (2 lines).
  - Right side: the clock, a **Favorites** pill toggle (filled heart when on; switches between Favorites and All channels), and a **Group: <name> ▾** pill that opens the group picker.
- **Group picker**: a 420-wide panel on the left over a dimmed screen, titled "Choose a group", with "N groups · Back to close". It lists the same groups and headings as Live TV, and focus starts on the current group. Choosing a group also changes the Live TV group.
- **Grid**:
  - The window runs 150 minutes from the current half hour. The ruler shows times every 30 min. The channel column is 224 wide.
  - Programme cells are sized by duration. The cell on air now is lighter (Raised) than the others (Panel).
  - Rows without guide data are compact (38 instead of 52) and read "<channel> · no guide information". While loading: "Loading…".
  - A vertical **accent "now" line** has a time tag at the top.
- OK on a current or future cell plays that channel live, full screen. Divider rows show as small headings.
- **Past shows (2.5)**, on catch-up channels:
  - ◀ on a row's first cell moves the whole window **90 min back**, within that channel's catch-up days. On channels without catch-up, ◀ goes to the nav rail as before. ▶ on a row's last cell moves 90 min forward, back up to now. A **Now** pill appears in the header when the window is in the past.
  - Past shows come from `get_simple_data_table` (fetched for visible catch-up rows once the window is in the past; cached for 30 min).
  - Past cells that can be replayed start with "↺". Past cells outside the catch-up days, or on channels without catch-up, are dimmed; OK on them shows the toast "This show can't be replayed".
  - The header shows "Aired 7:00 – 8:00 PM" with "Replay available · OK to watch" or "Not available to replay". The day label follows the window.
  - OK on a replayable cell plays the show from its start in the **movie player** (§12): seek, pause, and Audio & subtitles. The title is the channel; the subtitle is "<show> · aired <day>, <time>". No resume point is saved. On error: "Provider says this recording isn't available (404)" and the like.
  - The "now" line is only drawn when now is inside the window.
- Empty texts:
  - Favorites: "No favorite channels yet. Hold OK on a channel to add it, or choose another group."
  - Other groups: "No channels in this group. Choose another group above."

## 9. Home

- Header: a greeting by hour ("Good morning" 5–11, "Good afternoon" 12–16, "Good evening" otherwise), "Updating playlist…" while refreshing, and the clock.
- Rows, in this order. Rows with nothing in them are hidden.
  1. **Favorites**: channel tiles (220 wide), then movie and series posters (132 wide).
  2. **Recently watched**: the last 12 items (channels, movies, series), newest first. Movie posters show resume progress.
  3. **Recently added**: the 20 newest movies (by `added`), filtered by region.
- **Channel tiles play live with sound after 1 s of focus.**
- If there's nothing to show yet: "Your channels will appear here" with "Watch something, or hold OK on any channel, movie or series to add it to Favorites." and buttons **Browse Live TV** and **Movies**.
- Initial focus is on the first item of the first row on screen.

## 10. Favorites and Recently watched pages

- Two-pane layout: the content on the left, and a **400-wide live preview pane** on the right (same as Live TV).
- Header: the title (30 Bold), a subtitle, and a **Tile view / List view** toggle (remembered per page; both pages start in list view).
  - **Favorites** subtitle: "N channels · N movies · N series · Hold OK to remove". It shows Channels (3-column tiles or list rows), then "Movies & series" as a poster row.
  - **Recently watched** subtitle: "Your last N channels, movies and series". It lists all recent items. Movies and series appear as posters (tiles) or rows labelled "Movie" or "Series" (list).
- Focus starts on the last watched channel if it's present.
- Empty texts:
  - Favorites: "Nothing here yet. Hold OK on any channel, movie or series to add it to Favorites."
  - Recently watched: "Things you watch will appear here."

## 11. Movies and Series

- Header: "Movies" or "Series" (30 Bold), "N titles", and the clock.
- **Category chips** (pills):
  - Movies: Recently added, Favorites, All, then each visible provider category.
  - Series: the same chips. The default for Series is "All"; for Movies it's "Recently added".
  - Recently added = up to 300, newest first (Movies use `added`, Series use `last_modified`).
- **Grid**: 6 columns of 2:3 posters with titles. A heart badge marks favourites. **Hold OK** toggles the favourite.
- **Continue watching** (Movies, only on the Recently added chip): the 20 most recently watched in-progress movies, as 16:9 cards with a progress bar. OK resumes playback; hold OK opens the detail page.
- Empty texts: "Loading movies… <progress>" while loading; "No favorite movies yet. Hold OK on a poster to add one."; otherwise "Nothing here yet."

### 11.1 Movie detail
- Shows:
  - "MOVIE" and the title (40 Bold, 2 lines).
  - Meta: year · genre · "1h 52m" · "★ 7.1".
  - The plot (6 lines; "Loading details…" while loading), the director, and the cast.
  - If in progress: a progress bar and "N min left".
- Buttons: **Play** / **Resume** (focused), **Start over** (only if in progress), and **Add to favorites** / **In favorites**.
- A 2:3 poster (300 wide) sits on the right.

### 11.2 Series detail
- Shows "SERIES", the title, and meta (year · "N seasons" · genre · rating), then the plot (4 lines). While loading: "Loading episodes…". On error: "Couldn't load episodes: <reason>".
- Buttons:
  - **Resume S2 · E5** if an episode was last watched, otherwise **Play S1 · E1**.
  - **Add to favorites**.
- The backdrop (16:9, 400 wide) sits on the right.
- **Season chips**: focusing a chip selects that season. It starts on the last-watched episode's season.
- **Episode cards** (266 wide, 16:9) show the image (or "E5" text), a progress bar, "E5 · 42 min", and the title. OK plays the episode.
- Playing an episode queues **all episodes of all seasons in order**, so the player can move to the next one automatically.

## 12. Movie and episode player (2.3)

The player screen always holds focus and handles every key itself. The stock ExoPlayer controller **must not** be used: it loses focus when it hides, and the remote stops responding.

### 12.1 Keys
| State | Key | Action |
|---|---|---|
| Bar hidden | OK | Play/pause, and show the bar |
| Bar hidden | ◀ / ▶ | Back / forward 10 s and show the bar (timeline row) |
| Bar hidden | ▲ / ▼ | Show the bar |
| Bar shown, timeline row | ◀ / ▶ | Seek ∓10 s |
| Bar shown, timeline row | ▼ | Move to the buttons row |
| Bar shown, buttons row | ◀ / ▶ | Move between buttons (no wrap) |
| Bar shown, buttons row | OK | Press the button |
| Bar shown, buttons row | ▲ | Back to the timeline |
| Any | Holding ◀ / ▶ | Faster: 10 s steps, then 30 s after 6 repeats, then 60 s after 20 repeats |
| Any | PLAY_PAUSE / PLAY / PAUSE | As named |
| Any | REWIND / FAST_FORWARD | ∓10 s (accelerating) |
| Any | MEDIA_NEXT | Next episode |
| Any | MENU / CAPTIONS | Open Audio & subtitles |
| Any | Back | Close the panel, else hide the bar, else leave (saving progress) |

- Seeks in a quick run add up from the target, not the current position. The run resets after 1.2 s. Seeks never go past `duration − 1 s`.

### 12.2 Bar
- Bottom card:
  - **Timeline row**: elapsed "1:02:05", a progress bar (6 high and white when that row is active; 4 high and muted otherwise), and remaining time "−48:10".
  - **Buttons row**: Pause/Play, Back 10s, Forward 10s, Audio & subtitles, Next episode (only if there's a next one), Start over. The current button is filled light with dark text; the others are dark pills.
  - A hint on the right: "◀ ▶ 10 s (hold = faster)␣␣␣·␣␣␣OK Play / pause␣␣␣·␣␣␣▼ Buttons", or "◀ ▶ Choose␣␣␣·␣␣␣OK Select␣␣␣·␣␣␣▲ Timeline" on the buttons row.
- The title and episode line sit top left, and the clock top right, whenever the bar or panel is showing.
- The bar shows on start and hides after **5 s** without a key. It **stays while paused**.

### 12.3 Behaviour
- Resume from the saved position, or start from 0 for Start over.
- Progress saves every 10 s and on exit.
  - Under 30 s it isn't saved.
  - Past 95%, or with under 60 s left, the resume point is cleared (counted as finished).
- When a title ends: mark it finished, then play the next item in the queue, or leave if there is none.
- On a playback error, show the clear message (§7.8) and "Press Back to return."
- Opening a movie adds it to Recently watched. Opening an episode records it as the series' last episode and adds the series to Recently watched.
- **Audio & subtitles panel**: as in live, but the note reads "Used for all movies & series.␣␣Back to close." (two spaces) and the empty text is "This title has no subtitles."
  - Choices are saved as **one general setting for all movies and series** (`vod_audio_lang`, `vod_text_lang`), applied **once per title** when its tracks first load.
  - Changes made during a title stay in effect for that title.

## 13. Search

- A field: "Channels, movies or series", with a search icon. Focusing it and pressing OK opens the keyboard. Results update as you type, from 2 characters.
- Three rows: **Channels** (cards 260 wide with logo and name), **Movies**, **Series** (posters 140 wide). Each row shows up to 40 results.
- Matching is a case-insensitive substring of the raw name. Results respect regions; favourite channels are always included.
- Hints: "Press OK to type. Results appear as you type." and "No matches for "xyz"."

## 14. Settings

- Left column (520 wide, scrollable), one row each (title + subtitle):
  1. **Update available — install now** (only when one exists; highlighted; focused first). The subtitle reads "<release name>. Downloads and opens Android's installer." While downloading, the title becomes "Downloading · 12.3 MB".
  2. **Change login**: "Scan a QR code and enter a new login on your phone".
  3. **My regions**: lists the chosen region labels, or "Showing all regions".
  3a. **Subtitle style**: the subtitle shows the current style, e.g. "Medium · White · Drop shadow · Semi-transparent box". It opens §14.1.
  4. **Update playlist now**: "Updated 3 h ago · updates automatically once a day".
  5. **Clear watch history**: "Removes resume points for movies and episodes". This also clears saved audio/subtitle choices. Toast: "Watch history cleared".
  6. **Check for updates**: "Looks for a newer version on GitHub".
  7. **Diagnostics**: "Activity log and last error, viewable on your phone".
  8. **About Sparky IPTV**: "Who made it, and what's new in each version".
- Right column: **PLAYLIST** (Name, Type, Server, Username, Status, Expires, Connections, and Library counts), then **APP** (Version "Sparky IPTV 2.3 (build N)").
- The nav rail's Settings icon shows a small accent dot when an update is available.

### 14.1 Subtitle style (2.4)
- One style for **all** subtitles: movies, series and live TV. It's saved on the TV and not cleared by Clear watch history.
- Five options. ◀ / ▶ change the value (no wrap); OK steps to the next value (wraps):

  | Option | Values | Default |
  |---|---|---|
  | Size | Small (0.8×) · Medium (1×) · Large (1.25×) · Extra large (1.5×) of the player's standard size | Medium |
  | Text colour | White `#FFFFFF` · Yellow `#FFE14D` · Cyan `#7FE3F0` | White |
  | Background | None · Semi-transparent box (black at 50%) · Solid black box | Semi-transparent box |
  | Edge | Drop shadow · Outline · None (black edge colour) | Drop shadow |
  | Position | Normal (standard bottom gap) · Raised (bottom gap 20% of the picture height) | Normal |

- **Reset to default** restores the defaults with the toast "Subtitle style reset".
- The style overrides any colours, fonts or sizes embedded in the subtitle stream.
- **Settings screen**:
  - Title "Subtitle style" with "Used for movies, series and live TV. ◀ ▶ to change, OK to step through."
  - The options on the left.
  - On the right, "PREVIEW": a sample subtitle ("This is how subtitles will look, / on bright and dark scenes alike.") drawn by the player's own subtitle view over a bright sky-like gradient, with "Shown over a bright scene, the hardest case for reading subtitles."
- **In the player**: Audio & subtitles → Subtitle style… shows the same options, titled "Subtitle style" with the note "◀ ▶ to change, shown live on the video.␣␣Back to return." Changes apply to the playing video at once.

## 15. App updates

- On each open, the app checks `api.github.com/repos/ashwin-mathias/couch-lab/releases/latest`. The tag `build-N` is compared with the installed `versionCode`; the asset used is the first one ending in `.apk`.
  - (2.5.2) Only assets whose link starts with `https://github.com/ashwin-mathias/couch-lab/releases/download/` count. A release is offered only if it also has `sparky-iptv.apk.sha256` (the SHA-256 as 64 hex characters).
  - The download must stay on https through redirects, and its SHA-256 must match. If not, the file is deleted and the toast says "Update failed: The update failed its integrity check, so it was not installed".
- If newer: the toast "Update available — open Settings to install" (once per session).
- **Install**:
  - If "install unknown apps" isn't allowed, open that system screen with the toast "Allow Sparky IPTV to install updates, then press Back".
  - Otherwise, download to `cache/updates/sparky-iptv.apk` and launch Android's installer through a FileProvider.
  - Extra presses are ignored while downloading and for **10 s** after the installer opens (2.5; fixes a second download).
- A manual check shows "Checking for updates…", then "You have the latest version" or the update toast.

## 16. Diagnostics

- Left side: the title; "Scan to open the full log on your phone, then copy it and send it to Claude."; a QR code (the log server on ports 8091–8099, only while the page is open); the URL; and a header:
  - The version ("Sparky IPTV 2.5 (build 15)"; the build appears once, fixed in 2.5), the device, memory used/max, and library counts.
  - **Catch-up**: "N channels · up to N days", or "not offered by your provider", or "checking at the next playlist update".
  - "Channel switch: average 1.4 s (last 10)", once channels have been switched.
  - The catch-up test result, once run.
  - Playlist type and live format.
- Buttons: **Test catch-up** (2.5), then Refresh and Clear crash (only if there is one).
  - Test catch-up asks the provider for a recording from about an hour ago on up to 3 catch-up channels (favourites first), trying **all three link formats** on each (2.5.1).
  - "Video" means any of: MPEG-TS packets (sync byte 0x47 every 188 or 192 bytes, starting anywhere in the first 2,000 bytes), `#EXTM3U` (HLS), or `ftyp` at byte 4 (MP4).
  - For each format, the log records the HTTP code, content type, size and first 16 bytes in hex.
  - The result reads like "Catch-up works on channel 4120 · using timeshift .m3u8 · server time UTC+0:00 · timeshift .ts: MPEG-TS · timeshift .m3u8: HLS · timeshift.php: HTTP 404", or "Catch-up didn't work · … · channel X: <per-format reasons>". An empty answer reads "no recording for that time (empty)".
  - The preferred format is HLS, then .ts, then php.
- Right side: "Last crash" (if any, in red) and the **last 200 log lines, newest first**. Errors are accent, warnings amber. The lines are focusable so the remote can scroll them.
- The phone page has a **Copy everything** button. On plain http it falls back to a hidden text box and the copy command; if that fails it selects all the text ("Text selected: tap Copy"). There is also a **Select all** button (2.5.1; before that Copy did nothing on phones).
- The log is kept in memory and in a file (400 lines, trimmed past 256 KB). Crashes are written to `last_crash.txt` by an uncaught-exception handler. **The password and username are replaced with •••** in every log line.
- **Cancelled loads are not logged as errors** (2.5): coroutine cancellations and cancelled network calls (for example, a detail page closed before its data arrived) are dropped from the log.
- The first daily refresh logs "Daily refresh: first update" (before 2.5 it printed the hours since 1970).

## 17. About

- Left side:
  - "ABOUT", then the author row: the avatar (64, circle), **Ashwin Mathias** (22 Bold) and "Creator of Sparky IPTV" (from `assets/about.json`).
  - **Sparky card** (2.5; replaces LinkedIn, which was removed):
    - Sparky's photo (96, circle; a yellow Labrador against a blue sky).
    - "Designed with love for **Sparky**, who sits on the couch while we watch TV."
    - "Buy Sparky a treat", then two QR codes side by side: **Venmo** "@AshwinMathias" and **Zelle** "Scan with your bank app".
    - The codes are generated in the app from links in `about.json` (`venmo`, `zelle`), not screenshots. That leaves out the bank-app chrome, the account digits and the face photo. Ashwin accepted that they're public (Oct 2026).
  - Version, Device and Network.
  - "Your login is stored only on this TV and is only sent to your provider."
- Right side: **Release history** from `assets/changelog.json`, newest first. Each version is a focusable card: "Version 2.3", the date, a **LATEST** tag on the first, the title, and bullet notes. Below that, **Open-source credits**.
- **Every release must add a changelog entry and bump `appVersion`.**

## 18. Favourites, recents and history (rules)

- Favourite keys: `live:<stream_id>`, `movie:<id>`, `series:<id>`, stored as one set.
- Recents: the last **12** items `{type, id, ts}`, deduplicated, newest first. Channels are added after 10 s of watching; movies and series when played.
- Resume points: `pos:movie:<id>` and `pos:episode:<id>` = "pos,dur,timestamp".
- Clear watch history removes resume points, the last episode per series, and all saved audio/subtitle choices. **It does not remove** favourites or recents.

## 19. Lifecycle

- When the app goes to the background, the player pauses. When it returns, playback resumes if a player or Home was showing, and the daily refresh check runs on Home.
- The activity is landscape and `singleTask`. Config changes don't restart it.

## 20. Out of scope (decided, not built)

- **Failover** (retry, then the other format, then a backup copy of the same channel): planned for **2.6**.
- **Preloading neighbouring channels**: never. The account allows 1 connection.
- Google TV home-screen "Continue watching" integration (discussed and deferred).
- Multiple playlists or profiles, parental controls, recording, external EPG (XMLTV), and multi-view.
- An iOS version (discussed; open questions remain).
