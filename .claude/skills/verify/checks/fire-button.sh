#!/usr/bin/env bash
# Drives features/fire-button.md with the android-CLI commands only (trapdoor for reset and seeds).
source "$(dirname "$0")/common.sh"
$V reset >/dev/null 2>&1; $V launch >/dev/null; sleep 4; $V tap "text:Not Now" >/dev/null 2>&1
web_debugging_on
$V seed fireproof-populated >/dev/null
$V open https://www.search-company.site/ >/dev/null; wait_host www.search-company.site >/dev/null
$V open https://privacy-test-pages.site/ >/dev/null; wait_host privacy-test-pages.site >/dev/null
$V record start fire-button-cli.mp4 >/dev/null
tabs0=$($V text tabCount)

echo "== fire-dialog (loaded page)"
$V tap id:fireIconMenu >/dev/null; $V wait id:dialogTitle 5 >/dev/null
check "title Delete tabs and site data?" '[[ "$($V text dialogTitle)" == "Delete tabs and site data?" ]]'
check "Delete This Tab and Delete All offered" '$V assert visible id:deleteThisTabButton && $V assert visible id:deleteAllSecondaryButton'
$V shot fire-dialog.png >/dev/null

echo "== fire-dismiss"
$V tap id:touch_outside >/dev/null; sleep 1
check "sheet closed" '$V assert not-visible id:dialogTitle'
check "page kept" 'wait_host privacy-test-pages.site 3'
check "tab count kept" '[[ "$($V text tabCount)" == "$tabs0" ]]'

echo "== fire-single-tab"
$V tap id:fireIconMenu >/dev/null; $V wait id:deleteThisTabButton 5 >/dev/null
$V tap id:deleteThisTabButton >/dev/null; sleep 4
check "tab replaced by a New Tab Page" '$V wait id:inputField 10 && $V assert visible id:inputModeWidgetNavFire'
check "tab count unchanged" '[[ "$($V text tabCount)" == "$tabs0" ]]'
$V tap id:inputModeWidgetNavTabs >/dev/null; sleep 2
check "switcher: page card gone" '$V assert not-visible "text:Privacy Test Pages - Home"'
check "switcher: other tab kept" '$V text | grep -q "^title	Ad Click Flow"'
$V shot fire-single-tab-switcher.png >/dev/null
$V key back >/dev/null; sleep 1

echo "== fire-confirm (New Tab Page, Delete All)"
to_browser
$V assert visible id:inputModeWidgetNavFire >/dev/null 2>&1 || $V tap id:inputModeUnifiedBack >/dev/null
$V tap id:inputModeWidgetNavFire >/dev/null 2>&1 || $V tap id:fireIconMenu >/dev/null
$V wait id:deleteAllSecondaryButton 5 >/dev/null
$V shot fire-dialog-ntp.png >/dev/null
$V tap id:deleteAllSecondaryButton >/dev/null; sleep 1
$V shot fire-animation.png >/dev/null
check "fresh New Tab Page" '$V wait id:inputField 15'
sleep 2
check "one tab left" '$V assert text id:tabCount 1'
$V shot fire-after.png >/dev/null
$V tap id:inputModeWidgetNavTabs >/dev/null; sleep 2
check "switcher: one New Tab card" '[[ $($V text | grep -c "^close	") == 1 ]] && $V assert visible "text:New Tab"'
check "switcher: earlier pages gone" '! $V text | grep -qE "Ad Click Flow|Privacy Test Pages"'
$V key back >/dev/null; sleep 1

echo "== fire-fireproof / fire-automatic"
settings; $V scroll up >/dev/null; $V tap "text:Data Clearing" >/dev/null; sleep 2
check "at Data Clearing" '$V assert where DataClearingSettingsActivity'
check "1 site excluded" '$V text | grep -q "1 site excluded from clearing"'
check "automatic deletion Off" '$V text | grep -A1 "Automatically Delete Data" | grep -q Off'
$V tap "text:Fireproof Sites" >/dev/null; sleep 2
check "fireproofed site survives Delete All" '$V assert where FireproofWebsitesActivity && $V assert visible text:example.com'
$V shot fire-fireproof-after.png >/dev/null
$V key back >/dev/null; sleep 1
$V tap "text:Automatically Delete Data" >/dev/null; sleep 2
check "automatic deletion screen opens" '$V assert where AutomaticDataClearingSettingsActivity'
$V record stop >/dev/null

echo "== fire-duckai-chat"
to_browser
$V record start fire-duckai-cli.mp4 >/dev/null
$V tap id:aiChatIconMenu >/dev/null; $V wait id:contextualChatMenuNewChat 5 >/dev/null; $V tap id:contextualChatMenuNewChat >/dev/null
$V wait id:duckAIHeader 25 >/dev/null; wait_duckai_attached >/dev/null
seed_chats --count 2 --title Keep
$V type id:inputField "Reply with only the word pineapple" >/dev/null; $V tap id:nativeInputAskButton >/dev/null
check "reply arrived" 'wait_reply pineapple'
check "URL carries chatID" '$V web eval location.search | grep -q chatID='
tabsd=$($V text tabCount)
$V tap id:duckAiSidebar >/dev/null; sleep 2
title=$($V text | awk -F'\t' '$1=="chatHistoryTitle" && $2 !~ /^Keep /{print $2; exit}')
echo "   chat title: $title"
check "history before: 2 seeded + the new chat" '[[ $($V text | grep -c "^chatHistoryTitle") == 3 && -n "$title" ]]'
$V shot fire-duckai-history-before.png >/dev/null
$V key back >/dev/null; sleep 2
$V tap id:inputFieldFireIconMenu >/dev/null; $V wait id:dialogTitle 5 >/dev/null
check "dialog Delete this chat?" '[[ "$($V text dialogTitle)" == "Delete this chat?" ]] && [[ "$($V text deleteThisTabButton)" == "Delete Chat" ]]'
$V shot fire-dialog-duckai.png >/dev/null
$V tap id:deleteThisTabButton >/dev/null; sleep 7
check "fresh chat, no chatID" '$V assert visible id:duckAIHeader && ! $V web eval location.search | grep -q chatID'
check "tab replaced, count kept" '[[ "$($V text tabCount)" == "$tabsd" ]]'
$V tap id:duckAiSidebar >/dev/null; sleep 2
check "history after: chat gone" '! $V text | grep -qF "$title"'
check "history after: seeded chats kept" '[[ $($V text | grep -c "^chatHistoryTitle	Keep ") == 2 ]]'
$V shot fire-duckai-history-after.png >/dev/null
$V key back >/dev/null
$V seed duckai-chats-none >/dev/null

$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
