#!/usr/bin/env bash
# Verification driver for the DuckDuckGo Android app: owns one emulator per run,
# installs the worktree's internal debug build, drives it with Maestro, and keeps evidence.
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
export JAVA_HOME="${JAVA_HOME_21:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export PATH="$JAVA_HOME/bin:$PATH"

PKG="com.duckduckgo.mobile.android.debug"
LAUNCHER="$PKG/com.duckduckgo.app.launch.Launcher"
APK_DIR="$REPO/app/build/outputs/apk/internal/debug"
VERIFY_ROOT="$REPO/build/verify"
STATE="$VERIFY_ROOT/state"
EVIDENCE_ROOT="$VERIFY_ROOT/evidence"

die() { echo "verify: $*" >&2; exit 1; }
serial() { [[ -f "$STATE/serial" ]] || die "no emulator owned by this checkout; run: verify.sh boot"; cat "$STATE/serial"; }
run_id() { [[ -f "$STATE/run" ]] || die "no active run; run: verify.sh boot"; cat "$STATE/run"; }
evidence_dir() { local d="$EVIDENCE_ROOT/$(run_id)"; mkdir -p "$d"; echo "$d"; }
apk() { ls -t "$APK_DIR"/*.apk 2>/dev/null | head -1; }
a() { "$ADB" -s "$(serial)" "$@"; }

VERIFY_AVD="ddg_verify_api34"
BASE_SNAPSHOT="verify-base"
SETTLE_SECS="${SETTLE_SECS:-240}"
VERIFY_IMAGE="system-images/android-34/google_apis_playstore/arm64-v8a/"

# A dedicated AVD, so verification never touches the user's own emulators or their data.
ensure_avd() {
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
fastboot.forceColdBoot=yes
EOF
  printf 'avd.ini.encoding=UTF-8\npath=%s\npath.rel=avd/%s.avd\ntarget=android-34\n' "$dir" "$VERIFY_AVD" \
    > "$HOME/.android/avd/$VERIFY_AVD.ini"
  echo "created AVD $VERIFY_AVD"
}

cmd_boot() {
  if [[ -f "$STATE/pid" ]] && kill -0 "$(cat "$STATE/pid")" 2>/dev/null; then
    echo "already running: $(cat "$STATE/serial") (pid $(cat "$STATE/pid"))"; return
  fi
  ensure_avd
  mkdir -p "$STATE"
  local port=5580 has_base=0 boot_args
  while "$ADB" devices | grep -q "emulator-$port"; do port=$((port + 2)); done
  [[ -d "$HOME/.android/avd/$VERIFY_AVD.avd/snapshots/$BASE_SNAPSHOT" ]] && has_base=1
  # A wiped Play Store image restarts Google Play services for minutes after boot and kills apps that
  # use its providers. So the first boot settles once and saves a snapshot; later boots load it unchanged.
  if (( has_base )); then boot_args=(-snapshot "$BASE_SNAPSHOT" -no-snapshot-save)
  else boot_args=(-wipe-data -no-snapshot-load); fi
  "$EMULATOR" -avd "$VERIFY_AVD" "${boot_args[@]}" -no-window -no-audio -no-boot-anim -port "$port" \
    > "$STATE/emulator.log" 2>&1 &
  echo $! > "$STATE/pid"
  echo "emulator-$port" > "$STATE/serial"
  date +%Y%m%d-%H%M%S > "$STATE/run"
  echo "booting $VERIFY_AVD as emulator-$port (pid $(cat "$STATE/pid"), $( ((has_base)) && echo "from snapshot $BASE_SNAPSHOT" || echo "cold, first run"))"
  "$ADB" -s "emulator-$port" wait-for-device
  local booted=0
  for _ in $(seq 1 120); do
    [[ "$("$ADB" -s "emulator-$port" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && { booted=1; break; }
    sleep 2
  done
  (( booted )) || die "emulator-$port did not finish booting; see $STATE/emulator.log"
  if (( ! has_base )); then
    echo "settling Google Play services for ${SETTLE_SECS}s before saving snapshot $BASE_SNAPSHOT"
    sleep "$SETTLE_SECS"
    "$ADB" -s "emulator-$port" emu avd snapshot save "$BASE_SNAPSHOT" | tr -d '\r' | grep -v '^OK$' || true
  fi
  echo "ready: emulator-$port"
}

cmd_build() {
  (cd "$REPO" && ./gradlew :app:assembleInternalDebug -q)
  echo "built: $(apk)"
}

cmd_install() {
  local f; f="$(apk)"; [[ -n "$f" ]] || die "no APK in $APK_DIR; run: verify.sh build"
  a install -r -t "$f" >/dev/null
  # install -r leaves an already-running process on the old code.
  a shell am force-stop "$PKG"
  echo "installed $(basename "$f") on $(serial)"
}

cmd_launch() {
  a shell am force-stop "$PKG"
  a shell am start -W -n "$LAUNCHER" | grep -E "Status|LaunchState"
}

cmd_doctor() {
  local ok=1 s pid
  s="$(serial)"; pid="$(cat "$STATE/pid" 2>/dev/null || true)"
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then echo "ok   emulator pid $pid owned by this checkout"; else echo "FAIL emulator process not running"; ok=0; fi
  if "$ADB" devices | grep -q "^$s[[:space:]]*device"; then echo "ok   $s online"; else echo "FAIL $s not online"; ok=0; fi
  [[ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && echo "ok   boot completed" || { echo "FAIL boot not completed"; ok=0; }
  local f installed built
  f="$(apk)"
  installed="$("$ADB" -s "$s" shell dumpsys package "$PKG" 2>/dev/null | grep -m1 lastUpdateTime | sed 's/.*=//' | tr -d '\r')"
  if [[ -z "$installed" ]]; then echo "FAIL $PKG not installed"; ok=0
  else
    echo "ok   $PKG installed $installed ($("$ADB" -s "$s" shell dumpsys package "$PKG" | grep -m1 versionName | tr -d ' \r'))"
    built="$(date -r "$f" '+%Y-%m-%d %H:%M:%S' 2>/dev/null || echo none)"
    [[ "$installed" > "$built" ]] && echo "ok   install is newer than APK built $built" || { echo "FAIL APK built $built after install; run: verify.sh install"; ok=0; }
  fi
  echo "info worktree $(git -C "$REPO" rev-parse --abbrev-ref HEAD)@$(git -C "$REPO" rev-parse --short HEAD), dirty files: $(git -C "$REPO" status --porcelain | wc -l | tr -d ' ')"
  command -v android >/dev/null && echo "ok   android cli $(android --version 2>/dev/null | head -1)" || { echo "FAIL android cli not on PATH"; ok=0; }
  command -v jq >/dev/null || { echo "FAIL jq not on PATH"; ok=0; }
  [[ -n "$(layout_json)" ]] && echo "ok   android layout reads $s" || { echo "FAIL android layout returned nothing for $s"; ok=0; }
  echo "info evidence -> $EVIDENCE_ROOT/$(run_id)"
  [[ $ok == 1 ]] || exit 1
}

layout_json() { android layout --device "$(serial)" 2>/dev/null | grep -m1 '^\['; }

# Selector: --id <resource-id> (matches the bare name or a package-qualified one), --text <regex>, --desc <regex>.
# Prints matching elements as JSON lines.
find_elements() {
  local kind="$1" value="$2" filter
  case "$kind" in
    --id)   filter='select((."resource-id" // "") | test("(^|[:/])" + $v + "$"))' ;;
    --text) filter='select((.text // "") | test($v; "i"))' ;;
    --desc) filter='select((."content-desc" // .contentDesc // "") | test($v; "i"))' ;;
    *) die "selector must be --id, --text or --desc" ;;
  esac
  layout_json | jq -c --arg v "$value" ".[] | $filter"
}

cmd_ui() {
  local name="${1:-}" json; json="$(layout_json)"
  [[ -n "$name" ]] && echo "$json" | jq . > "$(evidence_dir)/$name.layout.json"
  echo "$json" | jq -r '.[] | select(.text or ."resource-id" or ."content-desc")
    | [(."resource-id" // "-"), (.text // ."content-desc" // "-"), .center, ((.interactions // []) | join(","))] | @tsv'
}

cmd_wait() {
  local kind="$1" value="$2" timeout="${3:-20}" end=$((SECONDS + ${3:-20}))
  while (( SECONDS < end )); do
    [[ -n "$(find_elements "$kind" "$value" | head -1)" ]] && { echo "visible: $kind $value"; return; }
    sleep 1
  done
  die "not visible after ${timeout}s: $kind $value"
}

cmd_assert() {
  local negate=0; [[ "${1:-}" == "--not" ]] && { negate=1; shift; }
  local hit; hit="$(find_elements "$1" "$2" | head -1)"
  if (( negate )); then [[ -z "$hit" ]] && echo "ok   not visible: $1 $2" || die "FAIL still visible: $1 $2"
  else [[ -n "$hit" ]] && echo "ok   visible: $1 $2 $hit" || die "FAIL not visible: $1 $2"; fi
}

cmd_tap() {
  local kind="$1" value="$2" index="${3:-0}" el x y
  el="$(find_elements "$kind" "$value" | sed -n "$((index + 1))p")"
  [[ -n "$el" ]] || die "no element for $kind $value"
  read -r x y <<<"$(echo "$el" | jq -r '.center' | tr -d '[]' | tr ',' ' ')"
  a shell input tap "$x" "$y"
  echo "tapped $kind $value at $x,$y"
}

cmd_type() {
  # adb input text needs spaces escaped as %s.
  a shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')"
}

cmd_key() { a shell input keyevent "$1"; }

# Forwards a local port to the app's DevTools socket. Needs Developer Settings > web content debugging on.
cdp_port() {
  local pid sock port
  pid="$(a shell pidof "$PKG" | tr -d '\r')"
  [[ -n "$pid" ]] || die "$PKG is not running"
  sock="webview_devtools_remote_$pid"
  a shell cat /proc/net/unix | grep -q "@$sock" \
    || die "no $sock socket: enable web content debugging (see features/README.md) and reload the page"
  if [[ -f "$STATE/cdp" && "$(cut -d' ' -f1 "$STATE/cdp")" == "$pid" ]]; then cut -d' ' -f2 "$STATE/cdp"; return; fi
  port="$(a forward tcp:0 "localabstract:$sock" | tr -d '\r')"
  echo "$pid $port" > "$STATE/cdp"
  echo "$port"
}

cdp() { node "$(dirname "${BASH_SOURCE[0]}")/cdp.mjs" "$@"; }

# Screen coordinates for a CSS selector: the element's centre in CSS px, scaled by devicePixelRatio
# and offset by the WebView's bounds, so the tap is a real touch through the app.
web_point() {
  local selector="$1" port rect wv
  port="$(cdp_port)"
  rect="$(cdp "$port" eval "(() => { const e = document.querySelector($(jq -Rn --arg s "$selector" '$s'));
    if (!e) return null; e.scrollIntoView({block: 'center'}); const r = e.getBoundingClientRect();
    return {x: r.left + r.width / 2, y: r.top + r.height / 2, dpr: devicePixelRatio}; })()")"
  [[ "$rect" != "null" ]] || die "no element for selector: $selector"
  wv="$(a exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o 'class="android.webkit.WebView"[^>]*bounds="\[[0-9]*,[0-9]*\]' | tail -1 | grep -o '\[[0-9]*,[0-9]*\]' | tr -d '[]')"
  [[ -n "$wv" ]] || die "no WebView on screen"
  jq -rn --argjson r "$rect" --arg wv "$wv" \
    '($wv | split(",") | map(tonumber)) as [$l, $t] | "\(($l + $r.x * $r.dpr) | round) \(($t + $r.y * $r.dpr) | round)"'
}

cmd_web() {
  local sub="${1:-}"; shift || true
  case "$sub" in
    targets) cdp "$(cdp_port)" targets ;;
    eval)    cdp "$(cdp_port)" eval "${1:?usage: verify.sh web eval '<js>' [url-regex]}" "${2:-}" ;;
    tap)     local x y; read -r x y <<<"$(web_point "${1:?usage: verify.sh web tap '<css selector>'}")"
             a shell input tap "$x" "$y"; echo "tapped web $1 at $x,$y" ;;
    *) die "usage: verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>'" ;;
  esac
}

# screenrecord stops each file at 180 s, so recording loops over numbered segments until `rec stop`.
cmd_rec() {
  local sub="${1:-}" name="${2:-}" s; s="$(serial)"
  case "$sub" in
    start)
      [[ -n "$name" ]] || die "usage: verify.sh rec start <name>"
      [[ -f "$STATE/rec" ]] && die "already recording $(cut -d' ' -f2 "$STATE/rec"); run: verify.sh rec stop"
      "$ADB" -s "$s" shell "rm -rf /sdcard/verify-rec && mkdir -p /sdcard/verify-rec"
      ( i=0
        while [[ -f "$STATE/rec" ]]; do
          "$ADB" -s "$s" shell screenrecord --bit-rate 6000000 --time-limit 180 \
            "/sdcard/verify-rec/$name-$(printf %02d $i).mp4" >/dev/null 2>&1 || true
          i=$((i + 1))
        done ) &
      echo "$! $name" > "$STATE/rec"
      sleep 1
      echo "recording $name" ;;
    stop)
      [[ -f "$STATE/rec" ]] || die "not recording"
      local pid d; pid="$(cut -d' ' -f1 "$STATE/rec")"; name="$(cut -d' ' -f2 "$STATE/rec")"
      rm -f "$STATE/rec"
      # SIGINT lets screenrecord finalise the mp4; a killed recorder leaves an unplayable file.
      "$ADB" -s "$s" shell pkill -INT screenrecord || true
      for _ in $(seq 1 10); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      d="$(evidence_dir)"
      "$ADB" -s "$s" pull /sdcard/verify-rec/. "$d/" >/dev/null 2>&1
      "$ADB" -s "$s" shell rm -rf /sdcard/verify-rec
      local parts=("$d/$name"-*.mp4)
      if (( ${#parts[@]} == 1 )); then mv "${parts[0]}" "$d/$name.mp4"
      elif command -v ffmpeg >/dev/null; then
        printf "file '%s'\n" "${parts[@]}" > "$d/$name.parts"
        ffmpeg -loglevel error -f concat -safe 0 -i "$d/$name.parts" -c copy "$d/$name.mp4" && rm "${parts[@]}" "$d/$name.parts"
      fi
      ls "$d/$name"*.mp4 ;;
    *) die "usage: verify.sh rec start <name> | rec stop" ;;
  esac
}

cmd_shot() {
  local name="${1:-shot}" d; d="$(evidence_dir)"
  a exec-out screencap -p > "$d/$name.png"
  layout_json | jq . > "$d/$name.layout.json" || true
  echo "$d/$name.png"
}

cmd_logcat() {
  local d; d="$(evidence_dir)"
  a logcat -d > "$d/logcat-$(date +%H%M%S).txt"
  echo "$d"
}

cmd_cleanup() {
  [[ -f "$STATE/rec" ]] && cmd_rec stop || true
  if [[ -f "$STATE/pid" ]]; then
    local pid s; pid="$(cat "$STATE/pid")"; s="$(cat "$STATE/serial" 2>/dev/null || true)"
    [[ -n "$s" ]] && "$ADB" -s "$s" forward --remove-all >/dev/null 2>&1 || true
    [[ -n "$s" ]] && "$ADB" -s "$s" emu kill >/dev/null 2>&1 || true
    for _ in $(seq 1 15); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -0 "$pid" 2>/dev/null && kill "$pid" || true
    echo "stopped $s (pid $pid)"
  fi
  rm -rf "$STATE"
  echo "evidence kept in $EVIDENCE_ROOT"
}

case "${1:-}" in
  boot|build|install|launch|doctor|ui|wait|assert|tap|type|key|web|rec|shot|logcat|cleanup) c="$1"; shift; "cmd_$c" "$@" ;;
  *) cat >&2 <<EOF
usage: verify.sh boot | build | install | launch | doctor | cleanup
       verify.sh ui [name]                        list on-screen elements (id, text, center, interactions)
       verify.sh wait --id|--text|--desc <v> [s]  wait until an element is on screen
       verify.sh assert [--not] --id|--text|--desc <v>
       verify.sh tap --id|--text|--desc <v> [index]
       verify.sh type "<text>" | key <KEYCODE>
       verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>'
       verify.sh rec start <name> | rec stop       screen video of a recipe, pulled into evidence
       verify.sh shot <name> | logcat
EOF
     exit 2 ;;
esac
