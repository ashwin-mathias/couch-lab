# Sparky IPTV — Design system and screen layouts

This file describes how the app **looks**. Every value here is taken from the shipping 2.3 code. If code and this file ever disagree, fix one of them in the same commit.

---

## 1. Units: design pixels → dp

- Mockups were drawn on a **1280 × 720** canvas. A 1080p TV lays out at **960 × 540 dp**.
- All sizes below are **design pixels (px)**. Convert with:
  - `Int.u` = px × 0.75 **dp** (sizes, padding, radii)
  - `Int.su` = px × 0.75 **sp** (font sizes)
- Example: a 64 px row is `64.u` = 48 dp. Never write raw dp in UI code; always use `.u` / `.su`.

## 2. Colour tokens (`object C`)

| Token | Hex | Use |
|---|---|---|
| `Bg` | `#0E0F11` | App background; dark text on focused items |
| `Panel` | `#16171A` | Cards, pills, side panels, expanded nav rail |
| `Raised` | `#1E2024` | Selected items, primary buttons, poster placeholders, live guide cell |
| `Line` | `#22242A` | Dividers, progress tracks |
| `Line2` | `#2A2C31` | Stronger lines, borders, inactive bar pills |
| `Text` | `#EEEBE5` | Main text; **focus fill** |
| `Text2` | `#CFCBC3` | Secondary text, clock |
| `Muted` | `#A19D96` | Body copy, unselected nav |
| `Dim` | `#8A8781` | Hints, counts, labels |
| `Accent` | `#E07A5F` | LIVE badge, "now" line, errors, update dot, LATEST |
| `Ok` | `#9DB89A` | Success dots and ticks |
| `OnFocus` | `#0E0F11` | Text and icons on a focused (light) item |
| `OnFocusMuted` | `#4A4843` | Secondary text on a focused item |
| `TrackOnFocus` | `#CFCBC3` | Progress track inside a focused row |

Extra colours:
- Toast background `#F01E2024`.
- Error box background `#2A1A17`, border `#6B3A30`, text `#F2C4B8` / `#D9A99C`.
- Warning log text `#E6C17A`.
- Overlays use `Bg` at 0.70 (picker scrim), 0.85 (clock chip), 0.92 (player info bar) and 0.94 (side panel).

Android theme: `Theme.Material.NoActionBar`, with the window, status and nav bar colour `#0E0F11`. Compose uses `androidx.tv.material3.MaterialTheme(darkColorScheme(background = Bg, surface = Bg, onSurface = Text))`.

## 3. Typography

- **Inter**, four weights bundled as OTF in `res/font`: `inter_regular` (400), `inter_medium` (500), `inter_semibold` (600) and `inter_bold` (700).
- One text composable `T(text, size, color = Text, weight = Normal, maxLines = 1, letterSpacing, lineHeight)`:
  - It always ellipsizes.
  - `letterSpacing` and `lineHeight` are multiples of the font size (em-like).
- `Label(text)` = UPPERCASE, 12 px, SemiBold, letterSpacing 0.12, colour `Dim` by default. It's used for section labels everywhere.

| Role | Size px | Weight |
|---|---|---|
| Setup headline | 44 | Bold |
| Detail page title | 40 | Bold, lineHeight 1.15, 2 lines |
| Loading % | 40 | Bold |
| Screen title (Movies, Settings, Search, Favorites, greeting, Guide focus title) | 30 | Bold |
| Loading title | 28 | Bold |
| About name | 26 | Bold |
| Player / preview programme title | 24 | Bold |
| Panel title ("Audio & subtitles"), picker title | 20–22 | Bold |
| Release card title | 18 | Bold |
| Setting row title | 17 | SemiBold |
| List row title, button text | 15 | SemiBold |
| Body | 14–15 | Normal, colour Muted, lineHeight 1.5–1.6 |
| Hints and counts | 12–13 | Normal, colour Dim |
| Badge | 10 | Bold, letterSpacing 0.06 |

Time formats: clock `h:mm a`; short `h:mm`; day `EEEE, MMM d`; date `MMM d, yyyy`; time left "42m left" or "1h 05m left"; durations "52 min" or "1h 52m"; player times "12:05" / "1:02:05".

## 4. Focus model (the most important rule)

- **FocusItem** (rows, pills, buttons): a `tv-material3` `Surface`.
  - Unfocused: the container colour (transparent, `Panel` or `Raised`); text `Text` / `Muted`.
  - **Focused: fills with `Text` (#EEEBE5); its text and icons switch to `OnFocus` (#0E0F11)** and secondary text to `OnFocusMuted`. Scale 1.0.
  - `selected = true` gives the `Raised` background (for example, the current group or nav item).
  - Children receive `focused: Boolean` and must recolour themselves.
- **FocusCard** (posters, tiles): container `Raised`; focused = a **3 px `Text` border** plus a **1.06 scale**. The content colour doesn't change.
- Every screen requests initial focus about 150 ms after it appears (`FocusRequester` + `LaunchedEffect`). Lists start on the last-watched channel where relevant.
- The nav rail expands while it has focus (72 → 240 wide, background `Bg` → `Panel`, labels appear).

## 5. Shapes and spacing

- Radii:
  - Pills: 999.
  - Buttons and cards: 12.
  - Group and track rows: 10.
  - Guide cells, logos: 8.
  - Badges: 5.
  - Large art: 14–16.
  - Panels: square edge.
- **Safe area**: the Home shell is padded start 36, end 24, top 16, bottom 16, because TVs crop the edges.
- Screen content paddings are typically 40–64 horizontal and 28–48 vertical (given per screen below).

## 6. Components

| Component | Spec |
|---|---|
| `ProgressBar(fraction, track, fill, height = 3)` | Rounded 2. Lists: track `Line`, fill `Dim` (focused: track `TrackOnFocus`, fill `OnFocus`). Posters: track `Bg@0.6`, fill `Text`, height 4. |
| `Badge(text)` | Padding 6×2, radius 5. "LIVE": `Accent` with `Bg` text. Others: `Line2` with `Text2` text (focused: `OnFocus` with `Text` text). |
| `ChannelLogo(name, url, size)` | Rounded 8, background `Raised` (`OnFocus` when focused). The image is Coil `Fit` at 86% of the box. If it fails or is missing, a **monogram** (first letter of up to 3 words, or the first 3 letters of a single word; "TV" if empty), 12 Bold. |
| `Poster(title, url)` | Background `Raised`, Coil `Crop`. Fallback: the title, 15 Bold, 3 lines, bottom-left, padding 14. |
| `PillButton(text, primary, icon)` | Height 50, radius 12, horizontal padding 24. Container `Raised` (primary) or `Panel`. Icon 18 + 10 gap. Text 15 SemiBold. |
| `Clock()` | 15 SemiBold `Text2`, updates every 15 s. |
| `Toast` | Bottom centre, 36 from the bottom. Pill `#F01E2024`, 15 SemiBold, padding 22×12, **2 s**. |
| `ChannelTile` | 16:9 FocusCard on `Panel` with the logo (64) centred. The number at top-left (11 SemiBold Dim). ♥ at top-right (13, Text2). The now-progress bar at the bottom (3, track Line). Below the card: the badge and title (14 SemiBold) and the now title (12 Dim). Optional live preview: plays in the card after 1 s of focus. Hold OK = favourite. |
| `PosterCard` / `PosterTile` | 2:3 FocusCard. Favourite = a heart in a dark circular chip (`Bg@0.75`, padding 5) at top-right. Optional bottom progress (4). Title 13 `Text2` below. |
| Setting row | Height 66, `Panel` (`Raised` if highlighted), padding 20. Title 17 SemiBold, then 4 gap, then the subtitle 13 Dim. |
| Chip | Height 40, pill, `Panel`; selected `Raised`; text 14 SemiBold (selected `Text`, else `Muted`), padding 18. |
| Track row | Height 46, radius 10. A "✓" column 24 wide, then the label 15. |
| Option row (2.4) | Height 54, radius 10, `Panel`, padding 16. Label 14 `Muted` (focused `OnFocusMuted`) on the left; on the right "‹" 18 Bold, the value 15 SemiBold (padding 10), and "›" 18 Bold. An arrow is hidden at the first or last value. ◀/▶ change the value; OK steps through. |

## 7. Icons

All icons are **24×24 vector drawables, white, tinted at runtime**. Line icons use stroke 1.8 with round caps and joins and no fill. **Copy the XML files from `app/src/main/res/drawable/` exactly.** The path data is repeated here so they can be recreated without the repo.

| File | Style | pathData |
|---|---|---|
| `ic_home` | stroke | `M4,11 L12,4 L20,11 L20,20 L4,20 Z M10,20 L10,14 L14,14 L14,20` |
| `ic_live` | stroke | `M5,6 H19 A2,2 0 0 1 21,8 V17 A2,2 0 0 1 19,19 H5 A2,2 0 0 1 3,17 V8 A2,2 0 0 1 5,6 Z M8,3 L12,6 L16,3` |
| `ic_guide` | stroke | `M5,4 H19 A2,2 0 0 1 21,6 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 V6 A2,2 0 0 1 5,4 Z M3,10 H21 M9,10 V20` |
| `ic_heart` | stroke | `M12,20.5 C11.6,20.2 4,15.3 4,9.6 C4,7 6,5 8.4,5 C10,5 11.3,5.9 12,7.1 C12.7,5.9 14,5 15.6,5 C18,5 20,7 20,9.6 C20,15.3 12.4,20.2 12,20.5 Z` |
| `ic_recent` | stroke | `M4.5,12 A7.5,7.5 0 1 0 6.7,6.7 M4,4.5 L4,8.5 L8,8.5 M12,8 L12,12 L15,14` |
| `ic_movies` | stroke | `M6,3 H18 A2,2 0 0 1 20,5 V19 A2,2 0 0 1 18,21 H6 A2,2 0 0 1 4,19 V5 A2,2 0 0 1 6,3 Z M4,8 H20 M4,16 H20 M9,3 V21` |
| `ic_series` | stroke | `M5,7 H19 A2,2 0 0 1 21,9 V18 A2,2 0 0 1 19,20 H5 A2,2 0 0 1 3,18 V9 A2,2 0 0 1 5,7 Z M6,4 H18` |
| `ic_search` | stroke | `M17.5,11 A6.5,6.5 0 1 1 4.5,11 A6.5,6.5 0 1 1 17.5,11 Z M20,20 L16,16` |
| `ic_settings` | stroke | `M15,12 A3,3 0 1 1 9,12 A3,3 0 1 1 15,12 Z M12,3 V6 M12,18 V21 M3,12 H6 M18,12 H21 M5.6,5.6 L7.7,7.7 M16.3,16.3 L18.4,18.4 M5.6,18.4 L7.7,16.3 M16.3,7.7 L18.4,5.6` |
| `ic_list` | stroke (round cap, no join) | `M4,6 H20 M4,12 H20 M4,18 H20` |
| `ic_tiles` | stroke (round join, no cap) | `M4,4 H10 V10 H4 Z M14,4 H20 V10 H14 Z M4,14 H10 V20 H4 Z M14,14 H20 V20 H14 Z` |
| `ic_refresh` | stroke | `M20,12 A8,8 0 1 1 17.7,6.3 M20,4 V8 H16` |
| `ic_restart` | stroke | `M4,12 A8,8 0 1 0 6.3,6.3 M4,4 V8 H8` |
| `ic_play` | **fill** | `M7,4 L20,12 L7,20 Z` |
| `ic_star` | **fill** (a filled heart, despite the name) | same path as `ic_heart` |
| `ic_star_outline` | stroke (an outlined heart) | same path as `ic_heart` |

Usage:
- Nav icons are 22 (tint `OnFocus` when focused, `Text` when selected, otherwise `Muted`).
- Favourite markers are 13–16 using `ic_star`.
- Toggles use `ic_star` / `ic_star_outline`.
- Tiles/List toggles use `ic_tiles` / `ic_list` at 15–16.

### Image assets (copy the binaries; they can't be regenerated from text)
| File | Size | Content |
|---|---|---|
| `drawable-nodpi/logo.png` | 256² RGBA | Rounded-square illustration: a smiling golden Labrador-style dog at a wooden desk, with a wall-mounted TV showing a **black squirrel** (charcoal `#2C2C30`, grey belly, white eye glint; brown until 2.7) on a brown branch, and a red "BRK ..NEWS!!" banner, on a warm cream background. Used in the nav rail (48, radius 12), Setup (44, radius 11) and Loading (140, radius 32). |
| `drawable-xhdpi/banner.png` | 320×180 | The Android TV launcher banner: the logo (with the black squirrel) on the left, with "Sparky" (white) and "IPTV" (accent coral) in bold sans on near-black. |
| `mipmap-*/ic_launcher.png` | 48–192 | The launcher icon (the same art as the logo). The 192 px file is the master; the smaller sizes are Lanczos downscales of it. |
| `drawable-nodpi/avatar.png` | 320² | The author's avatar (the cartoon dog at a desk) for the About page (64, circle). |
| `drawable-nodpi/sparky.jpg` | 512² | Photo of Sparky, a yellow Labrador, face centred against a blue sky (2.5). About page, 96 circle, crop. |
| `res/font/inter_*.otf` | — | Inter 400/500/600/700. |

## 8. Screen layouts (design px)

### 8.1 Shell (Home)
- A Row with the safe-area padding (36/24/16/16), containing **NavRail** and then the content (weight 1).
- **NavRail**:
  - Column, full height, padding 22 vertical and 12 horizontal, spacing 6.
  - Width 72 (collapsed) or 240 (expanded with focus).
  - Top: the logo 48 (radius 12), plus "Sparky IPTV" 15 Bold when expanded; 14 below it.
  - Buttons are 48 high, 48 wide (or 216 when expanded), with padding 13, the icon, a 14 gap and the label 15.
  - Order: Home, Live TV, TV Guide, Favorites (heart), Recently watched (clock), Movies, Series, Search, then a spacer, then **Settings** at the bottom (with an 8 px accent dot when an update exists).

### 8.2 Live TV
- **Groups column**, 270 wide: padding top 28, start 20, end 16.
  - The "GROUPS" label (padding start 10, top 6, bottom 14).
  - The list: 4 spacing. Group rows are 42 high (radius 10, padding 12): the name 15 (Normal; SemiBold when selected or focused) and the count 13 Dim.
  - Region headings (2.7) are focusable rows, 42 high (radius 10, padding 12), with 10 above: the arrow `▾` / `▸` 14 `Text2` in an 18-wide slot, the Label `Text2`, and the count 11 Dim. Focused: the usual light fill with `OnFocus` text. Selected (closed, holding the current group): `Raised`.
  - Favorites folder rows (2.7) are group rows indented 14, with the name 14.
  - The action row is 42 high on `Panel`, 14 SemiBold.
- A 1 px `Line` divider.
- **Channels column**, 470 wide: padding top 28 and horizontal 12.
  - The header row is 36 high: the label (the group name) and the toggle pill (34 high, `Panel`, with icon 15 and text 12) or the hint "Hold OK to ♥" (12 Dim).
  - The list has 4 spacing and rows 64 high, padded 14:
    - The number column is 46 wide (13 SemiBold Dim), then the logo 40, then a 14 gap.
    - Then a column with 4 spacing: [badge + title 15 SemiBold (Bold when focused) + ♥ 13 + "Nm left" 12], the now title 13 Muted, and the progress bar 3.
  - Divider rows: an 18 px line, the Label `Text2`, and a line filling the rest (padding top 16, bottom 6).
  - The tile grid is 3 columns with 14 h-spacing and 18 v-spacing.
- A 1 px divider.
- **Preview pane** (weight 1): padding 28, spacing 20.
  - The clock row on the right.
  - The 16:9 video (radius 14, `Panel`) with a LIVE pill (`Bg`, a 7 px accent dot, "LIVE" 11 Bold, padding 10×4, at 12 from the top-left).
  - The now label, the title (24 Bold, 2 lines), the progress bar, and the description (14 Muted, 3 lines, lineHeight 1.55).
  - A spacer, a 1 px line, then "Next · time" with the title 16 SemiBold, and "OK to watch" 13 Dim on the right.

### 8.3 Full-screen live
- Black background; the video fills the screen (fit).
- The clock chip at top-right (32 from the top, 48 from the right; `Bg@0.85`, radius 10, padding 14×8).
- The **info bar** at bottom centre (margins 48 / 48 / 40, radius 18, `Bg@0.92`, padding 28×24):
  - The logo 64, a 24 gap, then a column with 8 spacing:
    - [number 15 Bold Muted, name 15 SemiBold Text2, ♥ 16, "N of M" 13 Dim].
    - The title 24 Bold.
    - [start 13, progress bar 4 (track Line2, fill Text), end 13].
    - [Next line 13 Dim, and the hint 13 Dim on the right].
    - Catch-up channels: "CATCH-UP 7D" 11 Bold Dim (letterSpacing 0.06), 12 after the name.
    - An error replaces the title, at 20 Bold, up to 2 lines.
- **Paused / behind live (2.5)**: the info bar is replaced by the shared **control bar** (§8.4) with a heading line (15 SemiBold Text2).
  - A chip at top-left (32 / 48; `Bg@0.85`, radius 10, padding 14×8) shows "PAUSED" or "BEHIND LIVE" (12 Bold Accent, letterSpacing 0.08), a 10 gap, then the lag 15 SemiBold Text2.
  - The clock chip stays at top-right.
- The **track panel**: right side, full height, 380 wide, `Bg@0.94`, padding 24 / 40 / 40 / 40, spacing 6.
  - The title 20 Bold, then the note 12 Dim.
  - The "AUDIO" label (padding top 6, bottom 4), then track rows.
  - The "SUBTITLES" label (padding top 16), then Off and the tracks.
  - Then (after a 10 gap each) "Subtitle style…" and "Sleep timer…" (2.6) rows, each with a 12 Dim summary line indented 38.
- The **sleep panel** (2.6) has the same frame as the track panel: the title "Sleep timer" 20 Bold, the note 12 Dim (2 lines, 10 below), then four track rows (46 high, ✓ column 24) for Off, 30, 60 and 90 min.
- **Clock chip with sleep timer** (2.6): while a timer runs, the chip reads "Sleep in 42 min" (13 SemiBold Text2), "␣␣␣·␣␣␣" (13 Dim), then the clock.

### 8.4 Movie/episode player
- The same black full-screen video.
- When the bar or panel is visible: the title 20 Bold and subtitle 14 Text2 at top-left (32 / 48, right margin 480), and the clock chip at top-right (hidden while the panel is open).
- **Control bar** (one shared `ControlBar` composable for movies, episodes, recordings and live when behind; 2.5): bottom centre, margins 48 / 48 / 40, radius 18, `Bg@0.92`, padding 28×22, column spacing 16.
  - Optional heading 15 SemiBold Text2 (live only).
  - **Timeline row**: the left label 14 SemiBold (96 wide; `Text` when active, `Muted` otherwise), the progress bar (6 high and `Text` fill when active; 4 high and `Muted` fill otherwise; track `Line2`), a 16 gap, then the right label 14 Muted (natural width).
  - **Buttons row**: spacing 10. Pills are radius 10, padding 16×9, text 14 SemiBold. The current pill is `Text` with `OnFocus` text; the others are `Line2@0.6` with `Text`. Then a spacer and the hint 12 Dim.
- The track panel is the same as live.

### 8.5 TV Guide
- Padding 48 horizontal and 28 vertical, spacing 18.
- The header is 150 high:
  - Left: [GUIDE label + day label Text2], the title 30 Bold, the info line 14, and the description 14 Muted (2 lines, lineHeight 1.6, padding end 40).
  - Right: the clock and the pills (40 high, `Panel`, padding 16; the group pill is at most 300 wide).
- **Grid**: the channel column is 224 wide. Minutes = (width − 224) / 150.
  - The ruler is 32 high, with labels 13 SemiBold Muted every 30 min. Then a 1 px line and a 4 gap.
  - Rows are 52 high (compact 38): the number 12 Dim (44 wide), the logo 34 (compact 28), a 12 gap, and the name 14 SemiBold.
  - Cells are 46 high (compact 34), radius 8, with 4 gaps; on-air `Raised`, otherwise `Panel`. Each holds the title 14 SemiBold and the times 11 Dim, padding 12. The minimum cell width is 24 dp.
- **Now line**: 2 px `Accent`, starting 26 from the top. The tag is `Accent`, radius 6, padding 8×2, time 11 Bold `OnFocus`, offset −24. Only drawn when now is in the window.
- **Past shows (2.5)**:
  - Replayable cells start with "↺␣␣".
  - Non-replayable past cells use `Dim` title text (still `Panel`).
  - The **Now** pill is first in the header pill row: 40 high, `Raised`, radius 999, "Now" 14 SemiBold, padding 18.
- **Group picker**: scrim `Bg@0.7`; the panel is 420 wide, `Panel`, padding 20×28. Rows are 46 high.

### 8.5a Favourite menu, folder checklist, Move (2.7)
- Menus are dialogs: a panel 460 wide (480 for the folder checklist), `Panel`, radius 18, a 1 px `Line` border, padding 22, spacing 6. The scrim is the system dialog dim.
  - A Label ("FAVORITE", "FOLDERS", "FOLDER"), then the item name 20 Bold (2 lines), then 10 space.
  - **Menu rows**: 48 high, `Raised`, radius 10, padding 16: the title 16 SemiBold on the left, the value 14 `Dim` on the right (`OnFocusMuted` focused).
  - The hint "Back to close" 12 Dim at the bottom. Errors 14 `Accent`.
  - The folder list scrolls above 380 high.
- **Name field**: 50 high, `Bg`, radius 10, border 1 `Line` (focused 2 `Text`), padding 16, text 16 (placeholder Dim).
- **Moving**: the item gets a 3 px `Accent` outline (radius 14). A pill at the top centre (20 from the top), `Accent` with `Bg` text 15 SemiBold, padding 22 × 10: "Moving CNN · ▲ ▼ to move · OK to drop · Back to cancel".

### 8.6 Home
- LazyColumn padding start 40, top 24, bottom 48; spacing 26.
- The header row (padding start 8, end 48): the greeting 30 Bold, a spacer, the updating text 13 Dim, and the clock.
- Each row: Label (Muted, padding start 8), a 10 gap, then a LazyRow with spacing 20 and row padding start 8, end 48, top 8, bottom 8 (room for the focus scale).
- Channel tiles 220; posters 132.
- Folder rows (2.7) follow Favorites and look the same: the folder name as the Label, then channel tiles.

### 8.7 Favorites / Recently watched pages
- Content grid padding 40 / 28 / 32 / 48. Tiles: 3 columns, spacing 18 × 22. List: 1 column, spacing 6.
- List rows are 64 high on `Panel`, padding 16: the number column 52, the logo 40, the title and now line, and on the right a 160-wide column with "left" and the progress bar.
- The header toggle pill is 40 high, `Panel`, padding 16, with icon 16 and text 14.
- The preview pane on the right is 400 wide.

### 8.8 Movies / Series
- Grid of 6 columns, padding 48 / 48 / 32 / 40, spacing 20 × 24.
- The header is the title 30, a 16 gap, "N titles" 14 Dim, a spacer and the clock. Chips use spacing 10.
- Continue-watching cards are 248 wide and 16:9, with the title 14 SemiBold padded 12×10.

### 8.9 Movie detail
- Padding 64 × 48; columns spaced 56.
- Left column spacing 14. Meta joined by "   ·   " at 14 Text2. Plot 15 Muted (6 lines, lineHeight 1.6). Director and cast 13 Dim. The progress block is 320 wide. Buttons use spacing 12, with padding top 8.
- Right: the poster, 300 wide at 2:3, radius 16.

### 8.10 Series detail
- Padding 64 × 44; spacing 30.
- The top row matches movie detail, but the art is 400 wide at 16:9.
- Season chips use spacing 10. Episode cards are 266 wide with spacing 20 and end padding 64; their text is "E5 · 42 min" 12 Dim and the title 15 SemiBold.

### 8.11 Search
- Padding 48 / 48 / 32 / 40; spacing 28.
- The field is 56 high, `Panel`, radius 14, with a border 1 `Line2` (focused: 2 `Text`), padding 18. It holds the icon 20 Muted, a 14 gap, and the text 17 (placeholder Dim).
- Channel result cards are 260 × 64 on `Panel`. Posters are 140 wide.

### 8.12 Settings
- Padding 48 left and right, 24 top, 16 bottom; spacing 16 (2.6).
- The left column is 520 wide with spacing 4, scrollable only as a fallback (2.6):
  - **Rows**: single line, **48 high**, the usual `FocusItem` (`Panel`; the update row `Raised`), padding 20. The title 17 SemiBold on the left; a 16 gap; the value 14 `Dim` on the right (`OnFocusMuted` when focused), right-aligned and ellipsized.
  - **Group headings**: the `Label` style (12 SemiBold uppercase, Dim), padding start 4; 16 above (none for the first) and 6 below, counting the column spacing.
  - At 1080p the four groups (12 rows in 2.7: Favorite folders, Back up settings and Restore settings were added) fit without scrolling.
- The right column has spacing 14, with info rows of the label 14 Dim (140 wide) and the value 14 Text2. Only the PLAYLIST block (the APP block was removed in 2.6).

### 8.13 Regions
- Padding 64 × 40; spacing 18.
- Grid of 4 columns with 12 spacing. Cards are 64 high, radius 12, `Panel` (`Raised` when on), padding 14.
- The checkbox is 22, radius 6, with a 1.5 border (`Dim`; `Text` when on; `OnFocus` when focused). When on it fills `Text` (`OnFocus` when focused) with a "✓" 13 Bold.

### 8.14 Loading
- Padding 112 × 72; columns spaced 80 and vertically centred.
- The left column is 360 wide with spacing 20: the logo 140, the title 28, the text 15 Muted, and the status dot 8 with its text 13 Dim.
- Right column:
  - The % at 40 Bold, with the elapsed time 14 Dim.
  - The track is 8 high, radius 4, `Line`; the fill is `Text` (accent on error). The sheen is 18% wide, white at 0.18, sweeping every 1.6 s.
  - Step rows are 44 high, radius 10 (`Panel` when active). The status mark is 18: done = an `Ok` circle with "✓"; failed = an `Accent` circle with "!"; active = a pulsing `Text` circle; pending = a `Line2` ring.

### 8.15 Setup
- Padding 112 × 64; spacing 40.
- Brand row: the logo 44 and "Sparky IPTV" 18 SemiBold.
- Left: the headline 44, the text 18 Muted (max 520 wide), and the steps (the number 14 Bold Dim in a 30-wide column, the text 17).
- Right: the QR box 300, radius 18, `Text` background, padding 24. The QR uses dark modules `#0E0F11` on light `#EEEBE5`, margin 0. Below it: "Or open on any device on this Wi-Fi" 13 Dim and the address 18 SemiBold.
- Footer: a 1 px line, then the status dot and texts at 14 Dim.

### 8.15a Subtitle style (Settings)
- Padding 64 × 44; columns spaced 48.
- The left column is 460 wide, spacing 8: the title 30 Bold, the text 14 Muted (2 lines, lineHeight 1.5, 12 below), then the option rows (spacing 6) and a "Reset to default" row (46 high, transparent, 14 SemiBold Muted).
- The right column (weight 1, spacing 12): "PREVIEW" label (10 from the top), a 16:9 box (radius 14) with a vertical gradient `#3C82DC → #66A3EA → #B9D5F2 → #7A5A48` and a Media3 `SubtitleView` showing the sample cue, then the note 13 Dim.
- In the player panel, the same option rows replace the track list. The panel's title becomes "Subtitle style" and the note reads "◀ ▶ to change, shown live on the video.␣␣Back to return."
- **Subtitles themselves**: the colours and sizes are in SPEC §14.1. The font is the player default.

### 8.16 Diagnostics and About
- **Diagnostics**: padding 64 × 44; columns spaced 48.
  - The left column is 340 wide: the QR box 220 (radius 16) and the header text 12 Dim (10 lines).
  - At the bottom: the **Test catch-up** pill on its own row, then [Refresh, Clear crash].
  - Log lines are 12 px, focusable (focused background `Panel`).
- **About**: padding 64 × 44; columns spaced 56.
  - The left column is 380 wide, spacing 16:
    - The ABOUT label.
    - The author row: avatar 64 circle, a 16 gap, the name 22 Bold and the role 13 Muted (spacing 3).
    - The **Sparky card** (2.5): `Panel`, radius 16, padding 18, spacing 14.
      - [Sparky photo 96 circle (crop), a 16 gap, the note 15 Text2, lineHeight 1.45, with "Sparky" Bold `Text`].
      - "Buy Sparky a treat" 14 SemiBold.
      - A row of two QR tiles spaced 16. Each tile is 128 square, radius 12, `Text` background, padding 10; the QR uses dark modules on light, margin 0. Under it: the label 13 SemiBold and the sub-label 11 Dim, spaced 6.
    - A spacer, then Version, Device, Network, and the privacy note.
  - Release cards are `Panel` (focused `Raised`), radius 14, padding 22×18: "Version x" 13 Bold Text2, the date 13 Dim, "LATEST" 11 Bold Accent, the title 18 Bold, and bullets 14 Muted.

### 8.17 Favorite folders, Back up and Restore screens (2.7)
- **Favorite folders**: padding 96 × 56. Left column 620 wide, spacing 14: the title 34 Bold, the text 15 Muted (3 lines, lineHeight 1.5), then a list of menu rows (48 high, spacing 6). Then an 80 gap and the QR column.
- **Back up / Restore**: padding 112 × 64, like Setup: the title 40 Bold, the text 18 Muted (max 560 wide), numbered steps (number 14 Bold Dim in a 30 slot, text 17), the hint 14 Dim. Then an 80 gap and the QR column.
- **QR column** (max 420 wide, spacing 16): a Label, the QR 260 on a `Text` tile (radius 18, padding 20), the text 15 Muted, and the hint 13 Dim.

## 9. Phone web pages (setup and diagnostics)

- They are styled to match: background `#0E0F11`, text `#EEEBE5`, a system font stack, max width 440 (setup) or 760 (log).
- Inputs are 50 high, radius 12, on `#16171A` with a `#2A2C31` border (focused 1.5 px `#EEEBE5`).
- The primary button is 54 high, radius 14, `#EEEBE5` with dark text.
- The tab switcher is a segmented control; the active tab is light with dark text.
- The error box uses the error colours. Success shows a 64 px circle with a green tick.
- **2.7 pages** (backup, restore, favorites folders) use the same base plus:
  - Checkbox / radio cards: padding 14 × 16, `#16171A`, border `#2A2C31`, radius 12, with a small `#8A8781` line under the title.
  - Message boxes: success `#17221A` / `#3B5A3E` / `#C9E3C6`; warning `#2A2417` / `#6B5A30` / `#F2E2B8`; error as before.
  - A secondary button (50 high, `#16171A`, bordered) for "Restore anyway".
  - Folder cards (radius 14) with channel rows (number 12 Dim, name 15, and 40 × 36 icon buttons ↑ ↓ ✕). Section headings are 13 uppercase `#8A8781` with letter spacing. **Save to TV** sticks to the bottom.
  - On the setup page, a "Restore from a backup file" link under the form.
