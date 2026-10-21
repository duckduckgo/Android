# Native input

`NativeInputModeWidget` is the single input surface for search and Duck.ai: the omnibar's focused field,
the Duck.ai chat composer, the contextual Duck.ai sheet and the edit-message screen. Its controls are being
moved to a plugin architecture; part of the code still predates that. Read **Refactor in progress** before
building on anything the widget wires by hand.

---

## Where things live

| Concern | Location |
|---|---|
| Widget + `NativeInputWidget` interface | `duckchat/duckchat-impl/.../NativeInputModeWidget.kt` |
| Widget ViewModel (state writer, most pixels) | `duckchat/duckchat-impl/.../NativeInputModeWidgetViewModel.kt` |
| Widget body layout (containers live here) | `duckchat/duckchat-impl/.../view_native_input_mode_switch_widget.xml` |
| `NativeInputPlugin`, plugin point, `NativeInputHost` | `duckchat/duckchat-impl/.../NativeInputPlugin.kt` |
| Control plugins (one per control) | `duckchat/duckchat-impl/.../StopStreamingNativeInputPlugin.kt` and siblings in that package |
| State contract (`-api`) | `duckchat/duckchat-api/.../NativeInputState.kt`, `NativeInputStateProvider.kt`, `NativeInputStatePublisher.kt` |
| State store implementation | `duckchat/duckchat-impl/.../RealNativeInputStateStore.kt` |
| Footer plugin point | `duckchat/duckchat-impl/.../NativeInputFooterPlugin.kt` |
| Omnibar host (`:app`) | `app/src/main/java/com/duckduckgo/app/browser/nativeinput/NativeInputManager.kt` |
| Omnibar wrapper layout (both positions) | `app/src/main/res/layout/input_mode_widget_card_view.xml` |
| Contextual sheet host | `duckchat/duckchat-impl/.../ContextualNativeInputManager.kt` |
| Edit-message host | `duckchat/duckchat-impl/.../EditPromptActivity.kt` |

`:app` depends on `:duckchat-impl` directly, so `:app` can contribute plugins without an API move.

## Hosts

| Host | Configured with | Notes |
|---|---|---|
| Omnibar (top / bottom) | `configure(tabId, isDuckAiMode, isBottom)` | `NativeInputManager` inflates the single wrapper and sets per-position chrome in `applyPositionChrome`. Duck.ai mode is always bottom. |
| Contextual Duck.ai sheet | `configureContextual(tabId)` | Shares the browser tab's state slot with the omnibar widget; the widget forces `DUCK_AI_CONTEXTUAL` and never reshapes the card. |
| Edit-message screen | `configureForEdit(sessionId)` | Publishes under a synthetic key (`edit:<sessionId>`), so it never overwrites the real tab. Must not touch shared browser state. |

---

## State flows one way

`NativeInputState` is per tab, held by `RealNativeInputStateStore` and exposed through two `-api` interfaces:

| Side | Interface | Use |
|---|---|---|
| Read | `NativeInputStateProvider.state` | Follows the selected browser tab. Plugins observe this. |
| Read | `NativeInputStateProvider.stateForTab(tabId)` | Keyed, synchronous. Use it on the edit surface, whose synthetic key `state` never selects. |
| Write | `NativeInputStatePublisher.update(tabId) { ... }` | Widget ViewModel only. Use `update`, not `publish`, so fields owned by others survive. |

Existing writers outside the ViewModel, do not add more: `DuckChatJSHelper` (`submitEnabled`),
`ContextualNativeInputManager` (restores the slot on close/reopen), `TabDataRepository` and
`EditPromptActivity` (clearing only).

A control decides its own visibility and enabled state from these fields. If a control needs data the state
does not carry, add a **defaulted** field to `NativeInputState` and have the widget ViewModel publish it.
That is a `-api` change: it needs an API Proposal first (`.claude/docs/contributions.md`).

## Adding or changing a control

Every control is a `NativeInputPlugin` with its own View and ViewModel. Do not add per-button callbacks,
visibility helpers or click handling to the widget.

```kotlin
// Plugin: picks the container and the input contexts it renders in
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = NativeInputPlugin::class,
    featureName = "pluginClearTextNativeInput",       // remote kill switch per control
    parentFeatureName = "pluginPointNativeInput",
)
class ClearTextNativeInputPlugin @Inject constructor() : NativeInputPlugin {
    override val containerId: Int = R.id.clearTextContainer
    // override val supportedContexts = setOf(InputContext.BROWSER)  // default: all contexts
    override fun createView(context: Context, host: NativeInputHost): View = ClearTextView(context).apply { this.host = host }
}

// ViewModel: derives UI from state; edit surface reads its own key
@ContributesViewModel(ViewScope::class)
class ClearTextViewModel @Inject constructor(private val provider: NativeInputStateProvider) : ViewModel() {
    fun isVisible(editTabId: String?): Flow<Boolean> =
        (if (editTabId != null) provider.stateForTab(editTabId) else provider.state)
            .map { it.hasText }
            .distinctUntilChanged()
}
```

The View is `@InjectWith(ViewScope::class)`, injects in `onAttachedToWindow`, resolves its ViewModel lazily
via `findViewTreeViewModelStoreOwner()`, observes in the view-tree lifecycle scope, sets both its own and
its container's visibility, and acts back through `host` (`host.clearInput()`, `host.submit()`, ...). Pass
`host.takeIf { it.isEditSurface() }?.tabId()` to the ViewModel. Copy `ClearTextView.kt` for the shape.

Checklist:

- The container is a `FrameLayout` in `view_native_input_mode_switch_widget.xml` with the plugin's `containerId`.
- Declare `supportedContexts` when the control has no place in a context (start-chat is `BROWSER` only).
- Need a new action from the widget? Add a method to `NativeInputHost` (impl-only, no proposal) and implement it in the widget.
- Pixels move with the control. Fire them from the control's ViewModel, not the widget's; a control moved without its pixel still looks right while telemetry silently drops.
- Content descriptions move with the view, and placement must hold in RTL, where leading and trailing swap.
- One ViewModel test per control for its visibility/enabled rules (`SubmitButtonViewModelTest.kt` is the model).

Footers (usage limits, terms consent) are a separate plugin point: implement `NativeInputFooterPlugin`
(`featureName = "plugin<Name>NativeInputFooter"`, `parentFeatureName = "pluginPointNativeInputFooter"`).
`NativeInputFooterCoordinator` orders them by `priority`; footers sharing a `FooterCategory` are mutually
exclusive. General plugin-point mechanics: `.claude/docs/plugin-system.md`.

---

## Rules and gotchas

| Rule | Detail |
|---|---|
| Do not grow the widget or its ViewModel | Both are being shrunk. New behaviour goes in a plugin, a host, or a pure function; growth in either file needs a stated reason. |
| Visibility has one owner | The host and the plugin both deciding a control's visibility makes it flicker or stick. Widget-owned rules are pure `NativeInputState` extension functions at the bottom of the widget file, tested in `NativeInputPluginVisibilityTest.kt` and `NativeInputControlsVisibilityTest.kt`. |
| Evict per-tab state with the tab | Every tab-deleting path in `app/src/main/java/com/duckduckgo/app/tabs/model/TabDataRepository.kt` calls `nativeInputStatePublisher.clearTab` next to `tabVisitedSitesRepository.clearTab`. A new path that skips it leaks state into the next tab that reuses the id. |
| Buffer writes that can arrive before `configure*` | `activeTabId` is null until configured. A setter fed by a `distinctUntilChanged` source must buffer into a `pending*` field and replay in `replayPendingState`, or the value is lost for good. `configureForEdit` does not replay. |
| Guard injected members | `configure*`, `setWidgetPosition` and `applyDefaultTogglePosition` run inside `doOnAttach`; ViewModel setters check `isAttachedToWindow`. See `.claude/docs/lateinit-hazards.md`. |
| The widget reads `viewModel.state`, plugins read the provider | The provider seeds unknown tabs with `NativeInputState.zero()`; the widget avoids rendering that placeholder. |
| Keep the omnibar wrapper a `LinearLayout` | `applyOmnibarShape` positions the card with negative margins. A `ConstraintLayout` clamps them and the focused card drops below the unfocused omnibar. |
| Resolve card-themed attrs against `card.context` | The card carries `android:theme="?attr/daxInputModeCardThemeOverlay"`, which remaps `daxColorSurface`/`daxColorWindow` in Fire Tabs. Resolving off the root's context paints the non-fire colour. |
| Contextual and omnibar widgets share one tab slot | Gate context-specific rendering on the host (`isContextualSurface()`, `isEditSurface()`), not on `inputContext` alone. |
| The edit widget must not write shared browser state | No `selectedMode`/`inputQuery` pushes, ignores global `ChatState`, never launches the edit screen itself. |
| Bottom-pinned input and insets | Use `EdgeToEdgeHandler.applyNavigationBarInsetsAsMargin` (`common/common-utils/.../EdgeToEdgeHandler.kt`); hand-rolled inset maths leaves the input behind the IME or nav bar on recent target SDKs. |
| Removing or renaming a layout or id | Also compile instrumented tests: `./gradlew :app:compilePlayDebugAndroidTestKotlin`. Unit-test runs do not compile `androidTest`. |

## Talking to the Duck.ai frontend

Many widget callbacks end in a message to the Duck.ai frontend, which ships on its own release train. A
change that compiles can still break or silently do nothing on the other side.

| Rule | Detail |
|---|---|
| Names come from the frontend contract | Events are `SubscriptionEventData` on the `aiChat` feature, sent with `JsMessaging.sendSubscriptionEvent`. There is no registry, so a name or payload key nobody on the frontend agreed to fails silently. Link the frontend task or PR. |
| Follow the naming | Native-to-frontend actions use a `submit` prefix (`submitNewChatAction`, `submitChangeModelAction`, `submitPromptInterruption`). |
| Wire every host | The browser tab (`BrowserTabFragment`) and the contextual sheet (`ContextualNativeInputManager`) each own a `JsMessaging`. A new widget callback that sends an event must be forwarded by both. |
| Mind drops and pairs | A nullable `jsMessaging` drops events sent before the webview binds. An event that carries state (focus, streaming) needs its opposite on every exit path: tab switch, dismissal, destroy. |
| Test the message | Assert `subscriptionName` and `params` in `RealDuckChatJSHelperTest.kt`; inline literals in a fragment are untested by default. |

## Feature flags

Native input itself has no flag: the omnibar click catcher always routes to it, and the omnibar's leftover
focus code is dead (see **Refactor in progress**). The flags below gate Duck.ai-side integrations and default on, so "it's behind a flag" is not a reason for thin coverage. Check
`DuckChatFeature.kt` for current defaults.

| Flag | Gates |
|---|---|
| `aiChat.nativeChatInput` | Native composer on Duck.ai pages (`native-input=true` param). |
| `aiChat.contextualNativeInput` | Native input in the contextual sheet. |
| `aiChat.nativePromptEditing` | Edit-message screen. |
| `aiChat.nativeInputNavBar` | Persistent nav bar above the top omnibar input. |
| `pluginPointNativeInput` + each plugin `featureName` | Individual controls (remote kill switches). |

## Testing

| What | Where | Run |
|---|---|---|
| Widget ViewModel, plugin ViewModels, store, footers | `duckchat/duckchat-impl/src/test` | `./gradlew :duckchat-impl:testDebugUnitTest` |
| Omnibar host, coordinator, animator, nav decisions | `app/src/test/java/com/duckduckgo/app/browser/nativeinput` | `./gradlew :app:testPlayDebugUnitTest --tests "*nativeinput*"` |
| Wrapper layout structure | `app/src/androidTest/java/com/duckduckgo/app/browser/nativeinput/NativeInputHostLayoutTest.kt` | instrumented |
| End-to-end | `.maestro/unified_input_screen` (tag `unifiedInputTest`) | needs an internal build |

There is no widget-level UI test. For anything visual, verify on an internal build in top and bottom
omnibar, a Duck.ai page, the contextual sheet, the edit screen and a Fire Tab, focused and unfocused.

---

## Refactor in progress

Overall tracker: https://app.asana.com/0/0/1216509599440244/f. Do not extend the hand-wired paths below; if a
change needs them, check the linked task first.

| Still hand-wired | Direction | Task |
|---|---|---|
| Fire, tabs, menu and back: `onFireButtonTapped`, `onBackPressed`, `setNavBarVisible`, `bindTabCount`, the leading fire found by walking ancestors | Plugins contributed from `:app`, placed via host-resolved slots | https://app.asana.com/0/0/1218750568353646/f |
| Onboarding lock rendered by the widget (`setInteractionLocked`) | Each plugin renders its own locked state from `interactionLock` | https://app.asana.com/0/0/1218750257810051/f |
| Card chrome duplicated in every host layout, shaped by `applyOmnibarShape` and `applyPositionChrome` | One card owned and styled inside the widget | https://app.asana.com/0/0/1217275429788919/f |
| Legacy focused state in `OmnibarLayout` | Omnibar becomes display-only; the widget owns all text input | https://app.asana.com/0/0/1215373984475612/f |
| `DuckChat` god-interface in `duckchat-api` | Split into single-responsibility `DuckAi*` interfaces | https://app.asana.com/0/0/1215641620311419/f |

Known dead or stale code, safe to remove alongside the work above: the in-widget trailing fire, tab count and
menu buttons (`inputModeMainButtonsContainer`) are never shown because every host calls `hideMainButtons()`;
`setFloatingSubmitContainer` only hosts the new-line button.

Older PRs, proposals and docs reference things that no longer exist. Do not build against them:
`NativeInputHost.getInputState()` and `getPromptContribution()` (replaced by the state store),
`NativeInputFieldSuppressor` (never landed), `InputScreenButtons`, the `InputModeWidget` base class, and the
separate bottom omnibar wrapper layout.
