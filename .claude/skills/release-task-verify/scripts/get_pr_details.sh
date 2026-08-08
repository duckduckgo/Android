#!/bin/bash
for pr in "$@"; do
    title=$(gh pr view "$pr" --json title --jq ".title" 2>/dev/null)
    taskline=$(gh pr view "$pr" --json body --jq ".body" | grep -i "Task/Issue URL:" | head -1)
    gid=""
    if [ -n "$taskline" ]; then
        gid=$(echo "$taskline" | grep -oE "/task/[0-9]+" | head -1 | grep -oE "[0-9]+")
        if [ -z "$gid" ]; then
            gid=$(echo "$taskline" | grep -oE "app\.asana\.com/0/[0-9]+/[0-9]+" | head -1 | sed "s|.*/||")
        fi
    fi
    echo "PR:${pr}|GID:${gid}|TITLE:${title}"
done
