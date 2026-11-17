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

- **URL.** `$V record start nav-url.mp4`, `$V type id:inputField privacy-test-pages.site`, `$V wait text:privacy-test-pages.site 10` (the suggestion row), `$V shot nav-typed.png`, `$V key enter`. Then `$V wait id:browserWebView 20`, `$V assert where BrowserActivity`, `$V web eval 'location.host'` returns `"privacy-test-pages.site"`, `$V shot nav-loaded.png`, `$V record stop`. The screenshot shows the "Privacy Test Pages" heading and the URL in the address bar.
- **Autocomplete.** After typing, `$V ui` lists the suggestion rows; `$V tap text:privacy-test-pages.site`. Same end state.
- **Query.** Type `what is a car` and `$V key enter`. The shot shows DuckDuckGo results and `$V web eval 'location.host'` is `"duckduckgo.com"`.
- **From a page.** On a loaded page, `$V tap id:omnibarTextInputClickCatcher`, `$V wait id:inputField`, `$V type id:inputField www.search-company.site`, `$V key enter`. `$V web eval 'location.host'` returns `"www.search-company.site"` and `$V text tabCount` is unchanged.

## Gotchas

- On the New Tab Page the field is `inputField`; on a loaded page tap `omnibarTextInputClickCatcher` to open the input.
- `$V type` focuses, clears and types; read the field back with `$V text inputField` and assert on its effect (the suggestion rows).
- `$V open` is setup only. It skips the input entirely, so it never proves this feature.
- On a loaded page, `android layout` also lists the page's own content; select toolbar elements by id, not by text, so a word on the page doesn't match first.
- Driven with trapdoor: `nav-url` (2026-11-13, on the `ddg_verify_api34` emulator and on a Pixel 7 with Android 17); the other sub-features with the earlier android-CLI harness. Not yet re-driven with this version of the commands; on 2026-11-17 `$V type` into `inputField` was checked on its own (it typed `duck ai & privacy` exactly).
