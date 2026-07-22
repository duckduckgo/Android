# Onboarding dialog spec (v6 — summary)

## Problem

`BrandDesignUpdateWelcomePage` is ~3.1k lines and growing. Three causes:

1. **Every dialog is described twice.** `configureDaxCta` (~720 lines) wires each dialog for
   animated transitions; `showDialogWithoutAnimation` (~620 lines) wires the same dialogs
   again for snapped renders (rotation, re-entry). Every change touches both, and they drift.
2. **Every dialog is modeled three times**:
    ```
    NewUserOnboardingActivityDialog   (step render intent, built in the plan provider)
      └─ applyDialog() maps it to →   PreOnboardingDialogType + ~11 scattered ViewState fields
            └─ two when-blocks map that to →   actual views
    ```
3. **Dialogs assume their neighbors.** Branches hardcode what the previous screen left
   behind (which embellishment to dismiss, which animation to exit). Re-ordering screens
   breaks these assumptions one by one.

The Custom AI flow already re-orders screens, and the parent project will add more
permutations. The current structure makes each one a hand-wired special case.

## Goals

**Goals**
- One `DialogSpec` per screen: pure data (background, embellishment, content, CTAs). The
  step in the plan provider resolves it, the VM forwards it, the renderer draws it. Three representations become one.
- One render engine that diffs previous spec against new spec. One code path for animated
  and snapped renders, so they cannot drift.
- Any dialog can follow any dialog, or appear from nothing. Re-ordering a flow becomes a
  list edit in the plan provider.

**Non-goals**
- The legacy (non-brand-design) onboarding flow stays as-is, soon to be removed anyway.
- The one-time intro/outro animations and the system dialogs (notifications, default
  browser, add widget). They stay as they are.
- CTAs displayed in `BrowserActivity` stay as they are.

## Strategy

<CLAUDE_INSTRUCTION>make below a plantuml diagram</CLAUDE_INSTRUCTION>
```
plan provider    step resolves Dialog(DialogSpecs.comparisonChart())   ← ordering + content authority
      ▼
VM               forwards spec into ViewState, fires Presented          ← no translation layer
      ▼
render engine    diffs prev vs new spec, runs one fixed phase pipeline
      ▼
elements         background · embellishment · card · content · CTAs · indicator
```

### `DialogSpec`

```kotlin
data class DialogSpec(
    val background: OnboardingBackgroundStep,   // existing enum, reused as-is
    val embellishment: Embellishment,           // enum: WalkingDax, BobbingDax, BottomWing, LeftWing, None
    val content: ContentSpec,                   // sealed data, below; carries the screen's title and variable elements
    val primaryCta: CtaSpec,
    val secondaryCta: CtaSpec? = null,
    val stepIndicator: StepProgress? = null,    // existing type, filled in by the VM from plan position
)
```

**Spec is value-comparable data.** No lambdas, no views. Equality drives the diff, and
  specs are unit-testable straight off the plan.

### `ContentSpec` and `ContentHandle`

`ContentSpec` carries the screen's title plus whatever seed data varies:

```kotlin
sealed interface ContentSpec {
    val title: TextSpec   // every screen has one; rendered by its include's title view

    // stateless
    data class Welcome(override val title: TextSpec, val body1: TextSpec, val body2: TextSpec?) : ContentSpec
    data class ComparisonChart(override val title: TextSpec, val config: ComparisonChartConfig) : ContentSpec
    data class AddToDock(override val title: TextSpec) : ContentSpec
    data class WidgetPrompt(override val title: TextSpec) : ContentSpec

    // stateful: seed in, live edits stay in the view layer, result comes back on submit
    data class AddressBar(override val title: TextSpec, val initialPosition: OmnibarType, val showSplitOption: Boolean) : ContentSpec
    data class InputScreen(override val title: TextSpec, val initialWithAi: Boolean) : ContentSpec
    data class InputScreenPreview(override val title: TextSpec, val isSearchDefault: Boolean, val searchSuggestions: List<…>, val chatSuggestions: List<…>) : ContentSpec
    data class QuickSetup(override val title: TextSpec, val hideSetDefaultBrowserRow: Boolean, val hideAddWidgetRow: Boolean, val hideAddressBarRow: Boolean, val isReinstallUser: Boolean) : ContentSpec
}
```

View elements, strings, etc. that never vary stay in the include's XML or in
the binder, as today. Only the title and plan-dependent copy travel through the spec.

The view layer binds a spec and hands the engine a small handle. The handle is how a screen
declares its views without re-describing the choreography:

```kotlin
class ContentHandle(
    val title: OnboardingDialogTitleView?,   // engine types content.title into it
    val fadeTargets: List<View>,             // bodies, media, pickers; engine fades them uniformly
    val intro: Animator? = null,             // bespoke extras only (check-icon stagger, suggestion buttons)
    val result: (() -> ContentResult)? = null, // stateful screens: read on Submit
    val unbind: () -> Unit = {},             // resource release, animation cancels
)
```

**Titles.** Every screen layout today copy-pastes the same title machinery: a
`TypeAnimationTextView` for the typing effect, an invisible sizing twin (`hiddenTitleText`)
that keeps the card from resizing while the text types, and `preventWidows` handling (the
U+00A0 before the last word). That pattern becomes one `OnboardingDialogTitleView` compound
widget, dropped into each layout. The binder sets `content.title` on
it; the rendering engine tells it when to type or snap. No screen re-implements title behavior.

**Stateful screens** (address bar, input screen, quick setup). User edits inside the screen
stay in a small, VM-backed holder (to survive config changes), so the engine only diffs on
real step changes. The result crosses to the orchestrator on submit:

```kotlin
// view layer, one per stateful screen
class AddressBarStateHolder(seed: ContentSpec.AddressBar) {
    var selected = seed.initialPosition
        private set
    fun select(position: OmnibarType) { selected = position }  // live edit, no spec re-emit
    fun result() = ContentResult.AddressBar(selected)          // read once on Submit
}

// in the binder
is ContentSpec.AddressBar -> {
    val holder = AddressBarStateHolder(content)
    picker.onOptionSelected = holder::select
    ContentHandle(title = …, fadeTargets = listOf(picker), result = holder::result)
}
```

<CLAUDE_INSTRUCTION>we're missing an example of how a binder looks like</CLAUDE_INSTRUCTION>

### Flow
<CLAUDE_INSTRUCTION>prepare a PLANTUML diagram of the flow. from step definition, to VM, state, to binding, to rendering, to interaction, to result, to step cahnge, and whatever I'm missing</CLAUDE_INSTRUCTION>

Key decisions:

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
- New bubble screen = defining (reusing) a layout and providing variables. No need for ~200 lines duplicated across two when-blocks for plumbing.
- One owner for running animations: tap-to-skip and view teardown become one call instead of
  hand-enumerating ~25 animators. This pays off even before any re-ordering does.
- Every dialog can enter from an empty stage. "No previous spec" means clear the stage
  and enter — not a special case. This covers the first dialog, returns from
  `BrowserActivity` (today a hand-coded comparison-chart path), and migration handoffs.
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
| Two consecutive steps resolve identical specs, `StateFlow` swallows the second | Emitted state is keyed by step id, not spec equality alone |
| Engine grows dialog-specific logic over time | Hard rule: bespoke behaviour goes into the screen's content spec or its handle, never into the engine |

## Rollout

Scaffolding first (pure data types + engine, no behaviour change), then migrate screens
simplest-first, then delete the legacy when-blocks, `PreOnboardingDialogType`, and dead
`ViewState` fields. POC before committing: the three-screen chain above exercises every
risky mechanism in one pass.
