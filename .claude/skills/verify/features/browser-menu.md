# Browser menu

The browser menu is a bottom sheet with a shortcut row (Back, Forward, New Tab, Duck.ai, Settings) and entries for
page actions, Bookmarks, Passwords, Downloads, Duck.ai, data clearing and privacy. Each entry hands off to
another feature; this file maps where each one lands.

## Sub-features

- `menu-open` the sheet opens from the New Tab Page, a loaded page and the tab switcher.
- `menu-destinations` every entry lands where its label says; the full map is the table below.

## How to get to it (user POV)

- New Tab Page: `inputModeWidgetNavMenu` ("More options"); 12 entries (the shortcut row, Bookmarks, Passwords, Downloads, the four Duck.ai entries, Send Internal Feedback). With the New Tab Page input unfocused (after its back arrow, `inputModeUnifiedBack`) the button is `browserMenu`, same menu.
- Loaded page: `browserMenu`; 23 entries, a superset of the New Tab Page menu. The list scrolls: entries from New Voice Chat down need one or two `$V scroll up` inside the sheet. Forward has no id in the layout while it is disabled.
- Tab switcher: `popupMenuToolbarButton` (a different menu).

## Driving it with verify.sh

Preconditions: baseline; a loaded page (`$V open https://privacy-test-pages.site/`, then poll `$V web eval 'location.host'`).

- **Open.** `$V record start menu.mp4`, `$V tap id:browserMenu`, `$V wait id:settingsMenuItem`, `$V shot menu.png`. Count the entries with `$V ui` (ids ending in `MenuItem`), scrolling the sheet with `$V scroll up` twice.
- **Any entry.** Reopen the menu on the page, scroll until `$V assert visible id:<entry>` holds, tap it, and check where it lands against this table. An entry that opens a screen is checked with `$V assert where <Activity>`; one that stays in the browser (a sheet, a bar, the page itself) with `$V where` printing `BrowserActivity` plus the element or `web eval` read named below. `$V record stop` every few entries, since a recording stops at 170 s.

| Entry id | Lands on |
|---|---|
| `backMenuItem` | the tab's previous page (`web eval 'location.host'`) |
| `newTabMenuItem` | a new tab's New Tab Page, input focused (`inputField`) |
| `newDuckChatMenuItem`, `duckAiNewChatMenuItem` | Duck.ai in the tab, `duckAIHeader` (see `duck-ai.md`) |
| `duckAiNewVoiceChatMenuItem` | Duck.ai in the tab with the "Voice chat with Duck.ai" sheet (web content, read with `web eval 'document.body.innerText'`); on a fresh install duck.ai's "Say hello to Duck.ai" terms sheet comes first |
| `duckAiChatsMenuItem` | `ChatHistoryActivity` |
| `duckAiChatSettingsMenuItem` | duck.ai's "Duck.ai Settings" modal in the tab (`https://duck.ai/`; Chats, DuckDuckGo Subscription, Appearance, About & Legal, Feedback) |
| `settingsMenuItem` | `SettingsActivity` (AI Features is in `ai-settings.md`) |
| `refreshMenuItem` | the same page, reloaded (a `window` marker set with `web eval` is gone) |
| `addBookmarksMenuItem` | bookmark-added sheet for about 3 s; the entry then reads "Edit Bookmark", and Bookmarks lists the page |
| `bookmarksMenuItem` | `BookmarksActivity` |
| `sharePageMenuItem` | the Android share sheet: `$V where` prints `-` (system `ChooserActivity`) |
| `addToHomeMenuItem` | the launcher's add-to-home dialog (`$V where` prints `-`; "Add to home screen" and "Cancel") |
| `findInPageMenuItem` | the find-in-page bar (`findInPageInput`, `closeFindInPagePanel`), keyboard up |
| `changeBrowsingModeMenuItem` | the page reloaded as desktop site: `web eval navigator.userAgent` drops "Mobile"; tapping it again restores it |
| `autofillMenuItem` | `AutofillManagementActivity` (Passwords) |
| `downloadsMenuItem` | `DownloadsActivity` |
| `fireproofWebsiteMenuItem` | the page; the site is listed in Settings > Data Clearing > Fireproof Sites and the entry reads "Remove Fireproofing" |
| `fireMenuItem` ("Delete Tabs and Data") | "Delete tabs and site data?" sheet (see `fire-button.md`) |
| `reportBrokenSiteMenuItem` | `PrivacyDashboardHybridActivity`, the breakage form (`web targets` shows `screen=breakageForm`) |
| `privacyProtectionMenuItem` ("Disable Privacy Protection") | protection off for the site, then `PrivacyDashboardHybridActivity` asking "Site not working? Let us know."; the entry then reads "Enable Privacy Protection" |
| `printPageMenuItem` | the system print preview (`$V where` prints `-`); two `$V key back` return |
| `internalFeedbackMenuItem` | the internal feedback form in a tab, which redirects to the company SSO sign-in (`login.microsoftonline.com`) on a device that is not signed in |

## Gotchas

- System windows (share, add to home, print) are another app: `$V where` prints `-` even though the tap worked. Confirm with a screenshot, and `$V key back` to return.
- A page hides its toolbar once it scrolls; `$V scroll down` brings `browserMenu` back.
- `$V key back` closes the sheet; tapping outside it (`$V tap id:touch_outside`, when listed) dismisses it too.
- Several entries change state (desktop site, fireproof, fire, add bookmark). On an emulator the run booted, `$V reset` afterwards; on the user's device, undo each one.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `cd1cf8877d`): `menu-open` (New Tab Page and loaded page counts) and every loaded-page entry in the table, as 26 checks that all passed. Not driven: the tab switcher's menu (in `tabs.md`), Forward, and anything past the first screen each entry opens (sharing, printing, sending a report or feedback).
