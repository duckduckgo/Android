# Page-load benchmark pipeline

`pageload_benchmark.py` turns Macrobenchmark output into per-scenario stats and,
optionally, reports them as a `m_page_load_time_android` pixel.

## Prerequisites

`trace_processor_shell` — not a pip package, a standalone Perfetto binary that
must be on `PATH` separately. The script fails with a clear error if it's
missing, but does not install it. To install:

```bash
curl -LO https://get.perfetto.dev/trace_processor
chmod +x ./trace_processor
sudo mv ./trace_processor /usr/local/bin/trace_processor_shell
```

## What it does

1. **Discover** — given an FTL/Flank results directory, find every
   `PageLoadBenchmark` entry across all `*benchmarkData.json` files and match
   each one to its `.perfetto-trace` by filename. Requires exactly the six
   scenarios in `SCENARIO_BY_BENCHMARK_NAME` (`no-trackers`,
   `many-trackers-blocked`, `first-party-trackers`, `cpm`,
   `all-protections-on`, `all-protections-off`) — missing, duplicate, or
   unexpected scenarios abort the whole run. The two `all-protections-*`
   scenarios run the same fixture with DuckDuckGo protections enabled and
   disabled respectively, so they're directly comparable to each other.
2. **Extract** — query each trace for `ddg.pageLoad` slice durations via
   `trace_processor_shell` (must be on `PATH`; see below).
3. **Compute stats** — drop the leading warmup sample and the trailing
   sentinel sample by position, then report `count`, `median`, `mean`,
   `std_dev`, `min`, `max`, `p90` over the remaining samples.
4. **Report** — with `--report-pixel`, build one pixel URL per scenario and
   send it. Refuses to send anything unless all six scenarios validated
   successfully (no partial reporting).

## Usage

```bash
# Process a full FTL/Flank results directory, print + write combined stats
python3 pageload_benchmark.py --results-dir /path/to/results

# Same, and report pixels
python3 pageload_benchmark.py --results-dir /path/to/results \
  --report-pixel --github-action-run-id 123 --git-commit-sha abcdef

# Process a single trace (e.g. for local debugging one scenario)
python3 pageload_benchmark.py --trace some.perfetto-trace --scenario cpm
```

`--trace` mode requires `--scenario` and cannot be combined with
`--report-pixel` (only `--results-dir` validates all six scenarios
atomically, so it's the only mode allowed to send pixels).
