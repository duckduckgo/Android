#!/usr/bin/env bash
# Local PIR scan memory & timing benchmark harness.
# Sweeps the detached-WebView count and records per-process memory + scan duration.
# Requires: an internal (debuggable) build installed, one device over adb, a seeded PIR profile.
set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/parse.sh
source "$DIR/lib/parse.sh"

# ---- Config (run matrix; see spec §8) ----
COUNTS=(1 10 20 40)
REPS=3
SAMPLE_INTERVAL="${SAMPLE_INTERVAL:-1}"     # seconds between memory samples
DETAIL_INTERVAL="${DETAIL_INTERVAL:-10}"    # seconds between detailed per-pid captures
MAX_SCAN_SECONDS="${MAX_SCAN_SECONDS:-1800}"  # safety timeout per run
EXECUTION_TYPE="${EXECUTION_TYPE:-MANUAL_INITIAL}"
SERVICE="com.duckduckgo.pir.impl.scan.PirForegroundScanService"
OVERRIDE_FILE="pir_benchmark_webview_count"
EXTRA_KEY="extra_execution_type"             # PirForegroundScanService.EXTRA_EXECUTION_TYPE literal; value is PirExecutionType.name

PKG=""
OUT_DIR="$DIR/results"

usage() {
  cat <<EOF
Usage: $0 [--package <applicationId>] [--out <dir>]
  --package  App id (default: auto-detect a com.duckduckgo.* package)
  --out      Output directory (default: $OUT_DIR)
Env overrides: SAMPLE_INTERVAL, DETAIL_INTERVAL, MAX_SCAN_SECONDS, EXECUTION_TYPE
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --package) PKG="$2"; shift 2 ;;
    --out) OUT_DIR="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

die() { echo "ERROR: $*" >&2; exit 1; }

command -v adb >/dev/null || die "adb not found on PATH"
[ "$(adb get-state 2>/dev/null)" = "device" ] || die "no device in 'device' state (check 'adb devices')"

if [ -z "$PKG" ]; then
  PKG="$(adb shell pm list packages | sed 's/package://' | tr -d '\r' | grep -E 'com\.duckduckgo\.mobile\.android(\.debug)?$' | head -n1)"
  [ -n "$PKG" ] || die "could not auto-detect package; pass --package"
fi
echo "Package: $PKG"

# run-as sanity: confirms the build is debuggable (required to write the override file)
adb shell run-as "$PKG" true 2>/dev/null || die "run-as failed for $PKG — is this a debuggable internal build?"

TS="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"
TIMELINE="$OUT_DIR/pir_bench_timeline_$TS.csv"
SUMMARY="$OUT_DIR/pir_bench_summary_$TS.csv"
echo "count,rep,elapsed_ms,phase,process_name,pid,total_pss_kb,java_heap_kb,native_heap_kb,graphics_kb,code_kb" > "$TIMELINE"
echo "count,rep,duration_ms,peak_total_pss_kb,mean_total_pss_kb,peak_pir_pss_kb,peak_renderer_pss_kb,renderer_process_count" > "$SUMMARY"

# Emits app-group process rows: pss_kb|name|pid  (our package + webview renderer processes)
app_group_processes() {
  adb shell dumpsys meminfo 2>/dev/null | tr -d '\r' | parse_global_pss \
    | grep -E "\|($PKG(:[^|]*)?|[^|]*sandboxed_process[^|]*|webview_zygote)\|"
}

pir_pid() {
  adb shell pidof "$PKG:pir" 2>/dev/null | tr -d '\r' | awk '{print $1}'
}

run_once() {
  local count="$1" rep="$2"
  echo "=== count=$count rep=$rep ==="

  adb shell am force-stop "$PKG"
  # Write the override via run-as (debuggable builds only).
  adb shell run-as "$PKG" sh -c "printf '%s' '$count' > files/$OVERRIDE_FILE" \
    || die "failed to write override file"
  adb logcat -c

  # Trigger the scan directly against the (non-exported, but debuggable) foreground service.
  adb shell am start-foreground-service -n "$PKG/$SERVICE" --es "$EXTRA_KEY" "$EXECUTION_TYPE" \
    || die "failed to start $SERVICE"

  local start_epoch_ms scan_start_ms="" scan_done=0 elapsed=0 last_detail=-999
  start_epoch_ms="$(adb shell date +%s%3N | tr -d '\r')"
  local peak_total=0 peak_pir=0 peak_renderer=0 sum_total=0 n_samples=0 renderer_count=0

  while [ "$scan_done" -eq 0 ] && [ "$elapsed" -lt "$MAX_SCAN_SECONDS" ]; do
    local now_ms rel_ms phase="steady"
    now_ms="$(adb shell date +%s%3N | tr -d '\r')"
    rel_ms=$((now_ms - start_epoch_ms))

    # Detect markers from logcat (message-based; the logcat lib uses class tags).
    local markers
    markers="$(adb logcat -d 2>/dev/null | tr -d '\r' | grep 'PIR-BENCH:')"
    if [ -z "$scan_start_ms" ] && echo "$markers" | grep -q 'scan_start'; then
      scan_start_ms="$rel_ms"
    fi
    if echo "$markers" | grep -q 'scan_complete'; then
      scan_done=1
    fi
    [ -z "$scan_start_ms" ] && phase="rampup"

    # Per-tick: global per-process Total PSS for the whole app group.
    local procs total_this_sample=0 pir_this=0 renderer_this=0 r_count=0
    procs="$(app_group_processes)"
    local do_detail=0
    if [ $((rel_ms / 1000 - last_detail)) -ge "$DETAIL_INTERVAL" ]; then
      do_detail=1
      last_detail=$((rel_ms / 1000))
    fi

    while IFS='|' read -r pss name pid; do
      [ -z "$pss" ] && continue
      total_this_sample=$((total_this_sample + pss))
      case "$name" in
        "$PKG:pir") pir_this=$pss ;;
        *sandboxed_process*|*webview*) renderer_this=$((renderer_this + pss)); r_count=$((r_count + 1)) ;;
      esac

      local java="" nat="" code="" gfx=""
      if [ "$do_detail" -eq 1 ]; then
        local detail
        detail="$(adb shell dumpsys meminfo "$pid" 2>/dev/null | tr -d '\r' | parse_detail_pss)"
        IFS='|' read -r java nat code gfx _ <<EOF
$detail
EOF
      fi
      echo "$count,$rep,$rel_ms,$phase,$name,$pid,$pss,$java,$nat,$gfx,$code" >> "$TIMELINE"
    done <<EOF
$procs
EOF

    [ "$total_this_sample" -gt "$peak_total" ] && peak_total=$total_this_sample
    [ "$pir_this" -gt "$peak_pir" ] && peak_pir=$pir_this
    [ "$renderer_this" -gt "$peak_renderer" ] && peak_renderer=$renderer_this
    [ "$r_count" -gt "$renderer_count" ] && renderer_count=$r_count
    sum_total=$((sum_total + total_this_sample))
    n_samples=$((n_samples + 1))

    sleep "$SAMPLE_INTERVAL"
    elapsed=$(( (rel_ms / 1000) + SAMPLE_INTERVAL ))
  done

  # Pull the authoritative durationMs from the scan_complete marker if present.
  local duration_ms
  duration_ms="$(adb logcat -d 2>/dev/null | tr -d '\r' | grep 'PIR-BENCH: scan_complete' \
    | sed -n 's/.*durationMs=\([0-9]*\).*/\1/p' | tail -n1)"
  [ -z "$duration_ms" ] && duration_ms="$rel_ms"

  local mean_total=0
  [ "$n_samples" -gt 0 ] && mean_total=$((sum_total / n_samples))
  echo "$count,$rep,$duration_ms,$peak_total,$mean_total,$peak_pir,$peak_renderer,$renderer_count" >> "$SUMMARY"
  adb shell am force-stop "$PKG"
}

for c in "${COUNTS[@]}"; do
  for r in $(seq 1 "$REPS"); do
    run_once "$c" "$r"
  done
done

echo ""
echo "=== Summary (peak/mean total PSS in MB, duration in s) ==="
awk -F, 'NR>1 {
  cnt[$1]++; dur[$1]+=$3; pk[$1]+=$4; mn[$1]+=$5; pir[$1]+=$6; rnd[$1]+=$7
} END {
  printf "%-6s %-8s %-12s %-12s %-12s %-12s\n","count","reps","dur_s","peak_MB","pir_peak_MB","rnd_peak_MB"
  for (c in cnt) printf "%-6s %-8s %-12.1f %-12.1f %-12.1f %-12.1f\n", \
    c, cnt[c], dur[c]/cnt[c]/1000, pk[c]/cnt[c]/1024, pir[c]/cnt[c]/1024, rnd[c]/cnt[c]/1024
}' "$SUMMARY" | sort -n

echo ""
echo "Timeline: $TIMELINE"
echo "Summary:  $SUMMARY"
