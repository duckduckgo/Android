# Onboarding dialog spec (v5)

## 1. Problem

`BrandDesignUpdateWelcomePage` is ~3.1k lines and growing. It hosts every pre-onboarding
dialog and hand-wires each one, assuming a fixed order of screens. The Custom AI flow already
re-orders screens (requiring special-casing), and the parent project will add more permutations, including re-ordering.

The size has two root causes:

**(a) Every dialog is described twice.** `configureDaxCta` (~720 lines) describes each dialog
for animated transitions. `showDialogWithoutAnimation` (~620 lines) describes the same dialogs
again for snapped renders (rotation, mid-flow re-entry). Every new screen or re-order touches
both, and they drift apart.

**(b) Every dialog is modeled three times** on its way to the screen:

```
NewUserOnboardingActivityDialog   (step render intent, built in the plan provider)
  └─ applyDialog() maps it to →   PreOnboardingDialogType + ~11 scattered ViewState fields
        └─ two when-blocks map that to →   actual views
```

There is a third, structural problem the duplication hides: **dialogs assume their neighbors.**
For example, the `INPUT_SCREEN` branch hardcodes `animateBobbingDaxOut()` because the address bar screen
comes before it. The `ADDRESS_BAR_POSITION` branch defensively dismisses every embellishment any
predecessor might have left behind. Re-ordering breaks these assumptions one by one.

## 2. Goals and non-goals

**Goals**

- One `DialogSpec` per screen. Pure data. The step in the plan provider resolves it, the VM forwards it, the
  renderer draws it. The three representations become one.
- One render engine that diffs the previous spec against the new one. One code path for
  animated and snapped renders, so the two descriptions can't drift.
- Any dialog can appear after any other dialog, or after nothing at all. Re-ordering a flow
  is a list edit in the plan provider.
- Collapse `ViewState` to the spec plus two transient animation flags.

**Non-goals**

- The legacy (non-brand-design) onboarding flow.
- The one-time intro/outro animations and the system dialogs (notifications, default
  browser, add widget). They stay as they are.
- CTAs displayed in `BrowserActivity`.

## 3. Design at a glance

```
plan provider          step.resolveDialog() = Dialog(DialogSpecs.comparisonChart())
        │
        ▼
VM                     forwards spec into ViewState (adds step indicator), fires Presented pixel
        │
        ▼
render engine          diffs prevSpec vs newSpec, runs one fixed phase pipeline
        │
        ▼
elements               background · embellishment · card · content · CTAs · indicator
```

The step's render hook and the render spec are the same object. The VM stops translating.
The plan provider is the one place that says, per step: when it shows, what it shows, what
comes next, what telemetry fires, whether it counts in the step indicator.

## 4. The data

### 4.1 Fold the dialog type

The eleven bubble variants collapse into one. The four genuine side effects stay:

```kotlin
sealed interface NewUserOnboardingActivityDialog {
    data class Dialog(val spec: DialogSpec) : NewUserOnboardingActivityDialog  // dax bubble

    data class IntroAnimation(val withDuckAi: Boolean) : NewUserOnboardingActivityDialog
    data object NotificationPermission : NewUserOnboardingActivityDialog
    data object DefaultBrowserPrompt : NewUserOnboardingActivityDialog
    data object AddWidget : NewUserOnboardingActivityDialog
}
```

### 4.2 `DialogSpec`

```kotlin
data class DialogSpec(
    val background: OnboardingBackgroundStep,   // existing enum, reused as-is
    val embellishment: Embellishment,           // enum: WalkingDax, BobbingDax, BottomWing, LeftWing, None
    val content: ContentSpec,                   // sealed data, below; carries the screen's title and copy
    val primaryCta: CtaSpec,
    val secondaryCta: CtaSpec? = null,
    val stepIndicator: StepProgress? = null,    // filled in by the VM from plan position
)
```

Rules that keep it useful:

- **Value-comparable only.** No lambdas, no views, no animators. `equals` drives the diff.
- **Embellishment and background stay explicit fields**, defaulted per screen inside the
  `DialogSpecs` builders. Explicit gives the plan full control for permutations; the default
  keeps the common case a one-liner.
- **No title or subtitle fields.** The layout shares only the CTAs, the step indicator and
  the card across screens. Each screen's title and copy live inside its content include, so
  they are `ContentSpec` data (4.3).

### 4.3 `ContentSpec` and `ContentHandle`

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

Copy that never varies (for example the add-to-dock body) stays in the include's XML or in
the binder, as today. Only the title and plan-dependent copy travel through the spec.

The view layer binds a spec and hands the engine a small handle. The handle is how a screen
declares its views without re-describing the choreography:

```kotlin
class ContentHandle(
    val title: OnboardingDialogTitleView?,   // engine types content.title into it
    val fadeTargets: List<View>,             // bodies, media, pickers; engine fades them uniformly
    val intro: Animator? = null,             // bespoke extras only (check-icon stagger, suggestion buttons)
    val result: (() -> ContentResult)? = null, // stateful screens: read on Submit
    val unbind: () -> Unit = {},             // video release, animation cancels
)
```

**Titles.** Every include today copy-pastes the same title machinery: a
`TypeAnimationTextView` for the typing effect, an invisible sizing twin (`hiddenTitleText`)
that keeps the card from resizing while the text types, and `preventWidows` handling (the
U+00A0 before the last word). That pattern becomes one `OnboardingDialogTitleView` compound
widget, dropped into each include in place of the pair. The binder sets `content.title` on
it; the engine tells it when to type or snap. No screen re-implements title behaviour.

**Stateful screens** (address bar, input screen, quick setup). User edits inside the screen
stay in a small view-layer holder and never produce a new spec, so the engine only diffs on
real step changes. The result crosses to the VM once, on Submit:

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

To survive rotation, the holder's live value is backed by a small dedicated VM field; the
holder is a thin facade over it.

### 4.4 CTAs

CTAs stay at the `DialogSpec` level, unlike titles, for two reasons. They are one shared
view pair in the dialog card — the same buttons persist across transitions and just change
text, which is what keeps the card morph continuous. And they are the step's exit contract:
they carry orchestrator events, which the plan decides (welcome with or without a skip CTA
is a plan choice). Everything else interactive belongs to content and reports through the
handle.

```kotlin
data class CtaSpec(val label: TextSpec, val action: CtaAction)

sealed interface CtaAction {
    /** Fire this orchestrator event as-is. */
    data class Emit(val event: NewUserOnboardingEvent) : CtaAction
    /** Stateful screens: read ContentHandle.result() and map it to its event. */
    data object Submit : CtaAction
}
```

No new action vocabulary: CTAs carry the existing `NewUserOnboardingEvent` type. The split
is about *when* the event's payload is known. `Emit` holds a finished event instance
(`ContinueClicked`, `SkipRequested`, …) baked in when the plan builds the spec — possible
because nothing in it depends on what the user does on the screen. `Submit` covers events
whose payload only exists at click time: `AddressBarConfirmed(type)` needs the user's live
selection, so the click reads the content's `result()` and each sealed `ContentResult` maps
1:1 to its event (`ContentResult.AddressBar → AddressBarConfirmed`, and so on). The
alternative — re-emitting a fresh spec on every in-screen edit so the event could stay
baked in — would make the renderer diff on every toggle; the holder design exists to avoid
exactly that. This replaces the `onPrimaryCtaClicked` / `onSecondaryCtaClicked` when-blocks
in the VM. No dialog-type switch anywhere.

### 4.5 `DialogSpecs` catalog

A factory of reusable builders, next to the plan provider that uses them. Shown in full for
`welcome` to make the mechanics clear, elided for the rest:

```kotlin
object DialogSpecs {
    fun welcome(customAiCopy: Boolean = false, secondaryCta: CtaSpec? = null) = DialogSpec(
        background = OnboardingBackgroundStep.Welcome,
        embellishment = Embellishment.WalkingDax,
        content = ContentSpec.Welcome(
            title = TextSpec(R.string.preOnboardingWelcomeDialogTitle),
            body1 = if (customAiCopy) {
                TextSpec(R.string.preOnboardingWelcomeDialogBodyCustomAi)
            } else {
                TextSpec(R.string.preOnboardingWelcomeDialogBody1)
            },
            body2 = if (customAiCopy) null else TextSpec(R.string.preOnboardingWelcomeDialogBody2),
        ),
        primaryCta = CtaSpec(TextSpec(R.string.preOnboardingWelcomeDialogCta), CtaAction.Emit(ContinueClicked)),
        secondaryCta = secondaryCta,
    )
    fun comparisonChart() = DialogSpec(…)
    fun aiComparisonChart() = DialogSpec(…)
    fun addToDock() = DialogSpec(…)
    fun widgetPrompt() = DialogSpec(…)
    fun addressBar(initialPosition: OmnibarType, showSplitOption: Boolean) = DialogSpec(…)
    fun quickSetup(…, customAiCta: Boolean = false) = DialogSpec(…)
    // syncRestore / reinstall / inputScreen / preview …
}
```

Note what happens to a flag like `customAiCopy`: it is resolved right here, inside the
builder, into concrete string resources. Nothing downstream ever sees the flag — the VM
forwards the spec, the binder sets the resulting `TextSpec`s on the include's views like any
other seed data. The Custom AI flow today threads `isCustomAiOnboardingFlow` at runtime into
three screens (welcome body, comparison chart copy, quick setup CTA); all three become
plan-selected builder arguments, and the flag disappears from `ViewState`.

A step after the fold:

```kotlin
private fun comparisonChartStep() = NewUserOnboardingActivityStep(
    id = COMPARISON_CHART, pixelName = ONBOARDING_SET_DEFAULT, showsStepIndicator = true,
    resolveDialog = { Dialog(DialogSpecs.comparisonChart()) },
    transition = { event -> /* unchanged */ },
)
```

Async seed keeps resolving inside `resolveDialog` exactly as `quickSetupStep` does today.

## 5. The render engine

One concrete `render(newSpec, animate)` function plus a few concrete collaborators. **No
generic transition framework.** The current code proves the animated path is already one
uniform sequence for every dialog; only the data differs. So the engine is a fixed phase
pipeline:

```
render(newSpec, animate):
    if prevSpec == null: clearStage()          # hide all includes, reset intro visuals
    unbind previous content                    # release video, cancel typing, etc.

    snap path (animate == false):
        background.snapTo; embellishment.snap; bind content; title.snap; alphas = 1; listeners on

    animated path:
        in parallel:  background.transitionTo
                      embellishment swap (old exits, new enters)
                      card re-anchor + ChangeBounds morph
        then:         title types (OnboardingDialogTitleView)
        then:         fade in fadeTargets + CTAs + step indicator, play handle.intro
        then:         attach listeners, isAnimating = false
```

Collaborators, all concrete classes:

- `OnboardingBackgroundAnimator` — exists, unchanged.
- `EmbellishmentController` — new, owns the five Lottie lifecycles that are currently spread
  across the page (delayed runnables, min/max progress, enter/exit, cancels). Also owns the
  two things that make embellishments not purely spec-driven:
  - **the fit veto**: `applyDecorationLayout` measures available space and may hide the
    embellishment even though the spec asks for it;
  - **card anchoring**: the dialog card's constraints (`bottomToTop` vs `bottomToBottom`,
    `verticalBias`, arrow depth) are a function of which embellishment actually shows and
    whether the device is a tablet. The controller returns the anchoring decision and the
    engine applies it before the ChangeBounds measure.
- `ContentBinder` — binds a `ContentSpec` to its include and returns the `ContentHandle`.

If a future screen genuinely breaks the phase template, the escape hatch is a richer
`ContentSpec` or `ContentHandle.intro`. Never a `when(dialogType)` inside the engine. If
several screens end up needing per-element variance, extracting an interface then is a
mechanical refactor; starting with one is speculative.

### 5.1 Design invariant: every dialog can enter from an empty stage

`prev == null` is not an error and not a special case. It means: clear the stage, then run
every element's enter. This one rule covers:

- **First dialog after the intro** (welcome).
- **Return from `BrowserActivity`** mid-plan. Today this is a hand-coded comparison-chart
  special case (`revealComparisonChart(freshEntry = true)`). It becomes the normal path, so
  any future flow can hand back to any screen.
- **Mid-migration handoff**: while old and new systems coexist, a legacy-rendered dialog on
  screen is just an unknown stage. Clear it, enter.

Animate-vs-snap is then pure policy, uniform for all screens:
`animate = !hasAnimatedCurrentDialog`. First presentation animates, rotation and re-entry
snap. No per-dialog decisions.

The one boundary case: the welcome dialog entering *while the intro exits* keeps its current
choreography (`playOutroAnimation` with the walking Dax). Intro/outro is a non-goal; the
intro simply ends by handing the engine an empty stage.

### 5.2 Card anchor timing during embellishment exit

When two consecutive specs use the same embellishment, the diff sees no change and does
nothing — no exit, no re-enter, no anchor change. That's the normal rule and it needs no
extra code.

The one subtlety is timing when the embellishment *does* change and the old one anchored
the card. Today `ADD_TO_DOCK` (no embellishment) keeps the card anchored to the
predecessor's bottom wing while the wing fades out (`keepBottomWingAnchor`), so the card
doesn't jump down mid-morph. That generalises into a default engine rule, no pair knowledge
needed: **hold the card's anchor until the exiting embellishment finishes, then swap
constraints**. Verify it visually during that screen's migration; if it holds, there are no
special-cased transitions anywhere.

## 6. VM after the fold

```kotlin
data class ViewState(
    val currentStepId: LinearOnboardingStepId? = null,  // dedup key; also resets the animate flag
    val currentSpec: DialogSpec? = null,
    val hasPlayedIntroAnimation: Boolean = false,
    val hasAnimatedCurrentDialog: Boolean = false,
)
```

`observeOrchestratorState` becomes a five-branch dispatch:

```kotlin
when (val d = step.resolveDialog()) {
    is Dialog -> {
        _viewState.update { it.copy(currentStepId = step.id, currentSpec = d.spec.withIndicator(state.stepIndicatorProgress()), hasAnimatedCurrentDialog = false) }
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)   // shown pixels ride this
    }
    is IntroAnimation      -> command(PlayIntroAnimation(d.withDuckAi))
    NotificationPermission -> startNotificationPermissionFlow()
    DefaultBrowserPrompt   -> showDefaultBrowserPromptOrAdvance()
    AddWidget              -> launchAddWidgetFlow()
}
```

Two details that are easy to get wrong:

- **`Presented` must keep firing.** Shown pixels work through the `firingShownPixels()` step
  wrapper, which only triggers on the `Presented` event. Today that event is sent from
  `fireDialogShownPixel`, which this design deletes. The dispatch above fires it explicitly,
  once per step.
- **`currentStepId` is part of the emitted state.** Two consecutive steps could resolve equal
  specs; `StateFlow` would swallow the second emission, so nothing would render and no pixel
  would fire. Keying on the step id makes every step render.

Gone: `applyDialog`, `PreOnboardingDialogType`, `setCurrentDialog`,
`setInputScreenPreviewDialog`, `fireDialogShownPixel`, the CTA-click when-blocks, and ~11
per-screen `ViewState` fields.

## 7. What this buys

- **Snap can't drift from animate.** Both derive from the same spec. ~1.3k lines of
  duplicated per-dialog description become one diff plus per-screen data.
- **Re-order or permute a flow = edit a list.** `buildDefaultPlan` and `buildCustomAiPlan`
  already are different lists; the add-to-dock and widget screens already entered as
  `buildList` edits. With the engine diffing specs, any ordering animates correctly with no
  new transition code, and the step indicator renumbers itself from plan position.
- **New bubble screen** = one step factory + one `DialogSpecs` builder + one `ContentSpec` +
  one binder entry + its include XML. Today it's ~200 lines duplicated across two when-blocks
  plus VM plumbing.
- **One owner for running animations.** Today `skipCurrentDialogAnimation` hand-enumerates 9
  animator sets and 8 title views, and `onDestroyView` cancels ~25 animators one by one.
  Both collapse to one call on the engine's current composite animation. Skip-on-tap
  (`Command.SkipDialogAnimation`) becomes "end current pipeline", identical to the snap
  render by construction. This payoff arrives even before any re-ordering does.
- **No special case for re-entering mid-flow** from anywhere, including `BrowserActivity`
  handbacks (5.1).
- **Testability.** Specs and the plan are pure values; plan-provider unit tests can assert
  the full rendered intent of every step without a device.
- **Compose-ready.** `DialogSpec` is exactly the state model a future Compose port would
  consume. This refactor is the declarative architecture without the rewrite risk; if the
  screen ever moves to Compose, the data layer and plan provider carry over unchanged.

## 8. Migration (strangler)

1. **Scaffolding, no behaviour change.** `DialogSpec`, `ContentSpec`, `TextSpec`, `CtaSpec`,
   `Embellishment`, `DialogSpecs`; `OnboardingDialogTitleView`; engine + `EmbellishmentController`
   + `ContentBinder`. Add the `Dialog(spec)` variant next to the existing ones.
2. **Migrate one step at a time**, simplest first: Initial / Reinstall / SyncRestore →
   Comparison / AiComparison → AddToDock / WidgetPrompt → AddressBar → InputScreen →
   InputScreenPreview → QuickSetup. Per step: point `resolveDialog` at a builder, route it
   through the engine, delete that dialog's branches from both legacy when-blocks and from
   `applyDialog`.
   **Interop rule:** legacy branches stay authoritative for their own enter (they already
   defensively reset everything). When the engine takes over from a legacy dialog it sees
   `prev == null` and clears the stage first, per 5.1. No cross-system choreography.
3. **Extract the stateful holders** as their screens migrate; results flow to the VM on
   Submit through the existing callback surface.
4. **Pixel parity before deletion.** The legacy `PREONBOARDING_*_SHOWN_UNIQUE` pixels fired
   by `fireDialogShownPixel` must be moved onto steps or confirmed superseded by the
   `OnboardingPixelName` shown-pixels before that function goes. Also confirm
   `SKIP_ONBOARDING_OPTION` is dead (both when-blocks are `Unit`) and drop it.
5. **Delete** the old bubble variants, `PreOnboardingDialogType`, both when-blocks, and the
   dead `ViewState` fields.
6. **Parity gate throughout:** release-blocker Maestro onboarding flows plus VM and
   plan-provider unit tests green at every step.

Each step ships and reverts independently.

**POC first.** A thin spike of the welcome → comparison → address-bar chain de-risks the
real unknowns in one pass: title-in-content via the widget, the fit veto, card re-anchoring,
and background morph-vs-snap under one pipeline.

## 9. Appendix — screen → element mapping

| Step | Content | Embellishment | Background | Notes |
|---|---|---|---|---|
| Initial / Reinstall / SyncRestore | Welcome | WalkingDax | Welcome | optional secondary CTA (skip / restore); custom-AI body variant |
| ComparisonChart / AiComparisonChart | ComparisonChart | BottomWing | ComparisonChart | check-icon stagger via handle.intro; AI copy = builder |
| AddToDock | AddToDock (looping video) | None | AddToDock | anchor timing, see 5.2; unbind releases video |
| WidgetPrompt | WidgetPrompt (image) | LeftWing | AddWidget | Add / Skip CTAs |
| AddressBarPosition | AddressBar | BobbingDax | AddressBar | stateful; split option gated |
| InputScreen | InputScreen | LeftWing | InputType | stateful (AI toggle) |
| InputScreenPreview | InputScreenPreview | None | (previous) | keyboard + suggestion stagger via handle.intro |
| QuickSetup | QuickSetup | BottomWing | QuickSetup | stateful; row visibility from seed; custom-AI CTA = builder |
| IntroAnimation / NotificationPermission / DefaultBrowserPrompt / AddWidget | — side-effect variants, not bubble dialogs — |
