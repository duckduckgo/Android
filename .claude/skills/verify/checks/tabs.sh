#!/usr/bin/env bash
# Drives features/tabs.md with the android-CLI commands only (trapdoor for reset and the tabs seed).
source "$(dirname "$0")/common.sh"
$V reset >/dev/null 2>&1; $V launch >/dev/null; sleep 4; $V tap "text:Not Now" >/dev/null 2>&1
web_debugging_on
$V open https://privacy-test-pages.site/ >/dev/null; wait_host privacy-test-pages.site >/dev/null
$V record start tabs-cli.mp4 >/dev/null
PTP="text:Privacy Test Pages - Home"

echo "== tabs-open-switcher / tabs-card"
$V tap id:tabsMenu >/dev/null; sleep 2
check "at the switcher" '$V assert where TabSwitcherActivity'
check "card titled with the page" '$V assert visible "$PTP"'
check "one card, one close button" '[[ $($V text | grep -c "^close	") == 1 ]]'
check "regular tab count 1" '[[ "$($V text regularTabCount)" == 1 ]]'
$V shot tabs-switcher.png >/dev/null

echo "== tabs-new"
$V tap id:newTabToolbarButton >/dev/null
check "New Tab Page opens" '$V wait id:inputField 5 && $V assert where BrowserActivity'
check "tab count 2" '$V assert text id:tabCount 2'
$V shot tabs-new.png >/dev/null

echo "== tabs-switch"
$V tap id:inputModeWidgetNavTabs >/dev/null; sleep 2
check "two cards" '[[ $($V text | grep -c "^close	") == 2 ]]'
check "New Tab card listed" '$V assert visible "text:New Tab"'
$V shot tabs-switcher-two.png >/dev/null
$V tap "$PTP" >/dev/null; sleep 2
check "back in the browser" '$V assert where BrowserActivity'
check "on the page tab" 'wait_host privacy-test-pages.site 5 && $V text omnibarTextInput | grep -q privacy-test-pages.site'

echo "== tabs-close"
$V tap id:tabsMenu >/dev/null; sleep 2
$V tap id:close --index 1 >/dev/null; sleep 2
check "page card gone" '$V assert not-visible "$PTP"'
check "one card left" '[[ $($V text | grep -c "^close	") == 1 ]]'
$V shot tabs-closed.png >/dev/null
$V key back >/dev/null; sleep 2
check "second view: toolbar count 1" '$V assert text id:tabCount 1'

echo "== tabs-menu"
$V tap id:inputModeWidgetNavTabs >/dev/null; sleep 2
$V tap id:popupMenuToolbarButton >/dev/null; sleep 1
check "overflow lists layout, select, close all" '$V assert visible id:listLayoutMenuItem && $V assert visible id:selectTabsMenuItem && $V assert visible id:closeAllTabsMenuItem'
$V shot tabs-menu.png >/dev/null
$V key back >/dev/null; sleep 1

echo "== many tabs (seed)"
$V seed tabs --count 20 >/dev/null; sleep 3
sleep 2; to_browser
check "toolbar count reads 20 or more" '[[ $($V text tabCount) -ge 20 ]]'
$V tap id:inputModeWidgetNavTabs >/dev/null 2>&1 || $V tap id:tabsMenu >/dev/null; sleep 2
check "switcher regular count matches" '[[ $($V text regularTabCount) -ge 20 ]]'
$V shot tabs-many.png >/dev/null

$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
