---
alwaysApply: false
description: What pixels are, when to use them, privacy requirements, and general best practices for pixel telemetry.
---
# Pixels

Pixels are lightweight telemetry events sent via HTTP with a name and optional parameters. They are used for:

- Feature usage events (e.g., button clicks, screen impressions, toggle changes)
- Error monitoring (e.g., network failures, parsing errors, crash reports)
- Conversion and retention (e.g., subscription purchase, activation, onboarding completion)

## Privacy Requirements

Pixels must uphold user privacy. Every pixel must satisfy these invariants:

- **No PII.** Never include personally identifiable information — emails, names, account IDs, usernames, phone numbers — in pixel names or parameters.
- **No URLs or domains.** Do not include browsing URLs, page titles, or domain names as parameter values (breakage reports are a controlled exception with explicit user consent).
- **No correlation IDs.** Do not include session IDs, GUIDs, exact timestamps, or any value that could link multiple events to the same user session.
- **Bucket numerical values.** Exact numbers (duration in ms, byte counts, item counts) can serve as fingerprints. Always bucket into ranges.
- **Bounded enums over free-form strings.** Parameters should use a known set of values wherever possible. High-cardinality, unbounded string parameters create privacy risk and are hard to analyze.

## Types of Pixels

### Standard Pixels

Sent every time the event occurs. Use for events where per-occurrence volume matters.

### Daily Pixels

Sent at most once per calendar day per event. Use to measure the number of unique users affected by something (e.g., how many users hit a particular error per day). Most daily pixel implementations also support a "count" variant that fires every time.

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
- **Use structured, typed parameters.** Each parameter should have a clear type (`string`, `integer`, `number`, `boolean`) and, where possible, a bounded `enum` of allowed values.
- **Include error context for error pixels.** When a pixel reports an error, include enough information to diagnose the issue — error type, error code, failing step — without leaking PII.
- **Bucket all numeric values.** Durations, counts, sizes, and other continuous values must be bucketed into ranges.

```
# Good — bucketed duration
"duration_bucket": "1_to_5_min"

# Bad — exact value
"duration_ms": "237841"
```

## Pixel Definition Files

Every pixel fired in code **must** have a corresponding entry in a definition file under `PixelDefinitions/pixels/definitions/`. Without this, the pixel validation CI check will fail.

- Find the appropriate definition file for your feature area (or create a new one)
- Add an entry keyed by the full pixel name
- Include `description`, `owners`, `triggers`, `parameters`, and `suffixes` fields
- The `owners` field must contain the author's GitHub username
- Match the format of existing entries in the same file

See the `pixel-definitions` rule for detailed guidance on writing definition files.

## Validation

Pixel definitions are validated by CI using the `@duckduckgo/pixel-schema` package. Validation runs automatically on pull requests and checks:

- Schema correctness of all definition files
- Parameter and suffix references resolve to dictionary entries
- Formatting (via Prettier)

Run validation locally before pushing:

```bash
cd PixelDefinitions
npm ci
npm run validate-defs-without-formatting
npm run lint
```

## When to Use a Wide Event Instead

Use a **wide event** rather than a pixel when:

- A user journey has multiple steps that can succeed or fail independently
- You need to understand where users drop off or encounter errors in a multi-step process
- The outcome of earlier steps affects the interpretation of later steps

Use **both** when a wide event makes sense but the journey spans a long time period where waiting for completion would delay monitoring.

See the `wide-events` rule for detailed guidance on designing and implementing wide events.