#!/usr/bin/env bash
# PreToolUse(Bash) hook: force ./gradlew runs to quiet logging so build output
# stays small enough for an agent to read. A deliberate log-level flag always wins.
# No-ops silently if jq is missing, leaving the command untouched.
set -uo pipefail

input=$(cat)
cmd=$(printf '%s' "$input" | jq -r '.tool_input.command // empty' 2>/dev/null)

if [[ -z "$cmd" ]]; then
  echo '{}'
  exit 0
fi

if [[ ! "$cmd" =~ (^|[[:space:]])\./gradlew($|[[:space:]]) ]]; then
  echo '{}'
  exit 0
fi

if [[ "$cmd" =~ (^|[[:space:]])(-q|-w|-i|-d|--quiet|--warn|--info|--debug)($|[[:space:]]) ]]; then
  echo '{}'
  exit 0
fi

new_cmd=$(printf '%s' "$cmd" | sed -E 's#\./gradlew#./gradlew -q#g')
encoded=$(printf '%s' "$new_cmd" | jq -Rs .)
printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","updatedInput":{"command":%s}}}\n' "$encoded"
