# Sparky IPTV — documentation

| File | What it answers |
|---|---|
| [SPEC.md](SPEC.md) | What the app does: every screen, key, rule and on-screen text |
| [DESIGN.md](DESIGN.md) | How it looks: colours, type, units, focus, components, icons, screen measurements |
| [ARCHITECTURE.md](ARCHITECTURE.md) | How it's built: pinned stack, code layout, data formats, storage, player, CI |
| [DECISIONS.md](DECISIONS.md) | Why: settled decisions and lessons from past bugs |
| [ACCEPTANCE.md](ACCEPTANCE.md) | How to prove it works: TV test checklist and regression tests |
| [REBUILD_PROMPT.md](REBUILD_PROMPT.md) | The prompt for rebuilding the app from these docs |

- Every release updates the affected docs **in the same commit** as the code.
- `./docs/make-bundle.sh` joins the five spec files into one `sparky-rebuild-context.md` for pasting into a chat.
