#!/usr/bin/env bash
# Build, install and run the network probe app.
#
#   probe.sh install                      build the APK with the SDK tools (no Gradle) and install it
#   probe.sh run <hosts> [mode] [timeout] run it and print one RESULT line per host
#
# hosts is comma separated; mode is single | window | split | late-sni (see ProbeActivity).
set -euo pipefail

SKILL_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROBE_DIR="$SKILL_DIR/probe"
PACKAGE="dev.verifyandroid.probe"
KEYSTORE="$HOME/.android/debug.keystore"

find_sdk() {
  local repo props
  if [ -n "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}" ]; then echo "${ANDROID_HOME:-$ANDROID_SDK_ROOT}"; return; fi
  repo="$(git -C "$SKILL_DIR" rev-parse --show-toplevel)"
  props="$(sed -n 's/^sdk\.dir=//p' "$repo/local.properties" 2>/dev/null | sed 's/\\:/:/g')"
  if [ -n "$props" ]; then echo "$props"; return; fi
  for default in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do   # macOS, Linux
    if [ -d "$default" ]; then echo "$default"; return; fi
  done
  echo "Android SDK not found: set ANDROID_HOME, or sdk.dir in local.properties" >&2
  exit 2
}
SDK="$(find_sdk)"
command -v adb >/dev/null || PATH="$SDK/platform-tools:$PATH"

install_probe() {
  local build_tools platform
  # Stable releases only: preview platforms and rc build-tools sort last but may be incomplete.
  build_tools="$SDK/build-tools/$(ls "$SDK/build-tools" | grep -E '^[0-9]+(\.[0-9]+)*$' | sort -V | tail -1)"
  platform="$SDK/platforms/$(ls "$SDK/platforms" | grep -E '^android-[0-9]+$' | sort -V | tail -1)/android.jar"
  out="$(mktemp -d)"
  trap 'rm -rf "$out"' EXIT

  # -source 11 against android.jar warns about the bootclasspath; only errors matter.
  javac -nowarn -Xlint:-options -source 11 -target 11 -classpath "$platform" -d "$out/classes" \
    $(find "$PROBE_DIR/src" -name '*.java')
  "$build_tools/d8" --min-api 26 --lib "$platform" --output "$out" $(find "$out/classes" -name '*.class')
  "$build_tools/aapt2" link -o "$out/unaligned.apk" -I "$platform" \
    --manifest "$PROBE_DIR/AndroidManifest.xml" \
    --min-sdk-version 26 --target-sdk-version 35 --version-code 1 --version-name 1.0
  # Python rather than zip, which minimal Linux installs and CI images often lack.
  python3 -c 'import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "a") as apk: apk.write(sys.argv[2], "classes.dex")' \
    "$out/unaligned.apk" "$out/classes.dex"
  "$build_tools/zipalign" -f 4 "$out/unaligned.apk" "$out/probe.apk"
  # Gradle creates this key on the first debug build; a machine that never built one has none yet.
  if [ ! -f "$KEYSTORE" ]; then
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -keystore "$KEYSTORE" -storepass android -alias androiddebugkey -keypass android \
      -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
  fi
  "$build_tools/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android \
    --key-pass pass:android --ks-key-alias androiddebugkey "$out/probe.apk"

  adb install -r "$out/probe.apk" >/dev/null
  echo "installed $PACKAGE"
}

run_probe() {
  local hosts="$1" mode="${2:-single}" timeout="${3:-5000}" run="$RANDOM$RANDOM"
  adb shell am start -S -W -n "$PACKAGE/.ProbeActivity" \
    --es hosts "$hosts" --es mode "$mode" --ei timeout "$timeout" --es run "$run" >/dev/null
  # One host can take up to 2x timeout (connect + read) before the probe logs DONE.
  local hosts_count wait_s
  hosts_count=$(tr ',' '\n' <<<"$hosts" | wc -l)
  wait_s=$(( (timeout * 2 * hosts_count) / 1000 + 5 ))
  local lines=""
  for _ in $(seq "$wait_s"); do
    # Captured instead of piped: under pipefail, grep -q closing the pipe early would fail the check.
    lines="$(adb logcat -d -s VerifyProbe:I)"
    grep -q "DONE run=$run" <<<"$lines" && break
    sleep 1
  done
  grep "run=$run" <<<"$lines" | sed 's/^.*VerifyProbe: //'
  if ! grep -q "DONE run=$run" <<<"$lines"; then
    echo "INCOMPLETE run=$run: no DONE after ${wait_s}s, hosts without a RESULT line were not probed" >&2
    return 1
  fi
}

case "${1:-}" in
  install) install_probe ;;
  run) shift; run_probe "$@" ;;
  *) sed -n '2,7p' "$0"; exit 1 ;;
esac
