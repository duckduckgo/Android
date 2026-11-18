#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
adb_cmd=(adb)
port=8765
args=("$@")
for ((i=0; i<${#args[@]}; i++)); do
  if [ "${args[i]}" = --port ] && [ $((i + 1)) -lt ${#args[@]} ]; then
    port="${args[i+1]}"
  elif [[ "${args[i]}" == --port=* ]]; then
    port="${args[i]#--port=}"
  fi
done
[ -n "${SERIAL:-}" ] && adb_cmd+=( -s "$SERIAL" )
if [ "${NO_DEVICE:-0}" != 1 ]; then
  "${adb_cmd[@]}" reverse tcp:8765 "tcp:$port"
  trap '"${adb_cmd[@]}" reverse --remove tcp:8765' EXIT
fi
python3 -I "$here/server.py" "$@"
