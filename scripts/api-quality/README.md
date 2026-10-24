# API quality scripts

[AOI:Android API Standards](https://app.asana.com/1/137249556945/project/1218312816344693/list/1218311445762235) 

Scores every `-api` module against the mechanical checks M1–M8 of the API quality rules, compares runs, and publishes the
weekly report.

Refer to [API Quality Rules and Scores](https://app.asana.com/1/137249556945/project/1218312816344693/task/1218312816344701?focus=true) on Asana for more information and full list of checks.

| File | What it does | Run by                              |
|---|---|-------------------------------------|
| `score.sh` + `Scorer.java` | Scores the checked-out code | Manual or every workflow            |
| `compare.py` | Diffs two scorer runs by declaration: new, resolved and moved findings, score changes, and whether it regressed | Every workflow except the scorecard |
| `asana_sync.py` | Posts the weekly report to Asana and files one Triage task per new finding group | Weekly workflow                     |
| `asana_task.py` | Creates the one Asana task of a run, or updates it on a re-run | Scorecard and compare workflows     |

## Score your checkout

```bash
scripts/api-quality/score.sh
```

Writes five files to `scripts/api-quality/output/` (gitignored), named with today's date. Needs Java 21. The Kotlin
compiler jars come from your Gradle cache, or are downloaded once from Maven Central into `~/.cache/api-quality-scorer`.

| # | Argument | Default | Meaning |
|---|---|---|---|
| 1 | Output folder | `scripts/api-quality/output` | Where the files go |
| 2 | Date | Today | Used in the file names |
| 3 | Previous scores CSV | None | Adds a "Score changes" section to the scorecard |

Set `REPO_ROOT` to score a different checkout, e.g. `REPO_ROOT=/tmp/old scripts/api-quality/score.sh /tmp/old-score`.

## Compare two runs

```bash
python3 scripts/api-quality/compare.py --before /tmp/old-score --after scripts/api-quality/output --mode weekly --out-md /tmp/report.md
```

`--mode pr` renders the PR comment instead. Both runs must come from the same `Scorer.java`.

## Output files

| File | Contents |
|---|---|
| `api-quality-scorecard-<date>.md` | Summary and per-module scores |
| `api-quality-scores-<date>.csv` | One row per module, with its failing rules |
| `api-quality-findings-<date>.csv` | One row per finding, including undocumented declarations in modules that still clear the 80% KDoc threshold |
| `api-quality-declarations-<date>.csv` | Every counted public declaration, to audit M3, M4 and M6 |
| `api-quality-constructors-<date>.csv` | Every constructor of a public class, any visibility; not counted, only lets `compare.py` tell a constructor still exists |

## Asana configuration

The posting scripts read every Asana ID from these repository variables (*Settings → Secrets and variables → Actions →
Variables*), which the workflows pass in as environment variables of the same names. For a local run, `export` them.

| Variable | Asana ID of |
|---|---|
| `GH_API_QUALITY_PROJECT_ID` | The project |
| `GH_API_QUALITY_TRIAGE_SECTION_ID` | Its Triage section: finding and regression tasks |
| `GH_API_QUALITY_SCORECARDS_SECTION_ID` | Its Scorecards section: scorecard tasks |
| `GH_API_QUALITY_WEEKLY_REPORTS_SECTION_ID` | Its Weekly Reports section: weekly report tasks |
| `GH_API_QUALITY_GITHUB_RUN_FIELD_KEY_ID` | The "GitHub run" text field, which lets a re-run find and update its own task |
| `GH_API_QUALITY_STATUS_FIELD_KEY_ID` | The "API quality status" single-select field |
| `GH_API_QUALITY_STATUS_FIELD_NEEDS_EXPLANATION_VALUE_ID` | Its "Needs explanation" option, set on every new finding and regression task |

Scheduled weekly runs post once `GH_API_QUALITY_PROJECT_ID` is set. A run that posts with any variable missing fails and
names the missing ones. Posting uses the existing `ASANA_ACCESS_TOKEN` secret.

## Definitions

- **Public:** no `private`, `internal` or `protected` modifier, inside a public parent. `override` members are left out, because
  the contract and KDoc belong to the declaration they override.
- **Declaration (M6):** each public type (top-level or nested, including type aliases; not companion objects or enum entries),
  top-level function or property, and member function or property. Primary-constructor `val`/`var` parameters count as properties.
- **M3** covers types and top-level functions. **M4** covers member functions and properties, and top-level properties. A
  property is also documented when its class KDoc describes it with an `@property`/`@param` tag or a `[name]` link. A module with
  nothing to document for a check scores 100% on it.
- **Constructors:** a `val`/`var` parameter is a property, public even behind a private constructor. Other parameters of a
  public constructor are checked by M2, M7 and M8 as `Type.constructor(...)`; enum and sealed class constructors aren't.
- **M2:** `Toggle` anywhere in a signature or supertype, including type arguments such as `List<Toggle>`. A qualified name such
  as `Toggle.State` doesn't count.
- **M5:** a function whose declared return type is exactly `Intent?`, `Fragment?`, `View?` or `Bundle?`.
- **M7:** `Any`, `Pair` or `Triple` anywhere in a signature, including type arguments, except `equals(other: Any?)` and
  `readResolve()`.
- **M8:** a public `var` whose setter is also public, or a `Mutable*` type anywhere in a signature.

Files are parsed with the Kotlin compiler's own parser, so the same code always gives the same score. There is no symbol
resolution: types are matched by simple name, and a declaration whose type is inferred isn't checked by M2, M5, M7 or M8.

Changing any definition changes scores across the fleet, so it follows the re-baseline procedure.

## Tests

```bash
pip install -r scripts/api-quality/requirements.txt
cd scripts/api-quality && python3 -m pytest
```

`test_scorer.py` runs the scorer on small Kotlin fixtures, so it needs Java 21; it's skipped without it.
