# Linear Onboarding Orchestrator

## Overview

Promote the linear-onboarding state machine that today lives inside `BrandDesignUpdatePageViewModel` (and `WelcomePageViewModel`) to an `AppScope` orchestrator. This will unblock interleaving of linear-onboarding steps across multiple activities/fragments.

This is in response to a requirement in [Android: AI onboarding for custom store listings](https://app.asana.com/1/137249556945/project/1208671518894266/task/1213551217308374?focus=true) where the goal is to show a subset of onboarding steps from the existing `OnboardingActivity`, move on to some Duck.ai onboarding steps in `BrowserActivity` before eventually coming back to the `OnboardingActivity` to finish the remaining steps. Check out [this Figma flow](https://www.figma.com/design/5QvJbyUBbeonblsjViDIkf/Mobile-Onboarding-AI-First?node-id=173-50969&t=w51SVY4Lvfst0gyJ-1) for reference.

The proximate motivation is the interleaving requirement. At the same time, the Tech Design proposes a structural investment where each step is a self-contained descriptor, and the future investment in reorganizing step's order, adding experiment-driven variations are easier to implement and cheaper to maintain, instead of growing `when` arms of the existing state machine.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/LinearOnboardingOrchestrator.kt` | **New** — `AppScope` `@SingleInstanceIn` class holding the plan, current step, transitions; writes `AppStage` on terminal transitions |
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/LinearOnboardingPlanProvider.kt` | **New** — builds the step registry, contains all step factory methods |
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/LinearStep.kt` | **New** — sealed class hierarchy (`IsolatedContext`, `BrowserContext`), `StepEvent`, `StepTransition` |
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/OnboardingPlanState.kt` | **New** — `NotStarted` / `InProgress` / `Completed` / `Skipped` |
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/LinearOnboardingOrchestratorFeature.kt` | **New** — `@ContributesRemoteFeature` toggle gating rollout |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdatePageViewModel.kt` | Add orchestrator-observer codepath gated on the feature flag; legacy `when (currentDialog)` retained until phase 4 cleanup |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingActivity.kt` | Add host-transition observer (self-finishes if current step's host is `BrowserContext`) |
| `app/src/main/java/com/duckduckgo/app/browser/BrowserActivity.kt` | Add host-transition observer (self-finishes if current step's host is `IsolatedContext`); add back-press intercept that closes app while linear flow is active |
| `app/src/main/java/com/duckduckgo/app/cta/ui/CtaViewModel.kt` | Add `shouldSuppressForLinearOnboarding()` gate at the top of `refreshCta`, `getFireDialogCta`, `getSiteSuggestionsDialogCta`, `getEndStaticDialogCta` |
| `app/src/main/java/com/duckduckgo/app/launch/LaunchViewModel.kt` | When `isNewUser()`, route to host indicated by `orchestrator.currentStepHost()` instead of unconditionally to `OnboardingActivity` |

## Goals

- Lift the linear-onboarding state machine to `AppScope` so it survives activity transitions
- Provide a single readable place where plan composition expresses itself
- Make each step a self-contained descriptor (precondition, params, transition rules) that can be added, removed, or experiment-gated locally
- Keep today's user-visible behavior (dialog sequence, pixels, side effects) unchanged
- Land a structural seam for the future step transitions between the isolated onboarding context and browser onboarding context

## Non-goals

- Persistence of orchestrator state across process death — today's flow restarts from scratch on process death; that behavior is preserved deliberately
- Designing the `BrowserContext` step renderer or `BrowserConstraintMode` descriptor — deferred to a follow-up TD
- Migrating `WelcomePageViewModel` / `WelcomePage` — they are about to be superseded by the brand-design variant of the onboarding anyway, so they stay on the legacy in-VM state machine until removed. Only `BrandDesignUpdatePageViewModel` / `BrandDesignUpdateWelcomePage` get migrated.
- Changing reactive-phase CTA logic from `CtaViewModel.getHomeCta()` / `getBrowserCta()` that's presented in the `BrowserTabFragment` — those continue to gate on `AppStage` as today
- Cross-host back-button navigation (e.g., back from a `IsolatedContext` step to a previous `BrowserContext` step) — back during linear closes the app, regardless of the current host
- Introducing analytics beyond the existing pixels — orchestrator must fire the same pixels in the same order as today's flow

## Architecture summary

```
┌─ AppScope ───────────────────────────────────────────────────────────┐
│                                                                       │
│   LinearOnboardingPlanProvider                                        │
│         │   buildPlan(): LinearOnboardingPlan                        │
│         │   step factories with precondition + resolveParams +       │
│         │   transition lambdas (each reads fresh state at call time) │
│         ▼                                                            │
│   LinearOnboardingOrchestrator    ─── state: StateFlow<…>            │
│         │   onEvent(StepEvent)                                       │
│         │   requestFirstStep()    (called once by welcome page)      │
│         │   currentStepHost(): Host                                  │
│         │   shouldSuppressForLinearOnboarding(): Boolean             │
│         │                                                            │
│         └──► AppStage facade (Room, unchanged)                       │
│              writes stageCompleted(NEW)/(DAX_ONBOARDING) on          │
│              terminal transitions                                    │
└──────────────────────────────────────────────────────────────────────┘
        │                                          │
        ▼                                          ▼
┌─ OnboardingActivity ────────────┐      ┌─ BrowserActivity ─────────────┐
│                                 │      │                               │
│  Host observer: self-finishes   │      │  Host observer: self-finishes │
│   if current step is            │      │   if current step is          │
│   BrowserContext                │      │   IsolatedContext             │
│                                 │      │                               │
│  Back-press intercept:          │      │  Back-press intercept:        │
│   while linear active,          │      │   while linear active,        │
│   finishAndRemoveTask()         │      │   finishAndRemoveTask()       │
│                                 │      │                               │
│  BrandDesignUpdatePageVM        │      │  CtaViewModel.refreshCta() &  │
│   (flag-gated): observe         │      │   friends suppress while      │
│   orchestrator.state, emit      │      │   on a BrowserContext step    │
│   ShowXxxDialog commands        │      │                               │
│                                 │      │  BrowserContext renderer:     │
│  WelcomePage / BrandDesign      │      │   DEFERRED (first project to  │
│  page Fragment unchanged:       │      │   introduce a BrowserContext  │
│   configureDaxCta(type)         │      │   step owns its design)       │
└─────────────────────────────────┘      └───────────────────────────────┘
```

Three new units (orchestrator, plan provider, step model) and four thin observers (two activities, one viewmodel, `CtaViewModel`).

## Step model

```kotlin
typealias StepId = String

sealed interface LinearStep {
    val id: StepId
    val precondition: suspend () -> Boolean
    val transition: suspend (StepEvent) -> StepTransition

    data class IsolatedContext(
        override val id: StepId,
        val type: PreOnboardingDialogType,
        val resolveParams: suspend () -> PreOnboardingParams = { PreOnboardingParams() },
        override val precondition: suspend () -> Boolean = { true },
        override val transition: suspend (StepEvent) -> StepTransition,
    ) : LinearStep

    data class BrowserContext(
        override val id: StepId,
        override val precondition: suspend () -> Boolean = { true },
        override val transition: suspend (StepEvent) -> StepTransition,
        // descriptor field deliberately omitted — the follow-up task to introduce
        // a BrowserContext step adds the renderer descriptor type here
    ) : LinearStep
}

data class PreOnboardingParams(
    val showSplitOption: Boolean = false,
    val showDuckAiCopy: Boolean = false,
    val defaultBrowserIntent: Intent? = null,
    val isSearchDefault: Boolean = false,         // for INPUT_SCREEN_PREVIEW
)

sealed interface StepEvent {
    data object PrimaryClicked : StepEvent
    data object SecondaryClicked : StepEvent
    data class SystemResult(val ok: Boolean) : StepEvent
    data class OmnibarTypeSelected(val type: OmnibarType) : StepEvent
    data class InputModeSelected(val withAi: Boolean) : StepEvent
}

sealed interface StepTransition {
    data object Advance : StepTransition          // walk forwardOrder, skip ineligible
    data class GotoStep(val target: StepId) : StepTransition  // ignores precondition
    data object AbortPlan : StepTransition        // terminates linear as Skipped
    data object Stay : StepTransition             // explicit no-op
}

data class LinearOnboardingPlan(
    val steps: Map<StepId, LinearStep>,   // every reachable step including side branches
    val forwardOrder: List<StepId>,       // the linear forward path
)
```

### Why this shape

- **Host encoded by-construction**, not as a separate field. `IsolatedContext` is rendered against an isolated full-screen surface (today: `OnboardingActivity`); `BrowserContext` is rendered against the live browser. The variant carries that distinction at compile time.
- **`precondition` and `resolveParams` are `suspend` and read fresh state at call time.** This is the load-bearing decision for the config-staleness story — see [State and persistence](#state-and-persistence).
- **`transition` is a per-step function returning a `StepTransition`.** Each step's flow-control logic is local to its descriptor instead of co-mingled in a giant centralised `when`.
- **Plan is `Map` + ordered `List`.** Side branches (`SKIP_ONBOARDING_OPTION`) live in the map for `GotoStep` lookup but not in `forwardOrder`, so `Advance` never lands on them. Cleaner than overloading `precondition` to mean both "off the main path" and "ineligible right now".

### Today's flow expressed in this model

```kotlin
@SingleInstanceIn(AppScope::class)
class LinearOnboardingPlanProvider @Inject constructor(
    private val syncAutoRestore: SyncAutoRestore,
    private val appBuildConfig: AppBuildConfig,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val duckAiOnboardingExperimentManager: DuckAiOnboardingExperimentManager,
    private val onboardingStore: OnboardingStore,
    private val settingsDataStore: SettingsDataStore,
    private val appInstallStore: AppInstallStore,
    private val duckChat: DuckChat,
    private val pixel: Pixel,
    private val context: Context,
) {
    fun buildPlan(): LinearOnboardingPlan = LinearOnboardingPlan(
        steps = mapOf(
            ID_SYNC_RESTORE           to syncRestoreStep(),
            ID_INITIAL_REINSTALL_USER to initialReinstallUserStep(),
            ID_INITIAL                to initialStep(),
            ID_COMPARISON_CHART       to comparisonChartStep(),
            ID_DEFAULT_BROWSER        to defaultBrowserStep(),
            ID_ADDRESS_BAR_POSITION   to addressBarPositionStep(),
            ID_INPUT_SCREEN           to inputScreenStep(),
            ID_INPUT_SCREEN_PREVIEW   to inputScreenPreviewStep(),
            ID_SKIP_ONBOARDING_OPTION to skipOnboardingOptionStep(),
        ),
        forwardOrder = listOf(
            ID_SYNC_RESTORE,
            ID_INITIAL_REINSTALL_USER,
            ID_INITIAL,
            ID_COMPARISON_CHART,
            ID_DEFAULT_BROWSER,
            ID_ADDRESS_BAR_POSITION,
            ID_INPUT_SCREEN,
            ID_INPUT_SCREEN_PREVIEW,
        ),
    )

    private fun syncRestoreStep() = IsolatedContext(
        id = ID_SYNC_RESTORE,
        type = PreOnboardingDialogType.SYNC_RESTORE,
        precondition = { syncAutoRestore.canRestore() },
        transition = { event -> when (event) {
            PrimaryClicked -> {
                pixel.fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
                syncAutoRestore.restoreSyncAccount()
                Advance
            }
            SecondaryClicked -> {
                pixel.fire(PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE, type = Unique())
                GotoStep(ID_SKIP_ONBOARDING_OPTION)
            }
            else -> Stay
        }},
    )

    private fun initialReinstallUserStep() = IsolatedContext(
        id = ID_INITIAL_REINSTALL_USER,
        type = PreOnboardingDialogType.INITIAL_REINSTALL_USER,
        precondition = { !syncAutoRestore.canRestore() && appBuildConfig.isAppReinstall() },
        resolveParams = { PreOnboardingParams(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            SecondaryClicked -> {
                pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                GotoStep(ID_SKIP_ONBOARDING_OPTION)
            }
            else -> Stay
        }},
    )

    private fun initialStep() = IsolatedContext(
        id = ID_INITIAL,
        type = PreOnboardingDialogType.INITIAL,
        precondition = { !syncAutoRestore.canRestore() && !appBuildConfig.isAppReinstall() },
        resolveParams = { PreOnboardingParams(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun comparisonChartStep() = IsolatedContext(
        id = ID_COMPARISON_CHART,
        type = PreOnboardingDialogType.COMPARISON_CHART,
        resolveParams = { PreOnboardingParams(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            PrimaryClicked -> {
                val isDefault = !defaultRoleBrowserDialog.shouldShowDialog()
                pixel.fire(PREONBOARDING_CHOOSE_BROWSER_PRESSED, mapOf(DEFAULT_BROWSER to isDefault.toString()))
                Advance  // forward walk skips DEFAULT_BROWSER and ADDRESS_BAR_POSITION via their preconditions
            }
            else -> Stay
        }},
    )

    private fun defaultBrowserStep() = IsolatedContext(
        id = ID_DEFAULT_BROWSER,
        type = PreOnboardingDialogType.DEFAULT_BROWSER,    // new enum value (today the system intent is launched from inside COMPARISON_CHART's primary)
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },
        resolveParams = {
            PreOnboardingParams(defaultBrowserIntent = defaultRoleBrowserDialog.createIntent(context))
        },
        transition = { event -> when (event) {
            is SystemResult -> {
                defaultRoleBrowserDialog.dialogShown()
                appInstallStore.defaultBrowser = event.ok
                if (event.ok) appInstallStore.wasEverDefaultBrowser = true
                pixel.fire(
                    if (event.ok) DEFAULT_BROWSER_SET else DEFAULT_BROWSER_NOT_SET,
                    mapOf(DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
                )
                Advance
            }
            else -> Stay
        }},
    )

    private fun addressBarPositionStep() = IsolatedContext(
        id = ID_ADDRESS_BAR_POSITION,
        type = PreOnboardingDialogType.ADDRESS_BAR_POSITION,
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },  // matches today's "skip if shouldShowDialog == false"
        resolveParams = { PreOnboardingParams(showSplitOption = isSplitOmnibarEnabled()) },
        transition = { event -> when (event) {
            is OmnibarTypeSelected -> {
                settingsDataStore.omnibarType = event.type
                when (event.type) {
                    SINGLE_BOTTOM -> pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
                    SPLIT -> pixel.fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
                    SINGLE_TOP -> { /* default, no pixel */ }
                }
                Stay  // selection alone doesn't advance; PrimaryClicked does
            }
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun inputScreenStep() = IsolatedContext(
        id = ID_INPUT_SCREEN,
        type = PreOnboardingDialogType.INPUT_SCREEN,
        precondition = {
            defaultRoleBrowserDialog.shouldShowDialog() &&
                    androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()
        },
        resolveParams = { PreOnboardingParams(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            is InputModeSelected -> {
                onboardingStore.storeInputScreenSelection(event.withAi)
                duckChat.setCosmeticInputScreenUserSetting(event.withAi)
                Stay
            }
            PrimaryClicked -> {
                val withAi = onboardingStore.getInputScreenSelection() == true
                if (withAi) {
                    pixel.fire(PREONBOARDING_AICHAT_SELECTED)
                    inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = appBuildConfig.isAppReinstall())
                } else {
                    pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
                }
                Advance
            }
            else -> Stay
        }},
    )

    private fun inputScreenPreviewStep() = IsolatedContext(
        id = ID_INPUT_SCREEN_PREVIEW,
        type = PreOnboardingDialogType.INPUT_SCREEN_PREVIEW,
        precondition = {
            onboardingStore.getInputScreenSelection() == true &&
                    duckAiOnboardingExperimentManager.enroll() in setOf(
                TREATMENT_WITH_DUCK_AI_DEFAULT, TREATMENT_WITH_SEARCH_DEFAULT,
            )
        },
        resolveParams = {
            val variant = duckAiOnboardingExperimentManager.enroll()
            PreOnboardingParams(isSearchDefault = variant == TREATMENT_WITH_SEARCH_DEFAULT)
        },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun skipOnboardingOptionStep() = IsolatedContext(
        id = ID_SKIP_ONBOARDING_OPTION,
        type = PreOnboardingDialogType.SKIP_ONBOARDING_OPTION,
        // precondition unused — only reachable via GotoStep
        transition = { event -> when (event) {
            PrimaryClicked -> {
                pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                duckChat.setInputScreenUserSetting(true)
                AbortPlan
            }
            SecondaryClicked -> {
                pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                GotoStep(ID_COMPARISON_CHART)
            }
            else -> Stay
        }},
    )

    private suspend fun isDuckAiCopyEnabled(): Boolean = /* same logic as today's WelcomePageViewModel */
        private suspend fun isSplitOmnibarEnabled(): Boolean = /* same logic as today */
}
```

Two notes on this mapping:

1. **`DEFAULT_BROWSER` is a first-class step.** Today the role-manager intent is launched from inside `WelcomePageViewModel.onPrimaryCtaClicked(COMPARISON_CHART)` and `onDefaultBrowserSet/NotSet` fires the next state. In the orchestrator model it's a discrete step with `Intent` resolved via `resolveParams` and result delivered via `SystemResult` event. The fragment-side rendering is unchanged (no visible separate "step"); it's only an architectural unit. This becomes important when L→C→L→C lands and `DEFAULT_BROWSER` is the second isolated phase the user re-enters.

2. **Pixel firing has moved into `transition` lambdas.** Today's `BrandDesignUpdatePageViewModel` has pixel calls scattered across `onPrimaryCtaClicked`, `onSecondaryCtaClicked`, `fireDialogShownPixel`, etc. In the orchestrator model, decision-time pixels live with the decision (in `transition`); show-time pixels (`fireDialogShownPixel` equivalents) stay in the renderer (the viewmodel observing `state` transitions emits the show pixel for the new step). The pixel sequence is preserved.

## State and persistence

```kotlin
sealed interface OnboardingPlanState {
    data object NotStarted : OnboardingPlanState
    data class InProgress(val currentStepId: StepId) : OnboardingPlanState
    data object Completed : OnboardingPlanState
    data object Skipped : OnboardingPlanState
}
```

State lives entirely in a `MutableStateFlow<OnboardingPlanState>` inside the orchestrator. **No DataStore, no Room table, no persistence of `currentStepId`.** Today's flow restarts from scratch on process death; that behavior is preserved.

### `AppStage` migration

`AppStage` (Room-backed enum: `NEW` / `DAX_ONBOARDING` / `ESTABLISHED`) stays as today's source of truth for "phase of onboarding". Existing readers (`LaunchViewModel.isNewUser`, `SystemSearchViewModel`, `BrowserAdditionalPixelParams`, `CtaViewModel.daxOnboardingActive`, `OnboardingFlowCheckerImpl`) don't change.

The orchestrator becomes a *writer* of `AppStage` on terminal transitions:

| Orchestrator transition | `AppStage` write |
|---|---|
| `NotStarted → InProgress(...)` | none (already `NEW`) |
| `InProgress → Completed` | `userStageStore.stageCompleted(AppStage.NEW)` → moves to `DAX_ONBOARDING` |
| `InProgress → Skipped` | `userStageStore.stageCompleted(AppStage.NEW)` then `userStageStore.stageCompleted(AppStage.DAX_ONBOARDING)` → reaches `ESTABLISHED`; plus `settingsDataStore.hideTips = true` (matching `FullOnboardingSkipper.markOnboardingAsCompleted`) |

Reactive completion in `CtaViewModel.completeStageIfDaxOnboardingCompleted` continues to write `DAX_ONBOARDING → ESTABLISHED`. The orchestrator never writes that transition itself, it is only handling the linear portion of the onboarding flow.

### Process / lifecycle behavior

**On orchestrator init (`@Inject` resolution after process start):**

1. Read `AppStage` from Room.
2. If `AppStage == ESTABLISHED`: state = `Completed`, orchestrator never activates.
3. If `AppStage == DAX_ONBOARDING`: state = `Completed`, orchestrator never activates (linear is past, reactive owns the rest).
4. If `AppStage == NEW`: state = `NotStarted`. Plan registry is built lazily on first `requestFirstStep()` call from the welcome page (so injection elsewhere doesn't trigger flag/state reads).

**On `requestFirstStep()` (called by `BrandDesignUpdatePageViewModel` after the welcome animation and notification permission flow complete):**

- Build the plan registry (synchronous, no config wait — preconditions evaluate fresh state lazily at advance time, see [Config staleness](#config-staleness)).
- Walk `plan.forwardOrder`, evaluate each `precondition()` until the first eligible step is found.
- Set `InProgress(firstEligibleStepId)`.

**On activity transitions mid-flow:**

- AppScope orchestrator stays alive across `OnboardingActivity ↔ BrowserActivity`.
- Each activity has a host-transition observer (see [Activity transitions](#activity-transitions-and-reactive-phase-handoff)) that finishes the current activity and launches the other when the current step's host doesn't match.

**On process death mid-flow:**

- Orchestrator state is lost (in-memory).
- On next launch: `AppStage == NEW` (Room-backed, durable), state = `NotStarted`, user starts linear onboarding from the first eligible step.
- Same outcome as "process died at any point during the existing flow". Not a regression.

### Config staleness

Plan registry is **synchronous and config-independent** at build time. This is the central design choice that addresses config-loading-during-onboarding without introducing an explicit "wait for config" state.

The privacy config feature flags (`androidBrowserConfigFeature.*`, `duckAiOnboardingExperimentManager.enroll()`) are read inside the `precondition` and `resolveParams` lambdas of each step. These lambdas execute `suspend` at advance time — i.e., the moment the orchestrator is about to land on the step. This is matching the existing behavior.

## Activity transitions and reactive-phase handoff

### Host-transition model

Finish-and-relaunch on every host change. Each activity observes orchestrator state and self-terminates when the current step's host doesn't match its own. No central transition controller.

Reasoning: matches today's `OnboardingActivity → BrowserActivity` pattern; no new component; state-derived (recoverable under activity recreation); on `BrowserContext` steps we don't expect users to accumulat any meaningful state, so finish-and-relaunch loses nothing.

```kotlin
// OnboardingActivity
private var hostTransitionInitiated = false

private fun observeOrchestratorForHostTransitions() {
    orchestrator.state.flowWithLifecycle(lifecycle, STARTED).onEach { state ->
        val expectedHost = state.expectedHost(orchestrator.planRegistry)
        if (expectedHost != Host.IsolatedContext && !hostTransitionInitiated) {
            hostTransitionInitiated = true
            startActivity(BrowserActivity.intent(this))
            finish()
        }
    }.launchIn(lifecycleScope)
}
```

`BrowserActivity` runs the symmetric observer. The `hostTransitionInitiated` guard prevents double-firing within a single activity's lifetime; the transient race during a transition (both activities briefly `STARTED`) resolves cleanly because Android's activity launch is idempotent at the task level.

```kotlin
fun OnboardingPlanState.expectedHost(plan: LinearOnboardingPlan): Host? = when (this) {
    is InProgress -> when (plan.steps.getValue(currentStepId)) {
        is IsolatedContext -> Host.IsolatedContext
        is BrowserContext -> Host.BrowserContext
    }
    Completed, Skipped -> Host.BrowserContext     // post-linear lives in BrowserActivity
    NotStarted -> null                            // shouldn't be observed in foreground
}
```

### Reactive-phase handoff

When orchestrator state moves to `Completed` or `Skipped`, `expectedHost` resolves to `Host.BrowserContext`:

- If `OnboardingActivity` is foreground: it self-terminates, `BrowserActivity` launches. Matches today.
- If `BrowserActivity` is foreground (last step was a `BrowserContext`): no transition.

Inside `BrowserActivity`, the reactive CTA path takes over via existing `CtaViewModel.refreshCta()` calls. Suppression while linear-active is handled in `CtaViewModel`, not in `BrowserTabFragment`.

```kotlin
// in CtaViewModel
private suspend fun shouldSuppressForLinearOnboarding(): Boolean {
    val state = orchestrator.state.value
    if (state !is InProgress) return false
    val step = orchestrator.planRegistry.steps[state.currentStepId] ?: return false
    return step is BrowserContext
}

suspend fun refreshCta(...): Cta? = withContext(dispatcher) {
    if (shouldSuppressForLinearOnboarding()) return@withContext null
    if (isBrowserShowing) getBrowserCta(site, detectedRefreshPatterns) else getHomeCta()
}

// same gate added to getFireDialogCta, getSiteSuggestionsDialogCta, getEndStaticDialogCta
```

`BrowserTabFragment` is unchanged regarding orchestrator awareness. It calls `viewModel.refreshCta()` as today; the suppression is opaque to it. Today's `daxOnboardingActive()` checks inside `getHomeCta` / `getBrowserCta` continue to work — they return `false` while `AppStage == NEW`, naturally suppressing the dax CTAs. The new `shouldSuppressForLinearOnboarding()` gate covers the cases `daxOnboardingActive()` doesn't (widget CTA, per-site CTAs in `getBrowserCta`).

### Back button

Back during the linear flow closes the app. No exception, no dismiss event, no abort. The only escape from the linear flow is the explicit `SKIP_ONBOARDING_OPTION` side branch (entered via secondary CTA from `INITIAL_REINSTALL_USER` or `SYNC_RESTORE`).

```kotlin
// in OnboardingActivity / BrowserActivity
onBackPressedDispatcher.addCallback(this) {
    if (orchestrator.state.value is InProgress) {
        finishAndRemoveTask()
    } else {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
    }
}
```

When `OnboardingPlanState` is `NotStarted` / `Completed` / `Skipped`, the callback disables and the host's normal back behavior applies. `OnboardingActivity` today has a "rewind ViewPager" `onBackPressed` override — that becomes moot (the brand-design pager only ever has one page).

## Renderer adapter

`BrandDesignUpdatePageViewModel` becomes a thin adapter between the orchestrator's state and the fragment's existing `Show*Dialog` commands. Conceptually:

```kotlin
init {
    if (linearOnboardingOrchestratorFeature.self().isEnabled()) {
        observeOrchestratorState()
    } else {
        // existing in-VM state machine path (unchanged)
    }
}

private fun observeOrchestratorState() {
    orchestrator.state.onEach { state ->
        when (state) {
            is InProgress -> emitShowDialogForCurrentStep(state.currentStepId)
            Completed, Skipped -> _commands.send(Command.Finish)  // (or OnboardingSkipped)
            NotStarted -> { /* welcome animation playing — no action */ }
        }
    }.launchIn(viewModelScope)
}

private suspend fun emitShowDialogForCurrentStep(stepId: StepId) {
    val step = orchestrator.planRegistry.steps[stepId] as? IsolatedContext ?: return
    val params = step.resolveParams()
    val command = when (step.type) {
        SYNC_RESTORE -> Command.ShowSyncRestoreDialog
        INITIAL_REINSTALL_USER -> Command.ShowInitialReinstallUserDialog(showDuckAiCopy = params.showDuckAiCopy)
        INITIAL -> Command.ShowInitialDialog(showDuckAiCopy = params.showDuckAiCopy)
        COMPARISON_CHART -> Command.ShowComparisonChart(showDuckAiCopy = params.showDuckAiCopy)
        DEFAULT_BROWSER -> Command.ShowDefaultBrowserDialog(intent = params.defaultBrowserIntent!!)
        ADDRESS_BAR_POSITION -> Command.ShowAddressBarPositionDialog(showSplitOption = params.showSplitOption)
        INPUT_SCREEN -> Command.ShowInputScreenDialog(showDuckAiCopy = params.showDuckAiCopy)
        INPUT_SCREEN_PREVIEW -> Command.ShowInputScreenPreviewDialog(/* … */)
        SKIP_ONBOARDING_OPTION -> Command.ShowSkipOnboardingOption
    }
    _commands.send(command)
    fireDialogShownPixel(step.type)  // existing show-time pixel logic
}

// fragment-callback routing
fun onPrimaryCtaClicked() = orchestrator.onEvent(StepEvent.PrimaryClicked)
fun onSecondaryCtaClicked() = orchestrator.onEvent(StepEvent.SecondaryClicked)
fun onAddressBarPositionOptionSelected(type: OmnibarType) = orchestrator.onEvent(StepEvent.OmnibarTypeSelected(type))
fun onInputScreenOptionSelected(withAi: Boolean) = orchestrator.onEvent(StepEvent.InputModeSelected(withAi))
fun onDefaultBrowserSet() = orchestrator.onEvent(StepEvent.SystemResult(ok = true))
fun onDefaultBrowserNotSet() = orchestrator.onEvent(StepEvent.SystemResult(ok = false))

// trigger the first step after welcome animation completes (called by the fragment, replaces today's loadDaxDialog logic)
fun loadDaxDialog() {
    if (linearOnboardingOrchestratorFeature.self().isEnabled()) {
        viewModelScope.launch { orchestrator.requestFirstStep() }
    } else {
        // existing in-VM logic (unchanged)
    }
}
```

The fragment (`BrandDesignUpdateWelcomePage`) is unchanged — it keeps calling `viewModel.onPrimaryCtaClicked()` etc. and observing the same `Command` flow.

## Rollout

### Feature flag

`@ContributesRemoteFeature(scope = AppScope::class, featureName = "linearOnboardingOrchestrator")` with a `self()` toggle. Default INTERNAL during build-out. Evaluated once at `BrandDesignUpdatePageViewModel` init.

When the flag is off:
- `BrandDesignUpdatePageViewModel` runs its existing in-VM `when (currentDialog)` path verbatim
- `LinearOnboardingOrchestrator.state` stays at `NotStarted`
- `CtaViewModel.shouldSuppressForLinearOnboarding()` returns false (state never leaves `NotStarted`)
- Activity host-transition observers never trigger (always see `NotStarted`)

So the flag controls one thing: which codepath the brand-design viewmodel runs. Everything else is no-op-by-construction when disabled. Killswitch-safe.

### Phases

| Phase | Action | Gate |
|---|---|---|
| **1. Build** | Implement orchestrator, plan provider, step descriptors mirroring today's brand-design behavior. Wire optional observers in viewmodel, both activities, `CtaViewModel`. | Flag off |
| **2. Internal validation** | Flag on for internal builds. Walk through every plan branch. Pixel-parity check against existing flow. | Internal/dogfood |
| **3. Gradual public ramp** | 1% → 10% → 50% → 100%. Monitor onboarding-completion pixel funnels. | Remote config |
| **4. Cleanup** | At 100%, delete the in-VM state machine from `BrandDesignUpdatePageViewModel`. Remove the flag check. The viewmodel becomes a pure orchestrator-state observer. | — |
| **5. L→C→L→C lands** | Follow-up project introducing linear-in-browser steps adds `BrowserContext` step descriptors, defines its descriptor type, adds the rendering hook. | Separate flag |

Phases 1–3 carry transient tech debt (two codepaths in parallel inside `BrandDesignUpdatePageViewModel`). Acceptable as a rollout cost. Phase 4 removes it.

This work does not block, and is not blocked by, the `WelcomePage` deprecation workstream. They run in parallel; both want to land before phase 5.

## Testing

### Unit tests on the orchestrator core

- `LinearOnboardingPlanProvider.buildPlan()` produces the expected registry. Property assertion: every step in `forwardOrder` exists in `steps`. `SKIP_ONBOARDING_OPTION` is in `steps` but not in `forwardOrder`.
- Each step's `precondition` evaluation under combinations of feature flags and stored state. Test fixtures parameterised by `canRestore` × `isReinstall` × `showInputScreen` × experiment variant.
- Each step's `transition(event)` returns the expected `StepTransition` for each `StepEvent`; un-handled events return `Stay`.
- `LinearOnboardingOrchestrator.advanceForward()` walks past ineligible steps and lands on the first eligible.
- `LinearOnboardingOrchestrator.onEvent()` dispatches to current step's transition; `Advance` walks forward, `GotoStep` lands directly (precondition ignored), `AbortPlan` writes `AppStage = ESTABLISHED` + `hideTips`, `Stay` is a no-op.
- `AppStage` writes happen exactly once per terminal transition; idempotent under repeat invocation.

### Behavioral / integration tests (the regression backstop)

- Walk through the full plan for canonical user shapes (normal, canRestore, reinstall, no-input-screen, treatment cohorts). For each, assert the sequence of `currentStepId` values matches the dialog sequence in the legacy in-VM flow.
- Skip-onboarding side branch: from `INITIAL_REINSTALL_USER` secondary, transition to `SKIP_ONBOARDING_OPTION`. Primary on skip → `Skipped` + `AppStage` writes. Secondary on skip → `GotoStep(COMPARISON_CHART)`, plan resumes forward from there.
- `CtaViewModel.refreshCta()` returns `null` while orchestrator state is `InProgress` on a `BrowserContext` step (synthetic plan with a `BrowserContext` step for the test).
- `CtaViewModel.refreshCta()` returns the existing reactive CTA when orchestrator state is `Completed`.

### Pixel parity test

For a representative set of plan inputs, compare the sequence of pixels fired through the orchestrator path against those fired through the legacy in-VM state machine path. Mechanically: run both paths against a recording `Pixel` test double, diff the resulting lists. Protection against silent behavioral drift during phases 2–3.

### Process-death scenarios

Single test exercising orchestrator init under each `AppStage` value:
- `NEW`: state rebuilds to `NotStarted`, fresh plan, first eligible step on `requestFirstStep()`.
- `DAX_ONBOARDING`: orchestrator stays inactive at `Completed`.
- `ESTABLISHED`: same.

### Manual QA checklist (phase 2)

- Cold start, no network: bundled-default flags drive plan; flow completes
- Cold start, slow network (3G throttled): config-dependent steps use fresh config when reached (validate `enroll()` is read late, not at startup)
- Background app for 30+ minutes during onboarding: verify resumption (or graceful restart-from-beginning if process died)
- Full L→C→L→C flow (when phase 5 ships): activity transitions visible smooth, back press during any step closes app

## Considered alternatives

### MVP "lift and shift"

Promote the existing `BrandDesignUpdatePageViewModel.when (currentDialog)` blocks to an AppScope class, expand `PreOnboardingDialogType` with new values for the L→C→L→C steps, add a `fun PreOnboardingDialogType.host(): Host` helper, and let both activities observe `currentDialog: PreOnboardingDialogType?` directly. Roughly 2x the existing viewmodel code instead of ~3x. Would satisfy the immediate L→C→L→C requirement.

**Rejected because:**

- Plan composition stays implicit in growing `when` arms — bad fit for the team's expected workload (experiment churn, variant combinations rather than step accumulation).
- Each new step touches multiple `when` arms in different methods (`onPrimaryCtaClicked`, `onSecondaryCtaClicked`, `onDefaultBrowserSet/NotSet`, `loadDaxDialog`, `fireDialogShownPixel`) — easy to drift, easy to miss one.
- Per-step testing remains all-or-nothing.
- The `IsolatedContext` / `BrowserContext` semantic distinction collapses into a flat enum, weakening the type-level expression of "this step is rendered against the live browser surface".
- The MVP version makes the eventual host-agnostic ideal harder, not easier — `BrowserTabFragment` (or wherever `BrowserContext` steps render) would gain knowledge of `PreOnboardingDialogType`, a name owned by the linear-onboarding domain.

The middle-path design pays roughly 50% more code than the MVP in exchange for descriptor-per-step structure and a single readable plan composition function. The team's expected workload (experiment-driven step variation) is exactly the workload that benefits from this seam and exactly the workload that hurts in growing `when` blocks.

### Pure static plan (resolved upfront, with config-wait)

`buildPlan()` resolves all feature-flag and experiment values at plan-build time, returning a plan whose composition is fixed for the session. Requires waiting for privacy config to load before building.

**Rejected because:**

- Onboarding is one-shot per user; if config is stale at plan-build, that's the user's onboarding for life. No "next session" to recover.
- The highest-stakes config-dependent decision (`duckAiOnboardingExperimentManager.enroll()`) is read deepest into the user's session today, giving config the longest possible window. Pre-computing compresses all of that into a single moment, making cohort assignment most vulnerable to staleness.
- The `Initializing` state and config-wait timeout add lifecycle complexity for a benefit (plan visible at a glance) that the hybrid largely preserves.

The hybrid (static registry of step descriptors with `suspend` precondition / `resolveParams`) keeps step composition readable in one function while reading fresh config at the latest possible moment for each decision — same as today's behavior, no regression.

### Over-engineered version (rejected during section 2 review)

An earlier draft included: separate `OnboardingPlanStateStore` class, plan-version content hashing for cross-upgrade invalidation, a `SelectionPayload` sealed class wrapper around typed selections, an `Initializing` state with explicit config-wait protocol, and DataStore-backed persistence of `currentStepId`. Each of these was identified as over-engineering during a self-review and pruned.

Specifically:
- `OnboardingPlanStateStore` collapses to inline state in the orchestrator (state is in-memory; no persistence).
- `planVersion` hashing is unnecessary because nothing is persisted that needs invalidation.
- `SelectionPayload` flattens to direct typed `StepEvent` variants (`OmnibarTypeSelected`, `InputModeSelected`).
- `Initializing` state and config-wait disappear because `precondition` and `resolveParams` are `suspend` and read fresh state at advance time.
- Persistence of `currentStepId` is dropped because today's flow doesn't persist either; product hasn't asked for the feature; AppScope already covers activity transitions for free.

## Open questions / deferred concerns

1. **`BrowserContext` step descriptor and renderer.** Owned by the first project to introduce a `BrowserContext` step. This spec only declares the structural seam.
2. **`SYNC_RESTORE` coverage in `BrandDesignUpdatePageViewModel`.** The brand-design viewmodel imports `SYNC_RESTORE` and handles it in `fireDialogShownPixel`, but its `loadDaxDialog` never dispatches to it, `onPrimaryCtaClicked(SYNC_RESTORE)` has a `// TODO`, and `onSecondaryCtaClicked(SYNC_RESTORE)` is a no-op. The orchestrator spec includes a `SYNC_RESTORE` step. If the product team is intentionally dropping `SYNC_RESTORE` with the brand-design migration, the orchestrator's `syncRestoreStep()` factory is removed in one place (and `ID_SYNC_RESTORE` from both the registry and `forwardOrder`). If `SYNC_RESTORE` is being kept, the brand-design viewmodel needs the missing rendering paths backfilled before phase 4 cleanup.
3. **Persistence as a follow-up.** If product later wants "preserve user's mid-flow position across process kills", it's a one-day add: DataStore-backed `OnboardingPlanState` + the reconciliation rule "if persisted `currentStepId` no longer in rebuilt plan, treat as `Completed`".
4. **Plugin-extensible plan.** If the team's experiment-churn workload ever grows to multiple modules wanting to inject onboarding steps, `LinearStep` plugin contributions via `@ContributesPluginPoint` would cleanly extend the registry. Out of scope for now (one customer, monolithic plan).
