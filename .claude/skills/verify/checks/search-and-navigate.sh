#!/usr/bin/env bash
# Drives features/search-and-navigate.md with the android-CLI commands only.
source "$(dirname "$0")/common.sh"
$V record start search-and-navigate-cli.mp4 >/dev/null

echo "== nav-url"
new_tab
$V type id:inputField privacy-test-pages.site >/dev/null
sleep 2
check "suggestion row reads the domain" '$V text | grep -qE "^(phrase|url)	privacy-test-pages.site"'
check "Ask Duck.ai row offered" '$V text | grep -q "^message	Ask Duck.ai"'
$V shot snav-typed.png >/dev/null
$V key enter >/dev/null
check "page host is privacy-test-pages.site" 'wait_host privacy-test-pages.site'
check "address bar shows the URL" '$V text omnibarTextInput | grep -q privacy-test-pages.site'
check "still in BrowserActivity" '$V assert where BrowserActivity'
$V shot snav-loaded.png >/dev/null

echo "== nav-autocomplete"
new_tab
$V type id:inputField privacy-test-pages.site >/dev/null; sleep 2
$V shot snav-autocomplete.png >/dev/null
$V tap id:url >/dev/null || $V tap id:phrase >/dev/null
check "tapped suggestion loads the page" 'wait_host privacy-test-pages.site'

echo "== nav-query"
new_tab
$V type id:inputField "what is a car" >/dev/null; $V key enter >/dev/null
check "SERP host" 'wait_host duckduckgo.com 25'
check "SERP query" '$V web eval "new URLSearchParams(location.search).get(\"q\")" | grep -q "what is a car"'
sleep 2; $V shot snav-serp.png >/dev/null

echo "== nav-from-page"
before=$($V text tabCount)
$V assert visible id:omnibarTextInputClickCatcher >/dev/null 2>&1 || $V scroll down >/dev/null
$V tap id:omnibarTextInputClickCatcher >/dev/null
check "input opens with the current query" '$V wait id:inputField 5 && $V text inputField | grep -q "what is a car"'
$V type id:inputField www.search-company.site >/dev/null; $V key enter >/dev/null
check "same tab navigates" 'wait_host www.search-company.site'
check "tab count unchanged" '[[ "$($V text tabCount)" == "$before" ]]'
sleep 2; $V shot snav-from-page.png >/dev/null

$V record stop >/dev/null
echo "RESULT pass=$pass fail=$fail"
