# Onboarding Dialog Spec — Feasibility Design (v2: VM-emitted spec)

**Date:** 2026-07-09
**Status:** Design / feasibility exploration (no production code in this pass)
**Author:** Łukasz Paczos

## 1. Problem

`BrandDesignUpdateWelcomePage` is ~3k lines and growing. It hosts every pre-onboarding
dialog and imperatively hand-wires each one. Two forces make this unsustainable:

- The **Custom AI flow** already re-orders screens.
- The parent project will introduce more screen **permutations, including re-ordering**.

### Where the size comes from

Two large `when(dialogType)` blocks describe the *same* per-dialog state twice:

| Function | Lines | Role |
|---|---|---|
| `configureDaxCta` | ~803–1402 (~600) | animated path — transitions between dialogs |
| `showDialogWithoutAnimation` | ~1515–2046 (~530) | snapped path — mid-flow re-entry / config change |

Each branch hand-wires content-include visibility, embellishment enter/exit, background
step, chrome (title / subtitle / CTAs / step indicator), and a bespoke content intro
animation. Animated and snapped are separate code, so every dialog is described twice and
every new ordering touches both.

A second, related smell: `BrandDesignUpdatePageViewModel.ViewState` has accreted ~14
fields, roughly half of them per-screen (`selectedAddressBarPosition`,
`inputScreenSelected`, `showSplitOption`, `inputScreenPreview*`, `hide*Row`,
`isCustomAiOnboardingFlow`, `currentPageNumber` / `maxPageCount`). Every new screen adds
more.

## 2. Goals & non-goals

**Goals**

- A lightweight, declarative **`DialogSpec`** — **pure data** — that the VM emits as its
  render state.
- A single **transition engine** that diffs the previously-emitted spec against the new
  one and drives the change, with one code path serving both animated and snapped renders.
- Reusable dialog *and* content elements, so new screens and orderings are data-driven, not new
  branches.
- Collapse `ViewState` to the spec plus a couple of transient animation flags.

**Non-goals**

- **Rewriting the VM's flow logic.** `OnboardingFlow` keeps deciding which
  dialog comes next. What changes is only the **output representation**: they build a
  `DialogSpec` instead of calling `setCurrentDialog(type)` and scattering fields across
  `ViewState`. This is a bounded change, and it *reduces* VM surface area.
- Spec-ifying the one-time **intro / outro** animations. They are preambles, not
  dialog↔dialog transitions. They stay as-is.

## 3. Current state (reference)

- **VM contract today:** `ViewState.currentDialog: PreOnboardingDialogType` + ~13 more
  fields; interaction callbacks (`onPrimaryCtaClicked`, `onAddressBarPositionOptionSelected`,
  …); `Command.SkipDialogAnimation`; re-entry snap gated by `hasAnimatedCurrentDialog`;
  re-entry intro guarded by `hasPlayedIntroAnimation`.
- **Dialog types:** `INITIAL`, `INITIAL_REINSTALL_USER`, `SYNC_RESTORE`, `COMPARISON_CHART`,
  `AI_COMPARISON_CHART`, `SKIP_ONBOARDING_OPTION`, `ADD_TO_DOCK`, `WIDGET_PROMPT`,
  `ADDRESS_BAR_POSITION`, `INPUT_SCREEN`, `INPUT_SCREEN_PREVIEW`, `QUICK_SETUP`.
- **Background already spec-like:** `OnboardingBackgroundAnimator` + `OnboardingBackgroundStep`
  (`Welcome`, `ComparisonChart`, `AddToDock`, `AddWidget`, `QuickSetup`, `AddressBar`,
  `InputType`) owns its own `transitionTo` / `snapTo`.
- **Content already include-based:** six stacked includes in
  `pre_onboarding_dax_dialog_cta_brand_design_update.xml`, toggled by visibility over
  shared `primaryCta` / `secondaryCta` and a `stepIndicator`.
- **Seed of the idea:** `ComparisonChartConfig` is a sealed class holding `@StringRes` /
  `@DrawableRes` content data, applied to a generic view. Precedent that **resource ids in
  VM-adjacent data are fine** in this codebase. v2 generalises it to the whole dialog.
- **Embellishments:** four Lottie views with bespoke enter/exit lifecycles —
  `welcomeScreenWalkingDax`, `bobbingDaxAnimation`, `bottomWingAnimation`, `leftWingAnimation`.

## 4. Proposed architecture

### 4.1 Layering

```
VM ── emits ──> DialogSpec (PURE DATA)   ← the render contract; also the ViewState
     flow logic (OnboardingFlow) builds the spec instead of setCurrentDialog(type)
        │
        ▼
DialogRenderer  (== the transition engine)
    diff(prevSpec, newSpec) → drive change; ONE path for animate + snap
        ├─ Background     → delegates to existing OnboardingBackgroundAnimator
        ├─ Embellishment  → None | WalkingDax | BobbingDax | BottomWing | LeftWing
        ├─ Chrome         → title, subtitle, primary/secondary CTA, step indicator
        └─ ContentBinder  → maps ContentSpec (data) → include visibility + bind + intro
```

The crucial split: **data lives in the spec (VM-side); view behavior lives in the renderer
(view-side).** The VM never touches `View` / `Animator` / `@IdRes`, so it stays unit-
testable.

### 4.2 `DialogSpec` — pure data, VM-emitted

```kotlin
data class DialogSpec(
    val background: OnboardingBackgroundStep,   // enum
    val embellishment: Embellishment,           // enum
    val title: TextSpec,                         // @StringRes + args
    val subtitle: TextSpec?,
    val content: ContentSpec,                    // sealed DATA (no View/Animator)
    val primaryCta: CtaSpec,                     // label + click-intent tag
    val secondaryCta: CtaSpec?,
    val stepIndicator: StepProgress?,            // page N of M, or null = hidden
)

sealed interface Embellishment { WalkingDax; BobbingDax; BottomWing; LeftWing; None }
```

`DialogSpec` is an immutable value. Its `equals` drives the transition diff (§4.4), so
every field is itself value-comparable — no lambdas, no view refs.

### 4.3 `ContentSpec` — pure data + renderer-side binder (Hybrid isolation)

`ContentSpec` is a sealed **data** type carrying only the **seed** values a screen needs.

```kotlin
sealed interface ContentSpec {
    // stateless
    data object Welcome : ContentSpec
    data class ComparisonChart(val config: ComparisonChartConfig) : ContentSpec
    data object AddToDock : ContentSpec       // "add to dock" promo (title-typing + body + looping video)
    data object WidgetPrompt : ContentSpec    // "add widget" promo (title-typing + body + image); Add / Skip CTAs

    // stateful — carry SEED values; live edits owned by a view-scoped holder,
    // result forwarded to the main VM on submit
    data class AddressBar(val initialPosition: OmnibarType, val showSplitOption: Boolean) : ContentSpec
    data class InputScreen(val initialWithAi: Boolean) : ContentSpec
    data class InputScreenPreview(val isSearchDefault: Boolean, val searchSuggestions: List<…>, val chatSuggestions: List<…>) : ContentSpec
    data class QuickSetup(val hideSetDefaultBrowserRow: Boolean, val hideAddWidgetRow: Boolean, val hideAddressBarRow: Boolean, val isReinstallUser: Boolean) : ContentSpec
}
```

Renderer side:

```kotlin
// view-layer; the ONLY place that knows includes/animators
fun ContentBinder.render(content: ContentSpec, view: View): Animator? = when (content) {
    Welcome                  -> { show(welcomeContent); null }
    is ComparisonChart       -> { populate(view, content.config); comparisonChartIntro(view) }
    AddToDock                -> { startAddToDockVideo(view); titleTypingIntro(view) }
    WidgetPrompt             -> { bindWidgetPrompt(view); titleTypingIntro(view) }
    is AddressBar            -> { bindAddressBar(view, content); positionPickerIntro(view) }
    is InputScreen           -> { bindInputScreen(view, content); null }
    is InputScreenPreview    -> { bindPreview(view, content); suggestionButtonsIntro(view) }
    is QuickSetup            -> { bindQuickSetup(view, content); changeBoundsIntro(view) }
}
```

Hybrid rationale: static screens are plain data; the three interactive
screens back a small **view-scoped state holder** for live edits, and only the *result*
crosses back to the VM on submit. The seed/live split is what lets the VM emit the whole
spec without a round-trip on every toggle.

### 4.4 `ViewState` collapse

```kotlin
// before — ~14 fields, half per-screen
// after
data class ViewState(
    val currentSpec: DialogSpec? = null,
    val hasPlayedIntroAnimation: Boolean = false,   // transient anim flag
    val hasAnimatedCurrentDialog: Boolean = false,  // transient anim flag
)
```

Where the old fields go:

| Old `ViewState` field | New home |
|---|---|
| `currentDialog: PreOnboardingDialogType?` | `currentSpec` (the type is now implied by `content` + chrome) |
| `selectedAddressBarPosition`, `showSplitOption` | `ContentSpec.AddressBar` (seed) + holder (live) |
| `inputScreenSelected` | `ContentSpec.InputScreen` (seed) + holder (live) |
| `inputScreenPreview*` (3 fields) | `ContentSpec.InputScreenPreview` |
| `hideSetDefaultBrowserRow`, `hideAddWidgetRow`, `hideAddressBarRow` | `ContentSpec.QuickSetup` |
| `currentPageNumber`, `maxPageCount` | `DialogSpec.stepIndicator: StepProgress?` |
| `isReinstallUser`, `isCustomAiOnboardingFlow` | private VM flow inputs; reflected in the built spec (e.g. CTA presence, `ComparisonChartConfig` variant), not exposed on `ViewState` |

### 4.5 Transition engine

Each transitionable element declares its own animators; the engine
diffs `prevSpec` vs `newSpec` element-by-element and a generic sequencer composes the result.

```kotlin
interface Transitionable { fun enter(): Animator?; fun exit(): Animator?; fun snapToEntered(); fun snapToExited() }
```

```
render(newSpec, animate):
    prev = currentSpec
    if !animate:                                  # snap path = SAME spec, zero choreography
        background.snapTo(newSpec.background)
        for element in {embellishment, content, chrome}: element.snapToEntered()
        return
    seq = Sequencer()
    if newSpec.background   != prev.background   : seq.parallel(background.transitionTo(newSpec.background))
    if newSpec.embellishment!= prev.embellishment: seq.parallel(prev.embellishment.exit()); seq.after(newSpec.embellishment.enter())
    if newSpec.content      != prev.content      : seq.after(swapContentVisibility(prev,newSpec), contentBinder.render(newSpec.content, view))
    seq.chrome(newSpec.title, newSpec.subtitle, newSpec.primaryCta, newSpec.secondaryCta, newSpec.stepIndicator)
    seq.start(); currentSpec = newSpec
```

This approach diffs elements, so re-ordering and new permutations need **zero new
  transition code**. bottom-wing → left-wing = `BottomWing.exit() ∥ LeftWing.enter()`, no
  branch, no table.

### 4.6 The headline win: snap is spec-derived

The snap path derives from the *same* `DialogSpec`, so `showDialogWithoutAnimation` (~530
lines) collapses into `snapToEntered/Exited()` calls. Animated and snapped descriptions can
no longer drift apart. Together with `configureDaxCta` shrinking to `render()`, this removes
the bulk of the 3k lines.

## 5. Migration plan (strangler)

1. **Land pure-data scaffolding** with no behavior change: `DialogSpec`, `ContentSpec`,
   `Embellishment`, `TextSpec`, `CtaSpec`; plus view-side `Transitionable`, `Sequencer`,
   `ContentBinder`, `DialogRenderer`.
2. **Migrate one dialog type at a time.** For each: (a) have the VM flow build its
   `DialogSpec` instead of `setCurrentDialog(type)`, folding that dialog's `ViewState`
   fields into the spec; (b) route it through `DialogRenderer`; (c) delete its branch from
   both `configureDaxCta` and `showDialogWithoutAnimation`. Order, simplest first:
   `INITIAL` / `INITIAL_REINSTALL_USER` / `SYNC_RESTORE` → `COMPARISON_CHART` /
   `AI_COMPARISON_CHART` → `ADD_TO_DOCK` / `WIDGET_PROMPT` → `SKIP_ONBOARDING_OPTION` →
   `ADDRESS_BAR_POSITION` → `INPUT_SCREEN` → `INPUT_SCREEN_PREVIEW` → `QUICK_SETUP`.
3. **Extract the three state holders** as their stateful contents migrate; result → VM on
   submit (VM callback surface unchanged).
4. **Delete** the legacy `when` blocks and the now-dead `ViewState` fields once all types
   are migrated. `ViewState` reaches its §4.4 shape.
5. **Parity gate throughout:** release-blocker Maestro onboarding flows + VM unit tests
   green at every step. Intro/outro untouched.

Each step is independently shippable and reversible. `currentDialog` can coexist with
`currentSpec` during migration (VM sets both) so half-migrated builds stay coherent.

## 6. Risks & open questions

- **VM now references semantic view vocabulary** (`Embellishment`, `OnboardingBackgroundStep`,
  `ContentSpec`). Mild layering question. Mitigation: these are *semantic* product choices
  ("this screen walks the Dax", "page 2 of 3"), which the VM already decides implicitly via
  `currentDialog`. Encoding them as structured data instead of an opaque enum is not new
  coupling — it is the same decision made explicit. All types stay view-free (no `View` /
  `Animator`).
- **Spec equality must be diff-stable.** `DialogSpec` drives the transition diff by
  `equals`, so it must contain only value-comparable data (no lambdas, no view refs, stable
  list identity for suggestions). Enforced by keeping it a pure `data class` tree.
- **Re-emission must not replay entries.** Intra-screen edits are owned by the holder and do
  **not** produce a new `DialogSpec`, so the renderer only ever sees a new spec on real
  dialog transitions. Guard: the VM emits `DialogSpec` only from flow transitions;
  `hasAnimatedCurrentDialog` still gates snap-vs-animate on re-entry.
- **Choreography edge cases.** Comparison chart: fresh-entry `snapTo(ComparisonChart)` vs
  morph `transitionTo(ComparisonChart)`; QuickSetup `ChangeBounds`. The sequencer must let
  one element enter snapped while others animate. Feasible; main thing a thin POC
  should de-risk.
- **Custom AI copy.** `isCustomAiOnboardingFlow` must be threaded into spec construction
  (e.g. `ComparisonChartConfig` variant, CTA copy). Now a private flow input rather than a
  `ViewState` field — must not be dropped in the fold.
- **`SKIP_ONBOARDING_OPTION` content.** Its include is not among the six named; it appears
  to fade-swap over an existing include. Confirm which view before writing its `ContentSpec`.
- **State-holder lifecycle.** Holders must survive config change / mid-flow re-entry.
  Simplest safe option: keep the live value inside the VM (a small dedicated field) and let
  the holder be a thin facade, rather than a separately retained object. Decide during the
  stateful-content step.
- **Skip-animation path.** `Command.SkipDialogAnimation` maps to `Sequencer.skipToEnd()`,
  equivalent to the snap render.
- **`preventWidows` non-breaking spaces.** Brand-design titles insert U+00A0 before the last
  word; `TextSpec` rendering must preserve it (also matters for Maestro text asserts).
- **Scope creep.** The engine stays dumb about specific dialogs. If a dialog needs behavior
  the engine can't express, the escape hatch is a richer `ContentSpec` / `Transitionable`,
  never a `when(dialogType)` back in the engine.

## 7. Appendix — current dialog → element mapping

Derived from `configureDaxCta`. The VM flow encodes this as `DialogSpec` construction.

| Dialog type | Content | Embellishment | Background | Notes |
|---|---|---|---|---|
| INITIAL / INITIAL_REINSTALL_USER / SYNC_RESTORE | Welcome | WalkingDax | Welcome | optional secondary CTA (skip/restore) |
| COMPARISON_CHART / AI_COMPARISON_CHART | ComparisonChart | BottomWing | ComparisonChart | fresh-entry snap vs morph; intro (title type + table + checks); custom-AI copy variant |
| ADD_TO_DOCK | AddToDock (title-typing + body + video) | none (wings dismissed) | AddToDock | Continue CTA; experiment-gated |
| WIDGET_PROMPT | WidgetPrompt (title-typing + body + image) | LeftWing | AddWidget | Add / Skip CTAs; experiment-gated. The system add-widget flow itself is a `Command`, not a bubble dialog |
| SKIP_ONBOARDING_OPTION | *fade-swap (confirm)* | — | — | fade-out → swap → fade-in |
| ADDRESS_BAR_POSITION | AddressBar | BobbingDax (in) | AddressBar | position picker; split option gated |
| INPUT_SCREEN | InputScreen | BobbingDax (out) + LeftWing (in) | InputType | AI toggle picker |
| INPUT_SCREEN_PREVIEW | InputScreenPreview | wings hidden | (prev) | keyboard input + suggestion-button stagger |
| QUICK_SETUP | QuickSetup | BottomWing | QuickSetup | ChangeBounds + fade; row visibility from flags |
