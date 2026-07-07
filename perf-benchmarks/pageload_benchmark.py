#!/usr/bin/env python3
"""Post-process a page-load Perfetto trace: extract ddg.pageLoad slices, compute stats, report to Pixels."""
import argparse
import json
import statistics
import subprocess
import sys
from pathlib import Path
from typing import Optional
from urllib.parse import urlencode

import requests

SECTION = "ddg.pageLoad"
PIXEL_BASE = "https://improving.duckduckgo.com/t/m_page_load_time_android"


def query_durations_ms(trace_path: Path) -> list:
    """Run trace_processor and return ddg.pageLoad slice durations in ms (positive only), in time order."""
    # ORDER BY ts so the returned order is chronological — compute_stats() drops the first sample as
    # the warmup navigation, which is only correct if the rows come back in the order they occurred.
    sql = f"SELECT dur FROM slice WHERE name = '{SECTION}' AND dur > 0 ORDER BY ts;"
    out = subprocess.run(
        ["trace_processor_shell", "-q", "/dev/stdin", str(trace_path)],
        input=sql,
        capture_output=True,
        text=True,
        check=True,
    ).stdout
    durations = []
    for line in out.splitlines():
        line = line.strip().strip('"')
        if line.isdigit():
            durations.append(int(line) / 1e6)  # ns -> ms
    return durations


def compute_stats(durations_ms: list, warmup: int = 1) -> dict:
    data = [d for d in durations_ms[warmup:] if d > 0]
    if not data:
        return {"count": 0, "median": 0.0, "mean": 0.0, "std_dev": 0.0, "min": 0.0, "max": 0.0, "p90": 0.0}
    ordered = sorted(data)
    # nearest-rank p90
    p90 = ordered[min(len(ordered) - 1, max(0, round(0.9 * len(ordered)) - 1))]
    return {
        "count": len(data),
        "median": statistics.median(data),
        "mean": statistics.mean(data),
        "std_dev": statistics.stdev(data) if len(data) > 1 else 0.0,
        "min": min(data),
        "max": max(data),
        "p90": float(p90),
    }


def pixel_url(stats: dict, run_id: Optional[str], sha: Optional[str]) -> str:
    params = {k: f"{stats[k]:.3f}" for k in ("median", "mean", "std_dev", "min", "max", "p90")}
    params["count"] = str(stats["count"])
    if run_id:
        params["github_action_run_id"] = run_id
    if sha:
        params["git_commit_sha"] = sha
    return f"{PIXEL_BASE}?{urlencode(params)}"


def diagnostics(benchmark_data: Optional[Path]) -> dict:
    if not benchmark_data or not benchmark_data.exists():
        return {}
    ctx = json.loads(benchmark_data.read_text()).get("context", {})
    return {
        k: ctx.get(k)
        for k in ("cpuLocked", "cpuMaxFreqHz", "sustainedPerformanceModeEnabled", "thermalThrottleSleepSeconds")
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--trace", type=Path, required=True)
    ap.add_argument("--benchmark-data", type=Path)
    ap.add_argument("--report-pixel", action="store_true")
    ap.add_argument("--github-action-run-id")
    ap.add_argument("--git-commit-sha")
    ap.add_argument("--out", type=Path, default=Path("pageload-results.json"))
    args = ap.parse_args()

    durations = query_durations_ms(args.trace)
    stats = compute_stats(durations)
    result = {"stats": stats, "raw_ms": durations, "diagnostics": diagnostics(args.benchmark_data)}
    args.out.write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))

    if stats["count"] == 0:
        print("ERROR: no positive ddg.pageLoad slices found", file=sys.stderr)
        return 1

    if args.report_pixel and args.github_action_run_id and args.git_commit_sha:
        url = pixel_url(stats, args.github_action_run_id, args.git_commit_sha)
        try:
            requests.get(url, timeout=10).raise_for_status()
            print(f"Reported: {url}")
        except requests.RequestException as e:
            print(f"Failed to report pixel: {e}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
