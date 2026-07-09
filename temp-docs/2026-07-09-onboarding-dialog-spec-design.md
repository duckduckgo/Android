# Onboarding Dialog Spec — Feasibility Design

**Date:** 2026-07-09
**Status:** Design / feasibility exploration (no production code in this pass)
**Author:** Łukasz Paczos

## 1. Problem

`BrandDesignUpdateWelcomePage` is ~3k lines and growing. It hosts every pre-onboarding
dialog (welcome, comparison chart, address bar, input screen, quick setup, etc.) and
imperatively hand-wires each one. Two forces make this unsustainable:

- The **Custom AI flow** already re-orders screens.
- The parent project will introduce more screen **permutations, including re-ordering**.

The page was tuned for a single, fixed path. Every new ordering multiplies the special
cases it has to reason about.

### Where the size actually comes from

The page holds two large `when(dialogType)` blocks that describe the *same* per-dialog
state twice:

| Function | Lines | Role |
|---|---|---|
| `configureDaxCta` | ~803–1402 (~600) | animated path — transitions between dialogs |
| `showDialogWithoutAnimation` | ~1515–2046 (~530) | snapped path — mid-flow re-entry / config change |

Each branch hand-wires the same five things: content-include visibility, embellishment
enter/exit (WalkingDax / BobbingDax / BottomWing / LeftWing), background step, chrome
(title / subtitle / primary + secondary CTA / step indicator), and a bespoke content
intro animation. Because the animated and snapped descriptions are separate code, every
dialog is described twice and every new ordering touches both.

## 2. Goals & non-goals

**Goals**

- A lightweight, declarative **`DialogSpec`** that describes a dialog's content
  programmatically.
- A single **transition engine** that diffs the outgoing spec against the incoming one
  and drives the change — one code path serving both the animated and snapped renders.
- Reusable dialog *and* content elements, so new screens and new orderings are data, not
  new branches.
- Dramatically reduce duplication in the page.

**Non-goals (this pass)**

- Rewriting the ViewModel. It already emits `currentDialog: PreOnboardingDialogType` plus
  interaction state through a clean flow abstraction (`LegacyFlow` / `OrchestratorFlow`).
  The spec is a **pure view-layer** concern.
- Spec-ifying the one-time **intro / outro** animations (logo, title slide-up, background
  reveal). They are preambles, not dialog↔dialog transitions. They stay as-is initially.
- Shipping a migration. This document establishes feasibility and the target shape.

## 3. Current state (reference)

Facts the design builds on, from the current code:

- **VM contract:** `ViewState.currentDialog: PreOnboardingDialogType`, plus interaction
  state (`selectedAddressBarPosition`, `inputScreenSelected`, `showSplitOption`,
  `isCustomAiOnboardingFlow`, `currentPageNumber` / `maxPageCount`, …). Animation-skip
  arrives as `Command.SkipDialogAnimation`; re-entry snap is gated by
  `hasAnimatedCurrentDialog`.
- **Dialog types:** `INITIAL`, `INITIAL_REINSTALL_USER`, `SYNC_RESTORE`, `COMPARISON_CHART`,
  `AI_COMPARISON_CHART`, `SKIP_ONBOARDING_OPTION`, `ADDRESS_BAR_POSITION`, `INPUT_SCREEN`,
  `INPUT_SCREEN_PREVIEW`, `QUICK_SETUP`.
- **Background is already spec-like:** `OnboardingBackgroundAnimator` +
  `OnboardingBackgroundStep` (`Welcome`, `QuickSetup`, `AddressBar`, `InputType`,
  `ComparisonChart`) owns its own `transitionTo` / `snapTo`. The spec only names a target
  step.
- **Content is already include-based:** `pre_onboarding_dax_dialog_cta_brand_design_update.xml`
  stacks six content includes (`welcomeContent`, `comparisonChartContent`,
  `addressBarContent`, `inputScreenContent`, `inputScreenPreviewContent`,
  `reinstallerQuickSetupContent`) toggled by visibility, over shared `primaryCta` /
  `secondaryCta` and a `stepIndicator`.
- **Seed of the idea already exists:** `ComparisonChartConfig` is a sealed class that
  declares a screen's content (title, CTA text, header icon, rows) and is applied to a
  generic view. This design generalises that pattern to the whole dialog.
- **Embellishments** live in the root layout as four Lottie views with bespoke
  enter/exit lifecycles: `welcomeScreenWalkingDax`, `bobbingDaxAnimation`,
  `bottomWingAnimation`, `leftWingAnimation`.

## 4. Proposed architecture

### 4.1 Layering

```
VM (unchanged) ── emits ──> currentDialog: PreOnboardingDialogType + interaction state
        │
        ▼
DialogSpecRegistry :  (type, state) ── pure fn ──> DialogSpec
        │
        ▼
DialogRenderer  (== the transition engine)
    diff(currentSpec, nextSpec) → drive change; ONE path for animate + snap
        ├─ Background     → delegates to existing OnboardingBackgroundAnimator
        ├─ Embellishment  → None | WalkingDax | BobbingDax | BottomWing | LeftWing
        ├─ Chrome         → title, subtitle, primary/secondary CTA, step indicator
        └─ Content        → ContentSpec (sealed): bind + playIntro / snap
```

The VM keeps emitting an enum. A **registry** maps `(type, state) → DialogSpec` as a pure
function. The page hands successive specs to the **renderer**, which owns all view work.
This keeps the VM decoupled from view concerns and means the registry is trivially
unit-testable.

### 4.2 `DialogSpec`

```kotlin
data class DialogSpec(
    val background: OnboardingBackgroundStep,
    val embellishment: Embellishment,
    val title: TextSpec,
    val subtitle: TextSpec?,
    val content: ContentSpec,
    val primaryCta: CtaSpec,
    val secondaryCta: CtaSpec?,
    val stepIndicator: StepProgress?,   // "page N of M" or null = hidden
)

sealed interface Embellishment {
    data object None : Embellishment
    data object WalkingDax : Embellishment
    data object BobbingDax : Embellishment
    data object BottomWing : Embellishment
    data object LeftWing : Embellishment
}
```

`TextSpec` carries a string res (plus args) — importantly it must preserve the
`preventWidows` non-breaking-space treatment the brand-design titles rely on. `CtaSpec`
carries label + visibility + a click intent tag the page maps to the existing VM calls
(`onPrimaryCtaClicked` / `onSecondaryCtaClicked`).

### 4.3 `ContentSpec` — Hybrid isolation

Content is a sealed type. Each variant knows its layout include and how to bind + animate
itself. **Stateless** contents are pure sub-specs; **stateful** contents back a
view-scoped state holder whose result is forwarded to the main VM on submit.

```kotlin
sealed interface ContentSpec {
    val includeId: Int                 // which stacked include this drives
    fun bind(view: View, res: Resources)
    fun playIntro(view: View): Animator?   // bespoke; null = nothing to animate
    fun snap(view: View)                   // end-state, no animation

    // stateless
    data class Welcome(...) : ContentSpec
    data class ComparisonChart(val config: ComparisonChartConfig) : ContentSpec

    // stateful — own a view-scoped holder; result -> main VM on submit
    data class AddressBar(val holder: AddressBarStateHolder) : ContentSpec
    data class InputScreen(val holder: InputScreenStateHolder) : ContentSpec
    data class QuickSetup(val holder: QuickSetupStateHolder) : ContentSpec
}
```

Rationale for Hybrid (chosen over "all page-owned" and "VM-per-everything"):

- Static screens (welcome, comparison chart) hold no interaction state — a plain sub-spec
  is enough, no new lifecycle infra.
- The three interactive screens genuinely own transient selection state
  (address-bar position, AI toggle, quick-setup rows). A small view-scoped **state holder**
  isolates that state from `BrandDesignUpdatePageViewModel`; only the *result* crosses back
  on submit. This is what stops the main VM from accreting per-screen fields.
- We avoid standing up a full DI-scoped ViewModel for screens that don't need one.

The bespoke content intro animations (comparison-chart title typing + table fade + check
icons, suggestion-button stagger, input-screen keyboard) stay **inside** their
`ContentSpec` variant. The engine sequences them; it never tries to generalise them.

### 4.4 Transition engine (Approach C — element-self-describing + sequencer)

Each transitionable element declares its own animators; the engine diffs specs
element-by-element and a generic sequencer composes the result.

```kotlin
interface Transitionable {
    fun enter(): Animator?
    fun exit(): Animator?
    fun snapToEntered()
    fun snapToExited()
}
```

Render algorithm:

```
render(next: DialogSpec, animate: Boolean):
    prev = currentSpec
    if !animate:
        # snap path — SAME spec, zero choreography
        background.snapTo(next.background)
        for each element in {embellishment, content, chrome}:
            element.snapToEntered()   # prev's leftovers snapToExited()
        return

    # animated path — diff, then sequence
    seq = Sequencer()
    if next.background != prev.background:  seq.parallel(background.transitionTo(next.background))
    if next.embellishment != prev.embellishment:
        seq.parallel(prev.embellishment.exit())     # e.g. BottomWing.exit()
        seq.after(next.embellishment.enter())        # e.g. LeftWing.enter()
    if next.content != prev.content:
        seq.after(swapContentVisibility(prev, next), next.content.playIntro())
    seq.chrome(next.title, next.subtitle, next.primaryCta, next.secondaryCta, next.stepIndicator)
    seq.start()
    currentSpec = next
```

Why C over the alternatives:

- **Rejected — pairwise matrix** `Map<Pair<From,To>, Transition>`: it is exactly the O(n²)
  combinatorial explosion the permutations project is trying to escape. A new ordering
  would demand new table entries.
- **Approach A (fixed-phase timeline)** is the same diff idea with a dumber, hardcoded
  sequencer. It is an acceptable fallback if choreography turns out fully uniform; C
  subsumes it.
- **Approach C** diffs elements, so re-ordering and new permutations need **zero new
  transition code** — an element only has to describe its own enter/exit once.

Worked example — bottom-wing dialog → left-wing dialog: the engine sees
`embellishment: BottomWing → LeftWing`, emits `BottomWing.exit() ∥ LeftWing.enter()`. No
table entry, no branch. Adding a screen between them changes only the registry, not the
engine.

### 4.5 The headline win: snap is spec-derived

Because the snap path (§4.4, `!animate`) is derived from the *same* `DialogSpec`,
`showDialogWithoutAnimation` (~530 lines) collapses into `element.snapToExited/Entered()`
calls. Combined with `configureDaxCta` becoming the small `render()` above, this removes
the bulk of the 3k lines and — more importantly — makes it impossible for the animated and
snapped descriptions of a dialog to drift apart.

## 5. Migration plan (strangler)

1. **Land the scaffolding** behind the existing page: `DialogSpec`, `ContentSpec`,
   `Embellishment`, `Transitionable`, `Sequencer`, `DialogRenderer`, `DialogSpecRegistry`.
   No behaviour change yet.
2. **Move one dialog type at a time** into the registry + renderer, deleting its branch
   from `configureDaxCta` and `showDialogWithoutAnimation`. Suggested order, simplest
   first: `INITIAL` / `INITIAL_REINSTALL_USER` / `SYNC_RESTORE` → `COMPARISON_CHART` /
   `AI_COMPARISON_CHART` → `SKIP_ONBOARDING_OPTION` → `ADDRESS_BAR_POSITION` →
   `INPUT_SCREEN` → `INPUT_SCREEN_PREVIEW` → `QUICK_SETUP`.
3. **Extract state holders** for the three stateful contents as they are migrated,
   forwarding results to the VM on submit (VM interface unchanged).
4. **Delete** the now-empty legacy `when` blocks once all types are migrated.
5. **Parity gate throughout:** existing Maestro onboarding flows (release-blocker tagged)
   plus VM unit tests must stay green at every step. Intro/outro untouched.

Each step is independently shippable and reversible.

## 6. Risks & open questions

- **Choreography edge cases.** Comparison chart has two entries today — fresh-entry
  `snapTo(ComparisonChart)` vs morph `transitionTo(ComparisonChart)` — and QuickSetup uses
  a `ChangeBounds`. The sequencer must express "this element enters snapped while others
  animate." Feasible in C (per-element decision) but needs care; it is the main thing a
  thin POC should de-risk.
- **Custom AI copy.** `ComparisonChartConfig` already branches copy on `isCustomAiCopy`;
  the registry must thread `isCustomAiOnboardingFlow` from `ViewState` into spec
  construction. Straightforward but must not be forgotten.
- **`SKIP_ONBOARDING_OPTION` content.** Its content include is not among the six named in
  the CTA layout (it appears to fade-swap over an existing include). Confirm which view it
  drives before writing its `ContentSpec`.
- **State-holder lifecycle.** View-scoped holders must survive config change and mid-flow
  re-entry the way the VM already seeds `hasPlayedIntroAnimation` /
  `hasAnimatedCurrentDialog`. Simplest safe option: hold state in the existing VM
  `ViewState` and let the holder be a thin read/write facade, rather than a separately
  retained object. Decide during the stateful-content step.
- **Skip-animation path.** `Command.SkipDialogAnimation` must map to "finish in-flight
  sequencer at end-state," equivalent to the snap render. The `Sequencer` needs a
  `skipToEnd()`.
- **`preventWidows` non-breaking spaces.** Brand-design titles insert U+00A0 before the
  last word; `TextSpec` rendering must preserve it (also matters for Maestro text asserts).
- **Scope creep.** The engine must stay dumb about specific dialogs. If a dialog needs
  behaviour the engine can't express generically, the escape hatch is a richer
  `ContentSpec` / `Transitionable`, never a `when(dialogType)` back in the engine.

## 7. Appendix — current dialog → element mapping

Derived from `configureDaxCta`; the registry encodes this table as data.

| Dialog type | Content include | Embellishment | Background step | Notes |
|---|---|---|---|---|
| INITIAL / INITIAL_REINSTALL_USER / SYNC_RESTORE | welcome | WalkingDax | Welcome | optional secondary CTA (skip/restore) |
| COMPARISON_CHART / AI_COMPARISON_CHART | comparisonChart | BottomWing | ComparisonChart | fresh-entry snap vs morph transition; content intro (title type + table + checks) |
| SKIP_ONBOARDING_OPTION | *fade-swap (confirm)* | — | — | fade-out → swap → fade-in |
| ADDRESS_BAR_POSITION | addressBar | BobbingDax (in) | AddressBar | position picker; split option gated |
| INPUT_SCREEN | inputScreen | BobbingDax (out) + LeftWing (in) | InputType | AI toggle picker |
| INPUT_SCREEN_PREVIEW | inputScreenPreview | wings hidden | (prev) | keyboard input + suggestion-button stagger |
| QUICK_SETUP | reinstallerQuickSetup | BottomWing | QuickSetup | ChangeBounds + fade; row visibility from flags |
