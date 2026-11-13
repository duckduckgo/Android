# Browser menu

The browser menu is a bottom sheet with a shortcut row (Back, New Tab, Duck.ai, Settings) and entries for
page actions, Bookmarks, Passwords, Downloads, Duck.ai, data clearing and privacy. Each entry hands off to
another feature; this file maps where each one lands. Bookmarks themselves are in `bookmarks.md`.

## Sub-features

- `menu-open` the sheet opens from the New Tab Page, a loaded page and the tab switcher.
- `menu-destinations` every entry lands where its label says; the full map is the menu survey below.

## How to get to it (user POV)

- New Tab Page: `inputModeWidgetNavMenu` ("More options"); 11 entries.
- Loaded page: `browserMenu`; 23 entries, a superset of the New Tab Page menu. The list scrolls: entries from Fireproof This Site down need a swipe inside the sheet.
- Tab switcher: `popupMenuToolbarButton` (a different menu).

## Driving it with verify.sh

Preconditions: baseline; a loaded page (`$V t browse --url https://privacy-test-pages.site/`).

- **Open.** `$V t record start menu.mp4`, `$V t tap id:browserMenu`, `$V wait id:settingsMenuItem`, `$V t shot menu.png`.
- **Any entry.** Tap it by id and check where it lands against this table (from a tapped survey of every entry on 2026-11-13, raw output in `build/verify/survey/browser-menu/`):

| Entry id | Lands on |
|---|---|
| `backMenuItem` | the tab's previous page |
| `newTabMenuItem` | a new tab's New Tab Page, input focused |
| `newDuckChatMenuItem`, `duckAiNewChatMenuItem` | Duck.ai in the tab (see `duck-ai.md`) |
| `duckAiNewVoiceChatMenuItem` | Duck.ai chat view (no voice UI on an emulator without a microphone) |
| `duckAiChatsMenuItem` | `duckai.history` |
| `duckAiChatSettingsMenuItem` | a Duck.ai settings page in the tab |
| `settingsMenuItem` | `settings` (see `settings.md`) |
| `refreshMenuItem` | the same page, reloaded |
| `addBookmarksMenuItem` | bookmark-added sheet for about 3 s (see `bookmarks.md`) |
| `bookmarksMenuItem` | `bookmarks` |
| `sharePageMenuItem` | the Android share sheet (system `ChooserActivity`) |
| `addToHomeMenuItem` | the launcher's add-to-home dialog |
| `findInPageMenuItem` | the find-in-page bar, keyboard up |
| `changeBrowsingModeMenuItem` | the page re-rendered as desktop site |
| `autofillMenuItem` | Passwords (`AutofillManagementActivity`; not in trapdoor's catalog, `where` returns null) |
| `downloadsMenuItem` | `downloads.main` |
| `fireproofWebsiteMenuItem` | the page; fireproofing is not visible without opening `fire.fireproof.websites` |
| `fireMenuItem` | "Delete tabs and site data?" sheet (see `fire-button.md`) |
| `reportBrokenSiteMenuItem`, `privacyProtectionMenuItem` | the privacy dashboard (`privacy.dashboard.hybrid`) |
| `printPageMenuItem` | the system print spooler |
| `internalFeedbackMenuItem` | the internal feedback form in the tab |

## Gotchas

- `where` names only what trapdoor opened itself. Entries that show a sheet, a bar or web content in the browser read as `browser.main`, and system windows (share, add to home, print) make trapdoor report "current screen or surface is not registered" even though the tap worked. Check the foreground with `adb -s <serial> shell dumpsys window | grep mCurrentFocus` and a screenshot.
- `key back` closes the sheet; `tap id:touch_outside` dismisses it without leaving the screen behind.
- Several entries change state (desktop site, fireproof, fire, add bookmark). On an emulator the run booted, `$V t reset` afterwards; on the user's device, undo each one.
- Driven with trapdoor on 2026-11-13: every loaded-page entry, once, with a screenshot. Only `settingsMenuItem`, `bookmarksMenuItem` and `addBookmarksMenuItem` have full recipes with a second view.
