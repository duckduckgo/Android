---
name: verify-android
description: Verify an Android app change on a dedicated emulator (or a named real device) by running a proof that drives the installed build, decides pass/fail and writes evidence. Invoke explicitly after implementation or to investigate behavior.
disable-model-invocation: true
---

A verification is a **proof**: a short Python file that acts on the installed app and checks the result, and that would come out differently if the change were wrong. Unit tests passing is not a proof; a proof exercises the app built from this source.

```
V=.claude/skills/verify-android/scripts/verify-android      # from the repo root; every command below uses $V
```

## Prerequisites

macOS or Linux (the scripts are bash and Python; Windows isn't supported), with:
- The Android SDK, found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir` in `local.properties`, or the default location. It needs platform-tools (`adb` is used from `PATH`, else from the SDK), emulator, cmdline-tools, build-tools, a platform, and a `google_apis` system image for the host's architecture (`arm64-v8a` on Apple Silicon or ARM, `x86_64` otherwise). `device start` names the image to install when it's missing.
- The JDK in `.github/.java-version` (what CI builds with, currently 21): on macOS it's found by version; elsewhere `JAVA_HOME` (or `java` on `PATH`) must be at least that.
- These are local tools: CI has no local emulator, so CI doesn't exercise this setup.
- Python 3.9+ and the [Android CLI](https://developer.android.com/tools/agents/android-cli) (`android`) on `PATH`; the harness runs it with `--no-metrics`.
  It leaves its instrumentation server running on the device, and only one UI driver can run at a time: before running Maestro on the same device, `adb shell am force-stop com.android.cli.interact.instrumentation`.
- On Linux, KVM for the emulator.
- A device in English: UI targets match the UI's English text.

## Loop

1. **Find the proof.** `proofs/` holds committed proofs you run as they are (see Proofs below). If one covers the change, use it. Otherwise write a throwaway proof for this change, starting from the closest committed one, and keep it outside the repo (the evidence folder gets a copy). Done when you can name the check that separates pass from fail.
2. **Get a device.** `$V device start` creates (once) and boots this checkout's own rootable emulator from a `google_apis` image, headless, and roots it. Commands use it unless `ANDROID_SERIAL` names another device; name a real device only when the bug needs that model, since proofs wipe the DDG app's data. `$V device stop` shuts it down.
3. **Build and install.** `$V build` assembles `internalDebug` and records the source it was built from (the committed tree and every uncommitted change, outside `.claude/`) and the APK's sha256. `$V install` installs that APK, refuses if the source changed since the build, and checks the installed APK's hash. For any other APK, `$V install --apk <path>`; the evidence marks it external.
4. **Run it.** `$V prove <proof.py> [--param key=value] [--like-moto]`. It refuses to run when the installed app isn't the recorded build of the current source.
5. **Run it before the change too.** The strongest evidence is a proof that fails on the base branch and passes on this one:
   ```
   $V build --base origin/develop && $V install --base && $V prove <proof.py>     # before: expect FAIL
   $V install && $V prove <proof.py>                                             # after: expect PASS
   ```
   `build --base` builds the ref (resolved locally: `git fetch` first for the latest) in a worktree the tool owns under the state directory, initialising submodules and copying `local.properties`. The worktree is reused, so later base builds are incremental; APKs are kept per commit, so the same base is built once. The report labels the build `base <ref> (<sha>)`. When the change shouldn't alter behavior (a removal or a refactor), both runs should pass: say that's the expectation.
6. **Report.** For the PR's Verification section, paste the output of `$V pr-summary [<before-evidence>] <after-evidence>`: a verdict line, the checks that changed between the runs, the device, builds and live services, collapsed full results, the proof's source when it isn't committed, and rerun commands. It carries no local paths, logs or screenshots, so it's safe for a public PR. For yourself, each evidence folder's `report.md` has the same run in full.

Exit codes: `0` pass (at least one check, none failed), `1` a check failed, `2` error: no device, stale or wrong build, a setup step failed, the network failed before the behavior was exercised, or the proof raised. An error is never a pass.

## Writing a proof

```python
"""Turning AppTP on starts its VPN."""            # required: what it proves, user-visible
from verify_android import steps

LIVE = []                                        # live services the proof depends on; listed in the evidence

def run(android):
    steps.fresh_start(android)                   # app data cleared, AppTP off
    start = android.mark()                       # logs and pixels count from here
    steps.enable_apptp(android)
    android.expect_pixel("m_vpn_ev_start_attempt_success_c", since=start)
    android.check("AppTP's tunnel is up", android.tunnel_up())
```

What `android` gives you (`lib/verify_android/harness.py`):
- **Act:** the UI through the Android CLI: `launch()`, `tap(target, wait=10, first=False)`, `type(text, target, submit=False)`, `scroll_to(target)`, `key(name)`, `hide_keyboard()`. A target is the CLI's selector (`id=skipOnboardingButton`, `text=/^OK$/`, `id=foo,text=Continue`): strings match as case-insensitive substrings, so anchor a `/regex/` when a short text could match a longer one. An action that fails is a setup error (exit 2) and saves `layout-at-failure.json`; every CLI call goes to `ui.log`. Also `broadcast(action, **extras)`; `shell(...)`; `probe(hosts, mode)` connects as a third-party app (the probe, `probe/`) and returns each host's outcome.
- **Read:** `layout()` (every element on screen, in every window), `visible(target)`, `wait_for(*targets, timeout=30)` (the first to appear, or None), `logs(since)`, `pixels(since)`, `tunnel_up()`, `param(name, default)` for `--param`.
- **Check:** `check(what, ok, detail)`, `expect_visible(what, target, timeout=30)` (when what's on screen is the behavior under proof), `expect_log(what, pattern)`, `expect_pixel(name)`, `expect_no_pixel(name)`. Call `mark()` right before the action under test; reads without a mark fail.
- **Evidence:** `shot(name)`, `save(name, text)`, `note(text)`, `unverified(what, why)`, `section(title, markdown)` for report tables.

Rules:
- Act through the user's path for the behavior under proof (the UI, the probe as another app). `shell`/`broadcast` set up or read back.
- Confirm every end state with an independent read: a log line, a pixel, the tunnel, a probe result. Taps that didn't fail are not proof.
- Debug builds log every pixel they fire (`Pixel sent:` / `Pixel enqueued:`), and also send it to DDG as a dev pixel.
- Raise `verify_android.Error` for setup problems (exit 2), never a failed check.

Shared steps (`lib/verify_android/steps.py`) handle app quirks once: `fresh_start`, `enable_apptp`. Add a step when a second proof needs the same quirk. `enable_apptp` shows skipping onboarding, a dialog that only some devices show, and opening Settings. The repo's own Maestro flows in `.maestro/` are a source of steps (ids, texts, order), translated to these calls.

## Proofs

| Proof | What it proves | Notes |
|---|---|---|
| `proofs/apptp.py` | AppTP blocks trackers by the TLS server name in every segmentation case | `references/apptp.md`; `--like-moto` for the moto g(60) TCP settings |

## Evidence

Each run writes `~/.ddg-verify-android/<checkout>/evidence/<stamp>-<proof>/` (`VERIFY_ANDROID_HOME` moves the root): `proof.py`, `report.md`, `manifest.json` (verdict, the proof and its params, device and API, the installed build's commit, dirty flag and sha256, the source fingerprint at run time, live services, every check, rerun command), files the proof saved, `final.png`, and a screen recording of the whole run as `video-1.mp4` (one clip per 3 minutes, `video-2.mp4` and on; a recording that fails is listed as none, never fails the run). Evidence never goes in the repo; `pr-summary` leaves screenshots and videos out. Device settings a run changes (such as `rmem_max`) are restored when it ends, pass or fail; a run killed before that is undone by the next one.

## Check the tool itself

```
$V selftest                       # a few minutes; add --with-broken-build for the full check
```

Run it after changing this skill, on a new machine, or after an SDK or emulator update. It builds and installs if needed, then checks: `tests/must_fail.py` (logs before `mark()` don't count, every pixel log format is read, a probe that never connected is refused), `proofs/apptp.py` passes on this build and the device is restored, and the tool refuses a stale build, an app it didn't install and a missing device. `--with-broken-build` also builds the app against netguard 1.10.2 (once, then cached) and checks `proofs/apptp.py --like-moto` fails on exactly the window-mode trackers: proof the checks catch a real bug, not only that they pass. It ends with `"ok": true` or names the step that failed.

## Disk space

Builds (about 180 MB each), the base worktree (a full checkout) and evidence live under the state directory. `$V cleanup` removes APKs no record points at and evidence older than 30 days (`--evidence-days N`); `$V cleanup --all` also removes the base worktree, this checkout's emulator and every file the tool created.

Internal builds register debug broadcast receivers in the `-internal` modules (`adb shell am broadcast -a <action>`); grep `InternalFeatureReceiver` for the list.
