# SponsorBlock dataset: Android profiling

These experiments compare the current bucketed JSON response and integer-per-prefix Room storage with different import strategies. They are exploratory measurements, not a final API or a production benchmark.

## Setup and limitations

- Samsung SM-A566B, Android 17; foreground app in each comparison.
- An optimized, minified internal build was made debuggable and pointed to the local mock. The mock was reached over `adb reverse`, not a production network.
- Each strategy started with the same 3,506,928 unique prefixes at revision 1. Its full replacement at revision 2 added the same single test prefix; the final marker database held 3,506,929 prefixes.
- All table entries were collected in the same profiling round on 16 November 2026, with one first import and one full replacement per strategy. Worker time covers revision checks, network, parsing, database writes and commit; it is not an isolated write time. Sampling memory with `dumpsys meminfo` can trigger garbage collection and affect timings.
- Decimal MB is used for file and response sizes; memory is described as reported by Android. Samples are not guaranteed peaks. Results can differ on other devices and under background scheduling, network or thermal constraints.

## Download

The same bucketed JSON is used for full replacements and partial updates. The prefix is the first eight lowercase hex digits of SHA-256(video ID); a bucket key is the first four digits and its value is a string of concatenated four-digit suffixes.

For the full dataset, one measured response was **14,683,146 bytes** uncompressed and **8,259,577 bytes** gzipped. A replacement sends the full list in `insert`; a diff sends changed prefixes in `insert` and `delete`. The client currently ignores the included checksum. A full response from a local mock is not evidence of production download latency. See [download formats](download-formats.md) for the other representations.

## Foreground results

| Measurement | Materialized JSON and one insert list | Stream to temporary file, then database batches | Direct stream with 0/1 marker |
|---|---:|---:|---:|
| First full import, worker start to success | 30.63 s | 22.81 s | 23.23 s |
| Full replacement, worker start to success | 27.12 s | 21.88 s | 24.55 s |
| Highest sampled Java-heap PSS, first import | 425.0 MiB | 109.8 MiB | 168.3 MiB |
| Highest sampled Java-heap PSS, replacement | 250.7 MiB | 99.1 MiB | 170.0 MiB |
| Highest observed live Java heap after GC, first import | 249 MB | 76 MB | 79 MB |
| Highest observed live Java heap after GC, replacement | 249 MB | 86 MB | 70 MB |
| Database after replacement | 35,155,968 bytes | 35,155,968 bytes | 38,690,816 bytes |
| WAL at end of replacement | 35,353,752 bytes | 35,353,752 bytes | 38,748,632 bytes |
| Temporary response file | None | ~14.68 MB | None |
| SQLite membership lookup median | 10.2 µs | 9.8 µs | 9.8 µs |
| SQLite membership lookup p95 / p99 | 13.6 / 18.2 µs | 12.7 / 18.8 µs | 15.3 / 18.9 µs |

The temporary-file size is calculated from the full uncompressed response; it was not separately sampled. The WAL figures are file sizes at completion, not proven peak disk usage. The highest sampled PSS values were taken over 12–14 periodic samples per run. The post-GC figures are the highest observed values in GC log entries during each run, including startup and download; they are not peak live heap. PSS and live heap are different metrics and neither isolates the dataset's memory cost. Sampling induced some explicit garbage collections.

Each worker ran while the profiling app was in the foreground at all sampled checks. The prior materialized and temporary-file strategies were reconstructed as temporary profiling variants; the workspace implementation was restored after each build. All strategies received the same dataset and replacement, although device load and execution order were not controlled. The reported timings should not be treated as statistically significant differences.

The lookup benchmark ran 10,000 indexed SQLite membership queries on the device, approximately half for present prefixes. It ran in a separate helper process and excludes the app's revision check, hash calculation, Room dispatch and player overhead. These values describe warm-query samples, not full end-to-end lookup latency.

## Current direct-streaming implementation

The client uses Retrofit `@Streaming` and Moshi's streaming reader. It reads each bucket and applies inserts and deletes in batches of up to 900 prefixes, without retaining the whole response or writing a temporary JSON file. One bucket's suffix string and the current batch are still materialized.

The response may put `replace` and `revision` after the buckets. The table therefore includes a Boolean `marked` field:

1. In one Room transaction, insert incoming prefixes with `marked = 1`; apply deletions only to unmarked rows. This makes an insert win if a prefix appears in both fields, regardless of field order.
2. Validate the required fields and revision. For a replacement, delete rows still marked `0`.
3. Reset marked rows to `0`, save the new revision and commit.

A parse error, invalid revision, failed batch or cancellation rolls back the transaction, leaving the prior revision and dataset available for a retry. Tests cover failures after a batch has been applied and insert/delete overlap in either field order. The marker adds roughly 3.5 MB to the measured database for this dataset; it also requires updating inserted rows again to reset the marker. The `UPDATE ... WHERE marked = 1` query currently has no marker index and scans the prefix table, including for small diffs. The scan cost for a small diff was not isolated in this profiling round.

The marker schema is experimental. Existing test databases with the previous schema must be cleared; no migration was added. The current client does not validate the response checksum.

## Lookup and concurrent reads

A single read-only SQL query checks that a revision exists and the full 32-bit prefix is present. If there is no stored revision, the client does **not** request segments from the network. The revision check and membership check therefore use one consistent query rather than a separate Room transaction.

The current import holds its write transaction open while it receives and processes the response, resets markers and saves the revision. Where SQLite WAL and Room permit a concurrent read, that read sees the previously committed dataset; new rows are not visible until commit. The read-only query removes an extra transaction from lookup, but it does **not** prove that lookups never wait: Room connection scheduling, locks and resource contention may still delay them. Concurrent lookup latency during a live sync has not been measured.

Moving marker reset after the data-and-revision commit is a possible alternative. Readers could then use the committed new dataset during cleanup, but the next update would need to wait for cleanup, including recovering from interruption or process death. This approach has not been implemented or profiled.

## Binary file experiment

### Partial updates at different sizes

On 17 November, the same debug builds and device were reset and synced to the 3,506,928-prefix dataset before each implementation's run. After a 30-second foreground settling period and initial import, the mock generated deterministic prefixes distributed across the hash space. For each size it added the specified number, then removed those same prefixes before moving to the next size. Each measurement is one run. After the binary run, the revision 7 file was decoded and compared with the original dataset; all 3,506,928 entries matched.

| Diff | SQLite update path | Binary update path | SQLite full worker | Binary full worker |
|---|---:|---:|---:|---:|
| Insert 100 | 0.207 s | 0.556 s | 1.211 s | 1.681 s |
| Delete 100 | 0.167 s | 0.507 s | 1.262 s | 1.522 s |
| Insert 1,000 | 0.301 s | 0.512 s | 1.338 s | 1.578 s |
| Delete 1,000 | 0.193 s | 0.509 s | 1.275 s | 1.524 s |
| Insert 10,000 | 0.675 s | 0.524 s | 1.702 s | 1.678 s |
| Delete 10,000 | 0.432 s | 0.516 s | 1.506 s | 1.626 s |

The update path runs from receiving response headers through transaction commit or file publication, and includes response reads and parsing. Full worker time additionally includes the revision request, server-side on-demand response generation and waiting for response headers. The mock calculates the checksum over the full dataset for each diff, so its server work is part of these worker times and is not representative of a production server that serves pre-generated files.

SQLite's marker reset took 0.13–0.20 s per update in this run, included in its update-path figures. The binary merge rewrote the dataset and took 0.50–0.55 s per update, also included above. These are single instrumented runs; in particular, the 10,000-insert result is not enough evidence to establish a crossover point. Diff distributions, device speed, caching and background activity can affect the result. Deletions here remove newly added prefixes; they do not model the age or actual rate of YouTube video removals.

### Full update path and storage stages

On 17 November, both debug builds were freshly installed with cleared app data, settled for 30 seconds on the foreground onboarding screen, and synced the same 3,506,928-prefix full dataset through the local mock. A subsequent one-prefix insertion and deletion used the same initial dataset. Each result is one run, with temporary timing probes and `dumpsys` sampling; the latter can trigger GC and affect timings.

| Measurement | SQLite | Binary file |
|---|---:|---:|
| Full import: worker start to success | 16.92 s | 4.40 s |
| Full import: update path after response headers | 14.47 s | 1.46 s |
| Within that path: time in response reads | 0.42 s | 0.14 s |
| Full import: insertion batches | 11.06 s | — |
| Full import: marker reset | 0.41 s | — |
| Full import: receive, parse and spool temporary files | — | 0.71 s |
| Full import: merge temporary files, sync and publish | — | 0.75 s |
| One-prefix insertion: worker start to success | 1.29 s | 1.54 s |
| One-prefix insertion: update path after response headers | 0.178 s | 0.538 s |
| One-prefix deletion: worker start to success | 1.19 s | 1.53 s |
| One-prefix deletion: update path after response headers | 0.161 s | 0.495 s |

The update-path timings are wall time: SQLite runs a Room transaction while reading and parsing the response, inserting or deleting rows, resetting markers, saving the revision and committing; the binary path reads and parses the response while writing temporary files, then reads and merges them into the replacement and publishes it. They exclude the earlier revision request and time before response headers arrive. Response-read timings are **included within** the update paths and can include waiting, buffering and decompression; they are not independent download times. The insert and reset timings are also subsets, not additional costs. The binary spool and merge stages overlap with response reads as indicated; the merge alone does not represent the cost of updating the file.

The one-prefix SQLite insertion and deletion spent 0.152 s and 0.148 s respectively resetting markers; the full transaction times also include parsing, response reads, the row operation and commit. The binary insertion spent 0.003 s spooling and 0.532 s merging; the deletion spent 0.003 s spooling and 0.488 s merging. Neither implementation has been separately measured for disk-only time, and the server's on-demand response generation is included in the worker times.

Lookup opens the file and reads two offsets for the requested bucket, then binary-searches aligned two-byte suffixes using random reads. It retains only small values and file machinery in app memory, not the dataset or offset table. The OS may cache file pages in memory. The update path is different: it allocates a roughly 256 KiB offset array and buffered streams while producing the replacement.

### Matched, settled first-import comparison

On 17 November, both implementations were rerun on the same Samsung device with the same internal debug build configuration and the same 3,506,928-prefix revision. The debug app's data was cleared before each run. A dedicated mock reported revision 0 while the app opened on the onboarding screen and settled for 30 seconds; it then exposed revision 1, and a single broadcast started the import. The screen was in the foreground before and after each import. Temporary timing probes were compiled into both APKs and removed from the working files after building. The server generated responses on demand over `adb reverse`.

| Measurement | SQLite, streamed and batched | Bucketed binary file |
|---|---:|---:|
| Full import, worker start to success | 17.00 s | 4.39 s |
| Instrumented storage phase | 11.09 s in insert batches | 0.74 s for final merge, file sync and rename |
| Idle Java heap PSS after sampling-induced GC | 44.88 MiB | 44.90 MiB |
| Highest sampled Java heap PSS during import | 69.84 MiB | 51.49 MiB |
| Idle total app PSS after sampling-induced GC | 487.63 MiB | 486.89 MiB |
| Highest sampled total app PSS during import | 514.35 MiB | 492.52 MiB |
| Full response, gzip / uncompressed | 8,259,577 / 14,683,146 bytes | 7,329,078 / 7,407,089 bytes |
| Dataset file after import | 38,686,720-byte database; 38,946,392-byte WAL | 7,276,008-byte file |
| Warm lookup, median / p95 / p99 | 10.0 / 15.5 / 18.5 µs | 48.2 / 61.7 / 88.2 µs |
| One-prefix insert, worker start to success | 1.18 s | 1.63 s |
| One-prefix deletion, worker start to success | 1.19 s | 1.57 s |

The storage-phase measurements have different boundaries: SQLite's figure sums batch insertion calls and excludes transaction commit; the file figure starts after the binary response has been parsed into temporary files and includes reading them to build and publish the final file. Neither is a complete measure of disk I/O. Worker time includes the revision request, server response generation, network transfer, parsing, storage and publication. These are single runs, not statistically significant timing comparisons.

PSS is whole-app memory, not memory attributed exclusively to SponsorBlock. The idle values are the third of three consecutive `dumpsys meminfo` samples, after the first induced a substantial GC; import values are the highest observed across 20 SQLite samples and 6 binary samples. Sampling can affect execution and can miss peaks, especially for the shorter binary run. The WAL size is at completion and can later be checkpointed; it is not additional unique dataset content. Binary temporary-file peak disk usage was not measured. Lookup timings use separate device helper processes and exclude hashing and app dispatch.

### Earlier exploratory run

On 17 November, the same device and full 3,506,928-prefix dataset were used for an exploratory binary-file test. This used an internal **debug** build, not the optimized build in the earlier table; timings cannot be directly compared with those runs. The mock used `adb reverse` and created responses on demand.

The binary response was 7,407,089 bytes uncompressed and 7,329,078 bytes gzipped. The installed file was 7,276,008 bytes, including revision and 65,537 offsets. One full replacement completed in 4.02 seconds from worker start to success; a single memory sample was 81,892 KiB Java PSS and 472,826 KiB total PSS for the whole app, not an import-specific or peak measurement. The first import overlapped a second worker, so its timing is not a clean measurement.

For a database baseline, a separate Android helper ran 10,000 individual insert and delete statements in one transaction that was rolled back. Median / p95 / p99 were **36.1 / 429.3 / 935.7 µs** for insert and **8.1 / 25.8 / 48.4 µs** for delete. These exclude commit, network and Room overhead. The binary store instead rewrites its file for a change: one end-to-end worker run took **1.63 s** for a one-prefix insert and **1.52 s** for a one-prefix deletion, including network, merge, file sync and replacement. These are different scopes, not directly comparable per-operation timings.

Warm lookups in separate device helper processes, approximately half present, were **10.4 / 16.6 / 19.6 µs** median / p95 / p99 for SQLite and **49.5 / 63.4 / 90.7 µs** for the binary file. The file helper opens the file for each lookup, matching the current implementation; neither helper includes hashing, dispatch or player overhead. These are single exploratory runs.

The experimental implementation serializes file lookups and updates with a lock, so lookups can wait for the full network response and merge. It also writes temporary insert/delete and suffix files while building a replacement; peak disk usage was not measured. The old file stays in place until the new file is complete, with the revision in the same file. The binary protocol does not yet carry or validate a checksum. Further work is needed before treating this as production-ready, including interruption, concurrent-read and device-performance testing.
