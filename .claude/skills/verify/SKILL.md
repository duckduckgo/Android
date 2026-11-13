---
name: verify
description: Verifies DuckDuckGo Android changes on the attached device (or an emulator the user picks): builds this checkout's internal debug app with the trapdoor bridge, drives real user paths with trapdoor (plus DevTools for WebView content), and records a video and screenshots per feature as proof. Use to verify an app change works on device, reproduce a UI bug, or prove a feature end to end before a PR. Not for unit tests (./gradlew jvm_tests) or Maestro CI suites (.maestro/).
---

# Verify the DuckDuckGo Android app

Two tools, one entry point. [trapdoor](https://github.com/duckduckgo/android-trapdoor) does navigation,
the view hierarchy, taps, typing, asserts, test data, screenshots, video and proofs.
`.claude/skills/verify/verify.sh` (`V` below, run from the repo root) adds what trapdoor does not:
choosing devices and booting emulators, and acting inside WebViews.

```bash
V=.claude/skills/verify/verify.sh
```

Prerequisites: trapdoor installed per its `INSTALL.md` (`~/.local/bin/trapdoor`; its driving skill
`ddg-android-trapdoor` covers every trapdoor command in depth), JDK 21 at `/opt/homebrew/opt/openjdk@21`
(override with `JAVA_HOME_21`), the Android SDK, `node` 22+, `jq`, `local.properties` with `sdk.dir`, and submodules (`git submodule update --init --recursive`).
The fallback AVD needs `system-images;android-34;google_apis_playstore;arm64-v8a`.

## Choose the device

```bash
$V devices   # attached devices (model, internal app installed or not) and the AVDs on this machine
```

1. **One device attached:** use it. Commands pick it up automatically.
2. **Several attached:** ask the user which one or ones to verify on, listing each serial with its model, then `$V use <serial> [<serial>...]`. With more than one, run per-device commands through `$V each <command>` or `$V on <serial> <command>`; evidence is split per device.
3. **None attached:** ask the user which of their AVDs to start (the list from `devices`), then `$V boot <avd>`. Their AVD boots with a window and without saving over its snapshot.
4. **None attached and no AVDs at all:** `$V boot` with no argument creates and starts the fallback AVD `ddg_verify_api34` (API 34, Play Store, 8 GB). Its first boot wipes it and waits 240 s for Google Play services to settle (about 4.5 minutes, once); later boots take about 15 s.

Never reach for `ddg_verify_api34` while the user has devices or AVDs of their own, and never start an
emulator without asking unless rule 4 applies.

The device decides what the run may do to it. Only on an emulator this run booted may you `$V t reset`,
uninstall, or clear data without asking. On the user's phone or an emulator they already had running,
ask first; trapdoor itself refuses `reset` on a physical device without `--yes`.

## Launch

```bash
$V build     # trapdoor refresh --no-pull: builds this checkout with the bridge, installs, smoke-tests
$V install   # reinstalls the last bridge build without rebuilding (e.g. on a second selected device)
$V doctor
```

A fresh worktree's first `build` also generates trapdoor's catalog for it (a few minutes); `refresh` ends
with smoke steps and is ready when its log shows `PASS smoke-where-browser-menu-sheet`. Use `build`
whenever the code changed since the last build; `install` only reinstalls what is on disk, and `doctor`
fails if that no longer matches `HEAD`. Installing keeps the app's data. For a clean start on an emulator
the run booted, `$V t reset` after installing.

`build` installs over whatever internal app the device has. If that fails with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, the device has an internal build signed with another key; uninstalling
it deletes the user's app data, so ask.

## Doctor

`$V doctor` is read-only. It checks the device is online, says whether this run booted it (and so may
reset it) or it belongs to the user, then runs
`trapdoor doctor`: bridge replies, protocol, catalog generated for this checkout, installed catalog and
screens match, and `sourceHead matches HEAD`. It prints the branch, commit and dirty file count so the
evidence names the code under test. Run it first, and whenever the app does something the code says it
should not. A catalog failure prints the exact generation command; run it, then `$V build`.

## Drive

Every trapdoor command goes through `$V t`, which pins the selected device and this checkout and saves
relative capture paths into that device's evidence folder.

| Command | Does |
|---|---|
| `$V t find <words>` / `$V t list --screens\|--surfaces\|--fixtures\|--commands` | The catalog: every registered screen, surface (dialogs, sheets, menus) and fixture |
| `$V t goto <target>` | Opens a screen or surface through the app's own navigation; `$V t where --json` reports where you are |
| `$V t hierarchy --interactive-only` | Tappable Views and Compose nodes with `ref`, `id`, `text`, `description`, bounds |
| `$V t tap <selector>` | `id:inputField`, `text:Bookmarks`, `desc:Tabs`, `ref:0003`; `--index N` when it repeats |
| `$V t type <selector> <text>` / `$V t key back\|home\|enter` | Replaces a native field's text / sends a key |
| `$V t assert visible\|not-visible <selector>`, `assert text <selector> <expected>`, `assert where <target>` | Exit code 15 when false |
| `$V wait <selector> [secs]` | Polls `assert visible` (trapdoor's assert does not wait) |
| `$V t browse --url <url> [--new-tab]` | Submits a URL to the tab without typing in the omnibar |
| `$V t seed <fixture>` / `$V t set theme\|flag ...` / `$V t reset` | Test data and state; `reset` clears app data |

Use `goto`, `browse` and `seed` to reach the starting state of a recipe, never as the proof. The step under
test is driven the way a user does it: taps and typing on the real entry points the feature file lists.
`goto` succeeding proves a screen opens, not that a user can get there.

### Web content

trapdoor's hierarchy stops at the WebView (`id:browserWebView`). Inside pages, use DevTools:

| Command | Does |
|---|---|
| `$V web targets` | The app's WebView pages (visible/attached, URL) |
| `$V web eval '<js>' [url-regex]` | Evaluates JS in the visible attached page and prints the JSON result; awaits promises |
| `$V web tap '<css selector>'` | Scrolls the element into view and taps its centre with a real touch (CSS px × devicePixelRatio + the WebView bounds from trapdoor) |
| `$V web type '<text>'` | Types into the focused web field (trapdoor `type` only targets native fields) |

Read and assert with `web eval`; act with `web tap` / `web type`, never with JS clicks, which skip the
touch path the user takes. DevTools needs Web Content Debugging on, once per run (applies live):

```bash
$V t goto dev.settings
$V t tap id:trailingSwitch --index 2   # third switch is "Web Content Debugging"; confirm with $V t hierarchy
$V t goto browser.main
```

## Evidence

Each feature recipe is one video plus screenshots of its key states, all in
`build/verify/evidence/<run-id>/<device>/` (`doctor` prints it):

```bash
$V t record start search-url.mp4   # before the first step under test; stops by itself at 170 s
$V t shot search-loaded.png        # waits for the UI to settle; writes a .json sidecar with screen and theme
$V t record stop
```

For flows made only of trapdoor commands, `$V t proof <name> -- <cmd> --then <cmd> ...` does the same in
one go: a screenshot per step, a video, and a README of step results. `--baseline <earlier proof>` adds
screenshot comparisons against a previous run, and `--theme dark --font-scale 1.3 --language de` before
the `--` repeats it under visual settings.

Proof standards:

- Every recipe has a video. The screenshots, `assert` exit codes and `web eval` output are what you cite.
- Drive the real user path for the step under test. `goto`, `browse`, `seed` and Developer Settings are setup only.
- Capture the action and the resulting state: a shot before the step that matters and one after.
- Verify side effects through a second view (the tab switcher after opening a tab, the bookmarks screen after adding one).
- Look at every PNG you cite; for web content pair it with a `web eval` read of the same state.
- Report the commit from `doctor` with the evidence path. An entry point you could not drive is reported as not verified, with the failing command and its exit code.
- Use synthetic data only and keep captures on this machine (trapdoor's rule too).

## Cleanup

```bash
$V cleanup
```

Undoes only what the run did: stops recordings (keeping the videos), removes the adb forwards it created,
stops emulators it booted, and deletes `build/verify/state`. The user's phone and emulators they started
keep running, with the verified build still installed. Evidence stays in `build/verify/evidence/` until
you delete it. Never `adb emu kill` or `pkill` devices the run did not boot. Run `$V cleanup` after a
failed run too, before retrying.

## Gotchas

- A device can have this checkout's internal app *without* the bridge (installed from Android Studio or plain Gradle). `trapdoor doctor` then passes its catalog and `sourceHead` checks but fails `bridge replies`; `$V build` fixes it. Confirm with `adb -s <serial> shell dumpsys package com.duckduckgo.mobile.android.debug | grep -c -i trapdoor` (0 means no bridge).
- On a Pixel 7 with Android 17 (2026-11-13), every UI command returns `notReady: could not establish UI safety from device observations`, even with the app focused and the screen unlocked. trapdoor's `UiSafety` reads `mPackageName` only from a line that starts with it, and this Android version prints it mid-line in `dumpsys window sessions`. Until trapdoor handles that, verify on an emulator or another device and report the phone as not verified.
- trapdoor exit codes: 10 `notReady` (retry once), 11 `wrongScreen`, 12 `unavailable`, 14 `staleRef` (refs expire on any layout change; use the fresh hierarchy in the reply), 15 assertion false. Do not repeat a mutation after an ambiguous reply; check `$V t where --json` first.
- trapdoor prepares first-run state on install, so recipes start past onboarding. Onboarding itself is driven through the `onboarding.*` targets and the `seed dax-onboarding` / `onboarding-fixture` fixtures.
- Booting a wiped Play Store image and launching straight away gets the app killed ("depends on provider ... in dying proc com.google.android.gms") or crashed inside the system WebView. That is why the fallback AVD settles on its first boot (marker file `verify-settled` in its AVD folder). Don't add `fastboot.forceColdBoot` or snapshot flags to its config; snapshots went stale under cold boots and failed to load.
- With several tabs, older WebViews stay `visible` but `detached` in `web targets`; `web eval` picks the attached one. Pass a URL regex when two are attached.
- `$V web type` escapes spaces only; avoid `&`, quotes and parentheses.
- An AVD can run once at a time. If the user's chosen AVD is already running, it shows up in `devices` as attached; select it with `use` rather than booting it again.
- Selection lives in `build/verify/state/devices` per checkout. Two checkouts driving the same device at once will fight over it; finish one run first.
