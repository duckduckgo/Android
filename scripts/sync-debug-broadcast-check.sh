#!/usr/bin/env bash
# Checks the Sync debug broadcast (sync-internal, debug builds) end to end on a device or emulator.
# Clears the debug app's data, then sends every command and checks each result and the audit log checker's logcat.
# Exit: 0 all passed, 1 a check failed, 2 the run could not finish (no device, no receiver, staging or network).
set -u

PKG=com.duckduckgo.mobile.android.debug
ACTION=com.duckduckgo.sync.internal.DEBUG
SERIAL=""
YES=0
passed=0
failed=0
signed_in_on_staging=0

usage() { echo "usage: $0 [-s <serial>] [--yes]"; exit 2; }
while [ $# -gt 0 ]; do
  case "$1" in
    -s) SERIAL="${2:-}"; [ -n "$SERIAL" ] || usage; shift 2 ;;
    --yes) YES=1; shift ;;
    *) usage ;;
  esac
done

ADB=(adb)
[ -n "$SERIAL" ] && ADB=(adb -s "$SERIAL")

stop() { echo "ERROR $1"; cleanup; summary; exit 2; }

summary() { echo; echo "passed: $passed, failed: $failed"; }

# Prints the result of one broadcast, waiting up to 35 s for the line with its id.
send() {
  local id="$1"; shift
  "${ADB[@]}" shell am broadcast -a "$ACTION" -p "$PKG" -f 32 --es id "$id" "$@" >/dev/null || stop "adb broadcast failed"
  local i line
  for i in $(seq 1 35); do
    line=$("${ADB[@]}" logcat -d -s SyncDebugBroadcast | grep -F "SyncDebugBroadcast: $id: " | tail -1)
    if [ -n "$line" ]; then echo "${line#*SyncDebugBroadcast: $id: }"; return; fi
    sleep 1
  done
  echo ""
}

# expect <id> <description> <regex> <broadcast extras...>
expect() {
  local id="$1" what="$2" regex="$3"; shift 3
  local result
  result=$(send "$id" "$@")
  [ -n "$result" ] || stop "$id: no result within 35 s ($what)"
  if echo "$result" | grep -Eq -- "$regex"; then
    echo "PASS  $id  $what"; passed=$((passed + 1))
  else
    echo "FAIL  $id  $what"; echo "      expected /$regex/"; echo "      got      $result"; failed=$((failed + 1))
  fi
  LAST="$result"
}

# Epoch seconds.millis: a time with a space would be split in two on its way through adb shell.
device_time() { "${ADB[@]}" shell date +%s.%N | tr -d '\r' | cut -c1-14; }

# expect_log <description> <since> <regex> <timeout s>
expect_log() {
  local what="$1" since="$2" regex="$3" timeout="$4" i logs
  for i in $(seq 1 "$timeout"); do
    logs=$("${ADB[@]}" logcat -d -T "$since") || stop "adb logcat failed"
    if echo "$logs" | grep -Eq "Sync-AuditLog: check result (Failed|Discarded)"; then
      stop "audit log check didn't complete ($what): staging or the network"
    fi
    if echo "$logs" | grep -Eq -- "$regex"; then echo "PASS  log  $what"; passed=$((passed + 1)); return; fi
    sleep 1
  done
  echo "FAIL  log  $what"; echo "      no line matching /$regex/ within ${timeout}s"; failed=$((failed + 1))
}

cleanup() {
  [ "$signed_in_on_staging" = 1 ] || return
  signed_in_on_staging=0
  send c1 --es command logOutOthers >/dev/null
  send c2 --es command deleteAccount >/dev/null
}
trap 'cleanup; exit 130' INT TERM

command -v adb >/dev/null || stop "adb not found"
"${ADB[@]}" get-state >/dev/null 2>&1 || stop "no device (use -s <serial> when several are attached)"
"${ADB[@]}" shell dumpsys package "$PKG" | grep -q SyncDebugBroadcastReceiver || stop "$PKG has no SyncDebugBroadcastReceiver: install an internal debug build"

if [ "$YES" != 1 ]; then
  printf "This clears all data of %s on %s, then signs in to staging Sync. Continue? [y/N] " "$PKG" "${SERIAL:-the attached device}"
  read -r answer
  [ "$answer" = y ] || [ "$answer" = Y ] || { echo "stopped"; exit 2; }
fi
"${ADB[@]}" shell pm clear "$PKG" >/dev/null || stop "couldn't clear the app's data"

echo "Signed out, production"
expect s1 "status: signed out, production" '^status: signedIn=false environment=https://sync\.duckduckgo\.com ' --es command status
expect s2 "no command: error" '^null: error: missing command extra$'
expect s3 "unknown command: error with the command list" '^nope: error: unknown command "nope"; known: .*createAccount' --es command nope
expect s4 "setFlag without flag: error" '^setFlag: error: setFlag needs a flag extra$' --es command setFlag
expect s5 "setFlag unknown flag: error with the flag list" '^setFlag: error: unknown flag "nope"; known: .*loginAuditLog' --es command setFlag --es flag nope
expect s6 "createAccount refused on production" '^createAccount: refused: createAccount only runs on staging' --es command createAccount
expect s7 "deleteAccount refused on production" '^deleteAccount: refused: deleteAccount only runs on staging' --es command deleteAccount

echo "Staging setup"
expect s8 "useStaging on" '^useStaging: environment=https://sync-staging\.duckduckgo\.com$' --es command useStaging --ez on true
expect s9 "setFlag loginAuditLog on" '^setFlag: loginAuditLog=true$' --es command setFlag --es flag loginAuditLog --ez on true
expect s10 "setFlag loginNotification on" '^setFlag: loginNotification=true$' --es command setFlag --es flag loginNotification --ez on true
since=$(device_time)
expect s11 "createAccount" '^createAccount: account created$' --es command createAccount
[ "$LAST" = "createAccount: account created" ] && signed_in_on_staging=1
expect_log "account creation sync runs the first check" "$since" 'check result Initialised' 30
expect s12 "status: signed in on staging, flags on" '^status: signedIn=true environment=https://sync-staging\.duckduckgo\.com loginAuditLog=true loginNotification=true Unseen logins: 0$' --es command status
expect s13 "readLog: no entries yet" '^readLog: 0 entries' --es command readLog

echo "Audit log check after real background syncs"
since=$(device_time)
expect s14 "runBackgroundSync" '^runBackgroundSync: enqueued SyncBackgroundWorker$' --es command runBackgroundSync
expect_log "background sync right after the last one is debounced" "$since" 'Sync-Engine: petition to sync debounced' 30
expect s15 "simulateLogin" '^simulateLogin: Logged in "Fake device ' --es command simulateLogin
expect s16 "readLog: the LOGIN" '^readLog: 2 entries: 2 decoded' --es command readLog
expect s17 "expireSyncDebounce" '^expireSyncDebounce: debounce expired: ' --es command expireSyncDebounce
since=$(device_time)
expect s18 "runBackgroundSync" '^runBackgroundSync: enqueued SyncBackgroundWorker$' --es command runBackgroundSync
expect_log "background sync accepted" "$since" 'Sync-Engine: petition to sync accepted' 60
expect_log "check finds the new login" "$since" 'check result Checked\(newLogins=1, renamedLogins=0, logouts=0\)' 60
expect s19 "status: 1 unseen login" 'Unseen logins: 1$' --es command status
expect s20 "logOutOthers" '^logOutOthers: Logged out "Fake device ' --es command logOutOthers
expect s21 "expireSyncDebounce" '^expireSyncDebounce: debounce expired: ' --es command expireSyncDebounce
since=$(device_time)
expect s22 "runBackgroundSync" '^runBackgroundSync: enqueued SyncBackgroundWorker$' --es command runBackgroundSync
expect_log "check sees the logout" "$since" 'check result Checked\(newLogins=0, renamedLogins=0, logouts=1\)' 60

echo "Teardown and switching server"
expect s23 "deleteAccount" '^deleteAccount: account deleted$' --es command deleteAccount
[ "$LAST" = "deleteAccount: account deleted" ] && signed_in_on_staging=0
expect s24 "status: signed out" '^status: signedIn=false ' --es command status
expect s25 "createAccount again" '^createAccount: account created$' --es command createAccount
expect s26 "useStaging off while signed in logs out (leaves an empty staging account)" '^useStaging: logged out; environment=https://sync\.duckduckgo\.com$' --es command useStaging --ez on false
expect s27 "status: signed out, production" '^status: signedIn=false environment=https://sync\.duckduckgo\.com ' --es command status

summary
[ "$failed" = 0 ] || exit 1
