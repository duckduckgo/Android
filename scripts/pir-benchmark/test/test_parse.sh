#!/usr/bin/env bash
# Unit tests for parse.sh. Runs on macOS, no device required.
set -u

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=../lib/parse.sh
source "$DIR/../lib/parse.sh"

failures=0
assert_eq() {
  local expected="$1" actual="$2" msg="$3"
  if [ "$expected" != "$actual" ]; then
    echo "FAIL: $msg"
    echo "  expected: [$expected]"
    echo "  actual:   [$actual]"
    failures=$((failures + 1))
  else
    echo "PASS: $msg"
  fi
}

global_out="$(parse_global_pss < "$DIR/fixtures/global_meminfo.txt")"

assert_eq "198220|com.duckduckgo.mobile.android:pir|5678" \
  "$(echo "$global_out" | grep ':pir')" \
  "global: parses :pir process pss/name/pid"

assert_eq "96540|com.google.android.webview:sandboxed_process0:org.chromium.content.app.SandboxedProcessService0:0|9012" \
  "$(echo "$global_out" | grep 'sandboxed_process0')" \
  "global: parses sandboxed renderer process with colons in name"

assert_eq "412880|com.duckduckgo.mobile.android|4321" \
  "$(echo "$global_out" | grep -E '\|com\.duckduckgo\.mobile\.android\|')" \
  "global: parses main process, strips '/ activities' suffix and commas"

assert_eq "10120|webview_zygote|3001" \
  "$(echo "$global_out" | grep 'webview_zygote')" \
  "global: parses webview_zygote row (excluded from renderer aggregation, still emitted)"

detail_out="$(parse_detail_pss < "$DIR/fixtures/detail_meminfo.txt")"
assert_eq "45120|88760|23450|65430|198220" "$detail_out" \
  "detail: parses java|native|code|graphics|total from App Summary"

if [ "$failures" -ne 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "All parser tests passed"
