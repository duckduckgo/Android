---
name: create-pixel
description: >
  Use this skill whenever the user asks to add, create, or fire a new analytics
  pixel / event / metric in the DuckDuckGo Android app, or says things like
  "track when X happens", "add a pixel for Y", "fire a pixel on Z".
  Also invoke when reviewing pixel-related code that appears incomplete (e.g.
  a Kotlin enum entry added without a matching JSON5 definition).
  CRITICAL: pixels require changes in BOTH the Kotlin enum AND in
  PixelDefinitions/pixels/definitions/<feature>.json5 — the CI workflow
  pixel-validation.yml will block the PR if the JSON5 entry is missing or
  malformed.
---

You are adding a new analytics pixel to the DuckDuckGo Android app. A pixel
has TWO homes in this repo, and both must be updated together:

1. A Kotlin enum in the feature's `-impl` module (e.g. `PirPixel.kt`) that
   declares the `baseName` and `PixelType(s)`.
2. A JSON5 schema entry in `PixelDefinitions/pixels/definitions/<feature>.json5`
   describing `triggers`, `suffixes`, `parameters`, and `owners`.

CI (`.github/workflows/pixel-validation.yml`) validates the JSON5 side against
the `duckduckgo/pixel-schema` repo and cross-checks `owners` against
`duckduckgo/internal-github-asana-utils/user_map.yml`. Forgetting the JSON5
side is the most common failure mode; this skill exists to prevent it.

---

## 1. Before you start

- **Locate the feature's pixel enum.** Look under
  `<feature>-impl/.../pixels/` — most features already have one. Use Glob:
  `<feature>/**/pixels/*Pixel.kt`.
- **Locate the matching JSON5 file** in `PixelDefinitions/pixels/definitions/`.
  If the feature does not have one yet, create it using
  `PixelDefinitions/pixels/definitions/TEMPLATE.json5` as a starting point.
- **Decide the correct `PixelType`** (defined in
  `statistics/statistics-api/src/main/java/com/duckduckgo/app/statistics/pixels/Pixel.kt`):
  - **`Count`** — fire every time the event happens (cardinality is fine)
  - **`Daily()`** — first-per-UTC-day only; use for presence/engagement signals
  - **`Unique()`** — once-ever-per-install; use for lifetime events like
    "first run of feature"
  - Multiple types may be declared together, e.g. `types = setOf(Count, Daily())`
- **Reliability:** if the pixel must survive network failure, the sender
  should use `enqueueFire` (see `SubscriptionPixel.kt` pattern), not plain `fire`.
- **Temporary pixels:** if the pixel is short-lived (experiment, rollout),
  set `"expires": "YYYY-MM-DD"` in the JSON5 entry.

## 2. Naming convention

- `baseName` = `m_<feature-prefix>_<event-name>`
  - `m_` is the mobile pixel prefix (required)
  - `<feature-prefix>` examples: `dbp` (PIR), `privacy-pro` / `ppro`
    (subscriptions), `autoconsent`, `new_tab_page`, `browser`
  - `<event-name>` uses kebab-case; segments separated by `_`
- Do **not** include `_c` / `_d` / `_u` in `baseName` — `getPixelNames()`
  appends them at runtime based on the `PixelType`.

## 3. Add the Kotlin enum entry

Before adding, read the feature's existing pixel enum to match its pattern.
Two common patterns:

### Pattern A — `baseName` + `PixelType(s)` (most features)

Reference: `pir/pir-impl/src/main/java/com/duckduckgo/pir/impl/pixels/PirPixel.kt`.

```kotlin
PIR_MY_NEW_EVENT(
    baseName = "m_dbp_my-new-event",
    types = setOf(Count, Daily()),
),
```

Or for a single type:

```kotlin
PIR_MY_NEW_EVENT(
    baseName = "m_dbp_my-new-event",
    type = Count,
),
```

Some features extend this with `includedParameters` (param whitelisting) and
an `enqueue: Boolean` flag — see
`subscriptions/subscriptions-impl/.../pixels/SubscriptionPixel.kt`.

### Pattern B — enum implements `Pixel.PixelName` directly (simpler features)

Reference: `autoconsent/autoconsent-impl/.../pixels/AutoConsentPixel.kt`.
The enum constant hard-codes the full pixel name, with no suffix generation.
Follow this style only if the feature's existing enum already does it.

**Detect which pattern the feature uses** by reading the existing enum before
adding your entry, and match it.

## 4. Add the JSON5 definition

File: `PixelDefinitions/pixels/definitions/<feature>.json5`.

Required keys: `description`, `owners`, `triggers`, `suffixes`, `parameters`.
Optional: `expires`.

```json5
"m_dbp_my-new-event": {
    "description": "Fired when <specific event condition>",
    "owners": ["<github-username>"],
    "triggers": ["other"],
    "suffixes": ["daily_count_short", "form_factor"],
    "parameters": [
        "appVersion",
        {
            "key": "my_param",
            "description": "What this param captures",
            "type": "string"
        }
    ]
}
```

### Suffix rules

Valid suffix keys live in `PixelDefinitions/pixels/suffixes_dictionary.json`:

- **`"daily_count_short"`** — when Kotlin declares `Count` and/or `Daily`
  (maps `c` / `d` server-side). This is the common case.
- **`"first_daily_count"`** — alternative with `first` / `daily` / `count`
  values; confirm in `suffixes_dictionary.json` before using.
- **`"form_factor"`** — standard; include unless the pixel is phone-only.

### Parameters

Pre-defined parameter keys (`appVersion`, `atb`, `remoteConfigEtag`, and the
wide-pixel params) live in `PixelDefinitions/pixels/params_dictionary.json`.
Reference them by name as plain strings; declare custom params as objects
with `key`, `description`, `type`.

### Owners

`owners` entries must exist in
`duckduckgo/internal-github-asana-utils/user_map.yml`. CI will fail with a
user_map error if a GitHub username is missing from that file.

For a fully-populated real-world example, see
`PixelDefinitions/pixels/definitions/personal_information_removal.json5`.

## 5. Wire the fire call

Find the feature's pixel-sender interface (e.g. `PirPixelSender`) and add a
high-level method that calls `fire(<YourPixel>, params)` (or `enqueueFire`
for reliability-sensitive pixels). Do **not** call `pixelSender.fire(...)`
directly from feature code — always go through the sender interface so
parameters stay consistent across call sites.

## 6. Verify locally

Schema validation for the JSON5 side:

```bash
cd PixelDefinitions
npm ci   # first time only
npm run lint
npm run validate-defs-without-formatting
```

If `validate-defs-without-formatting` fails with a user_map error, either
fix the GitHub username in `owners` or confirm it has been added to
`duckduckgo/internal-github-asana-utils/user_map.yml`.

Unit tests for the Kotlin side:

```bash
./gradlew :<feature>-impl:testDebugUnitTest
./gradlew spotlessApply
```

## 7. Checklist before opening the PR

- [ ] Kotlin enum entry added with correct `baseName` and `PixelType(s)`
- [ ] JSON5 entry added in
      `PixelDefinitions/pixels/definitions/<feature>.json5`
- [ ] `baseName` in Kotlin **matches** the key in JSON5 exactly
- [ ] `owners` list contains valid GitHub usernames from `user_map.yml`
- [ ] Temporary pixels include `"expires": "YYYY-MM-DD"`
- [ ] Pixel is fired via the feature's pixel-sender interface, not directly
- [ ] `npm run validate-defs-without-formatting` passes locally
- [ ] Module's `testDebugUnitTest` passes
- [ ] If reliability matters, the pixel uses `enqueueFire` not `fire`

Remind the author that `.github/workflows/pixel-validation.yml` will gate
the PR — any mismatch between the Kotlin `baseName` and the JSON5 key, or
an unknown owner, will fail the CI check.