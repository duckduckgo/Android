# Fire button

The user clears all tabs and site data. After confirming, every tab closes and the app returns to a single
New Tab Page.

## Sub-features

- `fire-dialog` the fire button opens "Delete tabs and site data?".
- `fire-confirm` "Delete All" closes all tabs and lands on a fresh New Tab Page.
- `fire-dismiss` tapping outside the sheet keeps the tabs.

## How to get to it (user POV)

- Loaded page: flame icon in the toolbar (`fireIconMenu`).
- New Tab Page: flame icon (`inputModeWidgetNavFire`).
- Tab switcher: `fireToolbarButton`.

## Driving it with verify.sh

Preconditions: baseline, then load `privacy-test-pages.site`.

- **Open.** `$V tap --id fireIconMenu`, `$V wait --id deleteAllPrimaryButton`, `$V assert --text "Delete tabs and site data"`, `$V shot fire-dialog`.
- **Confirm.** `$V tap --id deleteAllPrimaryButton`, `$V wait --id inputField 15`, `$V assert --text "^1$"`, `$V assert --not --text "Privacy Test Pages"`, `$V shot fire-after`. Then open the switcher (`$V tap --id tabCount`) and confirm no `Privacy Test Pages - Home` card, the second view of the mutation.
- **Dismiss.** Open the dialog, `$V tap --id touch_outside`; the page is still loaded.

## Gotchas

- The fire animation runs for a few seconds after "Delete All"; wait for `inputField` before asserting.
- The flame button id depends on the screen; `$V tap --desc "Clear data"` works on the browser and New Tab Page but matches two elements on the browser (`fireIconImageView` is not clickable), so prefer the id.
