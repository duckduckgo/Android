# Tabs

The user opens the tab switcher, sees one card per open tab with its title, opens new tabs, switches to and
closes tabs.

## Sub-features

- `tabs-open-switcher` the switcher opens from the toolbar and shows "N Tab(s)".
- `tabs-card` each open page has a card titled with the page title.
- `tabs-new` "New Tab" opens the New Tab Page and increments `tabCount`.
- `tabs-switch` tapping a card returns to that page.
- `tabs-close` the card's close button removes it.

## How to get to it (user POV)

- Tap the tab count button in the toolbar (`tabCount`, on both the New Tab Page and a loaded page).
- Overflow menu, "New Tab" (`newTabMenuItem`) for a new tab without the switcher.

## Driving it with verify.sh

Preconditions: baseline, then load `privacy-test-pages.site` as in `search-and-navigate.md`.

- **Open switcher.** `$V tap --id tabCount`, `$V wait --text "Privacy Test Pages" 10`, `$V assert --text "^1 Tab$"`, `$V shot tabs-switcher`. The card `title` reads `Privacy Test Pages - Home`.
- **New tab.** `$V tap --id newTabToolbarButton`, `$V wait --id inputField`, then `$V assert --text "^2$"` (the `tabCount` text).
- **Switch.** Back in the switcher, `$V tap --text "Privacy Test Pages - Home"`; `omnibarTextInput` reads `privacy-test-pages.site`.
- **Close.** In the switcher, `$V tap --id close 0` on the first card; `$V assert --not --text "Privacy Test Pages - Home"`.

## Gotchas

- Switcher toolbar ids: `duckAIToolbarButton`, `fireToolbarButton`, `newTabToolbarButton`, `popupMenuToolbarButton`; back is the unlabelled "Navigate up" element.
- The "trackers blocked" info panel (`infoPanelText`) sits above the cards and shifts their positions; never tap cards by coordinate.
