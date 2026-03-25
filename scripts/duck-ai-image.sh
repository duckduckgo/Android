#!/usr/bin/env bash
# Extract a duck.ai stored image from the device and open it on macOS.
#
# Usage:
#   ./scripts/duck-ai-image.sh              # list available images (uuid, chatId, dataSize)
#   ./scripts/duck-ai-image.sh <uuid>       # extract image and open in Preview
#
# Requires: adb, curl, python3

set -euo pipefail

ORIGIN="https://duck.ai"
PORT=8765
UUID="${1:-}"

adb forward tcp:"$PORT" tcp:"$PORT" > /dev/null

if [[ -z "$UUID" ]]; then
  echo "Available images:"
  body=$(curl -s -w "\n%{http_code}" -H "Origin: $ORIGIN" "http://127.0.0.1:$PORT/images")
  http_code="${body##*$'\n'}"
  body="${body%$'\n'*}"
  if [[ -z "$body" ]]; then
    echo "(empty response — HTTP $http_code)"
  else
    echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"
  fi
  exit 0
fi

echo "Fetching image $UUID..."
tmpjson=$(mktemp)
curl -s -H "Origin: $ORIGIN" "http://127.0.0.1:$PORT/images/$UUID" > "$tmpjson"

python3 - "$tmpjson" <<'EOF'
import json, base64, re, sys, tempfile, os, subprocess

with open(sys.argv[1]) as f:
    body = f.read().strip()

if not body:
    print("ERROR: empty response — is the app running and rebuilt?", file=sys.stderr)
    sys.exit(1)

try:
    obj = json.loads(body)
except json.JSONDecodeError as e:
    print(f"ERROR: invalid JSON: {e}\n{body[:200]}", file=sys.stderr)
    sys.exit(1)

data_url = obj.get("data", "")
if not data_url:
    print(f"ERROR: no data field — server said: {body[:200]}", file=sys.stderr)
    sys.exit(1)

match = re.match(r"data:([^;]+);base64,(.+)", data_url, re.DOTALL)
if not match:
    print(f"ERROR: unexpected data URL format: {data_url[:80]}", file=sys.stderr)
    sys.exit(1)

mime_type = match.group(1)
ext = mime_type.split("/")[-1]
raw = base64.b64decode(match.group(2))

tmp = tempfile.NamedTemporaryFile(suffix=f".{ext}", delete=False)
tmp.write(raw)
tmp.close()

print(f"uuid:   {obj.get('uuid', '?')}")
print(f"chatId: {obj.get('chatId', '?')}")
print(f"type:   {mime_type}  ({len(raw):,} bytes)")
print(f"file:   {tmp.name}")
subprocess.run(["open", tmp.name])
EOF

rm -f "$tmpjson"
