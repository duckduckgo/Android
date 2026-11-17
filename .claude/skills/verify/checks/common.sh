# Shared helpers for the check scripts (Common paths from features/README.md).
# Acts on VERIFY_SERIAL, else the device chosen with `verify.sh use`.
cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)" || exit 1
V=.claude/skills/verify/verify.sh
SERIAL="${VERIFY_SERIAL:-$(head -1 build/verify/state/devices 2>/dev/null)}"
ADB="adb -s $SERIAL"
pass=0; fail=0
check() { if eval "$2" >/dev/null 2>&1; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1"; fail=$((fail+1)); fi; }
to_browser() { local i; for i in 1 2 3 4 5 6; do [[ "$($V where)" == BrowserActivity ]] && return; [[ "$($V where)" == "-" ]] && { $V launch >/dev/null; continue; }; $V key back >/dev/null; sleep 1; done; }
menu() { $V assert visible id:inputModeUnifiedBack >/dev/null 2>&1 && ! $V assert visible id:inputModeWidgetNavMenu >/dev/null 2>&1 && { $V tap id:inputModeUnifiedBack >/dev/null; sleep 1; }
  if $V assert visible id:inputModeWidgetNavMenu >/dev/null 2>&1; then $V tap id:inputModeWidgetNavMenu >/dev/null; else $V assert visible id:browserMenu >/dev/null 2>&1 || $V scroll down >/dev/null; $V tap id:browserMenu >/dev/null; fi; $V wait id:settingsMenuItem 5 >/dev/null && return
  # right after a restart the first tap can land while the keyboard is still settling; try once more
  $V tap id:inputModeWidgetNavMenu >/dev/null 2>&1 || $V tap id:browserMenu >/dev/null 2>&1; $V wait id:settingsMenuItem 5 >/dev/null; }
new_tab() { to_browser; menu; $V tap id:newTabMenuItem >/dev/null; $V wait id:inputField 10 >/dev/null; sleep 1; }
settings() { to_browser; menu; $V tap id:settingsMenuItem >/dev/null; $V wait text:Settings 10 >/dev/null; sleep 1; }
# polls the attached page's host until it matches $1
wait_host() { local i; for i in $(seq 1 ${2:-20}); do [[ "$($V web eval 'location.host' 2>/dev/null)" == "\"$1\"" ]] && return 0; sleep 1; done; return 1; }
web_debugging_on() { settings; $V scroll up 3 >/dev/null; $V tap "text:Developer Settings" >/dev/null; $V wait text:"Web Content Debugging" 5 >/dev/null
  [[ "$($V checked trailingSwitch 3)" == true ]] || $V tap id:trailingSwitch --index 3 >/dev/null; sleep 1; to_browser; }
outside() { $ADB shell input tap 150 1500; }
# polls the Duck.ai page until the prompt word shows twice (prompt + reply)
wait_reply() { local i n; for i in $(seq 1 ${2:-30}); do n=$($V web eval "document.body.innerText.toLowerCase().split(\"$1\").length - 1" 2>/dev/null); [[ "${n:-0}" -ge 2 ]] && return 0; sleep 1; done; return 1; }
wait_duckai_attached() { local i; for i in $(seq 1 ${1:-30}); do $V web targets 2>/dev/null | grep -q 'visible  attached  https://duck.ai' && return 0; sleep 1; done; return 1; }
# a Duck.ai tab's menu has no Duck.ai shortcut, only "New Chat"
new_chat() { to_browser; menu; $V tap id:newDuckChatMenuItem >/dev/null 2>&1 || $V tap id:duckAiNewChatMenuItem >/dev/null; $V wait id:duckAIHeader 25 >/dev/null; wait_duckai_attached; }
# chat seeds refuse until Duck.ai storage has migrated; retry for a while and show the last answer
seed_chats() { local i out; for i in 1 2 3 4 5 6 7 8 9 10; do out=$($V seed duckai-chats "$@" 2>&1) && { echo "   seed: $out (try $i)"; return 0; }; sleep 3; done; echo "   seed failed: $out"; return 1; }
# on the privacy-test-pages tab, opening it if the current tab is something else
page() { to_browser; [[ "$($V web eval location.host 2>/dev/null)" == '"privacy-test-pages.site"' ]] && $V assert visible id:browserMenu >/dev/null 2>&1 && return; $V open https://privacy-test-pages.site/ >/dev/null; wait_host privacy-test-pages.site >/dev/null; sleep 1; }
# opens the browser menu and taps an entry, scrolling the sheet when the entry is further down
entry() { menu; local i; for i in 1 2 3; do $V assert visible id:$1 >/dev/null 2>&1 && break; $V scroll up >/dev/null; done; $V tap id:$1 >/dev/null; }
