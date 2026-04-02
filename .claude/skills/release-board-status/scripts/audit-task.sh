#!/usr/bin/env bash
# Usage: bash audit-task.sh GID
# Finds PRs referencing an Asana GID and checks which release they landed in.
GID=$1

git fetch --tags 2>/dev/null

# Search GitHub for PRs that reference this GID
prs=$(gh pr list --search "$GID" --state all --json number,state --jq '.[] | "\(.number) \(.state)"' 2>/dev/null)

if [ -z "$prs" ]; then
  echo "result:NO_PR_FOUND"
  exit 0
fi

echo "$prs" | while read -r number state; do
  if [ "$state" = "MERGED" ]; then
    commit=$(git log --oneline --all | grep -E "#${number}([^0-9]|$)" | head -1 | awk '{print $1}')
    if [ -n "$commit" ]; then
      first_release=$(git tag --sort=creatordate | grep -E "^5\.[0-9]+\.[0-9]+$" | while read -r tag; do
        if git merge-base --is-ancestor "$commit" "$tag" 2>/dev/null; then
          echo "$tag"
          break
        fi
      done)
      if [ -n "$first_release" ]; then
        echo "pr:$number state:MERGED released_in:$first_release"
      else
        echo "pr:$number state:MERGED released_in:PENDING commit:$commit"
      fi
    else
      echo "pr:$number state:MERGED released_in:COMMIT_NOT_FOUND"
    fi
  else
    echo "pr:$number state:$state"
  fi
done
