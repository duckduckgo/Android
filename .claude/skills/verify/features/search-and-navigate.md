# Search and navigate

The user types a URL or a query in the native input on the New Tab Page or over a loaded page, picks an
autocomplete suggestion or presses enter, and the page or SERP loads in the current tab.

## Sub-features

- `nav-url` typing a full domain and pressing enter loads that site.
- `nav-autocomplete` typing shows `autoCompleteSuggestionsList`, and tapping an entry loads it.
- `nav-query` a non-URL query loads the DuckDuckGo SERP.
- `nav-from-page` tapping the omnibar on a loaded page reopens the input and navigates the same tab.

## How to get to it (user POV)

- New Tab Page: tap the search field (`inputField`).
- Loaded page: tap the address bar (`omnibarTextInput`).

## Driving it with verify.sh

Preconditions: baseline from `README.md`.

- **URL.** `$V tap --id inputField`, `$V type "privacy-test-pages.site"`, `$V wait --id autoCompleteSuggestionsList`, `$V shot nav-typed`, `$V key KEYCODE_ENTER`. Then `$V wait --text "privacy-test-pages.site" 20` and `$V assert --id omnibarTextInput`, `$V assert --not --id ddgLogo`, `$V shot nav-loaded`. The screenshot shows the "Privacy Test Pages" heading.
- **Autocomplete.** After typing, `$V tap --text "privacy-test-pages.site"` inside the list instead of enter (use `$V ui` to pick the suggestion row, not the input text). Same end state.
- **Query.** Type `what is a car` and press enter. `omnibarTextInput` shows the query and the shot shows DuckDuckGo results.
- **From a page.** On a loaded page, `$V tap --id omnibarTextInput`, `$V wait --id inputField`, type `www.search-company.site`, enter. `omnibarTextInput` text becomes `search-company.site` and `tabCount` stays `1`.

## Gotchas

- `browserLayout` never appears in `android layout`; prove the load with `omnibarTextInput` text plus `$V web eval 'location.href'` or the screenshot.
- On the New Tab Page the address field id is `inputField`; once a page loads it is `omnibarTextInput`. The legacy `omnibarTextInputClickCatcher` overlays it, so tap by `omnibarTextInput` centre.
- `$V type` escapes spaces only; avoid `&` and quotes in queries.
