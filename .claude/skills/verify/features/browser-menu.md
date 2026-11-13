# Browser menu

The overflow menu is a bottom sheet with navigation shortcuts (Back, Forward, New Tab, Duck.ai, Settings)
and entries for Bookmarks, Passwords, Downloads and Duck.ai chats.

## Sub-features

- `menu-open` the sheet opens from the toolbar.
- `menu-bookmarks` "Bookmarks" opens the bookmarks screen.
- `menu-add-bookmark` on a loaded page, the menu adds the page as a bookmark.
- `menu-duckai` "New Chat" opens Duck.ai; the chat itself is covered in `duck-ai.md`.
- `menu-settings` the Settings shortcut opens Settings.

## How to get to it (user POV)

- New Tab Page: `inputModeWidgetNavMenu` ("More options").
- Loaded page: `browserMenu`.
- Tab switcher: `popupMenuToolbarButton`.

## Driving it with verify.sh

Preconditions: baseline.

- **Open.** `$V tap --id inputModeWidgetNavMenu`, `$V wait --id bookmarksMenuItem`, `$V shot menu`. Items: `bookmarksMenuItem`, `autofillMenuItem`, `downloadsMenuItem`, `duckAiNewChatMenuItem`, `duckAiChatsMenuItem`, `duckAiChatSettingsMenuItem`, plus the top row `backMenuItem`, `newTabMenuItem`, `newDuckChatMenuItem`, `settingsMenuItem`.
- **Bookmarks.** `$V tap --id bookmarksMenuItem`; on a fresh install the screen shows "No bookmarks added yet".
- **Add bookmark.** Load `privacy-test-pages.site`, `$V tap --id browserMenu`, `$V ui` to find the add bookmark entry (the loaded-page menu differs from the New Tab Page one), tap it, then reopen the menu, open Bookmarks and `$V assert --text "Privacy Test Pages - Home"`.
- **Duck.ai.** See `duck-ai.md`.
- **Settings.** `$V tap --id settingsMenuItem`, `$V ui` shows the Settings list.

## Gotchas

- Only `menu-open` has been driven with this harness so far; the other recipes come from `.maestro/bookmarks` and `.maestro/unified_input_screen`. Correct their ids here the first time you drive them.
- `KEYCODE_BACK` closes the sheet; tap `touch_outside` to dismiss without leaving a screen behind it.
