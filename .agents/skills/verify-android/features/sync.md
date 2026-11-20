# Sync & Backup

The user turns on Sync from Settings > Sync & Backup. With no second device, "Sync This Device Only" creates
an account, shows the recovery code with "Download Your Recovery Code" and a "Restore on App Reinstall"
switch, then Sync settings read "DuckDuckGo is in sync." with this phone under My Devices. Bookmarks,
passwords and Duck.ai chats are then synced, end-to-end encrypted. "Turn Off Sync & Backup" removes the
device or, with "Also delete server data?", deletes the account.

Each Sync account also has a server-side login audit log: one entry each time a device logs in or is logged
out. After every successful sync (account creation, app open, the 3-hourly background worker) the app checks it
for logins by other devices ("unseen logins", shown later by a banner and a notification), and the Device
Activity screen shows the log. Most of this has no screen of its own, so the recipe proves it with the checker's
logcat line and a second view of the server log and the unseen list, next to what the user sees.

## Sub-features

- `sync-entry` Settings > "Sync & Backup" opens `SyncActivity`, reading "Keep DuckDuckGo in sync!" with the switch off ("Off").
- `sync-setup-this-device` the switch opens `SyncThisDeviceActivity`; "Sync This Device Only" creates the account with no screen-lock prompt (the build skips it) and opens `RecoveryCodeActivity`: "<device name> has been added to Sync & Backup.", the recovery code, "Restore on App Reinstall" on. "Done" lands on Sync settings, "DuckDuckGo is in sync.", label "On", this device under My Devices.
- `sync-audit-flags-off` with `loginAuditLog` and `loginNotification` off, the check after a sync logs `check result Disabled`.
- `sync-audit-first-check` the first check on an account logs `check result Initialised` and finds nothing: older entries are not counted.
- `sync-audit-other-login` another device's login is a `LOGIN` entry in the server log, and the check after the next app open logs `Checked(newLogins=1, renamedLogins=0, logouts=0)`; the unseen list then has 1.
- `sync-audit-background` the same, with the app in the background: the background worker's sync runs the check and finds the login.
- `sync-device-activity` Device Activity lists the login: header "Most recently synced or removed devices:", row "Just now · Synced" and the device's name.
- `sync-rename-no-login` renaming this device adds no server entry, and the next check finds 0 new logins.
- `sync-audit-logout` logging the other devices out adds `LOGOUT` entries, and the next check logs the logouts.
- `sync-turn-off-delete` "Turn Off Sync & Backup" > "Turn Off and Delete Server Data" > "Permanently Delete" ends on "Keep DuckDuckGo in sync!" ("Off"), and the account is gone.
- Not built on develop yet: the Device Activity entry point in Sync settings, highlighting unseen rows, the "New synced device" banner, and the new-login notification. Their recipes join this file when they merge.

## How to get to it (user POV)

- Settings > "Sync & Backup" (`SyncActivity`); the row sits above "Next Steps", a swipe or two down.
- Other entry points open the same screen: the Passwords screen's "Sync Desktop Passwords", the bookmarks and passwords Sync promos, the sign-out and error notifications, and the Duck.ai setup handler (`SyncActivityWithEmptyParams` and the other params in `sync-api/.../SyncScreens.kt`).
- On Sync settings: the "Sync This Device" switch, "Sync With Another Device", "I Have a Recovery Code", each device row under My Devices, and "Turn Off Sync & Backup" (`deleteAccountItem`).
- Device Activity (`SyncAuditLogActivity`) has no user entry point on develop; open it from the audit log dev screen.
- The audit log check has no entry point: it runs after each successful sync. Opening the app syncs at once; the background worker's sync is skipped when the last sync was less than 10 minutes ago (`RealSyncScheduler`).
- Internal builds: Settings > Internal Features > "Sync Dev Settings" (`SyncInternalSettingsActivity`), and from it "Open Audit Log Dev Screen" (`SyncAuditLogDebugActivity`).

## Driving it with verify.sh

Preconditions: an emulator this run booted, never a phone: the recipe creates and deletes Sync accounts, and on
a phone it would sign in to Sync with whatever the app holds. Every run starts with `$V reset`, then proves the
app is empty before it turns Sync on: Saved Sites Settings > "View Bookmarks" shows `savedSiteEmptyHintTitle`
"No bookmarks added yet", and Autofill Dev Settings > "View saved logins" shows `emptyPlaceholderTitle`
"No passwords saved yet". If either has data, stop with an error: Sync would upload it, and the recovery code (on
screen and in the evidence) restores the account. When a recipe needs data to sync, add fake data only: Autofill
Dev Settings' "Add a few sample logins" (logins for `https://fill.dev`), or bookmarks on DDG test pages. Use
staging, a new account per run, and delete it at the end, also when the run stops half-way (a proof's `finally`).
Turn off "Restore on App Reinstall" unless the change is about it: it stores the recovery code in Google Block
Store, which an AVD signed in to a Google account backs up.

Sync's setup has no user path (staging, feature flags, a second device, deleting the test account), so this recipe sets up, tears down and reads second views with internal-build hooks: the debug broadcast below and exported dev screens. The steps under test go through the user's path.

**Setup, teardown and second views: the debug broadcast.** Debug builds have `SyncDebugBroadcastReceiver`
(`sync-internal`; adb only, through `android.permission.DUMP`):

```bash
adb shell am broadcast -a com.duckduckgo.sync.internal.DEBUG -p com.duckduckgo.mobile.android.debug --es command <command> [--ez on true|false]
```

Each result is one logcat line, `SyncDebugBroadcast: <command>: <result>`; take a timestamp before sending
(`adb shell date +%s.%N`) and read `adb logcat -d -T <timestamp>`. `simulateLogin`, `logOutOthers` and
`deleteAccount` refuse to run until `useStaging` is on.

| Command | Does | Result |
|---|---|---|
| `status` | Reads the state | `signedIn=… environment=… loginAuditLog=… loginNotification=… Unseen logins: N` |
| `useStaging` (`on`) | Points Sync at staging or production (before the account exists) | `environment=https://sync-staging.duckduckgo.com` |
| `setFlags` (`on`) | Sets `loginAuditLog` and `loginNotification` | `loginAuditLog=true loginNotification=true` |
| `simulateLogin` | Logs in a real extra device, "Fake device HH:mm:ss", with no phone behind it | `Logged in "Fake device …" (<id>) and set its name.` |
| `logOutOthers` | Logs out every other device | `Logged out "…"` per device, or `no other devices` |
| `readLog` | Reads and decrypts the newest 100 server entries | `N entries: N decoded, 0 skipped. …` then the entries |
| `ageLastSync` | Moves the last sync's time back past the 10-minute debounce | `last sync (SUCCESS) moved to …` |
| `runBackgroundSync` | Enqueues the real `SyncBackgroundWorker` once | `enqueued SyncBackgroundWorker` |
| `deleteAccount` | Deletes the account (teardown fallback) | `account deleted` |

**The steps under test: the user's path.** Turning Sync on and off is driven in the UI. The audit log check is
proved after a real sync: `$V restart` (app open) or `ageLastSync` then `runBackgroundSync` (the background
worker), with the checker's logcat line `Sync-AuditLog: check result …` (`RealAuditLogChecker`) after a mark.
The audit log dev screen's "Find unseen audit entries" runs the same check without a sync; use it to diagnose,
not to prove.

**Screens.** These dev screens are exported; open them with
`adb shell am start --activity-clear-top -n com.duckduckgo.mobile.android.debug/<class>`
(`--activity-clear-top` stops the back stack growing with each open):

| Screen | Class |
|---|---|
| Sync Dev Settings | `com.duckduckgo.sync.internal.ui.SyncInternalSettingsActivity` |
| Audit log dev screen | `com.duckduckgo.sync.internal.ui.SyncAuditLogDebugActivity` |
| Autofill Dev Settings ("View saved logins", sample logins) | `com.duckduckgo.autofill.internal.AutofillInternalSettingsActivity` |
| Saved Sites Settings ("View Bookmarks") | `com.duckduckgo.savedsites.internal.SavedSitesInternalSettingsActivity` |

Sync settings (`com.duckduckgo.sync.impl.ui.dashboard.SyncActivity`), Passwords and Device Activity are not
exported (`SecurityException`). Open Sync settings with Sync Dev Settings' `$V tap id:launchSyncSettingsButton`
("Launch Sync Settings"), Device Activity with the audit log dev screen's `openSyncAuditLogButton`. Drive the
Settings row (`sync-entry`) only when the change is about that entry point: Settings, `$V scroll up` one swipe at
a time with a 1 s pause, until `text:Sync & Backup` is visible above the navigation bar (its `syncSetting` id is
not in `android layout`).

- **Setup.** `useStaging` with `on` true, `setFlags` with `on` false. `status` reads `signedIn=false`, staging, both flags false.
- **Turn on, flags off.** Sync settings: `headlineText` "Keep DuckDuckGo in sync!", `label` "Off". Mark, then `$V tap id:trailingSwitch` opens `SyncThisDeviceActivity`; `$V tap id:syncThisDeviceButton`. After about 3 s, `RecoveryCodeActivity`: `headlineText` "<model> has been added to Sync & Backup.", `recoveryCode`, `downloadCodeButton`, a checked `trailingSwitch` ("Restore on App Reinstall"). `$V tap id:doneButton`: `headlineText` "DuckDuckGo is in sync.", `label` "On", `primaryText` <model> with `secondaryText` "This device". The account's first sync logs `check result Disabled` (flags off). `readLog` reads "0 entries": creating the account is not a login.
- **First check.** `setFlags` with `on` true, mark, `$V restart`: `check result Initialised`.
- **Another device logs in.** `simulateLogin`. `readLog`: "2 entries: 2 decoded, 0 skipped", a `LOGIN` with `name: Fake device ... (desktop) [LATER_INFO]` and a `DEVICE_INFO_ADDED`, same device id. Mark, `$V restart`: `check result Checked(newLogins=1, renamedLogins=0, logouts=0)`. `status` ends with "Unseen logins: 1".
- **Device Activity.** Audit log dev screen, `$V tap id:openSyncAuditLogButton`: `SyncAuditLogActivity`, title "Device Activity", "Most recently synced or removed devices:", `primaryText` "Just now · Synced", `secondaryText` "Fake device HH:mm:ss". The rows have no ids of their own.
- **Rename makes no login (control).** Sync Dev Settings, `$V scroll up` to `renameDeviceButton`, tap it: dialog "Rename device (type: phone)". `$V type id:internal_edit_text "<name>"`, `$V tap id:customDialogPositiveButton`: `renameDeviceResultTextView` "PATCH ok — 2 devices_v2 returned; name set to "<name>"". `readLog` still reads "2 entries". Mark, `$V restart`: `Checked(newLogins=0, renamedLogins=0, logouts=0)`.
- **Background.** `simulateLogin` again, `$V key home`, `ageLastSync`, mark, `runBackgroundSync`: logcat has `Sync-Engine: petition to sync now trigger: BACKGROUND_SYNC`, `petition to sync accepted, syncing now`, then `Checked(newLogins=1, renamedLogins=0, logouts=0)`. `status` ends with "Unseen logins: 2".
- **Logouts.** `logOutOthers`, mark, `$V restart`: `Checked(newLogins=0, renamedLogins=0, logouts=2)`. `readLog` has two `LOGOUT` entries with the devices' names.
- **Turn off and delete.** Sync settings, `$V scroll up 3` (Gotchas: the last row) and `$V tap id:deleteAccountItem`: the sheet "Turn Off Sync & Backup?" with `deleteServerDataSwitch` on (the only device) and `turnOffAndDeleteButton`. Tap it: "You Can’t Undo This Action." with "Cancel" and "Permanently Delete" (no ids; tap by text). `$V tap "text:Permanently Delete"`: after about 3 s, `headlineText` "Keep DuckDuckGo in sync!", `label` "Off". Second view: `status` reads `signedIn=false`. If the UI path fails, `deleteAccount`.

## Gotchas

- The recovery code, user id and device ids show on screen, in `$V text` and in `readLog`. Evidence (videos, shots, `ui.log`) keeps them, which is why the account is staging-only, holds no real data and is deleted at the end. `pr-summary` doesn't carry them, but keep them out of check names and details, which do.
- The background worker's sync is skipped ("petition to sync debounced") when the last sync was less than 10 minutes ago, so `runBackgroundSync` right after setup finds nothing. Send `ageLastSync` first.
- `SyncBackgroundWorkerScheduler` enqueues the 3-hourly worker only in `onCreate` of the app process, and only when signed in: right after turning Sync on there is no periodic worker until the app restarts. `runBackgroundSync` enqueues a one-time run of the same worker, so a recipe doesn't depend on the periodic one.
- Two checks can start together (app open and a background run); the second logs `check result AlreadyRunning`. Wait for the `Checked(…)` line, not the first `check result` line.
- `am start` without `--activity-clear-top` adds each dev screen to the back stack. After several opens, `steps.to_browser` (six backs) no longer reaches the browser.
- A swipe in Settings can stop on a row and count as a tap, opening that row's screen; check `$V where` after each scroll. The list also keeps moving for a moment after `$V scroll`: a check right after it can miss a row that the next swipe then moves past.
- Rows near the bottom edge: on Sync settings, `deleteAccountItem` is the last row. When `$V scroll up` stops with it at the bottom edge (top about 2336 on a 2400 px screen), its centre is on the navigation bar, and the tap sends the app home. Scroll up three times first; the row then sits at about 2085 and the tap lands. The same happened to the Settings row "Sync & Backup" at 2284.
- Right after `$V reset`, the app can take longer than `reset` waits to show the New Tab Page ("the app did not come to the front"; the launch screen or the home screen in `at-failure.png`). Run `reset` again.
- An AVD can also have the production app (`com.duckduckgo.mobile.android`). Its jobs and logs show next to the debug app's (`com.duckduckgo.mobile.android.debug`); match on the package.
- Not driven, and checks for a person: logins from real Windows, Mac or iOS apps; the legacy 3party name (needs a duck.ai account, which is production); Block Store restore after a reinstall; "Download Your Recovery Code"; "Sync With Another Device" and "I Have a Recovery Code"; the device rows' own menus; "Remove Device" (turn off without deleting); paging in Device Activity (more than 100 entries; unit tests cover it).
- The background check can be lost: after "petition to sync accepted" no `check result` line came, and a new app process started about 35 s later. The worker returns before the check (started on the app scope after the sync) ends, so Android may freeze or kill the backgrounded app first. Seen once in two runs on 2026-11-20; likely an app bug, not proven. A proof reports it as FAIL; don't retry it into a pass.
- Driven on 2026-11-20 (emulator API 34 google_apis_playstore, `$V build` of `905d49d55e`, staging) in two proof runs with this recipe. Run 1, 26/26: every sub-feature but `sync-turn-off-delete`, which the proof skipped (it read the label too early) and the broadcast deleted the account. Run 2, 26/29: every sub-feature, `sync-turn-off-delete` through the UI included, except `sync-audit-background` (the Gotcha above) and the two checks after it, which depend on it. `sync-entry` (the Settings row) was driven by hand. Not driven: turning "Restore on App Reinstall" off.
