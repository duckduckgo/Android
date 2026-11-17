#!/usr/bin/env bash
# Verification driver for the DuckDuckGo Android app. Picks the device(s) to verify on, leaves
# navigation, interaction and capture to trapdoor (github.com/duckduckgo/android-trapdoor), and adds
# what trapdoor does not do: device selection and emulator lifecycle, and acting inside WebViews.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$SDK/platform-tools:$PATH"

# DDG_TRAPDOOR_PACKAGE=com.duckduckgo.dev.android drives a build made with the subscriptions staging patch.
PKG="${DDG_TRAPDOOR_PACKAGE:-com.duckduckgo.mobile.android.debug}"
APK_DIR="$REPO/app/build/outputs/apk/internal/debug"
VERIFY_ROOT="$REPO/build/verify"
STATE="$VERIFY_ROOT/state"
EVIDENCE_ROOT="$VERIFY_ROOT/evidence"

VERIFY_AVD="ddg_verify_api34"
SETTLED_MARKER="$HOME/.android/avd/$VERIFY_AVD.avd/verify-settled"
SETTLE_SECS="${SETTLE_SECS:-240}"
VERIFY_IMAGE="system-images/android-34/google_apis_playstore/arm64-v8a/"

die() { echo "verify: $*" >&2; exit 1; }

online_serials() { "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'; }
user_avds() { "$EMULATOR" -list-avds 2>/dev/null | grep -v "^$VERIFY_AVD$" || true; }
selected() { [[ -f "$STATE/devices" ]] && cat "$STATE/devices" || true; }
dstate() { local d="$STATE/dev/$1"; mkdir -p "$d"; echo "$d"; }
owned() { [[ -f "$STATE/dev/$1/pid" ]]; }
label() {
  local s="$1" name
  if [[ "$s" == emulator-* ]]; then name="$("$ADB" -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
  else name="$("$ADB" -s "$s" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"; fi
  echo "${name:-$s}"
}

new_run() { mkdir -p "$STATE"; [[ -f "$STATE/run" ]] || date +%Y%m%d-%H%M%S > "$STATE/run"; }
run_id() { [[ -f "$STATE/run" ]] || die "no active run; run: verify.sh devices"; cat "$STATE/run"; }

select_devices() {
  new_run
  printf '%s\n' "$@" > "$STATE/devices"
  local s; for s in "$@"; do echo "selected $s ($(label "$s"))"; done
}

# The device commands act on: VERIFY_SERIAL (set by `each` and `on`), else the first selected device.
# With nothing selected, a single attached device is selected automatically; anything else needs a choice.
serial() {
  if [[ -n "${VERIFY_SERIAL:-}" ]]; then echo "$VERIFY_SERIAL"; return; fi
  local first; first="$(selected | head -1)"
  if [[ -n "$first" ]]; then echo "$first"; return; fi
  local online; online="$(online_serials)"
  case "$(printf '%s' "$online" | grep -c . || true)" in
    1) select_devices "$online" >&2; echo "$online" ;;
    0) die "no device attached; run: verify.sh devices, then boot one of the listed AVDs" ;;
    *) die "several devices attached; run: verify.sh devices, then verify.sh use <serial>..." ;;
  esac
}

evidence_dir() {
  local s; s="$(serial)"
  local d="$EVIDENCE_ROOT/$(run_id)/$(label "$s" | tr -c 'A-Za-z0-9._-\n' '_')"
  mkdir -p "$d"; echo "$d"
}
a() { "$ADB" -s "$(serial)" "$@"; }
td() {
  command -v trapdoor >/dev/null || die "trapdoor not installed; see github.com/duckduckgo/android-trapdoor INSTALL.md"
  trapdoor --device "$(serial)" --android-root "$REPO" "$@"
}

cmd_devices() {
  local s sel; sel="$(selected)"
  echo "attached:"
  local any=0
  for s in $(online_serials); do
    any=1
    local app="no app"
    "$ADB" -s "$s" shell pm path "$PKG" >/dev/null 2>&1 && app="internal app installed"
    printf '  %-16s %-22s %-22s %s%s\n' "$s" "$(label "$s")" "$app" \
      "$( [[ "$s" == emulator-* ]] && echo emulator || echo physical)" \
      "$(grep -qx "$s" <<<"$sel" && echo ", selected" || true)"
  done
  (( any )) || echo "  none"
  echo "AVDs on this machine:"
  "$EMULATOR" -list-avds 2>/dev/null | sed 's/^/  /' | grep . || echo "  none"
}

cmd_use() {
  (( $# )) || die "usage: verify.sh use <serial>..."
  local s online; online="$(online_serials)"
  for s in "$@"; do grep -qx "$s" <<<"$online" || die "$s is not attached and online"; done
  select_devices "$@"
}

# Runs a verify.sh command on one selected device, or on every selected device in turn.
cmd_on() { local s="${1:?usage: verify.sh on <serial> <command>...}"; shift; VERIFY_SERIAL="$s" "$0" "$@"; }
cmd_each() {
  (( $# )) || die "usage: verify.sh each <command>..."
  local s rc=0
  for s in $(selected); do echo "== $s ($(label "$s"))"; VERIFY_SERIAL="$s" "$0" "$@" || rc=1; done
  return "$rc"
}

# The fallback emulator, created only when the user has no AVDs of their own.
ensure_verify_avd() {
  local dir="$HOME/.android/avd/$VERIFY_AVD.avd"
  [[ -f "$dir/config.ini" ]] && return
  [[ -d "$SDK/$VERIFY_IMAGE" ]] || die "missing $VERIFY_IMAGE; install it with the SDK Manager"
  mkdir -p "$dir"
  cat > "$dir/config.ini" <<EOF
AvdId=$VERIFY_AVD
avd.ini.displayname=DDG verify API 34
abi.type=arm64-v8a
hw.cpu.arch=arm64
image.sysdir.1=$VERIFY_IMAGE
tag.id=google_apis_playstore
PlayStore.enabled=true
hw.device.name=pixel_7
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=420
hw.ramSize=4096
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
disk.dataPartition.size=8G
EOF
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=android-34\n' "$dir" "$VERIFY_AVD" \
    > "$HOME/.android/avd/$VERIFY_AVD.ini"
  echo "created AVD $VERIFY_AVD"
}

# boot <avd>: starts one of the user's AVDs (ask them first) without saving over its snapshot.
# boot with no AVD: only when the user has none, creates and starts the fallback ddg_verify_api34.
cmd_boot() {
  local avd="${1:-}"
  if [[ -z "$avd" ]]; then
    [[ -z "$(user_avds)" ]] || die "this machine has AVDs; ask the user which to start, then: verify.sh boot <avd>
$(user_avds | sed 's/^/  /')"
    ensure_verify_avd
    avd="$VERIFY_AVD"
  fi
  "$EMULATOR" -list-avds | grep -qx "$avd" || die "no AVD named $avd"
  local port=5580 boot_args fresh_base=0
  while "$ADB" devices | grep -q "emulator-$port"; do port=$((port + 2)); done
  if [[ "$avd" == "$VERIFY_AVD" ]]; then
    # A wiped Play Store image restarts Google Play services for minutes after boot and kills apps that
    # use its providers. So the first boot settles once; later boots cold-boot that settled disk.
    if [[ -f "$SETTLED_MARKER" ]]; then boot_args=(-no-snapshot-load -no-snapshot-save)
    else boot_args=(-wipe-data -no-snapshot-load -no-snapshot-save); fresh_base=1; fi
  else
    boot_args=(-no-snapshot-save)
  fi
  local s="emulator-$port" d
  d="$(dstate "$s")"
  "$EMULATOR" -avd "$avd" "${boot_args[@]}" -no-audio -no-boot-anim -port "$port" > "$d/emulator.log" 2>&1 &
  echo $! > "$d/pid"
  echo "booting $avd as $s (pid $(cat "$d/pid"))"
  "$ADB" -s "$s" wait-for-device
  local booted=0
  for _ in $(seq 1 150); do
    [[ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && { booted=1; break; }
    sleep 2
  done
  (( booted )) || die "$s did not finish booting; see $d/emulator.log"
  if (( fresh_base )); then
    echo "first boot: settling Google Play services for ${SETTLE_SECS}s"
    sleep "$SETTLE_SECS"
    touch "$SETTLED_MARKER"
  fi
  new_run
  grep -qx "$s" <<<"$(selected)" || echo "$s" >> "$STATE/devices"
  echo "ready: $s"
}

# Builds this checkout with the trapdoor bridge, installs it on the device and smoke-tests it.
# Puts the app in a subscription state and restarts it so every in-memory flow reads the new state.
# `none` removes a seeded subscription; anything else goes to `seed subscription` (--tier, --region, ...).
cmd_subscription() {
  local out
  if [[ "${1:-}" == "none" ]]; then out="$(td --json seed subscription-none)"
  else out="$(td --json seed subscription "$@")"; fi  # seeding, like cmd_seed
  echo "$out"
  grep -q '"error"' <<<"$out" && die "seed refused"
  cmd_restart
}

# --- Build identity, the way verify-android (#10551) does it ------------------------------------------
# A build records the source it came from (the committed tree plus every uncommitted change, outside the
# agent tooling folders, so editing this skill does not make a build stale) and the APK's sha256. install refuses a
# stale build and checks the hash on the device; doctor refuses an app this tool did not install.

BUILDS="$VERIFY_ROOT/builds"

source_fingerprint() {
  python3 -I - "$REPO" <<'PY'
import hashlib, json, subprocess, sys
repo = sys.argv[1]
git = ["git", "-C", repo]
run = lambda *a: subprocess.run(git + list(a), check=True, capture_output=True).stdout
head = run("rev-parse", "HEAD").decode().strip()
# Top-level tree entries hash everything below them, so this covers the whole tree outside .claude.
# Agent tooling (.claude, .agents, .codex) is not app source: changing it must not make a build stale.
TOOLING = (".claude", ".agents", ".codex")
EXCLUDE = [f":(exclude){t}" for t in TOOLING]
tree = [l for l in run("ls-tree", "HEAD").decode().splitlines() if l.split("\t", 1)[1] not in TOOLING]
h = hashlib.sha256("\n".join(tree).encode())
h.update(run("diff", "HEAD", "--binary", "--", ".", *EXCLUDE))
for name in sorted(n for n in run("ls-files", "-z", "--others", "--exclude-standard", "--", ".", *EXCLUDE).decode().split("\0") if n):
    h.update(name.encode())
    h.update(hashlib.sha256(open(f"{repo}/{name}", "rb").read()).hexdigest().encode())
dirty = bool(run("status", "--porcelain", "--", ".", *EXCLUDE).strip())
print(json.dumps({"head": head, "dirty": dirty, "fingerprint": h.hexdigest()}))
PY
}

file_sha256() { shasum -a 256 "$1" | cut -d' ' -f1; }
installed_sha256() {
  local path; path="$(a shell pm path "$PKG" | tr -d '\r' | sed -n 's/^package://p' | head -1)"
  [[ -n "$path" ]] && a shell sha256sum "$path" | tr -d '\r' | cut -d' ' -f1
}
trapdoor_root() { python3 -I -c 'import os,sys; print(os.path.dirname(os.path.dirname(os.path.realpath(sys.argv[1]))))' "$(command -v trapdoor)"; }

# Builds internalDebug with trapdoor's bridge added by its Gradle init script (seed and set flag need it),
# keeps a copy named by its hash, and records it.
cmd_build() {
  command -v trapdoor >/dev/null || die "trapdoor not installed (its bridge is what seed and set flag talk to); see github.com/duckduckgo/android-trapdoor INSTALL.md"
  local source bridge newest kept digest
  source="$(source_fingerprint)"; bridge="$(trapdoor_root)"
  [[ -f "$bridge/gradle/trapdoor-bridge.init.gradle" ]] || die "no trapdoor-bridge.init.gradle under $bridge"
  echo "building :app:assembleInternalDebug with the trapdoor bridge ..." >&2
  (cd "$REPO" && DDG_ANDROID_ROOT="$REPO" DDG_TRAPDOOR_BRIDGE="$bridge" ./gradlew -q \
    --init-script "$bridge/gradle/trapdoor-bridge.init.gradle" :app:assembleInternalDebug \
    -Pautofill-disable-auth-requirement -Psync-disable-auth-requirement -Pforce-default-variant) || die "build failed"
  # The file name carries the version, so APKs of earlier versions stay in the folder: the newest is this build.
  newest="$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -1)"
  [[ -n "$newest" ]] || die "the build produced no internalDebug APK"
  digest="$(file_sha256 "$newest")"; mkdir -p "$BUILDS"; kept="$BUILDS/${digest:0:16}.apk"
  cp "$newest" "$kept"
  python3 -I -c 'import json,sys; r=json.loads(sys.argv[1]); r.update(apk=sys.argv[2], sha256=sys.argv[3], bridge=sys.argv[4], built_at=sys.argv[5]); print(json.dumps(r))' \
    "$source" "$kept" "$digest" "$(git -C "$bridge" rev-parse --short HEAD 2>/dev/null || echo unknown)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$BUILDS/build.json"
  echo "built ${digest:0:16} from $(git -C "$REPO" rev-parse --short HEAD)$(grep -q '"dirty": true' "$BUILDS/build.json" && echo ' (with uncommitted changes)')"
}

# Installs the recorded build, refusing one the source has moved past, and checks the APK on the device.
cmd_install() {
  local record apk want s; record="$BUILDS/build.json"
  [[ -f "$record" ]] || die "no recorded build: run verify.sh build"
  [[ "$(jq -r .fingerprint "$record")" == "$(source_fingerprint | jq -r .fingerprint)" ]] || die "stale build: the source changed since verify.sh build; build again"
  apk="$(jq -r .apk "$record")"; want="$(jq -r .sha256 "$record")"; s="$(serial)"
  a install -r -d -t "$apk" >/dev/null || die "install failed; INSTALL_FAILED_UPDATE_INCOMPATIBLE means an internal build signed with another key is installed, and uninstalling it deletes the user's app data, so ask"
  [[ "$(installed_sha256)" == "$want" ]] || die "the installed APK doesn't match the one just installed"
  jq --arg serial "$s" --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '. + {serial: $serial, installed_at: $at}' "$record" > "$(dstate "$s")/installed.json"
  a shell am force-stop "$PKG"
  echo "installed ${want:0:16} on $s"
}

# Read-only: the device, the toolchain, and that the app on it is the build of the current source this tool installed.
cmd_doctor() {
  local ok=1 s rec; s="$(serial)"
  "$ADB" devices | grep -q "^$s[[:space:]]*device" && echo "PASS $s online ($(label "$s"))" || { echo "FAIL $s not online"; ok=0; }
  if owned "$s"; then
    kill -0 "$(cat "$STATE/dev/$s/pid")" 2>/dev/null && echo "INFO emulator started by this run" || { echo "FAIL emulator process gone"; ok=0; }
  else echo "INFO user's device: no reset, uninstall or data clearing without asking"; fi
  command -v android >/dev/null && echo "PASS android CLI for driving" || { echo "FAIL android CLI not on PATH"; ok=0; }
  command -v node >/dev/null && echo "PASS node $(node --version) for web commands" || { echo "FAIL node not on PATH"; ok=0; }
  echo "INFO worktree $(git -C "$REPO" rev-parse --abbrev-ref HEAD)@$(git -C "$REPO" rev-parse --short HEAD), dirty files: $(git -C "$REPO" status --porcelain | wc -l | tr -d ' ')"
  echo "INFO evidence -> $(evidence_dir)"
  rec="$(dstate "$s")/installed.json"
  if [[ ! -f "$rec" ]]; then echo "FAIL nothing installed by verify.sh on this device: run verify.sh build, then verify.sh install"; ok=0
  elif [[ "$(installed_sha256)" != "$(jq -r .sha256 "$rec")" ]]; then echo "FAIL the app on the device isn't the APK verify.sh installed; install again"; ok=0
  elif [[ "$(jq -r .fingerprint "$rec")" != "$(source_fingerprint | jq -r .fingerprint)" ]]; then echo "FAIL stale build: the source changed since it was built; build and install again"; ok=0
  else echo "PASS installed app is the build of the current source ($(jq -r '.sha256[0:16]' "$rec"), $(jq -r '.head[0:10]' "$rec")$(jq -r 'if .dirty then " + uncommitted changes" else "" end' "$rec"))"; fi
  if td ping >/dev/null 2>&1; then echo "PASS trapdoor bridge answers (seed, set flag)"; else echo "INFO trapdoor bridge does not answer: seed and set flag won't work until the app is running a verify.sh build"; fi
  [[ $ok == 1 ]] || exit 1
}

# --- Driving: android CLI layout + adb input -------------------------------------------------------
# trapdoor stays for state the app cannot be driven into (seed, set flag, reset); everything a user
# does on screen goes through `android layout` (the element tree) and `adb shell input`.

layout_json() {
  local out
  out="$(android layout --device "$(serial)" 2>/dev/null)" || true
  if [[ -z "$out" || "${out:0:1}" != "[" ]]; then
    sleep 1; out="$(android layout --device "$(serial)" 2>/dev/null)" || true
  fi
  [[ -n "$out" && "${out:0:1}" == "[" ]] \
    || die "android layout returned nothing (an animation or a WebView can block it); take a shot or use 'android screen capture --annotate'"
  printf '%s' "$out"
}
# Captured first so an empty layout returns non-zero instead of handing layout.py empty input.
lp() { local j; j="$(layout_json)" || return 1; printf '%s' "$j" | python3 -I "$HERE/layout.py" "$@"; }

# Parses "<selector> [--index N]" into SEL and IDX.
sel_index() { SEL="${1:?a selector: id:X, text:X, text~:X, desc:X or desc~:X}"; IDX=1; [[ "${2:-}" == "--index" ]] && IDX="${3:?--index needs a number}"; true; }

cmd_tap() {
  sel_index "$@"; local xy
  xy="$(lp find "$SEL" "$IDX")" || die "not on screen: $SEL (index $IDX)"
  a shell input tap $xy; echo "tapped $SEL at $xy"
}

# Taps the field, waits for focus, clears it and types. adb input text needs spaces as %s and shell
# characters escaped.
cmd_type() {
  local sel="${1:?usage: verify.sh type <selector> <text>}" text="${2:?usage: verify.sh type <selector> <text>}" i
  cmd_tap "$sel" >/dev/null
  for i in 1 2 3 4 5 6; do lp get "$sel" 2>/dev/null | grep -q '"focused"' && break; sleep 0.5; done
  a shell input keycombination 113 29 >/dev/null 2>&1 || true
  a shell input keyevent 67
  a shell input text "$(printf '%s' "$text" | sed -e 's/[\\()&<>;|*~"'"'"'$`]/\\&/g' -e 's/ /%s/g')"
  echo "typed into $sel"
}

cmd_key() {
  case "${1:-}" in
    back) a shell input keyevent 4 ;; home) a shell input keyevent 3 ;;
    enter) a shell input keyevent 66 ;; tab) a shell input keyevent 61 ;;
    *) die "usage: verify.sh key back|home|enter|tab" ;;
  esac
}

# Scrolls the screen: up shows content further down, as a finger swiping up does.
cmd_scroll() {
  local dir="${1:-up}" times="${2:-1}" w h x i
  read -r w h <<<"$(a shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1)"
  x=$((w / 2))
  for ((i = 0; i < times; i++)); do
    case "$dir" in
      up) a shell input swipe "$x" $((h * 7 / 10)) "$x" $((h * 3 / 10)) 600 ;;
      down) a shell input swipe "$x" $((h * 3 / 10)) "$x" $((h * 7 / 10)) 600 ;;
      *) die "usage: verify.sh scroll up|down [times]" ;;
    esac
    sleep 0.5
  done
}

# The resumed activity's class name, for example SettingsActivity. Prints "-" when another app is in front.
cmd_where() {
  local dump top
  # Read it whole first: grep -m1 on the live pipe makes dumpsys fail with SIGPIPE under pipefail.
  dump="$(a shell dumpsys activity activities | tr -d '\r')"
  top="$(grep -m1 'topResumedActivity' <<<"$dump" | grep -o '[A-Za-z0-9_.]*/[A-Za-z0-9_.$]*' | head -1 || true)"
  if [[ "${top%%/*}" == "$PKG" ]]; then echo "${top##*.}"; else echo "-"; fi
}

# assert visible|not-visible <selector> [--index N], assert text <selector> <expected>, assert where <Activity>.
# Exit 15 when false, like trapdoor's assert.
cmd_assert() {
  local kind="${1:-}"; shift || true
  case "$kind" in
    visible) sel_index "$@"; lp find "$SEL" "$IDX" >/dev/null && { echo "visible: $SEL"; return; }; echo "not visible: $SEL" >&2; exit 15 ;;
    not-visible) sel_index "$@"; local rc=0; lp find "$SEL" "$IDX" >/dev/null || rc=$?
          (( rc == 15 )) && { echo "not visible: $SEL"; return; }; (( rc == 0 )) && { echo "visible: $SEL" >&2; exit 15; }; exit 1 ;;
    text) local actual; actual="$(lp get "${1:?selector}" | python3 -c 'import sys,json; n=json.load(sys.stdin); print(n.get("text") or n.get("content-desc") or "")')" || { echo "not on screen: $1" >&2; exit 15; }
          [[ "$actual" == "${2?expected text}" ]] && { echo "text ok: $actual"; return; }; echo "text was: $actual" >&2; exit 15 ;;
    where) local at; at="$(cmd_where)"; [[ "$at" == "${1:?activity}" ]] && { echo "at $at"; return; }; echo "at $at" >&2; exit 15 ;;
    *) die "usage: verify.sh assert visible|not-visible <selector> | text <selector> <expected> | where <Activity>" ;;
  esac
}

cmd_wait() {
  local selector="${1:?usage: verify.sh wait <selector> [secs]}" secs="${2:-20}" end
  end=$((SECONDS + secs))
  while (( SECONDS < end )); do
    lp find "$selector" >/dev/null 2>&1 && { echo "visible: $selector"; return; }
    sleep 1
  done
  die "not visible after ${secs}s: $selector"
}

# Tappable elements, one per line: top, id, text, description, interactions and state.
cmd_ui() { lp ui; }

# Every element with text (id, text, interactions and state), or one id's text; exit 15 when absent.
cmd_text() { lp text "$@"; }

# Text in every window, popups included (android layout lists only the activity's window), with the
# centre to tap: "x y<TAB>text". Uses uiautomator, so another UI-automation client on the device kills it.
cmd_windows() {
  a shell uiautomator dump --windows /sdcard/verify-windows.xml >/dev/null 2>&1 \
    || { sleep 1; a shell uiautomator dump --windows /sdcard/verify-windows.xml >/dev/null 2>&1; } || die "uiautomator dump failed"
  a exec-out cat /sdcard/verify-windows.xml | python3 -Ic '
import re, sys
for m in re.finditer(r"<node [^>]*>", sys.stdin.read()):
    t = re.search(r" text=\"([^\"]+)\"", m.group(0)); b = re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", m.group(0))
    if t and b:
        x1, y1, x2, y2 = map(int, b.groups()); print((x1 + x2) // 2, (y1 + y2) // 2, sep=" ", end="\t"); print(t.group(1))'
}

# true/false for the Nth (1-based) checkable element with this id.
cmd_checked() { lp checked "${1:?usage: verify.sh checked <resource-id> [index]}" "${2:-1}" || die "no element with id $1 at index ${2:-1}"; }

# Screenshot plus the element tree beside it, and a sidecar naming the activity and commit.
cmd_shot() {
  local name="${1:?usage: verify.sh shot <name>.png}" d; d="$(evidence_dir)"
  a exec-out screencap -p > "$d/$name"
  [[ -s "$d/$name" ]] || die "screencap returned nothing"
  (layout_json) > "$d/$name.layout.json" 2>/dev/null || true
  printf '{"activity":"%s","capturedUtc":"%s","commit":"%s"}\n' "$(cmd_where)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    "$(git -C "$REPO" rev-parse --short HEAD)" > "$d/$name.json"
  echo "$d/$name"
}

# record start <name>.mp4 / record stop. screenrecord stops by itself after 170 s.
cmd_record() {
  local s d; s="$(serial)"; d="$(dstate "$s")"
  case "${1:-}" in
    start)
      local name="${2:?usage: verify.sh record start <name>.mp4}"
      [[ -f "$d/record" ]] && cmd_record stop >/dev/null
      ( "$ADB" -s "$s" shell screenrecord --time-limit 170 "/sdcard/verify-$name" >/dev/null 2>&1 ) &
      echo "$! $name" > "$d/record"; echo "recording $name" ;;
    stop)
      [[ -f "$d/record" ]] || { echo "not recording"; return; }
      local pid name; read -r pid name < "$d/record"
      a shell pkill -INT screenrecord >/dev/null 2>&1 || true
      for _ in $(seq 1 10); do kill -0 "$pid" 2>/dev/null || break; sleep 0.5; done
      sleep 1
      a pull "/sdcard/verify-$name" "$(evidence_dir)/$name" >/dev/null && a shell rm -f "/sdcard/verify-${name:?}"
      rm -f "${d:?}/record"; echo "$(evidence_dir)/$name" ;;
    *) die "usage: verify.sh record start <name>.mp4 | stop" ;;
  esac
}

# Brings the app to the front through its launcher icon, as a user does. Debug builds have a second
# launcher entry (LeakCanary's "Leaks"), so start the browser's own one by name rather than with monkey.
cmd_launch() {
  a shell am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$PKG/com.duckduckgo.app.launch.Launcher" >/dev/null 2>&1
  # The launcher entry is a short-lived "Launcher" activity that hands over to the browser.
  local i at; for i in $(seq 1 20); do at="$(cmd_where)"; [[ "$at" != "-" && "$at" != "Launcher" ]] && { echo "app in front: $at"; return; }; sleep 1; done
  die "the app did not come to the front"
}
cmd_restart() { a shell am force-stop "$PKG"; cmd_launch; }

# Opens a URL in the app the way another app's link does (setup, not the step under test).
cmd_open() {
  a shell am start -a android.intent.action.VIEW -d "${1:?usage: verify.sh open <url>}" -p "$PKG" >/dev/null
  echo "opened $1"
}

# State setup that cannot be driven through the UI: clears app data and seeds onboarding as done. Only on
# an emulator this run booted; on the user's device trapdoor itself asks for --yes.
cmd_reset() {
  owned "$(serial)" || die "reset clears app data; this device is not an emulator this run booted, so ask the user first"
  td reset; cmd_launch
}

# --- Seeding: the only use of trapdoor -------------------------------------------------------------
# trapdoor's in-app bridge writes state the UI cannot reach: seeded data, feature flags, a clean app.
# Nothing else goes through it; driving and evidence use the android CLI and adb above.
cmd_seed() { local d; d="$(evidence_dir)"; (cd "$d" && td seed "${@:?usage: verify.sh seed <fixture> [--param value ...]}"); }
cmd_seeds() { td list --fixtures | cut -f1; }
cmd_flag() {
  [[ "${1:-}" && "${2:-}" =~ ^(enabled|disabled)$ ]] || die "usage: verify.sh flag <parent.name> enabled|disabled"
  td set flag --feature "$1" --state "$2"
}
cmd_flags() { td flags "$@"; }

# DevTools forward on a free port per device (trapdoor's cdp-forward always uses tcp:9222).
# Needs Developer Settings > Web Content Debugging on.
cdp_port() {
  local s pid sock port d; s="$(serial)"; d="$(dstate "$s")"
  pid="$(a shell pidof "$PKG" | tr -d '\r')"
  [[ -n "$pid" ]] || die "$PKG is not running on $s"
  sock="webview_devtools_remote_$pid"
  a shell cat /proc/net/unix | grep -q "@$sock" \
    || die "no $sock socket: enable web content debugging (SKILL.md, Web content) and load a page"
  if [[ -f "$d/cdp" && "$(cut -d' ' -f1 "$d/cdp")" == "$pid" ]]; then cut -d' ' -f2 "$d/cdp"; return; fi
  port="$(a forward tcp:0 "localabstract:$sock" | tr -d '\r')"
  echo "$pid $port" > "$d/cdp"
  echo "$port"
}

cdp() { node "$HERE/cdp.mjs" "$@"; }

# Screen point for a CSS selector: the element centre in CSS px, scaled by devicePixelRatio and
# offset by the WebView's position on screen, so the tap is a real touch.
web_point() {
  local selector="$1" port rect wv
  port="$(cdp_port)"
  rect="$(cdp "$port" eval "(() => { const e = document.querySelector($(jq -Rn --arg s "$selector" '$s'));
    if (!e) return null; e.scrollIntoView({block: 'center'}); const r = e.getBoundingClientRect();
    return {x: r.left + r.width / 2, y: r.top + r.height / 2, dpr: devicePixelRatio}; })()")"
  [[ "$rect" != "null" ]] || die "no element for selector: $selector"
  # android layout drops the WebView's id once it lists the page's own content, so its position comes
  # from uiautomator, which keeps the class: the outermost android.webkit.WebView of the app.
  wv="$(a exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o "<node [^>]*class=\"android.webkit.WebView\" package=\"$PKG\"[^>]*>" \
    | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]' | grep -o '[0-9]*,[0-9]*' | tr ',' ' ')"
  [[ -n "$wv" ]] || die "no WebView on screen (or uiautomator was killed by another UI-automation client)"
  jq -rn --argjson r "$rect" --arg wv "$wv" '($wv | split(" ") | map(tonumber)) as $b | "\(($b[0] + $r.x * $r.dpr) | round) \(($b[1] + $r.y * $r.dpr) | round)"'
}

cmd_web() {
  local sub="${1:-}"; shift || true
  case "$sub" in
    targets) cdp "$(cdp_port)" targets ;;
    eval)    cdp "$(cdp_port)" eval "${1:?usage: verify.sh web eval '<js>' [url-regex]}" "${2:-}" ;;
    tap)     local x y; read -r x y <<<"$(web_point "${1:?usage: verify.sh web tap '<css selector>'}")"
             a shell input tap "$x" "$y"; echo "tapped web $1 at $x,$y" ;;
    # Web fields take keystrokes into the focused element.
    type)    a shell input text "$(printf '%s' "${1:?usage: verify.sh web type '<text>'}" | sed 's/ /%s/g')" ;;
    *) die "usage: verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>' | type '<text>'" ;;
  esac
}

cmd_logcat() { local d; d="$(evidence_dir)"; a logcat -d > "$d/logcat-$(date +%H%M%S).txt"; echo "$d"; }

# Undoes only what the run did: stops recordings, removes its own adb forwards, and stops emulators
# it started. The user's devices and emulators keep running.
cmd_cleanup() {
  local s d
  for s in $( { selected; ls "$STATE/dev" 2>/dev/null; } | sort -u); do
    d="$STATE/dev/$s"
    if "$ADB" devices | grep -q "^$s[[:space:]]*device"; then
      [[ -f "$d/record" ]] && (VERIFY_SERIAL="$s"; cmd_record stop >/dev/null 2>&1) || true
      [[ -f "$d/cdp" ]] && "$ADB" -s "$s" forward --remove "tcp:$(cut -d' ' -f2 "$d/cdp")" >/dev/null 2>&1 || true
    fi
    if [[ -f "$d/pid" ]]; then
      local pid; pid="$(cat "$d/pid")"
      "$ADB" -s "$s" emu kill >/dev/null 2>&1 || true
      for _ in $(seq 1 15); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      kill -0 "$pid" 2>/dev/null && kill "$pid" || true
      echo "stopped $s (pid $pid)"
    fi
  done
  rm -rf "$STATE"
  echo "evidence kept in $EVIDENCE_ROOT"
}

case "${1:-}" in
  devices|use|on|each|boot|build|install|doctor|seed|seeds|flag|flags|tap|type|key|scroll|where|assert|wait|ui|windows|text|checked|shot|record|launch|restart|open|reset|web|logcat|cleanup|subscription) c="$1"; shift; "cmd_$c" "$@" ;;
  *) cat >&2 <<EOF
usage: verify.sh devices                      attached devices and AVDs on this machine
       verify.sh use <serial>...              choose the device(s) to verify on
       verify.sh boot [<avd>]                 start a user AVD, or the fallback when there are none
       verify.sh build | install | doctor | cleanup
       verify.sh each <command>...            run a command on every selected device
       verify.sh on <serial> <command>...     run a command on one selected device
   drive (android layout + adb):
       verify.sh tap <selector> [--index N]   selector: id:X, text:X, text~:X, desc:X, desc~:X
       verify.sh type <selector> <text>       focus, clear and type into a native field
       verify.sh key back|home|enter|tab      verify.sh scroll up|down [times]
       verify.sh assert visible|not-visible <selector> | text <selector> <expected> | where <Activity>
       verify.sh wait <selector> [secs]       verify.sh where
       verify.sh ui | windows | text [<id>] | checked <id> [index]
       verify.sh shot <name>.png              verify.sh record start <name>.mp4 | stop
       verify.sh launch | restart | open <url>
       verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>' | type '<text>'
   seed (trapdoor, the only use of it):
       verify.sh seed <fixture> [--param value ...]   verify.sh seeds   list the fixtures
       verify.sh flag <parent.name> enabled|disabled  verify.sh flags   current flag values
       verify.sh subscription none | [--tier plus|pro] [--region us|row] [--status <s>] [--trial true] [--products <list>]
       verify.sh reset                        clear app data, onboarding done (emulators this run booted)
       verify.sh logcat
EOF
     exit 2 ;;
esac
