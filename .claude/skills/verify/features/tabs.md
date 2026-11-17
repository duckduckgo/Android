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

- New Tab Page: the tab count button (`inputModeWidgetNavTabs`, count in `tabCount`).
- Loaded page: the tab count button (`tabsMenu`, count in `tabCount`).
- Browser menu: "New Tab" (`newTabMenuItem`) opens a tab without the switcher.

## Driving it with verify.sh

Preconditions: baseline; `$V open https://privacy-test-pages.site/` and poll `$V web eval 'location.host'` until it returns `"privacy-test-pages.site"`.

- **Open switcher.** `$V record start tabs.mp4`, `$V tap id:tabsMenu`, `$V assert where TabSwitcherActivity`, `$V assert visible "text:Privacy Test Pages - Home"`, `$V text regularTabCount` reads `1`, `$V shot tabs-switcher.png`.
- **New tab.** `$V tap id:newTabToolbarButton`, `$V wait id:inputField`, `$V assert text id:tabCount 2` (the count is in `tabCount` on the New Tab Page too; `inputModeWidgetNavTabs` has no text), `$V shot tabs-new.png`.
- **Switch.** `$V tap id:inputModeWidgetNavTabs`; the switcher has two `close` buttons and a "New Tab" card. `$V tap "text:Privacy Test Pages - Home"`, `$V assert where BrowserActivity`, `$V web eval 'location.host'` returns `"privacy-test-pages.site"` and `$V text omnibarTextInput` holds the URL.
- **Close.** `$V tap id:tabsMenu`, `$V tap id:close --index 1`, `$V assert not-visible "text:Privacy Test Pages - Home"`; one `close` is left. Second view: `$V key back` and `$V assert text id:tabCount 1`.
- **Overflow menu.** In the switcher, `$V tap id:popupMenuToolbarButton`: `gridLayoutMenuItem`, `listLayoutMenuItem`, `selectTabsMenuItem` and `closeAllTabsMenuItem` ("Grid View" ticked, "List View", "Select Tabs", "Close All Tabs"). `$V key back` closes it. `$V record stop`.
- Many tabs: `$V seed tabs --count 20` before opening the switcher. It adds 20 untitled blank cards (a lone blank New Tab was dropped, so the count went 1 to 20; seeding again went to 40); `$V text tabCount` and `$V text regularTabCount` read it back.

## Gotchas

- Switcher toolbar ids: `duckAIToolbarButton`, `fireToolbarButton`, `newTabToolbarButton`, `popupMenuToolbarButton`; back is `desc:Navigate up`. The top segment switches between regular and Fire mode tabs (`regularSegment`, `fireSegment`); the regular count is `regularTabCount`.
- The "trackers blocked" panel (`item_container`) is the first tappable item; cards follow it, and every card's close button is `id:close`, so index by card order. Count cards with `$V text | grep -c "^close"`.
- A card's title can stay empty right after its page loads (seen on a Pixel 7 with Android 17: still empty after 10 s, while the emulator showed it). Assert the card count or the selected card rather than only its title text, and look at the screenshot.
- On the user's device, count tabs before the recipe and close the ones it opened; "New Tab" from a loaded page and `$V open` both add tabs.
- "New Tab" on a blank New Tab Page reuses that tab instead of adding one; open a page first when the recipe needs a second tab.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `cd1cf8877d`): `tabs-open-switcher`, `tabs-card`, `tabs-new`, `tabs-switch`, `tabs-close`, `tabs-menu` (opened and read, no entry tapped), plus the `tabs` seed, as 16 scripted checks that all passed. Not driven: the overflow entries themselves and Fire mode tabs (out of scope here).
