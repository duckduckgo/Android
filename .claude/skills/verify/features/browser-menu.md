# Browser menu

The browser menu is a bottom sheet with a shortcut row (Back, New Tab, Duck.ai, Settings) and entries for
page actions, Bookmarks, Passwords, Downloads, Duck.ai, data clearing and privacy. Each entry hands off to
another feature; this file maps where each one lands.

## Sub-features

- `menu-open` the sheet opens from the New Tab Page, a loaded page and the tab switcher.
- `menu-destinations` every entry lands where its label says; the full map is the table below.

## How to get to it (user POV)

- New Tab Page: `inputModeWidgetNavMenu` ("More options"); 11 entries.
- Loaded page: `browserMenu`; 23 entries, a superset of the New Tab Page menu. The list scrolls: entries from Fireproof This Site down need `$V scroll up` inside the sheet.
- Tab switcher: `popupMenuToolbarButton` (a different menu).

## Driving it with verify.sh

Preconditions: baseline; a loaded page (`$V open https://privacy-test-pages.site/`).

- **Open.** `$V record start menu.mp4`, `$V tap id:browserMenu`, `$V wait id:settingsMenuItem`, `$V shot menu.png`.
- **Any entry.** Tap it by id and check where it lands against this table. An entry that opens a screen is checked with `$V assert where <Activity>`; one that stays in the browser (a sheet, a bar, the page itself) with `$V where` printing `BrowserActivity` plus the element named below.

| Entry id | Lands on |
|---|---|
| `backMenuItem` | the tab's previous page |
| `newTabMenuItem` | a new tab's New Tab Page, input focused (`inputField`) |
| `newDuckChatMenuItem`, `duckAiNewChatMenuItem` | Duck.ai in the tab, `duckAIHeader` (see `duck-ai.md`) |
| `duckAiNewVoiceChatMenuItem` | Duck.ai chat view (no voice UI on an emulator without a microphone) |
| `duckAiChatsMenuItem` | `ChatHistoryActivity` |
| `duckAiChatSettingsMenuItem` | a Duck.ai settings page in the tab |
| `settingsMenuItem` | `SettingsActivity` (AI Features is in `ai-settings.md`) |
| `refreshMenuItem` | the same page, reloaded |
| `addBookmarksMenuItem` | bookmark-added sheet for about 3 s |
| `bookmarksMenuItem` | `BookmarksActivity` |
| `sharePageMenuItem` | the Android share sheet: `$V where` prints `-` (system `ChooserActivity`) |
| `addToHomeMenuItem` | the launcher's add-to-home dialog (`$V where` prints `-`) |
| `findInPageMenuItem` | the find-in-page bar, keyboard up |
| `changeBrowsingModeMenuItem` | the page re-rendered as desktop site |
| `autofillMenuItem` | `AutofillManagementActivity` (Passwords) |
| `downloadsMenuItem` | `DownloadsActivity` |
| `fireproofWebsiteMenuItem` | the page; fireproofing shows in Settings > Data Clearing > Fireproof Sites |
| `fireMenuItem` | "Delete tabs and site data?" sheet (see `fire-button.md`) |
| `reportBrokenSiteMenuItem`, `privacyProtectionMenuItem` | `PrivacyDashboardHybridActivity` |
| `printPageMenuItem` | the system print spooler (`$V where` prints `-`) |
| `internalFeedbackMenuItem` | the internal feedback form in the tab |

## Gotchas

- System windows (share, add to home, print) are another app: `$V where` prints `-` even though the tap worked. Confirm with a screenshot, and `$V key back` to return.
- A page hides its toolbar once it scrolls; `$V scroll down` brings `browserMenu` back.
- `$V key back` closes the sheet; tapping outside it (`$V tap id:touch_outside`, when listed) dismisses it too.
- Several entries change state (desktop site, fireproof, fire, add bookmark). On an emulator the run booted, `$V reset` afterwards; on the user's device, undo each one.
- Driven with trapdoor on 2026-11-13: every loaded-page entry, once, with a screenshot; the table's screen names were then mapped to their activities from the code. Not yet re-driven with the android CLI; `settingsMenuItem`, `newTabMenuItem` and `newDuckChatMenuItem` were driven with it on 2026-11-17 as part of `ai-settings.md` and `duck-ai.md`.
