# Sync & Backup

The user turns on Sync from Settings > Sync & Backup: with no second device, "Sync This Device Only" creates an account, shows the recovery code with "Download Your Recovery Code" and a "Restore on App Reinstall" switch, then Sync settings read "DuckDuckGo is in sync." with this phone under My Devices, and bookmarks, passwords and Duck.ai chats sync end-to-end encrypted; "Turn Off Sync & Backup" removes the device or, with "Also delete server data?", deletes the account. Each account also has a server-side login audit log, one entry each time a device logs in or is logged out: after every successful sync (account creation, app open, the 3-hourly background worker) the app checks it for logins by other devices ("unseen logins"), and the Device Activity screen, opened from Sync settings, lists it. Most of this has no screen of its own, so the recipe proves it with the checker's logcat line and a second view of the server log and the unseen list, next to what the user sees.

## Sub-features

- `sync-entry` Settings > "Sync & Backup" opens `SyncActivity`, reading "Keep DuckDuckGo in sync!" with the switch off ("Off").
- `sync-setup-this-device` the switch opens `SyncThisDeviceActivity`; "Sync This Device Only" creates the account with no device-auth prompt (the build skips it) and opens `RecoveryCodeActivity`: "<device name> has been added to Sync & Backup.", the recovery code, "Restore on App Reinstall" on. Turning that switch off shows no dialog. "Done" lands on Sync settings, "DuckDuckGo is in sync.", label "On", this device under My Devices.
- `sync-pair-copy-paste` two emulators pair through "Copy Text Code" on Host and "Enter Code" > "Paste Sync Code" on Joiner. Both list the peer, and a test bookmark syncs between them. See [Two-device pairing through copy and paste](#two-device-pairing-through-copy-and-paste).
- `sync-audit-flags-off` with `loginAuditLog` and `loginNotification` off, the check after a sync logs `check result Disabled`, and Sync settings have no Device Activity row.
- `sync-audit-first-check` the first check on an account logs `check result Initialised` and finds nothing: older entries are not counted.
- `sync-device-activity-empty` with `loginAuditLog` on, Sync settings show a "Device Activity" row; with no other device it opens "You have no recent activity.".
- `sync-audit-other-login` another device's login is a `LOGIN` entry in the server log, and the check after the next app open logs `Checked(newLogins=1, renamedLogins=0, logouts=0)`; the unseen list then has 1.
- `sync-audit-background` the same, with the app in the background: the background worker's sync runs the check and finds the login.
- `sync-device-activity` the Device Activity row lists the login: header "Most recently synced or removed devices:", row "Just now · Synced" and the device's name.
- `sync-device-activity-clears-unseen` once Device Activity has loaded, the logins it shows leave the unseen list (`status` reads "Unseen logins: 0").
- `sync-rename-no-login` renaming this device from its row (Edit Name) adds no server entry, and the next check finds 0 new logins.
- `sync-audit-logout` logging the other devices out adds `LOGOUT` entries, and the next check logs the logouts.
- `sync-turn-off-delete` "Turn Off Sync & Backup" > "Turn Off and Delete Server Data" > "Permanently Delete" ends on "Keep DuckDuckGo in sync!" ("Off"), and the account is gone.
## How to get to it (user POV)

- Settings > "Sync & Backup" (`SyncActivity`); the row sits above "Next Steps", a swipe or two down.
- Other entry points open the same screen: the Passwords screen's "Sync Desktop Passwords", the import passwords screen, the bookmarks and passwords Sync promos, the Duck.ai chat tab's Sync promo (`SyncActivityWithAnotherDevice`), a Sync setup link opened in the browser (`SyncActivityFromSetupUrl`), the browser's Sync setup launch (`SyncActivityStartSetupFlow`), the sign-out and error notifications, and the Duck.ai setup handler (`SyncActivityWithEmptyParams` and the other params in `sync-api/.../SyncScreens.kt`). None of these is driven here.
- On Sync settings: the "Sync This Device" switch, "Sync With Another Device", "I Have a Recovery Code", each device row under My Devices (opens `EditDeviceActivity`: "Edit Name", "Sync This Device"), "Device Activity" (`deviceActivityItem`, only while `loginAuditLog` is on) and "Turn Off Sync & Backup" (`deleteAccountItem`).
- The audit log check has no entry point: it runs after each successful sync. Opening the app syncs at once; the background worker's sync is skipped when the last sync was 10 minutes ago or less (`RealSyncScheduler`, whole minutes).
- Internal builds: Settings > Internal Features > "Sync Dev Settings" (`SyncInternalSettingsActivity`), and from it "Open Audit Log Dev Screen" (`SyncAuditLogDebugActivity`).

## Driving it with verify.sh

Preconditions: an emulator, never a phone: the recipe creates and deletes Sync accounts, and on a phone it would sign in to Sync with whatever the app holds. Every run starts with `$V reset` (on an emulator the user started, ask first), then proves the app is empty before it turns Sync on: Saved Sites Settings > "View Bookmarks" shows `savedSiteEmptyHintTitle` "No bookmarks added yet", and Autofill Dev Settings > "View saved logins" shows `emptyPlaceholderTitle` "No passwords saved yet". If either has data, stop with an error: Sync would upload it, and the recovery code restores the account. When a recipe needs data to sync, add fake data only: Autofill Dev Settings' "Add a few sample logins" (logins for `https://fill.dev`), or bookmarks on DDG test pages. Use staging, a new account per run, and delete it at the end, also when the run stops half-way (a proof's `finally`). Turn off "Restore on App Reinstall" before "Done", unless the change is about it: it stores the recovery code in Google Block Store, which an AVD signed in to a Google account backs up. The build must be `$V build`: it passes `-Psync-disable-auth-requirement` and `-Pautofill-disable-auth-requirement`, so Sync and Passwords open without a device-auth prompt.

Sync's setup has no user path (staging, feature flags, a second device, deleting the test account), so this recipe sets up, tears down and reads second views with internal-build hooks: the debug broadcast below and exported dev screens. The steps under test go through the user's path.

**Setup, teardown and second views: the debug broadcast.** Debug builds have `SyncDebugBroadcastReceiver` (`sync-internal`; adb only, through `android.permission.DUMP`):

```bash
adb -s <serial> shell am broadcast -a com.duckduckgo.sync.internal.DEBUG -p com.duckduckgo.mobile.android.debug -f 32 --es id <id> --es command <command> [--es flag <name>] [--ez on true|false]
```

`-f 32` (`FLAG_INCLUDE_STOPPED_PACKAGES`) is needed: without it Android drops the broadcast, with no error, when the app was just installed or force-stopped. `--ez on` defaults to true. Each broadcast logs exactly one line with the tag `SyncDebugBroadcast`: `SyncDebugBroadcast: <id>: <command>: <result>`. Send a new `id` each time and poll `adb logcat -d -s SyncDebugBroadcast` for `SyncDebugBroadcast: <id>: ` (up to 30 s; each command times out after 30 s). `createAccount`, `simulateLogin`, `logOutOthers` and `deleteAccount` refuse to run until `useStaging` is on. `scripts/sync-debug-broadcast-check.sh` checks every command in one run; read it for each command's exact result.

| Command | Does | Result |
|---|---|---|
| `status` | Reads the state | `signedIn=… environment=… loginAuditLog=… loginNotification=… Unseen logins: N` |
| `useStaging` (`on`) | Points Sync at staging or production. If signed in and the server changes, logs out first | `environment=https://sync-staging.duckduckgo.com`, or `logged out; environment=…` |
| `setFlag` (`flag`, `on`) | Sets one `SyncFeature` flag by name, for example `loginAuditLog` | `loginAuditLog=true` |
| `createAccount` | Creates a new account, like Sync Dev Settings' "Create account" (setup only; when turning Sync on is under test, use the UI) | `account created` |
| `simulateLogin` | Logs in a real extra device, "Fake device HH:mm:ss", with no phone behind it | `Logged in "Fake device …" (<id>) and set its name.` |
| `logOutOthers` | Logs out every other device | `Logged out "…"` per device, or `no other devices` |
| `readLog` | Reads and decrypts the newest server entries (the server's default page) | `N entries: N decoded, 0 skipped. …` then the entries |
| `expireSyncDebounce` | Moves the last sync's time back past the 10-minute debounce | `debounce expired: last sync (SUCCESS) moved to …` |
| `runBackgroundSync` | Enqueues the real `SyncBackgroundWorker` once | `enqueued SyncBackgroundWorker` |
| `deleteAccount` | Deletes the account (teardown fallback) | `account deleted` |

**ERROR, not FAIL.** These say nothing about the change under test, so a proof stops with ERROR (exit 2), never FAIL: a broadcast result that starts with `error: ` or `refused: `, or is `Not signed in` when the step needs an account; a result from `simulateLogin`, `logOutOthers`, `readLog` or `deleteAccount` that reports a failure (`Login failed: …`, `Log out failed: …`, `Could not get the device list: …`, `delete failed: …`, `Error: …`); no result line within 30 s; and a checker line `check result Failed(…)` or `check result Discarded` (staging or the network failed, or the state changed under the check). FAIL is for an expected user-visible or logged result that is wrong.

**The steps under test: the user's path.** Turning Sync on and off, renaming the device and opening Device Activity are driven in the UI. The audit log check is proved after a real sync: `$V restart` (app open) or `expireSyncDebounce` then `runBackgroundSync` (the background worker), with the checker's logcat line `Sync-AuditLog: check result …` (`RealAuditLogChecker`) after a mark. Poll logcat for it: up to 30 s after app open, 60 s after `runBackgroundSync` (`expect_log` reads once; use `android.poll`). The audit log dev screen's "Find unseen audit entries" runs the same check without a sync; use it to diagnose, not to prove.

**Screens.** These dev screens are exported; open them with `adb shell am start --activity-clear-top -n com.duckduckgo.mobile.android.debug/<class>` (`--activity-clear-top` stops the back stack growing with each open):

| Screen | Class |
|---|---|
| Sync Dev Settings | `com.duckduckgo.sync.internal.ui.SyncInternalSettingsActivity` |
| Audit log dev screen | `com.duckduckgo.sync.internal.ui.SyncAuditLogDebugActivity` |
| Autofill Dev Settings ("View saved logins", sample logins) | `com.duckduckgo.autofill.internal.AutofillInternalSettingsActivity` |
| Saved Sites Settings ("View Bookmarks") | `com.duckduckgo.savedsites.internal.SavedSitesInternalSettingsActivity` |

Sync settings (`com.duckduckgo.sync.impl.ui.dashboard.SyncActivity`), `EditDeviceActivity`, Passwords and Device Activity are not exported (`SecurityException`). Open Sync settings with Sync Dev Settings' `$V tap id:launchSyncSettingsButton` ("Launch Sync Settings"; it starts `SyncActivity` with no params, like `SyncActivityWithEmptyParams`). Drive the Settings row (`sync-entry`) only when the change is about that entry point: Settings, `$V scroll up` one swipe at a time with a 1 s pause, until `text:Sync & Backup` is visible above the navigation bar (its `syncSetting` id is not in `android layout`).

- **Setup.** `useStaging` with `on` true, `setFlag` `loginAuditLog` and `loginNotification` with `on` false. `status` reads `signedIn=false`, staging, both flags false.
- **Turn on, flags off.** Sync settings: `headlineText` "Keep DuckDuckGo in sync!", `label` "Off". Mark, then `$V tap id:trailingSwitch` opens `SyncThisDeviceActivity`; `$V tap id:syncThisDeviceButton`. After about 3 s, `RecoveryCodeActivity`: `headlineText` "<model> has been added to Sync & Backup.", `recoveryCode`, `downloadCodeButton`, a checked `trailingSwitch` ("Restore on App Reinstall"). Don't take a shot here (Gotchas). `$V tap id:trailingSwitch`: `$V checked trailingSwitch` is `false`, no dialog. `$V tap id:doneButton`: `headlineText` "DuckDuckGo is in sync.", `label` "On", `primaryText` <model> with `secondaryText` "This device", and no `deviceActivityItem`. The account's first sync logs `check result Disabled` (flags off). `readLog` reads "0 entries": creating the account is not a login.
- **First check.** `setFlag` both flags with `on` true, mark, `$V restart`: `check result Initialised`.
- **Device Activity, empty.** Sync settings, `$V scroll up 3`: `deviceActivityItem` ("Device Activity") sits above `deleteAccountItem`. Tap it: `SyncAuditLogActivity`, title "Device Activity", `messageText` "You have no recent activity.".
- **Another device logs in.** `simulateLogin`. `readLog`: "2 entries: 2 decoded, 0 skipped", a `LOGIN` with `name: Fake device ... (desktop) [LATER_INFO]` and a `DEVICE_INFO_ADDED`, same device id. Mark, `$V restart`: `check result Checked(newLogins=1, renamedLogins=0, logouts=0)`. `status` ends with "Unseen logins: 1".
- **Device Activity.** Sync settings, `$V scroll up 3`, `$V tap id:deviceActivityItem`: "Most recently synced or removed devices:", `primaryText` "Just now · Synced", `secondaryText` "Fake device HH:mm:ss". The rows have no ids of their own. Second view: `status` now ends with "Unseen logins: 0": the screen removes the logins it showed from the unseen list once page 1 has loaded (`SyncAuditLogViewModel`).
- **Rename makes no login (control).** Sync settings, tap this device's row (`text:<model>`, under My Devices): `EditDeviceActivity`, `headlineText` <model>. `$V tap id:editThisDeviceNameItem`: dialog "Edit Name". `$V type id:internal_edit_text "<name>"`, `$V tap id:customDialogPositiveButton` ("Done"): after about 3 s `headlineText` and the "Edit Name" row's `secondaryText` read <name>. `readLog` still reads "2 entries". Mark, `$V restart`: `Checked(newLogins=0, renamedLogins=0, logouts=0)`.
- **Background.** `simulateLogin` again, `$V key home`, `expireSyncDebounce`, mark, `runBackgroundSync`: logcat has `Sync-Engine: petition to sync now trigger: BACKGROUND_SYNC`, `petition to sync accepted, syncing now`, then `Checked(newLogins=1, renamedLogins=0, logouts=0)`. `status` ends with "Unseen logins: 1" (Device Activity cleared the first one).
- **Logouts.** `logOutOthers`, mark, `$V restart`: `Checked(newLogins=0, renamedLogins=0, logouts=2)`. `readLog` has two `LOGOUT` entries with the devices' names.
- **Turn off and delete.** Sync settings, `$V scroll up 3` (Gotchas: the last row) and `$V tap id:deleteAccountItem`: the sheet "Turn Off Sync & Backup?" with `deleteServerDataSwitch` on (the only device) and `turnOffAndDeleteButton`. Tap it: "You Can’t Undo This Action." with "Cancel" and "Permanently Delete" (no ids; tap by text). `$V tap "text:Permanently Delete"`: after about 3 s, `headlineText` "Keep DuckDuckGo in sync!", `label` "Off". Second view: `status` reads `signedIn=false`. If the UI path fails, `deleteAccount`.

## Two-device pairing through copy and paste

Use this recipe for joining an existing Android Sync account through manual code entry. Host is the
emulator displaying the linking QR code; Joiner is the emulator pasting its text equivalent. This exercises
copy, paste and the real pairing exchange, not camera scanning or recovery-code restore.

### Setup

Select two distinct emulators using the device-choice rules in [the verification skill](../SKILL.md#choose-the-device).
Ask which second AVD to boot if only one has been selected. Follow the empty-data, staging and reset
preconditions above on both devices. Use a fresh account and fake bookmarks only.

```bash
V=.agents/skills/verify-android/verify.sh
SYNC_HOST_SERIAL='<host-serial>'
SYNC_JOINER_SERIAL='<joiner-serial>'
$V use "$SYNC_HOST_SERIAL" "$SYNC_JOINER_SERIAL"
$V build
$V each install
$V each doctor
```

Send debug broadcasts with the target's explicit serial. Set `useStaging` on both, then confirm `status`
reports staging and `signedIn=false` on each. Set and record the feature flags required by the change on
both devices; do not infer the protocol from the screen's appearance. Create Host's account using the
"Turn on, flags off" UI path above, with the flags appropriate to this run. Disable "Restore on App
Reinstall" before leaving the recovery screen. Keep Joiner signed out. Give Host a distinct test name
through "Edit Name" if the emulators have the same model name.

Clipboard transfer is a prerequisite: the two Android clipboards are separate. Use emulator clipboard
sharing through the desktop, or another clipboard bridge verified on these AVDs. Check the transfer with
harmless text before opening the pairing session. A successful desktop copy alone does not prove the text
reached Joiner's Android clipboard. If no working bridge is available, report the missing capability;
do not substitute a recovery code, deep link, `simulateLogin`, or direct repository call for this path.
There is currently no clipboard-transfer command in `verify.sh`.

### Pair and verify

1. Start a separate recording for each device with `$V on "$SYNC_HOST_SERIAL" record start host-pairing.mp4`
   and `$V on "$SYNC_JOINER_SERIAL" record start joiner-pairing.mp4`. Scope every subsequent command with `on`.
2. On Joiner, open Settings > "Sync & Backup" > "Sync With Another Device". Select "Enter Code" and
   wait for `id:pasteCodeButton` ("Paste Sync Code"). Leave this screen ready before copying the code.
3. On Host, open "Sync With Another Device". If the scanner opens, tap `id:showQrCodeButton`
   ("Show QR Code"). Wait for the QR code and `id:copyCodeButton` ("Copy Text Code").
4. On Host, tap `id:copyCodeButton`, then transfer that clipboard value to Joiner's Android clipboard
   using the bridge checked during setup. Keep Host's pairing screen open. The copied value is the full
   pairing URL; `qrCodeText` can show a different representation. Do not reconstruct the value from that
   label, print it, or include it in proof details or shell command logs.
5. On Joiner, tap `id:pasteCodeButton`. Handle the confirmation dialogs on the device that displays
   them. In the v2 flow, "Sync New Device?" uses "Sync Now" to approve the peer. Check both screens
   for pending confirmation rather than waiting on only one. Do not close or restart either app while
   the exchange is active. Record a timeout or rejection as its own outcome, not successful pairing.
6. Complete the success screens. If a recovery screen appears, disable "Restore on App Reinstall"
   before "Done". Return to Sync settings on both devices. Check "DuckDuckGo is in sync." and verify
   each device list contains this device and the peer. Confirm Joiner's `status` reports `signedIn=true`.
7. Through the browser UI, add a uniquely named fake bookmark for a DDG test page on Host. Wait for
   that bookmark to appear in Joiner's bookmarks, then edit its title on Joiner and verify the new title
   appears on Host. Use a bounded wait and record it in the proof; a device-list entry alone does not
   establish that data sync works.
8. Capture the final device lists and bookmark checks on both devices. Stop both recordings with
   `$V on <serial> record stop`. Keep linking codes, recovery codes and identifiers out of published
   images and check details. Inspect recordings and layout sidecars for those values before publication.

### Cleanup and coverage

In `finally`, stop both recordings even if pairing fails. Delete the fresh staging account through
"Turn Off Sync & Backup" with server-data deletion enabled, or use the `deleteAccount` debug fallback
on a signed-in device. Confirm deletion succeeded; clearing local app data does not delete the account.
Remove any remaining local test state within the reset permissions above, and clear both emulator
clipboards and the desktop clipboard used for transfer. Report cleanup failures.

`verify.sh on` can drive this coordinated recipe by hand. `prove.py` currently runs a proof on one selected
device; selecting two devices does not make it coordinate their pairing. Keep evidence for both roles and
report any manual clipboard transfer. This recipe has been checked against source but has not yet been
driven end to end. Only claim copy/paste pairing after a live run; camera QR scanning remains separate.

## Gotchas

- The recovery code, user id and device ids show on screen, in `$V text` and in `readLog`. Videos and `ui.log` keep them, and `publish` uploads every screenshot, which `pr-summary` then shows as a thumbnail (behind DuckDuckGo sign-in). So take no shot of `RecoveryCodeActivity`, keep ids out of check names and details, and keep the account staging-only, empty of real data and deleted at the end.
- A build from Android Studio's Run button doesn't pass the two auth flags, so turning on Sync or opening Passwords shows a `BiometricPrompt`. Install a `$V build`. The flags don't skip Android's lock screen after a cold boot: an AVD with a PIN stays at `RUNNING_LOCKED` (`dumpsys user`) until someone unlocks it.
- The background worker's sync is skipped ("petition to sync debounced") when the last sync was 10 minutes ago or less (whole minutes), so `runBackgroundSync` right after setup finds nothing. Send `expireSyncDebounce` first.
- `SyncBackgroundWorkerScheduler` enqueues the 3-hourly worker only in `onCreate` of the app process, and only when signed in: right after turning Sync on there is no periodic worker until the app restarts. `runBackgroundSync` enqueues a one-time run of the same worker, so a recipe doesn't depend on the periodic one.
- The background worker waits for the audit log check before it returns (#10669). Before that, Android could freeze the backgrounded app first, and no `check result` line came after "petition to sync accepted". If that line is missing again, a proof reports it as FAIL; don't retry it into a pass.
- Two checks can start together (app open and a background run); the second logs `Sync-AuditLog: check already running, skipped`. Wait for the `Checked(…)` line.
- "Turn Off Sync & Backup?" and the recovery screen's headline contain non-breaking spaces in some strings (`strings-sync.xml`); a `text:` selector typed with normal spaces can miss them. Use ids, or `text~:` with a part that has no space.
- `readLog` writes all entries as one logcat message; logcat cuts a message at about 4 KB, so with many entries the end is missing.
- With more than one device attached, `adb` commands need `-s <serial>`; a proof's `android._adb` adds it.
- `am start` without `--activity-clear-top` adds each dev screen to the back stack. After several opens, `steps.to_browser` (six backs) no longer reaches the browser.
- A swipe in Settings can stop on a row and count as a tap, opening that row's screen; check `$V where` after each scroll. The list also keeps moving for a moment after `$V scroll`: a check right after it can miss a row that the next swipe then moves past.
- Rows near the bottom edge: on Sync settings, `deleteAccountItem` is the last row. When `$V scroll up` stops with it at the bottom edge (top about 2336 on a 2400 px screen), its centre is on the navigation bar, and the tap sends the app home. Scroll up three times first; the row then sits at about 2085 (`deviceActivityItem` at about 1851) and the tap lands. The same happened to the Settings row "Sync & Backup" at 2284.
- Right after `$V reset`, the app can take longer than `reset` waits to show the New Tab Page ("the app did not come to the front"; the launch screen or the home screen in `at-failure.png`). Run `reset` again.
- After `$V restart`, "Tired of cookie pop-ups?" (`CookiePopupOptInActivity`) can open over the browser: it shows once onboarding is done and enough days have passed since install, and clearing data keeps the install date. `steps.dismiss_cookie_prompt` declines it with `id:cookiePopupOptInDeclineButton` ("I'll accept them myself"). Right after a cold start the buttons can be missing from `android layout` for a moment, so wait for that id before tapping it.
- An AVD can also have the production app (`com.duckduckgo.mobile.android`). Its jobs and logs show next to the debug app's (`com.duckduckgo.mobile.android.debug`); match on the package.
- Driven with the android CLI on 2026-11-23 (emulator API 34 google_apis_playstore, `$V build` of `9bd5900fbc`, staging): `sync-setup-this-device`, `sync-audit-flags-off`, `sync-audit-first-check`, `sync-device-activity-empty`, `sync-audit-other-login`, `sync-device-activity`, `sync-device-activity-clears-unseen`, `sync-rename-no-login`, `sync-audit-background`, `sync-audit-logout`, `sync-turn-off-delete`, as 35 checks in a proof that all passed; `sync-entry` driven by hand. Not driven: the highlight of unseen rows; logins from real Windows, Mac or iOS apps; the legacy 3party name (needs a production duck.ai account); Block Store restore after a reinstall; "Download Your Recovery Code"; "Sync With Another Device" and "I Have a Recovery Code"; the other device rows' menus; "Remove Device" (turn off without deleting); paging in Device Activity (unit tests cover it).
