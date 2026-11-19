#!/usr/bin/env bash
# Verification driver for the DuckDuckGo Android app. Picks the device(s) to verify on, builds and installs
# this checkout, drives the app with the android CLI and adb, acts inside WebViews through DevTools, and
# captures screenshots and video.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$HOME/.local/bin:$SDK/platform-tools:$PATH"

PKG="com.duckduckgo.mobile.android.debug"
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
# prove.py sets VERIFY_UI_LOG to the run's ui.log, so a failed run shows every device command and its exit code.
uilog() { [[ -z "${VERIFY_UI_LOG:-}" ]] || printf '  %s -> %s\n' "$1" "$2" >> "$VERIFY_UI_LOG"; }
a() { local rc=0; "$ADB" -s "$(serial)" "$@" || rc=$?; uilog "adb $*" "$rc"; return "$rc"; }

# adb's own reason decides the advice: only a signing mismatch needs the user, since uninstalling deletes their data.
adb_install() {
  local out reason
  out="$(a install -r -d -t "$1" 2>&1)" && return 0
  reason="$(grep -o 'INSTALL_[A-Z_]*' <<<"$out" | head -1 || true)"
  case "$reason" in
    INSTALL_FAILED_UPDATE_INCOMPATIBLE) die "install failed ($reason): an internal build signed with another key is installed, and uninstalling it deletes the user's app data, so ask" ;;
    INSTALL_FAILED_INSUFFICIENT_STORAGE) die "install failed ($reason): the device is out of space; free some or pick another device" ;;
    *) die "install failed: $(grep -v '^[[:space:]]*$' <<<"$out" | tail -1 || true)" ;;
  esac
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

# --- Build identity, the way Android PR #10551 does it ------------------------------------------
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
  local path; path="$(a shell pm path "${1:-$PKG}" 2>/dev/null | tr -d '\r' | sed -n 's/^package://p' | head -1 || true)"
  [[ -n "$path" ]] && a shell sha256sum "$path" | tr -d '\r' | cut -d' ' -f1
}
# Assembles the internal debug app in checkout $1 and prints the APK it produced. The auth properties
# (app/build.gradle) let automation open Passwords and Sync without the device's screen lock.
assemble() {
  local root="$1" newest
  echo "building :app:assembleInternalDebug in $root ..." >&2
  (cd "$root" && ./gradlew -q :app:assembleInternalDebug \
    -Pautofill-disable-auth-requirement -Psync-disable-auth-requirement -Pforce-default-variant >&2) || die "build failed"
  # The file name carries the version, so APKs of earlier versions stay in the folder: the newest is this build.
  newest="$(ls -t "$root/app/build/outputs/apk/internal/debug"/*.apk 2>/dev/null | head -1)"
  [[ -n "$newest" ]] || die "the build produced no internalDebug APK"
  echo "$newest"
}
cmd_build() {
  [[ "${1:-}" == "--base" ]] && { cmd_build_base "${2:?usage: verify.sh build --base <ref>, e.g. origin/develop}"; return; }
  local source newest kept digest
  source="$(source_fingerprint)"
  newest="$(assemble "$REPO")"
  digest="$(file_sha256 "$newest")"; mkdir -p "$BUILDS"; kept="$BUILDS/${digest:0:16}.apk"
  cp "$newest" "$kept"
  python3 -I -c 'import json,sys; r=json.loads(sys.argv[1]); r.update(apk=sys.argv[2], sha256=sys.argv[3], built_at=sys.argv[4]); print(json.dumps(r))' \
    "$source" "$kept" "$digest" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$BUILDS/build.json"
  echo "built ${digest:0:16} from $(git -C "$REPO" rev-parse --short HEAD)$(grep -q '"dirty": true' "$BUILDS/build.json" && echo ' (with uncommitted changes)')"
}
# The "before" side of a comparison: builds <ref> in a worktree this tool owns. The worktree is moved
# between base commits so later base builds are incremental, and APKs are kept per commit.
cmd_build_base() {
  local ref="$1" sha tree kept digest
  sha="$(git -C "$REPO" rev-parse --verify "$ref^{commit}")" || die "unknown ref $ref (git fetch first for the latest)"
  tree="$VERIFY_ROOT/base-worktree"; kept="$BUILDS/base-${sha:0:12}.apk"; mkdir -p "$BUILDS"
  if [[ ! -f "$kept" ]]; then
    if [[ -e "$tree/.git" ]]; then
      # Nobody edits this worktree, so forcing the checkout can't lose work.
      git -C "$tree" checkout -q --detach --force "$sha"
    else
      git -C "$REPO" worktree add -q --detach "$tree" "$sha"
    fi
    git -C "$tree" submodule update -q --init --recursive
    [[ -f "$REPO/local.properties" ]] && cp "$REPO/local.properties" "$tree/local.properties"
    cp "$(assemble "$tree")" "$kept"
  fi
  digest="$(file_sha256 "$kept")"
  jq -n --arg base "$ref" --arg head "$sha" --arg apk "$kept" --arg sha256 "$digest" \
    --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '{base: $base, head: $head, apk: $apk, sha256: $sha256, built_at: $at}' \
    > "$BUILDS/base.json"
  echo "built base $ref (${sha:0:10}) as ${digest:0:16}"
}
cmd_install() {
  local record apk want s; record="$BUILDS/build.json"
  [[ "${1:-}" == "--apk" ]] && { cmd_install_apk "${2:?usage: verify.sh install --apk <path>}"; return; }
  if [[ "${1:-}" == "--base" ]]; then
    record="$BUILDS/base.json"
    [[ -f "$record" ]] || die "no base build: run verify.sh build --base <ref>"
  else
    [[ -f "$record" ]] || die "no recorded build: run verify.sh build"
    [[ "$(jq -r .fingerprint "$record")" == "$(source_fingerprint | jq -r .fingerprint)" ]] || die "stale build: the source changed since verify.sh build; build again"
  fi
  apk="$(jq -r .apk "$record")"; want="$(jq -r .sha256 "$record")"; s="$(serial)"
  adb_install "$apk"
  [[ "$(installed_sha256)" == "$want" ]] || die "the installed APK doesn't match the one just installed"
  jq --arg serial "$s" --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" '. + {serial: $serial, installed_at: $at}' "$record" > "$(dstate "$s")/installed.json"
  a shell am force-stop "$PKG"
  echo "installed ${want:0:16}$(jq -r 'if .base then " (base \(.base), \(.head[0:10]))" else "" end' "$record") on $s"
}
# An APK this tool didn't build, such as one from CI. Its record has the hash and no source, so doctor and the
# evidence say the app under test isn't a build of this checkout.
cmd_install_apk() {
  local apk="$1" digest s
  [[ -f "$apk" ]] || die "no APK at $apk"
  apk="$(cd "$(dirname "$apk")" && pwd)/$(basename "$apk")"
  digest="$(file_sha256 "$apk")"; s="$(serial)"
  adb_install "$apk"
  [[ "$(installed_sha256)" == "$digest" ]] || die "the installed $PKG isn't this APK: it must be an internal debug build ($PKG)"
  jq -n --arg apk "$apk" --arg sha256 "$digest" --arg serial "$s" --arg at "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{external: true, apk: $apk, sha256: $sha256, serial: $serial, installed_at: $at}' > "$(dstate "$s")/installed.json"
  a shell am force-stop "$PKG"
  echo "installed external APK ${digest:0:16} on $s: not a build of this source, and the evidence says so"
}

# Read-only: the device, the toolchain, and that the app on it is the build of the current source this tool installed.
cmd_doctor() {
  local ok=1 s rec locale; s="$(serial)"
  if "$ADB" devices | grep -q "^$s[[:space:]]*device"; then
    echo "PASS $s online ($(label "$s"))"
    # Proofs and recipes select by English UI text; on another language they would fail checks the app passes.
    locale="$(a shell getprop persist.sys.locale 2>/dev/null | tr -d '\r' || true)"
    [[ -n "$locale" ]] || locale="$(a shell getprop ro.product.locale 2>/dev/null | tr -d '\r' || true)"
    [[ -n "$locale" ]] || locale="$(a shell getprop persist.sys.language 2>/dev/null | tr -d '\r' || true)"
    case "$locale" in
      "") echo "INFO device language unknown (no locale property set); proofs assume English" ;;
      en|en-*|en_*) echo "PASS device language $locale" ;;
      *) echo "FAIL device language is $locale: proofs and recipes match English UI text, so set the device language to English"; ok=0 ;;
    esac
  else echo "FAIL $s not online"; ok=0; fi
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
  elif [[ "$(jq -r '.external // false' "$rec")" == "true" ]]; then echo "PASS installed app is an external APK ($(jq -r '.sha256[0:16]' "$rec")), not a build of this source: the evidence marks it external"
  elif [[ "$(jq -r '.base // empty' "$rec")" != "" ]]; then echo "PASS installed app is the base build of $(jq -r '.base' "$rec") ($(jq -r '.head[0:10]' "$rec")), not this checkout: a before run"
  elif [[ "$(jq -r .fingerprint "$rec")" != "$(source_fingerprint | jq -r .fingerprint)" ]]; then echo "FAIL stale build: the source changed since it was built; build and install again"; ok=0
  else echo "PASS installed app is the build of the current source ($(jq -r '.sha256[0:16]' "$rec"), $(jq -r '.head[0:10]' "$rec")$(jq -r 'if .dirty then " + uncommitted changes" else "" end' "$rec"))"; fi
  [[ $ok == 1 ]] || exit 1
}

# --- Driving: android CLI layout + adb input -------------------------------------------------------
# Everything a user does on screen goes through `android layout` (the element tree) and `adb shell input`.

layout_json() {
  local out rc=0
  out="$(android layout --device "$(serial)" 2>/dev/null)" || rc=$?
  uilog "android layout" "$rc"
  if [[ -z "$out" || "${out:0:1}" != "[" ]]; then
    sleep 1; rc=0; out="$(android layout --device "$(serial)" 2>/dev/null)" || rc=$?
    uilog "android layout (retry)" "$rc"
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
# Exit 15 when false.
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

# The raw element tree from android layout, as JSON.
cmd_layout() { layout_json; }

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
cmd_rows() { lp rows "${1:?usage: verify.sh rows <label-id> <control-id>}" "${2:?usage: verify.sh rows <label-id> <control-id>}"; }
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

# --- The probe: another app for App Tracking Protection to block -----------------------------------
# AppTP never blocks this app's own traffic, so its claim needs a third-party app: probe/ connects to each host
# and sends a hand-built TLS ClientHello (ProbeActivity.java). It is built with the SDK tools rather than
# Gradle so it stays out of the app's build, and kept in the builds folder under a hash of its sources.

PROBE_PKG="dev.verifyandroid.probe"
PROBE_DIR="$HERE/probe"

# Runs inside $(...), where set -e doesn't stop it, so every step dies on its own failure: a half-built APK cached
# under the source hash would be reused by every later run.
probe_apk() {
  local hash apk bt platform out keystore="$HOME/.android/debug.keystore"
  hash="$(cd "$PROBE_DIR" && find . -type f | LC_ALL=C sort | xargs shasum -a 256 | shasum -a 256 | cut -c1-16)"
  apk="$BUILDS/probe-$hash.apk"
  [[ -f "$apk" ]] && { echo "$apk"; return; }
  # Stable releases only: preview platforms and rc build-tools sort last but may be incomplete.
  bt="$SDK/build-tools/$(ls "$SDK/build-tools" | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1)"
  platform="$SDK/platforms/$(ls "$SDK/platforms" | grep -E '^android-[0-9]+$' | sort -V | tail -1)/android.jar"
  [[ -x "$bt/d8" && -f "$platform" ]] || die "the probe needs SDK build-tools and a platform in $SDK"
  out="$(mktemp -d)" || die "couldn't make a temporary folder for the probe"
  echo "building the probe app with $(basename "$bt") and $(basename "$(dirname "$platform")") ..." >&2
  # -source 11 against android.jar warns about the bootclasspath; only errors matter.
  javac -nowarn -Xlint:-options -source 11 -target 11 -classpath "$platform" -d "$out/classes" \
    $(find "$PROBE_DIR/src" -name '*.java') >&2 || die "javac failed on the probe"
  "$bt/d8" --min-api 26 --lib "$platform" --output "$out" $(find "$out/classes" -name '*.class') >&2 \
    || die "d8 failed on the probe"
  "$bt/aapt2" link -o "$out/unaligned.apk" -I "$platform" --manifest "$PROBE_DIR/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 1.0 >&2 || die "aapt2 failed on the probe"
  python3 -I -c 'import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as apk: apk.write(sys.argv[2], "classes.dex")' "$out/unaligned.apk" "$out/classes.dex" \
    || die "adding classes.dex to the probe failed"
  "$bt/zipalign" -f 4 "$out/unaligned.apk" "$out/probe.apk" || die "zipalign failed on the probe"
  # Gradle creates this key on the first debug build; a machine that never built one has none yet.
  if [[ ! -f "$keystore" ]]; then
    mkdir -p "$(dirname "$keystore")"
    keytool -genkeypair -keystore "$keystore" -storepass android -alias androiddebugkey -keypass android \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1 \
      || die "keytool couldn't create $keystore"
  fi
  "$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey "$out/probe.apk" >&2 || die "apksigner failed on the probe"
  "$bt/apksigner" verify "$out/probe.apk" >&2 || die "the signed probe doesn't verify"
  mkdir -p "$BUILDS" && cp "$out/probe.apk" "$apk.tmp" && mv "$apk.tmp" "$apk" || die "couldn't cache the probe as $apk"
  rm -rf "$out"
  echo "$apk"
}

probe_install() {
  local apk want out; apk="$(probe_apk)"; want="$(file_sha256 "$apk")"
  [[ "$(installed_sha256 "$PROBE_PKG")" == "$want" ]] && return
  if ! out="$(a install -r "$apk" 2>&1)"; then
    grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE <<<"$out" || die "installing the probe failed: $out"
    # Signed with another machine's debug key. The probe is this skill's own app, so replacing it loses nothing.
    a uninstall "$PROBE_PKG" >/dev/null && a install "$apk" >/dev/null || die "reinstalling the probe failed"
  fi
  [[ "$(installed_sha256 "$PROBE_PKG")" == "$want" ]] || die "the installed probe doesn't match $apk"
  echo "installed the probe ($(basename "$apk"))" >&2
}

# probe <host,host...> [mode] [timeout_ms]: one "RESULT run=.. host=.. mode=.. outcome=.." line per host.
# Exit 1 when the run didn't finish, so a host without a result is never read as an outcome.
cmd_probe() {
  local usage="usage: verify.sh probe <host,host...> [single|window|split|late-sni] [timeout_ms]"
  local hosts="${1:?$usage}" mode="${2:-single}" timeout="${3:-3000}" run="$RANDOM$RANDOM" lines="" wait_s n
  case "$mode" in single|window|split|late-sni) ;; *) die "$usage" ;; esac
  probe_install
  a shell am start -S -W -n "$PROBE_PKG/.ProbeActivity" \
    --es hosts "$hosts" --es mode "$mode" --ei timeout "$timeout" --es run "$run" >/dev/null
  # One host can take up to 2x timeout (connect + read) before the probe logs DONE.
  n="$(tr ',' '\n' <<<"$hosts" | grep -c .)"
  wait_s=$(( timeout * 2 * n / 1000 + 5 ))
  for _ in $(seq "$wait_s"); do
    # Captured instead of piped: under pipefail, grep -q closing the pipe early would fail the check.
    lines="$(a logcat -d -s VerifyProbe:I | tr -d '\r')"
    grep -qE "DONE run=$run\$" <<<"$lines" && break
    sleep 1
  done
  # Anchored on both sides, so an older run whose id starts with this one's doesn't match.
  grep -E "RESULT run=$run " <<<"$lines" | sed 's/^.*VerifyProbe: //' || true
  grep -qE "DONE run=$run\$" <<<"$lines" \
    || die "probe run $run incomplete: no DONE after ${wait_s}s; hosts without a RESULT line were not probed"
}

# A fresh app on the New Tab Page: clears app data, grants notifications up front so their prompt doesn't
# cover onboarding, and takes the internal build's Skip Onboarding. Only on an emulator this run booted.
cmd_reset() {
  owned "$(serial)" || die "reset clears app data; this device is not an emulator this run booted, so ask the user first"
  a shell pm clear "$PKG" >/dev/null || die "couldn't clear the app's data"
  a shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
  cmd_launch >/dev/null
  cmd_wait id:skipOnboardingButton 30 >/dev/null || die "onboarding didn't show Skip Onboarding"
  cmd_tap id:skipOnboardingButton >/dev/null
  cmd_wait id:inputField 20 >/dev/null || die "Skip Onboarding didn't land on the New Tab Page"
  echo "reset: fresh app on the New Tab Page"
}

# DevTools forward on a free port per device, so several devices can be driven at once.
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
  devices|use|on|each|boot|build|install|doctor|tap|type|key|scroll|where|assert|wait|ui|layout|windows|text|checked|rows|shot|record|launch|restart|open|reset|web|probe|logcat|cleanup) c="$1"; shift; "cmd_$c" "$@" ;;
  *) cat >&2 <<EOF
usage: verify.sh devices                      attached devices and AVDs on this machine
       verify.sh use <serial>...              choose the device(s) to verify on
       verify.sh boot [<avd>]                 start a user AVD, or the fallback when there are none
       verify.sh build [--base <ref>] | install [--base | --apk <path>] | doctor | cleanup
       verify.sh each <command>...            run a command on every selected device
       verify.sh on <serial> <command>...     run a command on one selected device
   drive (android layout + adb):
       verify.sh tap <selector> [--index N]   selector: id:X, text:X, text~:X, desc:X, desc~:X
       verify.sh type <selector> <text>       focus, clear and type into a native field
       verify.sh key back|home|enter|tab      verify.sh scroll up|down [times]
       verify.sh assert visible|not-visible <selector> | text <selector> <expected> | where <Activity>
       verify.sh wait <selector> [secs]       verify.sh where
       verify.sh ui | layout | windows | text [<id>] | checked <id> [index]
       verify.sh shot <name>.png              verify.sh record start <name>.mp4 | stop
       verify.sh launch | restart | open <url>
       verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>' | type '<text>'
       verify.sh reset                        clear app data and skip onboarding (emulators this run booted)
       verify.sh probe <host,...> [mode] [ms] connect to hosts as another app (App Tracking Protection)
       verify.sh logcat
EOF
     exit 2 ;;
esac
