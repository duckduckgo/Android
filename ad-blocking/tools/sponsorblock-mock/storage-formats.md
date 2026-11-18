# SponsorBlock prefix storage formats

Download and storage formats can be chosen independently. These measurements use the same 3,506,928-prefix dataset as [the download experiments](download-formats.md). SQLite sizes are local measurements in decimal MB, not Android benchmarks; they exclude temporary write-ahead-log and update space. The [Android profiling results](android-profiling.md) cover the experimental Room marker schema and its measured storage and import behavior.

| Storage | Dataset size | Lookup | Partial update |
|---|---:|---|---|
| One full prefix per row, integer primary key | 35.10 MB | Indexed lookup on the full integer. | Insert and delete individual rows in a transaction. |
| One bucket and suffix pair per row, composite primary key, `WITHOUT ROWID` | 44.16 MB | Indexed lookup on both integers. | Insert and delete individual pairs in a transaction. |
| Same composite key in an ordinary SQLite rowid table | 110.95 MB | Indexed lookup on both integers. | Individual rows; maintains both the table and primary-key index. |
| One bucket row with concatenated hex suffix text | 15.24 MB | Lookup bucket, then search fixed-width suffixes in application code. | Read, merge and rewrite each affected bucket. |
| One bucket row with packed two-byte suffix blob | 7.82 MB | Lookup bucket, then binary-search aligned two-byte suffixes. | Read, merge and rewrite each affected bucket. |
| One bucket row with JSON suffix list | Not measured | Lookup bucket and parse list. | Read, parse, merge and rewrite each affected bucket. |
| Indexed packed file, outside Room | 7.28 MB calculated | Offset-table lookup followed by binary search in the bucket. | Merge into a new file and atomically replace the old one. |

## Individual integer rows

```sql
CREATE TABLE sponsor_prefixes (prefix INTEGER PRIMARY KEY);
```

Store the full 32-bit prefix in a Kotlin `Long` so all unsigned values fit. SQLite enforces uniqueness and supports indexed membership checks. Apply inserts, deletes and revision changes in one transaction; a replacement clears the old set in that transaction. This is the simplest measured option for independently queryable entries.

An alternative separates the bucket and suffix:

```sql
CREATE TABLE sponsor_prefixes (
    bucket INTEGER NOT NULL,
    suffix INTEGER NOT NULL,
    PRIMARY KEY (bucket, suffix)
) WITHOUT ROWID;
```

This avoids a separate row ID and composite-key index, but measured larger than the single-integer schema. SQLite supports `WITHOUT ROWID`; how to create and migrate this schema with Room has not been verified.

## One row per bucket

The logical value is a list of sorted, unique suffixes. SQLite cannot store a list natively; encoding it as a blob uses two bytes per suffix. For example, bucket `0a13` has a blob `03 29 c4 80 f0 12`, representing suffixes `0329`, `c480` and `f012`.

Look up the bucket by primary key, then search aligned pairs, never an arbitrary byte subsequence. For partial updates, group changes by bucket and merge each affected bucket's existing suffixes with its inserts and deletes. The app must validate encoding, ordering and uniqueness. Commit bucket changes and revision together in a transaction. A JSON-list or fixed-width text column follows the same update pattern with different encoding and space costs.

## Indexed file

An alternative outside SQLite stores 65,537 four-byte offsets followed by sorted two-byte suffixes. Two adjacent offsets locate a bucket, and a binary search finds a suffix. For this dataset the maximum bucket contains 86 suffixes. The total is calculated as 262,148 bytes of offsets plus 7,013,856 bytes of suffixes.

Updates would merge the old dataset and diff into a new file, validate it and atomically replace the old file. This needs temporary space for both copies and application-owned handling of integrity, crash recovery, file format versions and concurrent readers. It has not been benchmarked on Android.

Regardless of storage, a checksum can detect corruption or a failed merge if the client validates it against the server's checksum. It does not authenticate the dataset on its own.
