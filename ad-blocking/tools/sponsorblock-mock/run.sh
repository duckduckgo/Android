#!/usr/bin/env bash
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
adb_cmd=(adb)
[ -n "${SERIAL:-}" ] && adb_cmd+=( -s "$SERIAL" )
if [ "${NO_DEVICE:-0}" != 1 ]; then
  "${adb_cmd[@]}" reverse tcp:8765 tcp:8765
  trap '"${adb_cmd[@]}" reverse --remove tcp:8765' EXIT
fi
python3 -I "$here/server.py"
