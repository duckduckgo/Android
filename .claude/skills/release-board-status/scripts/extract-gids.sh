#!/usr/bin/env bash
# Usage: bash extract-gids.sh PR1 PR2 ...
# Outputs one line per PR: "pr:N gid:M" (gid empty if not found)
for pr in "$@"; do
  body=$(gh pr view "$pr" --json body --jq '.body' 2>/dev/null || echo "")
  gid=""
  if [ -n "$body" ]; then
    gid=$(echo "$body" | grep -i "Task/Issue URL:" | head -1 | grep -oE '/task/[0-9]+' | head -1 | grep -oE '[0-9]+')
    if [ -z "$gid" ]; then
      gid=$(echo "$body" | grep -i "Task/Issue URL:" | head -1 | grep -oE 'app\.asana\.com/0/[0-9]+/[0-9]+' | head -1 | sed 's|.*/||')
    fi
  fi
  echo "pr:${pr} gid:${gid}"
done
