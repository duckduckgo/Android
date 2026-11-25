# DuckDuckGo Android verification map

Recipes for verifying user-facing behaviour of the internal debug build. Each is driven with the android CLI and adb, reaching every state through the UI. Commands use `V=.agents/skills/verify-android/verify.sh` from `../SKILL.md`.

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
- **App Tracking Protection:** Settings, `$V scroll up` until `text:App Tracking Protection` is visible, `$V tap "text:App Tracking Protection"`: `VpnOnboardingActivity` until the user has onboarded, `DeviceShieldTrackerActivity` after (`steps.apptp`).
- **PIR dev settings:** Settings, `$V scroll up` until `text:PIR dev settings` is visible (Internal Features), `$V tap "text:PIR dev settings"`, `$V assert where PirDevSettingsActivity`. Its "Open PIR Dashboard" (`id:pirOpenDashboard`) opens `PirDashboardWebViewActivity` whatever the subscription state.

## Proof and skip reporting

- Every recipe runs inside `$V record start <feature-id>.mp4` / `$V record stop`, so each feature has a video.
- Shot before and after the step under test (`$V shot <name>.png`), plus the `assert` or `web eval` results, all in the same run id (a proof records and shoots into its own attempt folder).
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
| [app-tracking-protection.md](app-tracking-protection.md) | App Tracking Protection: onboarding and VPN consent, on/off, per-app protection, report forms, dev settings, blocking trackers in another app | App Tracking Protection |
| [pir.md](pir.md) | Personal Information Removal: Settings rows, the free scan (landing page, profile steps, scan-only run, results, notification), the subscriber dashboard, dev settings | PIR dev settings, Settings |

Without a recipe yet: bookmarks, page forms, the rest of Settings, onboarding, sync, autofill and downloads.
Not covered on purpose: anything that depends on a paid plan (Duck.ai's paid models, the subscription VPN, ITR),
because the debug build can't reach a subscribed state through the UI. App Tracking Protection is free and has a
recipe; the subscription VPN is a different feature. PIR has a recipe for its free scan; its subscriber steps need a
device already signed in to a subscription.

## Proofs

A proof is a short Python file that drives a recipe and records checks; `../prove.py <proof>` runs it, refuses
to start unless `verify.sh doctor` passes, stops at the first driving step that fails, can check pixels from
logcat after `android.mark()`, and exits 0 PASS, 1 FAIL or 2 ERROR. Each run writes a fresh attempt folder with
the video, screenshots, `report.md`, `index.html` and the PR images (SKILL.md, Evidence and report). The video is
split into segments of 160 s, because screenrecord stops at 170 s.

No proofs are committed. An agent writes one for each change from the feature maps in `../proofs/`, where Git
ignores everything but `steps.py`, the shared paths a proof imports. Add a path to `steps.py` when a second
recipe needs it.

The proof is linked from the PR with `../prove.py pr-summary` (SKILL.md, Prove). It can be as specific as the
change needs (a long journey, a live service, a one-off edge case) because CI never runs it. When a proof keeps its value after the change ships,
ask the agent to turn it into a Maestro flow under `.maestro/` and commit that instead
(`.claude/rules/maestro-ui-tests.md`). If that flow should block a release, promote it through the
[Release AOR](https://app.asana.com/1/137249556945/project/1211760946270935/list/1211768268642986).

After changing `prove.py`, run `../prove.py selftest`: it checks that a wrong expectation is FAIL, a broken step
and a stale build are ERROR, a proof with no checks (or only `unverified` items) isn't a pass, pixels logged
before `mark()` aren't counted, and, with no device (`selftest --offline` runs only these), that `pr-summary` is
auto's scenario format and carries no local path, an evidence dir inside the checkout is refused, and an attempt
folder gets its key frames, poster, `report.md` in scenario order and a self-contained `index.html`.

## Adding a feature

Feature maps are written by the people who know the area best, by asking an agent to map the feature and then
reviewing what it drove. How to prompt for a map, review it, read its evidence and keep it current is in
[How to contribute to verify-android](https://app.asana.com/1/137249556945/project/1174433894299346/task/1220697369265141).
A map is a recipe for reaching and driving a feature and what proves it works; it never tests a specific change.

### What a feature map holds

`<feature>.md` here, with an H1, one paragraph of user-visible behaviour, then exactly these H2s:

- `Sub-features`: short ids, one line each, naming the observable result (`apptp-enabled` the tracker screen with
  the switch on and a `tun` interface up).
- `How to get to it (user POV)`: every entry point a user has, not only the shortest.
- `Driving it with verify.sh`: the commands for each entry point and the observable end state that proves it.
- `Gotchas`: what tripped the agent up (timing, dialogs some devices show, state that survives `reset`), ending
  with which sub-features have been driven and when.

[app-tracking-protection.md](app-tracking-protection.md) is the worked example, added in
[#10591](https://github.com/duckduckgo/Android/pull/10591): it covers onboarding and the VPN consent dialog, the
per-app switches, the dev settings, and blocking a tracker in another app through the probe app (`$V probe`).

### Creating one

1. Find the entry points in code: the screen's `@ContributeToActivityStarter` params and every
   `globalActivityStarter.start(..., <Params>)` call site.
2. Drive each one on an emulator this run booted, collecting selectors from `$V ui` and `$V text` (and
   `$V web eval` in pages). Selectors copied from code or Maestro flows without being seen in `android layout`
   don't count.
3. Write `<feature>.md`, add a row to the Features table above, remove the area from "Without a recipe yet", and
   add a Common path if the feature has a new starting point.
4. Prove the map once by driving every sub-feature with a throwaway proof in `../proofs/`, and hand
   the evidence and the `pr-summary` output to the person who asked. The proof isn't committed (see Proofs).
