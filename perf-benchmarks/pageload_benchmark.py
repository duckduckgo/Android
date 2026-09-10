#!/usr/bin/env python3
"""Collect page-load Macrobenchmark traces, validate them, and report one pixel per scenario."""

import argparse
import json
import os
import shutil
import statistics
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Optional
from urllib.parse import urlencode

import requests

SECTION = "ddg.pageLoad"
DEFAULT_EXPECTED_SAMPLES = 10
DEFAULT_WARMUP_SAMPLES = 1
DEFAULT_TRAILING_SAMPLES = 1

PIXEL_BASE_ENV_VAR = "PAGELOAD_PIXEL_BASE_URL"
PIXEL_BASE = os.environ.get(PIXEL_BASE_ENV_VAR, "https://improving.duckduckgo.com/t/m_page_load_time_android")

BENCHMARK_CLASS = "com.duckduckgo.macrobenchmark.PageLoadBenchmark"
SCENARIO_BY_BENCHMARK_NAME = {
    "noTrackers": "no-trackers",
    "manyTrackersBlocked": "many-trackers-blocked",
    "firstPartyTrackers": "first-party-trackers",
    "cpm": "cpm",
    "allScenarios": "all",
}
EXPECTED_SCENARIOS = frozenset(SCENARIO_BY_BENCHMARK_NAME.values())


@dataclass
class Stats:
    count: int = 0
    median: float = 0.0
    mean: float = 0.0
    std_dev: float = 0.0
    min: float = 0.0
    max: float = 0.0
    p90: float = 0.0


@dataclass
class DeviceMetadata:
    device: Optional[str] = None
    device_model: Optional[str] = None
    api_level: Optional[int] = None


@dataclass
class Artifact:
    scenario: str
    benchmark_name: str
    benchmark_data: Optional[Path]
    trace: Path
    metadata: DeviceMetadata


@dataclass
class ScenarioResult:
    stats: Stats
    raw_ms: list[float]
    metadata: DeviceMetadata
    trace: Path
    benchmark_data: Optional[Path]


def query_durations_ms(trace_path: Path) -> list[float]:
    """Return positive ddg.pageLoad slice durations in chronological order."""
    if shutil.which("trace_processor_shell") is None:
        raise FileNotFoundError(
            "trace_processor_shell not found on PATH; install the Perfetto trace_processor_shell "
            "binary before running this script"
        )
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
            durations.append(int(line) / 1e6)
    return durations


def compute_stats(
    durations_ms: list[float],
    warmup: int = DEFAULT_WARMUP_SAMPLES,
    trailing: int = DEFAULT_TRAILING_SAMPLES,
) -> Stats:
    end = len(durations_ms) - trailing if trailing else None
    data = [duration for duration in durations_ms[warmup:end] if duration > 0]
    if not data:
        return Stats()
    ordered = sorted(data)
    p90 = ordered[min(len(ordered) - 1, max(0, round(0.9 * len(ordered)) - 1))]
    return Stats(
        count=len(data),
        median=statistics.median(data),
        mean=statistics.mean(data),
        std_dev=statistics.stdev(data) if len(data) > 1 else 0.0,
        min=min(data),
        max=max(data),
        p90=float(p90),
    )


def _device_metadata(context: dict, device: str) -> DeviceMetadata:
    build = context.get("build", {})
    return DeviceMetadata(
        device=device,
        device_model=build.get("model"),
        api_level=build.get("version", {}).get("sdk"),
    )


def diagnostics(benchmark_data: Optional[Path], device: Optional[str] = None) -> DeviceMetadata:
    """Read device metadata from a benchmarkData.json, when available."""
    if not benchmark_data or not benchmark_data.exists():
        return DeviceMetadata()
    document = json.loads(benchmark_data.read_text())
    return _device_metadata(document.get("context", {}), device or benchmark_data.parent.name)


def _find_trace(results_dir: Path, benchmark_data: Path, filename: str, trace_files: dict[str, list[Path]]) -> Path:
    beside_json = benchmark_data.parent / filename
    if beside_json.is_file():
        return beside_json
    matches = trace_files.get(filename, [])
    if len(matches) != 1:
        raise ValueError(f"expected exactly one trace named {filename!r}, found {len(matches)} under {results_dir}")
    return matches[0]


def discover_artifacts(results_dir: Path, device_override: Optional[str] = None) -> list[Artifact]:
    """Correlate PageLoadBenchmark entries with their profiler trace by filename."""
    if not results_dir.is_dir():
        raise ValueError(f"results directory does not exist: {results_dir}")
    benchmark_files = sorted(results_dir.rglob("*benchmarkData.json"))
    if not benchmark_files:
        raise ValueError(f"no *benchmarkData.json files found under {results_dir}")

    trace_files: dict[str, list[Path]] = {}
    for trace in results_dir.rglob("*.perfetto-trace"):
        trace_files.setdefault(trace.name, []).append(trace)

    artifacts = []
    seen = set()
    for benchmark_data in benchmark_files:
        document = json.loads(benchmark_data.read_text())
        context = document.get("context", {})
        device = device_override or benchmark_data.parent.name
        metadata = _device_metadata(context, device)
        for benchmark in document.get("benchmarks", []):
            if benchmark.get("className") != BENCHMARK_CLASS:
                continue
            benchmark_name = benchmark.get("name")
            scenario = SCENARIO_BY_BENCHMARK_NAME.get(benchmark_name)
            if scenario is None:
                raise ValueError(f"unexpected {BENCHMARK_CLASS} benchmark name: {benchmark_name!r}")
            if scenario in seen:
                raise ValueError(f"duplicate page-load scenario: {scenario}")
            outputs = [
                output
                for output in benchmark.get("profilerOutputs", [])
                if output.get("type") == "PerfettoTrace" and output.get("filename")
            ]
            if len(outputs) != 1:
                raise ValueError(f"scenario {scenario} must have exactly one PerfettoTrace output, found {len(outputs)}")
            trace = _find_trace(results_dir, benchmark_data, outputs[0]["filename"], trace_files)
            artifacts.append(
                Artifact(
                    scenario=scenario,
                    benchmark_name=benchmark_name,
                    benchmark_data=benchmark_data,
                    trace=trace,
                    metadata=metadata,
                )
            )
            seen.add(scenario)

    missing = EXPECTED_SCENARIOS - seen
    unexpected = seen - EXPECTED_SCENARIOS
    if missing or unexpected:
        details = []
        if missing:
            details.append(f"missing scenarios: {sorted(missing)}")
        if unexpected:
            details.append(f"unexpected scenarios: {sorted(unexpected)}")
        raise ValueError("; ".join(details))
    devices = {artifact.metadata.device for artifact in artifacts}
    if len(devices) != 1:
        raise ValueError(f"expected one device definition, found {sorted(devices)}")
    return sorted(artifacts, key=lambda artifact: artifact.scenario)


def single_trace_artifact(
    trace: Path,
    scenario: str,
    benchmark_data: Optional[Path],
    device: Optional[str],
) -> Artifact:
    canonical = SCENARIO_BY_BENCHMARK_NAME.get(scenario, scenario)
    if canonical not in EXPECTED_SCENARIOS:
        raise ValueError(f"unknown scenario {scenario!r}; expected one of {sorted(EXPECTED_SCENARIOS)}")
    benchmark_name = next(
        (name for name, canonical_name in SCENARIO_BY_BENCHMARK_NAME.items() if canonical_name == canonical),
        scenario,
    )
    metadata = diagnostics(benchmark_data, device)
    if metadata.device is None:
        metadata.device = device or "local"
    return Artifact(
        scenario=canonical,
        benchmark_name=benchmark_name,
        benchmark_data=benchmark_data,
        trace=trace,
        metadata=metadata,
    )


def collect_results(artifacts: list[Artifact], expected_samples: int = DEFAULT_EXPECTED_SAMPLES) -> dict:
    """Process every trace and reject the whole collection if any scenario is partial."""
    if expected_samples <= 0:
        raise ValueError("expected samples must be positive")
    scenarios: dict[str, ScenarioResult] = {}
    for artifact in artifacts:
        scenario = artifact.scenario
        if scenario in scenarios:
            raise ValueError(f"duplicate page-load scenario: {scenario}")
        durations = query_durations_ms(artifact.trace)
        expected_total = expected_samples + DEFAULT_WARMUP_SAMPLES + DEFAULT_TRAILING_SAMPLES
        if len(durations) != expected_total:
            raise ValueError(
                f"scenario {scenario} has {len(durations)} positive slices; expected {expected_total} "
                f"({DEFAULT_WARMUP_SAMPLES} warmup + {expected_samples} retained + {DEFAULT_TRAILING_SAMPLES} trailing)"
            )
        stats = compute_stats(durations)
        if stats.count != expected_samples:
            raise ValueError(f"scenario {scenario} retained {stats.count} samples; expected {expected_samples}")
        scenarios[scenario] = ScenarioResult(
            stats=stats,
            raw_ms=durations,
            metadata=artifact.metadata,
            trace=artifact.trace,
            benchmark_data=artifact.benchmark_data,
        )
    return {"scenarios": dict(sorted(scenarios.items()))}


def result_to_json(result: dict) -> dict:
    """Convert a collect_results() payload into plain JSON-serialisable data."""
    return {
        "scenarios": {
            scenario: {
                "stats": asdict(entry.stats),
                "raw_ms": entry.raw_ms,
                "metadata": asdict(entry.metadata),
                "trace": str(entry.trace),
                "benchmark_data": str(entry.benchmark_data) if entry.benchmark_data else None,
            }
            for scenario, entry in result["scenarios"].items()
        }
    }


def pixel_url(
    stats: Stats,
    scenario: str,
    metadata: DeviceMetadata,
    run_id: str,
    sha: str,
    endpoint: str = PIXEL_BASE,
) -> str:
    missing_metadata = [
        name for name in ("device", "device_model", "api_level") if getattr(metadata, name) in (None, "")
    ]
    if missing_metadata:
        raise ValueError(f"scenario {scenario} is missing pixel device metadata: {missing_metadata}")
    params = {
        "scenario": scenario,
        "device": metadata.device,
        "device_model": metadata.device_model,
        "api_level": metadata.api_level,
        "unit": "ms",
        "median": f"{stats.median:.3f}",
        "mean": f"{stats.mean:.3f}",
        "std_dev": f"{stats.std_dev:.3f}",
        "min": f"{stats.min:.3f}",
        "max": f"{stats.max:.3f}",
        "p90": f"{stats.p90:.3f}",
        "count": stats.count,
        "github_action_run_id": run_id,
        "git_commit_sha": sha,
    }
    return f"{endpoint}?{urlencode(params)}"


def pixel_urls(result: dict, run_id: str, sha: str, endpoint: str = PIXEL_BASE) -> list[str]:
    scenarios = set(result["scenarios"])
    if scenarios != EXPECTED_SCENARIOS:
        raise ValueError(
            f"refusing partial pixel report; expected {sorted(EXPECTED_SCENARIOS)}, found {sorted(scenarios)}"
        )
    return [
        pixel_url(entry.stats, scenario, entry.metadata, run_id, sha, endpoint)
        for scenario, entry in result["scenarios"].items()
    ]


def report_pixels(urls: list[str]) -> None:
    """Send only a fully prevalidated and prebuilt set of pixels."""
    for url in urls:
        response = requests.get(url, timeout=10)
        response.raise_for_status()
        print(f"Reported: {url}")


def main(argv: Optional[list[str]] = None) -> int:
    ap = argparse.ArgumentParser()
    source = ap.add_mutually_exclusive_group(required=True)
    source.add_argument("--results-dir", type=Path)
    source.add_argument("--trace", type=Path)
    ap.add_argument("--benchmark-data", type=Path)
    ap.add_argument("--scenario", help="Benchmark method or canonical scenario name; required with --trace")
    ap.add_argument("--device", help="Override the device definition (otherwise artifact directory name, or local)")
    ap.add_argument("--expected-samples", type=int, default=DEFAULT_EXPECTED_SAMPLES)
    ap.add_argument("--report-pixel", action="store_true")
    ap.add_argument("--github-action-run-id")
    ap.add_argument("--git-commit-sha")
    ap.add_argument(
        "--endpoint",
        default=PIXEL_BASE,
        help=f"Pixel base URL (default: {PIXEL_BASE_ENV_VAR} env var, or production if unset)",
    )
    ap.add_argument("--out", type=Path, default=Path("pageload-results.json"))
    args = ap.parse_args(argv)

    if args.report_pixel and (not args.github_action_run_id or not args.git_commit_sha):
        print("ERROR: --report-pixel requires --github-action-run-id and --git-commit-sha", file=sys.stderr)
        return 1
    if args.trace and not args.scenario:
        print("ERROR: --trace requires --scenario so the result cannot be misattributed", file=sys.stderr)
        return 1
    if args.trace and args.report_pixel:
        print("ERROR: --report-pixel requires --results-dir so all five scenarios are validated atomically", file=sys.stderr)
        return 1

    try:
        artifacts = (
            discover_artifacts(args.results_dir, args.device)
            if args.results_dir
            else [single_trace_artifact(args.trace, args.scenario, args.benchmark_data, args.device)]
        )
        result = collect_results(artifacts, args.expected_samples)
        urls = pixel_urls(result, args.github_action_run_id, args.git_commit_sha, args.endpoint) if args.report_pixel else []
    except (OSError, ValueError, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    args.out.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(result_to_json(result), indent=2)
    args.out.write_text(payload + "\n")
    print(payload)

    if urls:
        try:
            report_pixels(urls)
        except requests.RequestException as error:
            print(f"Failed to report pixel: {error}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
