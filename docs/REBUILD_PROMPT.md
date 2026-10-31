# Rebuild prompt

Paste the prompt below into a new Claude Code session (or any coding agent) that has access to the `ashwin-mathias/couch-lab` repo. Attach `sparky-rebuild-context.md` (all five spec files in one) if the agent can't read the repo.

> Tip: an agent with the repo can copy the icons, fonts and images byte-for-byte. Without them it can match the icons (their paths are in DESIGN.md) but **not** the logo, banner or avatar. The signing key is **not in the repo** (it is in GitHub Actions secrets); an APK signed with a different key won't install over the current app.

---

```text
You are rebuilding "Sparky IPTV", an Android TV / Google TV IPTV app (package com.sparky.iptv),
from its specification. The result must match the shipping version in behaviour, look and feel. The current version is in `appVersion` in app/build.gradle.kts and at the top of docs/SPEC.md; the docs describe it. If this prompt and the docs disagree, the docs win.

## Read first (these files are the source of truth, in this order)
1. docs/SPEC.md          – what every screen and key does (behaviour, texts, rules)
2. docs/DESIGN.md        – design tokens, units, components, icons, per-screen measurements
3. docs/ARCHITECTURE.md  – pinned stack, code layout, data formats, storage keys, player/focus pattern, CI
4. docs/DECISIONS.md     – settled decisions and engineering lessons (do not reopen them)
5. docs/ACCEPTANCE.md    – the tests the build must pass, including regression tests (the R rows)
If anything is ambiguous, follow the spec's wording exactly, and list the ambiguity in your final report
instead of inventing new behaviour.

## Hard requirements
- Use exactly the stack and versions in ARCHITECTURE.md §1 (AGP 8.5.2, Kotlin 2.0.20, Compose for TV
  tv-material 1.0.0, Media3 1.4.1, OkHttp 4.12.0, Coil 2.7.0, NanoHTTPD 2.3.1, ZXing 3.5.3, JDK 17,
  minSdk 23, target 34). No extra libraries (no Navigation, Room, Hilt, Retrofit, Moshi).
- Copy these files byte-for-byte from the repo and don't regenerate them: app/src/main/res/drawable/*.xml
  (16 icons), res/drawable-nodpi/logo.png and avatar.png, res/drawable-xhdpi/banner.png,
  res/mipmap-*/ic_launcher.png, res/font/inter_*.otf, assets/changelog.json,
  assets/about.json, gradle wrapper files.
- UI sizes are written in design px. Implement the `.u` (×0.75 dp) and `.su` (×0.75 sp) units and use them
  everywhere. Use the colour tokens in DESIGN.md §2 by name (object C). Use no other colours, except the few
  listed extras.
- Focus is shown only as in DESIGN.md §4: FocusItem fills light (#EEEBE5) with dark content; FocusCard has a
  3px light border and 1.06 scale. Every screen sets initial focus. Focus must never be invisible or lost.
- Players: never use the stock ExoPlayer controller. One focusable Compose Box handles all keys with
  onPreviewKeyEvent. The PlayerView is not focusable. Follow the key tables in SPEC §7.6 and §12.1 and the
  BackHandler ordering in ARCHITECTURE §7 exactly.
- Big catalogs: stream downloads to disk, parse with android.util.JsonReader, store the binary snapshot
  (ARCHITECTURE §4.3, same MAGIC and version so existing installs can read it), load live first and VOD in
  the background, and build stream URLs on demand.
- Keep SharedPreferences name "sparky" and every key in ARCHITECTURE §6 unchanged, so an update keeps the
  user's login, favourites and history.
- Never log the password or username (AppLog redaction).
- All user-facing text exactly as written in SPEC.md (including punctuation like "…", "·", "—").
- The theme object is named C. Import Media3's C as MediaC.
- AppViewModel's init {} block is the last thing in the class.
- Lazy list keys are "$index:$id".

## How to work
1. Plan: list the files you'll create (ARCHITECTURE §3) and the order. Show me the plan, then continue.
2. Build in vertical slices, each one compiling and pushed before the next:
   a. Gradle setup, manifest, resources, theme/components (Theme.kt, Pieces.kt, Tiles.kt), an empty Home shell
      with the NavRail.
   b. Data: models, Names, Prefs, Xtream client, StreamParsers, Snapshot, Repository, AppLog, M3U.
   c. Setup (phone QR + NanoHTTPD page) → Loading (steps, progress, errors) → Home.
   d. Live TV (groups, list, tiles, preview, handoff) + full-screen live player + audio/subtitles panel.
   e. Guide, Regions, Favorites/Recent pages, Home rows.
   f. Movies, Series, details, VOD player bar (SPEC §12).
   g. Search, Settings, Updater, Diagnostics + LogServer, About.
   h. CI workflow (ARCHITECTURE §9), README.
3. After each slice, re-read the matching SPEC/DESIGN sections and compare them against your code line by line
   (texts, sizes, colours, keys). Fix differences before moving on.
4. Builds run on GitHub Actions. If a build fails, read the ::error:: annotations, fix and push again.
5. Finally, go through docs/ACCEPTANCE.md and report each item as: verified in code / needs TV test /
   not met (with reason). Don't claim anything is verified unless you checked it.

## Definition of done
- assembleRelease passes on CI and publishes build-N with sparky-iptv.apk, signed with the key from the GitHub Actions secrets (ARCHITECTURE §9).
- Every SPEC section is implemented; every DESIGN value is used; every ACCEPTANCE item is either verified in
  code or listed for TV testing.
- A final report lists: deviations (should be none), ambiguities found, and the TV test checklist.
```

---

## Notes for Ashwin
- **Exact rebuild vs. improved rebuild.** The prompt above asks for an exact copy. To change something, edit SPEC/DESIGN first, then rebuild. The docs stay the source of truth.
- **No prompt guarantees zero defects.** What keeps defects out is the acceptance list (especially R1–R10) run on the real TVs. Rebuilding in slices with a code-vs-spec check after each one also helps.
- **Keep the docs current.** Each release, update the relevant doc in the same commit as the code (this is in the release checklist in ARCHITECTURE §9).
