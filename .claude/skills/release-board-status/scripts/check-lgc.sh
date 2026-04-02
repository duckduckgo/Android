#!/usr/bin/env bash
# Usage: bash check-lgc.sh <lgc_tag> <commit1> <commit2> ...
# Outputs one line per commit: "<commit>:IN_LGC" or "<commit>:AFTER_LGC"
lgc_tag=$1
shift
for commit in "$@"; do
  git merge-base --is-ancestor "$commit" "$lgc_tag" 2>/dev/null && echo "$commit:IN_LGC" || echo "$commit:AFTER_LGC"
done
