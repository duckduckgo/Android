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
cmd_build() { td refresh --no-pull; }

# Reinstalls the last bridge build without rebuilding, keeping app data.
cmd_install() {
  local f; f="$(ls -t "$APK_DIR"/*.apk 2>/dev/null | head -1)"
  [[ -n "$f" ]] || die "no APK in $APK_DIR; run: verify.sh build"
  a install -r -t "$f" >/dev/null
  a shell am force-stop "$PKG"
  echo "installed $(basename "$f") on $(serial)"
}

cmd_doctor() {
  local ok=1 s; s="$(serial)"
  "$ADB" devices | grep -q "^$s[[:space:]]*device" && echo "PASS $s online ($(label "$s"))" || { echo "FAIL $s not online"; ok=0; }
  if owned "$s"; then
    kill -0 "$(cat "$STATE/dev/$s/pid")" 2>/dev/null && echo "INFO emulator started by this run" || { echo "FAIL emulator process gone"; ok=0; }
  else echo "INFO user's device: no reset, uninstall or data clearing without asking"; fi
  command -v node >/dev/null && echo "PASS node $(node --version) for web commands" || { echo "FAIL node not on PATH"; ok=0; }
  echo "INFO worktree $(git -C "$REPO" rev-parse --abbrev-ref HEAD)@$(git -C "$REPO" rev-parse --short HEAD), dirty files: $(git -C "$REPO" status --porcelain | wc -l | tr -d ' ')"
  echo "INFO evidence -> $(evidence_dir)"
  # trapdoor checks the bridge, protocol, catalog and that the installed build matches HEAD.
  td doctor || ok=0
  [[ $ok == 1 ]] || exit 1
}

# trapdoor pinned to the device and checkout. Runs from the device's evidence folder, so relative
# capture paths (shot, record, proof) land there.
cmd_t() { local d; d="$(evidence_dir)"; (cd "$d" && td "$@"); }

# trapdoor's assert has no wait; this polls it.
cmd_wait() {
  local selector="${1:?usage: verify.sh wait <selector> [secs]}" secs="${2:-20}" end
  end=$((SECONDS + secs))
  while (( SECONDS < end )); do
    td assert visible "$selector" >/dev/null 2>&1 && { echo "visible: $selector"; return; }
    sleep 1
  done
  die "not visible after ${secs}s: $selector"
}

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
# offset by the WebView bounds from trapdoor's hierarchy, so the tap is a real touch.
web_point() {
  local selector="$1" port rect wv
  port="$(cdp_port)"
  rect="$(cdp "$port" eval "(() => { const e = document.querySelector($(jq -Rn --arg s "$selector" '$s'));
    if (!e) return null; e.scrollIntoView({block: 'center'}); const r = e.getBoundingClientRect();
    return {x: r.left + r.width / 2, y: r.top + r.height / 2, dpr: devicePixelRatio}; })()")"
  [[ "$rect" != "null" ]] || die "no element for selector: $selector"
  wv="$(td hierarchy --json | jq -c '[.data.tappable[] | select(.class | test("WebView")) | select(.visible)] | last | .bounds')"
  [[ "$wv" != "null" && -n "$wv" ]] || die "no WebView on screen"
  jq -rn --argjson r "$rect" --argjson b "$wv" '"\(($b.left + $r.x * $r.dpr) | round) \(($b.top + $r.y * $r.dpr) | round)"'
}

cmd_web() {
  local sub="${1:-}"; shift || true
  case "$sub" in
    targets) cdp "$(cdp_port)" targets ;;
    eval)    cdp "$(cdp_port)" eval "${1:?usage: verify.sh web eval '<js>' [url-regex]}" "${2:-}" ;;
    tap)     local x y; read -r x y <<<"$(web_point "${1:?usage: verify.sh web tap '<css selector>'}")"
             a shell input tap "$x" "$y"; echo "tapped web $1 at $x,$y" ;;
    # trapdoor type only targets native editables; web fields take keystrokes into the focused element.
    type)    a shell input text "$(printf '%s' "${1:?usage: verify.sh web type '<text>'}" | sed 's/ /%s/g')" ;;
    *) die "usage: verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>' | type '<text>'" ;;
  esac
}

# trapdoor's hierarchy has no checked state; uiautomator does. Prints true/false for the Nth (1-based)
# visible view with this resource id, so a recipe can read a switch before and after tapping it.
cmd_checked() {
  local id="${1:?usage: verify.sh checked <resource-id> [index]}" index="${2:-1}" value
  value="$(a exec-out uiautomator dump /dev/tty 2>/dev/null | grep -o "<node [^>]*resource-id=\"[^\"]*:id/$id\"[^>]*>" \
    | sed -n "${index}p" | grep -o 'checked="[a-z]*"' | head -1 | cut -d'"' -f2)"
  [[ -n "$value" ]] || die "no view with id $id at index $index (uiautomator killed? another UI-automation client such as Maestro may be running)"
  echo "$value"
}

# Opens every catalog screen and surface whose name matches a regex and saves what each one shows:
# where, the tappable elements and a screenshot. Raw material for a feature file, not proof of a user path.
cmd_survey() {
  local pattern="${1:?usage: verify.sh survey '<name regex>'   e.g. '^settings'}"
  local out; out="$VERIFY_ROOT/survey/$(printf '%s' "$pattern" | tr -c 'A-Za-z0-9._-' '_')"
  mkdir -p "$out"
  local targets
  targets="$( { td list --screens; td list --surfaces; } | awk -F'\t' -v p="$pattern" '$1 ~ p { print $1 "\t" $2 "\t" $NF }')"
  [[ -n "$targets" ]] || die "no catalog targets match $pattern; try: verify.sh t find <words>"
  {
    echo "# Survey: $pattern"
    echo
    echo "$(label "$(serial)"), $(git -C "$REPO" rev-parse --short HEAD), $(date '+%Y-%m-%d %H:%M')"
    echo
    echo "| Target | Type | Result | Reached | Tappable |"
    echo "|---|---|---|---|---|"
  } > "$out/README.md"
  local name type drivable reply status where count
  while IFS=$'\t' read -r name type drivable; do
    if [[ "$drivable" != "not-drivable: -" ]]; then
      echo "| \`$name\` | $type | not drivable | ${drivable#not-drivable: } | |" >> "$out/README.md"; continue
    fi
    td dismiss >/dev/null 2>&1 || true
    reply="$(td goto "$name" --json 2>/dev/null || true)"
    status="$(jq -r '.status // "error"' <<<"$reply" 2>/dev/null || echo error)"
    if [[ "$status" == "notReady" ]]; then
      reply="$(td goto "$name" --json 2>/dev/null || true)"; status="$(jq -r '.status // "error"' <<<"$reply" 2>/dev/null || echo error)"
    fi
    if [[ "$status" == "ok" ]]; then
      where="$(td where --json | jq -r '.data | "\(.screenName)\(if .surface then " / " + .surface else "" end)"')"
      td hierarchy --interactive-only --json > "$out/$name.hierarchy.json" 2>/dev/null || true
      count="$(jq '.data.tappable | length' "$out/$name.hierarchy.json" 2>/dev/null || echo "?")"
      (cd "$out" && td shot "$name.png" >/dev/null 2>&1) || true
      echo "| \`$name\` | $type | ok | $where | $count |" >> "$out/README.md"
    else
      echo "| \`$name\` | $type | $status | $(jq -r '.reason // ""' <<<"$reply" 2>/dev/null | tr '|' '/') | |" >> "$out/README.md"
    fi
    echo "$name: $status"
  done <<<"$targets"
  td dismiss >/dev/null 2>&1 || true
  echo "survey: $out/README.md"
}

cmd_logcat() { local d; d="$(evidence_dir)"; a logcat -d > "$d/logcat-$(date +%H%M%S).txt"; echo "$d"; }

# Undoes only what the run did: stops recordings, removes its own adb forwards, and stops emulators
# it started. The user's devices and emulators keep running.
cmd_cleanup() {
  local s d
  for s in $( { selected; ls "$STATE/dev" 2>/dev/null; } | sort -u); do
    d="$STATE/dev/$s"
    if "$ADB" devices | grep -q "^$s[[:space:]]*device"; then
      (VERIFY_SERIAL="$s"; cd "$(evidence_dir)" && trapdoor --device "$s" --android-root "$REPO" record stop >/dev/null 2>&1) || true
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
  devices|use|on|each|boot|build|install|doctor|t|wait|checked|web|survey|logcat|cleanup) c="$1"; shift; "cmd_$c" "$@" ;;
  *) cat >&2 <<EOF
usage: verify.sh devices                      attached devices and AVDs on this machine
       verify.sh use <serial>...              choose the device(s) to verify on
       verify.sh boot [<avd>]                 start a user AVD, or the fallback when there are none
       verify.sh build | install | doctor | cleanup
       verify.sh each <command>...            run a command on every selected device
       verify.sh on <serial> <command>...     run a command on one selected device
       verify.sh t <trapdoor args>            trapdoor on the device, captures saved to evidence
       verify.sh wait <selector> [secs]       poll trapdoor assert visible (id:X, text:X, desc:X)
       verify.sh web targets | eval '<js>' [url-regex] | tap '<css selector>' | type '<text>'
       verify.sh checked <resource-id> [index]  true/false for a switch or checkbox (1-based index)
       verify.sh survey '<name regex>'       open every matching catalog target, save where/hierarchy/shot
       verify.sh logcat
EOF
     exit 2 ;;
esac
