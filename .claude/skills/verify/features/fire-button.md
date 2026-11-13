# Fire button

The user clears all tabs and site data. After confirming, every tab closes and the app returns to a single
New Tab Page. Fireproofed sites keep their data.

## Sub-features

- `fire-dialog` the fire button opens "Delete tabs and site data?".
- `fire-confirm` "Delete All" closes all tabs and lands on a fresh New Tab Page.
- `fire-dismiss` tapping outside the sheet keeps the tabs.
- `fire-single-tab` the single-tab variant (`browser.single-tab-fire-sheet`).
- `fire-fireproof` fireproofed sites survive (`fire.fireproof.websites`, `seed fireproof-populated`).
- `fire-automatic` automatic data clearing settings (`fire.automatic.data.clearing`).

## How to get to it (user POV)

- Loaded page: flame icon (`fireIconMenu`).
- New Tab Page: flame icon (`inputModeWidgetNavFire`).
- Tab switcher: `fireToolbarButton`.
- Settings > Data clearing for the automatic options.

## Driving it with verify.sh

Preconditions: baseline; `$V t browse --url https://privacy-test-pages.site/`, `$V wait id:browserWebView 20`.

- **Open.** `$V t record start fire.mp4`, `$V t tap id:fireIconMenu`, `$V wait id:deleteAllPrimaryButton`, `$V t assert visible "text:Delete tabs and site data?"`, `$V t shot fire-dialog.png`.
- **Confirm.** `$V t tap id:deleteAllPrimaryButton`, `$V wait id:inputField 15`, `$V t assert text id:inputModeWidgetNavTabs 1`, `$V t shot fire-after.png`. Second view: `$V t tap id:inputModeWidgetNavTabs` and `$V t assert not-visible "text:Privacy Test Pages - Home"`. `$V t record stop`.
- **Dismiss.** Open the dialog, `$V t tap id:touch_outside`; `$V t assert visible id:browserWebView` and `$V web eval 'location.host'` is still `"privacy-test-pages.site"`.
- **Fireproof.** `$V t seed fireproof-populated`, `$V t goto fire.fireproof.websites` to see the list, then fire and confirm the list is unchanged.

## Gotchas

- The fire animation runs for a few seconds after "Delete All"; wait for `inputField` before asserting.
- `desc:Clear data` matches a non-clickable image too on a loaded page; use the ids.
- Driven with the earlier harness: `fire-dialog`, `fire-confirm` (2026-11-13). Not yet driven with trapdoor.
