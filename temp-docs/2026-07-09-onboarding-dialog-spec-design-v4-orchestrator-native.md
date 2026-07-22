## 1. Problem

`BrandDesignUpdateWelcomePage` is ~3k lines and growing. It hosts every pre-onboarding
dialog and imperatively hand-wires each one, expecting a fixed order of screens. Multiple forces make this unsustainable:
- The **Custom AI flow** already re-orders screens and needs special-casing to handle the state.
- We keep adding new screens and embellishments, each one requiring 100-200 lines of wiring.
- The parent project will introduce even more screen **permutations, including re-ordering**.

### Where the size comes from

**(a) Duplicated view logic.** Two large `when(dialogType)` blocks in the page describe the
*same* per-dialog state twice, each nearly 1k lines long:

| Function | Role |
|---|---|
| `configureDaxCta` | animated path — transitions between dialogs |
| `showDialogWithoutAnimation` | snapped path — mid-flow re-entry / config change |

Each branch hand-wires content-include visibility, embellishment enter/exit, background
step, chrome (title / subtitle / CTAs / step indicator), and a bespoke content intro
animation. Because animated and snapped are separate code, every dialog is described twice
and every new ordering touches both.

**(b) A triple representation of "which dialog".** With the orchestrator driving onboarding,
the same dialog is modelled three times on its way to the screen:

```
NewUserOnboardingActivityDialog   (orchestrator render intent + seed data, built in the onboarding plan provider)
  └─ BrandDesignUpdatePageViewModel.applyDialog() maps it to →  PreOnboardingDialogType + scattered ViewState fields
        └─ configureDaxCta's when maps that to →  actual views
```

Every new screen means touching all three layers.

## 2. Goals & non-goals

**Goals**

- A lightweight, declarative **`DialogSpec`** — pure data — that a step resolves to and the
  VM emits as its render state.
- Fold the orchestrator's per-step render hook and the `DialogSpec` into **one
  representation**, deleting the `applyDialog` translation layer and `PreOnboardingDialogType` usage.
- A single **transition engine** that diffs the previously-emitted spec against the new one
  and drives the change, with one code path for both animated and snapped renders.
- Make the plan provider the single self-describing source per step, so **re-ordering /
  permuting screens is a list edit**, not new branches.
- Collapse `ViewState` to the spec plus a couple of transient animation flags.

**Non-goals**

- Updating the non-brand-design legacy onboarding flow.
- Spec-ifying the one-time **intro / outro** animations or system dialog actions.
- Updating CTAs displayed in the `BrowserActivity`.

## 4. Current state (reference)

The pieces this refactor builds on already exist. Structure and the triple representation are
in the class diagram: [diagrams/current-model.puml](diagrams/current-model.puml).

Behaviours a static diagram can't show:

- **Self-describing steps.** `NewUserOnboardingActivityStep` carries `precondition` (whether it
  shows), `transition` (event → next), `pixelName` (shown-pixel via `firingShownPixels()`),
  `showsStepIndicator`, and `resolveDialog()`. Render intent is already seed data — variants
  carry per-dialog seeds (`AddressBarPosition(showSplitOption)`, `QuickSetup(hide*Row, …)`,
  `IntroAnimation(withDuckAi)`).
- **Plan provider is already the ordering authority.** One factory per step composed into
  `steps = buildList { … }`; forks flows as different lists (`buildDefaultPlan` vs
  `buildCustomAiPlan`), conditionally includes by experiment (`if (showDock)
  add(addToDockStep())`; `if (showWidget) { add(widgetPromptStep()); add(addWidgetStep()) }`),
  and resolves async seed inside `resolveDialog` (`isDefaultBrowser()`, `hasInstalledWidgets`,
  `isSplitOmnibarEnabled()`).
- **Step indicator is position-derived.** `stepIndicatorProgress()` computes
  `StepProgress(current, total)` from the position of `showsStepIndicator` steps — no hardcoded
  page numbers.
- **Background / content / config are already spec-like.** `OnboardingBackgroundAnimator` owns
  its own `transitionTo` / `snapTo`; content is six stacked includes toggled by visibility over
  shared CTAs; `ComparisonChartConfig` is a sealed `@StringRes`/`@DrawableRes` seed applied to a
  generic view — precedent that resource ids in render-intent data are fine. v4 generalises it
  to the whole dialog. Embellishments are four Lottie views with bespoke enter/exit lifecycles.

## 5. Proposed architecture

Class diagram: [diagrams/new-model.puml](diagrams/new-model.puml) — same layout as
[current-model.puml](diagrams/current-model.puml) for side-by-side comparison.

### 5.1 Layering

See [diagrams/render-pipeline.puml](diagrams/render-pipeline.puml) for the layering: step
`resolveDialog()` → `NewUserOnboardingActivityDialog` (one `Dialog(spec)` bubble path + the
side-effect commands) → VM emits the spec to `ViewState.currentSpec` (+ step-indicator
position) → `DialogRenderer` diffs `prevSpec` vs `newSpec` and drives Background, Embellishment,
Chrome, and ContentBinder down **one path for both animate and snap**.

The fold: the step's render hook and the render `DialogSpec` become the **same object**. The
VM stops translating; it forwards. The plan provider becomes the one place that says, per
step, *when / what / next / telemetry / indicator*.

### 5.2 Fold `NewUserOnboardingActivityDialog`

The eleven bubble variants collapse into a single `Dialog(spec)`. Only the genuine non-bubble
side-effects stay as their own variants (they are not dax-bubble dialogs — they play the
intro Lottie, request a runtime permission, show the system default-browser dialog, or launch
the system add-widget flow):

```kotlin
sealed interface NewUserOnboardingActivityDialog {
    data class Dialog(val spec: DialogSpec) : NewUserOnboardingActivityDialog   // rendered in the dax bubble

    data class IntroAnimation(val withDuckAi: Boolean) : NewUserOnboardingActivityDialog
    data object NotificationPermission : NewUserOnboardingActivityDialog
    data object DefaultBrowserPrompt : NewUserOnboardingActivityDialog
    data object AddWidget : NewUserOnboardingActivityDialog                     // launches the system add-widget flow
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
    data object AddToDock : ContentSpec       // "add to dock" promo (title-typing + body + looping video)
    data object WidgetPrompt : ContentSpec    // "add widget" promo (title-typing + body + image); Add / Skip CTAs

    // stateful — carry SEED values; live edits owned by a view-scoped holder,
    // result forwarded to the VM on submit
    data class AddressBar(val initialPosition: OmnibarType, val showSplitOption: Boolean) : ContentSpec
    data class InputScreen(val initialWithAi: Boolean) : ContentSpec
    data class InputScreenPreview(val isSearchDefault: Boolean, val searchSuggestions: List<…>, val chatSuggestions: List<…>) : ContentSpec
    data class QuickSetup(val hideSetDefaultBrowserRow: Boolean, val hideAddWidgetRow: Boolean, val hideAddressBarRow: Boolean, val isReinstallUser: Boolean) : ContentSpec
}
```

```kotlin
// view-layer; the ONLY place that knows includes/animators
fun ContentBinder.render(content: ContentSpec, view: View): Animator? = when (content) {
    Welcome               -> { show(welcomeContent); null }
    is ComparisonChart    -> { populate(view, content.config); comparisonChartIntro(view) }
    AddToDock             -> { startAddToDockVideo(view); titleTypingIntro(view) }
    WidgetPrompt          -> { bindWidgetPrompt(view); titleTypingIntro(view) }
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

**View-scoped state holder shape.** A stateful screen's holder lives in the renderer/page
scope, is seeded from the `ContentSpec` when the screen enters, is mutated by user interaction
*without* emitting a new spec, and is read once on primary-CTA submit:

```kotlin
// view-layer; one per stateful screen
class AddressBarStateHolder(seed: ContentSpec.AddressBar) {
    val showSplitOption = seed.showSplitOption
    var selected: OmnibarType = seed.initialPosition   // live edit, no spec re-emit
        private set

    fun select(position: OmnibarType) { selected = position }
    fun result() = AddressBarResult(selected)          // read on submit, forwarded to the VM
}
```

Common shape: `seed(spec)` in → live mutation while the screen is up → `result()` out to the VM
via the existing callback surface. Only `result()` crosses back, so intra-screen toggling never
produces a new `DialogSpec` and the renderer diffs only on a real transition. Retention across
config change / mid-flow re-entry is the open item in §9 (simplest: back the live value with a
small VM field and keep the holder as a thin facade).

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
    fun addToDock() = DialogSpec(
        background = AddToDock, embellishment = None,     // video-centric; wings dismissed
        title = TextSpec(R.string.preOnboardingAddToDockTitle), subtitle = null,
        content = ContentSpec.AddToDock,
        primaryCta = CtaSpec(R.string.preOnboardingAddToDockPrimaryCta, Intent.Continue), secondaryCta = null,
    )
    fun widgetPrompt() = DialogSpec(
        background = AddWidget, embellishment = LeftWing,
        title = TextSpec(R.string.preOnboardingWidgetPromptTitle), subtitle = null,
        content = ContentSpec.WidgetPrompt,
        primaryCta = CtaSpec(R.string.preOnboardingWidgetPromptPrimaryCta, Intent.AddWidget),
        secondaryCta = CtaSpec(R.string.preOnboardingWidgetPromptSecondaryCta, Intent.SkipWidget),
    )
    fun addressBar(initialPosition: OmnibarType, showSplitOption: Boolean) = /* embellishment = BobbingDax, … */
    fun quickSetup(showSplitOption: Boolean, hideSetDefaultBrowserRow: Boolean, hideAddWidgetRow: Boolean, hideAddressBarRow: Boolean, isReinstallUser: Boolean) = /* … */
    // welcome/comparison/addToDock/widgetPrompt/input/preview/syncRestore/initialReinstall …
}
```

Step factory, after the fold:

```kotlin
private fun comparisonChartStep() = NewUserOnboardingActivityStep(
    id = COMPARISON_CHART, pixelName = ONBOARDING_SET_DEFAULT, showsStepIndicator = true,
    resolveDialog = { NewUserOnboardingActivityDialog.Dialog(DialogSpecs.comparisonChart()) },
    transition = { event -> /* unchanged */ },
)

private fun quickSetupStep(ctx: NewUserOnboardingPlanContext, forceWithAiInput: Boolean) = NewUserOnboardingActivityStep(
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
                hideAddressBarRow = forceWithAiInput,
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

`observeOrchestratorState` reduces to a five-branch dispatch; `applyDialog`,
`setCurrentDialog`, `setInputScreenPreviewDialog`, and `fireDialogShownPixel` are gone:

```kotlin
when (val d = step.resolveDialog()) {
    is Dialog               -> _viewState.update { it.copy(currentSpec = d.spec.withIndicator(state.stepIndicatorProgress())) }
    is IntroAnimation       -> command(PlayIntroAnimation(withDuckAi = d.withDuckAi))
    NotificationPermission  -> startNotificationPermissionFlow()
    DefaultBrowserPrompt    -> showDefaultBrowserPromptOrAdvance()
    AddWidget               -> launchAddWidgetFlow()   // result → AddWidgetFinished on return
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

This just played out for real: the merged **add-to-dock** and **add-widget** screens entered
the flow as a `buildList { … }` edit — `if (showDock) add(addToDockStep())` and
`if (showWidget) { add(widgetPromptStep()); add(addWidgetStep()) }`, gated by an experiment
variant. Under this design each new bubble screen is one step factory + one `DialogSpecs`
builder + one `ContentSpec` (plus its binder line); a side-effect screen like `AddWidget` is
one step factory + one dispatch branch. No engine, renderer, or `when(dialogType)` changes.

## 8. Migration plan (strangler)

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
| AddToDock | AddToDock (title-typing + body + looping video) | None (wings dismissed) | AddToDock | Continue CTA; experiment-gated (`if (showDock)`) |
| WidgetPrompt | WidgetPrompt (title-typing + body + image) | LeftWing | AddWidget | Add / Skip CTAs; experiment-gated (`if (showWidget)`) |
| SkipNewUserOnboardingOption | *fade-swap (confirm)* | — | — | fade-out → swap → fade-in |
| AddressBarPosition | AddressBar | BobbingDax (in) | AddressBar | position picker; split option gated |
| InputScreen | InputScreen | BobbingDax (out) + LeftWing (in) | InputType | AI toggle picker |
| InputScreenPreview | InputScreenPreview | wings hidden | (prev) | keyboard input + suggestion-button stagger |
| QuickSetup | QuickSetup | BottomWing | QuickSetup | ChangeBounds + fade; row visibility from flags |
| IntroAnimation / NotificationPermission / DefaultBrowserPrompt / AddWidget | — side-effect presentations, not bubble dialogs — |
