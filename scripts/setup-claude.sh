#!/usr/bin/env bash
# One-time setup: symlinks shared Claude skills from the repo into ~/.claude/skills/
# Run once after cloning: bash scripts/setup-claude.sh

set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel)"
SKILLS_SRC="$REPO_ROOT/.claude/skills"
SKILLS_DST="$HOME/.claude/skills"

mkdir -p "$SKILLS_DST"

for skill_file in "$SKILLS_SRC"/*.md; do
  name="$(basename "$skill_file")"
  target="$SKILLS_DST/$name"
  if [ -L "$target" ]; then
    echo "Already linked: $name"
  else
    ln -s "$skill_file" "$target"
    echo "Linked: $name"
  fi
done

echo "Done. Skills available in ~/.claude/skills/"
