# Search and navigate

The user types a URL or a query in the native input on the New Tab Page or over a loaded page, picks an
autocomplete suggestion or presses enter, and the page or SERP loads in the current tab.

## Sub-features

- `nav-url` typing a domain and pressing enter loads that site.
- `nav-autocomplete` typing shows suggestion rows, and tapping one loads it.
- `nav-query` a non-URL query loads the DuckDuckGo SERP.
- `nav-from-page` tapping the omnibar on a loaded page reopens the input and navigates the same tab.

## How to get to it (user POV)

- New Tab Page: tap the search field (`inputField`).
- Loaded page: tap the address bar (`omnibarTextInputClickCatcher` covers the field).

## Driving it with verify.sh

Preconditions: baseline; the **New tab** path in the README.

- **URL.** `$V record start nav-url.mp4`, `$V type id:inputField privacy-test-pages.site`. The suggestion list fills in about a second: `$V text` shows the domain as a row (`phrase` on a fresh install, `url` under a `title` once the site is in history) and an "Ask Duck.ai" row (`message`). `$V shot nav-typed.png`, `$V key enter`. Poll `$V web eval 'location.host'` until it returns `"privacy-test-pages.site"`; `$V text omnibarTextInput` reads `http://privacy-test-pages.site/` and `$V assert where BrowserActivity` holds. `$V shot nav-loaded.png`, `$V record stop`. The screenshot shows the "Privacy Test Pages" heading and the URL in the address bar.
- **Autocomplete.** After typing, tap the row: `$V tap id:url` (history row) or `$V tap id:phrase` (fresh install). Same end state.
- **Query.** Type `what is a car` and `$V key enter`. `$V web eval 'location.host'` reaches `"duckduckgo.com"`, `$V web eval 'new URLSearchParams(location.search).get("q")'` is `"what is a car"`, and the shot shows results with the query in the address bar.
- **From a page.** On a loaded page, `$V tap id:omnibarTextInputClickCatcher`, `$V wait id:inputField`; the field holds the current URL, or the query on a SERP. `$V type id:inputField www.search-company.site`, `$V key enter`. `$V web eval 'location.host'` reaches `"www.search-company.site"` and `$V text tabCount` is unchanged.

## Gotchas

- On the New Tab Page the field is `inputField`; on a loaded page tap `omnibarTextInputClickCatcher` to open the input.
- `$V type` focuses, clears and types; read the field back with `$V text inputField` and assert on its effect (the suggestion rows). Don't `$V wait text:<the typed domain>`: the field itself carries that text, so it matches at once.
- `android layout` does not list `browserWebView`, so `$V wait id:browserWebView` times out on a loaded page. Prove the load with `web eval 'location.host'` (poll it) and `$V text omnibarTextInput`.
- While the New Tab Page input is focused, the nav bar (`inputModeWidgetNavMenu`) is hidden. `$V tap id:inputModeUnifiedBack` (the back arrow) unfocuses it and shows the plain address bar with `browserMenu`.
- `$V open` is setup only. It skips the input entirely, so it never proves this feature.
- On a loaded page, `android layout` also lists the page's own content; select toolbar elements by id, not by text, so a word on the page doesn't match first.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `725af5b466`): `nav-url`, `nav-autocomplete`, `nav-query`, `nav-from-page`, as 14 checks in `proofs/search_and_navigate.py` that all passed. Not driven: none.
