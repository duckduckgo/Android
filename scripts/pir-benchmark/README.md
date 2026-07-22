# PIR Scan Memory & Timing Benchmark

Local harness to baseline how the detached-WebView pool size affects the PIR
scan's memory footprint and wall-clock time. See the design spec:
`docs/superpowers/specs/2026-07-22-pir-scan-memory-benchmark-design.md`.

## What it measures

Per WebView count in the sweep (`{1, 10, 20, 40}` × 3 reps by default), it records:
- Per-process Total PSS timeline for the app group: the `:pir` process **and** the
  WebView renderer/sandboxed process(es). WebView renders out-of-process, so
  measuring `:pir` alone undercounts — renderer memory is captured separately and
  the observed process topology is part of the output.
- Periodic per-process breakdown (Java heap / Native heap / Graphics / Code).
- Scan wall-clock duration (from the `PIR-BENCH: scan_complete` marker).

The timeline's `phase` column is only ever `rampup` (before the `scan_start` marker
is seen, i.e. before all runners are created) or `steady` — there is no distinct
teardown phase or `runner_destroyed` marker. WebViews are destroyed per-step inside
each runner's `stop()`, so there is no single global teardown boundary to mark.

## Prerequisites

1. **Internal, debuggable build** installed (`./gradlew installInternalDebug`). The
   count override is gated by `isInternalBuild()` and written via `adb run-as`, which
   only works on debuggable builds.
2. **Launch the app once** so broker data downloads into the database (the benchmark
   scans all active brokers). No Privacy Pro subscription/entitlement and no profile
   seeding are needed — the benchmark path is internal-build-only, bypasses the
   subscription gate, and uses a fixed built-in profile (`PirConstants.BENCHMARK_PROFILE`,
   "John Smith, 1990, New York, NY"). It also bypasses scan-job eligibility, so every run
   of the sweep scans all brokers (no reset needed between runs).
3. **One device** connected over adb (`adb devices` shows exactly one `device`).
   A physical device is recommended; emulator memory is not representative.
4. **Otherwise-idle device.** Other apps using WebView spawn their own renderer
   processes and can pollute renderer attribution — close them first.
5. macOS/Linux with `bash` and `adb` on PATH.

## Run

```bash
scripts/pir-benchmark/benchmark-pir-scan.sh
# or:
scripts/pir-benchmark/benchmark-pir-scan.sh --package com.duckduckgo.mobile.android.debug --out /tmp/pir-bench
```

Env overrides: `SAMPLE_INTERVAL` (s), `DETAIL_INTERVAL` (s), `MAX_SCAN_SECONDS`,
`EXECUTION_TYPE` (default `MANUAL_INITIAL`).

Outputs two CSVs (`pir_bench_timeline_<ts>.csv`, `pir_bench_summary_<ts>.csv`) plus a
printed summary table.

## Compare before/after an optimization

1. Run the sweep on the baseline branch; keep the summary CSV.
2. Run the identical sweep on the optimization branch.
3. Diff the summary tables per count: look at `peak_total_pss_kb`,
   `peak_renderer_pss_kb`, and `duration_ms`. The per-process split tells you whether
   savings landed in `:pir` (runner pool) or in the renderer.

## Troubleshooting

- `run-as failed` → build is not debuggable; install an internal **debug** build.
- Summary shows `renderer_process_count=0` → the device/WebView version may attribute
  renderers differently; inspect the raw timeline for unexpected process names and
  widen the match in `app_group_processes()`.
- Scan never completes / hits `MAX_SCAN_SECONDS` → confirm the service actually started
  (`adb logcat | grep PIR-`); if direct service start is unreliable on your device,
  trigger the scan from the PIR dev scan screen instead and re-run with a longer timeout.
