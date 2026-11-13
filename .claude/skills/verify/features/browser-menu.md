# Browser menu and bookmarks

The browser menu is a bottom sheet with a shortcut row (New Tab, Duck.ai, Settings) and entries for
Bookmarks, Passwords, Downloads and Duck.ai chats. Bookmarks lets the user save the current page and
manage saved sites in folders.

## Sub-features

- `menu-open` the sheet opens from the toolbar.
- `menu-bookmarks` "Bookmarks" opens the bookmarks screen.
- `menu-add-bookmark` on a loaded page, the menu saves the page as a bookmark (`browser.bookmark-added-sheet`).
- `bookmarks-manage` folders, edit and delete (`bookmarks.add-folder-dialog`, `bookmarks.edit-saved-site-dialog`, `bookmarks.delete-undo-snackbar`).
- `menu-settings` the Settings shortcut opens Settings.
- `menu-duckai` Duck.ai entries; the chat itself is in `duck-ai.md`.

## How to get to it (user POV)

- New Tab Page: `inputModeWidgetNavMenu` ("More options").
- Loaded page: `browserMenu`.
- Tab switcher: `popupMenuToolbarButton`.

## Driving it with verify.sh

Preconditions: baseline.

- **Open.** `$V t goto browser.newtab`, `$V t record start menu.mp4`, `$V t tap id:inputModeWidgetNavMenu`, `$V t assert where browser.menu-sheet`, `$V t shot menu.png`. Items: `newTabMenuItem`, `newDuckChatMenuItem`, `settingsMenuItem`, `bookmarksMenuItem`, `autofillMenuItem`, `downloadsMenuItem`, `duckAiNewChatMenuItem`, `duckAiNewVoiceChatMenuItem`, `duckAiChatsMenuItem`, `duckAiChatSettingsMenuItem`, `internalFeedbackMenuItem`.
- **Bookmarks.** `$V t tap id:bookmarksMenuItem`, `$V t assert where bookmarks`. On a fresh install it shows the empty state.
- **Add bookmark.** `$V t browse --url https://privacy-test-pages.site/`, `$V t tap id:browserMenu`, `$V t hierarchy --interactive-only` to find the add-bookmark entry (the loaded-page menu differs from the New Tab Page one), tap it, `$V t assert where browser.bookmark-added-sheet`. Second view: open Bookmarks and `$V t assert visible "text:Privacy Test Pages - Home"`.
- **Manage.** `$V t seed bookmarks-populated` (12 bookmarks, 2 folders, 3 favorites), `$V t goto bookmarks`, then drive the folder and edit flows from the hierarchy.
- **Settings.** `$V t tap id:settingsMenuItem`, `$V t assert where settings`.

## Gotchas

- `key back` closes the sheet; `tap id:touch_outside` dismisses it without leaving the screen behind.
- Driven with trapdoor: `menu-open` (hierarchy read, 2026-11-13). Add-bookmark and manage steps have not been driven yet; correct their selectors the first time.
