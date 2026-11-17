#!/usr/bin/env bash
# Drives features/ai-settings.md with the android-CLI commands only (trapdoor just for reset).
cd "$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)" || exit 1
V=.claude/skills/verify/verify.sh
pass=0; fail=0
check() { if eval "$2" >/dev/null 2>&1; then echo "PASS $1"; pass=$((pass+1)); else echo "FAIL $1"; fail=$((fail+1)); fi; }
on_ntp_menu() { $V tap id:inputModeWidgetNavMenu >/dev/null; $V wait id:settingsMenuItem 5 >/dev/null; }
to_browser() { local i; for i in 1 2 3 4 5; do [[ "$($V where)" == BrowserActivity ]] && return; [[ "$($V where)" == "-" ]] && { $V launch >/dev/null; continue; }; $V key back >/dev/null; sleep 1; done; }
new_tab() { to_browser; if $V assert visible id:inputModeWidgetNavMenu >/dev/null 2>&1; then $V tap id:inputModeWidgetNavMenu >/dev/null; else $V scroll down >/dev/null; $V tap id:browserMenu >/dev/null; fi
  $V wait id:newTabMenuItem 5 >/dev/null; $V tap id:newTabMenuItem >/dev/null; $V wait id:inputField 10 >/dev/null; sleep 1; }
settings() { new_tab; on_ntp_menu; $V tap id:settingsMenuItem >/dev/null; $V wait text:General 10 >/dev/null; }
ai_features() { settings; $V scroll up >/dev/null; $V wait id:includeDuckChatSetting 5 >/dev/null; $V tap id:includeDuckChatSetting >/dev/null; $V wait id:duckChatSettingsTitle 10 >/dev/null; }
cards() { $V scroll up >/dev/null; $V wait id:duckAiInputScreenWithAiContainer 5 >/dev/null; }
ntp_placeholder() { $V text inputField; }

$V reset >/dev/null 2>&1; $V launch >/dev/null
$V tap "text:Not Now" >/dev/null 2>&1 || true
$V record start ai-settings-cli.mp4 >/dev/null

echo "== Search Only (default)"
new_tab
check "placeholder is Search" '[[ "$(ntp_placeholder)" == "Search" ]]'
check "no Search/Duck.ai tabs" '! $V text | grep -q "^tab_title"'
check "start-chat button shown" '$V assert visible id:aiChatIconMenu'
$V shot ai-cli-ntp-search-only.png >/dev/null

echo "== Toggle between Search and Duck.ai"
ai_features
check "at AI Features" '$V assert where DuckChatSettingsActivity'
cards; $V tap id:duckAiInputScreenWithAiContainer >/dev/null; sleep 2
check "New Tab Toggle Position row appears" '$V wait id:duckAiDefaultTogglePosition 5'
$V shot ai-cli-settings-with-ai.png >/dev/null
new_tab
check "placeholder is Search or enter address" '[[ "$(ntp_placeholder)" == "Search or enter address" ]]'
check "tabs Search and Duck.ai" '[[ "$($V text | awk -F"\t" "\$1==\"tab_title\"{printf \"%s,\", \$2}")" == "Search,Duck.ai," ]]'
check "nav bar back button" '$V assert visible id:inputModeWidgetNavBack'
check "no start-chat button" '$V assert not-visible id:aiChatIconMenu'
$V shot ai-cli-ntp-search-and-duckai.png >/dev/null
settings; $V tap text:General >/dev/null; sleep 2
check "General shows Chat Suggestions" '$V text | grep -q "Chat Suggestions"'

echo "== New Tab Toggle Position = Duck.ai"
ai_features; cards; $V tap id:duckAiDefaultTogglePosition >/dev/null; $V wait text:Save 5 >/dev/null
$V tap "text:Duck.ai" >/dev/null; $V tap text:Save >/dev/null; sleep 1
check "row reads Duck.ai" '$V text | grep -A1 "New Tab Toggle Position" | grep -q "Duck.ai"'
to_browser; $V open https://privacy-test-pages.site/ >/dev/null; sleep 5
new_tab
sleep 2
check "new tab opens on the Duck.ai tab" '$V text | grep -qE "^tab_title.Duck.ai.*selected"'
check "Duck.ai placeholder" '[[ "$(ntp_placeholder)" == "Ask anything privately…" ]]'

echo "== Back to Search Only"
ai_features; cards; $V tap id:duckAiInputScreenWithoutAiContainer >/dev/null; sleep 2
check "toggle position row gone" '$V assert not-visible id:duckAiDefaultTogglePosition'
new_tab
check "placeholder back to Search" '[[ "$(ntp_placeholder)" == "Search" ]]'

echo "== Duck.ai off"
ai_features
check "Duck.ai switch starts on" '[[ "$($V checked trailingSwitch 1)" == true ]]'
$V tap id:trailingSwitch --index 1 >/dev/null; sleep 2
check "Duck.ai switch now off" '[[ "$($V checked trailingSwitch 1)" == false ]]'
check "mode cards hidden" '$V assert not-visible id:duckAiInputScreenWithAiContainer'
$V shot ai-cli-duckai-off.png >/dev/null
new_tab
check "no start-chat button" '$V assert not-visible id:aiChatIconMenu'
on_ntp_menu
check "menu has no Duck.ai items" '! $V ui | cut -f2 | grep -qiE "duck|chat"'
$V key back >/dev/null; sleep 1
ai_features; $V tap id:trailingSwitch --index 1 >/dev/null; sleep 1
check "Duck.ai back on" '[[ "$($V checked trailingSwitch 1)" == true ]]'

echo "== Shortcuts"
cards; $V tap id:duckAiShortcuts >/dev/null; sleep 2
check "at Shortcuts" '$V assert where DuckAiShortcutSettingsActivity'
$V tap id:trailingSwitch --index 1 >/dev/null; $V tap id:trailingSwitch --index 2 >/dev/null; sleep 1
check "Browser Menu and Address Bar off" '[[ "$($V checked trailingSwitch 1)$($V checked trailingSwitch 2)" == falsefalse ]]'
new_tab
check "Address Bar off: no start-chat button" '$V assert not-visible id:aiChatIconMenu'
on_ntp_menu
check "Browser Menu off: no Duck.ai items" '! $V ui | cut -f2 | grep -qiE "duck|chat"'
$V key back >/dev/null; sleep 1
$V tap id:inputModeWidgetNavTabs >/dev/null; sleep 2
check "tab switcher has no Duck.ai button" '$V assert not-visible id:duckAIToolbarButton'
$V key back >/dev/null; sleep 1
ai_features; cards; $V tap id:duckAiShortcuts >/dev/null; sleep 2
$V tap id:trailingSwitch --index 1 >/dev/null; $V tap id:trailingSwitch --index 2 >/dev/null; sleep 1
check "shortcuts restored" '[[ "$($V checked trailingSwitch 1)$($V checked trailingSwitch 2)" == truetrue ]]'

echo "== Use DuckDuckGo Without AI"
ai_features; cards; $V scroll up >/dev/null; $V tap id:duckAINoAiItem >/dev/null; sleep 2
check "Search Assist Never" '$V text | grep -A1 "Search Assist" | grep -q Never'
check "AI images hidden On" '$V text | grep -A1 "Hide AI-Generated Images" | grep -q "On"'
check "Without AI row disabled" '$V text | grep "Use DuckDuckGo Without AI" | grep -qv enabled'
$V shot ai-cli-without-ai.png >/dev/null

$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
