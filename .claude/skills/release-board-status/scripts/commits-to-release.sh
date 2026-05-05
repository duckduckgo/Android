#!/usr/bin/env bash
# Outputs release context: LGC tag, latest release, commits and PRs between them.
set -euo pipefail

git fetch --tags -q 2>/dev/null || true

lgc_tag=$(git tag -l 'LGC-*' | sort -r | head -1)
latest_release=$(git tag -l | grep -E '^5\.[0-9]+\.[0-9]+$' | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)

echo "lgc_tag:${lgc_tag}"
echo "latest_release:${latest_release}"

if [ -z "$lgc_tag" ] || [ -z "$latest_release" ]; then
  echo "commits:START"
  echo "commits:END"
  echo "prs:START"
  echo "prs:END"
  exit 0
fi

commits=$(git log "${latest_release}..${lgc_tag}" --oneline \
  | grep -vE "Merge branch 'release/|Updated .* for new release|Merge pull request .* from .*release" \
  || true)

echo "commits:START"
echo "$commits"
echo "commits:END"

echo "prs:START"
if [ -n "$commits" ]; then
  echo "$commits" | grep -oE '#[0-9]+' | tr -d '#' | sort -u
fi
echo "prs:END"
