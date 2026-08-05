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
        "triggers": ["other"],                            // required
        "suffixes": ["first_daily_count"],
        "parameters": ["appVersion", "channel"],
        "expires": "2026-06-30"                           // temporary pixels only; omit for permanent
    }
}
```

Valid `triggers` values: `"other"`, `"scheduled"`, `"startup"`, `"page_load"`, `"new_tab"`,
`"exception"`, `"user_submitted"`, `"search_ddg"`. Most pixels use `"other"`; `"scheduled"` for
daily/periodic, `"startup"` for app-launch, `"page_load"` for navigation-related.

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
        "triggers": ["other"],
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
