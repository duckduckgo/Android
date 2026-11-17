#!/usr/bin/env bash
# Drives features/browser-menu.md with the android-CLI commands only (trapdoor for reset).
source "$(dirname "$0")/common.sh"
$V reset >/dev/null 2>&1; $V launch >/dev/null; sleep 4; $V tap "text:Not Now" >/dev/null 2>&1
web_debugging_on
body_has() { $V web eval 'document.body.innerText' 2>/dev/null | grep -q "$1"; }
menu_ids() { $V ui | awk -F'\t' '$2 ~ /MenuItem$/ {print $2}'; }
all_menu_ids() { menu; { menu_ids; $V scroll up >/dev/null; menu_ids; $V scroll up >/dev/null; menu_ids; } | sort -u; $V key back >/dev/null; sleep 1; }

$V record start browser-menu-1-cli.mp4 >/dev/null
echo "== menu-open"
new_tab
check "New Tab Page menu: 12 entries" '[[ $(all_menu_ids | wc -l) -eq 12 ]]'
page
menu; $V shot menu-page.png >/dev/null; $V key back >/dev/null; sleep 1
check "loaded page menu: 23 entries" '[[ $(all_menu_ids | wc -l) -eq 23 ]]'

echo "== shortcut row and Duck.ai entries"
$V type id:omnibarTextInputClickCatcher x >/dev/null 2>&1; $V type id:inputField www.search-company.site >/dev/null; $V key enter >/dev/null; wait_host www.search-company.site >/dev/null
entry backMenuItem
check "back: previous page" 'wait_host privacy-test-pages.site 10'
before=$($V text tabCount)
entry newTabMenuItem
check "new tab: focused input, count +1" '$V wait id:inputField 5 && [[ $($V text tabCount) -eq $((before+1)) ]]'
page; entry newDuckChatMenuItem
check "Duck.ai shortcut: duckAIHeader" '$V wait id:duckAIHeader 25'
page; entry duckAiNewChatMenuItem
check "New Chat: duckAIHeader" '$V wait id:duckAIHeader 25'
page; entry duckAiNewVoiceChatMenuItem
check "New Voice Chat: terms sheet or voice sheet" '$V wait id:duckAIHeader 25 && sleep 4 && { body_has "Say hello to Duck.ai" || body_has "Voice chat with Duck.ai"; }'
$V shot menu-voice.png >/dev/null; $V key back >/dev/null
page; entry duckAiChatsMenuItem
check "Chats: ChatHistoryActivity" 'sleep 2; $V assert where ChatHistoryActivity'
page; entry duckAiChatSettingsMenuItem
check "Duck.ai Settings: web settings modal" '$V wait id:duckAIHeader 25 && sleep 3 && body_has "Duck.ai Settings"'
$V shot menu-duckai-settings.png >/dev/null
page; entry settingsMenuItem
check "Settings: SettingsActivity" 'sleep 2; $V assert where SettingsActivity'
$V record stop >/dev/null

$V record start browser-menu-2-cli.mp4 >/dev/null
echo "== page entries"
page; $V web eval 'window.__verifyMarker = 1' >/dev/null
entry refreshMenuItem; sleep 3
check "Refresh: same page, reloaded" 'wait_host privacy-test-pages.site && [[ "$($V web eval "typeof window.__verifyMarker")" == "\"undefined\"" ]]'
page; entry addBookmarksMenuItem; sleep 1
$V shot menu-bookmark-added.png >/dev/null; sleep 3
entry bookmarksMenuItem
check "Bookmarks: BookmarksActivity lists the page" 'sleep 2; $V assert where BookmarksActivity && $V assert visible "text:Privacy Test Pages - Home"'
page; entry sharePageMenuItem
check "Share: system sheet, app not in front" 'sleep 2; [[ "$($V where)" == "-" ]]'
$V shot menu-share.png >/dev/null; $V key back >/dev/null; sleep 1
page; entry addToHomeMenuItem
check "Add to Home: launcher dialog" 'sleep 2; [[ "$($V where)" == "-" ]] && $V assert visible "text:Add to home screen"'
$V tap text:Cancel >/dev/null; sleep 1
page; entry findInPageMenuItem
check "Find in Page: find bar" '$V wait id:findInPageInput 5 && $V assert visible id:closeFindInPagePanel'
$V tap id:closeFindInPagePanel >/dev/null; sleep 1
page; entry changeBrowsingModeMenuItem; sleep 4
check "Desktop Site: desktop user agent" '! $V web eval navigator.userAgent | grep -q Mobile'
entry changeBrowsingModeMenuItem; sleep 4
check "Mobile Site restores the mobile user agent" '$V web eval navigator.userAgent | grep -q Mobile'
page; entry autofillMenuItem
check "Passwords: AutofillManagementActivity" 'sleep 2; $V assert where AutofillManagementActivity'
page; entry downloadsMenuItem
check "Downloads: DownloadsActivity" 'sleep 2; $V assert where DownloadsActivity'
$V record stop >/dev/null

$V record start browser-menu-3-cli.mp4 >/dev/null
echo "== state and privacy entries"
page; entry fireproofWebsiteMenuItem; sleep 2
settings; $V scroll up >/dev/null; $V tap "text:Data Clearing" >/dev/null; sleep 2; $V tap "text:Fireproof Sites" >/dev/null; sleep 2
check "Fireproof This Site: listed in Fireproof Sites" '$V assert visible text:privacy-test-pages.site'
page; menu; $V scroll up >/dev/null; $V scroll up >/dev/null; $V ui | awk -F'\t' '$2=="fireproofWebsiteMenuItem"{print "   fireproof entry now:", $4}'; $V tap id:fireproofWebsiteMenuItem >/dev/null; sleep 2
page; entry fireMenuItem
check "Delete Tabs and Data: fire sheet" '$V wait id:dialogTitle 5 && [[ "$($V text dialogTitle)" == "Delete tabs and site data?" ]]'
$V tap id:touch_outside >/dev/null; sleep 1
page; entry reportBrokenSiteMenuItem
check "Report Broken Site: dashboard breakage form" 'sleep 3; $V assert where PrivacyDashboardHybridActivity && $V web targets | grep -q "screen=breakageForm"'
$V key back >/dev/null; sleep 1
page; entry privacyProtectionMenuItem; sleep 3
check "Disable Privacy Protection: breakage prompt" '$V assert where PrivacyDashboardHybridActivity && body_has "Site not working"'
$V shot menu-privacy-protection.png >/dev/null; $V key back >/dev/null; sleep 1
page; menu; $V scroll up >/dev/null; $V scroll up >/dev/null
check "entry now reads Enable Privacy Protection" '$V ui | grep -q "privacyProtectionMenuItem	-	Enable Privacy Protection"'
$V tap id:privacyProtectionMenuItem >/dev/null; sleep 3
page; entry printPageMenuItem
check "Print: system print spooler" 'sleep 3; [[ "$($V where)" == "-" ]]'
$V shot menu-print.png >/dev/null; $V key back >/dev/null; sleep 1; $V key back >/dev/null; sleep 1
page; entry internalFeedbackMenuItem
check "Send Internal Feedback: SSO-gated form in a tab" 'wait_host login.microsoftonline.com 30 && $V assert where BrowserActivity'
$V shot menu-internal-feedback.png >/dev/null
$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
