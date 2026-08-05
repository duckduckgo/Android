#!/usr/bin/env bash
#
# Drive N page loads under a single Perfetto capture, then report the ddg.* statistics.
#
# Exists because a hand-driven capture gives 2 page loads, which cannot resolve differences below
# roughly 20%. N=30 takes about five minutes and moves the noise floor low enough to compare two
# builds or two feature-flag states.
#
#   ./pageload-trace.sh capture https://www.bbc.co.uk/news -n 30 -o enabled.perfetto-trace
#   ./pageload-trace.sh analyze enabled.perfetto-trace
#   ./pageload-trace.sh compare enabled.perfetto-trace disabled.perfetto-trace
#
# Requires: an internalRelease build with <profileable android:shell="true"/> installed, adb on
# PATH, and trace_processor_shell for the analyze/compare steps.
set -euo pipefail

PKG="${DDG_PKG:-com.duckduckgo.mobile.android}"
DEVICE_TRACE="/data/misc/perfetto-traces/pageload-script.perfetto-trace"
TP="${TRACE_PROCESSOR:-trace_processor_shell}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

die() { echo "error: $*" >&2; exit 1; }
hr() { printf '%s\n' "------------------------------------------------------------"; }

# ---------------------------------------------------------------- capture

cmd_capture() {
  local url="${1:-}"; shift || true
  local navs=30 settle=8 include_sched=0 out="pageload.perfetto-trace"
  while [ $# -gt 0 ]; do
    case "$1" in
      -n) navs="$2"; shift 2 ;;
      -s) settle="$2"; shift 2 ;;
      -o) out="$2"; shift 2 ;;
      --sched) include_sched=1; shift ;;
      *) die "unknown option: $1" ;;
    esac
  done
  [ -n "$url" ] || die "usage: $0 capture <url> [-n navs] [-s settle_s] [-o out] [--sched]"

  command -v adb >/dev/null || die "adb not on PATH"
  [ -n "$(adb devices | sed -n '2p')" ] || die "no device connected"
  adb shell pm path "$PKG" >/dev/null 2>&1 || die "$PKG is not installed"

  # One warm-up navigation is discarded by the analysis, so capture navs+1.
  local total=$((navs + 1))
  local duration_ms=$(( (total * settle + 15) * 1000 ))

  # sched_switch/sched_waking are omitted by default. They are what a thread-state analysis needs,
  # but at this duration they dwarf the app's own events and can push ddg.* slices out of the ring
  # buffer — which looks exactly like missing instrumentation. Pass --sched if you need them.
  local sched_events=""
  if [ "$include_sched" = "1" ]; then
    sched_events='      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_waking"'
  fi

  cat > "$TMP/cfg.pbtx" <<EOF
buffers { size_kb: 262144 fill_policy: RING_BUFFER }
data_sources {
  config {
    name: "linux.ftrace"
    ftrace_config {
$sched_events
      atrace_categories: "view"
      atrace_categories: "webview"
      atrace_categories: "wm"
      atrace_apps: "$PKG"
    }
  }
}
data_sources { config { name: "linux.process_stats" } }
duration_ms: $duration_ms
EOF

  echo "url        $url"
  echo "navs       $total ($navs measured + 1 warm-up)"
  echo "settle     ${settle}s each"
  echo "duration   $((duration_ms / 1000))s"
  echo "sched      $([ "$include_sched" = 1 ] && echo on || echo 'off (pass --sched to include)')"
  hr

  # Clean slate, so the first (discarded) navigation absorbs any cold-start cost.
  adb shell am force-stop "$PKG" || true
  sleep 2

  # Config goes in on stdin. Pushing it to /data/local/tmp and passing a path fails with
  # "Permission denied" — perfetto runs in a restricted SELinux domain that cannot read there.
  adb shell perfetto -c - --txt -o "$DEVICE_TRACE" < "$TMP/cfg.pbtx" &
  local perfetto_pid=$!
  sleep 3

  for i in $(seq 0 "$navs"); do
    # A unique query parameter per navigation forces a fresh main-frame load rather than a
    # same-document update, so each iteration produces its own ddg.pageLoad envelope.
    local sep="?"; case "$url" in *\?*) sep="&" ;; esac
    adb shell am start -a android.intent.action.VIEW \
      -d "'${url}${sep}ddgperf=${i}'" "$PKG" >/dev/null 2>&1 || true
    if [ "$i" = "0" ]; then printf '  warm-up'; else printf '\r  navigation %d/%d' "$i" "$navs"; fi
    sleep "$settle"
  done
  printf '\n'
  hr

  echo "waiting for the capture to close..."
  wait "$perfetto_pid" || true
  adb pull "$DEVICE_TRACE" "$out" >/dev/null || die "could not pull the trace"
  echo "wrote $out ($(du -h "$out" | cut -f1))"
  hr
  cmd_analyze "$out"
}

# ---------------------------------------------------------------- analyze

# Runs one SQL statement and echoes the raw CSV rows, minus trace_processor's chatter.
q() {
  printf '%s\n' "$2" > "$TMP/q.sql"
  "$TP" -q "$TMP/q.sql" "$1" 2>/dev/null \
    | grep -v '^column\|Query execution\|Loading trace\|Trace loaded\|^$' \
    | tail -n +2
}

cmd_analyze() {
  local t="${1:-}"
  [ -n "$t" ] || die "usage: $0 analyze <trace>"
  [ -f "$t" ] || die "no such trace: $t"
  command -v "$TP" >/dev/null || die "$TP not on PATH (set TRACE_PROCESSOR)"

  echo "trace  $t"
  hr

  # The wait counter is emitted once per request from inside that request's own call stack. If these
  # three counts diverge, atrace dropped events and every mean below is biased toward whatever
  # survived — check this before trusting anything else.
  local sanity n_req n_wait n_hop
  # Every column needs an AS alias: without one, trace_processor names the column after the whole
  # subquery text, which spans lines and breaks the header/row split below.
  sanity=$(q "$t" "SELECT (SELECT COUNT(*) FROM slice WHERE name='ddg.interceptRequest') AS a, (SELECT COUNT(*) FROM counter c JOIN counter_track k ON k.id=c.track_id WHERE k.name='ddg.interceptRequest.mainHopWaitUs') AS b, (SELECT COUNT(*) FROM slice WHERE name='ddg.interceptRequest.mainHop') AS c;")
  IFS=',' read -r n_req n_wait n_hop <<<"$sanity"
  printf 'coverage   requests=%s  wait_samples=%s  hops=%s' "$n_req" "$n_wait" "$n_hop"
  if [ "$n_req" = "$n_wait" ] && [ "$n_req" = "$n_hop" ]; then
    printf '   OK (1:1)\n'
  else
    printf '   MISMATCH -- atrace dropped events, means are biased\n'
  fi

  local loads
  loads=$(q "$t" "SELECT COUNT(*) FROM slice WHERE name='ddg.pageLoad' AND dur>0;")
  echo "page loads $loads complete (unfinished envelopes have dur<0 and are excluded)"
  hr

  echo "PAGE LOAD (ms)"
  q "$t" "SELECT ROUND(AVG(dur)/1e6,1) AS mean, ROUND(MIN(dur)/1e6,1) AS min, ROUND(MAX(dur)/1e6,1) AS max
          FROM slice WHERE name='ddg.pageLoad' AND dur>0;" | awk -F, '{printf "  mean %-10.1f min %-10.1f max %.1f\n", $1+0, $2+0, $3+0}'

  echo "  time to first paint:"
  q "$t" "SELECT ROUND(AVG(ttfp)/1e6,1), ROUND(MIN(ttfp)/1e6,1), ROUND(MAX(ttfp)/1e6,1) FROM (
            SELECT (MIN(c.ts)-l.ts) AS ttfp
            FROM (SELECT id,ts,dur FROM slice WHERE name='ddg.pageLoad' AND dur>0) l
            JOIN slice c ON c.name='ddg.pageCommitVisible' AND c.ts>=l.ts AND c.ts<l.ts+l.dur
            GROUP BY l.id);" | awk -F, '{printf "    mean %-10.1f min %-10.1f max %.1f\n", $1+0, $2+0, $3+0}'
  hr

  echo "INTERCEPTION per request (ms)"
  q "$t" "SELECT ROUND(AVG(dur)/1e6,2) FROM slice WHERE name='ddg.interceptRequest';" \
    | awk '{printf "  mean       %.2f\n", $1+0}'
  q "$t" "SELECT ROUND(MAX(CASE WHEN p<=0.5 THEN ms END),2), ROUND(MAX(CASE WHEN p<=0.9 THEN ms END),2),
                 ROUND(MAX(CASE WHEN p<=0.99 THEN ms END),2), ROUND(MAX(ms),2)
          FROM (SELECT dur/1e6 AS ms, CAST(ROW_NUMBER() OVER (ORDER BY dur) AS REAL)/COUNT(*) OVER () AS p
                FROM slice WHERE name='ddg.interceptRequest');" \
    | awk -F, '{printf "  p50 %-9.2f p90 %-9.2f p99 %-9.2f max %.2f\n", $1+0, $2+0, $3+0, $4+0}'

  echo "  main-thread wait (ms):"
  q "$t" "SELECT ROUND(AVG(c.value)/1000.0,2) FROM counter c JOIN counter_track k ON k.id=c.track_id
          WHERE k.name='ddg.interceptRequest.mainHopWaitUs';" | awk '{printf "    mean     %.2f\n", $1+0}'
  q "$t" "SELECT ROUND(MAX(CASE WHEN p<=0.5 THEN ms END),2), ROUND(MAX(CASE WHEN p<=0.9 THEN ms END),2),
                 ROUND(MAX(CASE WHEN p<=0.99 THEN ms END),2), ROUND(MAX(ms),2)
          FROM (SELECT c.value/1000.0 AS ms,
                       CAST(ROW_NUMBER() OVER (ORDER BY c.value) AS REAL)/COUNT(*) OVER () AS p
                FROM counter c JOIN counter_track k ON k.id=c.track_id
                WHERE k.name='ddg.interceptRequest.mainHopWaitUs');" \
    | awk -F, '{printf "    p50 %-9.2f p90 %-9.2f p99 %-9.2f max %.2f\n", $1+0, $2+0, $3+0, $4+0}'
  hr

  # The three components come from the same trace, so the shares are internally consistent.
  echo "DECOMPOSITION of one intercepted request"
  local total wait work
  total=$(q "$t" "SELECT AVG(dur)/1e6 FROM slice WHERE name='ddg.interceptRequest';")
  wait=$(q "$t" "SELECT AVG(c.value)/1000.0 FROM counter c JOIN counter_track k ON k.id=c.track_id
                 WHERE k.name='ddg.interceptRequest.mainHopWaitUs';")
  work=$(q "$t" "SELECT AVG(dur)/1e6 FROM slice WHERE name='ddg.interceptRequest.mainHop';")
  awk -v t="$total" -v w="$wait" -v k="$work" 'BEGIN {
    if (t <= 0) { print "  no interception slices"; exit }
    logic = t - w - k;
    printf "  interception logic   %8.2f ms  %5.1f%%\n", logic, 100*logic/t;
    printf "  waiting for main     %8.2f ms  %5.1f%%\n", w, 100*w/t;
    printf "  main-thread work     %8.2f ms  %5.1f%%\n", k, 100*k/t;
    printf "  %-21s %8.2f ms\n", "total", t;
  }'
  hr

  echo "CONTENT SCOPE (ms, max per load where cached)"
  q "$t" "SELECT name, COUNT(*), ROUND(AVG(dur)/1e6,2), ROUND(MAX(dur)/1e6,2)
          FROM slice WHERE name IN ('ddg.contentScope.getScript','ddg.contentScope.dispatchJavascript',
                                    'ddg.jsInject.onPageStarted')
          GROUP BY name ORDER BY name;" \
    | awk -F, '{gsub(/"/,"",$1); printf "  %-38s n=%-5s mean %-9.2f max %.2f\n", $1, $2, $3+0, $4+0}'

  # More than one injection per load means the bundle is compiled and run again on redirects,
  # which multiplies every content-scope cost above.
  echo "  injections per page load:"
  q "$t" "SELECT ROUND(AVG(n),2), MIN(n), MAX(n) FROM (
            SELECT COUNT(s.id) AS n
            FROM (SELECT id,ts,dur FROM slice WHERE name='ddg.pageLoad' AND dur>0) l
            LEFT JOIN slice s ON s.name='ddg.contentScope.dispatchJavascript'
                            AND s.ts>=l.ts AND s.ts<l.ts+l.dur
            GROUP BY l.id);" \
    | awk -F, '{printf "    mean %-7.2f min %-7d max %-7d%s\n", $1+0, $2+0, $3+0, ($3+0>1 ? "<-- re-injection on some loads" : "")}'
  hr

  echo "DDG MAIN-THREAD COST"
  q "$t" "SELECT name, COUNT(*), ROUND(SUM(dur)/1e6,1)
          FROM slice WHERE name IN ('ddg.onPageStarted','ddg.onPageFinished','ddg.jsInject.onPageStarted',
                                    'ddg.interceptRequest.mainHop')
          GROUP BY name ORDER BY name;" \
    | awk -F, '{gsub(/"/,"",$1); printf "  %-38s n=%-5s total %8.1f ms\n", $1, $2, $3+0}'
  hr

  # Requests arriving after onPageFinished cost the same per request but never show up in a
  # page-load metric, so they are easy to miss entirely.
  echo "SHARE OF INTERCEPTION INSIDE A PAGE LOAD"
  q "$t" "SELECT (SELECT COUNT(*) FROM slice WHERE name='ddg.interceptRequest'),
                 (SELECT COUNT(*) FROM slice s
                    WHERE s.name='ddg.interceptRequest'
                      AND EXISTS (SELECT 1 FROM slice l WHERE l.name='ddg.pageLoad' AND l.dur>0
                                    AND s.ts>=l.ts AND s.ts<l.ts+l.dur));" \
    | awk -F, '{ if ($1>0) printf "  in-envelope %s of %s requests (%.1f%%); %.1f%% arrive after the load completes\n", $2, $1, 100*$2/$1, 100*($1-$2)/$1 }'
}

# ---------------------------------------------------------------- compare

cmd_compare() {
  local a="${1:-}" b="${2:-}"
  [ -n "$a" ] && [ -n "$b" ] || die "usage: $0 compare <trace-a> <trace-b>"
  for t in "$a" "$b"; do
    hr; echo "### $t"; hr
    cmd_analyze "$t"
    echo
  done
  hr
  cat <<'EOF'
Reading the comparison

  Establish the noise floor first. Interception cost cannot be affected by a content-scope
  change, so whatever gap you see on "INTERCEPTION mean" between two builds that differ only
  in content-scope IS this setup's noise floor. Treat any single-metric difference smaller
  than that as flat, not as a measured improvement.

  Check "coverage OK (1:1)" in both runs before comparing anything.
EOF
}

# ---------------------------------------------------------------- main

case "${1:-}" in
  capture) shift; cmd_capture "$@" ;;
  analyze) shift; cmd_analyze "$@" ;;
  compare) shift; cmd_compare "$@" ;;
  *) cat <<EOF
usage:
  $0 capture <url> [-n navs] [-s settle_s] [-o out.perfetto-trace] [--sched]
  $0 analyze <trace>
  $0 compare <trace-a> <trace-b>

env:
  DDG_PKG=$PKG
  TRACE_PROCESSOR=$TP
EOF
     exit 1 ;;
esac
