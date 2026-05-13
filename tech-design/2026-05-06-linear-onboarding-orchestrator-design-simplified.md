# Linear Onboarding Orchestrator

## TL;DR

Lift the linear-onboarding state machine from `BrandDesignUpdatePageViewModel` into an `AppScope` orchestrator and extract the linear onboarding-state contracts out of `:app`.

Two structural wins delivered together: the linear onboarding state machine is no longer tied to a single ViewModel — clearing the path for multi-activity onboarding once `BrowserActivity` is wired up in a follow-up — and onboarding contracts (`UserStageStore`, `OnboardingSkipper`, the orchestrator surface) get a real home outside `:app`.

## Context

The trigger is [Android: AI onboarding for custom store listings](https://app.asana.com/1/137249556945/project/1208671518894266/task/1213551217308374). We want the user to do a few linear onboarding steps (which live in `OnboardingActivity`), transition to some Duck.ai-flavoured steps (rely on implementation from `BrowserActivity`), then return to `OnboardingActivity` to finish. ([Figma](https://www.figma.com/design/5QvJbyUBbeonblsjViDIkf/Mobile-Onboarding-AI-First?node-id=173-50969&t=w51SVY4Lvfst0gyJ-1).)

Today's linear onboarding state lives on a fragment-scoped ViewModel — it cannot survive that transition.

## Goals

### Lift linear onboarding state up to `AppScope`

```
Before                                     After
─────────────────────                      ───────────────────────────
FragmentScope                              AppScope
└── BrandDesignUpdatePageVM                └── LinearOnboardingOrchestrator
    └── when (currentDialog) { ... }           ├── state: StateFlow<OnboardingPlanState>
                                               ├── onEvent(StepEvent)
                                               └── ...

Lives only inside OnboardingActivity.       Survives OnboardingActivity ↔ BrowserActivity.
Cannot interleave Browser steps.            Each step declares its host; activities
                                            observe state and hand off when the host
                                            for the current step doesn't match.
```

### Extract onboarding contracts out of `:app`

Place the `LinearOnboardingOrchestrator` + associated models, and move the existing `UserStageStore` (with `AppStage`) and `OnboardingSkipper`, into a new `:onboarding` module.

This contributes to our goal of modularizing the `:app` module (refs [AOI: Modularization - Split App Module](https://app.asana.com/1/137249556945/project/1214119362406643/task/1214399970989433?focus=true)) and unblocks future readers outside `:app` to access onboarding state.

```
onboarding/
├── onboarding-api/
│   ├── LinearOnboardingOrchestrator
│   ├── LinearOnboardingState, LinearStep, StepTransition, etc.
│   ├── UserStageStore  (moved from :app)
│   └── OnboardingSkipper (moved from :app)
│
├── onboarding-impl/
│   └── LinearOnboardingOrchestratorImpl
│
└── :app
    ├── LinearOnboardingPlanProvider (step factories)
    ├── AppUserStageStore impl + Room entity (stays)
    ├── FullOnboardingSkipper impl (stays)
    └── BrandDesignUpdatePageVM (thinned to a renderer adapter)
```

`LinearOnboardingPlanProvider` stays in `:app` because steps depend on flags/values form `SyncAutoRestore`, `DefaultRoleBrowserDialog`, `OnboardingStore`, `DuckChat`, etc. Moving it would force moving its dependency closure — a much wider refactor that we can revisit at a different time.

### Other minor wins

- Provide a single readable place where plan composition expresses itself.
- Make each step a self-contained descriptor (precondition, params, transition rules) — cheaper experiment-driven variation.
- Keep today's user-visible behavior (dialog sequence, pixels, side effects) unchanged.

## Non-goals

- Persistence of orchestrator state across process death — today's flow restarts from scratch on process death; preserved.
- Migrating `WelcomePageViewModel` / `WelcomePage` — soon-to-be-deleted, stays on the legacy in-VM machine. Only `BrandDesignUpdatePageViewModel` / `BrandDesignUpdateWelcomePage` are migrated.
- Changing reactive-phase CTA logic — `CtaViewModel.getHomeCta()` / `getBrowserCta()` keep gating on `AppStage`.
- Cross-host back navigation — back during linear onboarding closes the app, regardless of host.
- New analytics — same pixels, same order as today.

## Public API surface (`:onboarding-api`)

```kotlin
interface LinearOnboardingOrchestrator {
    val state: StateFlow<LinearOnboardingState>

    fun startOnboardingPlan(plan: LinearOnboardingPlan)
    suspend fun onEvent(event: LinearOnboardingEvent)
}

sealed interface LinearOnboardingState {
    data object NotStarted : LinearOnboardingState
    data class InProgress(
        val currentPlan: LinearOnboardingPlan,
        val currentStepIndex: Int,
    ) : LinearOnboardingState {
        val currentStep: LinearOnboardingStep
            get() = currentPlan.steps[currentStepIndex]
    }
    data object Completed : LinearOnboardingState
    data object Skipped : LinearOnboardingState
}

data class LinearOnboardingPlan(val steps: List<LinearOnboardingStep>)

typealias LinearOnboardingStepId = String

interface LinearOnboardingStep {
    val id: LinearOnboardingStepId
    val host: LinearOnboardingHost
    val precondition: suspend () -> Boolean
    val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition
}

enum class LinearOnboardingHost {
    OnboardingActivity,
    BrowserActivity,
}

// Opaque marker. The orchestrator never inspects events; it just routes them.
// Concrete event types live with the plan provider in :app, so consumers outside :app don't inherit onboarding's event vocabulary.
interface LinearOnboardingEvent

sealed interface LinearOnboardingTransition {
    data object Advance : LinearOnboardingTransition                                 // next step in current plan
    data class SwitchTo(val plan: LinearOnboardingPlan) : LinearOnboardingTransition // push frame, run plan from first eligible step
    data object Return : LinearOnboardingTransition                                  // pop frame, resume and step forward on the previous plan
    data object AbortPlan : LinearOnboardingTransition                               // terminates linear as Skipped
    data object Stay : LinearOnboardingTransition                                    // explicit no-op
}

```

**Design notes**
- `LinearOnboardingPlan` needs to be available as soon as the app launches for the first time. To make this possible, plan construction is privacy-config-independent — flag and experiment reads happen lazily inside each step's `suspend precondition`, evaluated when the orchestrator is about to advance onto that step. The alternative (block plan construction until privacy config arrives, or a timeout elapses) would delay the initial onboarding experience. The current design matches today's production behavior: any given step's flag check still risks reading a stale config value, but it's read at the latest possible moment, which is the same window today's flow has.
- `LinearOnboardingOrchestrator` and its models are not coupled to concrete onboarding steps. A plan provider and hosts are responsible for rendering the right dialogs and executing the right actions based on the provided `stepId`. This prevents leaking all available steps outside the hosts that can execute them, and allows us to add/remove steps without modifying the `:onboarding-api` contract.
- The orchestrator self-initialises at construction (see [Lifecycle & rollout](#lifecycle--rollout)): on first injection it reads `AppStage` and starts the main plan if the user is `NEW`. Renderers only observe `state`; they never call `startOnboardingPlan`. `startOnboardingPlan` stays on the interface for tests and explicit overrides — idempotent (no-op unless state is `NotStarted`). The "started exactly once per process" guarantee is structural, not a contract every host has to honour.

## Host coordination

See [`2026-05-06-linear-onboarding-orchestrator-design-simplified-host-coordination.puml`](2026-05-06-linear-onboarding-orchestrator-design-simplified-host-coordination.puml) for the handoff sequence.

- Each activity (`OnboardingActivity`, `BrowserActivity`) observes `orchestrator.state`. When `state.currentStep.host` doesn't match its own host, the activity launches the matching host activity and `finish()`es itself. No central coordinator; both activities run the same observer logic symmetrically.
- Back action (button/gesture) during the linear flow closes the app - this is matching existing behavior.
- Process death is unchanged from today's behaviour: orchestrator state is in-memory, so process kill mid-flow restarts linear from scratch.

## AppStage interaction

Existing readers of `AppStage` keep working unchanged. The orchestrator just writes to `AppStage` at the right moments.

**Writes on terminal transitions:**

| Orchestrator transition | `AppStage` write |
|---|---|
| `NotStarted → InProgress` | none (already `NEW`) |
| `InProgress → Completed` | `userStageStore.stageCompleted(NEW)` → `DAX_ONBOARDING` |
| `InProgress → Skipped` | `onboardingSkipper.markOnboardingAsCompleted()` — writes `hideTips`, dismisses `ADD_WIDGET`, advances to `ESTABLISHED` |

The reactive `DAX_ONBOARDING → ESTABLISHED` write continues to come from `CtaViewModel.completeStageIfDaxOnboardingCompleted()`. The orchestrator only owns the linear portion.

`AppStage` writes complete *before* the orchestrator emits the corresponding terminal state. A renderer reacting to `Completed` by routing into `BrowserActivity` therefore sees `AppStage == DAX_ONBOARDING` (not `NEW`) downstream — `LaunchViewModel`-style stage checks can't accidentally re-enter linear onboarding mid-handoff.

**Init from `AppStage`:**

| `AppStage` at orchestrator init | Initial state | Behaviour |
|---|---|---|
| `NEW` | `NotStarted` | Plan provider builds the main plan; `LaunchViewModel` routes to first step's host |
| `DAX_ONBOARDING` | `Completed` | Orchestrator stays inactive; reactive CTAs own the rest |
| `ESTABLISHED` | `Completed` | Orchestrator stays inactive |

The `DAX_ONBOARDING` / `ESTABLISHED` rows protect existing users (already past linear) from accidentally re-entering it after this lands.

## Lifecycle & rollout

### Orchestrator start

The orchestrator self-initialises. `LinearOnboardingOrchestratorImpl.init` reads `AppStage` and starts the main plan if the user is `NEW`. Renderers do not call `startOnboardingPlan` themselves — they only observe `state`.

```kotlin
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class LinearOnboardingOrchestratorImpl @Inject constructor(
    private val userStageStore: UserStageStore,
    private val planProvider: LinearOnboardingPlanProvider,
    private val onboardingSkipper: OnboardingSkipper,
    private val orchestratorFeature: LinearOnboardingOrchestratorFeature,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : LinearOnboardingOrchestrator {

    private val _state = MutableStateFlow<LinearOnboardingState>(NotStarted)
    override val state: StateFlow<LinearOnboardingState> = _state.asStateFlow()
    private val mutex = Mutex()

    init {
        appScope.launch {
            mutex.withLock {
                if (_state.value is NotStarted &&
                    orchestratorFeature.self().isEnabled() &&
                    userStageStore.isNewUser()
                ) {
                    advanceFrom(planProvider.buildMainPlan(), fromIndex = 0)
                }
            }
        }
    }

    // startOnboardingPlan / onEvent / advanceFrom — see Public API surface.
}
```

`NEW` is the only stage that triggers a start; `DAX_ONBOARDING` / `ESTABLISHED` users keep the orchestrator in `NotStarted` (matching the init table above).

The orchestrator depending on `LinearOnboardingPlanProvider` is a deliberate trade — it pulls plan-construction into the impl rather than having an external boss orchestrate the orchestrator. In return, we eliminate a separate bootstrapper class and tighten the construction-vs-first-activity race window: by the time DI hands the orchestrator to any activity, its `init` has already kicked off plan resolution.

### Kill switch and dual-path window

The migration ships behind a new `LinearOnboardingOrchestratorFeature` toggle (default `INTERNAL`, ramped from there). During the rollout window, `BrandDesignUpdatePageViewModel` carries both paths:

```kotlin
init {
    if (orchestratorFeature.self().isEnabled()) {
        observeOrchestratorState()           // new path — orchestrator owns state
    } else {
        // legacy in-VM state machine (unchanged)
    }
}
```

This roughly doubles the VM's surface area for the duration of the rollout — accepted cost. The legacy block is removed in a tracked cleanup once the toggle is permanently on. The orchestrator's self-init is gated on the same flag (see `init` above), so when the toggle is off the orchestrator stays `NotStarted` and the legacy machine drives the flow unimpeded — no double-driving.

## Appendix: example step factories

A few representative step factories from `LinearOnboardingPlanProvider` (in `:app`), showing how the generic `:onboarding-api` step interface gets specialized for the brand-design flow. Not exhaustive — these cover the load-bearing patterns.

`:app` defines its own event vocabulary, its own dialog vocabulary, and a concrete step type that adds a rendering hook to the generic `LinearOnboardingStep`:

```kotlin
// in :app — domain-specific events the orchestrator never inspects
sealed interface OnboardingEvent : LinearOnboardingEvent {
    data object PrimaryClicked : OnboardingEvent
    data object SecondaryClicked : OnboardingEvent
    data class DefaultBrowserPromptFinished(val isDefaultBrowser: Boolean) : OnboardingEvent
    data class OmnibarTypeSelected(val type: OmnibarType) : OnboardingEvent
    data class InputModeSelected(val withAi: Boolean) : OnboardingEvent
}

// in :app — domain-specific dialogs the renderer observes
sealed interface OnboardingActivityDialog {
    data object IntroAnimation : OnboardingActivityDialog
    data class InitialReinstallUser(val showDuckAiCopy: Boolean) : OnboardingActivityDialog
    data class AddressBarPosition(val showSplitOption: Boolean) : OnboardingActivityDialog
    data class InputScreenPreview(val isSearchDefault: Boolean) : OnboardingActivityDialog
    data object SkipOnboardingOption : OnboardingActivityDialog
    // … other variants for SyncRestore, Initial, ComparisonChart, DefaultBrowser, InputScreen
}

// in :app — concrete step descriptor with rendering hook
data class OnboardingActivityStep(
    override val id: LinearOnboardingStepId,
    override val host: LinearOnboardingHost = LinearOnboardingHost.OnboardingActivity,
    override val precondition: suspend () -> Boolean = { true },
    override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition,
    val resolveDialog: suspend () -> OnboardingActivityDialog,
) : LinearOnboardingStep
```

Selected step factories from `LinearOnboardingPlanProvider`:

```kotlin
@SingleInstanceIn(AppScope::class)
class LinearOnboardingPlanProvider @Inject constructor(/* … */) {

    fun buildMainPlan(): LinearOnboardingPlan = LinearOnboardingPlan(steps = listOf(
        introAnimationStep(),
        // SyncRestore / InitialReinstallUser / Initial are mutually exclusive via preconditions
        initialReinstallUserStep(),
        // ... rest of main path
        addressBarPositionStep(),
        inputScreenPreviewStep(),
    ))

    // Side plan, shared by callers via private val
    private val skipPlan: LinearOnboardingPlan by lazy {
        LinearOnboardingPlan(steps = listOf(skipOnboardingOptionStep()))
    }

    // Simple step: PrimaryClicked → Advance.
    // Modelled as a plan step (not a fragment-side prelude) so it's not re-played
    // when the user returns to OnboardingActivity from a BrowserActivity step.
    private fun introAnimationStep() = OnboardingActivityStep(
        id = "intro_animation",
        resolveDialog = { OnboardingActivityDialog.IntroAnimation },
        transition = { event -> if (event is OnboardingEvent.PrimaryClicked) Advance else Stay },
    )

    // Step with a side-plan entry on Secondary
    private fun initialReinstallUserStep() = OnboardingActivityStep(
        id = "initial_reinstall_user",
        precondition = { !syncAutoRestore.canRestore() && appBuildConfig.isAppReinstall() },
        resolveDialog = { OnboardingActivityDialog.InitialReinstallUser(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            is OnboardingEvent.PrimaryClicked -> Advance
            is OnboardingEvent.SecondaryClicked -> {
                pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                SwitchTo(skipPlan)
            }
            else -> Stay
        }},
    )

    // Step with a state-carrying event (selection persists before Advance)
    private fun addressBarPositionStep() = OnboardingActivityStep(
        id = "address_bar_position",
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },
        resolveDialog = { OnboardingActivityDialog.AddressBarPosition(showSplitOption = isSplitOmnibarEnabled()) },
        transition = { event -> when (event) {
            is OnboardingEvent.OmnibarTypeSelected -> {
                settingsDataStore.omnibarType = event.type
                fireOmnibarSelectionPixel(event.type)
                Stay  // selection alone doesn't advance; PrimaryClicked does
            }
            is OnboardingEvent.PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    // Step gated by both prior selection AND experiment assignment
    private fun inputScreenPreviewStep() = OnboardingActivityStep(
        id = "input_screen_preview",
        precondition = {
            onboardingStore.getInputScreenSelection() == true &&
                duckAiOnboardingExperimentManager.enroll() in setOf(
                    TREATMENT_WITH_DUCK_AI_DEFAULT,
                    TREATMENT_WITH_SEARCH_DEFAULT,
                )
        },
        resolveDialog = {
            val variant = duckAiOnboardingExperimentManager.enroll()
            OnboardingActivityDialog.InputScreenPreview(isSearchDefault = variant == TREATMENT_WITH_SEARCH_DEFAULT)
        },
        transition = { event -> if (event is OnboardingEvent.PrimaryClicked) Advance else Stay },
    )

    // Step in the side plan: Primary terminates the linear flow entirely;
    // Secondary returns to the caller plan (Return pops the call stack →
    // main resumes past the step that did SwitchTo).
    private fun skipOnboardingOptionStep() = OnboardingActivityStep(
        id = "skip_onboarding_option",
        resolveDialog = { OnboardingActivityDialog.SkipOnboardingOption },
        transition = { event -> when (event) {
            is OnboardingEvent.PrimaryClicked -> {
                pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                AbortPlan
            }
            is OnboardingEvent.SecondaryClicked -> {
                pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                Return
            }
            else -> Stay
        }},
    )
}
```

### Renderer adapter and fragment wiring

`BrandDesignUpdatePageViewModel` is the renderer adapter: it observes orchestrator state and translates it into UI-shaped surfaces the fragment renders, and it translates fragment callbacks into concrete `OnboardingEvent`s that go back to the orchestrator. The fragment only knows about `OnboardingActivityDialog` and `Command` — it never sees the orchestrator directly.

```kotlin
// in :app
@ContributesViewModel(FragmentScope::class)
class BrandDesignUpdatePageViewModel @Inject constructor(
    private val orchestrator: LinearOnboardingOrchestrator,
    private val orchestratorFeature: LinearOnboardingOrchestratorFeature,
    /* … */
) : ViewModel() {

    // Dialog the fragment renders. Derived from orchestrator state via the
    // :app-side OnboardingActivityStep#resolveDialog() hook.
    private val _dialogState = MutableStateFlow<OnboardingActivityDialog?>(null)
    val dialogState: StateFlow<OnboardingActivityDialog?> = _dialogState

    // Transient non-dialog signals (Finish, OnboardingSkipped, RequestNotificationPermissions, …)
    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

    init {
        if (orchestratorFeature.self().isEnabled()) {
            observeOrchestratorState()
        } else {
            // legacy in-VM state machine path (unchanged)
        }
    }

    private fun observeOrchestratorState() {
        orchestrator.state.onEach { state ->
            when (state) {
                is InProgress -> {
                    val step = state.currentStep as? OnboardingActivityStep ?: return@onEach
                    _dialogState.value = step.resolveDialog()
                }
                Completed -> { _dialogState.value = null; _commands.send(Command.Finish) }
                Skipped   -> { _dialogState.value = null; _commands.send(Command.OnboardingSkipped) }
                NotStarted -> _dialogState.value = null
            }
        }.launchIn(viewModelScope)
    }

    // Fragment callbacks → concrete events → orchestrator. onEvent is suspending,
    // so each callback launches into viewModelScope.
    fun onPrimaryCtaClicked()                    = emit(OnboardingEvent.PrimaryClicked)
    fun onSecondaryCtaClicked()                  = emit(OnboardingEvent.SecondaryClicked)
    fun onOmnibarTypeSelected(type: OmnibarType) = emit(OnboardingEvent.OmnibarTypeSelected(type))
    fun onInputModeSelected(withAi: Boolean)     = emit(OnboardingEvent.InputModeSelected(withAi))
    fun onDefaultBrowserSet()                    = emit(OnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
    fun onDefaultBrowserNotSet()                 = emit(OnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))

    private fun emit(event: OnboardingEvent) {
        viewModelScope.launch { orchestrator.onEvent(event) }
    }
}
```

Fragment side: a `dialogState` observer for what's currently visible, and a slimmer `commands` observer for transient signals. The fragment dispatches on `OnboardingActivityDialog` once, with special-case rendering for non-dax dialogs:

```kotlin
// in :app
class BrandDesignUpdateWelcomePage : OnboardingPageFragment(/* … */) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.dialogState.flowWithLifecycle(lifecycle, STARTED).onEach { dialog ->
            dialog?.let { configureDaxCta(it) }
        }.launchIn(lifecycleScope)

        viewModel.commands.flowWithLifecycle(lifecycle, STARTED).onEach { command ->
            when (command) {
                Command.Finish                         -> onContinuePressed()
                Command.OnboardingSkipped              -> onSkipPressed()
                Command.RequestNotificationPermissions -> requestNotificationsPermissions()
                /* … other transient signals … */
            }
        }.launchIn(lifecycleScope)
    }

    private fun configureDaxCta(dialog: OnboardingActivityDialog) {
        when (dialog) {
            is OnboardingActivityDialog.IntroAnimation -> playIntroAnimation { viewModel.onPrimaryCtaClicked() }
            is OnboardingActivityDialog.DefaultBrowser -> startActivityForResult(dialog.intent, REQ_DEFAULT_BROWSER_ROLE)
            else -> renderDaxDialog(dialog)
        }
    }
}
```

The full event loop is in [`2026-05-06-linear-onboarding-orchestrator-design-simplified-event-loop.puml`](2026-05-06-linear-onboarding-orchestrator-design-simplified-event-loop.puml) — a single primary tap on `Initial` traced from fragment through viewmodel → orchestrator → step transition → state re-emission → re-render as `ComparisonChart`. The orchestrator never inspects the event content; everything domain-specific stays on the `:app` side of the boundary.

An integration with `BrowserActivity` and the existing Duck.ai onboarding step will be considered separately.

## Appendix: future multi-module step contributions

Today every step factory lives in `:app`'s `LinearOnboardingPlanProvider`. This section sketches a known-good growth path for the day a second module wants to contribute steps — **not committed to this design**, just kept here so we don't paint ourselves into a corner.

Two orthogonal pieces. They can be adopted independently and on demand.

### Step aggregation via `PluginPoint`

`LinearOnboardingPlanProvider` becomes an aggregator over a `PluginPoint<LinearOnboardingStepContribution>`. Each contributing module declares its own steps; the planner just orders and flattens.

```kotlin
// :onboarding-api — one-time addition
@ContributesPluginPoint(AppScope::class)
interface LinearOnboardingStepContribution {
    suspend fun steps(): List<LinearOnboardingStep>
}
```

```kotlin
// :duck-ai-onboarding-impl (example contributor)
@ContributesMultibinding(AppScope::class)
@PriorityKey(200)
class DuckAiOnboardingSteps @Inject constructor(/* …feature-local deps… */) : LinearOnboardingStepContribution {
    override suspend fun steps() = listOf(duckAiPromptStep(), duckAiFireCtaStep())
}
```

```kotlin
// :app — planner becomes a thin aggregator
@SingleInstanceIn(AppScope::class)
class LinearOnboardingPlanProvider @Inject constructor(
    private val contributions: PluginPoint<LinearOnboardingStepContribution>,
) {
    suspend fun buildMainPlan() = LinearOnboardingPlan(
        steps = contributions.getPlugins().flatMap { it.steps() },
    )
}
```

Step ordering is provided by `@PriorityKey(n)` (lower = earlier), matching the project-wide pattern from `:lint-rules` and `feature-toggles-internal`. The api gains one interface and never grows when new contributors land.

### Cross-module data flow via typed context slots

When step A in module X writes data that step B in module Y reads, neither `:onboarding-api` nor `:app` should learn the data type. Typed-slot context:

```kotlin
// :onboarding-api — one-time addition
interface OnboardingContextSlot<T : Any> {
    val name: String  // debug only — slot identity is via singleton object
}

interface OnboardingContext {
    operator fun <T : Any> get(slot: OnboardingContextSlot<T>): T?
    operator fun <T : Any> set(slot: OnboardingContextSlot<T>, value: T)
}

// Step transitions and BrowserActivityStep.resolveAction gain a context parameter:
interface LinearOnboardingStep {
    val transition: suspend (LinearOnboardingEvent, OnboardingContext) -> LinearOnboardingTransition
}
```

The slot definitions live in a thin shared seam that both producer and consumer modules can depend on (an existing `*-api` module — e.g., `:duckchat-api` — or a new `*-onboarding-api`):

```kotlin
// :duck-ai-onboarding-api  (shared seam)
object DuckAiPromptSlot : OnboardingContextSlot<String> {
    override val name = "duck_ai.prompt"
}
```

```kotlin
// :input-screen-onboarding-impl  (producer)
private fun inputScreenStep() = OnboardingActivityStep(
    id = "input_screen_preview",
    resolveDialog = { OnboardingActivityDialog.InputScreenPreview(false) },
    transition = { event, ctx -> when (event) {
        is InputScreenEvent.PromptSubmitted -> {
            ctx[DuckAiPromptSlot] = event.prompt
            Advance
        }
        else -> Stay
    }},
)

// :duck-ai-onboarding-impl  (consumer)
private fun duckAiStep() = BrowserActivityStep(
    id = "duck_ai",
    resolveAction = { ctx ->
        val prompt = ctx[DuckAiPromptSlot]
            ?: error("duck_ai requires DuckAiPromptSlot to be set upstream")
        BrowserActivityAction.OpenDuckChat(buildUrl(prompt))
    },
    transition = { event, _ -> if (event is OnboardingEvent.DuckAiFireCompleted) Advance else Stay },
)
```

`OnboardingContext` is an `AppScope` singleton in `:onboarding-impl` — typed `ConcurrentHashMap<OnboardingContextSlot<*>, Any>` keyed by slot identity. Slots are singletons (`object`), so identity is the key and there's no string-collision risk between modules.

### When to adopt

Both patterns are zero-cost to think about, non-zero to implement.

| Trigger | Action |
|---|---|
| Single module contributes all steps | Don't adopt. Keep the planner concrete. |
| Second module wants to contribute its own steps | Adopt the `PluginPoint` aggregation. Cheap, additive. |
| Cross-module data flow between contributed steps | Adopt typed-slot context. Touches every step's `transition` signature — pay this tax only when there is a real producer/consumer pair across modules. |

Until the second contributor arrives, the planner stays a concrete class in `:app` with a private typed context for any cross-step data (a `data class` held in a `MutableStateFlow` on the planner singleton, written from step transitions, read from `resolveAction` / `resolveDialog`). The growth path above is what we'd reach for, not what we're committing to today.