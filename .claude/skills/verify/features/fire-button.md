# Fire button

The user clears all tabs and site data. After confirming, every tab closes and the app returns to a single
New Tab Page. Fireproofed sites keep their data.

## Sub-features

- `fire-dialog` the fire button opens "Delete tabs and site data?".
- `fire-confirm` "Delete All" closes all tabs and lands on a fresh New Tab Page.
- `fire-dismiss` tapping outside the sheet keeps the tabs.
- `fire-single-tab` the single-tab variant ("Delete This Tab", `deleteThisTabButton`).
- `fire-fireproof` fireproofed sites survive (Settings > Data Clearing > Fireproof Sites, `seed fireproof-populated`).
- `fire-automatic` automatic data clearing settings (Settings > Data Clearing).
- `fire-duckai-chat` in a Duck.ai tab, the flame next to the chat input asks "Delete this chat?" and deletes only that chat.

## How to get to it (user POV)

- Loaded page: flame icon (`fireIconMenu`).
- New Tab Page: flame icon (`inputModeWidgetNavFire`).
- Tab switcher: `fireToolbarButton`.
- Duck.ai tab: `inputFieldFireIconMenu`, left of the chat input.
- Settings > Data clearing for the automatic options.

## Driving it with verify.sh

Preconditions: baseline; `$V open https://privacy-test-pages.site/`, `$V wait id:browserWebView 20`.

- **Open.** `$V record start fire.mp4`, `$V tap id:fireIconMenu`, `$V wait id:deleteAllPrimaryButton`, `$V assert visible "text:Delete tabs and site data?"`, `$V shot fire-dialog.png`.
- **Confirm.** `$V tap id:deleteAllPrimaryButton`, `$V wait id:inputField 15`, `$V assert text id:inputModeWidgetNavTabs 1`, `$V shot fire-after.png`. Second view: `$V tap id:inputModeWidgetNavTabs` and `$V assert not-visible "text:Privacy Test Pages - Home"`. `$V record stop`.
- **Dismiss.** Open the dialog, `$V tap id:touch_outside`; `$V assert visible id:browserWebView` and `$V web eval 'location.host'` is still `"privacy-test-pages.site"`.
- **Duck.ai chat.** Start a chat with the `duck-ai.md` Ask recipe and note `$V web eval 'location.search'` (it carries `chatID=`). Before: `$V tap id:duckAiSidebar`, count rows with the chat's title (`$V text | grep -c <title>`), `$V shot duckai-history-before.png`, `$V key back`. Then `$V tap id:inputFieldFireIconMenu`, `$V assert visible "text:Delete this chat?"`, `$V shot fire-dialog-duckai.png`, `$V tap id:deleteThisTabButton` ("Delete Chat"). After about 5 s the tab shows a fresh Duck.ai chat: `location.search` has no `chatID`, and `tabsMenu` keeps its count (the tab is replaced, not closed). Second view: Chat History has no row with that title and other chats are still listed; `$V web eval` over the `savedAIChatData` IndexedDB finds 0 occurrences of the chat ID.
- **Fireproof.** `$V seed fireproof-populated`, Settings > `$V tap "text:Data Clearing"` > Fireproof Sites to see the list, then fire and confirm the list is unchanged.

## Gotchas

- The fire animation runs for a few seconds after "Delete All"; wait for `inputField` before asserting.
- `desc:Clear data` matches a non-clickable image too on a loaded page; use the ids.
- "Delete Chat" is safe on the user's device: per-domain clearing skips DuckDuckGo domains ([ClearPersonalDataAction.kt:221](../../../../app/src/main/java/com/duckduckgo/app/global/view/ClearPersonalDataAction.kt)) and the chat is deleted by its URL ([DataClearing.kt:127](../../../../app/src/main/java/com/duckduckgo/app/fire/DataClearing.kt)). "Delete All" is not; it closes every tab.
- On the New Tab Page the fire button is `inputModeWidgetNavFire` and opens a sheet with "Delete This Tab" (`deleteThisTabButton`) and "Delete All" (`deleteAllSecondaryButton`), then a confirmation; on a loaded page `fireIconMenu` opens `deleteAllPrimaryButton` directly (seen 2026-11-16).
- Driven with the earlier harness: `fire-dialog`, `fire-confirm` (2026-11-13); with trapdoor: `fire-duckai-chat` (2026-11-13, Pixel 7 with Android 17). Not yet re-driven with this version of the commands; the New Tab Page's Delete All was driven with it on 2026-11-16 during the PR 10544 run.
