#!/bin/bash
VERSION=$1
if ! git tag -l | grep -q "^${VERSION}$"; then
    echo "error:Version tag ${VERSION} not found"
    exit 1
fi
PREV=$(git tag --sort=-creatordate | grep -E "^5\.[0-9]+\.[0-9]+$" | grep -A1 "^${VERSION}$" | tail -1)
if [ -z "$PREV" ] || [ "$PREV" = "$VERSION" ]; then
    echo "error:Could not determine previous version"
    exit 1
fi
echo "version:${VERSION}"
echo "prev_version:${PREV}"
COMMITS=$(git log ${PREV}..${VERSION} --oneline | grep -vE "Merge branch .(release|hotfix)/|Updated .* for new release|Merge pull request .* from .*release")
echo "commits:START"
echo "$COMMITS"
echo "commits:END"
echo "commit_count:$(echo "$COMMITS" | grep -c .)"
echo "prs:START"
echo "$COMMITS" | grep -oE "#[0-9]+" | tr -d "#" | sort -u
echo "prs:END"
echo "pr_count:$(echo "$COMMITS" | grep -oE "#[0-9]+" | tr -d "#" | sort -u | grep -c .)"
