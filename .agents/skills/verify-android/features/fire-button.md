# Fire button

The user clears all tabs and site data. After confirming, every tab closes and the app returns to a single
New Tab Page. Fireproofed sites keep their data.

## Sub-features

- `fire-dialog` the fire button opens "Delete tabs and site data?".
- `fire-confirm` "Delete All" closes all tabs and lands on a fresh New Tab Page, with no second confirmation.
- `fire-dismiss` tapping outside the sheet keeps the tabs.
- `fire-single-tab` "Delete This Tab" (`deleteThisTabButton`) replaces the current tab with a fresh New Tab Page; the other tabs stay.
- `fire-fireproof` a site fireproofed from the browser menu ("Fireproof This Site") survives Delete All (Settings > Data Clearing > Fireproof Sites).
- `fire-automatic` automatic data clearing settings (Settings > Data Clearing > Automatically Delete Data).
- `fire-duckai-chat` in a Duck.ai tab, the flame next to the chat input asks "Delete this chat?" and deletes only that chat.

## How to get to it (user POV)

- Loaded page: flame icon (`fireIconMenu`).
- New Tab Page: flame icon (`inputModeWidgetNavFire`).
- Tab switcher: `fireToolbarButton`.
- Duck.ai tab: `inputFieldFireIconMenu`, left of the chat input.
- Settings > Data clearing for the automatic options.

## Driving it with verify.sh

Preconditions: baseline; `$V open https://www.search-company.site/`, poll `$V web eval 'location.host'`, then fireproof it from the browser menu (`fireproofWebsiteMenuItem`, "Fireproof This Site"); `$V open https://privacy-test-pages.site/` and poll again. That leaves two tabs, titled "Ad Click Flow" and "Privacy Test Pages - Home".

- **Open.** `$V record start fire.mp4`, `$V tap id:fireIconMenu`, `$V wait id:dialogTitle`; `$V text dialogTitle` is "Delete tabs and site data?" and the sheet offers `deleteThisTabButton` ("Delete This Tab") and `deleteAllSecondaryButton` ("Delete All"). `$V shot fire-dialog.png`.
- **Dismiss.** `$V tap id:touch_outside`; `$V assert not-visible id:dialogTitle`, `$V web eval 'location.host'` is still `"privacy-test-pages.site"` and `$V text tabCount` is unchanged.
- **Delete This Tab.** Open the sheet, `$V tap id:deleteThisTabButton`, `$V wait id:inputField 10`: the tab is now a New Tab Page and `tabCount` is unchanged. Second view: `$V tap id:inputModeWidgetNavTabs`; no "Privacy Test Pages - Home" card, "Ad Click Flow" still listed.
- **Delete All.** From the New Tab Page, `$V tap id:inputModeWidgetNavFire`, `$V wait id:deleteAllSecondaryButton`, `$V tap id:deleteAllSecondaryButton`. The fire animation runs (the screen is mostly black and `android layout` returns nothing for a moment), then `$V wait id:inputField 15` and `$V assert text id:tabCount 1`, `$V shot fire-after.png`. Second view: the switcher has one "New Tab" card and neither earlier page. `$V record stop`.
- **Fireproof and automatic.** Settings, `$V scroll up`, `$V tap "text:Data Clearing"` (`DataClearingSettingsActivity`): "Fireproof Sites" reads "1 site excluded from clearing" and "Automatically Delete Data" reads "Off". `$V tap "text:Fireproof Sites"` (`FireproofWebsitesActivity`) still lists `www.search-company.site` after Delete All. Back, then `$V tap "text:Automatically Delete Data"` opens `AutomaticDataClearingSettingsActivity`.
- **Duck.ai chat.** Open a chat (`$V tap id:aiChatIconMenu`, `$V tap id:contextualChatMenuNewChat`, `$V wait id:duckAIHeader 25`), wait for `$V web targets` to show duck.ai `visible attached`. Ask "Reply with only the word mango" (the chat that must survive), open a new chat from the browser menu, then ask "Reply with only the word pineapple" with the `duck-ai.md` Ask recipe; `$V web eval 'location.search'` gains `chatID=`. Before: `$V tap id:duckAiSidebar`; Chat History lists both chats (generated titles, such as "Pineapple" and "Mango"); `$V shot duckai-history-before.png`, `$V key back`. Then `$V tap id:inputFieldFireIconMenu`: `dialogTitle` "Delete this chat?" and one button, `deleteThisTabButton` "Delete Chat". `$V shot fire-dialog-duckai.png`, `$V tap id:deleteThisTabButton`. After about 5 s the tab shows a fresh Duck.ai chat: `location.search` has no `chatID`, and `tabCount` keeps its count (the tab is replaced, not closed). Second view: Chat History has no pineapple row and still lists the mango one.

## Gotchas

- The fire animation runs for a few seconds after "Delete All" and `android layout` can return nothing during it; wait for `inputField` before asserting.
- `desc:Clear data` matches a non-clickable image too on a loaded page; use the ids.
- The sheet is the same from a loaded page (`fireIconMenu`) and the New Tab Page (`inputModeWidgetNavFire`), with one tab or many: "Delete This Tab" and "Delete All" (`deleteAllSecondaryButton`), no confirmation after either. `deleteAllPrimaryButton` was not seen on 2026-11-17.
- `android layout` does not list `browserWebView`; prove a page is still there with `web eval 'location.host'`.
- Data Clearing has a "Delete Duck.ai Chats" switch (off after a reset) that makes the Fire Button delete Duck.ai history too; Delete All was driven with it off.
- A chat's history title is generated: right after Ask it can read as the prompt, later as a short title ("Pineapple", "Pineapple prompt"). Read it from Chat History rather than hard-coding it.
- "Delete Chat" is safe on the user's device: per-domain clearing skips DuckDuckGo domains ([ClearPersonalDataAction.kt:221](../../../../app/src/main/java/com/duckduckgo/app/global/view/ClearPersonalDataAction.kt)) and the chat is deleted by its URL ([DataClearing.kt:127](../../../../app/src/main/java/com/duckduckgo/app/fire/DataClearing.kt)). "Delete All" is not; it closes every tab.
- On a freshly reset app, the first prompt sent in Duck.ai is dropped: the input clears, the page never starts a chat, and logcat shows `DuckAiNativeStorage: isMigrationDone key=chats / done=false` around it. The same prompt sent again is answered. Reproduced 4 times on 2026-11-17 (reset, Delete All, Start chat, send); not seen once the storage had migrated. This looks like an app bug (a new user's first prompt is lost), so the proof reports it rather than resending.
- After Delete All the app can open "Tired of cookie pop-ups?" (`CookiePopupOptInActivity`) over the New Tab Page: it shows once onboarding is done and enough days have passed since install (`CookiePopupOptInEvaluator.kt:66-109`), and `reset` keeps the install date. `steps.dismiss_cookie_prompt` declines it (`cookiePopupOptInDeclineButton`, "I'll accept them myself") and notes that it showed.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `725af5b466`): `fire-dialog`, `fire-dismiss`, `fire-single-tab`, `fire-confirm`, `fire-fireproof` (site fireproofed through the browser menu) and `fire-automatic` (opened only) passed their 19 checks in a proof. `fire-duckai-chat` did not run: the first prompt on a freshly reset app was dropped (see the Gotcha above), so the proof stopped with ERROR before its checks. Not driven: the automatic deletion options themselves, the "Delete Duck.ai Chats" switch, and an IndexedDB read of the deleted chat ID.
