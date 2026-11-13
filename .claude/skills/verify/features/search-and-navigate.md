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

Preconditions: baseline; `$V t goto browser.newtab`.

- **URL.** `$V t record start nav-url.mp4`, `$V t tap id:inputField`, `$V t type id:inputField privacy-test-pages.site`, `$V wait text:privacy-test-pages.site 10` (the suggestion row), `$V t shot nav-typed.png`, `$V t key enter`. Then `$V wait id:browserWebView 20`, `$V t assert where browser.main`, `$V web eval 'location.host'` returns `"privacy-test-pages.site"`, `$V t shot nav-loaded.png`, `$V t record stop`. The screenshot shows the "Privacy Test Pages" heading and the URL in the address bar.
- **Autocomplete.** After typing, `$V t hierarchy --interactive-only` lists the suggestion rows (the input itself is not listed while suggestions show); `$V t tap text:privacy-test-pages.site`. Same end state.
- **Query.** Type `what is a car` and `key enter`. The shot shows DuckDuckGo results and `$V web eval 'location.host'` is `"duckduckgo.com"`.
- **From a page.** On a loaded page, `$V t tap id:omnibarTextInputClickCatcher`, `$V wait id:inputField`, `$V t type id:inputField www.search-company.site`, `key enter`. `$V web eval 'location.host'` returns `"www.search-company.site"` and `tabsMenu` text (the tab count) is unchanged.

## Gotchas

- trapdoor's hierarchy lists tappable elements only. The address bar text field and the suggestions container never appear in it; assert the loaded page with `where`, `browserWebView` and `web eval`, and the suggestions by their row text.
- On the New Tab Page the field is `inputField`; on a loaded page tap `omnibarTextInputClickCatcher` to open the input.
- `$V t type` replaces the field's text; trapdoor reports only the length, so assert on its effect (the suggestion rows) and check the `nav-typed.png` shot.
- `browse --url` is setup only. It skips the input entirely, so it never proves this feature.
- Driven with trapdoor: `nav-url` (2026-11-13, on the `ddg_verify_api34` emulator and on a Pixel 7 with Android 17). The other sub-features were driven with the earlier android-CLI harness only.
