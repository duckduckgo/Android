---
paths:
  - "PixelDefinitions/pixels/**"
---
# Pixel Registry Definitions

Every pixel and wide event fired by the app is documented in a JSON5 file under
`PixelDefinitions/pixels/definitions/`. Reusable properties live in `params_dictionary.json` and
`suffixes_dictionary.json` alongside it — prefer a dictionary reference over an inline definition, and
never redefine a dictionary entry inline.

Name a definition file after its feature area (`autofill.json5`, `browser_menu.json5`); wide events use
`wide_<name>.json5`. `TEMPLATE.json5` is a scaffold, not a real definition — ignore it when reviewing
or auditing.

## Pixel definition structure

```json5
{
    "pixel_name_here": {
        "description": "When and why this pixel fires",   // required
        "owners": ["githubUsername"],                     // required
        "triggers": ["user_interaction"],                 // required — see "Choosing triggers" below
        "suffixes": ["first_daily_count"],
        "parameters": ["appVersion", "channel"],
        "expires": "2026-06-30"                           // temporary pixels only; omit for permanent
    }
}
```

## Choosing triggers

Valid `triggers` values: `"user_interaction"`, `"user_submitted"`, `"impression"`,
`"feature_lifecycle"`, `"scheduled"`, `"web_detection"`, `"exception"`, `"startup"`, `"page_load"`,
`"new_tab"`, `"search_ddg"`, `"other"`. Never default to `"other"` — classify with this procedure
(full definitions and worked examples: the
[trigger classification guide](https://github.com/duckduckgo/pixel-schema/blob/main/docs/trigger-classification.md)):

```
Classify by the EVENT THAT CAUSES THE PIXEL TO FIRE — never by dedupe cadence
("daily"/"unique" suffixes) or the delivery mechanism.

1. Deliberate user action (tap, click, toggle, menu selection, swipe, gesture,
   prompt/query submission)?  → user_interaction
   • A surface shown 1:1 because of a gesture (menu on tap, dialog behind a
     button, screen entered from an explicit flow) is user_interaction.
   • user_submitted ONLY for consent-scoped submission of the user's own data
     (breakage report, feedback). The opener button is user_interaction.
2. Surface displayed WITHOUT the user asking? → impression
   • Litmus: display code has an eligibility gate (feature flag, subscription
     state, view-count threshold, cooldown) → impression. Only gate is "user
     navigated here" → user_interaction.
3. Injected scripts detected page content (captcha, adwall, CMP, ads)?
   → web_detection
4. Automatic feature operation runs / completes / changes state (migration,
   sync cycle, job engine, token refresh, update detection, state observer)?
   → feature_lifecycle
   • Async completion of a user-initiated flow that can outlive the UI or be
     driven by a non-user party (billing observer, remote sync peer, retrying
     backend call) → feature_lifecycle. Synchronous completion inside the
     user's action → user_interaction.
5. A timer or scheduled/delayed job literally fires it (rollup, sampler,
   watchdog, absence-of-event check)? → scheduled
   • If the timer merely DETECTS something, classify by what was detected:
     anomaly → exception; a user toggle noticed by a poll → user_interaction.
6. Error/crash → exception. Launch or foreground → startup. Page loaded →
   page_load. New tab → new_tab. DDG search → search_ddg.
7. One pixel name covering several events (e.g. an event=shown|clicked param)?
   → list every applicable trigger; triggers is an array.

Store-and-forward: when counters are recorded at event time and transmitted
later by a worker, classify by the RECORDED event, never the flusher.

Use "other" only when nothing above fits, and say why in the description.
```

Android-specific worked examples:

- `m_fire_dialog_shown` — dialog opened 1:1 by the fire-button tap → `user_interaction` (step 1).
- `m_remote_message_shown` — RMF message displayed by config/eligibility → `impression` (step 2).
- `m_dbp_optout_stage_*` — PIR engine stage events inside a run → `feature_lifecycle`; the
  worker-fired `m_dbp_engagement_dau` sampler → `scheduled` (steps 4–5).
- `adBlocking_state_daily` — `onResume` observer reporting state once per day → `startup`, not
  `scheduled` (no timer fires it) (step 6).
- `onboarding_<step>` pixels multiplexing `event=shown|clicked` under one name →
  `["impression", "user_interaction"]` (step 7).

`expires` dates should cover the expected analysis period without making the pixel effectively
permanent.

## Parameters

Definitions must document **all** query parameters sent over the wire, including default ones.
Reference a dictionary entry by its key name as a string, or define a custom one inline as an object:

```json5
"parameters": [
    "appVersion",
    {
        "key": "customParam",
        "type": "string",
        "description": "What this parameter represents",
        "enum": ["value1", "value2"]
    }
]
```

Object fields: `key` (fixed key) or `keyPattern` (regex for dynamic keys, e.g. `"^error[0-9]?$"`) —
never both; `type` (`"string"`, `"integer"`, `"number"`, `"boolean"`); `description`; and optionally
`enum`, `pattern`, `examples`.

Do not declare `"type": "string"` with an enum of only `"true"`/`"false"` — that is `"type": "boolean"`
with no enum.

### Wire format is not the schema type

The transport stringifies every value into URL parameters, so tests assert strings for parameters of
every type and the ingest pipeline coerces them back. `boolean`, `integer` and `number` are all valid
declared types, and a test asserting `"true"` or `"5000"` is not evidence of a wrong type.

## Suffixes

A suffix is appended to the base pixel name to create variants. Reference dictionary entries by key, or
inline an object supporting `description`, `enum`, and optionally `key`, `type`, `pattern`:

```json5
"suffixes": ["first_daily_count"]
```

**Prefer `first_daily_count` for count+daily pairs.** When a pixel simply fires a count variant and a
daily variant with the same parameters, define **one** entry (base pixel name, no `_count`/`_daily` in
the key) with `"suffixes": ["first_daily_count", ...]`, instead of two separate `_count`/`_daily`
entries. This is the default going forward — only fall back to separate entries when the count and
daily variants genuinely need different parameters or descriptions. The Kotlin code is unaffected: it
still fires two distinct wire-string pixels (`..._count`, `..._daily`) via `fireCountAndDaily` or
equivalent; only the JSON5 registration collapses to one entry. See the `m_aichat_recent_chat_delete_*`
pixels in `duck_chat.json5` for a worked example.

- Suffixes are **order-sensitive and required**. Enums must not contain `null` or `""`.
- Define suffixes as `enum` unless the type is bounded (e.g. `boolean`). Unbounded numeric and string
  values belong in `parameters` instead.
- Optional suffixes use nested arrays in the pixel definition, not in the dictionary:
  `"suffixes": [["required", "optional"], ["required"]]`
- Nesting in an inner array forms a compound suffix combined into one segment:
  `"suffixes": [["platform", "form_factor"]]` produces `platform_formfactor` rather than two positions.
- Only give a suffix a `key` when that key string actually appears in the full pixel name.

## Wide event definitions

Wide events are defined here as ordinary pixel definitions, carrying their payload through the
`widePixel*` parameters from `params_dictionary.json` plus inline `feature.*` / `context.*` keys:

```json5
{
    "wide_feature-name": {
        "description": "Wide event sent when the feature flow completes",
        "owners": ["githubUsername"],
        "triggers": ["feature_lifecycle"],
        "suffixes": ["daily_count_short", "form_factor"],
        "parameters": [
            "widePixelPlatform",
            "widePixelType",
            "widePixelSampleRate",
            "widePixelFeatureStatus",
            "widePixelAppVersion",
            "widePixelAppName",
            {
                "key": "feature.name",
                "description": "Feature identifier",
                "enum": ["feature-name"]
            },
            {
                "key": "feature.data.ext.last_step",
                "type": "string",
                "description": "Last step reached when the flow ended",
                "enum": ["step_one", "step_two"]
            }
        ]
    }
}
```

Check that:

- `widePixelFeatureStatus` covers every terminal state the flow can reach
- a `last_step` field exists for flows that can fail or end unexpectedly
- `failure_reason` is defined when the flow can end in failure
- custom `feature.data.ext.*` fields use bounded enums where possible
- the sample rate is documented
- the CLAUDE.md privacy invariants hold for every field

See the existing `wide_*.json5` files for the current shape.

## Validation

```bash
cd PixelDefinitions
npm ci
npm run validate-defs-without-formatting   # schema validation — always run after changes
npm run lint                                # Prettier check
npm run lint.fix                            # auto-fix formatting
```

CI runs the same validation on pull requests via `@duckduckgo/pixel-schema`, checking schema
correctness, that parameter and suffix references resolve to dictionary entries, and formatting.
