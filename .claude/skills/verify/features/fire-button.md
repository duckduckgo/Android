# Fire button

The user clears all tabs and site data. After confirming, every tab closes and the app returns to a single
New Tab Page. Fireproofed sites keep their data.

## Sub-features

- `fire-dialog` the fire button opens "Delete tabs and site data?".
- `fire-confirm` "Delete All" closes all tabs and lands on a fresh New Tab Page, with no second confirmation.
- `fire-dismiss` tapping outside the sheet keeps the tabs.
- `fire-single-tab` "Delete This Tab" (`deleteThisTabButton`) replaces the current tab with a fresh New Tab Page; the other tabs stay.
- `fire-fireproof` fireproofed sites survive (Settings > Data Clearing > Fireproof Sites, `seed fireproof-populated`).
- `fire-automatic` automatic data clearing settings (Settings > Data Clearing > Automatically Delete Data).
- `fire-duckai-chat` in a Duck.ai tab, the flame next to the chat input asks "Delete this chat?" and deletes only that chat.

## How to get to it (user POV)

- Loaded page: flame icon (`fireIconMenu`).
- New Tab Page: flame icon (`inputModeWidgetNavFire`).
- Tab switcher: `fireToolbarButton`.
- Duck.ai tab: `inputFieldFireIconMenu`, left of the chat input.
- Settings > Data clearing for the automatic options.

## Driving it with verify.sh

Preconditions: baseline; `$V seed fireproof-populated`; `$V open https://www.search-company.site/` then `$V open https://privacy-test-pages.site/` (two tabs, titled "Ad Click Flow" and "Privacy Test Pages - Home"), polling `$V web eval 'location.host'` after each.

- **Open.** `$V record start fire.mp4`, `$V tap id:fireIconMenu`, `$V wait id:dialogTitle`; `$V text dialogTitle` is "Delete tabs and site data?" and the sheet offers `deleteThisTabButton` ("Delete This Tab") and `deleteAllSecondaryButton` ("Delete All"). `$V shot fire-dialog.png`.
- **Dismiss.** `$V tap id:touch_outside`; `$V assert not-visible id:dialogTitle`, `$V web eval 'location.host'` is still `"privacy-test-pages.site"` and `$V text tabCount` is unchanged.
- **Delete This Tab.** Open the sheet, `$V tap id:deleteThisTabButton`, `$V wait id:inputField 10`: the tab is now a New Tab Page and `tabCount` is unchanged. Second view: `$V tap id:inputModeWidgetNavTabs`; no "Privacy Test Pages - Home" card, "Ad Click Flow" still listed.
- **Delete All.** From the New Tab Page, `$V tap id:inputModeWidgetNavFire`, `$V wait id:deleteAllSecondaryButton`, `$V tap id:deleteAllSecondaryButton`. The fire animation runs (the screen is mostly black and `android layout` returns nothing for a moment), then `$V wait id:inputField 15` and `$V assert text id:tabCount 1`, `$V shot fire-after.png`. Second view: the switcher has one "New Tab" card and neither earlier page. `$V record stop`.
- **Fireproof and automatic.** Settings, `$V scroll up`, `$V tap "text:Data Clearing"` (`DataClearingSettingsActivity`): "Fireproof Sites" reads "1 site excluded from clearing" and "Automatically Delete Data" reads "Off". `$V tap "text:Fireproof Sites"` (`FireproofWebsitesActivity`) still lists `example.com` after Delete All. Back, then `$V tap "text:Automatically Delete Data"` opens `AutomaticDataClearingSettingsActivity`.
- **Duck.ai chat.** Open a chat (`$V tap id:aiChatIconMenu`, `$V tap id:contextualChatMenuNewChat`, `$V wait id:duckAIHeader 25`), wait for `$V web targets` to show duck.ai `visible attached`, then `$V seed duckai-chats --count 2 --title Keep` (it can refuse for about 15 s after a reset; retry). Ask with the `duck-ai.md` Ask recipe; `$V web eval 'location.search'` gains `chatID=`. Before: `$V tap id:duckAiSidebar`; Chat History lists the new chat (its generated title, such as "Pineapple") above "Keep 1" and "Keep 2"; `$V shot duckai-history-before.png`, `$V key back`. Then `$V tap id:inputFieldFireIconMenu`: `dialogTitle` "Delete this chat?" and one button, `deleteThisTabButton` "Delete Chat". `$V shot fire-dialog-duckai.png`, `$V tap id:deleteThisTabButton`. After about 5 s the tab shows a fresh Duck.ai chat: `location.search` has no `chatID`, and `tabCount` keeps its count (the tab is replaced, not closed). Second view: Chat History has no row with that title and still lists "Keep 1" and "Keep 2". `$V seed duckai-chats-none` cleans up.

## Gotchas

- The fire animation runs for a few seconds after "Delete All" and `android layout` can return nothing during it; wait for `inputField` before asserting.
- `desc:Clear data` matches a non-clickable image too on a loaded page; use the ids.
- The sheet is the same from a loaded page (`fireIconMenu`) and the New Tab Page (`inputModeWidgetNavFire`), with one tab or many: "Delete This Tab" and "Delete All" (`deleteAllSecondaryButton`), no confirmation after either. `deleteAllPrimaryButton` was not seen on 2026-11-17.
- `android layout` does not list `browserWebView`; prove a page is still there with `web eval 'location.host'`.
- Data Clearing has a "Delete Duck.ai Chats" switch (off after a reset) that makes the Fire Button delete Duck.ai history too; Delete All was driven with it off.
- A chat's history title is generated: right after Ask it can read as the prompt, later as a short title ("Pineapple", "Pineapple prompt"). Read it from Chat History rather than hard-coding it.
- "Delete Chat" is safe on the user's device: per-domain clearing skips DuckDuckGo domains ([ClearPersonalDataAction.kt:221](../../../../app/src/main/java/com/duckduckgo/app/global/view/ClearPersonalDataAction.kt)) and the chat is deleted by its URL ([DataClearing.kt:127](../../../../app/src/main/java/com/duckduckgo/app/fire/DataClearing.kt)). "Delete All" is not; it closes every tab.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `cd1cf8877d`): `fire-dialog`, `fire-dismiss`, `fire-single-tab`, `fire-confirm`, `fire-fireproof`, `fire-automatic` (opened only), `fire-duckai-chat`, as 26 checks that all passed. Not driven: the automatic deletion options themselves, the "Delete Duck.ai Chats" switch, and an IndexedDB read of the deleted chat ID.
