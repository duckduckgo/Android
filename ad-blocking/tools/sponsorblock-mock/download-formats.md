# SponsorBlock prefix download formats

These are format experiments, not a finalized API. The client requests an update from its revision; the server may return either a partial update or a full replacement in the same response format. `insert`, `delete`, `revision`, `replace` and checksum have the same meaning in either case. A replacement contains the complete set in `insert` and an empty `delete`.

Measurements below use 3,506,928 sorted, unique eight-character hex prefixes. Sizes are decimal MB. JSON sizes include revision, replacement and checksum fields; binary measurements exclude protocol metadata unless noted. Gzip sizes were measured locally. Results for partial updates depend on their size and how changes are distributed across buckets; full-list sizes do not predict them.

## Flat formats

| Representation | Example `insert` | Full JSON or data | Gzipped | Tradeoff |
|---|---|---:|---:|---|
| JSON hex list | `["0a130329","0a13c480"]` | 38.58 MB | 12.40 MB | Explicit entries and simple debugging; creates many strings if materialized. |
| JSON integer list | `[169018153,169067648]` | 37.67 MB | 12.81 MB | Individual numeric values; unsigned 32-bit values need a sufficiently wide client type. |
| JSON concatenated hex | `"0a1303290a13c480"` | 28.06 MB | 11.99 MB | Fewer JSON delimiters; client must validate and split at eight-character boundaries. |
| JSON base64 of packed UInt32 values | `"ChMDKQoTxIA="` | 18.70 MB | 12.35 MB | Compact JSON before gzip; needs base64 decoding and a defined byte order. |
| Binary packed UInt32 values | Four bytes per prefix | 14.03 MB | 11.24 MB | Compact fixed-width entries; needs a binary envelope and decoder. |

## Bucketed formats

Each bucket key contains the first four hex characters (16 bits); each suffix contains the remaining four characters. These examples insert `0a130329` and `0a13c480` and delete `0a13f012`. Metadata is omitted from examples.

| Representation | Full response | Gzipped | Tradeoff |
|---|---:|---:|---|
| JSON lists of hex suffixes | 25.14 MB | 9.96 MB | Explicit entries and easy inspection; most JSON parsing and string allocation. |
| JSON concatenated hex suffixes | 14.68 MB | 8.26 MB | Readable and smaller; client must validate and split at four-character boundaries. |
| JSON base64 packed suffixes per bucket | 10.09 MB | 7.43 MB | Compact while retaining a JSON envelope; requires base64 decoding and two-byte boundary validation. |
| Sparse bucketed binary | 7.41 MB | 7.33 MB | Compact, same grouping for full and partial updates; requires a versioned binary specification and decoder. |

```json
{"insert":{"0a13":["0329","c480"]},"delete":{"0a13":["f012"]}}
```

```json
{"insert":{"0a13":"0329c480"},"delete":{"0a13":"f012"}}
```

```json
{"insert":{"0a13":"AynEgA=="},"delete":{"0a13":"8BI="}}
```

```text
insert: bucket 0a13 | count 2 | suffix bytes 03 29 c4 80
delete: bucket 0a13 | count 1 | suffix bytes f0 12
```

The binary experiment used a two-byte bucket key and a four-byte suffix count for each nonempty group. Its result is not a complete protocol: framing, byte order, metadata and validation still need definitions. In synthetic partial updates, grouping helped clustered changes but added overhead for scattered ones. Measure representative updates before selecting a format.

A separate dense binary experiment used 65,537 four-byte offsets and packed two-byte suffixes: 7.28 MB uncompressed, 7.18 MB gzipped. Wrapping those bytes in a base64 JSON field measured 9.70 MB and 7.29 MB gzipped. Neither experiment defines a single efficient representation for both full and partial updates.

For either JSON or binary, validate bucket keys, suffix lengths, ordering and duplicate handling. Apply the update and revision atomically, and define checksum validation and recovery for invalid or mismatched data. Streaming parsing can limit peak memory. See the [Android profiling results](android-profiling.md) for measured import times and memory observations.
