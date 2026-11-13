# Settings

The Settings hub lists next steps (import passwords, desktop browser), protections and preferences. General
holds Search Suggestions, Recently Visited Sites and the After Inactivity choice of what the app shows on
return. Private Search links out to the web search settings page.

## Sub-features

- `settings-open` Settings opens from the browser menu.
- `settings-general` General opens from its Settings row.
- `settings-suggestions` turning Search Suggestions off stops suggestion rows while typing.
- `settings-after-inactivity` the After Inactivity choice (Last Used Tab, New Tab Page, Specific Page) persists.
- `settings-next-steps` the overflow on each next-steps row offers Hide (`settings.next-steps-menu`, `settings.desktop-browser-complete-setup-menu`).
- `settings-web` Private Search > More Search Settings opens duckduckgo.com/settings in `settings.webview`.

## How to get to it (user POV)

- Settings: browser menu > `settingsMenuItem` ([BrowserTabFragment.kt:1805](../../../../app/src/main/java/com/duckduckgo/app/browser/BrowserTabFragment.kt)); also a New Tab Page shortcut on the configurable New Tab Page (`SettingsNewTabShortcutPlugin`).
- General: Settings > "General" row.
- After Inactivity: General > `showOnAppLaunchButton`; also the New Tab Page return-hatch menu ("After Inactivity"), which only appears on an idle return.
- Settings web view: Private Search > `privateSearchMoreSearchSettings` (behind `embeddedSettingsWebView`); also from Duck.ai settings and Sync.

## Driving it with verify.sh

Preconditions: baseline; `$V t goto browser.newtab`, `$V wait id:inputModeWidgetNavMenu`.

- **Open.** `$V t record start settings.mp4`, `$V t tap id:inputModeWidgetNavMenu`, `$V wait id:settingsMenuItem`, `$V t tap id:settingsMenuItem`, `$V t assert where settings`, `$V t shot settings.png`.
- **General.** `$V t tap text:General`, `$V t assert where settingsGeneral`.
- **Suggestions.** Read the start: `$V checked trailingSwitch 1` (Search Suggestions) and `2` (Recently Visited Sites); make both `true` first. `$V t tap id:trailingSwitch --index 1`, `$V checked trailingSwitch 1` is `false`, `$V t shot general-suggestions-off.png`. Second view: `$V t goto browser.newtab`, `$V wait id:inputField`, `$V t tap id:inputField`, `$V t type id:inputField privacy`, wait 3 s, and `$V t hierarchy --interactive-only` lists no suggestion rows (7 rows with suggestions on, 0 off on 2026-11-13). Restore: back out of the input (below), `goto settingsGeneral`, tap switch 1, and switch 2 if it reads `false`.
- **After Inactivity.** On General, `$V t tap id:showOnAppLaunchButton`, `$V t assert where settingsAfterInactivity`. Radios in order: Last Used Tab, New Tab Page, Specific Page (`$V checked radioButton 1..3`). `$V t tap "text:Last Used Tab"`, radio 1 reads `true`. Second view: `key back`, tap `showOnAppLaunchButton` again, radio 1 still `true`. Restore with `$V t tap "text:New Tab Page"`.
- **Next steps.** `$V t seed settings-next-steps-visible`, open Settings through the menu, `$V t tap id:trailingIconContainer --index 1` (Import Passwords from Google) or `--index 2` (Get Desktop Browser); `$V t assert visible id:hide`; `key back` closes it.
- **Web settings.** On Settings, `$V t tap id:privateSearchSetting`, `$V t assert where browser.private-search`, `$V t tap id:privateSearchMoreSearchSettings`, `$V t assert where settings.webview`, `$V web eval 'location.host + location.pathname'` returns `"duckduckgo.com/settings"`. `$V t record stop`.

## Gotchas

- With the native input focused (keyboard up), `goto` to another activity returns `notReady: probe did not pass: ActivityResumed(...)`. Press `$V t key back` two or three times first.
- trapdoor's hierarchy has no checked state, and a switch's position is not reset by anything but `reset`. Always read it with `$V checked` before tapping; a blind tap can turn a setting on instead of off.
- Recently Visited Sites is disabled while Search Suggestions is off, so it reads `false` and cannot be toggled until suggestions are back on.
- A popup opened by a tap is not named by `where` (`surface` is null); only `goto` reports `settings.next-steps-menu`. Assert on `id:hide`.
- The Settings list is longer than the screen; rows below Appearance need a swipe (`adb -s <serial> shell input swipe 672 2200 672 900 600`) before trapdoor lists them.
- Driven with trapdoor on 2026-11-13: `settings-open`, `settings-general`, `settings-suggestions`, `settings-after-inactivity` (from General), `settings-next-steps`, `settings-web` (from Private Search). Not driven: the return-hatch entry to After Inactivity (needs an idle return, see `FirstScreenHandler.kt`), the New Tab Page shortcut, and the Duck.ai and Sync entries to the web view.
