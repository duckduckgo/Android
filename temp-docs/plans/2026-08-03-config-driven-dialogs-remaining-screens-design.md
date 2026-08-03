# Config-driven linear onboarding dialogs — step 3: port remaining screens

Design for step 3 of the [tech design](https://app.asana.com/1/137249556945/project/72649045549333/task/1216854264994244):
*"Port remaining screens — one binder plus one config mapping each; mechanical after step 2."*

## Context

Step 2 landed the render engine, its axis controllers, the binder contracts and two screens ported end to end
(comparison chart — stateless; address bar position — stateful) behind
`OnboardingBrandDesignUpdateToggles#configDrivenDialogs` ([PR #9365](https://github.com/duckduckgo/Android/pull/9365)).

Everything the engine needs already exists. Nothing in `engine/` changes. Each remaining screen adds:

1. a `ContentConfig` variant (plus a state class if stateful),
2. a binder,
3. a `DialogConfigResolver.resolve()` branch,
4. a `ContentControllerImpl.bind()` branch.

The rest of the work closes gaps that keep the flag-on arm from being behaviourally complete, which step 4
(Maestro green in both flag states) depends on.

The POC branch `feature/lpaczos/linear-onboarding-dialog-spec` has all eight binders and is a useful reference,
but it predates the landed contracts: its `BindScope` carries an extra `emit` channel, its `ContentHandle` uses
`entrance` where the landed one has `afterFade` / `onContentReady`, its titles go through a
`DialogTitleController` that has since become the `OnboardingDialogTitleView` widget, and its resolver has no
`cardArrow`. Every port is reviewed against the landed contracts and the legacy fragment, not copied.

## Scope

In:

- Six binders and eight config mappings for the remaining rendered screens.
- Legacy once-ever "dialog shown" pixel parity for the new arm.
- Command-only dialog handling (notification permission, default browser prompt, add widget).
- The `ContentInteraction` set the ported screens need, and its view-model handling.
- Quick setup's bottom sheets, switch side effects and on-resume OS re-sync.
- Add-to-dock video surface lifecycle.

Out:

- The intro animation (`NewUserOnboardingActivityDialog.IntroAnimation`), implemented on a separate branch.
  `ConfigDrivenWelcomePage.settleIntroViews()` and the view model's `emit(IntroAnimationFinished)` stay as they are.
- Deleting the legacy fragment and view model — that is step 5.
- Changing the feature flag default. It stays off; enabling is manual for testing.

## Screens

| Dialog | `ContentConfig` | Binder | Background | Embellishment | Card arrow |
|---|---|---|---|---|---|
| `Initial` | `Welcome` | `WelcomeBinder` | `Welcome` | `WalkingDax` | `AtStart` |
| `InitialReinstallUser` | `Welcome` | `WelcomeBinder` | `Welcome` | `WalkingDax` | `AtStart` |
| `SyncRestore` | `Welcome` | `WelcomeBinder` | `Welcome` | `WalkingDax` | `AtStart` |
| `AddToDock` | `AddToDock` | `AddToDockBinder` | `AddToDock` | `None` | `Hidden` |
| `WidgetPrompt` | `WidgetPrompt` | `WidgetPromptBinder` | `AddWidget` | `LeftWing` | `AtEnd` |
| `InputScreen` | `InputScreen` | `InputScreenBinder` | `InputType` | `LeftWing` | `AtStart` |
| `InputScreenPreview` | `InputScreenPreview` | `InputScreenPreviewBinder` | `InputType` | `None` | `Hidden` |
| `QuickSetup` | `QuickSetup` | `QuickSetupBinder` | `QuickSetup` | `BottomWing` | derived from legacy |

The three `Welcome` dialogs differ only in copy and CTAs, so they share one binder and one config variant.
`SyncRestore` is a rendered screen in the legacy arm (it has its own dialog copy and shown pixel); the current
view model's `SyncRestore -> emit(SkipRequested)` shortcut is removed.

Background, embellishment and card-arrow values above are read off the legacy fragment's per-dialog branches.
Each is re-confirmed against `configureDaxCta` / `showDialogWithoutAnimation` while porting that screen, since
legacy expresses the arrow as two separate calls (`setShowArrow`, `setArrowAnimationFraction`) and its depth
comes from the decoration axis, which `CardAnchorController` now owns.

## Contract extensions

Additive only — no landed signature changes.

### `ContentConfig`

```kotlin
data class Welcome(
    override val title: TextConfig,
    val body1: TextConfig,
    val body2: TextConfig?,
) : ContentConfig

data class AddToDock(override val title: TextConfig, val body: TextConfig) : ContentConfig

data class WidgetPrompt(override val title: TextConfig, val body: TextConfig) : ContentConfig

data class InputScreen(
    override val title: TextConfig,
    val description: TextConfig,
    val initialWithAi: Boolean,
) : ContentConfig, Stateful<InputScreenContentState>

data class InputScreenPreview(
    override val title: TextConfig,
    val isSearchDefault: Boolean,
    val showModeToggle: Boolean,
    val searchSuggestions: List<DaxDialogIntroOption>,
    val chatSuggestions: List<DaxDialogIntroOption>,
) : ContentConfig, Stateful<InputScreenPreviewContentState>

data class QuickSetup(
    override val title: TextConfig,
    val showSplitOption: Boolean,
    val hideSetDefaultBrowserRow: Boolean,
    val hideAddWidgetRow: Boolean,
    val hideAddressBarRow: Boolean,
    val isReinstallUser: Boolean,
    val initialAddressBarPosition: OmnibarType,
    val initialWithAi: Boolean,
) : ContentConfig, Stateful<QuickSetupContentState>
```

State classes: `InputScreenContentState(withAi)`, `InputScreenPreviewContentState(isSearchSelected)`,
`QuickSetupContentState(defaultBrowserChecked, widgetChecked, addressBarPosition, withAi)`.
`initialState()` stays pure — derived from config values only, so configs remain value-comparable.

`InputScreenPreview` carries its suggestion lists as data, which means `DialogConfigResolver` takes
`OnboardingStore` to read them.

### `ContentInteraction`

Today an empty marker interface. It gains the interactions the ported screens raise outside the shared CTAs:

```kotlin
data class SubmitInput(val query: String, val isChat: Boolean, val fromSuggestion: Boolean) : ContentInteraction
data object EditAddressBarPosition : ContentInteraction
data object EditSearchOptions : ContentInteraction
data class SetDefaultBrowserToggled(val checked: Boolean) : ContentInteraction
data class AddWidgetToggled(val checked: Boolean) : ContentInteraction
```

`BindScope` is unchanged. The POC gave binders a second, direct `emit` channel to the orchestrator for the
input preview's submit; routing it through `SubmitInput` instead keeps the orchestrator's entry points to two —
the engine (CTA clicks and `ContentHandle.result`) and the view model — and matches the tech design's
`ContentInteraction` list.

### `ContentController`

Six more `bind()` branches. `resetStage()` already lists every content include, so it needs no change.

## Binders

Each binder owns only its own include's view tree: no shared card views, no view model calls except through
`BindScope.execute`, and it never starts the animations it declares.

- **`WelcomeBinder`** — stateless. Title, `body1`, optional `body2` (hidden explicitly, since a previous dialog
  may have left it hidden). Legacy applies `preventWidows()` and, for one copy variant, HTML — matched here.
- **`AddToDockBinder`** — stateless. Owns the dock demo video: the `AspectRatioTextureView` surface listener is
  set up at bind, playback starts from `ContentHandle.onContentReady` (it is not an `Animator`), and the
  `MediaPlayer` is released from `ContentHandle.unbind`.
- **`WidgetPromptBinder`** — stateless. Title and body only.
- **`InputScreenBinder`** — stateful over `InputScreenContentState`. The search/AI picker writes the selection
  into the state, the state collector renders it, and `ContentHandle.result` builds
  `NewUserOnboardingEvent.InputModeConfirmed(withAi)`.
- **`InputScreenPreviewBinder`** — stateful over `InputScreenPreviewContentState`. Mode tabs write which tab is
  selected; everything mode-dependent (suggestion buttons, hint, `inputType`, `imeOptions`, action icon, IME
  restart) renders from the collector. Submit from the action icon, the IME action or a suggestion button goes
  out as `ContentInteraction.SubmitInput`. The suggestion-button stagger is an `afterFade` animator; the
  keyboard focus that legacy performs at the same point runs from `onContentReady`, gated on
  `screenHeightDp >= 600` as legacy does. This screen has no CTA config: it advances on submit.
  Interactive views revealed by `afterFade` start with `isClickable = false`, restored from the animator's end
  listener, per the `ContentHandle.afterFade` contract.
- **`QuickSetupBinder`** — stateful over `QuickSetupContentState`. Row visibility from the config's `hide*`
  flags; the default-browser and add-widget switches report toggles through `ContentInteraction` and render
  from the state; the address-bar and search-option rows raise `EditAddressBarPosition` / `EditSearchOptions`
  and render the state's current selection as icon plus label. `ContentHandle.result` builds
  `NewUserOnboardingEvent.QuickSetupConfirmed(type, withAi)` from the state.

## View model

`ConfigDrivenOnboardingPageViewModel` gains:

- **Shown pixels.** A new `OnboardingDialogShownPixels` maps a `NewUserOnboardingActivityDialog` to the legacy
  once-ever `PREONBOARDING_*_SHOWN_UNIQUE` pixel (and `CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW`),
  ported 1:1 from the legacy view model's `fireDialogShownPixel`. Exhaustive `when`, no `else`, so a new dialog
  cannot compile without an explicit decision. Called from `applyStep` when a config resolves.
  Today the new arm fires none of these pixels — this is a parity gap, not new telemetry. The per-event
  pixels already fire in `NewUserOnboardingPlanProvider` for both arms and need no work.
- **Command-only dialogs.** `advancePastUnrenderedDialog` becomes `handleCommandOnlyDialog`, reached only for
  the four dialogs `resolve()` maps to null: `IntroAnimation`, `NotificationPermission`, `DefaultBrowserPrompt`,
  `AddWidget`. Every other branch is deleted, and the config-producing dialogs are listed exhaustively as
  `Unit` so the compiler keeps the two mappings in sync.
- **`onContentInteraction`.** Replaces today's no-op. `SubmitInput` emits
  `NewUserOnboardingEvent.InputDemoQuerySubmitted`. The two toggles mirror the view's optimistic switch state
  into the store *before* running their side effect — otherwise a later corrective write of the old value (a
  declined system dialog, an on-resume re-sync) is deduped by `MutableStateFlow` as a no-change and never
  reaches the binder. The two edit interactions read the current selection from the store and send the matching
  bottom-sheet command.
- **Quick setup results and re-sync.** `onAddressBarBottomSheetResult`, `onSearchOptionsBottomSheetResult`,
  `onQuickSetupDefaultBrowserSet` / `NotSet` (no orchestrator advance, no telemetry — the step only advances on
  `QuickSetupConfirmed`), and `syncQuickSetupSwitches()` reading `DefaultBrowserDetector.isDefaultBrowser()` and
  `WidgetCapabilities.hasInstalledWidgets` off the IO dispatcher into the store. Called from `onResume` and from
  the fragment's `ActivityNotFoundException` fallback, where no activity launches so no later `onResume` fires.
  `DefaultBrowserDetector` is injected back (step 2 dropped it as unused).
- **New commands.** `ShowQuickSetupDefaultBrowserDialog`, `OpenDefaultBrowserSystemSettings`,
  `ShowRemoveWidgetBottomSheet`, `ShowQuickSetupAddressBarPositionBottomSheet`,
  `ShowQuickSetupSearchOptionsBottomSheet`.

Store access needs both the step id and the config, since `ContentValueStore.contentState` is keyed on
`LinearOnboardingStepId`. A private helper reads the current `stepId` and `content as? ContentConfig.QuickSetup`
off `_viewState.value` and returns null when quick setup is not the screen on show.

## Fragment

`ConfigDrivenWelcomePage` gains the new command branches, the quick-setup bottom sheets and their fragment
result listeners, and the default-browser system-settings intent with its `ActivityNotFoundException` fallback.
All of it is command handling — no per-dialog branching, and the render path is untouched.

## Testing

`:app` unit tests cannot inflate XML, so binders are not unit-testable. Coverage goes where the decisions are:

- `DialogConfigResolverTest` — one case per new dialog asserting the whole `DialogConfig` as data, including the
  custom-AI copy variants and the `showSplitOption` / `hide*` pass-through.
- `ConfigDrivenOnboardingPageViewModelTest` — a shown pixel per dialog, each `ContentInteraction`, bottom-sheet
  results landing in the store, `onResume` re-sync, and command-only routing.
- `OnboardingDialogShownPixelsTest` — the mapping, including the dialogs that deliberately fire nothing.

Verification per commit: `./gradlew :app:testInternalDebugUnitTest --tests "…"` for the touched tests, then
`./gradlew spotlessApply lint_check` and the full `:app` unit test run once at the end. Manual device check of
each ported screen with the flag on, since the choreography is not unit-testable.

## Commit sequence

One commit per screen, each self-contained (config variant, binder, resolver branch, content-controller branch,
resolver test):

1. `WelcomeBinder` + `Initial`, `InitialReinstallUser`, `SyncRestore`
2. `AddToDockBinder` + `AddToDock`
3. `WidgetPromptBinder` + `WidgetPrompt`
4. `InputScreenBinder` + `InputScreen`
5. `InputScreenPreviewBinder` + `InputScreenPreview` (adds `ContentInteraction.SubmitInput` and its view-model handling)
6. `QuickSetupBinder` + `QuickSetup` (adds the quick-setup interactions, commands, bottom sheets and re-sync)

Then the parity commits:

7. `OnboardingDialogShownPixels` and its wiring
8. Command-only dialog cleanup — `advancePastUnrenderedDialog` reduced to the four command dialogs

Screens 1–4 are independent of each other. 5 and 6 each extend `ContentInteraction`, so they follow the screens
that do not. 7 and 8 come last because 8 can only drop a branch once every screen it covered renders.

## Risks

- **Choreography regressions on ported screens.** Mitigated by the flag defaulting to off, and by manual device
  verification per screen. The engine is not modified, so a mistake is contained to one binder's config.
- **A screen's legacy behaviour depends on shared card views a binder may not touch.** Legacy wraps the input
  preview's mode switch in a card-level `beginDelayedTransition`; the binder has no handle on the card, so the
  field resizes without that transition. Noted as a deliberate difference rather than reached around.
- **Silent pixel drift.** The exhaustive `when` in `OnboardingDialogShownPixels` plus the exhaustive
  `handleCommandOnlyDialog` means a new dialog fails to compile until both arms have an explicit mapping.
