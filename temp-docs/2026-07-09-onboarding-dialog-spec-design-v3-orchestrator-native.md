# Onboarding Dialog Spec — Feasibility Design (v3: orchestrator-native)

**Date:** 2026-07-09
**Status:** Design / feasibility exploration (no production code in this pass)
**Author:** Łukasz Paczos

## 1. Problem

`BrandDesignUpdateWelcomePage` is ~3k lines and growing. It hosts every pre-onboarding
dialog and imperatively hand-wires each one. Two forces make this unsustainable:

- The **Custom AI flow** already re-orders screens.
- The parent project will introduce more screen **permutations, including re-ordering**.

### Where the size comes from

**(a) Duplicated view logic.** Two large `when(dialogType)` blocks in the page describe the
*same* per-dialog state twice:

| Function | Lines | Role |
|---|---|---|
| `configureDaxCta` | ~803–1402 (~600) | animated path — transitions between dialogs |
| `showDialogWithoutAnimation` | ~1515–2046 (~530) | snapped path — mid-flow re-entry / config change |

Each branch hand-wires content-include visibility, embellishment enter/exit, background
step, chrome (title / subtitle / CTAs / step indicator), and a bespoke content intro
animation. Because animated and snapped are separate code, every dialog is described twice
and every new ordering touches both.

**(b) A triple representation of "which dialog".** With the orchestrator driving onboarding,
the same dialog is modelled three times on its way to the screen:

```
NewUserOnboardingActivityDialog   (orchestrator render intent + seed data, built in the plan provider)
  └─ BrandDesignUpdatePageViewModel.applyDialog() maps it to →  PreOnboardingDialogType + scattered ViewState fields
        └─ configureDaxCta's when maps that to →  actual views
```

Every new screen means touching all three layers.

## 2. Starting assumption

This design assumes **orchestrator-only** onboarding. The
`linearOnboardingOrchestratorFeature` kill switch, the in-VM `LegacyFlow` state machine, and
the VM's flow-selection are **removed upfront, separately**, before this work begins. v3
does not re-derive or migrate those; it takes an orchestrator-only VM as its starting point.

## 3. Goals & non-goals

**Goals**

- A lightweight, declarative **`DialogSpec`** — pure data — that a step resolves to and the
  VM emits as its render state.
- Fold the orchestrator's per-step render hook and the `DialogSpec` into **one
  representation**, deleting the `applyDialog` translation layer and `PreOnboardingDialogType`.
- A single **transition engine** that diffs the previously-emitted spec against the new one
  and drives the change, with one code path for both animated and snapped renders.
- Make the plan provider the single self-describing source per step, so **re-ordering /
  permuting screens is a list edit**, not new branches.
- Collapse `ViewState` to the spec plus a couple of transient animation flags.

**Non-goals**

- Removing `LegacyFlow` / the feature flag (done upfront — see §2).
- Rewriting the orchestrator, its plan/step model, or its transition semantics. Steps keep
  deciding what comes next; only their **render output** changes shape.
- Spec-ifying the one-time **intro / outro** animations. They are preambles, not
  dialog↔dialog transitions, and stay as-is (a side-effect presentation, see §5.2).

## 4. Current state (reference)

- **Step model:** `NewUserOnboardingActivityStep` already carries everything a step needs:
  `id`, `precondition` (whether it shows), `transition` (what event → where next),
  `pixelName` (shown-pixel, fired by the plan's `firingShownPixels()` wrapper on the
  `Presented` event), `showsStepIndicator`, and `resolveDialog: suspend () ->
  NewUserOnboardingActivityDialog` — its doc-comment: *"the VM reads it to know what to
  present."*
- **Render intent is already seed data:** `NewUserOnboardingActivityDialog` variants already
  carry per-dialog seeds — `AddressBarPosition(showSplitOption)`,
  `InputScreenPreview(isSearchDefault)`, `QuickSetup(showSplitOption, hideSetDefaultBrowserRow,
  hideAddWidgetRow, isReinstallUser)`, `IntroAnimation(withDuckAi)`.
- **Plan provider builds those closures:** `NewUserOnboardingPlanProvider` declares one
  factory per step and composes `steps = listOf(...)`. It already forks flows
  (`buildDefaultPlan` vs `buildCustomAiPlan`) as different step lists, and already resolves
  async seed inside `resolveDialog` (e.g. `quickSetupStep` reads `isDefaultBrowser()` /
  `hasInstalledWidgets`; `addressBarPositionStep` reads `isSplitOmnibarEnabled()`).
- **Step indicator is position-derived:** `LinearOnboardingState.InProgress.stepIndicatorProgress()`
  computes `StepProgress(current, total)` from the position of `showsStepIndicator` steps in
  the plan — no hardcoded page numbers.
- **Background is already spec-like:** `OnboardingBackgroundAnimator` + `OnboardingBackgroundStep`
  (`Welcome`, `QuickSetup`, `AddressBar`, `InputType`, `ComparisonChart`) owns its own
  `transitionTo` / `snapTo`.
- **Content is already include-based:** six stacked includes in
  `pre_onboarding_dax_dialog_cta_brand_design_update.xml`, toggled by visibility over shared
  `primaryCta` / `secondaryCta` and a `stepIndicator`.
- **Seed of the idea exists:** `ComparisonChartConfig` is a sealed class holding `@StringRes`
  / `@DrawableRes` content data applied to a generic view — precedent that resource ids in
  render-intent data are fine here. v3 generalises it to the whole dialog.
- **Embellishments:** four Lottie views with bespoke enter/exit lifecycles —
  `welcomeScreenWalkingDax`, `bobbingDaxAnimation`, `bottomWingAnimation`, `leftWingAnimation`.

## 5. Proposed architecture

### 5.1 Layering

```
Orchestrator step ── resolveDialog() ──> NewUserOnboardingActivityDialog
        │                                   ├─ Dialog(spec: DialogSpec)     → rendered in the bubble
        │                                   └─ IntroAnimation / NotificationPermission / DefaultBrowserPrompt → side-effect commands
        ▼
VM: emit spec to ViewState.currentSpec (+ enrich with step-indicator position)
        │
        ▼
DialogRenderer  (== the transition engine)
    diff(prevSpec, newSpec) → drive change; ONE path for animate + snap
        ├─ Background     → delegates to existing OnboardingBackgroundAnimator
        ├─ Embellishment  → None | WalkingDax | BobbingDax | BottomWing | LeftWing
        ├─ Chrome         → title, subtitle, primary/secondary CTA, step indicator
        └─ ContentBinder  → maps ContentSpec (data) → include visibility + bind + intro
```

The fold: the step's render hook and the render `DialogSpec` become the **same object**. The
VM stops translating; it forwards. The plan provider becomes the one place that says, per
step, *when / what / next / telemetry / indicator*.

### 5.2 Fold `NewUserOnboardingActivityDialog`

The ten bubble variants collapse into a single `Dialog(spec)`. Only the genuine non-bubble
side-effects stay as their own variants (they are not dax-bubble dialogs — they play the
intro Lottie, request a runtime permission, or show the system default-browser dialog):

```kotlin
sealed interface NewUserOnboardingActivityDialog {
    data class Dialog(val spec: DialogSpec) : NewUserOnboardingActivityDialog   // rendered in the dax bubble

    data class IntroAnimation(val withDuckAi: Boolean) : NewUserOnboardingActivityDialog
    data object NotificationPermission : NewUserOnboardingActivityDialog
    data object DefaultBrowserPrompt : NewUserOnboardingActivityDialog
}
```

### 5.3 `DialogSpec` — pure data

```kotlin
data class DialogSpec(
    val background: OnboardingBackgroundStep,   // enum
    val embellishment: Embellishment,           // enum
    val title: TextSpec,                         // @StringRes + args
    val subtitle: TextSpec?,
    val content: ContentSpec,                    // sealed DATA (no View/Animator)
    val primaryCta: CtaSpec,                     // label + click-intent tag
    val secondaryCta: CtaSpec?,
    val stepIndicator: StepProgress? = null,     // enriched by the VM from plan position; null = hidden
)

sealed interface Embellishment { WalkingDax; BobbingDax; BottomWing; LeftWing; None }
```

`DialogSpec` is an immutable value; its `equals` drives the transition diff (§5.7), so every
field is value-comparable — no lambdas, no view refs. `View` / `Animator` / `@IdRes` never
appear here, so the spec and the plan provider stay unit-testable.

### 5.4 `ContentSpec` — pure data + renderer-side binder (Hybrid isolation)

`ContentSpec` is a sealed **data** type carrying only the **seed** values a screen needs.
`includeId` / `bind` / `playIntro` live in the renderer, not here.

```kotlin
sealed interface ContentSpec {
    // stateless
    data object Welcome : ContentSpec
    data class ComparisonChart(val config: ComparisonChartConfig) : ContentSpec

    // stateful — carry SEED values; live edits owned by a view-scoped holder,
    // result forwarded to the VM on submit
    data class AddressBar(val initialPosition: OmnibarType, val showSplitOption: Boolean) : ContentSpec
    data class InputScreen(val initialWithAi: Boolean) : ContentSpec
    data class InputScreenPreview(val isSearchDefault: Boolean, val searchSuggestions: List<…>, val chatSuggestions: List<…>) : ContentSpec
    data class QuickSetup(val hideSetDefaultBrowserRow: Boolean, val hideAddWidgetRow: Boolean, val isReinstallUser: Boolean) : ContentSpec
}
```

```kotlin
// view-layer; the ONLY place that knows includes/animators
fun ContentBinder.render(content: ContentSpec, view: View): Animator? = when (content) {
    Welcome               -> { show(welcomeContent); null }
    is ComparisonChart    -> { populate(view, content.config); comparisonChartIntro(view) }
    is AddressBar         -> { bindAddressBar(view, content); positionPickerIntro(view) }
    is InputScreen        -> { bindInputScreen(view, content); null }
    is InputScreenPreview -> { bindPreview(view, content); suggestionButtonsIntro(view) }
    is QuickSetup         -> { bindQuickSetup(view, content); changeBoundsIntro(view) }
}
```

Hybrid rationale: static screens are plain data; the three interactive screens
(address bar, input screen, quick setup) back a small **view-scoped state holder** for live
edits, and only the *result* crosses back to the VM on submit. The seed/live split lets the
step emit a whole spec without a round-trip on every toggle, and keeps intra-screen edits
from producing a new spec (so the renderer only ever sees a new spec on a real transition).

### 5.5 `DialogSpecs` catalog — reusable specs, co-located with the plan

A small factory of reusable spec builders. This is the "registry", living next to the plan
provider that consumes it. Each step's `resolveDialog` picks a spec; async seed is resolved
in the closure exactly as today:

```kotlin
object DialogSpecs {
    fun welcome(secondaryCta: CtaSpec? = null) = DialogSpec(
        background = Welcome, embellishment = WalkingDax,
        title = TextSpec(R.string.onboardingWelcomeTitle), subtitle = null,
        content = ContentSpec.Welcome,
        primaryCta = CtaSpec(R.string.…, Intent.Continue), secondaryCta = secondaryCta,
    )
    fun comparisonChart() = DialogSpec(
        background = ComparisonChart, embellishment = BottomWing,
        title = TextSpec(R.string.…), subtitle = null,
        content = ContentSpec.ComparisonChart(ComparisonChartConfig.Browser(isCustomAiCopy = false)),
        primaryCta = CtaSpec(R.string.…, Intent.Continue), secondaryCta = null,
    )
    fun aiComparisonChart() = /* content = ComparisonChartConfig.Ai, … */
    fun addressBar(initialPosition: OmnibarType, showSplitOption: Boolean) = /* embellishment = BobbingDax, … */
    fun quickSetup(showSplitOption: Boolean, hideSetDefaultBrowserRow: Boolean, hideAddWidgetRow: Boolean, isReinstallUser: Boolean) = /* … */
    // welcome/comparison/input/preview/skip/syncRestore/initialReinstall …
}
```

Step factory, after the fold:

```kotlin
private fun comparisonChartStep() = NewUserOnboardingActivityStep(
    id = COMPARISON_CHART, pixelName = ONBOARDING_SET_DEFAULT, showsStepIndicator = true,
    resolveDialog = { NewUserOnboardingActivityDialog.Dialog(DialogSpecs.comparisonChart()) },
    transition = { event -> /* unchanged */ },
)

private fun quickSetupStep(ctx: NewUserOnboardingPlanContext) = NewUserOnboardingActivityStep(
    id = QUICK_SETUP, pixelName = ONBOARDING_QUICK_SETUP,
    resolveDialog = {
        val (isDefault, hasWidget) = withContext(dispatchers.io()) {
            defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
        }
        NewUserOnboardingActivityDialog.Dialog(
            DialogSpecs.quickSetup(
                showSplitOption = isSplitOmnibarEnabled(),
                hideSetDefaultBrowserRow = isDefault,
                hideAddWidgetRow = hasWidget,
                isReinstallUser = ctx.isReinstall,
            ),
        )
    },
    transition = { event -> /* unchanged */ },
)
```

Custom-AI copy is chosen by *which builder the plan lists* (`buildCustomAiPlan` uses
`aiComparisonChartStep()` + custom copy). No runtime `isCustomAiOnboardingFlow` flag is
threaded anywhere.

### 5.6 VM shrink and `ViewState` collapse

`observeOrchestratorState` reduces to a four-branch dispatch; `applyDialog`,
`setCurrentDialog`, `setInputScreenPreviewDialog`, and `fireDialogShownPixel` are gone:

```kotlin
when (val d = step.resolveDialog()) {
    is Dialog               -> _viewState.update { it.copy(currentSpec = d.spec.withIndicator(state.stepIndicatorProgress())) }
    is IntroAnimation       -> command(PlayIntroAnimation(withDuckAi = d.withDuckAi))
    NotificationPermission  -> startNotificationPermissionFlow()
    DefaultBrowserPrompt    -> showDefaultBrowserPromptOrAdvance()
}
```

```kotlin
data class ViewState(
    val currentSpec: DialogSpec? = null,
    val hasPlayedIntroAnimation: Boolean = false,   // transient anim flag
    val hasAnimatedCurrentDialog: Boolean = false,  // transient anim flag; still gates snap-vs-animate on re-entry
)
```

All the old per-screen fields (`selectedAddressBarPosition`, `inputScreenSelected`,
`showSplitOption`, `inputScreenPreview*`, `hide*Row`, `isReinstallUser`,
`isCustomAiOnboardingFlow`, `currentPageNumber` / `maxPageCount`, `currentDialog`) fold into
the spec / its `ContentSpec` / `stepIndicator`, or disappear (custom-AI is plan-selected).

### 5.7 Transition engine (Approach C — element-self-describing + sequencer)

Each transitionable element declares its own animators; the engine diffs `prevSpec` vs
`newSpec` element-by-element and a generic sequencer composes the result.

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
    if newSpec.background    != prev.background   : seq.parallel(background.transitionTo(newSpec.background))
    if newSpec.embellishment != prev.embellishment: seq.parallel(prev.embellishment.exit()); seq.after(newSpec.embellishment.enter())
    if newSpec.content       != prev.content      : seq.after(swapContentVisibility(prev,newSpec), contentBinder.render(newSpec.content, view))
    seq.chrome(newSpec.title, newSpec.subtitle, newSpec.primaryCta, newSpec.secondaryCta, newSpec.stepIndicator)
    seq.start(); currentSpec = newSpec
```

- **Rejected — pairwise matrix** `Map<Pair<From,To>, Transition>`: O(n²), the exact
  combinatorial explosion the permutations project must escape.
- **Fixed-phase timeline** is Approach C with a dumber sequencer; acceptable fallback if
  choreography turns out fully uniform.
- **Approach C (chosen)** diffs elements, so re-ordering and new permutations need **zero new
  transition code**. bottom-wing → left-wing = `BottomWing.exit() ∥ LeftWing.enter()`, no
  branch, no table.

### 5.8 The headline win: snap is spec-derived

The snap path derives from the *same* `DialogSpec`, so `showDialogWithoutAnimation` (~530
lines) collapses into `snapToEntered/Exited()` calls, and `configureDaxCta` shrinks to
`render()`. Animated and snapped descriptions can no longer drift apart.

## 6. What collapses

**Removed upfront, separately (see §2):** `linearOnboardingOrchestratorFeature` flag,
`LegacyFlow` (~180 lines), VM flow-selection.

**Removed by this design:**

- `configureDaxCta` + `showDialogWithoutAnimation` (~1130 lines of duplicated per-dialog
  `when`) → one `render()` diff + per-element/per-content code.
- `applyDialog()` when-block (~55 lines).
- `PreOnboardingDialogType` enum + `setCurrentDialog` / `setInputScreenPreviewDialog`.
- `fireDialogShownPixel` when-block — shown-pixels already ride `step.pixelName` +
  `firingShownPixels()`.
- Custom-AI flag threading — flow is plan-selected at `buildRootPlan`.
- ~11 `ViewState` fields → `currentSpec` + 2 transient flags.

## 7. The payoff: reorder / permute is a list edit

A screen is now fully described by its step (`precondition`, `transition`, `pixelName`,
`showsStepIndicator`, `resolveDialog → DialogSpecs.x()`). Re-ordering or introducing a
permutation means editing the `steps = listOf(...)` in the plan provider — precisely what
`buildDefaultPlan` and `buildCustomAiPlan` already do as different lists. Because the engine
diffs specs, any ordering animates correctly with no new transition code, and the step
indicator renumbers automatically from plan position.

## 8. Migration plan (strangler)

Starting point: orchestrator-only VM (§2 already done).

1. **Land pure-data scaffolding**, no behaviour change: `DialogSpec`, `ContentSpec`,
   `Embellishment`, `TextSpec`, `CtaSpec`, `DialogSpecs`; view-side `Transitionable`,
   `Sequencer`, `ContentBinder`, `DialogRenderer`. Add the `Dialog(spec)` variant to
   `NewUserOnboardingActivityDialog` alongside the existing variants.
2. **Migrate one step at a time.** For each bubble step: point its `resolveDialog` at
   `DialogSpecs.x()` wrapped in `Dialog(...)`; route `Dialog` through `DialogRenderer`; delete
   that dialog's branch from `configureDaxCta` and `showDialogWithoutAnimation` and from
   `applyDialog`. Order, simplest first: Initial / ReinstallUser / SyncRestore → Comparison /
   AiComparison → SkipOption → AddressBar → InputScreen → InputScreenPreview → QuickSetup.
3. **Extract the three state holders** as their stateful contents migrate; result → VM on
   submit (VM callback surface unchanged).
4. **Delete** the old bubble variants, `PreOnboardingDialogType`, the legacy `when` blocks,
   and the dead `ViewState` fields once all steps are migrated.
5. **Parity gate throughout:** release-blocker Maestro onboarding flows + VM/plan-provider
   unit tests green at every step. Intro/outro untouched.

Each step is independently shippable and reversible.

## 9. Risks & open questions

- **`DialogSpec` home / layering.** It lives in the app orchestrator/page layer, referencing
  view-free semantic enums (`Embellishment`, `OnboardingBackgroundStep`) + res ids + content
  seeds. The `-api` module stays clean (only `LinearOnboardingStep`), consistent with its
  rule that renderer-specific needs go on host subtypes. The plan provider now references
  embellishment/background vocabulary — semantic ("this screen walks the Dax"), not pixel-
  level; acceptable, and no `View`/`Animator` leaks in.
- **Spec equality must be diff-stable.** `DialogSpec` drives the diff by `equals`, so it must
  hold only value-comparable data — including the `InputScreenPreview` suggestion lists
  (already value-like in today's `ViewState`). No lambdas, no view refs.
- **Step-indicator enrichment.** Plan position isn't known inside a per-step closure, so the
  VM layers `stepIndicator` onto the spec at emit via `state.stepIndicatorProgress()`. Means
  the spec is finalised in two places (builder + VM enrich); position is inherently plan-
  level, so this is acceptable.
- **Choreography edge cases.** Comparison chart today has fresh-entry `snapTo(ComparisonChart)`
  vs morph `transitionTo(ComparisonChart)`; QuickSetup uses `ChangeBounds`. The sequencer
  must let one element enter snapped while others animate. Feasible in C; the main thing a
  thin POC should de-risk.
- **Shown-pixel parity.** The fold relies on `step.pixelName` + `firingShownPixels()`. Verify
  the legacy `PREONBOARDING_*_SHOWN_UNIQUE` pixels are either moved onto steps or fully
  superseded by the `OnboardingPixelName` ones before deleting `fireDialogShownPixel`.
- **State-holder lifecycle.** Holders must survive config change / mid-flow re-entry.
  Simplest safe option: keep the live value inside the VM (a small dedicated field) with the
  holder as a thin facade, rather than a separately retained object. Decide during the
  stateful-content step.
- **Skip-animation path.** `Command.SkipDialogAnimation` maps to `Sequencer.skipToEnd()`,
  equivalent to the snap render.
- **`preventWidows` non-breaking spaces.** Brand-design titles insert U+00A0 before the last
  word; `TextSpec` rendering must preserve it (also matters for Maestro text asserts).
- **embellishment/background: explicit vs derived.** Today they are 1:1 with content
  (Welcome↔WalkingDax, ComparisonChart↔BottomWing …). Keeping them explicit `DialogSpec`
  fields gives the plan full control for permutations; deriving them from `content` removes
  duplication. Recommend explicit, defaulted from content in the `DialogSpecs` builders.
- **Scope creep.** The engine stays dumb about specific dialogs. If a dialog needs behaviour
  it can't express, the escape hatch is a richer `ContentSpec` / `Transitionable`, never a
  `when(dialogType)` back in the engine.

## 10. Appendix — current step → element mapping

Derived from `configureDaxCta` + the plan provider. Encoded as `DialogSpecs` builders.

| Step / dialog | Content | Embellishment | Background | Notes |
|---|---|---|---|---|
| Initial / InitialReinstallUser / SyncRestore | Welcome | WalkingDax | Welcome | optional secondary CTA (skip/restore) |
| ComparisonChart / AiComparisonChart | ComparisonChart | BottomWing | ComparisonChart | fresh-entry snap vs morph; intro (title type + table + checks); custom-AI copy = plan-selected builder |
| SkipNewUserOnboardingOption | *fade-swap (confirm)* | — | — | fade-out → swap → fade-in |
| AddressBarPosition | AddressBar | BobbingDax (in) | AddressBar | position picker; split option gated |
| InputScreen | InputScreen | BobbingDax (out) + LeftWing (in) | InputType | AI toggle picker |
| InputScreenPreview | InputScreenPreview | wings hidden | (prev) | keyboard input + suggestion-button stagger |
| QuickSetup | QuickSetup | BottomWing | QuickSetup | ChangeBounds + fade; row visibility from flags |
| IntroAnimation / NotificationPermission / DefaultBrowserPrompt | — side-effect presentations, not bubble dialogs — |
