---
name: add-pixel-definition
description: >
  Use this skill to add a new pixel to the DuckDuckGo Android codebase — both
  the Kotlin-side `PixelName` enum entry AND the required JSON5 entry under
  `PixelDefinitions/pixels/definitions/`. Invoke whenever the user says things
  like "add a pixel for X", "I need to fire a pixel when Y happens", "wire up
  a daily pixel", "scaffold a unique pixel", or any variation that implies
  creating a new pixel. Every pixel fired in code MUST have a matching
  definition or the pixel validation CI check fails — this skill produces both
  in one pass so the two never drift apart.
---

# Add Pixel Definition

Scaffold a new pixel. All conventions, shapes, naming, types, suffix rules,
default parameters, stripping mechanism, and consistency checks live in the
rules — this skill does not restate them. Read the rules first, then drive
the workflow below.

## Rules (source of truth — read before the interview)

- `.cursor/rules/pixels.mdc`
- `.cursor/rules/pixel-definitions.mdc`

If the user's input conflicts with a rule, push back with the rule's shape —
do not silently rewrite.

## Scope

This skill **defines** the pixel and shows an **example** fire call. It does
not wire the pixel into any call site — only the developer knows which
try/catch, method, and parameters belong at the fire site.

Files this skill may edit:

- The Kotlin `*PixelName.kt` for the target module
- The JSON5 definition file under `PixelDefinitions/pixels/definitions/`
- A `PixelParamRemovalPlugin` implementation, when the definition omits a
  default parameter (see `pixels.mdc`)

Files this skill does NOT edit: call sites, DI modules, constructors, or
anything else outside the three above.

## Workflow

1. Read both rule files.
2. Interview the user (below).
3. Edit or create the Kotlin `*PixelName.kt` per `pixels.mdc`.
4. Edit or create the JSON5 definition per `pixel-definitions.mdc`.
5. If any default parameter is stripped, update/create a
   `PixelParamRemovalPlugin` per `pixels.mdc` ("Stripping Default
   Parameters").
6. Run the validator command from `pixel-definitions.mdc`. On failure, read
   the error, fix, re-run.
7. Print an example fire-call snippet per `pixels.mdc` ("Fire-Call
   Templates") and point the user at the "Definition / Call-Site
   Consistency" checklist before they wire it.

---

## Interview

Ask one at a time, or in a short batch if the user has front-loaded answers.
Skip anything already supplied. For anything the user is unsure about, point
at the relevant rule section — don't paraphrase it here.

1. **Pixel name** — literal string sent over the wire. Validate against
   `pixels.mdc` "Pixel Naming".
2. **Module** — which `-impl` module.
3. **Pixel type(s)** — one or more. Read `pixels.mdc` "Types of Pixels"
   and present the available types and their semantics to the user.
4. **Default parameters** — for each of `atb` and `appVersion`, ask whether
   to include or strip. Point at `pixels.mdc` "Default Parameters" for the
   decision criteria.
5. **Description** — one sentence describing exactly when it fires. Push
   back on vague answers.
6. **Owner GitHub username(s)** — for the `owners` array. Ask; don't guess.
7. **Trigger** — defaults to `other`; see `pixel-definitions.mdc`.
8. **Extra parameters** (optional) — key, type, description, enum values
   when bounded. Reject anything that violates the privacy invariants in
   `pixels.mdc`.
9. **Temporary or permanent** — if temporary, capture an `expires` date
   (`YYYY-MM-DD`).

---

## Output template

```
Added pixel: <name>
  Types:      <Count|Daily|Unique, ...>
  Defaults:   atb=<kept|stripped>, appVersion=<kept|stripped>
  Kotlin:     <module>/.../<Feature>PixelName.kt — added <N> entries
  Definition: PixelDefinitions/pixels/definitions/<file>.json5 — added <N> entries
  Removal:    <path or "n/a">
  Validation: <passed|failed>
```

---

## Edge cases

- **No existing `*PixelName.kt`** — create one per `pixels.mdc`.
- **Multiple pixels at once** — handle sequentially; offer to reuse
  owner/file/parameter answers.
- **No `-impl` module yet** — stop. Scaffolding a new module is a different
  flow.
