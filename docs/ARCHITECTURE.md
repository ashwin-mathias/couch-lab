# Sparky IPTV — Architecture

How the app is built: the stack, the code layout, the data, storage, and the release pipeline. Every exact value here matches the 2.3 code.

---

## 1. Stack (pinned versions; don't "upgrade while rebuilding")

| Piece | Version |
|---|---|
| Android Gradle Plugin | 8.5.2 |
| Gradle wrapper | 8.9 |
| Kotlin (+ compose compiler plugin) | 2.0.20 |
| JDK | 17 (source/target 17) |
| compileSdk / targetSdk / minSdk | 34 / 34 / 23 |
| Compose BOM | 2024.09.00 (`ui`, `foundation`) |
| `androidx.tv:tv-material` | 1.0.0 |
| core-ktx / activity-compose / lifecycle | 1.13.1 / 1.9.2 / 2.8.6 |
| Media3 (`exoplayer`, `exoplayer-hls`, `ui`, `datasource-okhttp` since 2.5) | 1.4.1 |
| OkHttp | 4.12.0 |
| Coil compose | 2.7.0 |
| NanoHTTPD | 2.3.1 |
| ZXing core | 3.5.3 |
| kotlinx-coroutines-android | 1.8.1 |

- Compiler flags: `-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api` and `-opt-in=androidx.compose.foundation.ExperimentalFoundationApi`.
- **R8 since 2.8**: `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt` + `app/proguard-rules.pro`. **`-dontobfuscate`**: names stay readable for crash reports (AppLog finds the app's lines by "com.sparky") and the activity log. The libraries ship their own keep rules; NanoHTTPD and OkHttp's optional TLS providers only need `-dontwarn`. `DebugProbesKt.bin` and `kotlin-tooling-metadata.json` are excluded. Lint does not fail the build. `gradle.properties` sets `-Xmx3g`, AndroidX and non-transitive R.
- No Jetpack Navigation, Room or Hilt. The app keeps its own small screen stack, SharedPreferences, and a hand-written binary cache.

## 2. Manifest

- Permissions: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `REQUEST_INSTALL_PACKAGES`.
- `uses-feature`: `leanback` and `touchscreen`, both `required=false`.
- Application:
  - `largeHeap=true`, `allowBackup=false` (2.5.2; the saved login must not leave the device), `usesCleartextTraffic=true` (providers use http), `banner=@drawable/banner`, `icon=@mipmap/ic_launcher`, `label="Sparky IPTV"`, theme `Theme.Sparky`.
- MainActivity:
  - `exported`, `singleTask`, `landscape`.
  - `configChanges` covers keyboard, keyboardHidden, navigation, orientation, screenLayout, screenSize, smallestScreenSize and uiMode.
  - `windowSoftInputMode=adjustNothing`.
  - Intent filter: `MAIN` + `LAUNCHER` + **`LEANBACK_LAUNCHER`**.
- FileProvider: authority `${applicationId}.files`, path `cache-path updates/`.

## 3. Code layout (`com.sparky.iptv`)

```
MainActivity.kt          AppLog.init; setContent { App(vm) }; onStart resume + daily-refresh check; onStop pause
data/
  Models.kt              Playlist, Category, StreamUrls, Channel, Movie, Series, Episode, SeriesInfo, MovieInfo,
                         EpgEntry, AccountInfo, Library, RecentItem, Progress
  Names.kt               region prefix parsing, strip, divider rows, event badges, region labels
  Prefs.kt               all SharedPreferences ("sparky")
  Http.kt                (2.8) one OkHttp base client; Repository and PlayerHolder derive theirs with newBuilder()
                         (shared connection pool and threads)
  Favorites.kt           (2.7) FavFolder + Favs: order/folder JSON, normalize, merge (reorder a filtered view), moveBy, name checks
  Backup.kt              (2.7) backup file format v1: create, read (checks + decrypt), apply (one commit), PBKDF2 + AES-GCM
  SecretBox.kt           Keystore AES-GCM for the saved login; one-time tokens for the phone pages
  Xtream.kt              XtreamClient (player_api.php), URL normalising, getText, JSONObject.s(), Parsers (detail/EPG JSON)
  StreamParsers.kt       streaming JsonReader parsers for the big lists (categories, live, movies, series)
  Snapshot.kt            compact binary cache (snap_live/movies/series.bin)
  Repository.kt          OkHttp client, load steps, cached load (live → vod), refresh, download-to-disk, validate,
                         short EPG, movie/series info; M3U parser
  AppLog.kt              redacting activity log + crash file; drops cancelled loads (isCancellation)
  Updater.kt             GitHub latest-release check, install permission, FileProvider install
player/
  PlayerHolder.kt        ONE shared ExoPlayer for previews and full screen
  TrackHelper.kt         list/select audio and subtitle tracks, reset, re-apply remembered language
  PlaybackErrors.kt      HTTP code from a player error → clear message (SPEC §7.8), log detail, account-problem check
  SubtitleStyle.kt       SubStyle (options, encode/decode, apply to a SubtitleView) + SubtitleStyleState (shared Compose state)
setup/
  SetupServer.kt         setup pages (login form, restore) on a PhoneServer (8080–8090); Net.localIp; Qr.bitmap
  SetupPage.kt           phone HTML (form/done)
  LogServer.kt           diagnostics page on a PhoneServer (8091–8099)
  PhoneServer.kt         (2.7; shared by every phone page since 2.8) NanoHTTPD: key check (401), 2 MB body limit (413),
                         expiry fixed or sliding (403), lock, body/form/download, port ranges
  PhonePages.kt          (2.7) phone HTML: backup, restore, favorites folders editor
ui/
  App.kt                 theme, screen switch, Home shell, NavRail, toast
  UiModels.kt            (2.8, moved from AppViewModel.kt) Screen, PlayRequest, VodItem, GroupItem, LiveIndex, MoveSession,
                         FavMenuRequest and the group / list constants
  PhoneFeatures.kt       (2.8, moved from AppViewModel) backup, restore and favorites-folder phone pages; restore logic
                         shared with setup; reconcileRestore
  AppViewModel.kt        all state + actions (see §5)
  components/Theme.kt    tokens C, Inter, units u/su, T, Label, FocusItem, FocusCard, Icon, ProgressBar, Clock, formatters, monogram
  components/Pieces.kt   ChannelLogo, Poster, PlayerSurface, Toast
  components/Tiles.kt    Badge, ChannelTile, PosterCard
  screens/               HomeSection, LiveScreen, Preview (PreviewDriver, ChannelPreviewPane), CollectionSections,
                         GuideScreen, VodScreens (Movies, Series, MovieDetail, PosterTile), SeriesDetailScreen,
                         SearchSettings, RegionsScreen, LoadingScreen, SetupScreens (+PillButton), DiagnosticsScreen,
                         AboutScreen, PlayerScreen (LivePlayer, VodPlayer, TrackPanel),
                         SubtitleStyleScreen (screen, SubtitleStyleOptions shared with TrackPanel, preview),
                         FavoritesUi (2.7: favourite menu, folder checklist, Move helpers, Favorite folders screen,
                         Back up / Restore screen)
assets/  changelog.json, about.json
res/     drawable/*.xml icons, drawable-nodpi/{logo,avatar}.webp + sparky.jpg, drawable-xhdpi/banner.webp, mipmap-*/ic_launcher.webp,
         font/inter_*.otf (trimmed, see §1), values/{strings,themes}.xml, xml/file_paths.xml
(no keystore in the repo)  the signing key is in GitHub Actions secrets (see §9) — it must be reused so updates install
```

**Name clash rule:** the theme object is `C`, and Media3 also has `androidx.media3.common.C`. In UI files that need both, import Media3's as `import androidx.media3.common.C as MediaC`. This clash broke the 2.2 build.

## 4. Data

### 4.1 Xtream API (all `GET {base}/player_api.php?username=…&password=…`)
| Action | Used for |
|---|---|
| *(none)* | Login. `user_info.auth`, `status`, `exp_date` (seconds), `max_connections`, `allowed_output_formats` |
| `get_live_categories` / `get_live_streams` | Live groups and channels (`stream_id`, `num`, `name`, `stream_icon`, `category_id`, `epg_channel_id`, `tv_archive`, `tv_archive_duration`) |
| `get_vod_categories` / `get_vod_streams` | Movies (`stream_id`, `name`, `stream_icon`, `category_id`, `rating`, `added`, `container_extension`) |
| `get_series_categories` / `get_series` | Series (`series_id`, `name`, `cover`, `category_id`, `plot`, `releaseDate`/`release_date`/`year`, `rating`, `genre`, `last_modified`) |
| `get_short_epg&stream_id=X&limit=12` | Now/next. `epg_listings[]` with `start_timestamp`, `stop_timestamp`; **title and description are Base64** |
| `get_simple_data_table&stream_id=X` | Full listing including **past** shows (same fields plus `has_archive`). Used for the guide's past shows on catch-up channels (2.5) |
| Login `server_info` | `time_now` (server local "yyyy-MM-dd HH:mm:ss"), `timestamp_now` (epoch s) → UTC offset in minutes (rounded to 15); `timezone` name as the fallback |
| `get_vod_info&vod_id=X` | Movie detail (`info.plot|description`, `releasedate`, `genre`, `duration_secs|duration "hh:mm:ss"`, `backdrop_path[0]`, `cast|actors`, `director`) |
| `get_series_info&series_id=X` | Seasons and episodes. `episodes` may be an **object keyed by season** or an **array (of arrays)**. Episode: `id`, `season`, `episode_num`, `title`, `container_extension`, `info.{duration_secs, duration, plot, movie_image}` |

- Stream URLs are built on demand and **not stored per item** (this saves memory):
  - live `{base}/live/{user}/{pass}/{id}.{m3u8|ts}`
  - movie `{base}/movie/{user}/{pass}/{id}.{ext}`
  - episode `{base}/series/{user}/{pass}/{id}.{ext}`
- User-Agent everywhere: `SparkyIPTV/1.0 (Linux; Android TV)`.
- OkHttp timeouts: connect 20 s, read 120 s, follow redirects, retry on failure.
- Number-or-string fields: always read through a helper that accepts numbers, strings, empty values and nulls (`JSONObject.s`, `JsonReader.value()`). `num` can be "12.0".
- Ratings of "0" count as missing.

### 4.2 Big lists, memory and speed (the reasons the app doesn't crash)
1. **Download straight to a temp file** (64 KB buffer, progress every 256 KB). Never `body.string()` for list endpoints.
2. **Parse with `android.util.JsonReader`** (lenient), one object at a time, keeping only the fields listed above. Category IDs, extensions, ratings and genres are `intern()`ed.
3. **Write a compact binary snapshot** after each section succeeds, then delete the temp file and any old JSON cache.
4. On launch, **read the live snapshot only**, open Home, then read movies and series in the background.
5. `largeHeap=true`; catch `OutOfMemoryError` per section and show a friendly message.

### 4.3 Snapshot format (`filesDir/snap_{live,movies,series}.bin`)
- `DataOutputStream`, buffered at 256 KB, written to `.tmp` and then renamed (atomic).
- Header: `int MAGIC = 0x5350524B ("SPRK")`, `int VERSION = 2`. Readers accept versions 1..2.
- A string is `int len` (−1 = null) followed by UTF-8 bytes.
- Body: categories (`int n`, then id and name), then items:
  - Live: id, `int num`, name, logo, categoryId, epgId, directUrl, `int archiveDays` (v2+ only).
  - Movies: id, name, poster, categoryId, rating, `long added`, ext.
  - Series: id, name, cover, categoryId, plot, year, rating, genre, `long lastModified`.
- Progress is reported every 20,000 items.

### 4.4 M3U
- Parse `#EXTINF` attributes (`tvg-id`, `tvg-name`, `tvg-logo`, `tvg-chno`, `group-title`); the name follows the last comma.
- Category id is `m3u:<group>` ("Uncategorized" if none). Channel id is a running number; the URL is stored as `directUrl`.
- Saved raw as `cache_m3u.json`. There are no movies or series.

### 4.5 Derived index (`LiveIndex`, rebuilt whenever the library changes)
- category → region code; category → cleaned name; VOD/series category → region; channel count per category.
- Regions sorted by channel count.

## 5. State (`AppViewModel`, an AndroidViewModel)

- Navigation:
  - `stack: SnapshotStateList<Screen>`; `current = stack.last`; `push`, `pop` (never empties the stack), `resetTo`.
  - `section: Section` for the Home shell.
- Library: `library`, `index`, `loadSteps`, `loadFraction`, `loadError`, `refreshing`, `vodLoading`, `vodProgress`.
- User data (Compose state mirrored to Prefs): `favorites`, `recents`, `myRegions`, `showAllRegions` (session only), `progressVersion` (bumped to refresh resume UI), and tile modes.
- Selections: `liveGroup`, `movieCat`, `seriesCat`, `searchQuery`.
- EPG:
  - `epg: SnapshotStateMap<channelId, List<EpgEntry>>`, fetched lazily per visible row by `ensureEpg`, at most **4 at a time** (Semaphore).
  - Re-fetch when: never fetched; empty and older than 10 min; or the data ends within the next hour and it was fetched more than 5 min ago. Only for Xtream.
- `liveHandoff: Boolean` is set just before opening full screen so the preview's dispose doesn't stop the shared player.
- **Sleep timer (2.6)**: `sleepMinutes` (0 = off), `sleepEndsAt` (`SystemClock.elapsedRealtime`), and a `viewModelScope` job. `setSleep(m)` starts, restarts or cancels it. In memory only, so closing the app clears it.
  - The job wakes 1 min before the end for the warning toast (`SLEEP_WARNING`), then each second; at the end `sleepFinished()` calls `PlayerHolder.release()`, sets `PlayerHolder.held`, pops a `Screen.Player`, toasts and logs.
  - `MainActivity.dispatchKeyEvent` passes every key to `vm.onAnyKey` first. Any key clears `PlayerHolder.held`. In the last minute a key restarts the timer and is swallowed (down and up), so it doesn't also pause or change channel.
- **Favorites (2.7)**: `favorites` (set), `favOrder` (all keys, your order), `folders`. `keysFor(listId)` returns a list's keys in display order (`all:live`, `all:movie`, `all:series`, `folder:<id>`, `folders`), or the moving order while something moves. Until `fav_order` is first saved, lists keep the provider's order (`favOrderSaved`); `onLibraryComplete()` saves it once channels, movies and series are loaded, then runs `reconcileRestore()`.
  - `holdFavorite(key, label, moveList, upDown, leftRight)`: adds, or stores a pending `FavMenuRequest`; `onAnyKey` opens it (`favMenu`) on the OK **release**, after the item has seen the release.
  - `moving: MoveSession?`: while set, `onAnyKey` swallows every key: arrows call `moveStep`, OK up `commitMove` (merges the visible order into the full list with `Favs.merge`), Back up `cancelMove`. Lists key items by favourite key, so the focused item keeps focus as it moves; `FollowMove` keeps it on screen.
- **Phone pages (2.7)**: `startPhonePage(BACKUP | RESTORE | FAVORITES)` / `stopPhonePage()`, `phoneUrl`. Requests run on the server thread; anything touching state uses `runBlocking(Dispatchers.Main)`, logins are checked on IO. `restoreRequest` is shared with the setup server (first-run restore); a backup without a login waits in `pendingRestore` until the login is sent.
- **`regionsClosed`** (2.7): the region headings closed in the group lists.
- **`init {}` must be the last block in the class.** It calls `load()`, which reads properties declared above. Putting it first caused a null crash in 2.1 development.

- **Performance rules (2.8)**:
  - `Prefs.playlist` keeps the decrypted login in memory after the first read (cleared on set), and `SecretBox` keeps the Keystore key. Before, every channel row and guide cell decrypted it through the Keystore on the UI thread. Use `prefs.isXtream` for type checks.
  - `LiveIndex.byCat` (built lazily) gives a group's channels without scanning the library; "All my channels" is filtered once per library and region choice; `channelKeys()` keeps the lazy-list keys of the last two big lists (over 2,000 channels).
  - Recently watched and favorites look items up in the id maps (`channelForKey`, …), never with `firstOrNull` over the library.
  - Search runs on `Dispatchers.Default` after a 250 ms pause.
- Since 2.8 `AppViewModel.kt` holds only the ViewModel; the types live in `UiModels.kt` and the phone pages in `PhoneFeatures.kt`. Members `PhoneFeatures` needs are `internal` (setLib, resetTo, saveFavs, lookup, present, reloadFromPrefs, clearEpg).

## 6. Storage (`SharedPreferences "sparky"`)

| Key | Value |
|---|---|
| `playlist_enc` | (2.5.2) `"v1:"` + Base64(12-byte IV + AES-256-GCM ciphertext) of the JSON `{type,name,url,user,pass}`. Key `sparky_prefs_key` is in the Android Keystore (`SecretBox`) |
| `playlist` | Old plain copy of the same JSON. Upgraded to `playlist_enc` on first read; written only if the Keystore is unavailable (logged) |
| `account` | JSON `{status,expires,max,format,tz,off}` (`tz` and `off` = server time zone and UTC offset in minutes; since 2.5) |
| `catchup_format` | `path` (.ts), `hls` (.m3u8) or `php`: the recording link format that last worked (from the test or from playback; default `path`) |
| `live_format` | `m3u8` or `ts` |
| `favorites` | string set of `live:/movie:/series:` keys |
| `pos:<movie:id|episode:id>` | `"pos,dur,ts"` |
| `slast:<seriesId>` | last episode id |
| `recents` | JSON array `[{type,id,ts}]` (max 12) |
| `my_regions` | string set |
| `regions_prompted` | bool |
| `tiles:<key>` | bool (keys: `__fav`, `__recent`, `page_favorites`, `page_recent`) |
| `trk:<live:id>` | `"audioLang|textLang"` (`textLang` "off" = off) |
| `trk_order` | JSON array, most recent first, **max 200**; older `trk:` entries are removed |
| `vod_audio_lang`, `vod_text_lang` | the general movie/series track setting |
| `sub_style` | `"size,color,background,edge,position"` indices (default `1,0,1,0,0`); invalid values fall back per field |
| `last_refresh` | epoch ms |
| `catchup_scanned` | bool |
| `last_channel` | channel id |
| `sleep_default` | (2.6) int minutes, 30, 60 or 90 (default 90): the sleep timer length focused first |
| `fav_order` | (2.7) JSON array of all favourite keys in your order. Missing = before 2.7 (set from the provider's order once the library is loaded) |
| `fav_folders` | (2.7) JSON `[{id, name, keys:[live:…]}]`; array order = folder order, `keys` order = order inside. Ids `f1`, `f2`… are local and stable |
| `regions_closed` | (2.7) string set of region codes whose heading is closed (default empty = all open) |
| `restore_pending` | (2.7) JSON `{names:{key:name}, total}` after a restore, until favourites are matched by name against the full library; then removed |

### 6.1 Backup file (2.7, format `sparky-backup` v1)
```
{ "format": "sparky-backup", "version": 1, "app": "2.7 (build N)", "created": "2026-11-02T20:15:00-08:00",
  "device": "TCL Google TV", "encrypted": true, "data": <object, or Base64 when encrypted> }
data: login {type,name,url,user,pass}              (only when Login is ticked; then the file is encrypted)
      favorites [{key,name}], fav_order, fav_folders, my_regions, regions_closed
      playback {sub_style, sleep_default, vod_audio_lang, vod_text_lang, live_format, catchup_format},
      tracks {trk:…}, trk_order, tiles {tiles:…}
      history {pos {pos:…}, slast {slast:…}, recents "<json>"}
```
- **Limits on restore (2.8)**: `Backup.MAX_*` (5,000 favorites, 100 folders, 2,000 resume points and last episodes, 50 tile keys; track choices capped at `Prefs.MAX_TRACK_CHOICES` = 200 using the file's `trk_order`). New backups need a passphrase of `Backup.MIN_PASSPHRASE` = 10 characters; older files with shorter ones still restore.
- Encryption: `Base64(salt 16 + IV 12 + AES-256-GCM ciphertext)`, key = PBKDF2-HMAC-SHA256(passphrase, salt, **210,000** rounds, 32 bytes). PBKDF2 is written out with `HmacSHA256` because `PBKDF2WithHmacSHA256` needs Android 8 (minSdk is 23). About 1 s on the TV.
- Never in the file: account status, caches, logs, `last_refresh`, `catchup_scanned`, `regions_prompted`, `last_channel`, tokens.
- Apply: everything is checked first, then written in one `commit()`. **Add** merges favourites (union; your order first, then the file's), folders by name (case-insensitive), resume points (newer timestamp wins), last episodes (only missing ones) and recents (by time, top 12); settings are replaced. **Replace** removes the included keys first.
- Restoring a different login: checked with `validate`, then saved, saved data cleared and Loading restarted (1.5 s later, so the phone gets its answer first).

Files in `filesDir`: `snap_*.bin`, `cache_m3u.json`, `app_log.txt`, `last_crash.txt`, `seen_crash.txt`. Cache: `cacheDir/dl_*.json` (temp) and `cacheDir/updates/sparky-iptv.apk`.

## 7. Playback

- `PlayerHolder`: a **single ExoPlayer** created on first use (2.5):
  - `OkHttpDataSource` over **one shared OkHttpClient** (our UA, redirects, connect 15 s, read 20 s), so the connection stays open between channel switches.
  - `DefaultLoadControl` with `bufferForPlaybackMs = 1000` and `bufferForPlaybackAfterRebuffer = 2000` (the defaults are 2500 and 5000); min and max buffer stay at the defaults.
  - `DefaultMediaSourceFactory`.
- **Never more than one stream.** The account allows 1 connection; preview, full screen and catch-up all reuse the single player.
- **Live player state (2.5)**: `shiftStart` (the recording's start, or null for live), `paused`, `pausedAt`, plus a virtual-focus control bar.
  - `StreamUrls.catchup(id, startMs, minutes)` builds recording links in the server's local time.
  - A sub-minute start is applied by `seekTo` once ready, if the stream is seekable.
  - Seeks are debounced by 600 ms, then either `seekTo` inside the recording or a new recording link.
  - The stream starts 300 ms after the last channel key press. `onRenderedFirstFrame` is used to time switches (`vm.recordSwitch`).
- `VodItem.saveProgress = false` marks recordings played from the guide; they don't store resume points.
  - `play(url, startMs, force)`: if the URL is unchanged and the player isn't idle, just resume. Otherwise call `TrackHelper.reset` (clear overrides, subtitles off), set the item (MIME `APPLICATION_M3U8` for `.m3u8`), prepare and play.
  - `stop` clears items; `pause`/`resume`; `currentPosition`; `duration` (0 if unknown).
  - `release` (2.6, sleep timer): stops and releases the ExoPlayer, evicts the OkHttp connection pool so the provider connection closes, and bumps `generation`. The next `get` builds a fresh player.
  - `held` (2.6): while true, `play` does nothing, so previews don't restart after the sleep timer. Cleared by any key.
  - `streamState()` (2.6): "none" or "open (playing/paused/buffering)", shown in Diagnostics.
- `PlayerSurface`: an `AndroidView(PlayerView)` with `useController = false`, **not focusable** (`FOCUS_BLOCK_DESCENDANTS`), buffering spinner shown while playing, fit mode, black shutter, `keepScreenOn`.
  - **Never enable the stock controller.** All keys go to our Compose box.
  - Its `update` block reads `PlayerHolder.generation` and re-attaches the view to the current player (2.6, after a release), then calls `SubStyle.apply(subtitleView, SubtitleStyleState.style)`. Because `update` reads that state, every surface re-styles as soon as the style changes. `apply` turns off embedded styles and font sizes, sets a `CaptionStyleCompat` (text colour, background, transparent window, edge type, black edge), sets the fractional text size to `DEFAULT_TEXT_SIZE_FRACTION × scale`, and sets the bottom padding fraction (0.2 when Raised, otherwise the default).
  - `AppViewModel.init` loads the style from Prefs into `SubtitleStyleState`; `setSubStyle` updates both.
- Previews and full screen share the player. `PreviewDriver` plays after 700 ms of focus and stops on dispose unless `liveHandoff`.
- Track selection: `TrackSelectionOverride(group.mediaTrackGroup, index)` with `setTrackTypeDisabled(type, false)`. Turning subtitles off = `clearOverridesOfType(TEXT)` + `setTrackTypeDisabled(TEXT, true)`. Labels are built from the language display name, the format label, 5.1/Stereo/Mono, and CC.

### Focus and key handling pattern (players)
```kotlin
Box(Modifier.fillMaxSize().focusRequester(focus).focusable().onPreviewKeyEvent { ev ->
    if (panelOpen) return@onPreviewKeyEvent false   // let the panel's own items handle keys
    // handle KeyDown by nativeKeyEvent.keyCode; repeatCount drives acceleration; return true when consumed
})
LaunchedEffect(panelOpen) { if (!panelOpen) { delay(80); focus.requestFocus() } }
BackHandler { exit() }                                   // registered first = lowest priority
BackHandler(enabled = barVisible && !panelOpen) { barVisible = false }
BackHandler(enabled = panelOpen) { panelOpen = false }   // registered last = highest priority
```
- The bar buttons are **virtual focus** (an index in state), not Compose focus targets. Focus therefore can't escape to an invisible view.
- The sleep panel (2.6) works like the Audio & subtitles panel: `sleepOpen` makes the box ignore keys, has its own BackHandler, and focus returns to the box when it closes.

### Focus pattern (lists with headings, 2.6)
Lists that mix focusable rows with non-focusable headings (guide grid, Live TV channel list) **don't use Compose's default ▲/▼ focus search**. In 2.7 the region headings in both group lists became focusable rows (they open and close), and the group lists still move with `ListNav.step`. `ui/components/ListNav.kt`:
- `stableKeys(items) { it.id }`: lazy keys are the item id (repeats get `#2`, `#3`), never `"$index:$id"`, so a row keeps focus when the list recomposes.
- Each row handles ▲/▼ in `onPreviewKeyEvent` (`verticalKeys`) and calls `ListNav.step`: find the next focusable index, `LazyListState.reveal(index, down, ~2 rows)` (scroll so it's visible with context; `scrollToItem` first if it isn't composed), then request focus on it, retrying for up to 12 frames until the row's onFocus reports its key. Otherwise it logs "<where> focus: expected row X, got Y".
- Repeats arriving while a move is settling are dropped, so holding a key never skips rows.
- The guide registers one function per composed row (`rowCells[key]`) that focuses the cell covering a time and returns its start; an anchor keeps the time across consecutive ▲/▼. Guide cells are drawn in one positional loop, so the "Loading…" cell and the first show are the same composable and keep focus when data arrives.

### Focus between panes (Live TV, 2.7)
- `LiveFocus` holds `entry` (a FocusRequester on the channel the list starts on), `focusSelectedGroup()` (set by the group list: reveal, then focus the selected group or its closed heading) and `focusVisible()` (set by the channel list: the first channel on screen).
- **No effect may move focus because data changed.** The channel list used to run `LaunchedEffect(channels) { requestFocus() }`; since focusing a group changes the channels, it pulled focus out of the Groups pane. Now only explicit actions move focus: opening Live TV, ▶ / OK on a group, ◀ from a channel.
- The channel pane follows the focused group after 300 ms without a key, and is keyed by group so each group opens at its own start position.

## 8. Logging and privacy
- `AppLog.setSecrets(pass, user)` is called before any network call. Every line passes through `redact`, which replaces each secret with `•••`.
- `AppLog.e(msg, t)` drops cancellations: `CancellationException`, `InterruptedIOException` (but not `SocketTimeoutException`), or an `IOException` "canceled" / "socket closed", anywhere in the cause chain. The same check guards warnings for background guide loads.
- `about.json` (2.5): `name`, `role`, `dog`, `venmo`, `venmoHandle`, `zelle`. The QR codes are generated at runtime with `Qr.bitmap`. LinkedIn was removed.
- The crash handler writes `"time · version · thread\n" + redacted stack` and then calls the previous handler.

- **Phone pages** (2.5.2): `SetupServer` and `LogServer` (NanoHTTPD) each get a `SecretBox.newToken()` and compare the `k` query parameter in constant time on every request. The token is only in the QR code.
- **Updates** (2.5.2): `Updater` trusts only `https://github.com/ashwin-mathias/couch-lab/releases/download/` links and needs `sparky-iptv.apk.sha256`. `Repository.download(…, expectedSha256, requireHttps = true)` hashes while streaming and deletes the file on a mismatch. Android's same-key signature check still applies. The checksum guards against corruption or a bad mirror, not against a hijacked GitHub account; the signing key is the real protection.

## 9. Build, release, install

- **GitHub Actions** (`.github/workflows/build.yml`) runs on every push to `main` (doc-only pushes are ignored) and on manual dispatch:
  1. Checkout, then setup-java temurin 17, then setup-gradle, then restore the signing key from secrets.
  2. `./gradlew --no-daemon assembleRelease`. On failure, the `e: ` and "What went wrong" lines are re-emitted as `::error::` annotations, because raw logs can't be downloaded without auth.
  3. Copy to `sparky-iptv.apk`, write `sparky-iptv.apk.sha256`, and publish both in a GitHub release `build-<run_number>` marked latest. Also upload it as an artifact.
- `versionCode = GITHUB_RUN_NUMBER`; `versionName = "<appVersion> (build N)"`. The release is signed with the key from the GitHub Actions secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (debug builds too), so each build installs over the last. The workflow decodes the key to `$RUNNER_TEMP/release.jks` and passes `KEYSTORE_PATH` and the passwords to Gradle as environment variables. Without the secrets the build fails with an `::error::` annotation. Keep an offline backup of the keystore: if it is lost, every device needs an uninstall and reinstall.
- **Install**: the Downloader app, code **6976634** → `https://github.com/ashwin-mathias/couch-lab/releases/latest/download/sparky-iptv.apk`. After that, use in-app updates.
- **Release checklist**: bump `appVersion`; add a `changelog.json` entry (newest first, `{version, date YYYY-MM-DD, title, notes[]}`); update README controls if keys changed; update docs/ if behaviour changed; commit; push; watch the build; if it fails, read the annotations with `gh api repos/.../check-runs/<id>/annotations`.
