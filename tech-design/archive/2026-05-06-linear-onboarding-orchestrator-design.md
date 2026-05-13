# Linear Onboarding Orchestrator

## Overview

Promote the linear-onboarding state machine that today lives inside `BrandDesignUpdatePageViewModel` (and `WelcomePageViewModel`) to an `AppScope` orchestrator. This will unblock interleaving of linear-onboarding steps across multiple activities/fragments.

This is in response to a requirement in [Android: AI onboarding for custom store listings](https://app.asana.com/1/137249556945/project/1208671518894266/task/1213551217308374?focus=true) where the goal is to show a subset of onboarding steps from the existing `OnboardingActivity`, move on to some Duck.ai onboarding steps in `BrowserActivity` before eventually coming back to the `OnboardingActivity` to finish the remaining steps. Check out [this Figma flow](https://www.figma.com/design/5QvJbyUBbeonblsjViDIkf/Mobile-Onboarding-AI-First?node-id=173-50969&t=w51SVY4Lvfst0gyJ-1) for reference.

Beyond satisfying the immediate requirement, this design invests in structure: each step becomes a self-contained descriptor, so future step reordering and experiment-driven variations are cheaper to add and maintain than they would be by growing the existing state machine's `when` arms.

## Files Changed

The orchestrator lives in two new modules — **`:onboarding-api`** (interfaces and pure types) and **`:onboarding-impl`** (implementations) — following the project's standard `-api` / `-impl` split. The plan provider, step factories, and existing UI continue to live in `:app` because their dependency closure is rooted there. See [Module structure](#module-structure) for the rationale.

### `:onboarding-api` (new module)

| File | Change |
|---|---|
| `LinearOnboardingOrchestrator.kt` | **New** — orchestrator interface (`state`, `onEvent`, `requestFirstStep`, `firstStepHost`, `isOnLinearBrowserStep`) |
| `OnboardingPlanState.kt` | **New** — `NotStarted` / `InProgress(currentPath, currentStepIndex)` / `Completed` / `Skipped` |
| `OnboardingPath.kt` | **New** — `data class OnboardingPath(val steps: List<LinearStep>)` |
| `LinearStep.kt` | **New** — `LinearStep` sealed hierarchy (`IsolatedContext`, `BrowserContext`), `Host` enum |
| `StepEvent.kt`, `StepTransition.kt` | **New** — sealed event and transition types |
| `IsolatedOnboardingDialog.kt` | **New** — sealed type carrying dialog identity + data (replaces the legacy `PreOnboardingDialogType` enum on the orchestrator path) |
| `OnboardingPathProvider.kt` | **New** — interface with `suspend fun buildMainPath(): OnboardingPath`, implemented by `:app`'s plan provider |
| `UserStageStore.kt` (**moved from `:app`**) | Interface + `AppStage` enum + `isNewUser()` / `daxOnboardingActive()` extension functions. Implementation (`AppUserStageStore`, the Room entity, DAO, type converter) stays in `:app`. |
| `OnboardingSkipper.kt` (**moved from `:app`**) | Interface only. `FullOnboardingSkipper` impl stays in `:app`. |

### `:onboarding-impl` (new module)

| File | Change |
|---|---|
| `RealLinearOnboardingOrchestrator.kt` | **New** — `@SingleInstanceIn(AppScope)` `@ContributesBinding(AppScope, LinearOnboardingOrchestrator::class)`. Holds `currentPath`, `currentStepIndex`, `callStack`, dispatches `StepEvent` to step transitions, writes `AppStage` on terminal transitions via injected `UserStageStore` and `OnboardingSkipper`. |
| `LinearOnboardingOrchestratorFeature.kt` | **New** — `@ContributesRemoteFeature` toggle gating rollout |

### `:app` (existing, modified)

| File | Change |
|---|---|
| `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/LinearOnboardingPlanProvider.kt` | **New** — `@ContributesBinding(AppScope, OnboardingPathProvider::class)`. Builds the main path; private side paths (e.g. `skipPath`) referenced by value from step transitions. Holds all step factory methods. Stays in `:app` because step factories depend on `:app`-rooted classes (`SyncAutoRestore`, `DefaultRoleBrowserDialog`, `OnboardingStore`, `SettingsDataStore`, `DuckChat`, etc.). |
| `app/src/main/java/com/duckduckgo/app/onboarding/store/UserStageStore.kt` | Interface + `AppStage` enum + extensions deleted from this file (moved to `:onboarding-api`). `AppUserStageStore`, `UserStage` Room entity, `UserStageDao`, `StageTypeConverter` stay; their imports update to point at `:onboarding-api`. |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/FullOnboardingSkipper.kt` | `OnboardingSkipper` interface deleted from this file (moved to `:onboarding-api`). `FullOnboardingSkipper` impl stays. |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdatePageViewModel.kt` | Add orchestrator-observer codepath gated on the feature flag. Expose `dialogState: StateFlow<IsolatedOnboardingDialog?>` for dialog rendering and shrink `Command` to non-dialog signals (`Finish`, `OnboardingSkipped`, `RequestNotificationPermissions`, `SkipDialogAnimation`, `FinishAndSubmitSearchQuery`, `FinishAndSubmitChatPrompt`). Legacy `when (currentDialog)` retained until phase 4 cleanup. |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdateWelcomePage.kt` | Refactor `configureDaxCta` to take an `IsolatedOnboardingDialog` sealed value instead of `(PreOnboardingDialogType, params...)`. Split the existing `commands` observer into a `dialogState` observer and a slimmer `commands` observer. Legacy `WelcomePage.kt` is untouched. |
| `app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingActivity.kt` | Add host-transition observer (launches `BrowserActivity` and self-finishes when current step's host is `BrowserContext`) |
| `app/src/main/java/com/duckduckgo/app/browser/BrowserActivity.kt` | Add host-transition observer (launches `OnboardingActivity` and self-finishes when current step's host is `IsolatedContext`); add back-press intercept that closes app while linear flow is active |
| `app/src/main/java/com/duckduckgo/app/cta/ui/CtaViewModel.kt` | Add `isOnLinearBrowserStep()` gate at the top of `refreshCta`, `getFireDialogCta`, `getSiteSuggestionsDialogCta`, `getEndStaticDialogCta` |
| `app/src/main/java/com/duckduckgo/app/launch/LaunchViewModel.kt` | When `isNewUser()`, route to host indicated by `orchestrator.firstStepHost()` instead of unconditionally to `OnboardingActivity` |
| Imports in `OnboardingViewModel`, `SystemSearchViewModel`, `BrowserAdditionalPixelParams`, `OnboardingFlowCheckerImpl` | Update `UserStageStore` / `AppStage` imports to point at `:onboarding-api`. No behaviour change. |
| `app/build.gradle` | Add `implementation(project(":onboarding-impl"))` and `implementation(project(":onboarding-api"))` |

## Goals

- Lift the linear-onboarding state machine to `AppScope` so it survives activity transitions, and out of `:app` module to improve code organization
- Provide a single readable place where plan composition expresses itself
- Make each step a self-contained descriptor (precondition, params, transition rules) that can be added, removed, or experiment-gated locally
- Keep today's user-visible behavior (dialog sequence, pixels, side effects) unchanged
- Land a structural seam for the future step transitions between the isolated onboarding context and browser onboarding context

## Non-goals

- Persistence of orchestrator state across process death — today's flow restarts from scratch on process death; that behavior is preserved deliberately
- Designing the `BrowserContext` step renderer — deferred to a follow-up TD
- Migrating `WelcomePageViewModel` / `WelcomePage` — they are about to be superseded by the brand-design variant of the onboarding anyway, so they stay on the legacy in-VM state machine until removed. Only `BrandDesignUpdatePageViewModel` / `BrandDesignUpdateWelcomePage` get migrated.
- Changing reactive-phase CTA logic from `CtaViewModel.getHomeCta()` / `getBrowserCta()` that's presented in the `BrowserTabFragment` — those continue to gate on `AppStage` as today
- Cross-host back-button navigation (e.g., back from a `IsolatedContext` step to a previous `BrowserContext` step) — back during linear closes the app, regardless of the current host
- Introducing analytics beyond the existing pixels — orchestrator must fire the same pixels in the same order as today's flow

## Module structure

Two new modules at the project root:

```
onboarding/
├── onboarding-api/      ← interfaces + pure types, no Anvil/Dagger
└── onboarding-impl/     ← @ContributesBinding implementations
```

### What goes where, and why

**`:onboarding-api`** holds the orchestrator's public surface plus the onboarding-state vocabulary that crosses module boundaries: `LinearOnboardingOrchestrator`, `OnboardingPlanState`, `OnboardingPath`, `LinearStep`, `StepEvent`, `StepTransition`, `IsolatedOnboardingDialog`, `Host`. It also picks up two pre-existing interfaces that are conceptually onboarding-state: **`UserStageStore` (with the `AppStage` enum)** and **`OnboardingSkipper`**. Both used to live in `:app`; the move puts the contract for "phase of onboarding" in the same module as the orchestrator that writes to it.

**`:onboarding-impl`** holds `RealLinearOnboardingOrchestrator` (the AppScope orchestrator with state, advance algorithm, and call-stack management) and `LinearOnboardingOrchestratorFeature` (the rollout toggle). The orchestrator-impl's only outside-the-onboarding-domain dependencies are `DispatcherProvider` (utility) and `@AppCoroutineScope CoroutineScope` (utility). It writes terminal `AppStage` transitions via injected `UserStageStore` (for the `Completed` path) and `OnboardingSkipper.markOnboardingAsCompleted()` (for the `Skipped` path) — both interfaces from `:onboarding-api`, so no `:app` dependency.

**`:app`** keeps the things whose dependency closure roots there: `LinearOnboardingPlanProvider` and all step factories (depend on `SyncAutoRestore`, `DefaultRoleBrowserDialog`, `OnboardingStore`, `SettingsDataStore`, `DuckChat`, `Pixel`, etc.), `BrandDesignUpdatePageViewModel`, fragments and activities, `CtaViewModel`. The plan provider implements `OnboardingPathProvider` (declared in `:onboarding-api`) and is bound at `AppScope` from `:app`.

### Why this split is worth doing now

- **Onboarding-state lives in the onboarding module.** `UserStageStore` and `OnboardingSkipper` describe the linear/reactive/established phase machine. They've always been part of the onboarding domain; they were just stranded in `:app` for historical reasons. Moving them to `:onboarding-api` is the orchestrator getting the contracts it needs *and* a real architectural cleanup independently.
- **Cross-module access becomes possible.** Future consumers outside `:app` (e.g., a tabs module wanting to know "are we mid-onboarding?", a privacy-pro module wanting to defer prompts) can depend on `:onboarding-api` directly without pulling in `:app`. Today, every reader has to be in `:app`.
- **`:app` shrinks.** Net code that moves out of `:app`: orchestrator implementation (~300 lines) plus the `UserStageStore` and `OnboardingSkipper` interface declarations (small, but they unblock the move). This aligns with the team's broader app-module-thinning effort.

### Why the plan provider stays in `:app` (for now)

`LinearOnboardingPlanProvider` is where the orchestrator's abstract API meets the app's concrete domain. Each step factory injects domain-specific dependencies that today live in `:app`. Moving the plan provider to `:onboarding-impl` would force moving its dependency closure too — a much wider refactor. The `OnboardingPathProvider` interface lets the orchestrator stay decoupled while the provider stays where its dependencies are. As individual dependencies are themselves modularised (e.g., a future `:default-browser-api`), step factories that use them can migrate piecewise.

### Module-rule compliance (per `CLAUDE.md`)

- `:onboarding-api` has no Anvil / Dagger dependencies; only pure Kotlin + Android platform types (e.g., `Intent`).
- `:onboarding-impl` is depended-on only by `:app` (the composition root).
- `:onboarding-api` does not depend on other `-api` modules.
- No `strings.xml` in either new module — no UI lives in them.
- No `KAPT` — `:onboarding-impl` uses KSP for Anvil/Dagger.

## Architecture summary

```
┌─ AppScope ───────────────────────────────────────────────────────────┐
│                                                                      │
│   LinearOnboardingPlanProvider                                       │
│         │   buildMainPath(): OnboardingPath                          │
│         │   step factories with precondition + resolveDialog +       │
│         │   transition lambdas (each reads fresh state at call time) │
│         │   side paths exposed as private vals (e.g. skipPath)       │
│         ▼                                                            │
│   LinearOnboardingOrchestrator    ─── state: StateFlow<…>            │
│         │   onEvent(StepEvent)                                       │
│         │   requestFirstStep()    (called once by welcome page)      │
│         │   firstStepHost(): Host                                    │
│         │                                                            │
│         └──► UserStageStore + OnboardingSkipper                      │
│              (interfaces in :onboarding-api,                          │
│               impls remain in :app, Room-backed)                      │
│              writes AppStage on terminal transitions                  │
└──────────────────────────────────────────────────────────────────────┘
        │                                          │
        ▼                                          ▼
┌─ OnboardingActivity ────────────┐      ┌─ BrowserActivity ─────────────┐
│                                 │      │                               │
│  Host observer: hands off to    │      │  Host observer: hands off to  │
│   BrowserActivity when step     │      │   OnboardingActivity when     │
│   host is BrowserContext        │      │   host is IsolatedContext     │
│                                 │      │                               │
│  Back-press intercept:          │      │  Back-press intercept:        │
│   while linear active,          │      │   while linear active,        │
│   finish()                      │      │   finish()                    │
│                                 │      │                               │
│  BrandDesignUpdatePageVM        │      │  CtaViewModel.refreshCta() &  │
│   (flag-gated): observe         │      │   friends suppress while      │
│   orchestrator.state, expose    │      │   on a BrowserContext step    │
│   IsolatedOnboardingDialog state     │      │                               │
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
        val resolveDialog: suspend () -> IsolatedOnboardingDialog,
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

sealed interface IsolatedOnboardingDialog {
    data object IntroAnimation : IsolatedOnboardingDialog            // welcome animation + notification permission flow
    data object SyncRestore : IsolatedOnboardingDialog
    data class Initial(val showDuckAiCopy: Boolean) : IsolatedOnboardingDialog
    data class InitialReinstallUser(val showDuckAiCopy: Boolean) : IsolatedOnboardingDialog
    data class ComparisonChart(val showDuckAiCopy: Boolean) : IsolatedOnboardingDialog
    data class DefaultBrowser(val intent: Intent) : IsolatedOnboardingDialog
    data class AddressBarPosition(val showSplitOption: Boolean) : IsolatedOnboardingDialog
    data class InputScreen(val showDuckAiCopy: Boolean) : IsolatedOnboardingDialog
    data class InputScreenPreview(val isSearchDefault: Boolean) : IsolatedOnboardingDialog
    data object SkipOnboardingOption : IsolatedOnboardingDialog
}

sealed interface StepEvent {
    data object PrimaryClicked : StepEvent
    data object SecondaryClicked : StepEvent
    data class DefaultBrowserPromptFinished(val isDefaultBrowser: Boolean) : StepEvent           // role-manager intent result
    data class OmnibarTypeSelected(val type: OmnibarType) : StepEvent
    data class InputModeSelected(val withAi: Boolean) : StepEvent
    data class InputDemoQuerySubmitted(val query: String, val isChat: Boolean) : StepEvent
}

sealed interface StepTransition {
    data object Advance : StepTransition                          // next step in current path
    data class SwitchTo(val path: OnboardingPath) : StepTransition // push frame, run path from first eligible step
    data object Return : StepTransition                            // pop frame, resume caller path past caller
    data object AbortPlan : StepTransition                         // terminates linear as Skipped
    data object Stay : StepTransition                              // explicit no-op
}

data class OnboardingPath(val steps: List<LinearStep>)
```

### Why this shape

- **Two step subtypes, one per host.** `IsolatedContext` steps run full-screen (today: rendered by `OnboardingActivity`). `BrowserContext` steps render against the live browser. The author picks the subtype that fits; the orchestrator infers which activity should host the step from the subtype, with no separate `host` field to keep in sync.
- **`precondition` and `resolveDialog` are `suspend` and read fresh state at call time.** This is the load-bearing decision for the config-staleness story — see [State and persistence](#state-and-persistence).
- **`IsolatedOnboardingDialog` is one sealed type, not enum + parallel params.** Each variant carries the data its renderer needs (e.g. `Initial(showDuckAiCopy)`, `DefaultBrowser(intent)`). The existing `PreOnboardingDialogType` enum is *deprecated; it stays in place only for the legacy `WelcomePage` path until that path is removed.
- **`transition` is a per-step function returning a `StepTransition`.** Each step's flow-control logic is local to its descriptor instead of co-mingled in a giant centralised `when`.
- **Paths are first-class values, not registry-keyed.** `OnboardingPath` is a plain data class wrapping a list of steps. The plan provider returns the *main* path, and side paths (e.g. the skip-confirmation flow) are private `val`s in the provider, referenced by value from `SwitchTo(...)` calls inside step transitions. A step in any path can switch into any other path; transitions are local rather than coordinated through a central registry.
- **Side flows are sequences, not single steps.** Multi-step side flows (e.g. confirm → feature-pitch → abort) fall out naturally — they're just a path with multiple steps. The "side branch" concept disappears; a side flow is just another `OnboardingPath` invoked via `SwitchTo`.

### Orchestrator advance algorithm

The orchestrator's internal state tracks `currentPath: OnboardingPath`, `currentStepIndex: Int`, and a `callStack: ArrayDeque<Frame>` where each `Frame = (path, indexAtJump)`. External observers see only the public `OnboardingPlanState` (see next section) — the call stack is private.

When a step's `transition(event)` returns:

- **`Advance`** — walk `currentPath.steps` from `currentStepIndex + 1`, skipping ineligible (precondition `false`). If we land on an eligible step, become it. If we exhaust the list:
  - If `callStack` is non-empty: pop a frame and resume the caller path at `frame.indexAtJump + 1` (which itself is an `Advance` walk). The caller's transition that triggered the original `SwitchTo` is now considered complete.
  - If `callStack` is empty: terminate as `Completed`.
- **`SwitchTo(path)`** — push `(currentPath, currentStepIndex)` onto the call stack. Set `currentPath = path`, walk to the first eligible step. If `path` has no eligible step, behave as if we'd already exhausted it: pop and resume caller (this is the "fallback" for an empty target).
- **`Return`** — pop a frame and resume the caller at `frame.indexAtJump + 1`. With an empty call stack, `Return` is a programming error (a path designed to be returned-from should never be entered as `main`); fail loudly.
- **`AbortPlan`** — set state to `Skipped`. Discard the entire call stack. Delegate the side effects (advance `AppStage` to `ESTABLISHED`, set `hideTips = true`, dismiss the `ADD_WIDGET` CTA) to `OnboardingSkipper.markOnboardingAsCompleted()` — that interface already encapsulates the existing skip semantics; the orchestrator just calls it.
- **`Stay`** — no state change.

The "advance from a side branch resumes main" rule from the old design is no longer special-cased — it's just `Advance` exhausting a path and popping the stack, which works the same regardless of whether the popped caller is on the main path or a deeper side path.

### Today's flow expressed in this model

The current brand-design flow maps onto **one main path of nine steps**, plus a **single-step side path** for the skip-confirmation:

- Main path (in order): `IntroAnimation`, `SyncRestore`, `InitialReinstallUser`, `Initial`, `ComparisonChart`, `DefaultBrowser`, `AddressBarPosition`, `InputScreen`, `InputScreenPreview`
- Skip path: `SkipOnboardingOption`

`IntroAnimation` is the welcome animation + notification permission flow. Modelling it as a step rather than as a fragment-side prelude has a real correctness benefit: when the user re-enters `OnboardingActivity` after a `BrowserContext` step (for the L→C→L→C flow), the orchestrator's `currentStepIndex` is already past `IntroAnimation`, so the animation does not replay. The fragment renders whatever the current step's dialog is.

Mutual exclusion between the three "first dialog" candidates (`SyncRestore` / `InitialReinstallUser` / `Initial`) is expressed as preconditions on each step, not as plan-level branching — the main path stays a flat, readable list, and ineligible steps are skipped during forward walking. `InitialReinstallUser` and `SyncRestore`'s secondary CTAs invoke `SwitchTo(skipPath)`. `SkipOnboardingOption`'s secondary returns `Advance` (which walks off the end of the single-step skip path → pops the call stack → resumes main from the caller's slot + 1). The full plan-provider code is in [Appendix A: Plan provider code](#appendix-a-plan-provider-code).

Two architectural points worth surfacing here, since they're easy to miss in the per-step factory code:

1. **`DefaultBrowser` is a first-class step.** Today the role-manager intent is launched from inside `WelcomePageViewModel.onPrimaryCtaClicked(COMPARISON_CHART)` and `onDefaultBrowserSet/NotSet` fires the next state. In the orchestrator model it's a discrete step whose `resolveDialog` returns `IsolatedOnboardingDialog.DefaultBrowser(intent = ...)`, with the result delivered via a `DefaultBrowserPromptFinished(isDefaultBrowser)` event. The fragment-side rendering is unchanged (no visible separate "step" to the user); it's only an architectural unit. This becomes important when the future `BrowserContext` steps land and `DefaultBrowser` is the isolated phase the user re-enters.

2. **Pixel firing has moved into `transition` lambdas.** Today's `BrandDesignUpdatePageViewModel` has pixel calls scattered across `onPrimaryCtaClicked`, `onSecondaryCtaClicked`, `fireDialogShownPixel`, etc. In the orchestrator model, decision-time pixels live with the decision (in `transition`); show-time pixels (`fireDialogShownPixel` equivalents) stay in the renderer (the viewmodel observing `state` transitions emits the show pixel for the new step). The pixel sequence is preserved.

## State and persistence

```kotlin
sealed interface OnboardingPlanState {
    data object NotStarted : OnboardingPlanState
    data class InProgress(
        val currentPath: OnboardingPath,
        val currentStepIndex: Int,
    ) : OnboardingPlanState {
        val currentStep: LinearStep
            get() = currentPath.steps[currentStepIndex]
    }
    data object Completed : OnboardingPlanState
    data object Skipped : OnboardingPlanState
}
```

The state surfaces the *current path + index* explicitly, with a computed `currentStep` getter for observers. `currentPath` disambiguates the case where the same step descriptor appears in multiple paths (uncommon but possible — sharing via `private val` is the natural pattern, but we don't enforce it). Index is the position-in-path, useful for tests and any future "step N of M" UI. The pair `(currentPath, currentStepIndex)` matches what the orchestrator tracks internally — single source of truth, no derivable redundancy in the state. Equality and `distinctUntilChanged` work as expected since the data class compares both fields.

The orchestrator's call stack is **internal**: not exposed on `OnboardingPlanState`. Observers don't need it; if some future feature wants nesting depth, the orchestrator can expose `callStackDepth(): Int` separately.

State lives entirely in a `MutableStateFlow<OnboardingPlanState>` inside the orchestrator. **No DataStore, no Room table, no persistence of `currentStepIndex` or the call stack.** Today's flow restarts from scratch on process death; that behavior is preserved.

The full state lifecycle, including the orchestrator's `AppStage` writes on terminal transitions and the call-stack semantics for `SwitchTo` / `Return`, is in [`2026-05-06-linear-onboarding-orchestrator-design-state.puml`](2026-05-06-linear-onboarding-orchestrator-design-state.puml).

### `AppStage` migration

`AppStage` (Room-backed enum: `NEW` / `DAX_ONBOARDING` / `ESTABLISHED`) stays as today's source of truth for "phase of onboarding". Existing readers (`LaunchViewModel.isNewUser`, `SystemSearchViewModel`, `BrowserAdditionalPixelParams`, `CtaViewModel.daxOnboardingActive`, `OnboardingFlowCheckerImpl`) don't change.

The orchestrator becomes a *writer* of `AppStage` on terminal transitions:

| Orchestrator transition | `AppStage` write |
|---|---|
| `NotStarted → InProgress(...)` | none (already `NEW`) |
| `InProgress → Completed` | `userStageStore.stageCompleted(AppStage.NEW)` → moves to `DAX_ONBOARDING` |
| `InProgress → Skipped` | `onboardingSkipper.markOnboardingAsCompleted()` — the existing `FullOnboardingSkipper` impl writes `hideTips`, dismisses the `ADD_WIDGET` CTA, and advances `AppStage` to `ESTABLISHED` in one call. Reusing the existing helper keeps the skip semantics identical to today's flow. |

Reactive completion in `CtaViewModel.completeStageIfDaxOnboardingCompleted` continues to write `DAX_ONBOARDING → ESTABLISHED`. The orchestrator never writes that transition itself, it is only handling the linear portion of the onboarding flow.

### Process / lifecycle behavior

**On orchestrator init (`@Inject` resolution after process start):**

1. Read `AppStage` from Room.
2. If `AppStage == ESTABLISHED`: state = `Completed`, orchestrator never activates.
3. If `AppStage == DAX_ONBOARDING`: state = `Completed`, orchestrator never activates (linear is past, reactive owns the rest).
4. If `AppStage == NEW`: state = `NotStarted`. Plan registry is built lazily on first `requestFirstStep()` call from the welcome page (so injection elsewhere doesn't trigger flag/state reads).

**On `requestFirstStep()` (called by `BrandDesignUpdatePageViewModel` once on view creation; idempotent if state is already `InProgress`):**

- Build the main path via `planProvider.buildMainPath()` (synchronous, no config wait — preconditions evaluate fresh state lazily at advance time, see [Config staleness](#config-staleness)).
- Walk the main path's steps from index 0, evaluating each `precondition()` until the first eligible step is found. Set state to `InProgress(currentPath = mainPath, currentStepIndex = indexOfFirstEligible)`. Empty call stack.

On a fresh first-time launch this lands on `IntroAnimation`, which the fragment renders. On re-entry to `OnboardingActivity` from `BrowserActivity` (during the L→C→L→C flow), the orchestrator state is already `InProgress(...)` somewhere past `IntroAnimation`, so `requestFirstStep()` is a no-op and the fragment renders the current step's dialog directly — no animation replay.

**On activity transitions mid-flow:**

- AppScope orchestrator stays alive across `OnboardingActivity ↔ BrowserActivity`.
- Each activity has a host-transition observer (see [Activity transitions](#activity-transitions-and-reactive-phase-handoff)) that finishes the current activity and launches the other when the current step's host doesn't match.

**On process death mid-flow:**

- Orchestrator state is lost (in-memory).
- On next launch: `AppStage == NEW` (Room-backed, durable), state = `NotStarted`, user starts linear onboarding from the first eligible step.
- Same outcome as "process died at any point during the existing flow". Not a regression.

### Config staleness

Plan registry is **synchronous and config-independent** at build time. This is the central design choice that addresses config-loading-during-onboarding without introducing an explicit "wait for config" state.

The privacy config feature flags (`androidBrowserConfigFeature.*`, `duckAiOnboardingExperimentManager.enroll()`) are read inside the `precondition` and `resolveDialog` lambdas of each step. These lambdas execute `suspend` at advance time — i.e., the moment the orchestrator is about to land on the step. This is matching the existing behavior.

## Activity transitions and reactive-phase handoff

### Host-transition model

Finish-and-relaunch on every host change. Each activity observes orchestrator state and, when the current step's host doesn't match its own, launches the matching host activity and finishes itself. No central transition controller.

Reasoning: matches today's `OnboardingActivity → BrowserActivity` pattern; no new component; state-derived (recoverable under activity recreation); on `BrowserContext` steps we don't expect users to accumulate any meaningful state, so finish-and-relaunch loses nothing.

The full handoff for a `Isolated → Browser → Isolated` round-trip is in [`2026-05-06-linear-onboarding-orchestrator-design-host-transition.puml`](2026-05-06-linear-onboarding-orchestrator-design-host-transition.puml).

```kotlin
// OnboardingActivity
private var hostTransitionInitiated = false

private fun observeOrchestratorForHostTransitions() {
    orchestrator.state.flowWithLifecycle(lifecycle, STARTED).onEach { state ->
        val expectedHost = state.expectedHost()
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
fun OnboardingPlanState.expectedHost(): Host? = when (this) {
    is InProgress -> when (currentStep) {
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
private fun isOnLinearBrowserStep(): Boolean {
    val state = orchestrator.state.value as? InProgress ?: return false
    return state.currentStep is BrowserContext
}

suspend fun refreshCta(...): Cta? = withContext(dispatcher) {
    if (isOnLinearBrowserStep()) return@withContext null
    if (isBrowserShowing) getBrowserCta(site, detectedRefreshPatterns) else getHomeCta()
}

// same gate added to getFireDialogCta, getSiteSuggestionsDialogCta, getEndStaticDialogCta
```

`BrowserTabFragment` is unchanged regarding orchestrator awareness. It calls `viewModel.refreshCta()` as today; the suppression is opaque to it. Today's `daxOnboardingActive()` checks inside `getHomeCta` / `getBrowserCta` continue to work — they return `false` while `AppStage == NEW`, naturally suppressing the dax CTAs. The new `isOnLinearBrowserStep()` gate covers the cases `daxOnboardingActive()` doesn't (widget CTA, per-site CTAs in `getBrowserCta`).

### `LaunchViewModel` routing

`LaunchViewModel.showOnboardingOrHome()` today routes `isNewUser` users unconditionally to `Command.Onboarding`. The orchestrator adds a host indirection: when the orchestrator's first eligible step is a `BrowserContext` (only possible once the L→C→L→C follow-up lands, but the seam is in place now), launch routes directly to the browser instead.

```kotlin
suspend fun showOnboardingOrHome() {
    command.value = if (userStageStore.isNewUser() && orchestrator.firstStepHost() == Host.BrowserContext) {
        Command.Home()
    } else if (userStageStore.isNewUser()) {
        Command.Onboarding
    } else {
        Command.Home()
    }
}
```

`firstStepHost()` is a one-shot helper on the orchestrator that builds the plan if needed and returns the host of the first eligible main-path step, reading fresh config at call time. **Today's plan starts with an `IsolatedContext` step in every cohort, so this routing change is a no-op until a `BrowserContext` first step exists.** It's wired in now so the L→C→L→C follow-up only has to add the step descriptor and not also touch `LaunchViewModel`.

### Back button

Back during the linear flow closes the app. No exception, no dismiss event, no abort. The only escape from the linear flow is the explicit `SKIP_ONBOARDING_OPTION` side branch (entered via secondary CTA from `INITIAL_REINSTALL_USER` or `SYNC_RESTORE`).

```kotlin
// in OnboardingActivity / BrowserActivity
onBackPressedDispatcher.addCallback(this) {
    if (orchestrator.state.value is InProgress) {
        finish()
    } else {
        isEnabled = false
        onBackPressedDispatcher.onBackPressed()
    }
}
```

When `OnboardingPlanState` is `NotStarted` / `Completed` / `Skipped`, the callback disables and the host's normal back behavior applies. `OnboardingActivity` today has a "rewind ViewPager" `onBackPressed` override — that becomes moot (the brand-design pager only ever has one page).

## Renderer adapter

`BrandDesignUpdatePageViewModel` becomes a thin adapter that exposes two surfaces to the fragment:

- **`dialogState: StateFlow<IsolatedOnboardingDialog?>`** — the dialog the fragment should currently render (or `null` for "no dialog yet"). Replaces the family of `Show*Dialog` commands that exist today.
- **`commands: Flow<Command>`** — transient, non-dialog signals (`Finish`, `OnboardingSkipped`, `RequestNotificationPermissions`, `SkipDialogAnimation`, `FinishAndSubmitSearchQuery`, `FinishAndSubmitChatPrompt`). Channel-based as today.

  *Correction from earlier drafts:* `SetAddressBarPositionOptions` is **not** a command on `BrandDesignUpdatePageViewModel` — it lives on the legacy `WelcomePageViewModel` only. The brand-design fragment reads `selectedAddressBarPosition` from `viewState`, and with the new `dialogState` carrying `AddressBarPosition(showSplitOption)` the fragment renders straight off the dialog payload. No separate command is needed.

The split eliminates a duplicated dispatch — without it, the same dialog identity would be encoded once into a `Show*Dialog` command and immediately decoded back in the fragment to call `configureDaxCta`. With the state-flow surface, the fragment's render path is a single dispatch on `IsolatedOnboardingDialog`.

```kotlin
class BrandDesignUpdatePageViewModel ... {
    private val _dialogState = MutableStateFlow<IsolatedOnboardingDialog?>(null)
    val dialogState: StateFlow<IsolatedOnboardingDialog?> = _dialogState

    private val _commands = Channel<Command>(1, DROP_OLDEST)
    val commands: Flow<Command> = _commands.receiveAsFlow()

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
                is InProgress -> {
                    val step = state.currentStep as? IsolatedContext ?: return@onEach
                    val dialog = step.resolveDialog()
                    _dialogState.value = dialog
                    fireDialogShownPixel(dialog)  // show-time pixel
                }
                Completed -> {
                    _dialogState.value = null
                    _commands.send(Command.Finish)
                }
                Skipped -> {
                    _dialogState.value = null
                    _commands.send(Command.OnboardingSkipped)
                }
                NotStarted -> _dialogState.value = null  // welcome animation playing
            }
        }.launchIn(viewModelScope)
    }

    // fragment-callback routing
    fun onPrimaryCtaClicked() = orchestrator.onEvent(StepEvent.PrimaryClicked)
    fun onSecondaryCtaClicked() = orchestrator.onEvent(StepEvent.SecondaryClicked)
    fun onAddressBarPositionOptionSelected(type: OmnibarType) = orchestrator.onEvent(StepEvent.OmnibarTypeSelected(type))
    fun onInputScreenOptionSelected(withAi: Boolean) = orchestrator.onEvent(StepEvent.InputModeSelected(withAi))
    fun onInputModeDemoQuerySubmitted(query: String, isChat: Boolean) = orchestrator.onEvent(StepEvent.InputDemoQuerySubmitted(query, isChat))
    fun onDefaultBrowserSet() = orchestrator.onEvent(StepEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
    fun onDefaultBrowserNotSet() = orchestrator.onEvent(StepEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))

    // called by the fragment on view creation. With IntroAnimation modelled as a step,
    // this fires before the animation runs — the orchestrator transitions to
    // InProgress(IntroAnimation), the renderer emits dialogState = IntroAnimation,
    // and the fragment plays the animation as part of rendering that step.
    // Idempotent: a no-op if state is already InProgress (e.g., on re-entry from BrowserActivity).
    fun loadDaxDialog() {
        if (linearOnboardingOrchestratorFeature.self().isEnabled()) {
            viewModelScope.launch { orchestrator.requestFirstStep() }
        } else {
            // existing in-VM logic (unchanged)
        }
    }
}
```

Fragment-side, the observer splits in two:

```kotlin
viewModel.dialogState.flowWithLifecycle(lifecycle, STARTED).onEach { dialog ->
    dialog?.let { configureDaxCta(it) }
}.launchIn(lifecycleScope)

viewModel.commands.flowWithLifecycle(lifecycle, STARTED).onEach { command ->
    when (command) {
        Finish -> onContinuePressed()
        OnboardingSkipped -> onSkipPressed()
        RequestNotificationPermissions -> requestNotificationsPermissions()
        SkipDialogAnimation -> skipDialogAnimations()
        is FinishAndSubmitSearchQuery -> (activity as OnboardingActivity).finishAndSubmitSearchQuery(command.query)
        is FinishAndSubmitChatPrompt -> (activity as OnboardingActivity).finishAndSubmitChatPrompt(command.prompt)
        // Show*Dialog cases removed — those are now in dialogState
    }
}.launchIn(lifecycleScope)
```

`configureDaxCta(dialog: IsolatedOnboardingDialog)` becomes the single fragment-side rendering entry point. Most variants render a dax dialog; `IntroAnimation` triggers the existing welcome-animation + notification-permission flow (which fires `onPrimaryCtaClicked()` when complete to advance the orchestrator); `DefaultBrowser` launches the system role-manager intent:

```kotlin
private fun configureDaxCta(dialog: IsolatedOnboardingDialog) {
    when (dialog) {
        is IntroAnimation -> playIntroAnimation { onPrimaryCtaClicked() }  // existing welcome animation + permission flow
        is DefaultBrowser -> startActivityForResult(dialog.intent, DEFAULT_BROWSER_ROLE_MANAGER_DIALOG)
        else -> renderDaxDialog(dialog)  // existing per-variant rendering
    }
}
```

The `IntroAnimation` case is what makes the no-replay property work: when the fragment is recreated (e.g., the user returns to `OnboardingActivity` from a `BrowserContext` step), it observes whatever the orchestrator's current step is. If that's not `IntroAnimation`, the animation simply isn't played — the fragment renders the current dialog directly.

### Why this shape

- **One dispatch, not three.** The orchestrator emits a step ID; the adapter resolves it to a `IsolatedOnboardingDialog`; the fragment renders. No round-trip through a parallel `Show*Dialog` command vocabulary.
- **State-flow semantics for the dialog.** A reader can ask "what dialog is the user looking at?" via `viewModel.dialogState.value`. Activity recreation re-renders the current dialog automatically; we don't need the channel to replay missed `Show*` commands.
- **`Command` shrinks to genuinely transient signals.** Terminal events (`Finish`, `OnboardingSkipped`, `FinishAndSubmitSearchQuery`, `FinishAndSubmitChatPrompt`), system signals (`RequestNotificationPermissions`), and UI side-effects (`SkipDialogAnimation`). These remain channel-based because they're one-shots that should fire exactly once.
- **DefaultBrowser stays a dialog state, not a command.** Even though its rendering is a system intent rather than a custom view, it's still "what's currently active in the linear flow". Putting it in `dialogState` keeps the surface uniform.

## Rollout

### Feature flag

`@ContributesRemoteFeature(scope = AppScope::class, featureName = "linearOnboardingOrchestrator")` with a `self()` toggle. Default INTERNAL during build-out. Evaluated once at `BrandDesignUpdatePageViewModel` init.

When the flag is off:
- `BrandDesignUpdatePageViewModel` runs its existing in-VM `when (currentDialog)` path verbatim
- `LinearOnboardingOrchestrator.state` stays at `NotStarted`
- `CtaViewModel.isOnLinearBrowserStep()` returns false (state never leaves `NotStarted`)
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

- `LinearOnboardingPlanProvider.buildMainPath()` produces the expected ordered list of steps; the private side paths (e.g. `skipPath`) contain the expected steps and transitions.
- Each step's `precondition` evaluation under combinations of feature flags and stored state. Test fixtures parameterised by `canRestore` × `isReinstall` × `showInputScreen` × experiment variant.
- Each step's `transition(event)` returns the expected `StepTransition` for each `StepEvent`; un-handled events return `Stay`.
- `LinearOnboardingOrchestrator` advance walks `currentPath.steps` past ineligible entries to the first eligible. When a path is exhausted, the call stack is popped and the caller resumes at `indexAtJump + 1`; if the stack is empty, state becomes `Completed`.
- `LinearOnboardingOrchestrator.onEvent()` dispatches to the current step's transition. `Advance` walks forward (with stack-pop on path exhaustion); `SwitchTo(path)` pushes a frame and runs the new path; `Return` pops a frame; `AbortPlan` calls `OnboardingSkipper.markOnboardingAsCompleted()` regardless of stack depth; `Stay` is a no-op.
- `AppStage` writes happen exactly once per terminal transition; idempotent under repeat invocation.

### Behavioral / integration tests (the regression backstop)

- Walk through the full plan for canonical user shapes (normal, canRestore, reinstall, no-input-screen, treatment cohorts). For each, assert the sequence of `state.currentStep.id` values matches the dialog sequence in the legacy in-VM flow.
- Skip-onboarding side path: from `InitialReinstallUser` secondary, `SwitchTo(skipPath)` pushes the caller frame and lands on `SkipOnboardingOption`. Primary on skip → `AbortPlan` → `Skipped` + `AppStage` writes. Secondary on skip → `Advance`, which exhausts the single-step skip path → pops the call stack → resumes the main path past `InitialReinstallUser`, landing on `ComparisonChart` (the first eligible main step).
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

The hybrid (static registry of step descriptors with `suspend` precondition / `resolveDialog`) keeps step composition readable in one function while reading fresh config at the latest possible moment for each decision — same as today's behavior, no regression.

### Over-engineered version (rejected during section 2 review)

An earlier draft included: separate `OnboardingPlanStateStore` class, plan-version content hashing for cross-upgrade invalidation, a `SelectionPayload` sealed class wrapper around typed selections, an `Initializing` state with explicit config-wait protocol, and DataStore-backed persistence of `currentStepId`. Each of these was identified as over-engineering during a self-review and pruned.

Specifically:
- `OnboardingPlanStateStore` collapses to inline state in the orchestrator (state is in-memory; no persistence).
- `planVersion` hashing is unnecessary because nothing is persisted that needs invalidation.
- `SelectionPayload` flattens to direct typed `StepEvent` variants (`OmnibarTypeSelected`, `InputModeSelected`).
- `Initializing` state and config-wait disappear because `precondition` and `resolveDialog` are `suspend` and read fresh state at advance time.
- Persistence of `currentStepId` is dropped because today's flow doesn't persist either; product hasn't asked for the feature; AppScope already covers activity transitions for free.

## Open questions / deferred concerns

1. **`BrowserContext` step descriptor and renderer.** Owned by the first project to introduce a `BrowserContext` step. This spec only declares the structural seam.
2. **`SYNC_RESTORE` coverage in `BrandDesignUpdatePageViewModel`.** The brand-design viewmodel imports `SYNC_RESTORE` and handles it in `fireDialogShownPixel`, but its `loadDaxDialog` never dispatches to it, `onPrimaryCtaClicked(SYNC_RESTORE)` has a `// TODO`, and `onSecondaryCtaClicked(SYNC_RESTORE)` is a no-op. The orchestrator spec includes a `SYNC_RESTORE` step. If the product team is intentionally dropping `SYNC_RESTORE` with the brand-design migration, the orchestrator's `syncRestoreStep()` factory is removed from the main path in one place. If `SYNC_RESTORE` is being kept, the brand-design viewmodel needs the missing rendering paths backfilled before phase 4 cleanup.
3. **`DefaultBrowser` intent nullability.** `defaultRoleBrowserDialog.createIntent(context)` is nullable today and the legacy VM falls through to `ADDRESS_BAR_POSITION` when it returns null while `shouldShowDialog()` is true. The orchestrator handles this by checking both in `defaultBrowserStep.precondition` (see Appendix A). Worth confirming with the team that this fall-through is the intended behaviour to preserve, since the legacy code also fires `DEFAULT_BROWSER_DIALOG_NOT_SHOWN` in that case — that pixel needs to keep firing on the orchestrator path too.
4. **`expectedHost == null` while `NotStarted`.** `expectedHost()` returns `null` when `OnboardingPlanState == NotStarted` — neither activity should redirect during that window. With `IntroAnimation` modelled as a step, this window collapses to "between activity creation and the fragment's `requestFirstStep()` call" — typically tens of milliseconds. Activities silently linger if the orchestrator is `NotStarted`; the `null` return is intentional to keep both activities passive during that window.
5. **Persistence as a follow-up.** If product later wants "preserve user's mid-flow position across process kills", it's a one-day add: DataStore-backed `OnboardingPlanState` + the reconciliation rule "if persisted `currentStepId` no longer in rebuilt plan, treat as `Completed`".
6. **Plugin-extensible plan.** If the team's experiment-churn workload ever grows to multiple modules wanting to inject onboarding steps, `LinearStep` plugin contributions via `@ContributesPluginPoint` would cleanly extend the registry. Out of scope for now (one customer, monolithic plan).


## Appendix A: Plan provider code

The full step factories for today's brand-design flow, mapped onto the orchestrator model. Each factory captures the same preconditions, params, and transitions that `BrandDesignUpdatePageViewModel`'s `when (currentDialog)` blocks express today.

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
    fun buildMainPath(): OnboardingPath = OnboardingPath(steps = listOf(
        introAnimationStep(),
        syncRestoreStep(),
        initialReinstallUserStep(),
        initialStep(),
        comparisonChartStep(),
        defaultBrowserStep(),
        addressBarPositionStep(),
        inputScreenStep(),
        inputScreenPreviewStep(),
    ))

    // Side path. Constructed once and shared across callers (both InitialReinstallUser
    // and SyncRestore SwitchTo it). Steps inside have stable identity.
    private val skipPath: OnboardingPath by lazy {
        OnboardingPath(steps = listOf(skipOnboardingOptionStep()))
    }

    private fun introAnimationStep() = IsolatedContext(
        id = ID_INTRO_ANIMATION,
        // No precondition: this is always the very first step on a fresh plan.
        // Once advanced past, the orchestrator never lands here again — the no-replay
        // property is just the universal forward-walk semantics of the advance algorithm.
        resolveDialog = { IsolatedOnboardingDialog.IntroAnimation },
        transition = { event -> when (event) {
            // Fragment fires PrimaryClicked once the welcome animation + notification
            // permission flow finishes. No pixels here — they're already fired by the
            // existing notification-runtime-permission flow inside the fragment.
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun syncRestoreStep() = IsolatedContext(
        id = ID_SYNC_RESTORE,
        precondition = { syncAutoRestore.canRestore() },
        resolveDialog = { IsolatedOnboardingDialog.SyncRestore },
        transition = { event -> when (event) {
            PrimaryClicked -> {
                pixel.fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
                syncAutoRestore.restoreSyncAccount()
                Advance
            }
            SecondaryClicked -> {
                pixel.fire(PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE, type = Unique())
                SwitchTo(skipPath)
            }
            else -> Stay
        }},
    )

    private fun initialReinstallUserStep() = IsolatedContext(
        id = ID_INITIAL_REINSTALL_USER,
        precondition = { !syncAutoRestore.canRestore() && appBuildConfig.isAppReinstall() },
        resolveDialog = { IsolatedOnboardingDialog.InitialReinstallUser(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            SecondaryClicked -> {
                pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                SwitchTo(skipPath)
            }
            else -> Stay
        }},
    )

    private fun initialStep() = IsolatedContext(
        id = ID_INITIAL,
        precondition = { !syncAutoRestore.canRestore() && !appBuildConfig.isAppReinstall() },
        resolveDialog = { IsolatedOnboardingDialog.Initial(showDuckAiCopy = isDuckAiCopyEnabled()) },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun comparisonChartStep() = IsolatedContext(
        id = ID_COMPARISON_CHART,
        resolveDialog = { IsolatedOnboardingDialog.ComparisonChart(showDuckAiCopy = isDuckAiCopyEnabled()) },
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
        // Both checks needed: createIntent(context) is nullable today, and the legacy code falls
        // through to ADDRESS_BAR_POSITION when shouldShowDialog() is true but createIntent returns null
        // (firing DEFAULT_BROWSER_DIALOG_NOT_SHOWN). Encoding both here preserves that behaviour.
        precondition = {
            defaultRoleBrowserDialog.shouldShowDialog() &&
                defaultRoleBrowserDialog.createIntent(context) != null
        },
        resolveDialog = {
            // Today the system intent is launched from inside COMPARISON_CHART's primary;
            // here it becomes a discrete step whose dialog carries the intent.
            IsolatedOnboardingDialog.DefaultBrowser(intent = defaultRoleBrowserDialog.createIntent(context)!!)
        },
        transition = { event -> when (event) {
            is DefaultBrowserPromptFinished -> {
                defaultRoleBrowserDialog.dialogShown()
                appInstallStore.defaultBrowser = event.isDefaultBrowser
                if (event.isDefaultBrowser) appInstallStore.wasEverDefaultBrowser = true
                pixel.fire(
                    if (event.isDefaultBrowser) DEFAULT_BROWSER_SET else DEFAULT_BROWSER_NOT_SET,
                    mapOf(DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
                )
                Advance
            }
            else -> Stay
        }},
    )

    private fun addressBarPositionStep() = IsolatedContext(
        id = ID_ADDRESS_BAR_POSITION,
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },  // matches today's "skip if shouldShowDialog == false"
        resolveDialog = { IsolatedOnboardingDialog.AddressBarPosition(showSplitOption = isSplitOmnibarEnabled()) },
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
        precondition = {
            defaultRoleBrowserDialog.shouldShowDialog() &&
                androidBrowserConfigFeature.showInputScreenOnboarding().isEnabled()
        },
        resolveDialog = { IsolatedOnboardingDialog.InputScreen(showDuckAiCopy = isDuckAiCopyEnabled()) },
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
        precondition = {
            onboardingStore.getInputScreenSelection() == true &&
                duckAiOnboardingExperimentManager.enroll() in setOf(
                    TREATMENT_WITH_DUCK_AI_DEFAULT,
                    TREATMENT_WITH_SEARCH_DEFAULT,
                )
        },
        resolveDialog = {
            val variant = duckAiOnboardingExperimentManager.enroll()
            IsolatedOnboardingDialog.InputScreenPreview(isSearchDefault = variant == TREATMENT_WITH_SEARCH_DEFAULT)
        },
        transition = { event -> when (event) {
            PrimaryClicked -> Advance
            else -> Stay
        }},
    )

    private fun skipOnboardingOptionStep() = IsolatedContext(
        id = ID_SKIP_ONBOARDING_OPTION,
        resolveDialog = { IsolatedOnboardingDialog.SkipOnboardingOption },
        transition = { event -> when (event) {
            PrimaryClicked -> {
                pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                duckChat.setInputScreenUserSetting(true)
                AbortPlan
            }
            SecondaryClicked -> {
                // Advance off the end of the single-step skip path → pops the call stack →
                // resumes the main path at caller.indexAtJump + 1. For a reinstall user
                // (caller = InitialReinstallUser): walk skips Initial (precondition false),
                // lands on ComparisonChart. Same destination for a canRestore user (caller =
                // SyncRestore walks past the same gates).
                pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                Advance
            }
            else -> Stay
        }},
    )

    private suspend fun isDuckAiCopyEnabled(): Boolean = /* same logic as today's WelcomePageViewModel */
    private suspend fun isSplitOmnibarEnabled(): Boolean = /* same logic as today */
}
```
