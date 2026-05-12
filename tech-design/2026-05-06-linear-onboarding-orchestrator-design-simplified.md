# Linear Onboarding Orchestrator

## TL;DR

Lift the linear-onboarding state machine from `BrandDesignUpdatePageViewModel` into an `AppScope` orchestrator and extract the linear onboarding-state contracts out of `:app`.

Two structural wins delivered together: the linear onboarding state machine can now span multiple activities, and onboarding contracts (`UserStageStore`, `OnboardingSkipper`, the orchestrator surface) get a real home outside `:app`.

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
    fun onEvent(event: LinearOnboardingEvent)
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

// Opaque marker. The orchestrator never inspects events; it just routes them to
// step.transition(). Concrete event types live with the plan provider in :app, so
// consumers outside :app don't inherit onboarding's event vocabulary.
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
- The primary `LinearOnboardingPlan` is built **synchronously at app launch**, so `LaunchViewModel` can route to the right host (e.g. `OnboardingActivity` vs `BrowserActivity`) without waiting on async setup. To make this synchronous, plan construction is privacy-config-independent — flag and experiment reads happen lazily inside each step's `suspend precondition`, evaluated when the orchestrator is about to advance onto that step. The alternative (block plan construction until privacy config arrives, or a timeout elapses) would delay the initial onboarding experience. The current design matches today's production behaviour: any given step's flag check still risks reading a stale config value, but it's read at the latest possible moment, which is the same window today's flow has.
- `LinearOnboardingOrchestrator` or its model are not coupled to concrete onboarding steps. A plan provider and hosts are responsible for rendering the right dialogs and executing the right actions based on the provided `stepId`. This prevents leaking all available steps outside of the hosts that can execute them, and allows us to add/remove steps without modifying the `:onboarding-api` contract.
