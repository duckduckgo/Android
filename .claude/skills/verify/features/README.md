# DuckDuckGo Android verification map

The maintained recipes for verifying user-facing behaviour of the internal debug build. Read this index,
then the feature file that matches the change. Commands use `V=.claude/skills/verify/verify.sh` from
`../SKILL.md`.

## Baseline preconditions

- `$V doctor` passes: emulator owned by this checkout, install newer than the APK, layout readable.
- The app was launched from a fresh `$V boot` (wiped data) and the SKILL.md baseline ran: notification prompt denied, onboarding skipped, `inputField` visible on the New Tab Page.
- Default settings: omnibar at the top, native input on (internal builds), one tab open.
- Network access works. Use `privacy-test-pages.site` or `www.search-company.site` as targets; they are DDG controlled and stable.

## Driving conventions

- Select by resource id first, text regex second; run `$V ui` after every screen change.
- The browser screen and the New Tab Page use different ids for the same buttons (`fireIconMenu` vs `inputModeWidgetNavFire`), so check which screen you are on.
- Page links and text appear in `android layout`; form fields, values and page state need `$V web eval` / `$V web tap` (SKILL.md, Web content).

## Proof and skip reporting

- Every recipe runs inside `$V rec start <feature-id>` / `$V rec stop`, so each feature has a video.
- Shot before and after the step under test, plus the `assert` or `web eval` lines, all from the same run id.
- A mutation is proved through a second view (tab switcher, bookmarks list), not the screen that made it.
- Report every entry point in the feature's list as verified, failed, or not attempted. Do not report an entry point as verified because another one worked.

## Features

| File | Feature |
|---|---|
| [search-and-navigate.md](search-and-navigate.md) | Typing a URL or query in the native input and loading the result |
| [tabs.md](tabs.md) | Tab switcher, opening and closing tabs |
| [fire-button.md](fire-button.md) | Clearing tabs and site data |
| [browser-menu.md](browser-menu.md) | Overflow menu entries: bookmarks, downloads, settings |
| [web-forms.md](web-forms.md) | Links, form fields and submit inside a page |
| [duck-ai.md](duck-ai.md) | Duck.ai chat: open, ask, pickers, history |

## Adding a feature

1. Drive it once by hand on a fresh run: `$V ui` on every screen (and `$V web eval` for page content) to collect real ids. Never copy ids from code or Maestro flows without seeing them on screen.
2. Create `<feature>.md` here with an H1, one paragraph of user-visible behaviour, then exactly these H2s: `Sub-features` (short ids, one line each), `How to get to it (user POV)` (every entry point), `Driving it with verify.sh` (commands and the observable end state per entry point), `Gotchas`.
3. Add a row to the table above.
4. Run the recipe end to end inside `rec start` / `rec stop` and fix what drifted. List any sub-feature you did not drive under Gotchas.

`/maintain-verification-skill` does this against recent code changes when the map falls behind the app.
