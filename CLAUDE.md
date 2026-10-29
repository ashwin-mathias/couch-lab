# Working on Sparky IPTV (couch-lab)

Read `docs/README.md` first. The docs in `docs/` are the source of truth for behaviour, design and architecture.

## Every release (required, same commit as the code)
1. Bump `appVersion` in `app/build.gradle.kts`.
2. Add a newest-first entry to `app/src/main/assets/changelog.json` (`version`, `date` YYYY-MM-DD, `title`, `notes`).
3. Update the docs that the change touches:
   - `docs/SPEC.md` for behaviour, keys or on-screen text.
   - `docs/DESIGN.md` for sizes, colours, components or icons.
   - `docs/ARCHITECTURE.md` for libraries, storage keys, data formats or CI.
   - `docs/DECISIONS.md` for any decision Ashwin made (with the reason) and any lesson from a bug.
   - `docs/ACCEPTANCE.md`: add tests for new features, and add a regression row (R#) for every bug fixed.
   - The "Version described" line at the top of SPEC.md.
4. Update the README controls table if remote keys changed.
5. Commit, push, and watch the GitHub Actions build. If it fails, read the `::error::` annotations.

## Working style with Ashwin
- Discuss and agree before building when he asks to; wait for "build it".
- Reply in Minto style: bold key points first, then supporting bullets.
