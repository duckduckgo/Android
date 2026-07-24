# Config-driven onboarding dialogs (tech design)

## Problem

`BrandDesignUpdateWelcomePage` is ~3.1k lines and growing. This becomes unmanageable. Three causes:

1. **Every dialog is described twice.** `configureDaxCta` (~720 lines) wires each dialog for
   animated transitions; `showDialogWithoutAnimation` (~620 lines) wires the same dialogs
   again for snapped renders (rotation, re-entry). Every change touches both, and they drift.
2. **Every dialog is modeled three times**:
    ```
    NewUserOnboardingActivityDialog   (step definition, built in the plan provider)
      └─ applyDialog() maps it to →   PreOnboardingDialogType + ~11 scattered ViewState fields
            └─ two when-blocks map that to →   actual views
    ```
3. **Dialogs assume their neighbors.** Branches hardcode what the previous screen left
   behind (which embellishment to dismiss, which animation to exit). Re-ordering screens
   breaks these assumptions one by one.

The Custom AI flow already re-orders screens, and https://app.asana.com/1/137249556945/project/1208671518894266/task/1215556935109578?focus=true will add more
permutations. The current structure makes each one a hand-wired special case.

## Goals

**Goals**
- One `DialogConfig` per step, describing all of its unique characteristics.
- One code path for animated and snapped renders, so they cannot drift.
- Any dialog can follow any dialog, or appear from nothing. Re-ordering a flow becomes a
  list edit in the plan provider, no transition logic changes or special cases needed.

**Non-goals**
- The legacy (non-brand-design) onboarding flow stays as-is, soon to be removed anyway.
- The one-time intro/outro animations and the system dialogs (notifications, default
  browser, add widget). They stay as they are.
- CTAs displayed in `BrowserActivity` stay as they are.

## Strategy

1. Each onboarding step describes its screen as a `DialogConfig`: plain data listing the
background, embellishment (Dax animations), content, and CTA buttons. The plan provider becomes the single authority
for what each screen shows and in what order.
2. The VM stops translating and just forwards the config.
3. A new render engine compares the previous config with the new one and animates only what
changed — the same code path snaps everything into place when there is nothing to animate
(rotation, re-entry). All the per-dialog view wiring that exists today collapses into that
one engine plus per-screen data.
4. The engine itself is a set of independent axis controllers — background, embellishment, card anchor, content — each seeing only its own previous → next
value.

### `DialogConfig`

```kotlin
data class DialogConfig(
    val background: OnboardingBackgroundStep,   // existing enum, reused as-is
    val embellishment: Embellishment,           // enum: WalkingDax, BobbingDax, BottomWing, LeftWing, None
    val content: ContentConfig,                 // sealed data, described below
    val primaryCta: CtaConfig? = null,          // CTA button configs
    val secondaryCta: CtaConfig? = null,
    val stepIndicator: StepProgress? = null,    // existing type, filled in by the VM from plan position
    val cardArrow: CardArrowConfig = Hidden,
)

enum class CardArrowConfig { Hidden, AtStart, AtEnd }

data class CtaConfig(
    val text: TextConfig,
    val action: CtaAction,
)

sealed interface CtaAction {
    data class Emit(val event: NewUserOnboardingEvent) : CtaAction  // click forwards this event to the orchestrator as-is
    data object Submit : CtaAction  // click asks the bound screen to build the event from its live state (ContentHandle.result below)
}
```

**Config is value-comparable data.** No lambdas, no views. Equality drives the diff, and
  configs are unit-testable straight off the plan.

### `ContentConfig` and `ContentHandle`

`ContentConfig` carries the screen's title plus whatever seed data varies:

```kotlin
sealed interface ContentConfig {
    val title: TextConfig   // every screen has one; rendered by each layout's title view

    // stateless dialogs
    data class Welcome(override val title: TextConfig, val body1: TextConfig, val body2: TextConfig?) : ContentConfig
    data class ComparisonChart(override val title: TextConfig, val config: ComparisonChartConfig) : ContentConfig
    data class AddToDock(override val title: TextConfig, val body: TextConfig) : ContentConfig
    data class WidgetPrompt(override val title: TextConfig, val body: TextConfig) : ContentConfig

    // stateful dialogs
    data class AddressBar(override val title: TextConfig, val initialPosition: OmnibarType, val showSplitOption: Boolean) :
        ContentConfig, Stateful<AddressBarContentState> {
        // ...
    }
    data class InputScreen(override val title: TextConfig, val description: TextConfig, val initialWithAi: Boolean) :
        ContentConfig, Stateful<InputScreenContentState> {
        // ...
    }
    data class InputScreenPreview(override val title: TextConfig, val isSearchDefault: Boolean, val showModeToggle: Boolean, val searchSuggestions: List<…>, val chatSuggestions: List<…>) :
        ContentConfig, Stateful<InputScreenPreviewContentState> {
        // ...
    }
    data class QuickSetup(override val title: TextConfig, val showSplitOption: Boolean, val hideSetDefaultBrowserRow: Boolean, val hideAddWidgetRow: Boolean, val hideAddressBarRow: Boolean, val initialAddressBarPosition: OmnibarType, val initialWithAi: Boolean) :
        ContentConfig, Stateful<QuickSetupContentState> {
        // ...
    }
}

// stateful steps declare their working state; the engine seeds it at bind
interface Stateful<S : Any> {
    fun initialState(): S
}

// example state class for the address bar position step
data class AddressBarContentState(val position: OmnibarType)
```

View elements, strings, etc. that never vary can stay in the XML as today. Only the title and plan-dependent mutations travel through the config.

`TextConfig` resolves a resource or a literal, plus one flag:

```kotlin
sealed interface TextConfig {
    val html: Boolean   // default false; the binder html-decodes the resolved text when set

    data class Resource(@StringRes val resId: Int, override val html: Boolean = false) : TextConfig
    data class Literal(val text: String, override val html: Boolean = false) : TextConfig
}
```

The view layer binds a config and hands the engine a small handle.
```kotlin
// view layer — one binder per screen
interface DialogBinder<C : ContentConfig> {
    fun bind(content: C, scope: BindScope): ContentHandle
}
interface StatefulDialogBinder<C, S : Any> where C : ContentConfig, C : Stateful<S> {
    fun bind(content: C, state: MutableStateFlow<S>, scope: BindScope): ContentHandle  // state seeded from the config's initialState() before bind
}
```

The handle is how a screen declares its views without re-describing the choreography:

```kotlin
class ContentHandle(
    val title: OnboardingDialogTitleView?,   // engine types content.title into it
    val fadeTargets: List<View>,             // bodies, media, pickers; engine fades them uniformly
    val afterFade: (() -> Animator)? = null, // bespoke intro animations, played by the engine once the standard fade lands (check-icon stagger, suggestion buttons)
    val result: (() -> NewUserOnboardingEvent)? = null, // stateful screens: builds the submit event from the current selection
    val unbind: () -> Unit = {},             // non-animation resource release; engine cancels scope animators itself
)
```

The bind scope carries the coroutine scope for state observation and one channel for content-initiated interactions — everything a screen triggers outside the primary/secondary CTA clicks, which the engine wires itself:

```kotlin
class BindScope(
    val coroutineScope: CoroutineScope,          // engine cancels it at unbind (state observation lives here)
    val execute: (ContentInteraction) -> Unit,   // content-initiated VM interactions
)

// the VM interactions a bound screen can trigger outside the shared CTA flow
sealed interface ContentInteraction {
    data class SubmitInput(val query: String, val isChat: Boolean, val fromSuggestion: Boolean) : ContentInteraction  // input preview: typed query or suggestion tap
    data object EditAddressBarPosition : ContentInteraction
    data object EditSearchOptions : ContentInteraction
    data class SetDefaultBrowserToggled(val checked: Boolean) : ContentInteraction
    data class AddWidgetToggled(val checked: Boolean) : ContentInteraction
}
```

**Binders declare animations, never run them.** The engine owns every animator it gets: it plays it at the declared hook,
ends it on the snap path and `cancel()`s it on unbind.

**Titles.** Every step layout today copy-pastes the same title machinery: a
`TypeAnimationTextView` for the typing effect, an invisible sizing twin (`hiddenTitleText`)
that keeps the card from resizing while the text types, and `preventWidows` handling (the
non-breaking-space before the last word). That pattern becomes one `OnboardingDialogTitleView` compound
widget, dropped into each layout. The binder sets `content.title` on
it; the rendering engine tells it when to type or snap. No screen re-implements title behavior.

**Stateful screens** (address bar, input screen, quick setup — with more planned as part of the parent project). User edits inside the screen
never produce a new config, they stay local until submitted:

- **Live state.** A stateful screen's working state is one value class in a store owned by
  the VM. State flows one way:
  writes go into the store, the binder observes and renders. The engine seeds the flow from
  the config's `initialState()` before bind, and the observation is bind-scoped — it lives in
  the `BindScope`'s coroutine scope, which the engine cancels at unbind, like animators. A new
  stateful screen carries its state class, doesn't need a new VM field.
  ```
  BrandDesignUpdatePageViewModel
      └─ contentValues: ContentValueStore
  ```
  ```kotlin
  class ContentValueStore {
      private val states = mutableMapOf<KClass<*>, MutableStateFlow<*>>()
      fun <S : Any> contentState(content: Stateful<S>): MutableStateFlow<S>  // seeded from initialState() on first use
  }
  ```
- **Submit.** The binder gives the handle a `result` closure that builds the orchestrator
  event from the screen's state (`{ AddressBarConfirmed(state.value.position) }`) when primary/secondary button is clicked.
- **External changes.** Quick setup re-syncs its default-browser and widget switches on
  resume. Same path: the VM writes fresh values through `contentState(…)` on the showing
  screen's config and the bound screen's observation renders them.

### Architecture

The new config driven stack lives behind a feature flag. The orchestrator, the plan provider, and
the layouts serve both old and new implementations; the new arm replaces the fragment and the VM:

- **Orchestrator and plan provider: untouched.** Steps keep returning
  `NewUserOnboardingActivityDialog`; the new arm adds one pure `DialogConfigResolver` (a single
  `when` mapping dialog → `DialogConfig`) — the unit-testable config source immediately, inlined
  into the steps once the legacy arm goes.
- **Layouts: shared.** The new fragment inflates the same card and includes. One exception:
  the `OnboardingDialogTitleView` dropped in to each step's layout.
- **New slim VM.** `ViewState` is a step id, a `DialogConfig`, plus the
  `ContentValueStore` for stateful screens. Shown pixels come from one shared mapping: an
  exhaustive `when (dialog)` → pixel, extracted from legacy (~15 lines) and called by both
  VMs. Parity can't drift — a pixel added during ramp fires from both arms, and a new dialog
  is a compile error until mapped.
- **New fragment: thin.** It observes the `ViewState` and hands each config to the render
  engine; the engine drives the axis controllers and the per-screen binders. No per-dialog
  branches anywhere in the fragment.

```plantuml
@startuml
skinparam shadowing false
skinparam componentStyle rectangle
left to right direction

package "Shared" {
  [Orchestrator\n+ plan provider] as Orch
  [Shown-pixel\nmapper] as Pixels
  [Card + content\nlayouts (XML)] as Layouts
}

package "Legacy arm (flag off)" {
  [BrandDesignUpdatePageViewModel\nViewState: ~16 per-dialog fields] as OldVM
  [BrandDesignUpdateWelcomePage\n~3.1k lines: every dialog wired twice\n(animated + snapped)] as OldPage
}

package "Config-driven arm (flag on)" {
  [ConfigDrivenOnboardingPageViewModel\nViewState: stepId + DialogConfig + ContentValueStore] as NewVM
  [DialogConfigResolver\ndialog → DialogConfig] as Resolver
  [ConfigDrivenWelcomePage\n(thin fragment)] as NewPage
  [DialogRenderEngine\ndiff previous vs new config] as Engine
  [Axis controllers\nbackground / embellishment /\ncard anchor / step indicator] as Axes
  [ContentBinder\n+ one binder per screen] as Binders
}

Orch <--> OldVM
Orch <--> NewVM
OldVM ..> Pixels
NewVM ..> Pixels
NewVM --> Resolver
OldVM --> OldPage : ViewState
NewVM --> NewPage : ViewState
NewPage --> Engine : render(config, animate)
Engine --> Axes : one axis each,\nprevious → next
Engine --> Binders : bind / unbind
Binders --> Layouts
OldPage --> Layouts
@enduml
```

### Flow

One full step lifecycle, from the step becoming current to the next step taking over:

```plantuml
@startuml
skinparam shadowing false
participant Orchestrator
participant "Plan step" as Step
participant VM
participant "Render engine" as Engine
participant ContentBinder as Binder
actor User

== Step becomes current ==
Orchestrator -> VM : state: InProgress(step)
VM -> Step : resolveDialog()
Step --> VM : dialog
VM -> VM : DialogConfigResolver:\ndialog → DialogConfig
VM -> VM : fire shown pixel\n(mapper shared with the legacy arm)
VM -> VM : ViewState = stepId + config\n(+ step indicator from plan position)
VM -> Orchestrator : Presented

== Render ==
VM -> Engine : render(config, animate)
note right : the flag whether animations should play or not is VM-owned,\nto handle orientation change
Engine -> Engine : diff previous vs new config\n(no previous → reset stage, enter everything)
Engine -> Binder : unbind(previous content)
note right : cancels running animators\nand the BindScope (state observation)
Engine -> Binder : bind(new content, BindScope)
note right : stateful screens: dispatch seeds the\nMutableStateFlow via contentValues\n(initialState() on first use) and hands\nit to the screen binder
Binder --> Engine : ContentHandle
Engine -> Engine : reveal card → background ∥ embellishment ∥ card morph\n→ title types → content + CTAs fade in\n→ click listeners attach
note right : animate = false runs the same\npipeline snapped to end states\n(rotation, re-emission, tap-to-skip,\nsystem animations off)

== Interaction ==
User -> Binder : live edits (picker, toggle)
Binder -> VM : state.update { … }\n(flow in contentValues, survives rotation)
VM --> Binder : flow emits, binder renders\n(state down)
note right : no new config, no engine re-render
opt external change (quick setup resume sync)
  VM -> VM : contentState(showing config)\n  .update { … }
  VM --> Binder : flow emits, binder renders
end
opt content-initiated interaction (query submit, bottom sheet, system toggle)
  User -> Binder : text submit / row tap / switch flip
  Binder -> VM : scope.execute(interaction)
  note right : VM interprets it: SubmitInput becomes an\norchestrator event, the rest run commands\nor side effects; self-toggling views write the\noptimistic value into the content state first
end
User -> Engine : CTA click
alt action is Emit(event)
  Engine -> VM : event as-is
else action is Submit
  Engine -> Binder : handle.result()
  Binder --> Engine : event built from state.value
  Engine -> VM : event
end
VM -> Orchestrator : forward event
note right : VM never sees the handle;\nit forwards events blindly

== Step change ==
Orchestrator -> Step : transition(event)
Step --> Orchestrator : Advance (or Stay / SwitchTo / AbortPlan)
Orchestrator -> VM : state: InProgress(next step)
note over VM, Engine : cycle repeats; the engine diffs\nthe outgoing config against the new one
@enduml
```

## Benefits

- ~1.3k lines of duplicated per-dialog wiring become unified. Snap and
  animate cannot drift apart.
- Re-ordering or permuting a flow means editing a list. Any ordering animates correctly with no new transition code.
- One owner for running animations: tap-to-skip and view teardown become one call instead of
  hand-enumerating ~25 animators.
- Key render logic is split by concerns, improving readability and maintainability.
- `ViewState` collapses from 16 fields to a `DialogConfig` and a couple of flags.
- `DialogConfig` is the state model a future Compose port would consume unchanged — the
  declarative architecture without the rewrite risk.
- The orchestrator already supports `GoBack` and a diff is direction-agnostic, so backward
  transitions come free if we ever need them. Enabled by structure, but not scoped.

## Risks and mitigations

| Risk | Mitigation                                                                                                                                                                                    |
|---|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Choreography edge cases: embellishments can be vetoed by available space, and they decide the card's anchoring | All of it lives in one `EmbellishmentController` — fit veto, anchoring, and the declared-vs-actual reconciliation the per-frame veto implies.                                                 |
| Shown pixels silently stop firing | Pixel mapping extracted into a shared utility called by both old and new VMs, ensuring parity.                                                                                                |
| Regression in a release-critical flow | Whole parallel renderer is behind a remote toggle. With the flag-off, unchanged, byte-identical old arm is used. Maestro release-blocker flows run in both flag states.                       |
| Engine grows dialog-specific logic over time | Bespoke behaviour goes into the screen's content config or its handle, never into the engine. No code branches on (previous, next) screen pairs — each axis controller sees only its own axis |

## Rollout

Behind a remote feature flag. A new toggle added on the existing
`OnboardingBrandDesignUpdateToggles` (`configDrivenDialogs()`). The flag
selects a whole parallel renderer, not per-screen paths, so mixed-renderer sessions never
exist.

## Appendix: Examples
[1] Binder examples:
```kotlin
// stateless dialog example
class ComparisonChartBinder(private val binding: ViewComparisonChartContentBinding) : DialogBinder<ContentConfig.ComparisonChart> {
    override fun bind(content: ContentConfig.ComparisonChart, scope: BindScope): ContentHandle = with(binding) {
        populate(content.config)
        ContentHandle(title = titleView, fadeTargets = listOf(comparisonTable), afterFade = { checkIconStaggerAnimator() })
    }
}

// stateful dialog example
class AddressBarBinder(private val binding: ViewAddressBarContentBinding) : StatefulDialogBinder<ContentConfig.AddressBar, AddressBarContentState> {
    override fun bind(content: ContentConfig.AddressBar, state: MutableStateFlow<AddressBarContentState>, scope: BindScope): ContentHandle = with(binding) {
        picker.onOptionSelected = { position -> state.update { it.copy(position = position) } }  // events up
        observe(state, scope) { picker.selected = it.position }                                  // state down, bind-scoped
        ContentHandle(title = titleView, fadeTargets = listOf(picker), result = { AddressBarConfirmed(state.value.position) })
    }
}

class ContentBinder(binding: …, private val contentValues: ContentValueStore) {
    private val welcome = WelcomeBinder(binding.welcomeContent)
    private val comparisonChart = ComparisonChartBinder(binding.comparisonChartContent)
    private val addressBar = AddressBarBinder(binding.addressBarContent)
    // one per screen …

    fun bind(content: ContentConfig, scope: BindScope): ContentHandle = when (content) {
        is ContentConfig.Welcome -> welcome.bind(content, scope)
        is ContentConfig.ComparisonChart -> comparisonChart.bind(content, scope)
        is ContentConfig.AddressBar -> addressBar.bind(content, contentValues.contentState(content), scope)
        // one line per screen …
    }
}
```