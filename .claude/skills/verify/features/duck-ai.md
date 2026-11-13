# Duck.ai chat

The user opens Duck.ai, types a prompt in the native input at the bottom, taps Ask, and the reply
streams into the chat WebView.

## Sub-features

- `duckai-open` Duck.ai opens in a new tab with the `duckAIHeader` toolbar and the native input.
- `duckai-ask` a prompt sent with Ask shows the user bubble and a model reply.
- `duckai-pickers` the model chip (`modelPickerChip`) and reasoning picker (`reasoningModePickerButton`) open their selectors.
- `duckai-history` the sidebar button (`duckAiSidebar`, "Chat History") lists previous chats.

## How to get to it (user POV)

- Browser menu, top-row Duck.ai shortcut (`newDuckChatMenuItem`), from a page or the New Tab Page.
- Browser menu, "New Chat" (`duckAiNewChatMenuItem`).
- The chat icon next to the address bar (`aiChatIconMenu`, labelled "Start chat" on the New Tab Page).
- Tab switcher, `duckAIToolbarButton`.

## Driving it with verify.sh

Preconditions: baseline plus the DevTools step from SKILL.md; network access to duck.ai.

- **Open.** `$V tap --id browserMenu` (on a page) or `$V tap --id inputModeWidgetNavMenu` (New Tab Page), then `$V tap --id newDuckChatMenuItem`. `$V wait --id duckAIHeader 20`; `$V web targets` shows `https://duck.ai/chat?native-input=true...` as `visible attached`.
- **Ask.** `$V tap --id inputField` (hint "Ask anything privately…"), `$V type "Reply with only the word pineapple"`, `$V tap --id nativeInputAskButton`. Poll `$V web eval 'document.body.innerText.toLowerCase().split("pineapple").length - 1'` until it is 2 or more (prompt plus reply, about 5 s). `$V web eval 'location.href'` gains a `chatID=` parameter. `$V shot duckai-answered` shows the bubble and the "pineapple" reply.
- **Pickers.** `$V tap --id modelPickerChip` and `$V ui` to list the models; `KEYCODE_BACK` closes it.

## Gotchas

- The terms sheet did not appear on the internal build (2026-11-13). If it does, accept it before Ask; `.maestro/shared/accept_duckai_agreement.yaml` names its button.
- Assert on DOM text with `web eval`, not exact reply wording; model output varies. A one-word instruction keeps it deterministic enough.
- Opening Duck.ai adds a tab (`tabCount` goes up by one) and leaves the previous page's WebView as a detached target.
- Only `duckai-open` and `duckai-ask` have been driven so far.
