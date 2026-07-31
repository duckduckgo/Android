---
name: prepare-site-breakage-triage-repro-build
description: Build, install, launch, screenshot, and cleanup mechanics for automated reproduction of site breakage on Cursor self-hosted Android agents in `ddg-native-android`. Use when setting up a device for automated site breakage triage repro runs.
disable-model-invocation: true
---

## Build and install

- Use `npm run android:build` for build smoke.
- Use `npm run android:install` when the target AVD does not already have DDG installed.
- If an emulator restart loses the install but the APK exists, reinstall with:

```sh
adb install -r android/app/build/outputs/apk/internal/debug/<apk-name>
```

## Emulator

- Default to `ddg_pixel9_api35` as a known-good AVD.
- Start headlessly with explicit software GPU rendering:

```sh
emulator -avd ddg_pixel9_api35 -no-window -no-snapshot-save -no-boot-anim -gpu swiftshader
```

If the emulator crashes, disappears from ADB, or fails to capture screenshots, retry once with:

```sh
emulator -avd ddg_pixel9_api35 -no-window -no-snapshot-save -no-boot-anim -gpu lavapipe
```
Record the GPU mode used in the reproduction summary. Keep the proxy, URL, app package, and screenshot workflow unchanged between attempts.

### ADB reliability
- Wrap long or failure-prone ADB calls with `timeout`, especially `screencap`, `dumpsys`, `uiautomator`, and cleanup `adb shell` commands.
- If a retry starts another AVD, use explicit serial targeting for all later commands:
```sh
adb -s <serial> ...
```

## Load URL

Use the debug package explicitly:

```sh
adb shell am start -W -a android.intent.action.VIEW -d '<url>' com.duckduckgo.mobile.android.debug
```

## Screenshots

Prefer device-file screenshots; avoid `adb exec-out screencap -p` because it may hang.

```sh
adb shell screencap -p /sdcard/screenshot_initial.png
adb pull /sdcard/screenshot_initial.png /opt/cursor/artifacts/<dir>/screenshot_initial.png
```

## System UI recovery

If screenshots are blocked by `Application Not Responding: com.google.android.apps.nexuslauncher`, verify DDG is foreground/responsive first.

```sh
adb shell input keyevent BACK
adb shell am force-stop com.google.android.apps.nexuslauncher
```

Then relaunch the URL activity and report the cleanup in the required output.

## Runtime configuration cleanup

- Remove `/data/local/tmp/webview-command-line` from every AVD touched
- Force-stop DDG, then stop emulators

  
