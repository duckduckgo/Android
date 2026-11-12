# Pixels

Pixels are lightweight telemetry events sent via HTTP with a name and optional parameters. They are used for:

- Feature usage events (e.g., button clicks, screen impressions, toggle changes)
- Error monitoring (e.g., network failures, parsing errors, crash reports)
- Conversion and retention (e.g., subscription purchase, activation, onboarding completion)

The privacy invariants every pixel must satisfy (no PII, no URLs, no correlation IDs, bucketed numbers,
bounded enums) are in AGENTS.md and apply to all outbound data — they are not repeated here.

## Types of Pixels

### Standard Pixels

Sent every time the event occurs. Use for events where per-occurrence volume matters.

### Daily Pixels

Sent at most once per calendar day per event. Use to measure the number of unique users affected by something (e.g., how many users hit a particular error per day). Most daily pixel implementations also support a "count" variant that fires every time.

When a pixel fires as a plain count+daily pair with identical parameters, register it as **one**
definition entry using the `first_daily_count` suffix rather than two separate `_count`/`_daily`
entries — see `.claude/rules/pixel-definitions.md`. The firing code is unchanged either way: it still
calls `pixel.fire()` twice with the explicit `_count`/`_daily` wire-string names.

### Unique Pixels

Sent once per install for the lifetime of the install. Use for one-time lifecycle events (e.g., first activation, first use of a feature).

## Pixel Naming

- **Use underscores or hyphens** as word separators. Be consistent within a feature area.
- **Use clear, descriptive names.** Anyone reading the pixel name should understand what it represents without additional context. Avoid cryptic abbreviations.
- **Group related pixels** with a common prefix (e.g., `burn_started`, `burn_completed`, `burn_error`).
- **Embed dynamic data in parameters, not pixel names.** Pixel names should be static string literals. Use query parameters for variable data.

```
# Good — static name with parameter
pixel: "burn_error", parameters: { "operation": "delete", "exceptionType": "IOException" }

# Bad — dynamic data in pixel name
pixel: "burn_error_delete_IOException"
```

## Pixel Parameters

- **Prefer shared dictionary entries.** If a parameter is used by multiple pixels, define it in `params_dictionary` and reference it by key.
- **Use structured, typed parameters.** Each parameter should have a clear type (`string`, `integer`, `number`, `boolean`) and string parameters need a bounded `enum` (or tight `pattern`) of allowed values — see Privacy Pitfalls below.
- **Include error context for error pixels.** When a pixel reports an error, include enough information to diagnose the issue — error type, error code, failing step — without leaking PII.
- **Bucket all numeric values.** Durations, counts, sizes, and other continuous values must be bucketed into ranges.

```
# Good — bucketed duration
"duration_bucket": "1_to_5_min"

# Bad — exact value
"duration_ms": "237841"
```

## Privacy Pitfalls

Pixels that look harmless in a diff can still single out a user or reveal what they did. Check every
new or changed pixel against these, in addition to the AGENTS.md invariants.

### Bound every string

A string parameter that can take any value can't be reviewed. Declare an `enum` (or a tight `pattern`)
for it in the definition, and send a value the code controls.

- **Never send `e.message`.** Exception messages carry hostnames, URLs, file paths and queries
  (`Unable to resolve host "files.mybank.example"`). Send the exception class
  (`e.javaClass.simpleName`) plus a bounded stage or step enum instead.
- **Never send `toString()` of a data or sealed class.** It prints every field, and any field added
  later leaks without review (`Failed(code=-2, url=https://...)`). Map each state to a value with an
  explicit `when`.
- **Never send an enum constant's `.name`.** A rename silently changes the data, and nothing stops a
  future caller sending something else. Give the enum a stable `pixelValue` property and declare the
  same values as the definition's `enum`.

```kotlin
// Good
enum class AppTheme(val pixelValue: String) { LIGHT("light"), DARK("dark"), SYSTEM("system") }
pixel.fire(SettingsPixelName.THEME_CHANGED, mapOf("theme" to newTheme.pixelValue))

// Bad
pixel.fire(SettingsPixelName.THEME_CHANGED, mapOf("theme" to newTheme.name.lowercase()))
pixel.fire(DownloadsPixelName.DOWNLOAD_FAILED, mapOf("error" to e.message.orEmpty()))
```

### Bucket so every bucket is crowded

A bucket only protects users if many of them share it. Use narrow buckets where most users sit and
widen them toward the tail, ending in an open bucket (`over_50`) that still holds many users. Rare top
buckets (`501_1000`, `over_5000` tabs) are as identifying as the exact number, and worse on a daily
pixel, on a rare pixel, or next to other rare values. Check the cut against real data if unsure.

### Watch the combinations

Values that are common on their own can identify someone together. API level + full WebView version +
manufacturer + model + locale + font scale in one daily pixel is a device fingerprint. Ask one question
per pixel, send coarse values (major versions only), and drop anything the analysis doesn't need.

Several fields are fine when the combination stays common: a `pattern` like
`^default:(on|off),widget:(on|off),address_bar:(top|bottom|split)$` has 12 possible values, each shared
by many users. The test is whether the combination makes people rare, not how many fields it has.

### Don't let events link to each other

Anything that lets one event be matched to another turns anonymous events into one user's history.

- **No exact timestamps.** A precise time is a join key, and local time also reveals the time zone. The
  server already knows roughly when a pixel arrived, so usually drop it. If timing matters, send a
  bucketed duration (`under_1h`, `1h_to_1d`, `over_1d`) or part of day in a fixed time zone, wider the
  rarer the event.
- **No values that change predictably.** `days_since_install` goes up by one every day, so whoever sent
  43 today sends 44 tomorrow. Send a few wide ranges (`0`, `1_7`, `8_30`, `31_90`, `over_90`) that stay
  the same for weeks.
- **Review pixels fired together as one.** Pixels sent by the same trigger arrive together from the same
  device with the same `appVersion`, so four one-parameter daily pixels can be paired back into the
  fingerprint above. Apply the combination check across all of them.

To make arrival time harder to match, add the `petal` parameter, which routes the pixel through the
PETAL timestamp-randomization pipeline:

```kotlin
put(Pixel.PixelParameter.PETAL, Pixel.PixelValues.PETAL_RANDOMIZE)
```

and reference `"petal"` from `params_dictionary.json` in the definition's `parameters`.

## Pixel Definition Files

Every pixel fired in code **must** have a corresponding entry in a definition file under `PixelDefinitions/pixels/definitions/`. Without this, the pixel validation CI check will fail.

- Find the appropriate definition file for your feature area (or create a new one)
- Add an entry keyed by the full pixel name
- Include `description`, `owners`, `triggers`, `parameters`, and `suffixes` fields
- Choose `triggers` with the decision procedure in `.claude/rules/pixel-definitions.md` — classify by
  the event that causes the pixel to fire, and never default to `["other"]`
- The `owners` field must contain the author's GitHub username
- Match the format of existing entries in the same file

For the definition-file schema, the dictionaries, and the local validation commands, read
`.claude/rules/pixel-definitions.md`.

## Privacy Review & Triage

Any PR that **adds a new pixel or modifies the pixel registry** (anything under `PixelDefinitions/pixels` — pixel definitions, wide-event definitions, and the shared params/suffixes dictionaries) must go through privacy triage. The mechanism is a single label:

- **Apply the `privacy review required` label to the PR.** This automatically creates a Privacy Triage task in Asana and adds the PR author as a collaborator; the Privacy Triage DRI follows up there. Applying the label *is* how the triage is created — there is no separate manual step.
- **When an agent creates or helps create a pixel PR, it MUST add this label when opening the PR** — apply it after creating the PR with `gh pr edit <number> --add-label "privacy review required"` (see `.claude/docs/contributions.md` for how the PR itself is opened; `gh pr create` is not used in this repo). If the agent cannot set labels, it must say so explicitly so the author applies it. This is in addition to updating the pixel definitions — both are required, do not skip either.
- Self-service still applies: you may proceed with implementation without blocking on triage where appropriate, but the label must still be applied so triage happens.

**Modifying an existing pixel that isn't in the registry yet:** first document the old pixel in the registry. If the change is small, note in the PR/code which parts are new; if it's large, split into two commits/PRs (one documenting the already-approved existing pixel, one for the change). If a prior triage is known, record it in the pixel's `privacyReview` property.

Full process: [Pixels Triage using the Pixel Registry](https://app.asana.com/1/137249556945/task/1208615048491217).

## When to Use a Wide Event Instead

Use a **wide event** rather than a pixel when:

- A user journey has multiple steps that can succeed or fail independently
- You need to understand where users drop off or encounter errors in a multi-step process
- The outcome of earlier steps affects the interpretation of later steps

Use **both** when a wide event makes sense but the journey spans a long time period where waiting for completion would delay monitoring.

For designing and implementing wide events, read `.claude/rules/wide-events.md`.