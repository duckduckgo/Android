# Duck.ai chat

The user opens Duck.ai, types a prompt in the native input at the bottom, taps Ask, and the reply streams
into the chat WebView. Previous chats are listed in the history.

## Sub-features

- `duckai-open` Duck.ai opens in a new tab with the `duckAIHeader` toolbar and the native input.
- `duckai-ask` a prompt sent with Ask shows the user bubble and a model reply.
- `duckai-pickers` the model chip and reasoning picker open their menus (`duckchat.model-picker-menu`, `duckchat.reasoning-mode-picker-menu`).
- `duckai-history` the sidebar lists previous chats (`duckai.history`, `seed duckai-chat-history`).
- `duckai-settings` Duck.ai settings (`duckai.settings`).

## How to get to it (user POV)

- Browser menu, shortcut row Duck.ai (`newDuckChatMenuItem`) or "New Chat" (`duckAiNewChatMenuItem`).
- The chat icon next to the address bar (`aiChatIconMenu`).
- Tab switcher, `duckAIToolbarButton`.

## Driving it with verify.sh

Preconditions: baseline; Web Content Debugging on; network access to duck.ai.

- **Open.** `$V t record start duckai.mp4`, `$V t tap id:inputModeWidgetNavMenu`, `$V t tap id:newDuckChatMenuItem`, `$V wait id:duckAIHeader 20`, `$V t assert where browser.duckai-view`. `$V web targets` shows `https://duck.ai/chat?...` as `visible attached`.
- **Ask.** `$V t tap id:inputField`, `$V t type id:inputField "Reply with only the word pineapple"`, `$V t tap id:nativeInputAskButton`. Poll `$V web eval 'document.body.innerText.toLowerCase().split("pineapple").length - 1'` until it is 2 or more (prompt plus reply, about 5 s). `$V web eval 'location.search'` gains `chatID=`. `$V t shot duckai-answered.png`, `$V t record stop`.
- **Pickers.** `$V t tap id:modelPickerChip`, `$V t assert where duckchat.model-picker-menu`, `$V t key back`.
- **History.** `$V t seed duckai-chat-history`, then `$V t tap id:duckAiSidebar` from the chat and confirm the seeded chat row.

## Gotchas

- The terms sheet did not appear on the internal build (2026-11-13). If it does, accept it before Ask.
- Assert on DOM text with `web eval`, not on exact reply wording; a one-word instruction keeps it deterministic enough.
- Opening Duck.ai adds a tab and leaves the previous page's WebView as a detached target.
- Driven with the earlier harness: `duckai-open`, `duckai-ask` (2026-11-13). Not yet driven with trapdoor.
