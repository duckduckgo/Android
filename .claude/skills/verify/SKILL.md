---
name: verify
description: Launches the DuckDuckGo Android browser (internal debug build of this checkout) on a dedicated emulator, drives a real user path with the android CLI and adb, and captures screenshots and layout dumps as proof. Use to verify an Android app change works on device, reproduce a UI bug, or prove a feature end to end before a PR. Not for unit tests (./gradlew jvm_tests) or Maestro CI suites (.maestro/).
---

# Verify the DuckDuckGo Android app

Every command goes through `.claude/skills/verify/verify.sh` (run from the repo root, shown as `V` below).
It owns one emulator per checkout, so it never drives the user's phone or their own AVDs.

```bash
V=.claude/skills/verify/verify.sh
```

Prerequisites: JDK 21 at `/opt/homebrew/opt/openjdk@21` (override with `JAVA_HOME_21`), Android SDK at
`~/Library/Android/sdk` with `system-images;android-34;google_apis_playstore;arm64-v8a`, the `android` CLI
on PATH, `jq`, `local.properties` with `sdk.dir`, and submodules (`git submodule update --init`).

## Launch

```bash
$V build      # ./gradlew :app:assembleInternalDebug, a few minutes cold
$V boot       # boots AVD ddg_verify_api34 headless with wiped data; prints "ready: emulator-NNNN"
$V install    # installs the newest internal debug APK and force-stops the old process
$V launch     # cold start of com.duckduckgo.mobile.android.debug; expect "Status: ok"
```

`boot` uses a dedicated `ddg_verify_api34` AVD (8 GB data), created on first use. The very first boot
wipes it, waits 240 s for Google Play services to finish restarting, and saves the `verify-base`
snapshot (about 4.5 minutes, once). Every later boot loads `verify-base` without saving over it, so
each run starts on a settled device without the app, and `install` puts a fresh copy on it. Build and boot are
independent, so run them in parallel when both are needed. The internal flavor is required: native
input, the onboarding skip button and Developer Settings exist only there.

Readiness: `boot` returns only after `sys.boot_completed=1`; the app is ready once
`$V wait --id skipOnboardingButton 30` (first launch) or `$V wait --id inputField` (after onboarding) passes.

## Doctor

```bash
$V doctor
```

Read-only. Fails unless the emulator process is the one this checkout started, the device is online and
booted, the debug package is installed, the install is newer than the APK on disk (otherwise you are
driving a stale build, so run `$V install`), and `android layout` can read the screen. It also prints the
branch, commit and dirty file count so the evidence names the exact code under test. Run it first, and
again whenever the app does something the code says it should not.

## Drive

Navigation uses `android layout` (a JSON list of on-screen elements) and `adb shell input`, wrapped by:

| Command | Does |
|---|---|
| `$V ui [name]` | Lists elements as `resource-id  text/desc  [x,y]  interactions`; with a name, saves the JSON as evidence |
| `$V wait --id X [secs]` | Polls until the element is on screen (default 20 s) |
| `$V assert [--not] --id X` | Passes or fails on the current screen without touching it |
| `$V tap --id X [index]` | Taps the centre of the first (or index-th) match |
| `$V type "text"` / `$V key KEYCODE_ENTER` | Types into the focused field / sends a key |

Selectors: `--id` matches the bare resource name (`inputField`), `--text` and `--desc` take a
case-insensitive regex. Prefer `--id`, then `--text`; never hard-code coordinates, they move with the
omnibar position and the screen size. Run `$V ui` after every screen change instead of guessing ids.

### Web content

`android layout` sees WebView links and text through accessibility, so `$V tap --text "Geolocation"` works
on a page. For form fields, values, icon-only elements or page state, use DevTools:

| Command | Does |
|---|---|
| `$V web targets` | Lists the app's WebView pages (visible/attached, URL) |
| `$V web eval '<js>' [url-regex]` | Evaluates JS in the visible attached page (or the one matching the URL) and prints the JSON result; awaits promises |
| `$V web tap '<css selector>'` | Scrolls the element into view and taps its centre with a real touch (CSS px × devicePixelRatio + the WebView's on-screen bounds) |

Use `web eval` to read and assert, never to act: clicking through JS bypasses the touch path the user
takes. Act with `web tap` and `$V type`. DevTools needs Developer Settings > Web Content Debugging on
(baseline step below); it applies live, no restart.

Baseline from a fresh boot, used by every recipe in [features/](features/README.md):

```bash
$V launch
$V wait --id permission_deny_button 15 && $V tap --id permission_deny_button   # Android 13+ notification prompt
$V wait --id skipOnboardingButton 30 && $V tap --id skipOnboardingButton
$V wait --id inputField                                                         # New Tab Page with native input
```

When the recipe touches web content, also enable DevTools once per run:

```bash
$V tap --id inputModeWidgetNavMenu && $V wait --id settingsMenuItem && $V tap --id settingsMenuItem
# swipe until "Developer Settings" is listed: adb -s <serial> shell input swipe 672 2200 672 900 600
$V tap --text "^Developer Settings$"
$V tap --id trailingSwitch 2          # third switch: "Web Content Debugging"; $V ui shows primaryText to confirm
$V key KEYCODE_BACK; $V key KEYCODE_BACK
```

Then follow the feature file for what you are verifying. Each lists every user entry point; a proof that
uses one entry point does not cover the others.

## Evidence

```bash
$V rec start search-url  # video of the whole recipe; start after the baseline, before the first step
$V shot 03-page-loaded   # PNG via adb screencap + the layout JSON next to it
$V rec stop              # pulls search-url.mp4 (3-minute segments joined with ffmpeg)
$V logcat                # full logcat dump, for crashes or log markers
```

Everything lands in `build/verify/evidence/<run-id>/` (run id printed by `doctor`). Proof standards:

- Every feature recipe is recorded: one `rec start <feature-id>` / `rec stop` pair per recipe, plus shots of the key states. The video shows the path; the shots and asserts are what you cite.
- Drive the real user path (taps and typing), never internal settings, intents or test hooks the user cannot reach, unless the feature file says so. Developer Settings toggles are allowed as setup, not as the thing under test.
- Capture the action and the resulting state: a shot before the step that matters and one after, plus the `assert` or `web eval` output.
- Verify side effects, not just pixels: after a mutation, reach the stored result through a second view (the tab switcher after opening a tab, the bookmarks screen after adding one).
- Look at every PNG you cite. For web content, pair the screenshot with a `web eval` read of the same state.
- Report the commit from `doctor` with the evidence path. A step you could not reach is reported as not verified, with the command that failed.

## Cleanup

```bash
$V cleanup
```

Stops a running recording (keeping the video), removes this emulator's adb forwards, stops only the
emulator whose pid this checkout recorded and deletes `build/verify/state`. Evidence in
`build/verify/evidence/` is kept; delete it by hand when done. Never `adb emu kill` or `pkill` other
devices, and never uninstall the app from a device you did not boot. If a run fails halfway, still run
`$V cleanup` before retrying.

## Gotchas

- `shot` uses `adb screencap`, which targets the run's emulator. `android screen capture` has no device flag and refuses to run whenever another device (the user's phone) is attached.
- The `android` CLI may print an update notice; it is harmless. Do not run `android update` unprompted.
- `android layout` lists only elements with text, an id that carries content, or an interaction, and gives no bounds or class. Bare containers such as `browserLayout` never appear, so assert on a child (the omnibar URL, a title) instead. `web tap` reads the WebView bounds from `uiautomator dump` for this reason.
- Booting a wiped Play Store image and launching straight away gets the app killed ("depends on provider ... in dying proc com.google.android.gms") or crashes it inside the system WebView. That is why boots load `verify-base`. If `verify-base` is ever deleted, the next boot rebuilds it; if the app disappears mid-run, check `adb logcat -b crash` and `$V logcat` before blaming the change.
- With several tabs, older WebViews stay `visible` but `detached` in `web targets`; `web eval` picks the attached one. Pass a URL regex when two are attached.
- `adb install -r` does not restart a running app; `install` force-stops it, but after a manual install run `$V launch`.
- `$V type` sends text through `adb shell input text`; characters like `&`, `'` and `(` need shell escaping.
- A second checkout running this skill picks the next free emulator port, but both share the `ddg_verify_api34` AVD and the emulator refuses a second instance of a running AVD. Finish or clean up one run before starting another.
