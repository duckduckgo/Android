#!/usr/bin/env bash
# Drives features/duck-ai.md (open, ask, pickers, history, seeded chats, one tier spot-check)
# with the android-CLI commands only (trapdoor for reset, chat seeds and the subscription seed).
source "$(dirname "$0")/common.sh"
$V reset >/dev/null 2>&1; $V launch >/dev/null; sleep 4; $V tap "text:Not Now" >/dev/null 2>&1
web_debugging_on
body_has() { $V web eval 'document.body.innerText' 2>/dev/null | grep -q "$1"; }
# popup rows are only in $V windows; rows share the x of the selected model's row
popup_rows() { local w x; w="$($V windows)"; x=$(awk -F'\t' -v m="$($V text modelPickerChip)" 'index($2, m) && $2 != m {split($1,c," "); print c[1]; exit}' <<<"$w")
  awk -F'\t' -v x="$x" '{split($1,c," ")} c[1]==x && !seen[$2]++ {print $2}' <<<"$w"; }

$V record start duck-ai-1-cli.mp4 >/dev/null
echo "== duckai-open"
new_tab
$V tap id:aiChatIconMenu >/dev/null; $V wait id:contextualChatMenuNewChat 5 >/dev/null
check "Start chat menu: New Chat and Chats" '$V assert visible id:contextualChatMenuNewChat && $V assert visible id:contextualChatMenuAllChats'
$V tap id:contextualChatMenuNewChat >/dev/null
check "duckAIHeader in the browser" '$V wait id:duckAIHeader 25 && $V assert where BrowserActivity'
check "duck.ai page visible attached" 'wait_duckai_attached'
check "header reads Free Plan" '[[ "$($V text duckAIFreePillTitle)" == "Free Plan" ]]'
$V shot duckai-open.png >/dev/null

echo "== duckai-ask"
$V type id:inputField "Reply with only the word pineapple" >/dev/null
check "Ask button appears" '$V wait id:nativeInputAskButton 5'
$V tap id:nativeInputAskButton >/dev/null
check "prompt and reply on the page" 'wait_reply pineapple'
check "URL carries chatID" '$V web eval location.search | grep -q chatID='
sleep 2; $V shot duckai-answered.png >/dev/null

echo "== duckai-history"
$V tap id:duckAiSidebar >/dev/null; sleep 2
check "Chat History opens" '$V assert where ChatHistoryActivity'
check "the chat is listed" '$V text | grep -qi "^chatHistoryTitle	.*pineapple"'
$V key back >/dev/null; sleep 2
check "back returns to the chat" '$V assert visible id:duckAIHeader'

echo "== open from the tab switcher"
$V tap id:tabsMenu >/dev/null; sleep 2
$V tap id:duckAIToolbarButton >/dev/null
check "switcher Duck.ai popup: New Chat and View All Chats" '$V wait id:contextualChatsPopupNewChat 5 && $V assert visible id:contextualChatsPopupViewAll'
$V shot duckai-switcher-popup.png >/dev/null
$V tap id:contextualChatsPopupNewChat >/dev/null
check "switcher Duck.ai button opens a chat" '$V wait id:duckAIHeader 25 && wait_duckai_attached'
$V record stop >/dev/null

$V record start duck-ai-2-cli.mp4 >/dev/null
echo "== duckai-pickers"
new_chat
$V tap id:inputField >/dev/null
check "model chip shown" '$V wait id:modelPickerChip 5'
$V tap id:modelPickerChip >/dev/null; sleep 2
models=$(popup_rows | wc -l | tr -d ' ')
echo "   model rows: $(popup_rows | tr '\n' ',')"
check "model picker lists models" '[[ $models -ge 3 ]]'
$V shot duckai-model-picker.png >/dev/null
outside; sleep 1
check "tap outside closes it, chat stays" '! $V windows | grep -q "Best for everyday use" && $V assert visible id:duckAIHeader'
$V tap id:inputField >/dev/null; $V wait id:reasoningModePickerButton 5 >/dev/null
$V tap id:reasoningModePickerButton >/dev/null; sleep 2
echo "   reasoning rows: $($V windows | cut -f2 | grep -xE "Fast|Reasoning|Extended Reasoning" | sort -u | tr '\n' ',')"
check "reasoning picker offers Fast and Reasoning" '$V windows | cut -f2 | grep -qx Fast && $V windows | cut -f2 | grep -qx Reasoning'
$V shot duckai-reasoning-picker.png >/dev/null
outside; sleep 1
check "reasoning popup closed" '! $V windows | cut -f2 | grep -qx Fast'

echo "== seeded chats"
check "seed text chats (2 pinned)" 'seed_chats --count 4 --pinned 2 --title Seed'
check "seed a voice chat" 'seed_chats --kind voice --count 1 --title Voice'
check "seed an image chat" 'seed_chats --kind image --count 1 --title Image'
$V tap id:duckAiSidebar >/dev/null; sleep 2
check "Pinned and Recent sections" '$V text | grep -q "^sectionHeaderText	Pinned" && $V text | grep -q "^sectionHeaderText	Recent"'
check "pinned chats first" '[[ "$($V text | awk -F"\t" "\$1==\"chatHistoryTitle\"{print \$2}" | head -2 | tr "\n" ,)" == "Seed 1,Seed 2," ]]'
$V shot duckai-history-seeded.png >/dev/null
$V tap "text:Voice 1" >/dev/null
check "voice chat opens, answers labelled Voice Chat" '$V wait id:duckAIHeader 25 && sleep 4 && body_has "Voice Chat"'
$V shot duckai-voice-chat.png >/dev/null
$V tap id:duckAiSidebar >/dev/null; sleep 2; $V tap "text:Image 1" >/dev/null
check "image chat opens with a seeded image" '$V wait id:duckAIHeader 25 && sleep 4 && body_has "Seeded image" && [[ $($V web eval "document.querySelectorAll(\"img\").length") -ge 1 ]]'
$V shot duckai-image-chat.png >/dev/null
$V seed duckai-chats-none >/dev/null; sleep 1
$V tap id:duckAiSidebar >/dev/null; sleep 2
check "clearing seeds keeps the real chat" '! $V text | grep -qE "chatHistoryTitle	(Seed|Voice|Image) " && $V text | grep -qi "^chatHistoryTitle	.*pineapple"'
$V key back >/dev/null
$V record stop >/dev/null

$V record start duck-ai-3-cli.mp4 >/dev/null
echo "== tier spot-check (plus)"
$V subscription --tier plus >/dev/null; sleep 3
new_chat
check "Plus header reads Duck.ai" '[[ "$($V text aiTitle)" == "Duck.ai" ]]'
$V tap id:inputField >/dev/null; $V wait id:modelPickerChip 5 >/dev/null
$V tap id:modelPickerChip >/dev/null; sleep 2
echo "   plus gated rows: $(popup_rows | grep "…$" | tr '\n' ',')"
check "gated rows end in …" '[[ $(popup_rows | grep -c "…$") -ge 1 ]]'
$V shot duckai-plus-model-picker.png >/dev/null
outside; sleep 1
$V subscription none >/dev/null; sleep 3
new_chat
check "back to Free Plan" '[[ "$($V text duckAIFreePillTitle)" == "Free Plan" ]]'
$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
