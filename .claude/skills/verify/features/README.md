# DuckDuckGo Android verification map

Recipes for verifying user-facing behaviour of the internal debug build. The inventory of what exists is
trapdoor's catalog, generated from this checkout's code; these files are the maintained recipes for the
areas that have one. Commands use `V=.claude/skills/verify/verify.sh` from `../SKILL.md`.

## Baseline preconditions

- `$V doctor` passes: emulator owned by this checkout, bridge and catalog match `HEAD`.
- The app starts past onboarding with one tab on the New Tab Page. trapdoor prepares first-run state on install; if a previous recipe left tabs or data behind, `$V t reset` (only without asking on an emulator the run booted, see SKILL.md) and `$V t goto browser.newtab`.
- Default settings: omnibar at the top, native input on (internal builds), light theme.
- Web Content Debugging on (SKILL.md, Web content). Recipes prove page loads with `web eval`, so this is part of every baseline; `reset` clears it, so switch it on again after one.
- Use `privacy-test-pages.site` and `www.search-company.site` as web targets; they are DDG controlled and stable.

## Driving conventions

- Reach the starting state with `goto`, `browse` and `seed`; drive the step under test with `tap`, `type` and `web tap` on the entry points the file lists.
- Select by `id:` first, then `text:` / `desc:`. Refs from `hierarchy` are fine within one screen but expire on any layout change.
- The New Tab Page and a loaded page use different ids for the same toolbar buttons (`inputModeWidgetNavFire` vs `fireIconMenu`); run `$V t where --json` and `$V t hierarchy --interactive-only` when unsure.

## Proof and skip reporting

- Every recipe runs inside `$V t record start <feature-id>.mp4` / `$V t record stop`, so each feature has a video.
- Shot before and after the step under test (`$V t shot <name>.png`), plus the `assert` or `web eval` results, all in the same run id.
- A mutation is proved through a second view (tab switcher, bookmarks list), not the screen that made it.
- Report every entry point in a file as verified, failed (command and exit code) or not attempted. Do not report one as verified because another worked.

## Features

| File | Feature | Catalog targets |
|---|---|---|
| [search-and-navigate.md](search-and-navigate.md) | Typing a URL or query and loading the result | `browser.newtab`, `browser.main` |
| [tabs.md](tabs.md) | Tab switcher, opening, switching and closing tabs | `tabSwitcher`, `tabs.tab-switcher-menu`, `seed tabs` |
| [fire-button.md](fire-button.md) | Clearing tabs and site data | `browser.data-clearing`, `fire.*`, `browser.single-tab-fire-sheet` |
| [browser-menu.md](browser-menu.md) | Browser menu: every entry and where it lands | `browser.menu-sheet` |
| [duck-ai.md](duck-ai.md) | Duck.ai chat: open, ask, pickers, history, and what each subscription tier gets (header, models, reasoning, upsells) | `browser.duckai-view`, `duckai.*`, `duckchat.*`, `seed duckai-chats` |
| [ai-settings.md](ai-settings.md) | AI Features settings: Search Only vs Search & Duck.ai, New Tab Toggle Position, Duck.ai on/off, shortcuts, Without AI | `duckai.settings`, `duckai.shortcut.settings` |
| [subscription.md](subscription.md) | Subscription tier, region and status, and how Duck.ai, VPN, PIR, ITR and AppTP change with it | `ppro.settings`, `vpn.main`, `duckai.paid-settings`, `seed subscription` |

Catalog areas without a recipe yet (screen + surface counts on 2026-11-13; the map is scoped to Duck.ai and its
entry points, so bookmarks, page forms and the rest of Settings are deliberately left out): onboarding 34, sync 23,
autofill 20, subscriptions 18 (state recipe in subscription.md; onboarding and feedback flows not covered), apptp 18,
designsystem 18, pir 11, feedback 8, networkprotection 7 (entry points in subscription.md), privacy 5, vpn 4, sitepermissions 3, downloads 3. List them with
`$V t list --screens` / `--surfaces`, or `$V t find <area>`.

## Adding a feature

1. Survey it: `$V survey '<name regex>'` (e.g. `'^settings'`) opens every matching catalog target and saves where it landed, its tappable elements and a screenshot to `build/verify/survey/<regex>/`, with a README table of what opened and what did not. A target that is `notReady` usually needs a fixture: `$V t list --fixtures`.
   Then find the user entry points in code: the screen's `@ContributeToActivityStarter` params, and every `globalActivityStarter.start(..., <Params>)` call site. The survey only proves a screen opens; the code tells you how a user reaches it.
2. Drive it once on a fresh run. `goto` the starting point, then walk every user entry point with `$V t hierarchy --interactive-only` on each screen (and `$V web eval` inside pages) to collect real selectors. Never copy selectors from code or Maestro flows without seeing them in the hierarchy.
3. Create `<feature>.md` here: an H1, one paragraph of user-visible behaviour, then exactly these H2s: `Sub-features` (short ids, one line each), `How to get to it (user POV)` (every entry point), `Driving it with verify.sh` (commands and the observable end state per entry point), `Gotchas` (ending with which sub-features have been driven and when).
4. Add a row to the table above and remove the area from the "without a recipe" list.
5. Run the recipe end to end inside `record start` / `record stop` and fix what drifted.

`/maintain-verification-skill` does this against recent code changes when the map falls behind the app.
