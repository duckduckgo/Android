# AI Features settings

Settings > AI Features is where the user turns Duck.ai on or off and chooses how the address bar input
behaves: **Search Only** (one search field with a start-chat button) or **Toggle between Search and Duck.ai**
(the native input gets Search / Duck.ai tabs). The screen also holds the New Tab Toggle Position, the Duck.ai
Shortcuts (browser menu, address bar, voice), Search Assist, Hide AI-Generated Images and "Use DuckDuckGo
Without AI". None of it depends on the subscription tier (nothing in `DuckChatSettingsViewModel` reads it).

## Sub-features

- `ai-open` AI Features opens from Settings (`duckai.settings`).
- `ai-search-only` "Search Only" gives the native input one field: placeholder "Search", no Search / Duck.ai tabs, start-chat button `aiChatIconMenu`, inline `inputModeWidgetNavFire/Tabs/Menu`.
- `ai-search-and-duckai` "Toggle between Search and Duck.ai" adds the tabs ("Search", "Duck.ai"), placeholder "Search or enter address", the nav bar (`inputModeWidgetNavBack`), the "New Tab Toggle Position" row here and "Chat Suggestions" in General.
- `ai-toggle-position` New Tab Toggle Position (Search, Duck.ai, Last Used) decides which tab a new tab opens on.
- `ai-duckai-off` the Duck.ai switch off hides the mode choice, the shortcuts, the start-chat button and every Duck.ai browser menu item.
- `ai-shortcuts` Duck.ai Shortcuts: Browser Menu, Address Bar, Voice Search, Voice Chat.
- `ai-without-ai` "Use DuckDuckGo Without AI" turns Duck.ai off, sets Search Assist to Never and hides AI images in one tap.
- `ai-picker-sheet` the one-time address-bar picker sheet offers the same Search Only / Search & Duck.ai choice.

## How to get to it (user POV)

- Settings hub row `includeDuckChatSetting` "AI Features" (browser menu > `settingsMenuItem`, then swipe once; it sits under Other Settings).
- Duck.ai paid settings (`duckai.paid-settings`): "Manage in AI Features Settings" / "Enable in AI Features Settings" (`duckAiPaidSettingsEnableInSettings`), subscribers only.
- The SERP settings page and the duck.ai page link here (`openNativeSettings` with `aiFeatures`, JS `openAIChatSettings`); not driven.
- The address-bar picker sheet ("Adjust in Settings > AI Features."), shown once when Duck.ai is on, onboarding is complete and Search & Duck.ai was never enabled (`NewAddressBarPickerManager.kt:79-84`).
- Note: the browser menu's "Duck.ai Settings" (`duckAiChatSettingsMenuItem`) opens duck.ai's web settings, not this screen.

Controls on the screen (ids as `$V ui` lists them): Duck.ai switch `trailingSwitch` index 1; `showDuckChatSearchSettingsLink` "Search Assist"; `duckAiHideAiGeneratedImagesLink`; the two mode cards `duckAiInputScreenWithoutAiContainer` "Search Only" and `duckAiInputScreenWithAiContainer` "Toggle between Search and Duck.ai"; `duckAiDefaultTogglePosition` "New Tab Toggle Position" (Search & Duck.ai only); `duckAiShortcuts`; `duckAiWebSettings` "Duck.ai Settings"; "Automatically Send Page Content" switch `trailingSwitch` index 2; `duckAINoAiItem` "Use DuckDuckGo Without AI".

## Driving it with verify.sh

Preconditions: baseline. A fresh install starts on Search Only with Duck.ai on, Search Assist "Sometimes" and Hide AI-Generated Images "Off". Run on an emulator you may `$V t reset` afterwards; every step here changes a user setting.

Shared steps:
- **Open AI Features.** `$V t goto settings`, swipe up once (`adb -s <serial> shell input swipe 540 2200 540 900 600`) if `$V t assert visible id:includeDuckChatSetting` fails, `$V t tap id:includeDuckChatSetting`, `$V t assert where duckai.settings`. Swipe up again (`... swipe 670 2400 670 1400 600`) to bring the mode cards and the rows below them on screen.
- **Read the New Tab Page input (the second view).** `$V t goto browser.newtab`, then `$V text` for the placeholder (`inputField` line) and the tabs (`tab_title` lines), and `$V ui` for the ids. Screenshot it: the cards' check marks and the tab row are clearest in the picture.

Recipes, all inside `$V t record start ai-<name>.mp4` / `$V t record stop`, driven on 2026-11-16 (ddg_verify_api34, API 34):

- **Search Only to Search & Duck.ai.** Read the New Tab Page first: placeholder "Search", no `tab_title`, ids `aiChatIconMenu`, `inputModeUnifiedBack`, `inputModeWidgetNavFire/Tabs/Menu`. Open AI Features, `$V t tap id:duckAiInputScreenWithAiContainer`; `$V ui` now lists `duckAiDefaultTogglePosition` "New Tab Toggle Position". New Tab Page: placeholder "Search or enter address", `tab_title` "Search" and "Duck.ai", ids `inputModeWidgetNavBack` plus `inputModeWidgetNavFire/Tabs/Menu`, no `aiChatIconMenu`. `$V t goto settingsGeneral`: `$V text` contains "Chat Suggestions".
- **Back to Search Only.** `$V t tap id:duckAiInputScreenWithoutAiContainer`: the New Tab Toggle Position row disappears, the New Tab Page is back to the Search Only reading above, and General no longer has "Chat Suggestions".
- **New Tab Toggle Position** (Search & Duck.ai on). `$V t tap id:duckAiDefaultTogglePosition`, the dialog lists Search, Duck.ai, Last Used, Cancel, Save; `$V t tap "text:Duck.ai"`, `$V t tap "text:Save"`, and the row's value reads "Duck.ai". Open a new tab the user's way (`$V t tap id:inputModeWidgetNavMenu`, `$V t tap id:newTabMenuItem`): the placeholder is "Ask anything privately…", i.e. it opened on the Duck.ai tab. `goto browser.newtab` reuses the current tab, so it does not show this.
- **Duck.ai off.** Read the switch first (`$V checked trailingSwitch 1` is `true`), `$V t tap id:trailingSwitch --index 1`, it reads `false`, and `$V ui` drops the mode cards, Shortcuts and Duck.ai Settings (Search Assist, Hide AI-Generated Images and Use DuckDuckGo Without AI stay). New Tab Page: no `aiChatIconMenu`. Browser menu (`$V t tap id:inputModeWidgetNavMenu`): `$V ui` lists no id containing `duck` or `chat`; with Duck.ai on it lists `newDuckChatMenuItem`, `duckAiNewChatMenuItem`, `duckAiNewVoiceChatMenuItem`, `duckAiChatsMenuItem`, `duckAiChatSettingsMenuItem`.
- **Shortcuts.** `$V t tap id:duckAiShortcuts`, `$V t assert where duckai.shortcut.settings`; switches in order Browser Menu, Address Bar, Voice Search, Voice Chat (`$V checked trailingSwitch 1..4`, all `true` by default). Browser Menu off: the browser menu has none of the five Duck.ai items. Address Bar off (in Search Only): the New Tab Page loses `aiChatIconMenu` and the tab switcher (`$V t goto tabSwitcher`) loses `duckAIToolbarButton`. Turn both back on.
- **Use DuckDuckGo Without AI.** `$V t tap id:duckAINoAiItem` applies at once, with no confirmation: `$V text` shows Search Assist "Never", Hide AI-Generated Images "On", the Duck.ai switch off, and the row itself `DISABLED` with "AI is currently disabled in the browser…". Undo by turning each back on, or `$V t reset` on an emulator.

## Gotchas

- `$V t goto` to another screen while the app is in the background is blocked by Android (`BAL_BLOCK`), and every following `goto` returns `notReady` or lands nowhere; `$V t where --json` then shows `"activity":null`. Bring the app back first: `adb -s <serial> shell monkey -p com.duckduckgo.mobile.android.debug -c android.intent.category.LAUNCHER 1`.
- With the native input focused (keyboard up), `goto` to another activity returns `notReady: probe did not pass: ActivityResumed(...)`. Press `$V t key back` first.
- trapdoor's hierarchy has no checked state and the mode cards have no text for their check marks: read switches with `$V checked`, and prove the chosen card by its effect (the New Tab Toggle Position row, the New Tab Page) and a screenshot.
- The New Tab Toggle Position dialog's buttons are "Cancel" and "Save"; nothing is stored until Save.
- `goto onboarding.new-address-bar-picker-sheet` returns `wrongScreen` and `open` returns `notReady: WindowShown(kind=FOCUSED_DIALOG)`; the sheet only appears under its own conditions (see How to get to it), so `ai-picker-sheet` was not driven.
- On a fresh emulator a "Set As Default Browser" sheet can cover the New Tab Page; `$V t tap "text:Not Now"`.
- Not covered here: Search Assist and Hide AI-Generated Images dialogs (values only read), Voice Search and Voice Chat shortcuts, "Automatically Send Page Content" (flag `aiChat.automaticContextAttachment`), the SERP and duck.ai entry points. Flags that gate the screen: `aiChat.self` (screen and hub row), `aiChat.duckAiInputScreen` (the mode choice), `aiChat.aiFeaturesNativeControls` (this layout), `aiChat.nativeInputNavBar` (the nav bar).
- Driven with trapdoor on 2026-11-16 (ddg_verify_api34, API 34): `ai-open`, `ai-search-only`, `ai-search-and-duckai`, `ai-toggle-position`, `ai-duckai-off`, `ai-shortcuts` (Browser Menu, Address Bar), `ai-without-ai`. Not driven: `ai-picker-sheet`.
