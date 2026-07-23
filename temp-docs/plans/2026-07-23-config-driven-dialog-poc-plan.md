# Config-Driven Onboarding Dialogs POC — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** POC of the v6 design (`temp-docs/2026-07-09-onboarding-dialog-spec-design-v6-summary.md`): one `DialogConfig` per screen, one diff-based render engine (animated + snapped in one code path), a parallel config-driven fragment behind a new remote toggle. Legacy path stays byte-identical except two mechanical edits (shown-pixel extraction, rollout seam).

**Architecture:** Plan-provider dialogs (`NewUserOnboardingActivityDialog`) are mapped by a pure `DialogConfigResolver` to `DialogConfig` (background / embellishment / content / CTAs). A slim VM forwards config + animate policy. A `DialogRenderEngine` diffs previous vs new config per axis (background, embellishment, card anchor, step indicator, content) and drives per-screen `DialogBinder`s that return a `ContentHandle`. Stateful screens keep working state in `ContentValueStore` (`MutableStateFlow` per screen keyed by config class).

**Tech Stack:** Kotlin, Android views (no Compose), Anvil/Dagger (`@ContributesViewModel`, `@InjectWith`), Lottie, ConstraintLayout, kotlinx.coroutines. Module: `:app`.

## Global Constraints

- POC: **NO unit tests, NO Maestro tests.** Verification = compilation + spotless.
- All new code in package `com.duckduckgo.app.onboarding.ui.page.configdriven` → directory `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/`.
- **No new layout XML, no new strings.** Reuse `R.layout.content_onboarding_welcome_page_update`, its includes, and existing `R.string.*` / drawables.
- Legacy files must NOT be modified except where a task explicitly says so (`BrandDesignUpdatePageViewModel.kt` pixel extraction; rollout seam files).
- Configs are value-comparable data: no lambdas, no views, no Context inside `DialogConfig`/`ContentConfig`.
- Engine rules (from spec): bespoke behaviour lives in content config or handle, never in the engine; no code branches on (previous, next) screen pairs — each axis controller sees only its own axis.
- Style: ktlint via spotless (max line 150), `@SingleInstanceIn` never `@Singleton`, no hardcoded dispatchers (inject `DispatcherProvider`), file license header required (copy the 2026 Apache header from any sibling file, e.g. `PreOnboardingDialogType.kt`).
- Every new file starts with the standard DuckDuckGo Apache 2.0 license header (copy verbatim from `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/PreOnboardingDialogType.kt` lines 1-15, keep year as found there).
- Subagents do NOT run `git commit` and do NOT run gradle unless the task says so. The orchestrator commits.
- Existing key sources (read, don't modify unless told):
  - Fragment: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdateWelcomePage.kt` (3118 lines; `configureDaxCta` :871-1586, `showDialogWithoutAnimation` :1697-2315)
  - VM: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdatePageViewModel.kt`
  - Dialogs: `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/NewUserOnboardingActivityDialog.kt`
  - Events: `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/NewUserOnboardingEvent.kt`
  - Steps: `app/src/main/java/com/duckduckgo/app/onboarding/orchestrator/NewUserOnboardingActivityStep.kt` (`StepProgress` :46, `stepIndicatorProgress()` :53-59)
  - Background: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/OnboardingBackgroundAnimator.kt` (`OnboardingBackgroundStep` :39-71)
  - Fit veto: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdateOnboardingLayoutHelper.kt`, `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/OnboardingDecorationFitCorrector.kt`
  - Comparison config: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/ComparisonChartConfig.kt`
  - Card layout: `app/src/main/res/layout/pre_onboarding_dax_dialog_cta_brand_design_update.xml`; page layout: `app/src/main/res/layout/content_onboarding_welcome_page_update.xml`

---

### Task 1: Config data model + ContentValueStore

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/TextConfig.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfig.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentValueStore.kt`

**Interfaces:**
- Consumes: `OnboardingBackgroundStep`, `StepProgress`, `NewUserOnboardingEvent`, `OmnibarType`, `ComparisonChartConfig`, `DaxBubbleCta.DaxDialogIntroOption` (nested in `app/src/main/java/com/duckduckgo/app/cta/ui/Cta.kt:2108`).
- Produces: everything below, exactly as written — later tasks import these names verbatim.

- [ ] **Step 1: Write TextConfig.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.content.Context
import androidx.annotation.StringRes

sealed interface TextConfig {
    data class Resource(@StringRes val resId: Int) : TextConfig
    data class Literal(val text: String) : TextConfig

    fun resolve(context: Context): String = when (this) {
        is Resource -> context.getString(resId)
        is Literal -> text
    }
}
```

- [ ] **Step 2: Write DialogConfig.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep

/** Which animated stage decoration accompanies the dialog. Fit veto may still hide it at runtime. */
enum class Embellishment { WalkingDax, BobbingDax, BottomWing, LeftWing, None }

sealed interface CtaAction {
    /** CTA click forwards this event to the orchestrator as-is. */
    data class Emit(val event: NewUserOnboardingEvent) : CtaAction

    /** CTA click asks the bound screen's [ContentHandle.result] to build the event from live state. */
    data object Submit : CtaAction
}

data class CtaConfig(
    val text: TextConfig,
    val action: CtaAction,
)

data class DialogConfig(
    val background: OnboardingBackgroundStep,
    val embellishment: Embellishment,
    val content: ContentConfig,
    val primaryCta: CtaConfig,
    val secondaryCta: CtaConfig? = null,
    val stepIndicator: StepProgress? = null,
)
```

- [ ] **Step 3: Write ContentConfig.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig

/** Stateful screens declare their working state; the engine seeds it at bind via [ContentValueStore]. */
interface Stateful<S : Any> {
    fun initialState(): S
}

sealed interface ContentConfig {
    /** Every screen has a title; rendered by the screen's title views through DialogTitleController. */
    val title: TextConfig

    // stateless dialogs
    data class Welcome(
        override val title: TextConfig,
        val body1: TextConfig,
        val body2: TextConfig?,
    ) : ContentConfig

    data class ComparisonChart(
        override val title: TextConfig,
        val config: ComparisonChartConfig,
    ) : ContentConfig

    data class AddToDock(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig

    data class WidgetPrompt(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig

    // stateful dialogs
    data class AddressBar(
        override val title: TextConfig,
        val initialPosition: OmnibarType,
        val showSplitOption: Boolean,
    ) : ContentConfig, Stateful<AddressBarContentState> {
        override fun initialState() = AddressBarContentState(position = initialPosition)
    }

    data class InputScreen(
        override val title: TextConfig,
        val description: TextConfig,
        val initialWithAi: Boolean,
    ) : ContentConfig, Stateful<InputScreenContentState> {
        override fun initialState() = InputScreenContentState(withAi = initialWithAi)
    }

    data class InputScreenPreview(
        override val title: TextConfig,
        val isSearchDefault: Boolean,
        val showModeToggle: Boolean,
        val searchSuggestions: List<DaxDialogIntroOption>,
        val chatSuggestions: List<DaxDialogIntroOption>,
    ) : ContentConfig, Stateful<InputScreenPreviewContentState> {
        override fun initialState() = InputScreenPreviewContentState(isSearchSelected = isSearchDefault)
    }

    data class QuickSetup(
        override val title: TextConfig,
        val showSplitOption: Boolean,
        val hideSetDefaultBrowserRow: Boolean,
        val hideAddWidgetRow: Boolean,
        val hideAddressBarRow: Boolean,
        val isReinstallUser: Boolean,
        val initialAddressBarPosition: OmnibarType,
        val initialWithAi: Boolean,
    ) : ContentConfig, Stateful<QuickSetupContentState> {
        override fun initialState() = QuickSetupContentState(
            defaultBrowserChecked = false,
            widgetChecked = false,
            addressBarPosition = initialAddressBarPosition,
            withAi = initialWithAi,
        )
    }
}

data class AddressBarContentState(val position: OmnibarType)

data class InputScreenContentState(val withAi: Boolean)

data class InputScreenPreviewContentState(val isSearchSelected: Boolean)

data class QuickSetupContentState(
    val defaultBrowserChecked: Boolean,
    val widgetChecked: Boolean,
    val addressBarPosition: OmnibarType,
    val withAi: Boolean,
)
```

Note: check the actual import for `DaxDialogIntroOption` — it is nested inside `DaxBubbleCta` in `app/src/main/java/com/duckduckgo/app/cta/ui/Cta.kt` (~line 2108). Import the same way `BrandDesignUpdatePageViewModel.kt` imports it.

- [ ] **Step 4: Write ContentValueStore.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * VM-owned store of live working state for stateful screens.
 * One MutableStateFlow per screen, keyed by the config class; seeded from initialState() on first use.
 * Survives rotation with the VM; the engine's observation of it is bind-scoped.
 */
class ContentValueStore {
    private val states = mutableMapOf<KClass<*>, MutableStateFlow<*>>()

    @Suppress("UNCHECKED_CAST")
    fun <S : Any> contentState(content: Stateful<S>): MutableStateFlow<S> =
        states.getOrPut(content::class) { MutableStateFlow(content.initialState()) } as MutableStateFlow<S>
}
```

- [ ] **Step 5: Report files created.** No build (batched later).

---

### Task 2: DialogConfigResolver

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`

**Interfaces:**
- Consumes: Task 1 types; `NewUserOnboardingActivityDialog` (all variants — see `NewUserOnboardingActivityDialog.kt:22-45`); `NewUserOnboardingEvent`; `ComparisonChartConfig.Browser(isCustomAiCopy)` / `ComparisonChartConfig.Ai`; `OnboardingStore` (for suggestions — see how `BrandDesignUpdatePageViewModel.setInputScreenPreviewDialog` at :191-206 calls `onboardingStore.getSearchOptions()` / `onboardingStore.getChatSuggestions()`).
- Produces: `class DialogConfigResolver @Inject constructor(private val onboardingStore: OnboardingStore)` with exactly:
  - `fun resolve(dialog: NewUserOnboardingActivityDialog, isCustomAiFlow: Boolean): DialogConfig?`
  - Returns `null` for command-only dialogs: `IntroAnimation`, `NotificationPermission`, `DefaultBrowserPrompt`, `AddWidget` (the VM handles those as commands).

- [ ] **Step 1: Extract the exact copy per dialog from the legacy fragment.**

Read these `BrandDesignUpdateWelcomePage.kt` ranges and note every `R.string.*` id used for titles, bodies, and CTA texts, including custom-AI variants:
- Welcome family (Initial / InitialReinstallUser / SyncRestore): :888-971 and snap twin :1709-1769. Known CTA ids: `syncRestoreDialogPrimaryCta`, `syncRestoreDialogSecondaryCta`.
- Comparison charts: title/CTA come from `ComparisonChartConfig` (`titleRes`, `primaryCtaTextRes`) — do NOT duplicate them; build `ContentConfig.ComparisonChart(title = TextConfig.Resource(config.titleRes), config = config)` and `primaryCta = CtaConfig(TextConfig.Resource(config.primaryCtaTextRes), CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked))`.
- AddToDock: :1090-1170 (title + body + primary CTA string ids).
- WidgetPrompt: :1172-1268. Known CTA ids: `preOnboardingWidgetPromptPrimaryCta`, `preOnboardingWidgetPromptSecondaryCta`.
- AddressBarPosition: :1272-1358. Known primary CTA: `preOnboardingAddressBarOkButton`.
- InputScreen: :1360-1449. Known primary CTA: `preOnboardingInputScreenButton`. Also note the description string id used for `inputScreenDescription`.
- InputScreenPreview: :1451-1583 (title differs for custom-AI flow; primary CTA is the "continue"-style id used there).
- QuickSetup: :973-1075. Known: title `preOnboardingReinstallQuickSetupTitle`; primary CTA `preOnboardingReinstallStartBrowsing`, or `preOnboardingDaxDialog3ButtonCustomAi` when custom-AI flow.

- [ ] **Step 2: Write DialogConfigResolver.kt.**

Structure (fill `TODO-STRING` slots with the ids extracted in Step 1 — the final file must contain zero TODOs):

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep
import javax.inject.Inject

/** Single authority mapping an orchestrator dialog to the screen's DialogConfig. Pure data out; unit-testable off the plan. */
class DialogConfigResolver @Inject constructor(
    private val onboardingStore: OnboardingStore,
) {

    fun resolve(dialog: NewUserOnboardingActivityDialog, isCustomAiFlow: Boolean): DialogConfig? = when (dialog) {
        is NewUserOnboardingActivityDialog.IntroAnimation,
        NewUserOnboardingActivityDialog.NotificationPermission,
        NewUserOnboardingActivityDialog.DefaultBrowserPrompt,
        NewUserOnboardingActivityDialog.AddWidget,
        -> null // command-only steps, handled by the VM directly

        NewUserOnboardingActivityDialog.Initial -> DialogConfig(
            background = OnboardingBackgroundStep.Welcome,
            embellishment = Embellishment.WalkingDax,
            content = ContentConfig.Welcome(
                title = TextConfig.Resource(/* extracted title id */),
                body1 = TextConfig.Resource(/* extracted body1 id */),
                body2 = if (isCustomAiFlow) null else TextConfig.Resource(/* extracted body2 id */),
            ),
            primaryCta = CtaConfig(TextConfig.Resource(/* extracted */), CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked)),
        )
        // ... one branch per remaining dialog, mirroring the legacy branch's copy, background step,
        // embellishment, and CTA events exactly (see mapping table below)
    }
}
```

Mapping table (authoritative; events verbatim from `BrandDesignUpdatePageViewModel.onPrimaryCtaClicked` :230-247 / `onSecondaryCtaClicked` :249-265):

| Dialog | background | embellishment | content | primary CTA action | secondary CTA action |
|---|---|---|---|---|---|
| `Initial` | `Welcome` | `WalkingDax` | `Welcome` (body2 hidden when custom-AI) | `Emit(ContinueClicked)` | none |
| `InitialReinstallUser` | `Welcome` | `WalkingDax` | `Welcome` | `Emit(ContinueClicked)` | `Emit(SkipRequested)` |
| `SyncRestore` | `Welcome` | `WalkingDax` | `Welcome` (no body2) | `Emit(RestoreRequested)` | `Emit(SkipRequested)` |
| `ComparisonChart` | `ComparisonChart` | `BottomWing` | `ComparisonChart(config = ComparisonChartConfig.Browser(isCustomAiCopy = isCustomAiFlow))` | `Emit(ContinueClicked)` | none |
| `AiComparisonChart` | `ComparisonChart` | `BottomWing` | `ComparisonChart(config = ComparisonChartConfig.Ai)` | `Emit(ContinueClicked)` | none |
| `AddToDock` | `AddToDock` | `None` | `AddToDock` | `Emit(ContinueClicked)` | none |
| `WidgetPrompt` | `AddWidget` | `LeftWing` | `WidgetPrompt` | `Emit(AddWidgetRequested)` | `Emit(WidgetPromptSkipped)` |
| `AddressBarPosition(showSplitOption)` | `AddressBar` | `BobbingDax` | `AddressBar(initialPosition = OmnibarType.SINGLE_TOP, showSplitOption)` | `Submit` | none |
| `InputScreen` | `InputType` | `LeftWing` | `InputScreen(initialWithAi = true)` | `Submit` | none |
| `InputScreenPreview(isSearchDefault)` | `InputType` | `None` | `InputScreenPreview(isSearchDefault, showModeToggle = !isCustomAiFlow, searchSuggestions = onboardingStore.getSearchOptions(), chatSuggestions = onboardingStore.getChatSuggestions())` | `Emit(ContinueClicked)` | none |
| `QuickSetup(...)` | `QuickSetup` | `BottomWing` | `QuickSetup(...)` copying all 5 dialog flags + `initialAddressBarPosition = OmnibarType.SINGLE_TOP`, `initialWithAi = true` | `Submit` | none |

`stepIndicator` stays `null` here — the VM fills it from plan position (`copy(stepIndicator = progress)`).

Check `OnboardingStore` import path by looking at how `BrandDesignUpdatePageViewModel.kt` imports it. If `getSearchOptions()`/`getChatSuggestions()` have different exact names, use whatever `setInputScreenPreviewDialog` (:191-206) actually calls.

- [ ] **Step 3: Report the extracted string-id table** (dialog → title/body/CTA ids) in your final message so the orchestrator can spot-check.

---

### Task 3: DialogTitleController

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogTitleController.kt`

**Interfaces:**
- Consumes: `TypeAnimationTextView` (`android-design-system/design-system/src/main/java/com/duckduckgo/common/ui/view/TypeAnimationTextView.kt` — `startTypingAnimation(htmlText, isCancellable, afterAnimation)`, `hasAnimationStarted()`, `finishAnimation()`, `cancelAnimation()`, `typingDelayInMs`, `delayAfterAnimationInMs`), `String.preventWidows()` (`common/common-utils/src/main/java/com/duckduckgo/common/utils/extensions/StringExtensions.kt:123`).
- Produces: `class DialogTitleController(visibleTitle: TypeAnimationTextView, sizingTwin: TextView)` with `fun set(text: String)`, `fun type(onFinished: () -> Unit)`, `fun snap()`, `fun finishTyping()`, `fun cancel()`.

This is the POC stand-in for the spec's `OnboardingDialogTitleView` compound widget: same API, but wraps the existing per-include view pair (`*Title` + `*TitleHidden`) instead of replacing include internals, so legacy layouts stay untouched.

- [ ] **Step 1: Read the legacy title machinery** — `BrandDesignUpdateWelcomePage.kt:2691-2698` (`startOnboardingTypingAnimation` ext: sets `delayAfterAnimationInMs = 20`, `typingDelayInMs = 20`, `startTypingAnimation(text, isCancellable = true, afterAnimation)`), and one usage of the sizing twin (e.g. :908 sets `hiddenTitleText`).

- [ ] **Step 2: Write DialogTitleController.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.widget.TextView
import com.duckduckgo.common.ui.view.TypeAnimationTextView
import com.duckduckgo.common.utils.extensions.preventWidows

/**
 * One owner for the copy-pasted title machinery: typing animation, invisible sizing twin,
 * preventWidows. POC stand-in for the OnboardingDialogTitleView compound widget.
 */
class DialogTitleController(
    private val visibleTitle: TypeAnimationTextView,
    private val sizingTwin: TextView,
) {
    private var text: String = ""

    /** Stages the text: sizing twin reserves final height; visible title stays empty until type()/snap(). */
    fun set(text: String) {
        this.text = text.preventWidows()
        sizingTwin.text = this.text
        visibleTitle.text = ""
    }

    fun type(onFinished: () -> Unit) {
        visibleTitle.delayAfterAnimationInMs = TYPING_DELAY_MS
        visibleTitle.typingDelayInMs = TYPING_DELAY_MS
        visibleTitle.startTypingAnimation(text, isCancellable = true, afterAnimation = onFinished)
    }

    /** Snap path: full text immediately, no animation. */
    fun snap() {
        visibleTitle.cancelAnimation()
        visibleTitle.text = text
    }

    /** Tap-to-skip: finish a running typing animation (fires its afterAnimation callback). */
    fun finishTyping() {
        if (visibleTitle.hasAnimationStarted()) {
            visibleTitle.finishAnimation()
        }
    }

    fun cancel() {
        visibleTitle.cancelAnimation()
    }

    private companion object {
        const val TYPING_DELAY_MS = 20L
    }
}
```

Verify `typingDelayInMs`/`delayAfterAnimationInMs` types (Long vs Int) in `TypeAnimationTextView.kt` and match. Verify `finishAnimation()` invokes the after-animation callback; if it doesn't, replicate what the legacy tap-to-skip does (`performClick()` on titles whose animation started, :2704-2714) inside `finishTyping()`.

---

### Task 4: Binder contracts + stateless binders

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentHandle.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WelcomeBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/ComparisonChartBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/AddToDockBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WidgetPromptBinder.kt`

**Interfaces:**
- Consumes: Task 1 types, Task 3 `DialogTitleController`. ViewBinding include types generated from `pre_onboarding_dax_dialog_cta_brand_design_update.xml`: `binding.welcomeContent` (`IncludeBrandDesignDialogWelcomeBinding`), `binding.comparisonChartContent` (`IncludeBrandDesignComparisonChartBinding`), `binding.addToDockContent` (`IncludeBrandDesignAddToDockBinding`), `binding.widgetPromptContent` (`IncludeBrandDesignWidgetPromptBinding`) — verify generated names by checking the include layout file names and the legacy fragment's usage (`binding.daxDialogCta.welcomeContent` etc.).
- Produces:

```kotlin
// ContentHandle.kt
class ContentHandle(
    val title: DialogTitleController?,
    val fadeTargets: List<View>,
    val entrance: (EntranceScope.() -> Unit)? = null,
    val result: (() -> NewUserOnboardingEvent)? = null,
    val unbind: () -> Unit = {},
)

interface EntranceScope {
    fun afterFade(animator: () -> Animator)
    fun afterTitle(animator: () -> Animator)
}
```

```kotlin
// DialogBinder.kt
class BindScope(
    val coroutineScope: CoroutineScope,               // cancelled by the engine at unbind
    val emit: (NewUserOnboardingEvent) -> Unit,        // content-initiated orchestrator events
    val execute: (ContentInteraction) -> Unit,         // content-initiated VM interactions
)

sealed interface ContentInteraction {
    data object EditAddressBarPosition : ContentInteraction
    data object EditSearchOptions : ContentInteraction
    data class SetDefaultBrowserToggled(val checked: Boolean) : ContentInteraction
    data class AddWidgetToggled(val checked: Boolean) : ContentInteraction
}

interface DialogBinder<C : ContentConfig> {
    val view: View                                     // include root; engine toggles visibility
    fun bind(content: C, scope: BindScope): ContentHandle
}

interface StatefulDialogBinder<C, S : Any> where C : ContentConfig, C : Stateful<S> {
    val view: View
    fun bind(content: C, state: MutableStateFlow<S>, scope: BindScope): ContentHandle
}
```

(`BindScope`/`ContentInteraction` extend the spec: the spec's handle only covers CTA-built events, but input-screen-preview submits from text input and quick setup opens bottom sheets — content-initiated. Documented POC finding.)

- [ ] **Step 1: Write ContentHandle.kt and DialogBinder.kt** exactly as in Interfaces (add imports: `android.animation.Animator`, `android.view.View`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.flow.MutableStateFlow`, plus package types).

- [ ] **Step 2: WelcomeBinder.kt.** Read legacy :888-971 (animated) + :1709-1769 (snap) for exact view usage. The include has `hiddenTitleText`, `titleText` (TypeAnimationTextView), `bodyText1`, `bodyText2`.

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

// imports...

class WelcomeBinder(private val binding: IncludeBrandDesignDialogWelcomeBinding) : DialogBinder<ContentConfig.Welcome> {
    override val view: View = binding.root

    override fun bind(content: ContentConfig.Welcome, scope: BindScope): ContentHandle {
        val context = binding.root.context
        binding.bodyText1.text = content.body1.resolve(context)
        binding.bodyText2.isVisible = content.body2 != null
        content.body2?.let { binding.bodyText2.text = it.resolve(context) }
        val title = DialogTitleController(binding.titleText, binding.hiddenTitleText)
        title.set(content.title.resolve(context))
        return ContentHandle(
            title = title,
            fadeTargets = listOfNotNull(binding.bodyText1, binding.bodyText2.takeIf { content.body2 != null }),
        )
    }
}
```

- [ ] **Step 3: ComparisonChartBinder.kt.** Read legacy `populateComparisonChart` (search the fragment for it), `playComparisonChartContentIntro` and `snapCheckIconsToFinalState` (:2737-2744) plus the row-inflation code (~:3024, `include_brand_design_comparison_chart_row`, row ids `rowIcon`/`rowText`/`rowCheck`). Port the populate logic into the binder. Views: `comparisonChartTitleHidden`, `comparisonChartTitle`, `comparisonTable`, header views, `comparisonRows`.
  - `bind` populates rows from `content.config`, returns handle with `fadeTargets = listOf(binding.comparisonTable)` and `entrance = { afterFade { checkIconStaggerAnimator() } }` where `checkIconStaggerAnimator()` is a ported version of the check-icon stagger built as a single `Animator` (an `AnimatorSet` of per-row icon-swap/scale animations; port from `playCheckIconAnimation` :2746-2789 — replace scheduled runnables with `startDelay`s so the engine owns one animator it can `end()`/`cancel()`).
  - Ensure `end()` on that AnimatorSet leaves icons in final state (`ic_check_green_24`, alpha/scale 1 — legacy snap :1816-1821). If a listener-based port can't guarantee that, set final state in the animator's `onAnimationEnd` regardless of cancellation flag.

- [ ] **Step 4: AddToDockBinder.kt.** Read legacy :1090-1170, :1833-1884, and `setupAddToDockVideo`/`releaseAddToDockVideo` (search fragment). Views: `addToDockTitleHidden`, `addToDockTitle`, `addToDockBody`, `addToDockMedia`, `addToDockPreviewVideo` (`AspectRatioTextureView`, plays `R.raw.onboarding_add_to_home_screen_tutorial`).
  - `bind` sets body text, sets up the video (port the setup code), returns `fadeTargets = listOf(binding.addToDockBody, binding.addToDockMedia)` and `unbind = { releaseVideo() }` (port release code into a private fun).

- [ ] **Step 5: WidgetPromptBinder.kt.** Read legacy :1172-1268, :1886-1963. Views: `widgetPromptTitleHidden`, `widgetPromptTitle`, `widgetPromptBody`, `widgetPromptMedia`. `bind` sets body, returns `fadeTargets = listOf(binding.widgetPromptBody, binding.widgetPromptMedia)`.

Binders never touch primary/secondary CTA views, never start animations themselves, never call the VM — they only declare via the handle.

---

### Task 5: Stateful binders + ContentBinder dispatcher

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/AddressBarBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenPreviewBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/QuickSetupBinder.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentBinder.kt`

**Interfaces:**
- Consumes: Tasks 1, 3, 4 types. Pickers: `BrandDesignAddressBarPositionPicker` (`app/src/main/java/com/duckduckgo/app/onboardingquicksetup/ui/BrandDesignAddressBarPositionPicker.kt` — `setLightMode(Boolean)`, `isSplitOptionVisible`, `setSelection(OmnibarType, animate)`, `setOnSelectionChangedListener`), `BrandDesignInputScreenPicker` (`.../BrandDesignInputScreenPicker.kt` — `setSelection(withAi, transition)`, `Transition.NONE/ANIMATE/CROSSFADE_ANIMATE`, `startWithAiAnimation(delayedStart)`, `cancelLottieAnimations`, `setOnSelectionChangedListener`), `QuickSetupSwitchRow` (`setCheckedSilently`, `setOnCheckedChangeListener`), `QuickSetupEditRow`. Legacy quick-setup selection display helpers :2367-2400 (`addressBarPositionIconRes/labelRes`, `searchOptionsIconRes/labelRes`).
- Produces:

```kotlin
class ContentBinder(
    binding: PreOnboardingDaxDialogCtaBrandDesignUpdateBinding, // verify generated binding class name
    private val contentValues: ContentValueStore,
    private val isLightMode: () -> Boolean,
) {
    fun bind(content: ContentConfig, scope: BindScope): BoundContent
}

data class BoundContent(
    val view: View,          // include root of the bound screen
    val handle: ContentHandle,
)
```

State pattern for every stateful binder — state-down-events-up:
```kotlin
picker.setOnSelectionChangedListener { position -> state.update { it.copy(position = position) } }  // events up
scope.coroutineScope.launch { state.collect { picker.setSelection(it.position, animate = true) } }   // state down
```

- [ ] **Step 1: AddressBarBinder.kt.** Views: `addressBarTitleHidden`, `addressBarTitle`, `addressBarPicker`. Read legacy `updateAddressBarPositionOptions` :228-234.

```kotlin
class AddressBarBinder(
    private val binding: IncludeBrandDesignAddressBarPositionBinding,
    private val isLightMode: () -> Boolean,
) : StatefulDialogBinder<ContentConfig.AddressBar, AddressBarContentState> {
    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.AddressBar,
        state: MutableStateFlow<AddressBarContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        addressBarPicker.setLightMode(isLightMode())
        addressBarPicker.isSplitOptionVisible = content.showSplitOption
        addressBarPicker.setSelection(state.value.position, animate = false)
        addressBarPicker.setOnSelectionChangedListener { position -> state.update { it.copy(position = position) } }
        scope.coroutineScope.launch { state.collect { addressBarPicker.setSelection(it.position, animate = true) } }
        val title = DialogTitleController(addressBarTitle, addressBarTitleHidden)
        title.set(content.title.resolve(root.context))
        ContentHandle(
            title = title,
            fadeTargets = listOf(addressBarPicker),
            result = { NewUserOnboardingEvent.AddressBarConfirmed(state.value.position) },
        )
    }
}
```

- [ ] **Step 2: InputScreenBinder.kt.** Views: `inputScreenTitleHidden`, `inputScreenTitle`, `inputScreenPicker`, `inputScreenDescription`. Same pattern; initial `setSelection(state.value.withAi, Transition.NONE)`; state-down uses `Transition.CROSSFADE_ANIMATE`; `result = { NewUserOnboardingEvent.InputModeConfirmed(state.value.withAi) }`; `entrance = { afterFade { /* animator wrapper that starts inputScreenPicker.startWithAiAnimation(delayedStart = true) */ } }` — if `startWithAiAnimation` isn't Animator-based, wrap in a trivial `ValueAnimator` whose `onAnimationEnd`/`doOnStart` triggers it, so the engine owns the hook (see legacy :1387-1424); `unbind = { binding.inputScreenPicker.cancelLottieAnimations() }` (verify method name in the picker class). Set description text from `content.description`.

- [ ] **Step 3: InputScreenPreviewBinder.kt.** Views: `inputScreenPreviewTitleHidden`, `inputScreenPreviewTitle`, `inputModeToggle` (`InputModeTabLayout`), `inputModeDemoCard`, `inputText`, `inputModeDemoActionIcon`, `suggestion1/2/3` (`OnboardingSelectionButton`). Read legacy :1451-1583, :2202-2313, `setInputScreenPreviewInputMode` :2853-2884, `playSuggestionButtonsAnimation` (search fragment).
  - Toggle visibility from `content.showModeToggle`; select initial tab from `state.value.isSearchSelected`; tab change → `state.update { it.copy(isSearchSelected = ...) }`; state-down collect rebinds suggestions/hint/inputType per mode (port from `setInputScreenPreviewInputMode`).
  - Suggestion click → `scope.emit(NewUserOnboardingEvent.InputDemoQuerySubmitted(suggestions[index].link, isChat = !state.value.isSearchSelected, fromSuggestion = true))`. Free-text submit (action icon + IME) → same event with `fromSuggestion = false`.
  - `entrance = { afterFade { suggestionButtonsAnimator() } }` — port `playSuggestionButtonsAnimation` as one returned AnimatorSet. Keyboard focus/show: do it in the entrance animator's end action, guarded by `screenHeightDp >= 600` like legacy (:1522-1535).
  - `fadeTargets = listOf(binding.inputModeToggle, binding.inputModeDemoCard)` (toggle only when shown).

- [ ] **Step 4: QuickSetupBinder.kt.** Views: `quickSetupTitleHidden`, `quickSetupTitle`, `quickSetupOptionsContainer`, `setDefaultBrowserItem`, `addWidgetItem`, `addressBarPositionItem`, `addressBarSearchOptionsItem`, dividers. Read legacy :973-1075, :2134-2200, listeners :2329-2355, selection display :2357-2400, row visibility :2317-2326.
  - Row visibility from config flags (`isVisible = !content.hideSetDefaultBrowserRow` etc., dividers too — mirror :2317-2326).
  - Switches: events up via `scope.execute(ContentInteraction.SetDefaultBrowserToggled(checked))` / `AddWidgetToggled(checked)`. State down: collect state → `setCheckedSilently(it.defaultBrowserChecked)` / `(it.widgetChecked)`.
  - Edit rows: `scope.execute(ContentInteraction.EditAddressBarPosition)` / `EditSearchOptions`. State down: update edit-row icon/label from `it.addressBarPosition` / `it.withAi` (port the four `*IconRes/*labelRes` helpers :2376-2400 as private funs).
  - `result = { NewUserOnboardingEvent.QuickSetupConfirmed(state.value.addressBarPosition, state.value.withAi) }`.
  - `fadeTargets = listOf(binding.quickSetupOptionsContainer)`.

- [ ] **Step 5: ContentBinder.kt**

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

// imports...

class ContentBinder(
    binding: PreOnboardingDaxDialogCtaBrandDesignUpdateBinding,
    private val contentValues: ContentValueStore,
    isLightMode: () -> Boolean,
) {
    private val welcome = WelcomeBinder(binding.welcomeContent)
    private val comparisonChart = ComparisonChartBinder(binding.comparisonChartContent)
    private val addToDock = AddToDockBinder(binding.addToDockContent)
    private val widgetPrompt = WidgetPromptBinder(binding.widgetPromptContent)
    private val addressBar = AddressBarBinder(binding.addressBarContent, isLightMode)
    private val inputScreen = InputScreenBinder(binding.inputScreenContent, isLightMode)
    private val inputScreenPreview = InputScreenPreviewBinder(binding.inputScreenPreviewContent)
    private val quickSetup = QuickSetupBinder(binding.reinstallerQuickSetupContent)

    fun bind(content: ContentConfig, scope: BindScope): BoundContent = when (content) {
        is ContentConfig.Welcome -> BoundContent(welcome.view, welcome.bind(content, scope))
        is ContentConfig.ComparisonChart -> BoundContent(comparisonChart.view, comparisonChart.bind(content, scope))
        is ContentConfig.AddToDock -> BoundContent(addToDock.view, addToDock.bind(content, scope))
        is ContentConfig.WidgetPrompt -> BoundContent(widgetPrompt.view, widgetPrompt.bind(content, scope))
        is ContentConfig.AddressBar -> BoundContent(addressBar.view, addressBar.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.InputScreen -> BoundContent(inputScreen.view, inputScreen.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.InputScreenPreview ->
            BoundContent(inputScreenPreview.view, inputScreenPreview.bind(content, contentValues.contentState(content), scope))
        is ContentConfig.QuickSetup -> BoundContent(quickSetup.view, quickSetup.bind(content, contentValues.contentState(content), scope))
    }
}
```

Adjust constructor params of individual binders to whatever Tasks 4/5 actually needed (e.g. `isLightMode` only where pickers need it). All eight include-binding property names must be verified against the generated binding (legacy fragment usages are the source of truth: `binding.daxDialogCta.welcomeContent` etc.).

---

### Task 6: Axis controllers

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/BackgroundController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/EmbellishmentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/CardAnchorController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/StepIndicatorController.kt`

**Interfaces:**
- Consumes: `OnboardingBackgroundAnimator` (`transitionTo(step, enterStartX, onStart, onEnd)`, `snapTo(step)`, `cancel()`), `Embellishment` enum, `BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(...)`, `OnboardingDecorationFitCorrector`, `OnboardingStepIndicatorView` (`setSteps(total, current)`, `setCurrentStep(step)`, `animateToNextStep()`), `StepProgress`. Legacy embellishment choreography: walking dax :2444-2487/:2562-2586, bobbing :2588-2651, bottom wing :2791-2820, left wing :2822-2851, fit :2489-2525, constants :3087-3116.
- Produces (exact signatures later tasks use):

```kotlin
class BackgroundController(private val animator: OnboardingBackgroundAnimator) {
    fun apply(previous: OnboardingBackgroundStep?, next: OnboardingBackgroundStep, animate: Boolean)
}

class EmbellishmentController(
    binding: ContentOnboardingWelcomePageUpdateBinding,
    private val layoutHelper: BrandDesignUpdateOnboardingLayoutHelper, /* or however it is instantiated in legacy — check fragment field */
    ...
) {
    /**
     * Transitions previous -> next embellishment. Runs the fit veto for next; reports the settled
     * decoration view (null if vetoed/None) so the card can anchor. Card anchor must be applied in
     * onSettled, which fires only after the exiting embellishment finished (engine rule).
     */
    fun transition(previous: Embellishment?, next: Embellishment, animate: Boolean, onSettled: (decorationView: View?) -> Unit)
    fun skipRunning()
    fun release()
}

class CardAnchorController(private val binding: ContentOnboardingWelcomePageUpdateBinding) {
    /** Anchors the dax card above the decoration (or parent-bottom when null) and sets arrow depth. */
    fun apply(decorationView: View?, isTablet: Boolean)
}

class StepIndicatorController(private val indicator: OnboardingStepIndicatorView) {
    fun apply(previous: StepProgress?, next: StepProgress?, animate: Boolean)
}
```

- [ ] **Step 1: BackgroundController.kt** — thin diff wrapper:

```kotlin
class BackgroundController(private val animator: OnboardingBackgroundAnimator) {
    fun apply(previous: OnboardingBackgroundStep?, next: OnboardingBackgroundStep, animate: Boolean) {
        if (previous == next) return
        if (animate) animator.transitionTo(next) else animator.snapTo(next)
    }
}
```
Match `transitionTo`'s real parameter list (`enterStartX`, `onStart`, `onEnd` have defaults? — check `OnboardingBackgroundAnimator.kt:136`; pass only what's needed).

- [ ] **Step 2: EmbellishmentController.kt** — the meat. One `when`-free structure: a private map `Embellishment -> Decoration` where `Decoration` bundles the Lottie view + enter/exit/snap lambdas + fit parameters, ported from legacy:
  - `WalkingDax` → `welcomeScreenWalkingDax`: enter = ported `playWalkingDaxAnimation` (:2562-2586) as engine-owned Animator (postDelayed 400ms → alpha 0→1 100ms + translationX -48dp→-22dp 600ms, then `playAnimation()`); exit = hide (`isInvisible = true`, cancel animation) — legacy hides it instantly on transitions; snap = `progress = 1f, alpha = 1f, translationX = -22dp` (:1709-1769); fit = `applyWalkingDaxLayout` equivalent (:2444-2487).
  - `BottomWing` → `bottomWingAnimation`: enter = port `playBottomWingAnimation` (:2791-2804: visible, alpha 0, `setMaxProgress(0.5f)`, delay 300ms, fade 150ms + `playAnimation()`); exit = port `dismissBottomWingAnimation` (:2806-2820: `setMinProgress(0.5f)`/`setMaxProgress(1f)`, play, `isInvisible` on end); snap = `setMinAndMaxProgress(0f, 0.5f)`, `progress = 0.5f`, visible alpha 1.
  - `LeftWing` → `leftWingAnimation`: same pattern (:2838-2851 / :2822-2836), exit sets `isGone`.
  - `BobbingDax` → `bobbingDaxAnimation`: enter = port `animateBobbingDaxIn` (:2588-2619, slide from screenWidth→0, then loop); exit = port `animateBobbingDaxOut` (:2621-2651, slide to -screenWidth, hide on end); snap = translationX 0, visible, `playAnimation()` looping (legacy snap :2029-2043).
  - `None` → no view.
  - `transition(previous, next, animate, onSettled)`:
    1. If `previous == next`: re-run fit for next, call `onSettled(fitVetoedView)`, return.
    2. Exit previous (if any, and its view is visible). When `animate`, wait for the exit's end listener; when not, snap-hide.
    3. Run fit veto for next via `layoutHelper.calculateDecorationHeight(...)` + size the view + `fitCorrector.track(...)` (port `applyDecorationLayout` :2489-2525; keep the `OnboardingDecorationFitCorrector` usage — construct it like legacy :546-552, with `onDecorationHidden` re-anchoring via a callback the fragment supplies). If vetoed → `onSettled(null)`.
    4. Enter next (animate) or snap. Call `onSettled(view)` once the decoration is placed (after exit completes — engine rule "hold the card anchor until the exiting embellishment finishes").
  - Track every animator it starts in a list; `skipRunning()` = `end()` all; `release()` = `cancel()` all + `fitCorrector.clear()`.
  - Constants: copy values from legacy :3087-3116 (`WING_START_DELAY = 300L`, `WING_FADE_IN_DURATION = 150L`, `WING_STOP_PROGRESS = 0.5f`, `WALKING_DAX_DELAY = 400L`, `WALKING_DAX_FADE_DURATION = 100L`, `WALKING_DAX_SLIDE_DURATION = 600L`, `WALKING_DAX_START_X_DP = 48`, `WALKING_DAX_FINAL_X_DP = 22`, and the per-decoration MAX/MIN heights — copy exact names/values from the fragment's companion).
  - Postponed delays must be animator `startDelay`s, not `postDelayed`, so `end()`/`cancel()` control them.
  - The fit-parameter values (`*_MAX_HEIGHT_DP` / `*_MIN_HEIGHT_DP`, `leftWingBottomOverlapPx()`) — copy from the fragment companion (:3087-3104) and the `leftWingBottomOverlapPx` helper (search fragment).
  - Check how legacy instantiates `BrandDesignUpdateOnboardingLayoutHelper` and `OnboardingDecorationFitCorrector` (fragment fields ~:546-552) and mirror construction.

- [ ] **Step 3: CardAnchorController.kt** — port the repeated anchor block (e.g. :1057-1067, :1288-1298):

```kotlin
class CardAnchorController(private val binding: ContentOnboardingWelcomePageUpdateBinding) {
    fun apply(decorationView: View?, isTablet: Boolean) {
        val card = binding.daxDialogCta.root
        card.updateLayoutParams<ConstraintLayout.LayoutParams> {
            if (decorationView != null && (isTablet || anchorsOnPhone(decorationView))) {
                bottomToTop = decorationView.id
                bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                verticalBias = if (isTablet) 0.5f else 1f
            } else {
                bottomToTop = ConstraintLayout.LayoutParams.UNSET
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                verticalBias = if (decorationView != null) 1f else 0f
            }
        }
        binding.daxDialogCta.cardView.setArrowDepthFraction(if (decorationView != null) 1f else 0f)
    }
}
```
IMPORTANT: legacy anchor rules differ per dialog (walking dax + bottom wing anchor on phones too — `bottomToTop = decoration.id`; bobbing dax and left wing anchor only on tablet). Read the five legacy anchor blocks (:1057-1067 quick setup, :1257-1267 widget, :1288-1298 address bar, :1430-1440 input screen, :1681-1691 comparison) and encode the per-decoration anchoring policy as a property of the decoration (e.g. `anchorsCardOnPhone: Boolean` on the controller's decoration map, exposed via `EmbellishmentController` in `onSettled` — adapt signatures if cleaner: `onSettled(decoration: SettledDecoration?)` with `data class SettledDecoration(val view: View, val anchorsCardOnPhone: Boolean)`). Verify `setArrowDepthFraction` exists on `DaxOnboardingBubbleBrandDesignUpdateCardView` (legacy uses it, e.g. via `onDecorationHidden` :550). Also port `setShowArrow(false)` for InputScreenPreview — expose `fun setArrowVisible(visible: Boolean)` on this controller (legacy :1492-1535 area, `cardView.setShowArrow(false)`); the engine calls it with `config.embellishment != None`... no — legacy shows the arrow for all dialogs except input-screen-preview; encode as: arrow hidden when content is InputScreenPreview — pass a `showArrow: Boolean` param to `apply(...)` and let the engine derive it from `config.content is ContentConfig.InputScreenPreview`. POC-acceptable content-type check at the fragment/engine boundary; note it as a config-model gap (candidate `DialogConfig.showCardArrow` field).

- [ ] **Step 4: StepIndicatorController.kt** — port `showStep`/`animateToStep` helpers (:2984-2995) and the fade-out (:1464-1478):

```kotlin
class StepIndicatorController(private val indicator: OnboardingStepIndicatorView) {
    private var fadeOut: Animator? = null

    fun apply(previous: StepProgress?, next: StepProgress?, animate: Boolean) {
        when {
            next == null && previous != null && animate -> { /* fade out then isVisible = false (port :1464-1478) */ }
            next == null -> indicator.isVisible = false
            !animate || previous == null -> { indicator.isVisible = true; indicator.setSteps(next.total, next.current) }
            else -> { indicator.isVisible = true; indicator.setSteps(next.total, next.current - 1); indicator.animateToNextStep() }
        }
    }

    fun skipRunning() { fadeOut?.end() }
}
```
Match real `OnboardingStepIndicatorView` API (`setSteps(totalSteps, currentStep)` — verify param order/names at `OnboardingStepIndicatorView.kt:132-157`).

---

### Task 7: DialogRenderEngine

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/DialogRenderEngine.kt`

**Interfaces:**
- Consumes: Tasks 1-6. `TransitionManager.beginDelayedTransition` + `ChangeBounds` (400ms, like legacy :998-1040), `ViewPropertyAnimator`/`ObjectAnimator` for fades.
- Produces:

```kotlin
class DialogRenderEngine(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val contentBinder: ContentBinder,
    private val background: BackgroundController,
    private val embellishments: EmbellishmentController,
    private val cardAnchor: CardAnchorController,
    private val stepIndicator: StepIndicatorController,
    private val isTablet: Boolean,
    private val emit: (NewUserOnboardingEvent) -> Unit,
    private val execute: (ContentInteraction) -> Unit,
) {
    fun render(config: DialogConfig, animate: Boolean)
    fun skipRunningAnimations()   // tap-to-skip / reduced motion
    fun release()                 // fragment onDestroyView
}
```

- [ ] **Step 1: Write the engine.** Core algorithm:

```kotlin
fun render(config: DialogConfig, animate: Boolean) {
    if (config == previous && bound != null) return          // re-emission, nothing changed

    val emptyStage = previous == null
    val animateNow = animate || emptyStage                    // empty stage always animates its entrance

    skipRunningAnimations()                                    // never two renders racing
    unbindCurrent()                                            // handle.unbind(), cancel bindScope, hide old include

    // axes see only their own previous -> next values
    background.apply(previous?.background, config.background, animateNow)
    stepIndicator.apply(previous?.stepIndicator, config.stepIndicator, animateNow)

    val scope = BindScope(coroutineScope = createBindScope(), emit = emit, execute = execute)
    val newBound = contentBinder.bind(config.content, scope)
    bound = newBound
    val handle = newBound.handle
    newBound.view.isVisible = true
    bindCtas(config, handle)

    val entrance = EntranceCollector().also { c -> handle.entrance?.invoke(c) }

    embellishments.transition(previous?.embellishment, config.embellishment, animateNow) { settled ->
        cardAnchor.apply(settled?.view, isTablet, showArrow = config.content !is ContentConfig.InputScreenPreview)
    }

    if (animateNow) {
        handle.fadeTargets.forEach { it.alpha = 0f }
        ctaViews(config).forEach { it.alpha = 0f }
        morphCard {                                            // ChangeBounds 400ms on binding.root; onEnd:
            handle.title?.type {                               //   title types; on finish:
                runAndTrack(entrance.afterTitleAnimators)
                fadeIn(handle.fadeTargets + ctaViews(config)) {   // uniform fade; on end:
                    runAndTrack(entrance.afterFadeAnimators)
                }
            }
        }
    } else {
        handle.title?.snap()
        handle.fadeTargets.forEach { it.alpha = 1f }
        ctaViews(config).forEach { it.alpha = 1f }
        // engine owns every animator: end() applies end values even if never started
        (entrance.afterTitleAnimators + entrance.afterFadeAnimators).forEach { it().apply { end() } }
    }

    previous = config
}
```

Details:
- `previous: DialogConfig?`, `bound: BoundContent?`, `bindScopeJob: Job?` fields; `runningAnimators: MutableList<Animator>`.
- `createBindScope()`: `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` — POC exception to the no-hardcoded-dispatchers lint rule: engine is view-layer, not injectable; add `@SuppressLint`/lint suppression if the custom lint rule fires, with a comment. (If the lint rule blocks compilation, accept a `dispatchers: DispatcherProvider` constructor param passed from the fragment instead.)
- `unbindCurrent()`: `bound?.handle?.unbind?.invoke()`, cancel bind scope job, `bound?.view?.isVisible = false`, `bound = null`.
- `bindCtas(config, handle)`: resolve texts into `binding.daxDialogCta.primaryCta` / `.secondaryCta`; secondary `isGone` when null. Listener:
```kotlin
private fun ctaListener(action: CtaAction, handle: ContentHandle): () -> Unit = {
    when (action) {
        is CtaAction.Emit -> emit(action.event)
        CtaAction.Submit -> handle.result?.invoke()?.let(emit)
    }
}
```
- `morphCard(onEnd)`: `TransitionManager.beginDelayedTransition(binding.root, ChangeBounds().setDuration(400L).also { it.addListener(onEnd-on-transition-end) })` then `binding.root.requestLayout()` — mirror legacy usage (:998-1040). Snap path skips it entirely.
- `fadeIn(views, onEnd)`: one `AnimatorSet` of `ObjectAnimator.ofFloat(view, View.ALPHA, 1f)` (duration: reuse legacy fade duration — find the value used by `welcomeFadeInAnimatorSet`, search fragment for its construction), tracked in `runningAnimators`.
- `EntranceCollector : EntranceScope` collects `afterFade`/`afterTitle` factories into lists.
- `runAndTrack(factories)`: create each animator, add to `runningAnimators`, `start()`.
- `skipRunningAnimations()`: `bound?.handle?.title?.finishTyping()`; `runningAnimators.toList().forEach { it.end() }`; `runningAnimators.clear()`; `embellishments.skipRunning()`; `stepIndicator.skipRunning()`. (Title finish triggers the queued fade via its afterAnimation callback; the fade animator is then `end()`able on the next skip call — acceptable POC behaviour: two taps to fully skip is NOT acceptable, so after `finishTyping()` also immediately `end()` any animators created by the title callback: call `runningAnimators` drain twice or have `fadeIn` check a `skipping` flag and jump to end state. Implement the flag.)
- `release()`: `skipRunningAnimations()` variant with `cancel()`, `unbindCurrent()`, `embellishments.release()`, `background` left alone (fragment owns the animator instance).
- Engine never branches on `(previous, next)` pairs and contains zero screen-specific logic except the InputScreenPreview arrow flag noted in Task 6.

---

### Task 8: Shown-pixel mapper + slim ViewModel

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingDialogShownPixels.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/BrandDesignUpdatePageViewModel.kt` (ONLY: replace the pixel-firing `when` body of `fireDialogShownPixel` (:208-220) with a call to the shared mapper — keep the `Presented` emission line untouched)

**Interfaces:**
- Consumes: Tasks 1-2. Legacy VM (:85-563) for ports. `stepIndicatorProgress()` ext (`NewUserOnboardingActivityStep.kt:53-59`), `forPlan` ext (`LinearOnboardingOrchestrator.kt:188`), `NewUserOnboardingPlanProvider.ROOT_PLAN_ID`.
- Produces:

```kotlin
@ContributesBinding-free plain class:
class OnboardingDialogShownPixels @Inject constructor(private val pixel: Pixel) {
    fun fireFor(dialog: NewUserOnboardingActivityDialog)   // exhaustive when, no else
}

@ContributesViewModel(FragmentScope::class)
class ConfigDrivenOnboardingPageViewModel @Inject constructor(...) : ViewModel() {
    data class ViewState(
        val stepId: LinearOnboardingStepId? = null,
        val config: DialogConfig? = null,
        val animateEntry: Boolean = true,
        val hasPlayedIntroAnimation: Boolean = false,
    )
    val viewState: StateFlow<ViewState>
    val commands: Flow<Command>   // reuse pattern; define own Command sealed interface (subset ported from legacy)
    val contentValues: ContentValueStore   // exposed for the fragment's ContentBinder

    fun onEvent(event: NewUserOnboardingEvent)          // engine emit -> orchestrator, blind forward
    fun onContentInteraction(interaction: ContentInteraction)
    fun onDialogRendered(stepId: LinearOnboardingStepId) // flips animate-once bookkeeping
    fun onResume()
    fun onIntroAnimationFinished()
    fun notificationPermissionFlowFinished(granted: Boolean?)   // port names/signatures from legacy where they exist
    fun onDefaultBrowserSet() / onDefaultBrowserNotSet()        // port from legacy
    fun onAddressBarBottomSheetResult(type: OmnibarType)        // bottom-sheet result -> store write
    fun onSearchOptionsBottomSheetResult(withAi: Boolean)
    fun checkAddWidgetPromptResult()
}
```

- [ ] **Step 1: OnboardingDialogShownPixels.kt.** Exhaustive `when(dialog)` (compile error until a new dialog is mapped — that's the point). Mapping (from legacy `fireDialogShownPixel` :208-220, translated from `PreOnboardingDialogType` to dialogs):

```kotlin
class OnboardingDialogShownPixels @Inject constructor(private val pixel: Pixel) {
    fun fireFor(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            NewUserOnboardingActivityDialog.SyncRestore -> pixel.fire(PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InitialReinstallUser -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.Initial -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.ComparisonChart -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.AiComparisonChart -> pixel.fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
            is NewUserOnboardingActivityDialog.AddressBarPosition -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InputScreen -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
            is NewUserOnboardingActivityDialog.InputScreenPreview,
            is NewUserOnboardingActivityDialog.QuickSetup,
            NewUserOnboardingActivityDialog.AddToDock,
            NewUserOnboardingActivityDialog.WidgetPrompt,
            is NewUserOnboardingActivityDialog.IntroAnimation,
            NewUserOnboardingActivityDialog.NotificationPermission,
            NewUserOnboardingActivityDialog.DefaultBrowserPrompt,
            NewUserOnboardingActivityDialog.AddWidget,
            -> Unit
        }
    }
}
```
Copy exact `Pixel`/`Unique` imports and pixel enum imports from `BrandDesignUpdatePageViewModel.kt`.

- [ ] **Step 2: Legacy edit — `BrandDesignUpdatePageViewModel.fireDialogShownPixel`.** The legacy method fires keyed on `PreOnboardingDialogType` but `applyDialog` (:497-559) has the actual dialog. Change: inject `OnboardingDialogShownPixels` into the legacy VM; inside `applyDialog`, call `shownPixels.fireFor(dialog)` once at the top (before the `when`); strip the pixel `when` body from `fireDialogShownPixel` so it ONLY emits `Presented` (rename it `notifyDialogPresented()` if trivial, otherwise keep the name). VERIFY parity: every legacy pixel that fired before still fires exactly once per dialog application, at effectively the same moment. If `applyDialog` can run without `setCurrentDialog` being reached for some dialog (command-only branches like `IntroAnimation`/`NotificationPermission`/`DefaultBrowserPrompt`/`AddWidget`), those were never firing shown pixels before (they're `Unit` in the mapper), so top-of-applyDialog placement is parity-safe. State this check in your report.

- [ ] **Step 3: ConfigDrivenOnboardingPageViewModel.kt.** Port from legacy VM (:85-563), slimmed:
  - Injected deps (mirror legacy imports/types): `orchestrator: LinearOnboardingOrchestrator`, `newUserOnboardingPlanBootstrapper` (check exact type/usage in legacy init), `dialogConfigResolver: DialogConfigResolver`, `shownPixels: OnboardingDialogShownPixels`, `dispatchers: DispatcherProvider`, `defaultBrowserDetector`, `widgetCapabilities`, `defaultRoleBrowserDialog`, plus whatever the ported command handlers need (copy from legacy constructor :85-100; take only what's used).
  - `val contentValues = ContentValueStore()` (plain property, not injected).
  - Custom-AI flag: find how legacy sets `ViewState.isCustomAiOnboardingFlow` (search legacy VM for `isCustomAiOnboardingFlow =`) and port the same source into a private `suspend fun isCustomAiFlow(): Boolean`.
  - `observeOrchestratorState()` (port :458-493): collect `orchestrator.state.forPlan(NewUserOnboardingPlanProvider.ROOT_PLAN_ID)`. On `InProgress` with `NewUserOnboardingActivityStep`:
    ```kotlin
    val dialog = step.resolveDialog()
    val config = dialogConfigResolver.resolve(dialog, isCustomAiFlow())
    if (config != null) {
        shownPixels.fireFor(dialog)
        _viewState.update {
            it.copy(
                stepId = step.id,
                config = config.copy(stepIndicator = state.stepIndicatorProgress()),
                animateEntry = step.id != lastPresentedStepId,
            )
        }
        lastPresentedStepId = step.id
        emit(NewUserOnboardingEvent.Presented)
    } else {
        handleCommandOnlyDialog(dialog)   // port applyDialog branches for IntroAnimation /
                                           // NotificationPermission (2s delay) / DefaultBrowserPrompt / AddWidget
    }
    ```
    `animateEntry` policy (spec): first render of a step animates; re-emission of the same step snaps. `lastPresentedStepId: LinearOnboardingStepId?` is a plain VM field — VM survives rotation, so rotation re-collection sees the same step id and snaps. Fresh activity entry recreates the VM → `previous == null` in the engine → empty-stage entrance animates. Keep BrowserActivity host handling (`Command.HandOffToBrowserActivity`) and `Completed`/`Skipped` routing exactly as legacy (:470-493).
  - `onEvent(event)` = `viewModelScope.launch { orchestrator.onEvent(event) }` — blind forward.
  - `onContentInteraction`: port legacy quick-setup handlers — `EditAddressBarPosition` → `Command.ShowQuickSetupAddressBarPositionBottomSheet(current position from contentValues, showSplitOption from current config)`; `EditSearchOptions` → `Command.ShowQuickSetupSearchOptionsBottomSheet(...)`; `SetDefaultBrowserToggled(true)` → port `onQuickSetupSetAsDefaultClicked` (:335-347); `(false)` → port `onQuickSetupSetAsDefaultUnchecked`; `AddWidgetToggled` → port `onQuickSetupAddHomescreenWidgetClicked`/`onQuickSetupRemoveHomescreenWidgetClicked`.
  - Bottom-sheet results + external syncs write into the store (spec's external-change path). Helper:
    ```kotlin
    private fun currentQuickSetup(): ContentConfig.QuickSetup? = _viewState.value.config?.content as? ContentConfig.QuickSetup
    fun onAddressBarBottomSheetResult(type: OmnibarType) {
        currentQuickSetup()?.let { contentValues.contentState(it).update { s -> s.copy(addressBarPosition = type) } }
        (viewStateConfigContentAs<ContentConfig.AddressBar>())?.let { contentValues.contentState(it).update { s -> s.copy(position = type) } }
    }
    ```
    (Only the quick-setup path matters for bottom sheets; the AddressBar line is illustrative — drop it if it complicates.)
  - `onResume()`: port `checkQuickSetupSwitchesState` (:355-369) but instead of a Command, write through the store:
    ```kotlin
    fun onResume() {
        val quickSetup = currentQuickSetup() ?: return
        viewModelScope.launch(dispatchers.io()) {
            val isDefault = defaultBrowserDetector.isDefaultBrowser()
            val hasWidget = widgetCapabilities.hasInstalledWidgets
            contentValues.contentState(quickSetup).update { it.copy(defaultBrowserChecked = isDefault, widgetChecked = hasWidget) }
        }
        checkAddWidgetPromptResult()   // port :390-398 verbatim (fires AddWidgetFinished)
    }
    ```
  - Commands sealed interface: port ONLY what the new fragment needs: `PlayIntroAnimation(withDuckAi)`, `RequestNotificationPermissions`, `ShowDefaultBrowserDialog(intent)`, `OpenDefaultBrowserSystemSettings`, `ShowQuickSetupDefaultBrowserDialog(intent)`, `LaunchAddWidgetPrompt`, `ShowRemoveWidgetBottomSheet`, `ShowQuickSetupAddressBarPositionBottomSheet(initialSelection, showSplitOption)`, `ShowQuickSetupSearchOptionsBottomSheet(initialWithAi)`, `Finish`, `FinishAndSubmitSearchQuery(query)`, `FinishAndSubmitChatPrompt(prompt)`, `OnboardingSkipped`, `HandOffToBrowserActivity`. Copy legacy command-channel plumbing (Channel + receiveAsFlow — see legacy VM).
  - Intro handling: port `seedHasPlayedIntroAnimation` (:446-456) + `onIntroAnimationFinished` semantics so `hasPlayedIntroAnimation` behaves like legacy. Port the `IntroAnimation` applyDialog branch (`Command.PlayIntroAnimation`).
  - Port `notificationPermissionFlowFinished`, `onDefaultBrowserSet/NotSet`, `notificationRuntimePermissionRequested` (Presented emission) — whatever the ported command branches require. Skip everything animation-bookkeeping (`hasAnimatedCurrentDialog`, `onDialogAnimationStarted`, `SkipDialogAnimation` command stays though — tap-to-skip: port `onDialogTapped`/`onBackgroundTapped` semantics as a `SkipDialogAnimation`-like command OR simpler: fragment calls engine.skipRunningAnimations() directly on tap; choose the direct-call route and note it).

---

### Task 9: Config-driven fragment + intro choreographer

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingIntroChoreographer.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenWelcomePage.kt`

**Interfaces:**
- Consumes: everything above. Legacy fragment for ports: intro (:237-505), command handling (:637-696), launchers (search for `registerForActivityResult`), bottom-sheet launch/result (:2402-2442), onResume (:830-834).
- Produces: `class ConfigDrivenWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update)` annotated `@InjectWith(FragmentScope::class)` — constructed by Task 10's builder.

- [ ] **Step 1: OnboardingIntroChoreographer.kt.** Port, verbatim where possible, the one-time intro/outro from legacy (:237-505 + constants :3044-3067): `buildIntroAnimatorSet`, `buildBackgroundIntroAnimatorSet`, `buildOutroAnimatorSet`, `playIntroAnimation(withDuckAi, onFinished)`, `prepareDuckAiIntroAnimation`, `resolveOnboardingTextPrimary`, `snapToIntroEndState()`, `playOutro(onEnd)`. Constructor: `(binding: ContentOnboardingWelcomePageUpdateBinding, backgroundAnimator: OnboardingBackgroundAnimator, appTheme/whatever resolveOnboardingTextPrimary needs)`. Spec keeps intro/outro as-is — this is a mechanical extraction for the new fragment only; legacy keeps its own copy. Skip `introInProgress`-flow coupling; expose plain callbacks. Also expose `fun releaseIntroAnimators()` for onDestroyView.

- [ ] **Step 2: ConfigDrivenWelcomePage.kt.** Structure:

```kotlin
@InjectWith(FragmentScope::class)
class ConfigDrivenWelcomePage : OnboardingPageFragment(R.layout.content_onboarding_welcome_page_update) {

    @Inject lateinit var viewModelFactory: FragmentViewModelFactory
    @Inject lateinit var appTheme: AppTheme                      // if pickers/intro need it (mirror legacy @Inject fields it actually uses)

    private val binding: ContentOnboardingWelcomePageUpdateBinding by viewBinding()
    private val viewModel by lazy { ViewModelProvider(this, viewModelFactory)[ConfigDrivenOnboardingPageViewModel::class.java] }

    private var engine: DialogRenderEngine? = null
    private var intro: OnboardingIntroChoreographer? = null
    private var backgroundAnimator: OnboardingBackgroundAnimator? = null
    private var pendingFirstRender: (() -> Unit)? = null
```
  - `onViewCreated`: construct `OnboardingBackgroundAnimator` exactly like legacy does (search legacy fragment for `OnboardingBackgroundAnimator(` to copy constructor args — `backgroundPrimary`/`backgroundSecondary` views etc.), construct controllers + `ContentBinder(binding.daxDialogCta, viewModel.contentValues, isLightMode = { appTheme.isLightModeEnabled() })` + engine (`emit = viewModel::onEvent`, `execute = viewModel::onContentInteraction`, `isTablet` = same check legacy uses — search for how legacy decides tablet, e.g. a boolean resource or `resources.getBoolean(R.bool....)`; copy it). Wire tap-to-skip: `binding.root.setOnClickListener { engine?.skipRunningAnimations() }` and card container likewise (legacy :543-544).
  - Collect `viewModel.viewState` (with `flowWithLifecycle(STARTED)`): 
    ```kotlin
    when {
        state.config == null -> Unit                                   // intro not finished yet
        !state.hasPlayedIntroAnimation -> Unit                         // wait for intro command flow
        introRunning -> pendingFirstRender = { renderConfig(state) }   // render after outro
        else -> renderConfig(state)
    }
    ```
    `renderConfig(state)`: first render after intro plays outro first (`intro.playOutro { engine.render(config, animate = true) }`); otherwise `engine.render(state.config, state.animateEntry)`. Track `hasRenderedOnce` locally; keep it simple and note residual complexity in the report — this intro/first-dialog handshake is exactly the "empty stage" policy boundary the POC must exercise.
  - Collect `viewModel.commands`: port handlers from legacy :637-696 for the commands Task 8 kept — notification permission request (port launcher), default-browser dialogs/launchers, add-widget prompt launch + `ShowRemoveWidgetBottomSheet`, the two quick-setup bottom sheets + their `setFragmentResultListener` wiring (:2419-2442) routing results to `viewModel.onAddressBarBottomSheetResult` / `onSearchOptionsBottomSheetResult` / `viewModel.checkAddWidgetPromptResult()`, `PlayIntroAnimation` → `intro.playIntroAnimation(withDuckAi) { viewModel.onIntroAnimationFinished() }`, `Finish*`/`OnboardingSkipped`/`HandOffToBrowserActivity` → copy what legacy does (it calls `onboardingPageCallback`? — check `OnboardingPageFragment` for the finish contract and mirror legacy exactly).
  - `onResume()`: `viewModel.onResume()`.
  - `onDestroyView()`: `engine?.release()`, `intro?.releaseIntroAnimators()`, `backgroundAnimator?.cancel()`, null fields.

---

### Task 10: Rollout seam

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboardingbranddesignupdate/OnboardingBrandDesignUpdateToggles.kt` (add one toggle)
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingPageBuilder.kt` (new blueprint + builder method)
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingPageManager.kt` (new blueprint branch)
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/OnboardingViewModel.kt` (toggle branch)

**Interfaces:**
- Consumes: Task 9 `ConfigDrivenWelcomePage`.
- Produces: flag-off byte-identical behaviour; flag-on swaps only the welcome-page fragment.

- [ ] **Step 1: Toggle.** In `OnboardingBrandDesignUpdateToggles` add:

```kotlin
/**
 * Selects the config-driven parallel renderer for brand-design onboarding dialogs (POC).
 */
@Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
fun configDrivenDialogs(): Toggle
```

- [ ] **Step 2: Blueprint + builder.** In `OnboardingPageBuilder.kt`: add `data object ConfigDrivenWelcomePageBlueprint : OnboardingPageBlueprint()`; add `fun buildConfigDrivenWelcomePage(): ConfigDrivenWelcomePage` to the interface and `override fun buildConfigDrivenWelcomePage() = ConfigDrivenWelcomePage()` to `OnboardingFragmentPageBuilder`.

- [ ] **Step 3: Manager.** In `OnboardingPageManager.kt`: add `fun buildConfigDrivenPageBlueprints()` to the interface; impl mirrors `buildBrandDesignUpdatePageBlueprints()` (:including the `shouldShowDefaultBrowserPage()` conditional — same second page) but pushes `ConfigDrivenWelcomePageBlueprint` first; add `is ConfigDrivenWelcomePageBlueprint -> onboardingPageBuilder.buildConfigDrivenWelcomePage()` to `buildPage`'s `when`.

- [ ] **Step 4: OnboardingViewModel.** In `initializePages()` (:59-68):

```kotlin
val isBrandDesignUpdateEnabled = withContext(dispatchers.io()) {
    onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()
}
val isConfigDrivenDialogsEnabled = isBrandDesignUpdateEnabled &&
    withContext(dispatchers.io()) { onboardingBrandDesignUpdateToggles.configDrivenDialogs().isEnabled() }
when {
    isConfigDrivenDialogsEnabled -> pageLayoutManager.buildConfigDrivenPageBlueprints()
    isBrandDesignUpdateEnabled -> pageLayoutManager.buildBrandDesignUpdatePageBlueprints()
    else -> pageLayoutManager.buildPageBlueprints()
}
```

---

### Task 11: Compile, format, fix

**Files:** whatever the compiler flags.

- [ ] **Step 1:** Run `./gradlew :app:compileInternalDebugKotlin` (long first run is expected). Fix every error in NEW files first; only touch legacy/seam files for errors this plan's edits introduced.
- [ ] **Step 2:** Run `./gradlew spotlessApply`, then re-run `./gradlew :app:compileInternalDebugKotlin`.
- [ ] **Step 3:** Report: exact commands run + tail of output proving success (or the precise remaining errors if blocked).

---

## Execution notes (orchestrator)

- Wave plan: T1 → (T2 ∥ T3) → (T4 ∥ T6) → T5 → T7 → T8 → T9 → T10 → T11. Compile checkpoints: after T5 (`:app:compileInternalDebugKotlin` — expect resolver/binder type issues surface here) and T11 (final).
- Commits by orchestrator only, explicit file staging, no co-author lines.
- Review between tasks: check produced signatures against this plan's Interfaces blocks; drift here is the top failure mode.

## Known POC simplifications (report as gaps at the end)

1. `OnboardingDialogTitleView` implemented as controller over existing include views, not a compound widget — in-place include refactor deferred.
2. `BindScope.emit`/`execute` + `ContentInteraction` extend the spec — spec's handle covers only CTA-built events.
3. Card arrow visibility branches on `ContentConfig.InputScreenPreview` type — candidate `DialogConfig` field.
4. Secondary CTA GONE vs legacy INVISIBLE space-reservation nuance ignored.
5. Welcome-dialog outro handshake handled in fragment, not engine (one-time animation boundary).
6. Legacy behavioural quirks intentionally not reproduced: per-dialog fade timing differences, `SkipDialogAnimation` command path (direct engine call instead), `translationZ` tweaks, `addBottomShadow`.
