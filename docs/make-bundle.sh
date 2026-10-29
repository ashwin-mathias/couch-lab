#!/usr/bin/env bash
# Joins the spec files into one file you can attach to a chat with an AI coding agent.
set -euo pipefail
cd "$(dirname "$0")"
out="${1:-sparky-rebuild-context.md}"
{
  echo "# Sparky IPTV — full rebuild context"
  echo
  echo "Generated $(date +%Y-%m-%d) from docs/ in ashwin-mathias/couch-lab. Sections: SPEC, DESIGN, ARCHITECTURE, DECISIONS, ACCEPTANCE."
  for f in SPEC.md DESIGN.md ARCHITECTURE.md DECISIONS.md ACCEPTANCE.md; do
    echo; echo "---"; echo; echo "<!-- $f -->"; echo
    cat "$f"
  done
} > "$out"
echo "Wrote $out"
