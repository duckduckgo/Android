---
name: verify-android
description: Verifies DuckDuckGo Android changes on the attached device (or an emulator the user picks). Builds this checkout's internal debug app, drives real user paths with the android CLI and adb (plus DevTools for WebView content), records a video and screenshots per feature, and runs Python proofs with a pass/fail verdict, before and after a change. Use to verify an app change works on device, reproduce a UI bug, or prove a feature end to end before a PR. Not for unit tests (./gradlew jvm_tests) or Maestro CI suites (.maestro/).
---

# Verify the DuckDuckGo Android app

One entry point, `.agents/skills/verify-android/verify.sh` (`V` below, run from the repo root).

```bash
V=.agents/skills/verify-android/verify.sh
```

Driving and evidence use the `android` CLI and `adb`, as the `android-cli` skill's
`references/interact.md` describes: `android layout` for the element tree, `adb shell input` to act,
`screencap` and `screenrecord` for evidence, and Chrome DevTools inside WebViews. Every state a recipe
needs is reached through the UI, the way a user gets there.

Prerequisites: the `android` CLI (`~/.local/bin/android`), JDK 21 at `/opt/homebrew/opt/openjdk@21` (override
with `JAVA_HOME_21`), the Android SDK, `node` 22+, `jq`, `python3`, `local.properties` with `sdk.dir`, and
submodules (`git submodule update --init --recursive`). The fallback AVD needs
`system-images;android-34;google_apis_playstore;arm64-v8a`. The PR images are drawn by `Compose.java` with that
same JDK, so they need nothing more; `ffmpeg` (`brew install ffmpeg`) is optional and makes the poster the video's
last frame instead of the last screenshot.

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

The device decides what the run may do to it. Only on an emulator this run booted may you `$V reset`,
uninstall, or clear data without asking. On the user's phone or an emulator they already had running,
ask first.

## Launch

```bash
$V build     # ./gradlew :app:assembleInternalDebug
$V install   # installs that build, refusing it if the source changed since, and checks the APK hash on the device
            # (--base installs the build from `build --base <ref>` instead; see Prove)
$V install --apk <path>   # an APK this skill didn't build, such as one from CI
$V doctor
```

`install --apk` records only the APK's sha256, marked external, with no source. `doctor` passes it, the
way it passes a base build, and says the app isn't a build of this source; proofs run against it and the
report and `pr-summary` name it "external APK". It must be an internal debug build
(`com.duckduckgo.mobile.android.debug`), or the hash check after installing fails.

`build` works the way Android PR #10551 does. It records the source the APK came from
(the committed tree plus every uncommitted change, outside the agent tooling folders `.claude`, `.agents`
and `.codex`, so editing this skill does not make a build stale) and the APK's sha256, and keeps the APK
in `build/verify/builds/` under its hash. It passes the app's own `-Pautofill-disable-auth-requirement`
and `-Psync-disable-auth-requirement` (`app/build.gradle`), so Passwords and Sync open without the
device's screen lock. `install` uses `adb install -r -d` and keeps the app's data.

A first install on a clean device opens on onboarding. On an emulator this run booted, `$V reset` clears
the data, grants the notification permission (so its prompt doesn't cover onboarding) and taps the internal
build's Skip Onboarding, landing on the New Tab Page; otherwise walk onboarding the way a user does.

`install` reports adb's own reason when it fails. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` means the device
has an internal build signed with another key; uninstalling it deletes the user's app data, so ask.
`INSTALL_FAILED_INSUFFICIENT_STORAGE` means the device is out of space: free some, or pick another
device. Anything else prints adb's last line.

## Doctor

`$V doctor` is read-only. It checks the device is online and set to English (proofs and recipes match
English UI text, so any other language fails here instead of failing checks; it reads
`persist.sys.locale`, then `ro.product.locale`, and only notes it when neither is set), says whether this
run booted it (and so may reset it) or it belongs to the user, that the `android` CLI and `node` are
there, and that the app on the device is the APK `install` put there, built from the current source
(else: build and install again), or a base or external APK installed on purpose. It
prints the branch, commit and dirty file count so the evidence names the code under test. Run it first, and whenever the app does something the code says it should not.

## Prove

A proof is a Python file with a docstring stating the claim and `run(android)`, which drives the app
through this skill's commands and records `android.check(...)` results. A proof is throwaway: write one in
`proofs/` to verify what one PR changes, and only that, using the shared paths in `proofs/steps.py`. Never
commit it (`.gitignore` keeps it out): `pr-summary` puts its source in the PR, and what it taught you about
the app goes into the recipe.

```bash
P=.agents/skills/verify-android/prove.py
$P [--evidence-dir <dir>] .agents/skills/verify-android/proofs/<name>.py   # exit 0 PASS, 1 FAIL (a check failed), 2 ERROR
$V build --base origin/develop                     # the "before" build (git fetch first for the latest)
$P compare [--evidence-dir <dir>] .agents/skills/verify-android/proofs/<name>.py   # base build, then this checkout's
$P publish <attempt or compare folder> [--into <dir>]   # uploads a copy to a fresh auto evidence folder; prints the Evidence URL
$P pr-summary <attempt or compare folder>          # the PR's Verification scenario (or <before> <after> folders)
$P selftest                                        # after changing prove.py: the harness still tells pass from fail
$P selftest --offline                              # only the cases that need no device
```

A run refuses to start unless `doctor` passes, stops with ERROR at the first step that can't be done
(so a broken path never reads as failed checks), and needs at least one check to pass. Each run writes its
own attempt folder (see Evidence and report): the video (in 160 s segments), every screenshot the proof took,
and a `ui.log` of every `verify.sh` command with the `adb` and `android` calls it made and their exit codes.
When a step fails it also saves `layout-at-failure.json` (the element tree) and `at-failure.png`; the error
names them.

The `android` handle a proof gets:

- **Act:** `tap`, `type`, `key`, `scroll`, `open`, `launch`, `reset`, `shot(name)`; a step that fails raises `Error` (ERROR).
- **Read:** `where`, `text`, `lines`, `checked`, `rows(label_id, control_id)` (each list row's label with the control in that same row, for a switch beside an app's name), `windows`, `ui`, `web_targets`, `visible`, `wait`, `web_eval`, `poll(read, until, timeout)`.
- **Check:** `check(what, ok, detail)`; `mark()` right before the action under test, then `expect_pixel(name)` / `expect_no_pixel(name)` (the pixels the debug build logs, independently of the screen) and `expect_log(what, pattern)` (a logcat line matching the regex since the mark).
- **Report:** `note(text)`, `section(title, markdown)`, and `unverified(what, why)` for something the proof couldn't check: listed in the report and `pr-summary`, never a pass and never a fail (a proof with only those is FAIL, as with no checks).
- A module-level `LIVE = ["Duck.ai chat"]` names the live services the proof depends on; the report and `pr-summary` list them.

`build --base <ref>` builds the ref in `build/verify/base-worktree`, a worktree this skill owns and moves
between base commits, and keeps the APK per commit, so the same base is built once. `compare` installs
it, runs the proof, installs this checkout's build and runs it again, each run in its own attempt folder
(`before/` and `after/`) inside one compare folder. The strongest evidence for a fix is FAIL before and PASS
after; for a refactor or a removal, PASS on both is the expectation, so say which one you expect. Its exit
code is the after run's.

## Evidence and report

Each run writes a fresh attempt folder, `<evidence dir>/<stamp>-<proof name>-<id>/`, and never reuses one:

```
<stamp>-<name>-<id>/
  proof.py  proof.json  report.md  index.html
  <device model>/            recording-1.mp4 (more segments after 160 s), the proof's screenshots (each with the
                             .layout.json and .json that `shot` writes), ui.log, key-frames.png, poster.png,
                             and on an error layout-at-failure.json and at-failure.png
```

`compare` writes `<stamp>-<name>-vs-base-<id>/` holding `before/` and `after/` (each an attempt folder as above)
and its own `compare.json`, `report.md`, `index.html` and `before-after.png`. Every link in these files is
relative to the folder, so it works wherever the whole folder is copied or published.

**Where evidence goes.** Without a flag, attempt folders go in `build/verify/evidence/`. `--evidence-dir <dir>`
puts them in any directory outside the checkout (use the one auto's `new-evidence.ts` printed, so `publish`
uploads in place), and `--decision-log <decisions.tsv>` puts them in `proofs/` beside auto's decision log, so the
log can link them. Either one inside the checkout is refused: evidence there could be committed.

**`report.md`** is one verification scenario, in the order a PR presents it: **Verified** (the proof's
docstring, whose first line is the title and the claim a reviewer reads), **Evidence** (relative links per
device: video, key frames, poster, screenshots, logs), **Outcome** (every check with its result and detail,
the error, problems, notes), **Details** (revision and uncommitted file count, build, device model and API,
live services, the proof, where the poster came from, the rerun command), then **Unverified**. Report to the
user from it.

**Images.** `key-frames.png` is the proof's screenshots side by side in the order taken, each captioned with
its name; `poster.png` is the video's last frame with a play badge (the last screenshot without `ffmpeg`);
`before-after.png` pairs the base run's screenshots above this checkout's by name, with "not taken" where one
side has none. An image that can't be drawn (no JDK, an unreadable frame) is listed under Problems in the
report and `pr-summary` and never changes the verdict: the checks are the proof, the images only show it.

**`index.html`** is the entry page a reviewer opens: the claim and verdict, the video playing inline with its
poster, the key frames and screenshots, the checks with failures first, problems, unverified items, and the
details. For a compare it shows both runs side by side, `before-after.png`, and the checks with the changed
ones first. It loads nothing from elsewhere and runs no script. The artifacts viewer renders `report.md`
without `<video>`, so link `index.html` when a video matters.

**Publishing it in a PR.** `publish` needs auto installed and signed in to its store (`upload-evidence.ts
--check` from auto's show-me-your-work scripts says `ready`). auto uploads a whole evidence folder, so
`publish` copies the attempt or compare folder into a fresh one from `new-evidence.ts` and uploads that: only
the run you pass ever leaves the machine. `--into <auto evidence folder>` copies it into that folder instead
(the one auto opened for this change), and everything already in it goes up too. It records each
`index.html`'s served URL as `evidence_url` in `proof.json` and `compare.json` (in the copy and the original),
uploads, and prints the URLs, ending with `Evidence URL:`. The store never replaces a file, so publish a folder
once and rerun the proof for new evidence. The URLs need DuckDuckGo sign-in and point at internal storage only.
Look at a run's screenshots and video before publishing it. `shot` collapses the notification shade first, but
on the user's phone a notification can still appear in the video, so turn on Do Not Disturb there.

`pr-summary <attempt or compare folder>` then prints the scenario in auto's PR format: `### <claim>`,
**Verified**, an evidence table (Before | After for a compare, else the run's own column) with a 200 px
thumbnail per screenshot and the poster linking the video, `[▶ Watch video]` links, the key frames, then
**Outcome**, **Details** with a `[Full report]` link to `index.html`, **Unverified**, and collapsed tables of
every check, the proof source when it isn't committed unchanged, and the rerun commands. Published runs link
their served URLs; an unpublished one links local relative paths and says so on stderr, and auto's PR gate
rejects those. It carries no local paths, logs or device serials, so it's safe for a public PR. A run that
stopped at `doctor` never read the device or the build, so its summary leaves those out rather than guessing.

The PR body keeps the repo template's fields at the top, `Task/Issue URL:` first (the merge and Asana sync
workflows read it), with `Evidence URL (if applicable):` set to the published `index.html` URL. The scenario
goes under `## Verification` inside one `<details><summary>Checks</summary>` collapse, one scenario per proof.
When auto writes the PR, the body goes through its `publish-pr.ts <body.md> --evidence-directory <auto evidence
folder> -- create|edit ...` (`--upload-evidence` instead when there is a decision log): it turns the
`<img src="https://artifacts.go.duckduckgo.com/...">` thumbnails into GitHub attachments, because github.com
can't load images behind DuckDuckGo sign-in. Without auto, drag the images into the PR in the browser.

## Drive

Selectors: `id:<resource-id>`, `text:<exact>`, `text~:<substring>`, `desc:<exact>`, `desc~:<substring>`;
`--index N` (1-based) when several match. Everything reads `android layout`, which lists every element on
screen with its text, interactions and state (checked, focused, selected), tappable or not.

| Command | Does |
|---|---|
| `$V tap <selector> [--index N]` | Taps the element's centre with `adb shell input tap` |
| `$V type <selector> <text>` | Taps the field, waits until it is focused, clears it and types |
| `$V key back\|home\|enter\|tab` / `$V scroll up\|down [times]` | Keys; a slow swipe (up shows content further down) |
| `$V assert visible\|not-visible <selector>`, `assert text <selector> <expected>`, `assert where <Activity>` | Exit code 15 when false |
| `$V wait <selector> [secs]` | Polls until the element is on screen |
| `$V where` | The resumed activity, for example `SettingsActivity`; `-` when the app is not in front |
| `$V ui` | Tappable elements, one per line: top, id, text, description, interactions and state |
| `$V layout` | The raw element tree from `android layout`, as JSON |
| `$V windows` | Text in every window with its centre (`x y`, tab, text), including popups such as the Duck.ai model and reasoning pickers, which `android layout` does not list; tap a row with `adb -s <serial> shell input tap x y` |
| `$V text [<resource-id>]` | Every element with text, with its state; with an id, that element's text or exit 15 |
| `$V checked <resource-id> [index]` | `true`/`false` for a switch, checkbox or radio |
| `$V launch` / `$V restart` | Opens the app from its launcher entry / force-stops and reopens it |
| `$V reset` | Clears app data and skips onboarding to the New Tab Page; only on an emulator this run booted |
| `$V open <url>` | Opens a URL in the app the way another app's link does |
| `$V probe <host,...> [mode] [timeout_ms]` | Connects to each host from the probe app, another app App Tracking Protection can block, and prints its outcome per host (features/app-tracking-protection.md) |

Recipes reach their starting point through the Common paths in [features/README.md](features/README.md):
the browser menu, Settings, AI Features, the developer screens, the tab switcher, a Duck.ai chat. When an
element has no usable id, text or description, `android screen capture --annotate` and
`android screen resolve` (interact.md) find it by its label.

### Web content

`android layout` lists a page's own content but not reliably the WebView itself. Inside pages, use DevTools:

| Command | Does |
|---|---|
| `$V web targets` | The app's WebView pages (visible/attached, URL) |
| `$V web eval '<js>' [url-regex]` | Evaluates JS in the visible attached page and prints the JSON result; awaits promises |
| `$V web tap '<css selector>'` | Scrolls the element into view and taps its centre with a real touch (CSS px × devicePixelRatio + the WebView's position, read from uiautomator) |
| `$V web type '<text>'` | Types into the focused web field |

Read and assert with `web eval`; act with `web tap` / `web type`, never with JS clicks, which skip the
touch path the user takes. DevTools needs Web Content Debugging on, once per run (applies live):

```bash
# Settings > Internal Features > Developer Settings (features/README.md, Common paths)
$V checked trailingSwitch 3          # "Web Content Debugging"; tap it only if this prints false
$V tap id:trailingSwitch --index 3
$V checked trailingSwitch 2          # "Start-up tracing" must stay false
```

## Evidence by hand

Driving a recipe by hand, each feature is one video plus screenshots of its key states, all in
`build/verify/evidence/<run-id>/<device>/` (`doctor` prints it). A proof's captures go to its attempt folder
instead (Evidence and report).

```bash
$V record start search-url.mp4   # screenrecord, before the first step under test; stops by itself at 170 s
$V shot search-loaded.png        # screencap, plus the element tree (.layout.json) and a sidecar naming the activity and commit
$V record stop                   # pulls the video into the evidence folder
```

`shot` captures at once; nothing waits for the screen to settle. After an action, `$V wait` for the
element you expect before shooting, or the picture can show the transition.

Proof standards:

- Every recipe has a video. The screenshots, `assert` exit codes, `text` output and `web eval` output are what you cite.
- Drive the real user path for the step under test. `open`, `reset` and Developer Settings are setup only.
- Capture the action and the resulting state: a shot before the step that matters and one after.
- Verify side effects through a second view (the tab switcher after opening a tab, the New Tab Page after changing a setting).
- Look at every PNG you cite; for web content pair it with a `web eval` read of the same state.
- Report the commit from `doctor` with the evidence path. For a PR, `$P publish` the attempt or compare folder and paste `$P pr-summary` of it, as Publishing it in a PR says, not paths or local screenshots. An entry point you could not drive is reported as not verified, with the failing command and its exit code.
- Use synthetic data only and keep captures on this machine.

## Cleanup

```bash
$V cleanup
```

Undoes only what the run did: stops recordings (keeping the videos), removes the adb forwards it created,
stops emulators it booted, and deletes `build/verify/state`. The user's phone and emulators they started
keep running, with the verified build still installed. Evidence stays in `build/verify/evidence/` (and in any
`--evidence-dir`) and builds in `build/verify/builds/` until you delete them. Never `adb emu kill` or `pkill` devices the run
did not boot. Run `$V cleanup` after a failed run too, before retrying.

## Gotchas

- An app installed from Android Studio or plain Gradle has no install record, so `doctor` fails. `$V build` and `$V install` fix it.
- `doctor` fails on a device set to another language. Switching the user's phone to English changes their settings, so ask first.
- `doctor` fails as stale after any change to app source, committed or not. Changes inside `.claude`, `.agents` and `.codex` don't count. Run `$V build` and `$V install`.
- `android layout` can come back empty during an animation or on some WebView screens; the helpers retry once and then stop with an error. Take a shot, or use `android screen capture --annotate` and `android screen resolve` (interact.md) to find the element by its label.
- A page hides its toolbar once it scrolls, so `id:browserMenu` and `id:tabsMenu` vanish from the layout; `$V scroll down` brings them back.
- `$V key back` on the New Tab Page leaves the app: `$V where` then prints `-` and every tap misses. `$V launch` brings it back.
- Debug builds have a second launcher entry (LeakCanary's "Leaks"). `$V launch` opens the browser's own one by name; tapping the wrong icon or `adb shell monkey` can land in LeakCanary (`$V where` prints `LeakLauncherActivity`).
- `--index` is 1-based. An off-by-one taps the neighbour, so read state with `$V checked` or a screenshot after tapping.
- Text selectors match the element that carries the text, often a child of the row; tapping it taps the row.
- android CLI 1.1 returns `android layout` as windows (status bar, keyboard, app) with nested children; `layout.py` reads every `APPLICATION` window (the app's, its dialogs, and another app's activity on top, such as Android's VPN consent), so the notification shade, status bar and keyboard stay out of selectors, but a dialog's text matches alongside the screen under it. Off-screen elements aren't listed (`--full` would mark them `"off-screen": true`). Its on-device server (`com.android.cli.interact.instrumentation`, listed as "Android CLI Instrumentation Server") is a UI-automation client, so `uiautomator dump` is killed (exit 137) and `$V windows` fails while it runs; read popups with `android layout --device <serial> --flat` instead.
- `$V web tap` reads the WebView's position with `uiautomator dump`, which is killed while another UI-automation client runs on the device, such as a leftover Maestro driver (`dev.mobile.maestro`). On the user's device, ask before stopping it.
- WebView debugging can appear to work with the switch off: `UrlExtractingWebView` enables it process-wide when created. Still switch it on in the baseline so `web` commands do not depend on that.
- Booting a wiped Play Store image and launching straight away gets the app killed ("depends on provider ... in dying proc com.google.android.gms") or crashed inside the system WebView. That is why the fallback AVD settles on its first boot (marker file `verify-settled` in its AVD folder). Don't add `fastboot.forceColdBoot` or snapshot flags to its config; snapshots went stale under cold boots and failed to load.
- With several tabs, older WebViews stay `visible` but `detached` in `web targets`; `web eval` picks the attached one. Pass a URL regex when two are attached.
- `$V web type` escapes spaces only; avoid `&`, quotes and parentheses.
- An AVD can run once at a time. If the user's chosen AVD is already running, it shows up in `devices` as attached; select it with `use` rather than booting it again.
- Selection lives in `build/verify/state/devices` per checkout. Two checkouts driving the same device at once will fight over it; finish one run first.
