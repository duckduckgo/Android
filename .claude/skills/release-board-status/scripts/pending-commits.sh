#!/usr/bin/env bash
# Outputs commits merged after the LGC tag (will miss the next cut).
set -euo pipefail

git fetch --tags -q 2>/dev/null || true

lgc_tag=$(git tag -l 'LGC-*' | sort -r | head -1)

echo "pending:START"
if [ -n "$lgc_tag" ]; then
  git log "${lgc_tag}..HEAD" --oneline \
    | grep -vE "Merge branch 'release/|Updated .* for new release|Merge pull request .* from .*release" \
    || true
fi
echo "pending:END"

echo "pending_prs:START"
if [ -n "$lgc_tag" ]; then
  git log "${lgc_tag}..HEAD" --oneline \
    | grep -vE "Merge branch 'release/|Updated .* for new release|Merge pull request .* from .*release" \
    | grep -oE '#[0-9]+' | tr -d '#' | sort -u \
    || true
fi
echo "pending_prs:END"
