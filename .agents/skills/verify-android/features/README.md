# DuckDuckGo Android verification map

Recipes for verifying user-facing behaviour of the internal debug build, scoped to Duck.ai and its entry
points. Each is driven with the android CLI and adb, reaching every state through the UI. Commands use `V=.agents/skills/verify-android/verify.sh` from `../SKILL.md`.

## Baseline preconditions

- `$V doctor` passes: the installed app is the `$V build` of the current source.
- The app starts past onboarding with one tab on the New Tab Page. On an emulator the run booted, `$V reset` gets there (it clears data and taps Skip Onboarding); on the user's device, ask before clearing anything and close the tabs a recipe opened.
- Default settings: omnibar at the top, native input on (internal builds), light theme.
- Web Content Debugging on (SKILL.md, Web content). Recipes prove page loads with `web eval`, so this is part of every baseline; `reset` clears it, so switch it on again after one.
- Use `privacy-test-pages.site` and `www.search-company.site` as web targets; they are DDG controlled and stable.

## Driving conventions

- Reach the starting state through the Common paths below and `open`; drive the step under test with `tap`, `type` and `web tap` on the entry points the file lists.
- Select by `id:` first, then `text:` / `desc:`. When none fits, `android screen capture --annotate` and `android screen resolve` (android-cli `references/interact.md`) find an element by its label.
- The New Tab Page and a loaded page use different ids for the same toolbar buttons (`inputModeWidgetNavFire` vs `fireIconMenu`); run `$V where` and `$V ui` when unsure.
- `android layout` does not list `browserWebView`, so prove a page load by polling `$V web eval 'location.host'`, not with `$V wait id:browserWebView`.
- Popup windows (the Duck.ai model and reasoning pickers) are not in `$V ui` or `$V text`; read them with `$V windows`.

## Common paths

Recipes reach their starting point the way a user does. `$V wait` for the first element of each screen before tapping on it.

- **Back to the browser:** from another screen, `$V key back` until `$V where` prints `BrowserActivity` (`-` means the app was left: `$V launch`).
- **Browser menu:** `$V tap id:inputModeWidgetNavMenu` on the New Tab Page, `$V tap id:browserMenu` on a page. A page hides its toolbar once scrolled; `$V scroll down` brings it back.
- **New tab:** browser menu, `$V tap id:newTabMenuItem`, `$V wait id:inputField`.
- **Settings:** browser menu, `$V wait id:settingsMenuItem 5`, `$V tap id:settingsMenuItem`, `$V assert where SettingsActivity`.
- **AI Features:** Settings, `$V scroll up`, `$V tap id:includeDuckChatSetting`, `$V assert where DuckChatSettingsActivity`.
- **Developer Settings:** Settings, `$V scroll up 3` to Internal Features, `$V tap "text:Developer Settings"`, `$V assert where DevSettingsActivity`.
- **Duck.ai developer settings:** Settings, `$V scroll up 6`, `$V tap "text:Developer settings and tools"` (the subtitle; the title "Duck.ai" repeats), `$V assert where DuckAiDevActivity`. From there `$V tap "text:URL Settings"` (`DuckAiUrlSettingsActivity`) or `$V tap "text:Browser Tools"`.
- **Tab switcher:** `$V tap id:inputModeWidgetNavTabs` on the New Tab Page or `$V tap id:tabsMenu` on a page, `$V assert where TabSwitcherActivity`.
- **Duck.ai chat:** browser menu, `$V tap id:newDuckChatMenuItem`, `$V wait id:duckAIHeader 25`.

## Proof and skip reporting

- Every recipe runs inside `$V record start <feature-id>.mp4` / `$V record stop`, so each feature has a video.
- Shot before and after the step under test (`$V shot <name>.png`), plus the `assert` or `web eval` results, all in the same run id.
- A mutation is proved through a second view (tab switcher, bookmarks list), not the screen that made it.
- Report every entry point in a file as verified, failed (command and exit code) or not attempted. Do not report one as verified because another worked.

## Features

| File | Feature | Starts from |
|---|---|---|
| [search-and-navigate.md](search-and-navigate.md) | Typing a URL or query and loading the result | New tab |
| [tabs.md](tabs.md) | Tab switcher, opening, switching and closing tabs | a loaded page, Tab switcher |
| [fire-button.md](fire-button.md) | Clearing tabs and site data, and deleting one Duck.ai chat | a loaded page, Duck.ai chat |
| [browser-menu.md](browser-menu.md) | Browser menu: every entry and where it lands | Browser menu |
| [duck-ai.md](duck-ai.md) | Duck.ai chat: open, ask, pickers, history | Duck.ai chat |
| [ai-settings.md](ai-settings.md) | AI Features settings: Search Only vs Search & Duck.ai, New Tab Toggle Position, Duck.ai on/off, shortcuts, Without AI | AI Features |

Not covered on purpose (the map is scoped to Duck.ai and its entry points): bookmarks, page forms, the rest of
Settings, onboarding, sync, autofill, downloads, and anything that depends on a paid plan (Duck.ai's paid
models, VPN, PIR, ITR): the debug build can't reach a subscribed state through the UI.

## Proofs

`../prove.py ../proofs/<name>.py` replays a recipe as checks: it refuses to start unless `verify.sh doctor` passes, stops at the first driving step that fails, can check pixels from logcat after `android.mark()`, and exits 0 PASS, 1 FAIL or 2 ERROR. Every recipe has a proof in `../proofs/` (`search-and-navigate.md` has `search_and_navigate.py`), with shared paths in `proofs/steps.py`. The video is split into segments of 160 s, because screenrecord stops at 170 s. After changing `prove.py`, run `../prove.py selftest`: it checks that a wrong expectation is FAIL, a broken step and a stale build are ERROR, a proof with no checks (or only `unverified` items) isn't a pass, pixels logged before `mark()` aren't counted, and `pr-summary` carries no local path (`selftest --offline` runs only that part, with no device).

## Adding a feature

1. Find the user entry points in code: the screen's `@ContributeToActivityStarter` params, and every `globalActivityStarter.start(..., <Params>)` call site.
2. Drive it once on a fresh run: reach the starting point through the Common paths, then walk every entry point with `$V ui` and `$V text` on each screen (and `$V web eval` inside pages) to collect real selectors. Never copy selectors from code or Maestro flows without seeing them in `android layout`.
3. Create `<feature>.md` here: an H1, one paragraph of user-visible behaviour, then exactly these H2s: `Sub-features` (short ids, one line each), `How to get to it (user POV)` (every entry point), `Driving it with verify.sh` (commands and the observable end state per entry point), `Gotchas` (ending with which sub-features have been driven and when).
4. Add a row to the table above and remove the area from the "without a recipe" list.
5. Write `../proofs/<feature>.py` from the recipe (one `check` per observable end state, `steps.py` for the shared paths) and run it with `../prove.py` until it passes; fix the recipe where it drifted.

