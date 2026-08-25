# Pixels

Pixels are lightweight telemetry events sent via HTTP with a name and optional parameters. They are used for:

- Feature usage events (e.g., button clicks, screen impressions, toggle changes)
- Error monitoring (e.g., network failures, parsing errors, crash reports)
- Conversion and retention (e.g., subscription purchase, activation, onboarding completion)

The privacy invariants every pixel must satisfy (no PII, no URLs, no correlation IDs, bucketed numbers,
bounded enums) are in CLAUDE.md and apply to all outbound data — they are not repeated here.

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