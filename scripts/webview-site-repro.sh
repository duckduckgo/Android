#!/usr/bin/env bash
set -euo pipefail

package="com.duckduckgo.mobile.android.debug"
activity="com.duckduckgo.app.dispatchers.IntentDispatcherActivity"
wait_seconds=15
out_dir=""
label="site-repro"
url=""
tap=""
swipe=""

usage() {
  cat <<EOF
Usage: scripts/webview-site-repro.sh --url URL [options]

Options:
  --package PACKAGE       App package. Default: ${package}
  --activity ACTIVITY     URL dispatch activity. Default: ${activity}
  --wait SECONDS          Wait after load/action. Default: ${wait_seconds}
  --label LABEL           Output label. Default: ${label}
  --out DIR               Output directory. Default: /tmp/webview-site-repro-<label>-<timestamp>
  --tap X,Y               Tap after initial page load, then capture again.
  --swipe X1,Y1,X2,Y2,D   Swipe after initial page load, then capture again.
  -h, --help              Show this help.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --package) package="$2"; shift 2 ;;
    --activity) activity="$2"; shift 2 ;;
    --wait) wait_seconds="$2"; shift 2 ;;
    --label) label="$2"; shift 2 ;;
    --out) out_dir="$2"; shift 2 ;;
    --url) url="$2"; shift 2 ;;
    --tap) tap="$2"; shift 2 ;;
    --swipe) swipe="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage; exit 1 ;;
  esac
done

if [[ -z "${url}" ]]; then
  usage
  exit 1
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
safe_label="$(echo "${label}" | tr -c 'A-Za-z0-9._-' '-')"
out_dir="${out_dir:-/tmp/webview-site-repro-${safe_label}-${timestamp}}"
mkdir -p "${out_dir}"

log_pattern="BrowserWebViewClient|BrowserChromeClient|onPageStarted|onPageFinished|onProgressChanged|shouldOverride|Renderer|RenderProcess|WEB_VIEW"

capture_logs() {
  local name="$1"
  local pid
  pid="$(adb shell pidof "${package}" | tr -d '\r' || true)"
  if [[ -z "${pid}" ]]; then
    echo "No process found for ${package}" > "${out_dir}/${name}-logcat.txt"
    return
  fi
  adb logcat -d --pid "${pid}" -v time | rg "${log_pattern}" > "${out_dir}/${name}-logcat.txt" || true
}

{
  echo "timestamp=${timestamp}"
  echo "package=${package}"
  echo "activity=${activity}"
  echo "url=${url}"
  echo "tap=${tap}"
  echo "swipe=${swipe}"
  echo
  adb devices
  echo
  adb shell wm size || true
  adb shell wm density || true
  echo
  adb shell getprop ro.build.version.sdk || true
  adb shell getprop ro.product.cpu.abilist || true
  echo
  adb shell dumpsys webviewupdate || true
} > "${out_dir}/environment.txt"

adb logcat -c
adb shell am start -n "${package}/${activity}" -a android.intent.action.VIEW -d "${url}" > "${out_dir}/initial-am-start.txt"
sleep "${wait_seconds}"
adb exec-out screencap -p > "${out_dir}/initial.png"
capture_logs "initial"

if [[ -n "${tap}" || -n "${swipe}" ]]; then
  adb logcat -c
  if [[ -n "${tap}" ]]; then
    IFS=',' read -r x y <<< "${tap}"
    adb shell input tap "${x}" "${y}"
  else
    IFS=',' read -r x1 y1 x2 y2 duration <<< "${swipe}"
    adb shell input swipe "${x1}" "${y1}" "${x2}" "${y2}" "${duration}"
  fi
  sleep "${wait_seconds}"
  adb exec-out screencap -p > "${out_dir}/after-action.png"
  capture_logs "after-action"
fi

echo "Saved repro artifacts to ${out_dir}"
