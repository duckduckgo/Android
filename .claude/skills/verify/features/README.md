# DuckDuckGo Android verification map

Recipes for verifying user-facing behaviour of the internal debug build, scoped to Duck.ai and its entry
points. Each is driven with the android CLI and adb; trapdoor only seeds state the UI cannot reach. Commands use `V=.claude/skills/verify/verify.sh` from `../SKILL.md`.

## Baseline preconditions

- `$V doctor` passes: the installed app is the `$V build` of the current source, and the seeding bridge answers.
- The app starts past onboarding with one tab on the New Tab Page. On an emulator the run booted, `$V reset` gets there (it clears data and seeds onboarding as done); on the user's device, ask before clearing anything and close the tabs a recipe opened.
- Default settings: omnibar at the top, native input on (internal builds), light theme.
- Web Content Debugging on (SKILL.md, Web content). Recipes prove page loads with `web eval`, so this is part of every baseline; `reset` clears it, so switch it on again after one.
- Use `privacy-test-pages.site` and `www.search-company.site` as web targets; they are DDG controlled and stable.

## Driving conventions

- Reach the starting state through the Common paths below, `open` and `seed`; drive the step under test with `tap`, `type` and `web tap` on the entry points the file lists.
- Select by `id:` first, then `text:` / `desc:`. When none fits, `android screen capture --annotate` and `android screen resolve` (android-cli `references/interact.md`) find an element by its label.
- The New Tab Page and a loaded page use different ids for the same toolbar buttons (`inputModeWidgetNavFire` vs `fireIconMenu`); run `$V where` and `$V ui` when unsure.

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

| File | Feature | Starts from | Seeds |
|---|---|---|---|
| [search-and-navigate.md](search-and-navigate.md) | Typing a URL or query and loading the result | New tab | |
| [tabs.md](tabs.md) | Tab switcher, opening, switching and closing tabs | a loaded page, Tab switcher | `tabs` |
| [fire-button.md](fire-button.md) | Clearing tabs and site data, and deleting one Duck.ai chat | a loaded page, Duck.ai chat | `fireproof-populated` |
| [browser-menu.md](browser-menu.md) | Browser menu: every entry and where it lands | Browser menu | |
| [duck-ai.md](duck-ai.md) | Duck.ai chat: open, ask, pickers, history, and what each subscription tier gets (header, models, reasoning, upsells) | Duck.ai chat | `duckai-chats`, `subscription`, flag `privacyPro.duckAiPlus` |
| [ai-settings.md](ai-settings.md) | AI Features settings: Search Only vs Search & Duck.ai, New Tab Toggle Position, Duck.ai on/off, shortcuts, Without AI | AI Features | |
| [subscription.md](subscription.md) | Subscription tier, region and status, and how Duck.ai, VPN, PIR, ITR and AppTP change with it | Settings | `subscription` |

Not covered on purpose (the map is scoped to Duck.ai and its entry points): bookmarks, page forms, the rest of
Settings, onboarding, sync, autofill, PIR and VPN beyond their entry points in subscription.md, downloads.

## Adding a feature

1. Find the user entry points in code: the screen's `@ContributeToActivityStarter` params, and every `globalActivityStarter.start(..., <Params>)` call site.
2. Drive it once on a fresh run: reach the starting point through the Common paths, then walk every entry point with `$V ui` and `$V text` on each screen (and `$V web eval` inside pages) to collect real selectors. Never copy selectors from code or Maestro flows without seeing them in `android layout`.
3. Create `<feature>.md` here: an H1, one paragraph of user-visible behaviour, then exactly these H2s: `Sub-features` (short ids, one line each), `How to get to it (user POV)` (every entry point), `Driving it with verify.sh` (commands and the observable end state per entry point), `Gotchas` (ending with which sub-features have been driven and when).
4. Add a row to the table above and remove the area from the "without a recipe" list.
5. Run the recipe end to end inside `record start` / `record stop` and fix what drifted.

