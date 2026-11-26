# Duck.ai chat

The user opens Duck.ai, types a prompt in the native input at the bottom, taps Ask, and the reply streams
into the chat WebView. Previous chats are listed in the history.

## Sub-features

- `duckai-open` Duck.ai opens in a new tab with the `duckAIHeader` toolbar and the native input.
- `duckai-ask` a prompt sent with Ask shows the user bubble and a model reply.
- `duckai-pickers` the model chip and reasoning picker open their popup menus.
- `duckai-new-chat-model` which model and effort a new chat starts on, and how long a pick lasts (flag `resetModelForNewChats`).
- `duckai-history` the sidebar opens Chat History (`ChatHistoryActivity`) and lists the user's chats.
- `duckai-settings` AI Features settings: see [ai-settings.md](ai-settings.md).
- `duckai-delete-fire` the flame next to the chat input deletes this chat only (recipe in `fire-button.md`).

## How to get to it (user POV)

- Browser menu, shortcut row Duck.ai (`newDuckChatMenuItem`) or "New Chat" (`duckAiNewChatMenuItem`).
- The chat icon next to the address bar (`aiChatIconMenu`, "Start chat"); it opens a menu with `contextualChatMenuNewChat` (New Chat) and `contextualChatMenuAllChats` (Chats).
- Tab switcher, `duckAIToolbarButton`: a popup with `contextualChatsPopupNewChat` (New Chat), the recent chats, and `contextualChatsPopupViewAll` (View All Chats).
- Whether the address bar input has Search / Duck.ai tabs, and which entry points exist at all, is set in AI Features settings: see [ai-settings.md](ai-settings.md).
- Native input controls appear once `inputField` is focused: `modelPickerChip` (model), `reasoningModePickerButton` (desc "Reasoning mode"), and an unnamed `ImageView` with desc "Duck.ai options" (Create Image, Web Search, Customize Responses).

## Driving it with verify.sh

Preconditions: baseline; Web Content Debugging on; network access to duck.ai.

- **Open.** `$V record start duckai.mp4`, `$V tap id:aiChatIconMenu`, `$V tap id:contextualChatMenuNewChat` (or `inputModeWidgetNavMenu` then `newDuckChatMenuItem`; inside a Duck.ai tab the menu has no Duck.ai shortcut, so use `duckAiNewChatMenuItem`), `$V wait id:duckAIHeader 25`, `$V assert where BrowserActivity`; the header reads "Free Plan" (`$V text duckAIFreePillTitle`). `$V web targets` shows `https://duck.ai/chat?...` as `visible attached`.
- **Ask.** `$V tap id:inputField`, `$V type id:inputField "Reply with only the word pineapple"`, `$V wait id:nativeInputAskButton 5`, `$V tap id:nativeInputAskButton` ("Ask"). Poll `$V web eval 'document.body.innerText.toLowerCase().split("pineapple").length - 1'` until it is 2 or more (prompt plus reply, about 5 s). `$V web eval 'location.search'` gains `chatID=`. `$V shot duckai-answered.png`, `$V record stop`.
- **Pickers.** `$V tap id:inputField`, `$V wait id:modelPickerChip 5`, `$V tap id:modelPickerChip`; `$V windows` lists the model rows with their subtitles (the popup is a separate window, so it is in neither `$V ui` nor `$V text`). Close it by tapping outside the popup (`adb -s <serial> shell input tap 150 1500`), not with back (see Gotchas); the input loses focus, so `$V tap id:inputField` again before `$V tap id:reasoningModePickerButton`, whose rows ("Fast", "Reasoning") are also read with `$V windows`. To pick a row, tap the centre `$V windows` prints with `adb -s <serial> shell input tap x y`.
- **New-chat model rules** (`resetModelForNewChats`, on by default in internal builds; switch it with the **Feature Flag Inventory** path in the README). With it on, a new chat starts on the tier default: Free "6 Luna" (`gpt-6-luna`, driven), Plus and Pro `gpt-5.6-terra` (from `NewChatDefaults`, not driven), effort Fast. A default that is missing from the models list or locked falls back to the first accessible model, and Fast to the first accessible effort. A pick made before sending applies only to the next new chat in that tab: it survives a tab switch and backgrounding, and is cleared when that chat is sent. Chats opened from history keep their own model. Opening or closing the "Ask about page" sheet clears the tab's pick, so the sheet starts on the default and its pick never reaches the address bar. With it off, a pick is saved and applies to every new chat.
- **New-chat model drive** (flag on, Free account). Open a chat (**Open** above). Pick a non-default model (**Pickers** above, e.g. "gpt-oss 120B"); `$V text modelPickerChip` reads it. Open the browser menu (**Browser menu** path in the README; with the input focused, `$V tap id:inputModeUnifiedBack` first), `$V tap id:duckAiNewChatMenuItem`, `$V wait id:duckAIHeader 20`, `$V tap id:inputField`: the chip still reads the pick (the chat is still empty, so New Chat refocuses it in the same tab). Send a prompt (**Ask** above) and prove the sent `model` (Gotchas). Open New Chat the same way: it now opens a new tab (`$V web targets` shows the sent chat `detached`), and after `$V tap id:inputField` the chip reads "6 Luna" (2026-11-26, Pixel 6). With the flag off, that new chat should keep the pick. Chat History's New (`chat_history_action_new`) also opens a new tab after a send, so no native path starts a new chat in the same tab once it has been sent.
- **History.** `$V tap id:duckAiSidebar` from the chat, `$V assert where ChatHistoryActivity`; `$V text` lists each chat's title as `chatHistoryTitle` (the pineapple prompt gets a generated title such as "Pineapple"; right after Ask it can still read as the prompt) under the section headers "Pinned" and "Recent", each with a `chatHistoryMore` overflow, and `chat_history_action_fire` deletes chats from there. `$V key back` returns to the chat. Tapping a title opens that chat.

## Gotchas

- The terms sheet did not appear before Ask on the internal build (2026-11-13, 2026-11-17). On a fresh install, New Voice Chat showed duck.ai's "Say hello to Duck.ai" sheet (Agree and Continue, Not Now, Disable Duck.ai) before the voice sheet (2026-11-17).
- Assert on DOM text with `web eval`, not on exact reply wording; a one-word instruction keeps it deterministic enough.
- Opening Duck.ai adds a tab and leaves the previous page's WebView as a detached target.
- The send button is `nativeInputAskButton` ("Ask"); it appears only once the field has text.
- On the user's device the history holds their own chats. Count rows by your chat's title before and after; never tap `chat_history_action_fire` there.
- With a picker popup open, the first back only hides the keyboard and the second leaves the chat for the previous tab while closing the popup (2026-11-16, Pixel 7, Android 17). Close pickers by tapping outside them.
- `$V text` reads the header and chip; the picker rows only show up in `$V windows`. Run one `$V windows` at a time: two uiautomator dumps at once kill each other.
- Not seen on 2026-11-16: the new-chat hints (Documents, Photo, Create image), the usage-limit footer and the high-usage model footer (`duckAiUsageWarnings`; the footer needs a real usage snapshot from duck.ai).
- The chip shows what the app will send, not what was sent. To prove the model, attach DevTools to the duck.ai WebView (`adb forward` to the `webview_devtools_remote` socket) and read `model` and `reasoningEffort` from the `/duckchat/v1/chat` POST body with `Network.requestWillBeSent` (2026-11-23, Pixel 6).
- A new image chat always opens a new tab on the default. Free's default (6 Luna) already generates images, so its model switch can't be reached on a Free account (2026-11-23).
- Subscription onboarding's Duck.ai step needs a purchase: `SubscriptionOnboardingActivity` is not exported, and `am start` fails with SecurityException (2026-11-23).
- After the "Ask about page" sheet closes, the address bar in Duck.ai mode can open with only the voice button: no chip, attach or options. Switching to Search and back restores them. Also seen on a `develop` build (2026-11-23).
- With a Custom Duck.ai URL left pointing at an SSO-protected dev instance whose sign-in has expired, every chat opened from history hangs on a grey spinner and `web eval` reads empty text. Reset it (the **Duck.ai developer settings** path in the README, "URL Settings", `$V tap id:reset`) before blaming the data.
- Driven with the android CLI on 2026-11-17 (ddg_verify_api34, API 34, `$V build` of `725af5b466`): `duckai-open` (via `aiChatIconMenu` and the tab switcher's `duckAIToolbarButton`), `duckai-ask`, `duckai-pickers` (model and reasoning, read with `$V windows`) and `duckai-history`, as 20 checks in a proof that all passed. `duckai-delete-fire` is driven in `fire-button.md`.
