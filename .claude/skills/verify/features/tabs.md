# Tabs

The user opens the tab switcher, sees one card per open tab with its title, opens new tabs, switches to and
closes tabs.

## Sub-features

- `tabs-open-switcher` the switcher opens from the toolbar and lists every tab.
- `tabs-card` each open page has a card titled with the page title.
- `tabs-new` "New Tab" opens the New Tab Page and increments the tab count.
- `tabs-switch` tapping a card returns to that page.
- `tabs-close` the card's close button removes it.
- `tabs-menu` the switcher's overflow menu (`popupMenuToolbarButton`).

## How to get to it (user POV)

- New Tab Page: the tab count button (`inputModeWidgetNavTabs`, its text is the count).
- Loaded page: the tab count button (`tabsMenu`, count in `tabCount`).
- Browser menu: "New Tab" (`newTabMenuItem`) opens a tab without the switcher.

## Driving it with verify.sh

Preconditions: baseline; `$V open https://privacy-test-pages.site/` and `$V wait id:browserWebView 20`.

- **Open switcher.** `$V record start tabs.mp4`, `$V tap id:tabsMenu`, `$V assert where TabSwitcherActivity`, `$V assert visible "text:Privacy Test Pages - Home"`, `$V shot tabs-switcher.png`.
- **New tab.** `$V tap id:newTabToolbarButton`, `$V wait id:inputField`, `$V assert text id:inputModeWidgetNavTabs 2`, `$V shot tabs-new.png`.
- **Switch.** `$V tap id:inputModeWidgetNavTabs`, `$V tap "text:Privacy Test Pages - Home"`, `$V wait id:browserWebView`, `$V web eval 'location.host'` returns `"privacy-test-pages.site"`.
- **Close.** In the switcher, `$V tap id:close --index 1` and `$V assert not-visible "text:Privacy Test Pages - Home"`. `$V record stop`.
- Many tabs: `$V seed tabs --count 20` before opening the switcher.

## Gotchas

- Switcher toolbar ids: `duckAIToolbarButton`, `fireToolbarButton`, `newTabToolbarButton`, `popupMenuToolbarButton`; back is `desc:Navigate up`.
- The "trackers blocked" panel (`item_container`) is the first tappable item; cards follow it, and every card's close button is `id:close`, so index by card order.
- A card's title can stay empty right after its page loads (seen on a Pixel 7 with Android 17: still empty after 10 s, while the emulator showed it). Assert the card count or the selected card rather than only its title text, and look at the screenshot.
- On the user's device, count tabs before the recipe and close the ones it opened; "New Tab" from a loaded page and `$V open` both add tabs.
- "New Tab" on a blank New Tab Page reuses that tab instead of adding one; open a page first when the recipe needs a second tab.
- Driven with trapdoor: `tabs-open-switcher`, `tabs-new` (2026-11-13); the rest with the earlier harness. Not yet re-driven with this version of the commands; on 2026-11-17 the tab switcher (`inputModeWidgetNavTabs`, `TabSwitcherActivity`) was driven with it as part of `ai-settings.md`.
