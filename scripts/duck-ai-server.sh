#!/usr/bin/env bash
# Inspect the duck.ai local HTTP server running inside the Android app.
#
# Usage:
#   ./scripts/duck-ai-server.sh                  # show all: settings, migration, chats
#   ./scripts/duck-ai-server.sh settings
#   ./scripts/duck-ai-server.sh migration
#   ./scripts/duck-ai-server.sh chats
#   ./scripts/duck-ai-server.sh chats/<chatId>
#   ./scripts/duck-ai-server.sh images
#   ./scripts/duck-ai-server.sh images/<uuid>
#   ./scripts/duck-ai-server.sh reset-migration  # reset done flag so JS migration runs again
#
# Requires: adb, curl, python3

set -euo pipefail

ORIGIN="https://duck.ai"
PORT=8765
ENDPOINT="${1:-all}"

# --- Help (no port needed) ---
if [[ "$ENDPOINT" == "help" || "$ENDPOINT" == "--help" || "$ENDPOINT" == "-h" ]]; then
  echo "Usage: ./scripts/duck-ai-server.sh [command]"
  echo ""
  echo "Commands:"
  echo "  (none)            Show all: settings, migration, chats"
  echo "  settings          GET /settings"
  echo "  migration         GET /migration"
  echo "  chats             GET /chats"
  echo "  chats/<chatId>    GET /chats/:chatId"
  echo "  images            GET /images (list metadata — uuid, chatId, dataSize)"
  echo "  images/<uuid>     GET /images/:uuid (full JSON including base64 data)"
  echo "  reset-migration   Reset migration flag (done=false) so JS migration runs again"
  echo "  help              Show this help"
  exit 0
fi

# --- Forward port ---
adb forward tcp:"$PORT" tcp:"$PORT" > /dev/null
echo "Using port $PORT (debug builds only)"
echo ""

# --- Query helper ---
query() {
  local path="$1"
  local label="${2:-$path}"
  echo "=== $label ==="
  local body
  body=$(curl -s -w "\n%{http_code}" -H "Origin: $ORIGIN" "http://127.0.0.1:$PORT/$path")
  local http_code="${body##*$'\n'}"
  body="${body%$'\n'*}"
  if [[ -z "$body" ]]; then
    echo "(empty response — HTTP $http_code)"
  else
    echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"
    [[ "$http_code" != 2* ]] && echo "(HTTP $http_code)"
  fi
  echo ""
}

# --- Mutate helper (no response body expected) ---
mutate() {
  local method="$1"
  local path="$2"
  local label="${3:-$method $path}"
  echo "=== $label ==="
  curl -sf -X "$method" -H "Origin: $ORIGIN" "http://127.0.0.1:$PORT/$path"
  echo "Done."
  echo ""
}

# --- Execute ---
case "$ENDPOINT" in
  all)
    query "settings"
    query "migration"
    query "chats"
    query "images"
    ;;
  settings|migration|chats|images)
    query "$ENDPOINT"
    ;;
  chats/*|images/*)
    query "$ENDPOINT"
    ;;
  reset-migration)
    mutate "DELETE" "migration" "reset migration (done=false)"
    query "migration"
    ;;
  *)
    query "$ENDPOINT"
    ;;
esac
