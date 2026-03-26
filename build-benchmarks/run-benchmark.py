#!/usr/bin/env python3

"""
Run build benchmarks using gradle-profiler and process the results.

This script can be invoked from anywhere and will use the build-benchmarks
directory as the working directory context.
"""

import csv
import os
import shutil
import statistics
import subprocess
import sys
import requests
import argparse
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import urlencode


class BenchmarkResult:
    """Represents benchmark results for a single scenario."""

    def __init__(self, scenario: str, sample: str, mean: float, median: float, std_dev: float, data: List[float]):
        self.scenario = scenario
        self.sample = sample
        self.mean = mean
        self.median = median
        self.std_dev = std_dev
        self.data = data
        self.min = min(data) if data else 0
        self.max = max(data) if data else 0


def get_repo_root() -> Path:
    """Get the repository root directory (parent of build-benchmarks)."""
    # Get the directory where this script is located
    script_dir = Path(__file__).resolve().parent
    # The script is in build-benchmarks, so parent is repo root
    return script_dir.parent


def get_build_benchmarks_dir() -> Path:
    """Get the build-benchmarks directory."""
    return Path(__file__).resolve().parent


def run_gradle_profiler(repo_root: Path, gradle_user_home: str) -> int:
    """Run gradle-profiler benchmark."""
    build_benchmarks_dir = get_build_benchmarks_dir()
    scenario_file = build_benchmarks_dir / "build-benchmark.scenarios"
    output_dir = build_benchmarks_dir / "results"
    
    # Clean and recreate output directory
    if output_dir.exists():
        print(f"Cleaning existing results directory: {output_dir}")
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Build the gradle-profiler command
    # Use relative paths from repo root
    scenario_file_rel = scenario_file.relative_to(repo_root)
    output_dir_rel = output_dir.relative_to(repo_root)
    
    cmd = [
        "gradle-profiler",
        "--benchmark",
        "--measure-config-time",
        "--scenario-file", str(scenario_file_rel),
        "--output-dir", str(output_dir_rel),
        "--csv-format", "long",
        "--gradle-user-home", gradle_user_home
    ]
    
    print(f"Running gradle-profiler from {repo_root}")
    print(f"Command: {' '.join(cmd)}")
    
    # Change to repo root and run the command
    result = subprocess.run(cmd, cwd=repo_root)
    return result.returncode


def parse_csv(csv_path: Path) -> List[BenchmarkResult]:
    """Parse gradle-profiler CSV output and extract metrics per scenario."""
    results = []

    if not csv_path.exists():
        print(f"Warning: CSV file not found: {csv_path}")
        return results

    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        measurements = {}

        for row in reader:
            scenario = row.get('Scenario')
            phase = row.get('Phase')
            sample = row.get('Sample')
            duration = row.get('Duration')
            # Process only 'total execution time' and 'task start' samples from MEASURE phase
            if (sample not in ['total execution time', 'task start'] or 
                not duration or 
                phase != 'MEASURE'):
                continue

            try:
                # Convert time to seconds (gradle-profiler outputs in ms)
                time_seconds = float(duration) / 1000.0

                key = (scenario, sample)
                if key not in measurements:
                    measurements[key] = []
                measurements[key].append(time_seconds)
            except (ValueError, TypeError) as e:
                print(f"Warning: Could not parse duration '{duration}' for scenario '{scenario}': {e}")
                continue

    # Create BenchmarkResult for each group of measurements
    for (scenario, sample), data in measurements.items():
        if data:
            results.append(BenchmarkResult(
                scenario=scenario,
                sample=sample,
                mean=statistics.mean(data),
                median=statistics.median(data),
                std_dev=statistics.stdev(data) if len(data) > 1 else 0,
                data=data
            ))

    return results


def format_time(seconds: float) -> str:
    """Format time in seconds to human-readable format."""
    if seconds >= 60:
        minutes = int(seconds // 60)
        secs = seconds % 60
        return f"{minutes}m {secs:.1f}s"
    else:
        return f"{seconds:.1f}s"


def generate_terminal_summary(results: List[BenchmarkResult]) -> str:
    """Generate terminal-friendly summary with better formatting."""
    
    if not results:
        return "No benchmark results found."
    
    output = []
    output.append("=" * 80)
    output.append("BUILD PERFORMANCE BENCHMARK RESULTS")
    output.append("=" * 80)
    output.append("")
    
    # Group results by scenario
    scenario_groups = {}
    for result in results:
        base_scenario = result.scenario
        if base_scenario not in scenario_groups:
            scenario_groups[base_scenario] = []
        scenario_groups[base_scenario].append(result)
    
    for base_scenario, scenario_results in scenario_groups.items():
        output.append(f"📊 {base_scenario}")
        output.append("-" * (len(base_scenario) + 4))
        
        # Separate total execution time and task start results
        total_exec_results = []
        task_start_results = []
        
        for result in scenario_results:
            # Use the task field directly
            if result.sample == "total execution time":
                total_exec_results.append(result)
            elif result.sample == "task start":
                task_start_results.append(result)
        
        # Display total execution time results
        for total_exec_result in total_exec_results:
            output.append(f"  📈 Total Execution Time:")
            output.append(f"    Mean:   {format_time(total_exec_result.mean):>8}")
            output.append(f"    Median: {format_time(total_exec_result.median):>8}")
            output.append(f"    StdDev: ±{format_time(total_exec_result.std_dev):>7}")
            output.append(f"    Range:  {format_time(total_exec_result.min):>8} - {format_time(total_exec_result.max)}")
            output.append("")
            
            # Display task start as sub-metric
            for task_start_result in task_start_results:
                output.append(f"    └─ Gradle Configuration Time:")
                output.append(f"      Mean:   {format_time(task_start_result.mean):>8}")
                output.append(f"      Median: {format_time(task_start_result.median):>8}")
                output.append(f"      StdDev: ±{format_time(task_start_result.std_dev):>7}")
                output.append(f"      Range:  {format_time(task_start_result.min):>8} - {format_time(task_start_result.max)}")
                output.append("")
        
        output.append("")
    
    return "\n".join(output)


def send_results_to_api(results: List[BenchmarkResult], github_action_run_id: Optional[str] = None, git_commit_sha: Optional[str] = None) -> None:
    """Send benchmark results to the API endpoint."""
    base_url = "https://improving.duckduckgo.com/t/m_build_time_android"
    
    for result in results:
        scenario = result.scenario
        sample = result.sample
        
        # Prepare query parameters
        params = {
            'scenario': scenario,
            'sample': sample,
            'mean': f"{result.mean:.3f}",
            'median': f"{result.median:.3f}",
            'std_dev': f"{result.std_dev:.3f}",
            'min': f"{result.min:.3f}",
            'max': f"{result.max:.3f}"
        }

        if github_action_run_id:
            params['github_action_run_id'] = github_action_run_id

        if git_commit_sha:
            params['git_commit_sha'] = git_commit_sha
        
        # Construct URL with query parameters
        url = f"{base_url}?{urlencode(params)}"
        
        try:
            print(f"Sending results for {scenario} - {sample}...")
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            print(f"✅ Successfully sent results using URL: {url}")
        except requests.exceptions.RequestException as e:
            print(f"❌ Failed to send results using URL: {url}: {e}")
        except Exception as e:
            print(f"❌ Unexpected error sending results using URL: {url}: {e}")


def process_results(
    csv_path: Path,
    github_action_run_id: Optional[str] = None,
    git_commit_sha: Optional[str] = None,
    report_pixel: bool = False
) -> int:
    """Process benchmark results from CSV file."""
    print(f"Parsing results from: {csv_path}")
    results = parse_csv(csv_path)
    print(generate_terminal_summary(results))
    
    # Send results to API only if both conditions are met
    if results and report_pixel and github_action_run_id and git_commit_sha:
        print("\n" + "=" * 80)
        print("SENDING RESULTS TO API")
        print("=" * 80)
        send_results_to_api(results, github_action_run_id, git_commit_sha)
    elif results and report_pixel and (not github_action_run_id or not git_commit_sha):
        print("\n⚠️  --report-pixel flag set but no --github-action-run-id and --git-commit-sha provided. Skipping API requests.")
    
    return 0 if results else 1


def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(
        description="Run build benchmarks and process results"
    )
    parser.add_argument(
        "--gradle-user-home",
        type=str,
        default=os.path.expanduser("~/.gradle"),
        help="Gradle user home directory (default: ~/.gradle)"
    )
    parser.add_argument(
        "--github-action-run-id",
        type=str,
        help="(optional) GitHub Action run ID for linking results with CI workflows"
    )
    parser.add_argument(
        "--git-commit-sha",
        type=str,
        help="(optional) Git commit SHA for linking results to specific code version"
    )
    parser.add_argument(
        "--report-pixel",
        action="store_true",
        help="Send results to the reporting API"
    )
    parser.add_argument(
        "--skip-profiler",
        action="store_true",
        help="Skip running gradle-profiler (only process existing results)"
    )
    parser.add_argument(
        "--skip-processing",
        action="store_true",
        help="Skip processing results (only run gradle-profiler)"
    )
    parser.add_argument(
        "--csv-file",
        type=Path,
        help="Path to CSV file for processing (only used with --skip-profiler). Defaults to build-benchmarks/results/benchmark.csv"
    )
    
    args = parser.parse_args()
    
    repo_root = get_repo_root()
    build_benchmarks_dir = get_build_benchmarks_dir()
    print(f"Repository root: {repo_root}")
    print(f"Build benchmarks directory: {build_benchmarks_dir}")
    
    # Run gradle-profiler
    if not args.skip_profiler:
        print("\n" + "=" * 80)
        print("RUNNING GRADLE PROFILER")
        print("=" * 80)
        profiler_exit_code = run_gradle_profiler(repo_root, args.gradle_user_home)
        if profiler_exit_code != 0:
            print(f"\n❌ gradle-profiler failed with exit code {profiler_exit_code}")
            sys.exit(profiler_exit_code)
        print("\n✅ gradle-profiler completed successfully")
    else:
        print("\n⏭️  Skipping gradle-profiler (--skip-profiler flag set)")
    
    # Process results
    if not args.skip_processing:
        print("\n" + "=" * 80)
        print("PROCESSING BENCHMARK RESULTS")
        print("=" * 80)
        
        # Determine CSV file path
        if args.csv_file:
            csv_path = args.csv_file
        else:
            csv_path = build_benchmarks_dir / "results" / "benchmark.csv"
        
        # If csv_path is relative, resolve it relative to repo root
        if not csv_path.is_absolute():
            csv_path = repo_root / csv_path
        
        processing_exit_code = process_results(
            csv_path,
            args.github_action_run_id,
            args.git_commit_sha,
            args.report_pixel
        )
        if processing_exit_code != 0:
            print(f"\n❌ Processing failed with exit code {processing_exit_code}")
            sys.exit(processing_exit_code)
        print("\n✅ Processing completed successfully")
    else:
        print("\n⏭️  Skipping processing (--skip-processing flag set)")
    
    print("\n" + "=" * 80)
    print("BENCHMARK RUN COMPLETED")
    print("=" * 80)


if __name__ == '__main__':
    main()
