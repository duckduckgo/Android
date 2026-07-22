# Onboarding dialog spec (v6 — summary)

## Problem

`BrandDesignUpdateWelcomePage` is ~3.1k lines and growing. Three causes:

1. **Every dialog is described twice.** `configureDaxCta` (~720 lines) wires each dialog for
   animated transitions; `showDialogWithoutAnimation` (~620 lines) wires the same dialogs
   again for snapped renders (rotation, re-entry). Every change touches both, and they drift.
2. **Every dialog is modelled three times**: the step's render intent → `applyDialog()` →
   `PreOnboardingDialogType` plus ~11 scattered `ViewState` fields → the view code.
3. **Dialogs assume their neighbours.** Branches hardcode what the previous screen left
   behind (which embellishment to dismiss, which animation to exit). Re-ordering screens
   breaks these assumptions one by one.

The Custom AI flow already re-orders screens, and the parent project will add more
permutations. The current structure makes each one a hand-wired special case.

## Goals

- One `DialogSpec` per screen: pure data (background, embellishment, content, CTAs). The
  step resolves it, the VM forwards it, the renderer draws it. Three representations become one.
- One render engine that diffs previous spec against new spec. One code path for animated
  and snapped renders, so they cannot drift.
- Any dialog can follow any dialog, or appear from nothing. Re-ordering a flow becomes a
  list edit in the plan provider.

Non-goals: legacy onboarding flow, the intro/outro animations, system dialogs
(notifications, default browser, add widget), `BrowserActivity` CTAs.

## Strategy

```
plan provider    step resolves Dialog(DialogSpecs.comparisonChart())   ← ordering + content authority
      ▼
VM               forwards spec into ViewState, fires Presented          ← no translation layer
      ▼
render engine    diffs prev vs new spec, runs one fixed phase pipeline
      ▼
elements         background · embellishment · card · content · CTAs · indicator
```

Key decisions:

- **Spec is value-comparable data.** No lambdas, no views. Equality drives the diff, and
  specs are unit-testable straight off the plan.
- **Fixed phase pipeline, no transition framework.** All dialogs already animate in the same
  order (background + embellishment + card morph → title types → content fades → listeners).
  The engine encodes that once; screens supply only data and views. Escape hatch for a
  bespoke screen is a richer content spec — never a `when(dialogType)` in the engine.
- **Titles and copy live in `ContentSpec`** (the per-screen part of the spec), matching the
  layout: only CTAs, step indicator and the card are shared views. A view-layer binder sets
  the spec's data onto the screen's layout include and gives the engine a small handle
  (title view, views to fade in, cleanup callback) — so screens declare their views instead
  of re-describing choreography. A single title widget replaces the typing-animation
  machinery every screen currently copy-pastes.
- **Stateful screens keep live edits in a small view-layer holder**; only the result crosses
  to the VM on submit, so in-screen toggling never re-renders.
- **CTAs carry their orchestrator event in the spec**; screens whose event needs the user's
  selection use a `Submit` marker that reads the holder's result at click time.
- **Every dialog can enter from an empty stage.** "No previous spec" means clear the stage
  and enter — not a special case. This covers the first dialog, returns from
  `BrowserActivity` (today a hand-coded comparison-chart path), and migration handoffs.
- **Copy variants are builder parameters** chosen by the plan (e.g. Custom AI copy), not
  runtime flags threaded through `ViewState`.

## Benefits

- ~1.3k lines of duplicated per-dialog wiring become one diff plus per-screen data. Snap and
  animate cannot drift apart.
- Re-ordering or permuting a flow = editing a list. Step indicator renumbers itself; any
  ordering animates correctly with no new transition code.
- New bubble screen = one step factory + one spec builder + one content spec + one binder
  entry + its layout. Today it's ~200 lines duplicated across two when-blocks plus VM plumbing.
- One owner for running animations: tap-to-skip and view teardown become one call instead of
  hand-enumerating ~25 animators. This pays off even before any re-ordering does.
- `ViewState` collapses from ~15 fields to a spec and two flags; the VM's dialog switch
  reduces to five branches.
- `DialogSpec` is the state model a future Compose port would consume unchanged — the
  declarative architecture without the rewrite risk.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Choreography edge cases: embellishments can be vetoed by available space, and they decide the card's anchoring; one screen depends on anchor timing during the previous embellishment's exit | Owned by one `EmbellishmentController` (fit veto + anchoring) plus a general engine rule: hold the card anchor until the exiting embellishment finishes. A thin POC of the welcome → comparison → address-bar chain de-risks all of this first |
| Shown pixels silently stop firing | Shown pixels fire when the orchestrator receives a `Presented` event, and today that event is sent from code this design deletes; the VM fires it explicitly per step instead. Legacy `PREONBOARDING_*_SHOWN_UNIQUE` pixels are moved onto steps or confirmed superseded before the old path goes |
| Regression in a release-critical flow | Strangler migration: one dialog at a time, each step shippable and revertible, legacy and new renderer coexist (legacy stays authoritative for unmigrated screens; the engine clears the stage when taking over). Maestro release-blocker flows plus unit tests gate every step |
| Holder state lost on rotation | Live values backed by small dedicated VM fields; holders are thin facades |
| Two consecutive steps resolve identical specs, `StateFlow` swallows the second | Emitted state is keyed by step id, not spec equality alone |
| Engine grows dialog-specific logic over time | Hard rule: bespoke behaviour goes into the screen's content spec or its handle, never into the engine |

## Rollout

Scaffolding first (pure data types + engine, no behaviour change), then migrate screens
simplest-first, then delete the legacy when-blocks, `PreOnboardingDialogType`, and dead
`ViewState` fields. POC before committing: the three-screen chain above exercises every
risky mechanism in one pass.
