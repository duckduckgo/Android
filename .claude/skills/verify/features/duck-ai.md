# Duck.ai chat

The user opens Duck.ai, types a prompt in the native input at the bottom, taps Ask, and the reply streams
into the chat WebView. Previous chats are listed in the history.

## Sub-features

- `duckai-open` Duck.ai opens in a new tab with the `duckAIHeader` toolbar and the native input.
- `duckai-ask` a prompt sent with Ask shows the user bubble and a model reply.
- `duckai-pickers` the model chip and reasoning picker open their popup menus.
- `duckai-history` the sidebar opens Chat History (`ChatHistoryActivity`), pinned chats first (`seed duckai-chats`).
- `duckai-settings` AI Features settings: see [ai-settings.md](ai-settings.md).
- `duckai-tier-header` the toolbar title is "Duck.ai" for a subscriber with the Duck.ai entitlement, otherwise a "Free Plan" pill, with "Upgrade" under it when the user can buy.
- `duckai-tier-models` the model picker lists the models the tier can use and gates the rest ("…" suffix, gated section header).
- `duckai-tier-reasoning` the reasoning picker offers the modes the selected model and tier allow.
- `duckai-tier-upsell` a gated model or the Upgrade pill opens the purchase page (free) or the Pro upgrade page (Plus).
- `duckai-tier-downgrade` a saved model the user loses access to is dropped and the chip falls back to the first accessible model.
- `duckai-tier-flag` with `privacyPro.duckAiPlus` off a subscriber sees the Free pill and no Duck.ai settings row, but keeps their models.
- `duckai-delete-fire` the flame next to the chat input deletes this chat only (recipe in `fire-button.md`).

## How to get to it (user POV)

- Browser menu, shortcut row Duck.ai (`newDuckChatMenuItem`) or "New Chat" (`duckAiNewChatMenuItem`).
- The chat icon next to the address bar (`aiChatIconMenu`, "Start chat"); it opens a menu with `contextualChatMenuNewChat` (New Chat) and `contextualChatMenuAllChats` (Chats).
- Tab switcher, `duckAIToolbarButton`.
- Whether the address bar input has Search / Duck.ai tabs, and which entry points exist at all, is set in AI Features settings: see [ai-settings.md](ai-settings.md).
- Native input controls appear once `inputField` is focused: `modelPickerChip` (model), `reasoningModePickerButton` (desc "Reasoning mode"), and an unnamed `ImageView` with desc "Duck.ai options" (Create Image, Web Search, Customize Responses).
- Upsell entries: the gated rows in the model and reasoning pickers, and the header pill `duckAIFreePill` ("Free Plan" `duckAIFreePillTitle`, "Upgrade" `duckAIFreePillUpgrade`).

## Driving it with verify.sh

Preconditions: baseline; Web Content Debugging on; network access to duck.ai.

- **Open.** `$V record start duckai.mp4`, `$V tap id:aiChatIconMenu`, `$V tap id:contextualChatMenuNewChat` (or `inputModeWidgetNavMenu` then `newDuckChatMenuItem`), `$V wait id:duckAIHeader 20`, `$V assert where BrowserActivity`. `$V web targets` shows `https://duck.ai/chat?...` as `visible attached`.
- **Ask.** `$V tap id:inputField`, `$V type id:inputField "Reply with only the word pineapple"`, `$V wait id:nativeInputSubmitButton 5`, `$V tap id:nativeInputSubmitButton`. Poll `$V web eval 'document.body.innerText.toLowerCase().split("pineapple").length - 1'` until it is 2 or more (prompt plus reply, about 5 s). `$V web eval 'location.search'` gains `chatID=`. `$V shot duckai-answered.png`, `$V record stop`.
- **Pickers.** `$V tap id:inputField`, `$V wait id:modelPickerChip 5`, `$V tap id:modelPickerChip`; `$V ui` lists the model rows (a popup is not in `$V text`). Close it by tapping outside the popup (`adb -s <serial> shell input tap 150 1500`), not with back (see Gotchas).
- **History.** `$V tap id:duckAiSidebar` from the chat, `$V assert where ChatHistoryActivity`; `$V text` lists each chat's title as `chatHistoryTitle` (the pineapple prompt is titled "Pineapple") under the section headers "Pinned" and "Recent", each with a `chatHistoryMore` overflow, and `chat_history_action_fire` deletes chats from there. `$V key back` returns to the chat.
- **Seed chats.** `$V seed duckai-chats [--kind text|voice|image] [--count 1..50] [--turns 1..400] [--reply short|long] [--pinned N] [--mode regular|fire] [--title <prefix>] [--days-apart 0..30] [--model <id>]` writes chats straight into native storage in the shapes the frontend stores, copied from real chats: titled `<prefix> 1..N`, the first `--pinned` of them pinned, each `--days-apart` days older than the one before. It refuses until Duck.ai has migrated its chats to native storage: after a reset, open Duck.ai and wait until `$V web targets` shows duck.ai `visible attached` (a restart before the page loads leaves it unmigrated), and one chat must stay under 1,000,000 characters. `$V seed duckai-chats-none [--mode fire]` removes every chat whose id starts `trapdoor-`, with its image files, and leaves the user's own. `seed duckai-chat-history` still adds its single fixed "Example conversation".
  - `--kind text` (default): `--reply long` adds a reasoning part and 5 long text parts per answer, like the internal "Seed long chats" tool (400 long turns fit). `--model` defaults to `gpt-6-luna`; a model duck.ai no longer lists (such as `gpt-5-mini`) hides the reasoning picker for that chat. Like a real chat, a seeded one shows the reasoning picker but no model chip once it exists.
  - `--kind voice`: model `voice-mode`, the only thing the app reads to call a chat voice (`RealDuckAiChatStore.kt:276`). On duck.ai its answers are labelled "Voice Chat"; its tab card is `tabChatItemType` "Transcript" with the last answer as `snippet`; with the input focused it shows the model chip, reasoning picker and voice button like a new chat, because voice chats get no per-chat id (`NativeInputManager.kt:1193`). It stores the result of a voice chat; a live voice conversation needs microphone audio and is not covered.
  - `--kind image` (up to 20 turns): each answer is a `generate-image` component pointing at a generated 512×512 JPEG (a flat colour labelled `<chat>.<turn>`), written to `files/duck_ai_bridge_files/<id>` with a `duck_ai_file_meta` row and listed in the chat's `fileRefs`, the layout a real image chat had on 2026-11-16. duck.ai renders each image with its download and feedback buttons under "Seeded image", and the tab card shows the last image as its thumbnail with the "Chat" label, the same as a real image chat.
  - Driven on 2026-11-16 (ddg_verify_api34): text (short, long, pinned, spread), voice and image kinds in Chat History, on duck.ai and in the tab switcher; clearing removed the seeded chats and their image files and kept the real ones.

**By subscription tier.** Put the app in a state with `$V subscription ...` ([subscription.md](subscription.md)), open
a new chat through the browser menu (`inputModeWidgetNavMenu` > `newDuckChatMenuItem`, `$V wait id:duckAIHeader 20`),
then for each control: `$V tap id:inputField`, `$V wait id:modelPickerChip 5`, tap the control, read its rows with
`$V ui` (popups are not in `$V text`), screenshot it (the gated section header is only in the picture), close it by
tapping outside. Header: `$V text aiTitle`, `$V text duckAIFreePillTitle`, `$V text duckAIFreePillUpgrade`.

Driven on 2026-11-16 (Pixel 7, internal debug). Model names and which ones a tier gets are backend data and
will change; assert on counts, the "…" gated suffix and where a tap lands, not on names.

| State (`$V subscription`) | Header | Models (available + gated) | Reasoning (default model) | Options |
|---|---|---|---|---|
| `none` | "Free Plan" | 6 + 0, gated hidden | Fast, Reasoning (6 Luna) | Create Image, Web Search, Customize Responses |
| `--products netp,itr,pir` (subscribed, no Duck.ai) | "Free Plan" + "Upgrade" | 6 + 4 | Fast, Reasoning (6 Luna) | same |
| `--tier plus` | "Duck.ai" | 8 + 2 ("GPT-5.6 Sol…", "Claude Opus 5.5…" under "Pro Plan Exclusive") | Fast, Reasoning, Extended Reasoning (5.6 Terra) | same |
| `--tier pro` | "Duck.ai" | 10 + 0 | Fast, Reasoning, Extended Reasoning (5.6 Terra) | same |
| `--tier pro --status waiting` | as pro | as pro | as pro | same |
| `--tier pro --status expired` | "Free Plan" | 6 + 0 | as none | same |

- **Upsell routing.** Tap a gated row (`$V tap "text:GPT-5.6 Sol…"`) or the pill (`$V tap id:duckAIFreePill`), then `$V where` and `$V web targets` (Web Content Debugging on). Plus on a Pro model: `SubscriptionsWebViewActivity` at `duckduckgo.com/subscriptions/plans?tier=pro&origin=funnel_duckai_android__modelpicker`. Subscribed without Duck.ai, gated model: `duckduckgo.com/subscriptions?featurePage=duckai&origin=funnel_addressbar_android__modelpicker`; the Upgrade pill: same page with `origin=funnel_duckai_android__freelabel`. No subscription: the pill has no "Upgrade" and tapping it does nothing (`PickerUpsell.kt:59-77` routes nothing when not eligible).
- **Downgrade.** As `--tier pro`, pick a Pro model (`$V tap "text:Claude Opus 5.5"`, chip reads "Opus 5.5"), then `$V subscription --tier plus` and reopen: the chip is back to the first accessible model ("5.6 Terra") and Opus is gated again. Going to `none` afterwards also clears the pick, so this sequence leaves no saved model behind.
- **Flag.** `$V flag privacyPro.duckAiPlus disabled` with `--tier pro`: header "Free Plan" + "Upgrade", all 10 models available, and the Settings hub drops the Duck.ai row (`RealSubscriptions.kt:85-94` filters the entitlement for the header and settings, while models read the entitlement name directly, `DuckAiModelManager.kt:376-386`). Restore with `$V flag privacyPro.duckAiPlus enabled` and confirm `$V flags --json | jq '.flags[] | select(.name=="privacyPro.duckAiPlus") | .effectiveValue'` prints `true`.
- **Reasoning follows the model.** The modes come from the selected model's `reasoningEffortAccess`, so a tier difference can be a model difference: Opus on Pro also offers all three. Compare tiers on the same model where the tier allows it.

## Gotchas

- The terms sheet did not appear on the internal build (2026-11-13). If it does, accept it before Ask.
- Assert on DOM text with `web eval`, not on exact reply wording; a one-word instruction keeps it deterministic enough.
- Opening Duck.ai adds a tab and leaves the previous page's WebView as a detached target.
- The send button is `nativeInputSubmitButton`; it appears only once the field has text.
- On the user's device the history holds their own chats. Count rows by your chat's title before and after; never tap `chat_history_action_fire` there.
- With a picker popup open, the first back only hides the keyboard and the second leaves the chat for the previous tab while closing the popup (2026-11-16, Pixel 7, Android 17). Close pickers by tapping outside them.
- `$V text` reads the header and chip; the picker rows only show up in `$V ui`.
- The debug build has no Play offers, so a user with no subscription is never purchase-eligible: no "Upgrade", no gated section, no Subscribe call to action in the usage-limit footer. To see the free-user upsells, seed a subscription without Duck.ai (`--products netp,itr,pir`), which is eligible because it is active.
- Not seen on 2026-11-16: the new-chat hints (Documents, Photo, Create image), the usage-limit footer and the high-usage model footer (`duckAiUsageWarnings`; the footer needs a real usage snapshot from duck.ai). The model list, attachment limits and tool support come from the backend per tier and were not compared beyond the pickers.
- Paid replies need a real token; with the seed, prompts to gated models are not expected to succeed. Verify tier changes on the native controls and routing, and ask with a free model.
- With a Custom Duck.ai URL left pointing at an SSO-protected dev instance whose sign-in has expired, every chat opened from history (seeded or real) hangs on a grey spinner and `web eval` reads empty text. Reset it (the **Duck.ai developer settings** path in the README, "URL Settings", `$V tap id:reset`) before blaming the data.
- Driven with trapdoor's commands: `duckai-open` (via `aiChatIconMenu`), `duckai-ask`, `duckai-history`, `duckai-delete-fire` (2026-11-13, Pixel 7 with Android 17); every `duckai-tier-*` sub-feature (2026-11-16, Pixel 7, Android 17). Not yet re-driven with this version of the commands. On 2026-11-17, with this build: opening a chat from the browser menu (`newDuckChatMenuItem`, `duckAIHeader`) and `seed duckai-chats` / `duckai-chats-none` on the `ddg_verify_api34` emulator.
