# Config-driven onboarding dialogs — remaining screens implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the six remaining onboarding screens to the config-driven renderer, and close the flag-on parity
gaps (shown pixels, command-only dialogs, content interactions) so the new arm behaves like the legacy arm.

**Architecture:** Step 2 landed the render engine, its axis controllers and the binder contracts. Nothing in
`engine/` changes. Each screen adds one `ContentConfig` variant, one binder, one `DialogConfigResolver.resolve()`
branch and one `ContentControllerImpl.bind()` branch. Binders declare views and animation factories; the engine
runs them. Stateful screens keep working state in the view-model-owned `ContentValueStore` and reach the view
model only through `BindScope.execute(ContentInteraction)`.

**Tech Stack:** Kotlin, Android views + view binding, Anvil/Dagger, coroutines/Flow, JUnit 4 + Mockito-Kotlin +
Turbine. Design spec: `temp-docs/plans/2026-08-03-config-driven-dialogs-remaining-screens-design.md`.

## Global Constraints

- Package for new binders: `com.duckduckgo.app.onboarding.ui.page.configdriven.binders`.
- Every new file starts with the Apache 2.0 header used by its neighbours, `Copyright (c) 2026 DuckDuckGo`.
- Max line length 150 characters. `./gradlew spotlessApply` before every commit.
- Unit tests in `:app` run with `./gradlew :app:testInternalDebugUnitTest`. Batch test runs per task, not per
  TDD micro-step.
- `:app` unit tests cannot inflate XML — no binder is unit tested. Tests cover the resolver, the view model and
  the shown-pixel mapper only.
- `DialogConfig` and every `ContentConfig` stay value-comparable: no views, no lambdas. `Stateful.initialState()`
  is pure, derived from config values only.
- Binders touch only their own include's view tree. They never touch `cardView`, `primaryCta`, `secondaryCta`,
  `stepIndicator` or any other shared card view, and never call the view model directly — only
  `BindScope.execute`.
- Binders never start the animations they declare. `ContentHandle.afterFade` returns a factory; the engine owns
  `start()` / `end()` / `cancel()`. Anything interactive revealed by `afterFade` must be created with
  `isClickable = false` and restored from the animator's end listener.
- Feature flag `OnboardingBrandDesignUpdateToggles#configDrivenDialogs` keeps its current default (off). Do not
  change it.
- Do not touch the intro animation. `ConfigDrivenWelcomePage.settleIntroViews()` and the view model's
  `IntroAnimation -> emit(IntroAnimationFinished)` branch stay exactly as they are — implemented on another branch.
- Never add `Co-Authored-By` lines to commits.
- Stage only the files the task touched. Never `git add -A`.

## Reference points

Legacy source of truth for behaviour, `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/`:

- `BrandDesignUpdateWelcomePage.kt` — `configureDaxCta` (animated path, from :877) and
  `showDialogWithoutAnimation` (snap path, from :1696). Per-dialog line ranges are named in each task.
- `BrandDesignUpdatePageViewModel.kt` — command routing, quick-setup handlers, `fireDialogShownPixel` (:208).

Landed contracts to bind against, `…/ui/page/configdriven/`:

- `DialogBinder` / `StatefulDialogBinder` / `BindScope` / `ContentInteraction` — `DialogBinder.kt`
- `ContentHandle` (`title`, `fadeTargets`, `afterFade`, `onContentReady`, `result`, `unbind`) — `ContentHandle.kt`
- `OnboardingDialogTitleView` (`setTitle`, the engine calls `typeTitle` / `snapTitle`) —
  `…/ui/view/OnboardingDialogTitleView.kt`
- Reference binders: `binders/ComparisonChartBinder.kt` (stateless, with an `afterFade` animator),
  `binders/AddressBarBinder.kt` (stateful).

The POC branch `feature/lpaczos/linear-onboarding-dialog-spec` has an earlier version of every binder
(`git show feature/lpaczos/linear-onboarding-dialog-spec:app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/<Name>.kt`).
Useful for the legacy line references in its KDoc, but it targets older contracts (`ContentHandle.entrance`,
a `DialogTitleController` taking two views, a `BindScope.emit` channel) and some stale string resources. Read
it for orientation, never copy it wholesale.

## File structure

Created:

| File | Responsibility |
|---|---|
| `…/configdriven/binders/WelcomeBinder.kt` | Welcome / reinstall / sync-restore include |
| `…/configdriven/binders/AddToDockBinder.kt` | Add-to-dock include, incl. demo video lifecycle |
| `…/configdriven/binders/WidgetPromptBinder.kt` | Widget-prompt include |
| `…/configdriven/binders/InputScreenBinder.kt` | Search/AI picker include |
| `…/configdriven/binders/InputScreenPreviewBinder.kt` | Input demo include |
| `…/configdriven/binders/QuickSetupBinder.kt` | Quick-setup rows include |
| `…/configdriven/OnboardingDialogShownPixels.kt` | Legacy once-ever "dialog shown" pixels |
| `app/src/test/…/configdriven/OnboardingDialogShownPixelsTest.kt` | Its mapping |

Modified:

| File | Change |
|---|---|
| `…/configdriven/ContentConfig.kt` | 6 new variants, 3 new state classes |
| `…/configdriven/DialogBinder.kt` | `ContentInteraction` members |
| `…/configdriven/DialogConfigResolver.kt` | 8 new branches, takes `OnboardingStore` |
| `…/configdriven/engine/ContentController.kt` | 6 new `bind` branches, 6 binder fields |
| `…/configdriven/ConfigDrivenOnboardingPageViewModel.kt` | Interactions, commands, quick setup, shown pixels |
| `…/configdriven/ConfigDrivenWelcomePage.kt` | Bottom sheets, new commands, quick-setup launcher |
| `app/src/test/…/configdriven/DialogConfigResolverTest.kt` | A case per new dialog |
| `app/src/test/…/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt` | Interactions, pixels, commands |

---

### Task 1: Welcome screen (Initial, InitialReinstallUser, SyncRestore)

Legacy: `BrandDesignUpdateWelcomePage.kt:888-971` (animated), `:1709-1769` (snap). Copy decisions at `:911-926`:
sync restore uses its own title/body/CTAs and no second line; the custom-AI flow replaces `body1` and drops the
second line; both of those run `body1` through `.html()`, the plain copy does not.

`body1AsHtml` is explicit config data rather than inferred from `body2 == null`, so the HTML decision does not
silently ride on "has a second line".

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WelcomeBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`

**Interfaces:**
- Consumes: `DialogConfig`, `CtaConfig`, `CtaAction`, `TextConfig`, `Embellishment`, `CardArrowConfig`,
  `ContentHandle`, `DialogBinder`, `BindScope` (all already landed).
- Produces: `ContentConfig.Welcome(title: TextConfig, body1: TextConfig, body1AsHtml: Boolean, body2: TextConfig?)`;
  `WelcomeBinder(binding: IncludeBrandDesignDialogWelcomeBinding)`.

- [ ] **Step 1: Write the failing resolver tests**

Add to `DialogConfigResolverTest`, and delete `NewUserOnboardingActivityDialog.Initial` from the existing
`resolves no config for a dialog that has no config-driven screen yet` test (leave `NotificationPermission` and
`AddToDock` in it):

```kotlin
    @Test
    fun `resolves the initial welcome dialog with a continue cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.Welcome, config.background)
        assertEquals(Embellishment.WalkingDax, config.embellishment)
        assertEquals(CardArrowConfig.AtStart, config.cardArrow)
        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
                body1AsHtml = false,
                body2 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the initial welcome dialog with single line html copy in the custom ai flow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = true)!!

        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBodyCustomAi),
                body1AsHtml = true,
                body2 = null,
            ),
            config.content,
        )
    }

    @Test
    fun `resolves the reinstall welcome dialog with a skip secondary cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InitialReinstallUser, isCustomAiFlow = false)!!

        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
                body1AsHtml = false,
                body2 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingDaxDialog1SecondaryButton),
                CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
            config.secondaryCta,
        )
    }

    @Test
    fun `resolves the sync restore dialog with its own copy and restore cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.SyncRestore, isCustomAiFlow = true)!!

        assertEquals(OnboardingBackgroundStep.Welcome, config.background)
        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle),
                body1 = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignBody1),
                body1AsHtml = true,
                body2 = null,
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.syncRestoreDialogPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.RestoreRequested),
            ),
            config.primaryCta,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.syncRestoreDialogSecondaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
            config.secondaryCta,
        )
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfigResolverTest"`
Expected: compile failure — `ContentConfig.Welcome` is unresolved.

- [ ] **Step 3: Add the content config**

In `ContentConfig.kt`, inside `sealed interface ContentConfig`, above `ComparisonChart`:

```kotlin
    data class Welcome(
        override val title: TextConfig,
        val body1: TextConfig,
        /** The plain welcome copy keeps its raw line breaks; the sync-restore and custom-AI variants carry markup. */
        val body1AsHtml: Boolean,
        val body2: TextConfig?,
    ) : ContentConfig
```

- [ ] **Step 4: Add the resolver branches**

In `DialogConfigResolver.kt`, remove `NewUserOnboardingActivityDialog.SyncRestore`,
`NewUserOnboardingActivityDialog.InitialReinstallUser` and `NewUserOnboardingActivityDialog.Initial` from the
null branch and add:

```kotlin
        NewUserOnboardingActivityDialog.Initial -> welcome(
            content = welcomeContent(isCustomAiFlow),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
        )

        NewUserOnboardingActivityDialog.InitialReinstallUser -> welcome(
            content = welcomeContent(isCustomAiFlow),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            secondaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1SecondaryButton),
                action = CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
        )

        NewUserOnboardingActivityDialog.SyncRestore -> welcome(
            content = ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle),
                body1 = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignBody1),
                body1AsHtml = true,
                body2 = null,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.syncRestoreDialogPrimaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.RestoreRequested),
            ),
            secondaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.syncRestoreDialogSecondaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
        )
```

and, next to the existing private `comparisonChart` helper:

```kotlin
    private fun welcome(
        content: ContentConfig.Welcome,
        primaryCta: CtaConfig,
        secondaryCta: CtaConfig? = null,
    ) = DialogConfig(
        background = OnboardingBackgroundStep.Welcome,
        embellishment = Embellishment.WalkingDax,
        cardArrow = CardArrowConfig.AtStart,
        content = content,
        primaryCta = primaryCta,
        secondaryCta = secondaryCta,
    )

    private fun welcomeContent(isCustomAiFlow: Boolean) = ContentConfig.Welcome(
        title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
        body1 = TextConfig.Resource(
            if (isCustomAiFlow) R.string.preOnboardingWelcomeDialogBodyCustomAi else R.string.preOnboardingWelcomeDialogBody1,
        ),
        body1AsHtml = isCustomAiFlow,
        body2 = if (isCustomAiFlow) null else TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
    )
```

- [ ] **Step 5: Add the binder**

Create `WelcomeBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignDialogWelcomeBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows

class WelcomeBinder(
    private val binding: IncludeBrandDesignDialogWelcomeBinding,
) : DialogBinder<ContentConfig.Welcome> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.Welcome, scope: BindScope): ContentHandle = with(binding) {
        val context = root.context

        val body1 = content.body1.resolve(context).preventWidows()
        bodyText1.text = if (content.body1AsHtml) body1.html(context) else body1
        // Set explicitly: a previous render of the single-line copy leaves this hidden.
        bodyText2.isVisible = content.body2 != null
        content.body2?.let { bodyText2.text = it.resolve(context).preventWidows() }

        titleText.setTitle(content.title.resolve(context))

        ContentHandle(
            title = titleText,
            fadeTargets = listOfNotNull(bodyText1, bodyText2.takeIf { content.body2 != null }),
        )
    }
}
```

- [ ] **Step 6: Route the config to the binder**

In `ContentController.kt`, add the field next to the existing two:

```kotlin
    private val welcome = WelcomeBinder(binding.welcomeContent)
```

and the branch in `bind`, before the `ComparisonChart` branch:

```kotlin
            is ContentConfig.Welcome -> {
                boundView = welcome.view
                welcome.bind(content, scope)
            }
```

Add the import `com.duckduckgo.app.onboarding.ui.page.configdriven.binders.WelcomeBinder`.

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WelcomeBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt
git commit -m "add the welcome binder and its three config mappings"
```

---

### Task 2: Add-to-dock screen

Legacy: `BrandDesignUpdateWelcomePage.kt:1090-1170` (animated), `:1833-1884` (snap), video lifecycle
`setupAddToDockVideo` `:705-729`, `playAddToDockVideo` `:731-744`, `releaseAddToDockVideo` `:746-749`.

The demo video starts from the `TextureView`'s surface-available callback, exactly as legacy does, rather than
from `ContentHandle.onContentReady`: the surface is what gates playback, and the listener already fires at the
right moment on both the animated and snapped paths.

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/AddToDockBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`

**Interfaces:**
- Consumes: everything Task 1 consumed.
- Produces: `ContentConfig.AddToDock(title: TextConfig, body: TextConfig)`;
  `AddToDockBinder(binding: IncludeBrandDesignAddToDockBinding)`.

- [ ] **Step 1: Write the failing resolver test**

Add to `DialogConfigResolverTest`, and remove `NewUserOnboardingActivityDialog.AddToDock` from the
`resolves no config …` test:

```kotlin
    @Test
    fun `resolves the add to dock dialog with no decoration and no arrow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.AddToDock, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.AddToDock, config.background)
        assertEquals(Embellishment.None, config.embellishment)
        assertEquals(CardArrowConfig.Hidden, config.cardArrow)
        assertEquals(
            ContentConfig.AddToDock(
                title = TextConfig.Resource(R.string.preOnboardingDockStepTitle),
                body = TextConfig.Resource(R.string.preOnboardingAddToDockBody),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingAddToDockPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfigResolverTest"`
Expected: compile failure — `ContentConfig.AddToDock` is unresolved.

- [ ] **Step 3: Add the content config**

In `ContentConfig.kt`:

```kotlin
    data class AddToDock(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig
```

- [ ] **Step 4: Add the resolver branch**

Remove `NewUserOnboardingActivityDialog.AddToDock` from the null branch and add:

```kotlin
        NewUserOnboardingActivityDialog.AddToDock -> DialogConfig(
            background = OnboardingBackgroundStep.AddToDock,
            embellishment = Embellishment.None,
            cardArrow = CardArrowConfig.Hidden,
            content = ContentConfig.AddToDock(
                title = TextConfig.Resource(R.string.preOnboardingDockStepTitle),
                body = TextConfig.Resource(R.string.preOnboardingAddToDockBody),
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingAddToDockPrimaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
        )
```

- [ ] **Step 5: Add the binder**

Create `AddToDockBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.View
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignAddToDockBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows

class AddToDockBinder(
    private val binding: IncludeBrandDesignAddToDockBinding,
) : DialogBinder<ContentConfig.AddToDock> {

    override val view: View = binding.root

    private var videoPlayer: MediaPlayer? = null

    override fun bind(content: ContentConfig.AddToDock, scope: BindScope): ContentHandle = with(binding) {
        val context = root.context

        addToDockBody.text = content.body.resolve(context).preventWidows()
        setUpVideo()

        addToDockTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = addToDockTitle,
            fadeTargets = listOf(addToDockBody, addToDockMedia),
            unbind = { releaseVideo() },
        )
    }

    private fun setUpVideo() = with(binding) {
        addToDockPreviewVideo.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT)
        addToDockPreviewVideo.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) {
                playVideo(surface)
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseVideo()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    private fun playVideo(surfaceTexture: SurfaceTexture) {
        releaseVideo()
        videoPlayer = MediaPlayer().apply {
            setSurface(Surface(surfaceTexture))
            binding.root.context.resources.openRawResourceFd(R.raw.onboarding_add_to_home_screen_tutorial).use { setDataSource(it) }
            isLooping = true
            setVolume(0f, 0f)
            setOnVideoSizeChangedListener { _, width, height -> binding.addToDockPreviewVideo.setVideoSize(width, height) }
            setOnPreparedListener { it.start() }
            prepareAsync()
        }
    }

    private fun releaseVideo() {
        videoPlayer?.release()
        videoPlayer = null
    }

    private companion object {
        // Seeds AspectRatioTextureView's first measurement before MediaPlayer reports the real size: at
        // wrap_content a zero height never produces a surface, so playback would never start.
        const val VIDEO_WIDTH = 1080
        const val VIDEO_HEIGHT = 944
    }
}
```

- [ ] **Step 6: Route the config to the binder**

In `ContentController.kt`:

```kotlin
    private val addToDock = AddToDockBinder(binding.addToDockContent)
```

```kotlin
            is ContentConfig.AddToDock -> {
                boundView = addToDock.view
                addToDock.bind(content, scope)
            }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/AddToDockBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt
git commit -m "add the add to dock binder and its config mapping"
```

---

### Task 3: Widget prompt screen

Legacy: `BrandDesignUpdateWelcomePage.kt:1172-1268` (animated), `:1886-1963` (snap). Copy at `:1181`, `:1201`,
`:1203`, `:1206` — note the title, body and secondary CTA come from the `experimentHomeScreenWidget…` strings,
not from `preOnboardingWidgetPrompt…` ones. The arrow slides to the end and the left wing is the decoration
(`:1209-1211`).

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WidgetPromptBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`

**Interfaces:**
- Produces: `ContentConfig.WidgetPrompt(title: TextConfig, body: TextConfig)`;
  `WidgetPromptBinder(binding: IncludeBrandDesignWidgetPromptBinding)`.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
    @Test
    fun `resolves the widget prompt dialog with add and skip ctas`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.WidgetPrompt, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.AddWidget, config.background)
        assertEquals(Embellishment.LeftWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        assertEquals(
            ContentConfig.WidgetPrompt(
                title = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogTitle),
                body = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogSubTitle),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingWidgetPromptPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.AddWidgetRequested),
            ),
            config.primaryCta,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogGhostButton),
                CtaAction.Emit(NewUserOnboardingEvent.WidgetPromptSkipped),
            ),
            config.secondaryCta,
        )
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfigResolverTest"`
Expected: compile failure — `ContentConfig.WidgetPrompt` is unresolved.

- [ ] **Step 3: Add the content config**

```kotlin
    data class WidgetPrompt(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig
```

- [ ] **Step 4: Add the resolver branch**

Remove `NewUserOnboardingActivityDialog.WidgetPrompt` from the null branch and add:

```kotlin
        NewUserOnboardingActivityDialog.WidgetPrompt -> DialogConfig(
            background = OnboardingBackgroundStep.AddWidget,
            embellishment = Embellishment.LeftWing,
            cardArrow = CardArrowConfig.AtEnd,
            content = ContentConfig.WidgetPrompt(
                title = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogTitle),
                body = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogSubTitle),
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingWidgetPromptPrimaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.AddWidgetRequested),
            ),
            secondaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogGhostButton),
                action = CtaAction.Emit(NewUserOnboardingEvent.WidgetPromptSkipped),
            ),
        )
```

- [ ] **Step 5: Add the binder**

Create `WidgetPromptBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignWidgetPromptBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder
import com.duckduckgo.common.utils.extensions.preventWidows

class WidgetPromptBinder(
    private val binding: IncludeBrandDesignWidgetPromptBinding,
) : DialogBinder<ContentConfig.WidgetPrompt> {

    override val view: View = binding.root

    override fun bind(content: ContentConfig.WidgetPrompt, scope: BindScope): ContentHandle = with(binding) {
        val context = root.context

        widgetPromptBody.text = content.body.resolve(context).preventWidows()
        widgetPromptTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = widgetPromptTitle,
            fadeTargets = listOf(widgetPromptBody, widgetPromptMedia),
        )
    }
}
```

- [ ] **Step 6: Route the config to the binder**

```kotlin
    private val widgetPrompt = WidgetPromptBinder(binding.widgetPromptContent)
```

```kotlin
            is ContentConfig.WidgetPrompt -> {
                boundView = widgetPrompt.view
                widgetPrompt.bind(content, scope)
            }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/WidgetPromptBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt
git commit -m "add the widget prompt binder and its config mapping"
```

---

### Task 4: Input screen (search vs AI picker)

Legacy: `BrandDesignUpdateWelcomePage.kt:1347-1450` (animated), `:2100-2200` region of
`showDialogWithoutAnimation` (snap), toggle application `updateAiChatToggleState` `:2950-2961`, description
`:1442-1444` (`preventWidows()` then `.html()`).

The picker's Lottie "with AI" flourish is not `Animator`-based, so it is wrapped in a zero-duration
`ValueAnimator` whose only job is to start it from `onAnimationStart` — same trick as
`ComparisonChartBinder.avdStartTrigger`. The engine calls `start()` then, on a snapped render, `end()`, so the
flourish also runs on the snap path, matching legacy's snap-path `Transition.ANIMATE`.

The state collector drops its replayed first value: `collect` replays immediately and
`Transition.CROSSFADE_ANIMATE` starts Lottie unconditionally, which would defeat the entrance-gated trigger.

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`

**Interfaces:**
- Produces: `ContentConfig.InputScreen(title: TextConfig, description: TextConfig, initialWithAi: Boolean)`
  implementing `Stateful<InputScreenContentState>`; `data class InputScreenContentState(val withAi: Boolean)`;
  `InputScreenBinder(binding: IncludeBrandDesignInputScreenBinding, isLightMode: () -> Boolean)`.

- [ ] **Step 1: Write the failing resolver test**

```kotlin
    @Test
    fun `resolves the input screen with a submitting cta and ai preselected`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreen, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.InputType, config.background)
        assertEquals(Embellishment.LeftWing, config.embellishment)
        assertEquals(CardArrowConfig.AtStart, config.cardArrow)
        val content = config.content as ContentConfig.InputScreen
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputScreenTitleUpdated), content.title)
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputScreenDescription), content.description)
        assertEquals(InputScreenContentState(withAi = true), content.initialState())
        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingInputScreenButton), CtaAction.Submit),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfigResolverTest"`
Expected: compile failure — `ContentConfig.InputScreen` is unresolved.

- [ ] **Step 3: Add the content config and state**

In `ContentConfig.kt`, in the stateful group next to `AddressBar`:

```kotlin
    data class InputScreen(
        override val title: TextConfig,
        val description: TextConfig,
        val initialWithAi: Boolean,
    ) : ContentConfig, Stateful<InputScreenContentState> {
        override fun initialState() = InputScreenContentState(withAi = initialWithAi)
    }
```

and at file level next to `AddressBarContentState`:

```kotlin
data class InputScreenContentState(val withAi: Boolean)
```

- [ ] **Step 4: Add the resolver branch**

Remove `NewUserOnboardingActivityDialog.InputScreen` from the null branch and add:

```kotlin
        NewUserOnboardingActivityDialog.InputScreen -> DialogConfig(
            background = OnboardingBackgroundStep.InputType,
            embellishment = Embellishment.LeftWing,
            cardArrow = CardArrowConfig.AtStart,
            content = ContentConfig.InputScreen(
                title = TextConfig.Resource(R.string.preOnboardingInputScreenTitleUpdated),
                description = TextConfig.Resource(R.string.preOnboardingInputScreenDescription),
                initialWithAi = true,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingInputScreenButton),
                action = CtaAction.Submit,
            ),
        )
```

- [ ] **Step 5: Add the binder**

Create `InputScreenBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.View
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignInputScreenBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.app.onboardingquicksetup.ui.BrandDesignInputScreenPicker.Transition
import com.duckduckgo.common.utils.extensions.html
import com.duckduckgo.common.utils.extensions.preventWidows
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class InputScreenBinder(
    private val binding: IncludeBrandDesignInputScreenBinding,
    private val isLightMode: () -> Boolean,
) : StatefulDialogBinder<ContentConfig.InputScreen, InputScreenContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.InputScreen,
        state: MutableStateFlow<InputScreenContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        inputScreenPicker.setLightMode(isLightMode())
        inputScreenPicker.setSelection(state.value.withAi, Transition.NONE)
        inputScreenPicker.setOnSelectionChangedListener { withAi -> state.update { it.copy(withAi = withAi) } }
        scope.coroutineScope.launch {
            // The replayed first value would crossfade and start the Lottie loop at bind time, which the
            // entrance trigger below owns.
            state.drop(1).collect { inputScreenPicker.setSelection(it.withAi, Transition.CROSSFADE_ANIMATE) }
        }

        inputScreenDescription.text = content.description.resolve(context).preventWidows().html(context)

        inputScreenTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = inputScreenTitle,
            fadeTargets = listOf(inputScreenPicker, inputScreenDescription),
            afterFade = { withAiFlourishTrigger() },
            result = { NewUserOnboardingEvent.InputModeConfirmed(state.value.withAi) },
            unbind = { inputScreenPicker.cancelLottieAnimations() },
        )
    }

    /**
     * The picker's flourish runs on its own coroutine, not on an animator timeline, so there is nothing to hand
     * the engine directly. This zero-duration animator exists only so the engine still owns starting it.
     */
    private fun withAiFlourishTrigger(): Animator =
        ValueAnimator.ofInt(0, 1).apply {
            duration = 0L
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        binding.inputScreenPicker.startWithAiAnimation(delayedStart = true)
                    }
                },
            )
        }
}
```

- [ ] **Step 6: Route the config to the binder**

```kotlin
    private val inputScreen = InputScreenBinder(binding.inputScreenContent, isLightMode)
```

`isLightMode` is already a constructor parameter of `ContentControllerImpl`. Add the branch:

```kotlin
            is ContentConfig.InputScreen -> {
                boundView = inputScreen.view
                inputScreen.bind(content, contentValues.contentState(stepId, content), scope)
            }
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 8: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt
git commit -m "add the input screen binder and its config mapping"
```

---

### Task 5: Input screen preview

Legacy: `BrandDesignUpdateWelcomePage.kt:1451-1583` (animated), `:2202-2313` (snap), mode application
`setInputScreenPreviewInputMode` `:2832-2891`, suggestion stagger `playSuggestionButtonsAnimation` `:2893-2927`.
Suggestions come from `OnboardingStore.getSearchOptions()` / `getChatSuggestions()` (legacy view model `:199-200`),
so `DialogConfigResolver` takes `OnboardingStore`. The mode toggle is hidden in the custom-AI flow (`:2243`).
This screen has no CTA: it advances when the user submits.

`ContentInteraction` gains `SubmitInput` in this task, and the view model forwards it to the orchestrator as
`NewUserOnboardingEvent.InputDemoQuerySubmitted`.

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogBinder.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenPreviewBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt`

**Interfaces:**
- Consumes: `ContentInteraction` (landed, empty).
- Produces: `ContentConfig.InputScreenPreview(title, isSearchDefault, showModeToggle, searchSuggestions, chatSuggestions)`
  implementing `Stateful<InputScreenPreviewContentState>`;
  `data class InputScreenPreviewContentState(val isSearchSelected: Boolean)`;
  `ContentInteraction.SubmitInput(query: String, isChat: Boolean, fromSuggestion: Boolean)`;
  `InputScreenPreviewBinder(binding: IncludeBrandDesignInputScreenPreviewBinding)`;
  `DialogConfigResolver(onboardingStore: OnboardingStore)`.

- [ ] **Step 1: Write the failing tests**

`DialogConfigResolverTest` — the resolver now takes a collaborator, so replace its field and add cases. Every
existing test keeps working unchanged:

```kotlin
    private val searchOptions = listOf(DaxDialogIntroOption(optionText = "search", iconRes = 0, link = "how to fix a bike"))
    private val chatSuggestions = listOf(DaxDialogIntroOption(optionText = "chat", iconRes = 0, link = "explain quantum computing"))
    private val onboardingStore: OnboardingStore = mock {
        on { getSearchOptions() } doReturn searchOptions
        on { getChatSuggestions() } doReturn chatSuggestions
    }

    private val testee = DialogConfigResolver(onboardingStore)
```

```kotlin
    @Test
    fun `resolves the input screen preview with store suggestions and no cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = true), isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.InputType, config.background)
        assertEquals(Embellishment.None, config.embellishment)
        assertEquals(CardArrowConfig.Hidden, config.cardArrow)
        assertEquals(
            ContentConfig.InputScreenPreview(
                title = TextConfig.Resource(R.string.preOnboardingInputModeDemoTitle),
                isSearchDefault = true,
                showModeToggle = true,
                searchSuggestions = searchOptions,
                chatSuggestions = chatSuggestions,
            ),
            config.content,
        )
        assertNull(config.primaryCta)
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the input screen preview without a mode toggle in the custom ai flow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false), isCustomAiFlow = true)!!

        val content = config.content as ContentConfig.InputScreenPreview
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputModeDemoTitleCustomAi), content.title)
        assertFalse(content.showModeToggle)
        assertEquals(InputScreenPreviewContentState(isSearchSelected = false), content.initialState())
    }
```

Add imports `com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption`,
`com.duckduckgo.app.onboarding.store.OnboardingStore`, `org.junit.Assert.assertFalse`,
`org.mockito.kotlin.doReturn`, `org.mockito.kotlin.mock`.

`ConfigDrivenOnboardingPageViewModelTest` — the view model's resolver is constructed in `createViewModel`, so
change `dialogConfigResolver = DialogConfigResolver()` to `dialogConfigResolver = DialogConfigResolver(mockOnboardingStore)`
with a new field `private val mockOnboardingStore: OnboardingStore = mock()`, and add:

```kotlin
    @Test
    fun `forwards a submitted input demo query to the orchestrator`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onContentInteraction(ContentInteraction.SubmitInput(query = "cats", isChat = true, fromSuggestion = true))
        advanceUntilIdle()

        assertEquals(
            listOf(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = true)),
            recordedEvents,
        )
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: compile failure — `ContentConfig.InputScreenPreview` and `ContentInteraction.SubmitInput` are unresolved.

- [ ] **Step 3: Add the content config, state and interaction**

In `ContentConfig.kt`:

```kotlin
    data class InputScreenPreview(
        override val title: TextConfig,
        val isSearchDefault: Boolean,
        val showModeToggle: Boolean,
        val searchSuggestions: List<DaxDialogIntroOption>,
        val chatSuggestions: List<DaxDialogIntroOption>,
    ) : ContentConfig, Stateful<InputScreenPreviewContentState> {
        override fun initialState() = InputScreenPreviewContentState(isSearchSelected = isSearchDefault)
    }
```

```kotlin
data class InputScreenPreviewContentState(val isSearchSelected: Boolean)
```

with the import `com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption`.

In `DialogBinder.kt`, replace the empty `ContentInteraction` body:

```kotlin
/** Interactions a bound screen raises outside the shared CTA buttons interactions. */
sealed interface ContentInteraction {

    /** The input demo's typed query, IME action or suggestion tap. */
    data class SubmitInput(
        val query: String,
        val isChat: Boolean,
        val fromSuggestion: Boolean,
    ) : ContentInteraction
}
```

- [ ] **Step 4: Add the resolver branch**

Give the resolver its collaborator:

```kotlin
class DialogConfigResolver @Inject constructor(
    private val onboardingStore: OnboardingStore,
) {
```

Remove `is NewUserOnboardingActivityDialog.InputScreenPreview` from the null branch and add:

```kotlin
        is NewUserOnboardingActivityDialog.InputScreenPreview -> DialogConfig(
            background = OnboardingBackgroundStep.InputType,
            embellishment = Embellishment.None,
            cardArrow = CardArrowConfig.Hidden,
            content = ContentConfig.InputScreenPreview(
                title = TextConfig.Resource(
                    if (isCustomAiFlow) R.string.preOnboardingInputModeDemoTitleCustomAi else R.string.preOnboardingInputModeDemoTitle,
                ),
                isSearchDefault = dialog.isSearchDefault,
                showModeToggle = !isCustomAiFlow,
                searchSuggestions = onboardingStore.getSearchOptions(),
                chatSuggestions = onboardingStore.getChatSuggestions(),
            ),
        )
```

with the import `com.duckduckgo.app.onboarding.store.OnboardingStore`.

- [ ] **Step 5: Handle the interaction in the view model**

Replace the no-op:

```kotlin
    fun onContentInteraction(interaction: ContentInteraction) {
        when (interaction) {
            is ContentInteraction.SubmitInput -> emit(
                NewUserOnboardingEvent.InputDemoQuerySubmitted(
                    query = interaction.query,
                    isChat = interaction.isChat,
                    fromSuggestion = interaction.fromSuggestion,
                ),
            )
        }
    }
```

- [ ] **Step 6: Add the binder**

Create `InputScreenPreviewBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignInputScreenPreviewBinding
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.InputScreenPreviewContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import com.duckduckgo.common.ui.view.addBottomShadow
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The mode tabs are the only write path into the state; everything mode-dependent renders from the collector,
 * so a tab tap and a replayed value take the same path.
 *
 * Legacy wraps each mode switch in a `beginDelayedTransition` on the shared card view so the field resizes
 * smoothly between the one-line search input and the three-line chat input. A binder has no handle on the card,
 * so the field snaps to its new size instead.
 */
class InputScreenPreviewBinder(
    private val binding: IncludeBrandDesignInputScreenPreviewBinding,
) : StatefulDialogBinder<ContentConfig.InputScreenPreview, InputScreenPreviewContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.InputScreenPreview,
        state: MutableStateFlow<InputScreenPreviewContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        if (Build.VERSION.SDK_INT >= 28) {
            inputModeDemoCard.addBottomShadow()
        }

        inputText.isFocusable = true
        inputText.isFocusableInTouchMode = true

        inputModeToggle.isVisible = content.showModeToggle
        if (content.showModeToggle && !state.value.isSearchSelected) {
            inputModeToggle.getTabAt(CHAT_TAB_INDEX)?.select()
        }
        applyMode(content, state.value.isSearchSelected, scope)

        inputModeToggle.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    state.update { it.copy(isSearchSelected = tab.position == SEARCH_TAB_INDEX) }
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            },
        )
        scope.coroutineScope.launch {
            state.collect {
                applyMode(content, it.isSearchSelected, scope)
                val target = if (it.isSearchSelected) SEARCH_TAB_INDEX else CHAT_TAB_INDEX
                if (inputModeToggle.selectedTabPosition != target) {
                    inputModeToggle.getTabAt(target)?.select()
                }
            }
        }

        inputScreenPreviewTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = inputScreenPreviewTitle,
            fadeTargets = listOfNotNull(inputModeToggle.takeIf { content.showModeToggle }, inputModeDemoCard),
            afterFade = { suggestionButtonsAnimator() },
            onContentReady = { showKeyboardIfRoom() },
        )
    }

    private fun applyMode(
        content: ContentConfig.InputScreenPreview,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        bindSuggestionButtons(
            suggestions = if (isSearchSelected) content.searchSuggestions else content.chatSuggestions,
            isSearchSelected = isSearchSelected,
            scope = scope,
        )

        val submitTypedQuery = {
            val query = inputText.text?.toString().orEmpty().trim()
            if (query.isNotEmpty()) {
                scope.execute(ContentInteraction.SubmitInput(query, isChat = !isSearchSelected, fromSuggestion = false))
            }
        }
        inputModeDemoActionIcon.setOnClickListener { submitTypedQuery() }
        inputText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitTypedQuery()
                true
            } else {
                false
            }
        }

        if (isSearchSelected) {
            inputText.minLines = 1
            inputText.maxLines = 1
            inputText.inputType = InputType.TYPE_CLASS_TEXT
            inputText.imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputText.setHint(R.string.preOnboardingInputModeDemoSearchHint)
            inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_find_search_24)
        } else {
            inputText.minLines = CHAT_INPUT_LINES
            inputText.maxLines = CHAT_INPUT_LINES
            inputText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            inputText.imeOptions = EditorInfo.IME_ACTION_UNSPECIFIED
            inputText.setHint(R.string.preOnboardingInputModeDemoChatHint)
            inputModeDemoActionIcon.setImageResource(CommonR.drawable.ic_arrow_right_24)
        }

        // A mode switch can land while the field is already focused, so the IME has to be told to pick up the
        // new action and Enter behaviour.
        if (inputText.hasFocus()) {
            ContextCompat.getSystemService(root.context, InputMethodManager::class.java)?.restartInput(inputText)
        }
    }

    private fun bindSuggestionButtons(
        suggestions: List<DaxDialogIntroOption>,
        isSearchSelected: Boolean,
        scope: BindScope,
    ) = with(binding) {
        suggestionButtons().forEachIndexed { index, button ->
            suggestions[index].setOptionView(button)
            button.setOnClickListener {
                scope.execute(
                    ContentInteraction.SubmitInput(
                        query = suggestions[index].link,
                        isChat = !isSearchSelected,
                        fromSuggestion = true,
                    ),
                )
            }
        }
    }

    private fun suggestionButtons() = listOf(binding.suggestion1, binding.suggestion2, binding.suggestion3)

    /**
     * Staggers the suggestion buttons in. They are only tappable once the stagger completes: the card stops
     * intercepting touches as the entrance starts, so a button revealed here would otherwise be tappable while
     * still invisible.
     */
    private fun suggestionButtonsAnimator(): Animator {
        val buttons = suggestionButtons()
        buttons.forEach {
            it.alpha = 0f
            it.isClickable = false
            it.isVisible = true
        }

        val fades = buttons.mapIndexed { index, button ->
            ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f).apply {
                duration = SUGGESTION_FADE_DURATION_MS
                startDelay = index * SUGGESTION_FADE_DURATION_MS
            }
        }

        return AnimatorSet().apply {
            playTogether(fades)
            startDelay = SUGGESTIONS_START_DELAY_MS
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        buttons.forEach {
                            it.alpha = 1f
                            it.isClickable = true
                            it.isVisible = true
                        }
                    }
                },
            )
        }
    }

    private fun showKeyboardIfRoom() = with(binding) {
        if (root.resources.configuration.screenHeightDp < MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP) return
        root.post {
            if (!root.isAttachedToWindow) return@post
            inputText.requestFocus()
            ViewCompat.getWindowInsetsController(inputText)?.show(WindowInsetsCompat.Type.ime())
        }
    }

    private companion object {
        const val SEARCH_TAB_INDEX = 0
        const val CHAT_TAB_INDEX = 1
        const val CHAT_INPUT_LINES = 3
        const val SUGGESTION_FADE_DURATION_MS = 500L
        const val SUGGESTIONS_START_DELAY_MS = 500L
        const val MIN_SCREEN_HEIGHT_FOR_KEYBOARD_DP = 600
    }
}
```

- [ ] **Step 7: Route the config to the binder**

```kotlin
    private val inputScreenPreview = InputScreenPreviewBinder(binding.inputScreenPreviewContent)
```

```kotlin
            is ContentConfig.InputScreenPreview -> {
                boundView = inputScreenPreview.view
                inputScreenPreview.bind(content, contentValues.contentState(stepId, content), scope)
            }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 9: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogBinder.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/InputScreenPreviewBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt
git commit -m "add the input screen preview binder and its config mapping"
```

---

### Task 6: Quick setup screen

Legacy: `BrandDesignUpdateWelcomePage.kt:977-1080` (animated), `:2100` region (snap), row visibility
`updateQuickSetupRowsVisibility` `:2307-2317`, listeners `setQuickSetupListeners` `:2319-2345`, selection display
`bindQuickSetupSelection` `:2357-2364` and the icon/label helpers `:2366-2389`, bottom sheets `:2392-2432`,
system settings fallback `openDefaultBrowserSystemSettings` `:2937-2948`. Legacy view model:
`onQuickSetupSetAsDefaultClicked` `:335`, `onQuickSetupSetAsDefaultUnchecked` `:349`,
`checkQuickSetupSwitchesState` `:355`, `onQuickSetupAddHomescreenWidgetClicked` `:371`,
`onQuickSetupRemoveHomescreenWidgetClicked` `:377`, `checkWidgetAddedState` `:383`.

Legacy quirk carried over deliberately: `hideAddressBarRow` gates the *search options* row and the divider above
it, never the address-bar-position row, which always shows.

`NewUserOnboardingActivityDialog.QuickSetup.isReinstallUser` is not carried into the config: nothing in the view
layer reads it (legacy stores it in its view state and never uses it; the pixel sender resolves reinstall status
itself).

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogBinder.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenWelcomePage.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt`
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/QuickSetupBinder.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt`

**Interfaces:**
- Produces:
  `ContentConfig.QuickSetup(title, showSplitOption, hideSetDefaultBrowserRow, hideAddWidgetRow, hideAddressBarRow, initialAddressBarPosition, initialWithAi)`
  implementing `Stateful<QuickSetupContentState>`;
  `data class QuickSetupContentState(defaultBrowserChecked: Boolean, widgetChecked: Boolean, addressBarPosition: OmnibarType, withAi: Boolean)`;
  `ContentInteraction.EditAddressBarPosition`, `EditSearchOptions`, `SetDefaultBrowserToggled(checked)`,
  `AddWidgetToggled(checked)`; view-model methods `syncQuickSetupSwitches()`,
  `onAddressBarBottomSheetResult(type)`, `onSearchOptionsBottomSheetResult(withAi)`,
  `onQuickSetupDefaultBrowserSet()`, `onQuickSetupDefaultBrowserNotSet()`;
  `QuickSetupBinder(binding: IncludeBrandDesignReinstallerQuickSetupBinding)`.

- [ ] **Step 1: Write the failing tests**

`DialogConfigResolverTest`:

```kotlin
    @Test
    fun `resolves quick setup with the plan's row visibility and a submitting cta`() {
        val dialog = NewUserOnboardingActivityDialog.QuickSetup(
            showSplitOption = true,
            hideSetDefaultBrowserRow = true,
            hideAddWidgetRow = false,
            hideAddressBarRow = true,
            isReinstallUser = true,
        )

        val config = testee.resolve(dialog, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.QuickSetup, config.background)
        assertEquals(Embellishment.BottomWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        assertEquals(
            ContentConfig.QuickSetup(
                title = TextConfig.Resource(R.string.preOnboardingReinstallQuickSetupTitle),
                showSplitOption = true,
                hideSetDefaultBrowserRow = true,
                hideAddWidgetRow = false,
                hideAddressBarRow = true,
                initialAddressBarPosition = OmnibarType.SINGLE_TOP,
                initialWithAi = true,
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingReinstallStartBrowsing), CtaAction.Submit),
            config.primaryCta,
        )
    }

    @Test
    fun `resolves quick setup with custom ai cta copy in the custom ai flow`() {
        val dialog = NewUserOnboardingActivityDialog.QuickSetup(
            showSplitOption = false,
            hideSetDefaultBrowserRow = false,
            hideAddWidgetRow = false,
            hideAddressBarRow = false,
            isReinstallUser = false,
        )

        val config = testee.resolve(dialog, isCustomAiFlow = true)!!

        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingDaxDialog3ButtonCustomAi), CtaAction.Submit),
            config.primaryCta,
        )
    }
```

`ConfigDrivenOnboardingPageViewModelTest` — add a `DefaultBrowserDetector` mock field, pass it to
`createViewModel`, and add a helper plus the tests:

```kotlin
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()

    private val quickSetupDialog = NewUserOnboardingActivityDialog.QuickSetup(
        showSplitOption = true,
        hideSetDefaultBrowserRow = false,
        hideAddWidgetRow = false,
        hideAddressBarRow = false,
        isReinstallUser = false,
    )

    private fun quickSetupState(testee: ConfigDrivenOnboardingPageViewModel): QuickSetupContentState {
        val content = testee.viewState.value.config!!.content as ContentConfig.QuickSetup
        return testee.contentValues.contentState(testee.viewState.value.stepId!!, content).value
    }
```

```kotlin
    @Test
    fun `asks for the address bar bottom sheet with the current quick setup selection`() = runTest {
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.commands.test {
            testee.onContentInteraction(ContentInteraction.EditAddressBarPosition)
            advanceUntilIdle()
            assertEquals(
                Command.ShowQuickSetupAddressBarPositionBottomSheet(
                    initialSelection = OmnibarType.SINGLE_TOP,
                    showSplitOption = true,
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `writes the address bar bottom sheet result into the quick setup state`() = runTest {
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.onAddressBarBottomSheetResult(OmnibarType.SINGLE_BOTTOM)

        assertEquals(OmnibarType.SINGLE_BOTTOM, quickSetupState(testee).addressBarPosition)
    }

    @Test
    fun `writes the search options bottom sheet result into the quick setup state`() = runTest {
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.onSearchOptionsBottomSheetResult(withAi = false)

        assertFalse(quickSetupState(testee).withAi)
    }

    @Test
    fun `mirrors the default browser toggle into the quick setup state before showing the system dialog`() = runTest {
        whenever(mockDefaultRoleBrowserDialog.createIntent(any())).thenReturn(Intent())
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.commands.test {
            testee.onContentInteraction(ContentInteraction.SetDefaultBrowserToggled(checked = true))
            advanceUntilIdle()
            assertTrue(quickSetupState(testee).defaultBrowserChecked)
            assertTrue(awaitItem() is Command.ShowQuickSetupDefaultBrowserDialog)
        }
    }

    @Test
    fun `opens the system browser settings when no default browser dialog is available`() = runTest {
        whenever(mockDefaultRoleBrowserDialog.createIntent(any())).thenReturn(null)
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.commands.test {
            testee.onContentInteraction(ContentInteraction.SetDefaultBrowserToggled(checked = true))
            advanceUntilIdle()
            assertEquals(Command.OpenDefaultBrowserSystemSettings, awaitItem())
        }
    }

    @Test
    fun `asks for the remove widget instructions when the widget toggle is turned off`() = runTest {
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.commands.test {
            testee.onContentInteraction(ContentInteraction.AddWidgetToggled(checked = false))
            advanceUntilIdle()
            assertFalse(quickSetupState(testee).widgetChecked)
            assertEquals(Command.ShowRemoveWidgetBottomSheet, awaitItem())
        }
    }

    @Test
    fun `resyncs the quick setup switches from the system on resume`() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        val testee = startAt(quickSetupDialog)
        advanceUntilIdle()

        testee.onResume()
        advanceUntilIdle()

        val state = quickSetupState(testee)
        assertTrue(state.defaultBrowserChecked)
        assertTrue(state.widgetChecked)
    }
```

Add imports `com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector`,
`com.duckduckgo.app.browser.omnibar.OmnibarType`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: compile failure — `ContentConfig.QuickSetup`, the new interactions and the new commands are unresolved.

- [ ] **Step 3: Add the content config, state and interactions**

In `ContentConfig.kt`:

```kotlin
    data class QuickSetup(
        override val title: TextConfig,
        val showSplitOption: Boolean,
        val hideSetDefaultBrowserRow: Boolean,
        val hideAddWidgetRow: Boolean,
        val hideAddressBarRow: Boolean,
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
```

```kotlin
data class QuickSetupContentState(
    val defaultBrowserChecked: Boolean,
    val widgetChecked: Boolean,
    val addressBarPosition: OmnibarType,
    val withAi: Boolean,
)
```

In `DialogBinder.kt`, add to `ContentInteraction`:

```kotlin
    data object EditAddressBarPosition : ContentInteraction

    data object EditSearchOptions : ContentInteraction

    data class SetDefaultBrowserToggled(val checked: Boolean) : ContentInteraction

    data class AddWidgetToggled(val checked: Boolean) : ContentInteraction
```

- [ ] **Step 4: Add the resolver branch**

Remove `is NewUserOnboardingActivityDialog.QuickSetup` from the null branch and add:

```kotlin
        is NewUserOnboardingActivityDialog.QuickSetup -> DialogConfig(
            background = OnboardingBackgroundStep.QuickSetup,
            embellishment = Embellishment.BottomWing,
            cardArrow = CardArrowConfig.AtEnd,
            content = ContentConfig.QuickSetup(
                title = TextConfig.Resource(R.string.preOnboardingReinstallQuickSetupTitle),
                showSplitOption = dialog.showSplitOption,
                hideSetDefaultBrowserRow = dialog.hideSetDefaultBrowserRow,
                hideAddWidgetRow = dialog.hideAddWidgetRow,
                hideAddressBarRow = dialog.hideAddressBarRow,
                initialAddressBarPosition = OmnibarType.SINGLE_TOP,
                initialWithAi = true,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(
                    if (isCustomAiFlow) R.string.preOnboardingDaxDialog3ButtonCustomAi else R.string.preOnboardingReinstallStartBrowsing,
                ),
                action = CtaAction.Submit,
            ),
        )
```

- [ ] **Step 5: Extend the view model**

Inject the detector — add to the constructor, after `dispatchers`:

```kotlin
    private val defaultBrowserDetector: DefaultBrowserDetector,
```

with the import `com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector`.

Add to `Command`:

```kotlin
        data class ShowQuickSetupDefaultBrowserDialog(val intent: Intent) : Command
        data object OpenDefaultBrowserSystemSettings : Command
        data object ShowRemoveWidgetBottomSheet : Command
        data class ShowQuickSetupAddressBarPositionBottomSheet(
            val initialSelection: OmnibarType,
            val showSplitOption: Boolean,
        ) : Command
        data class ShowQuickSetupSearchOptionsBottomSheet(val initialWithAi: Boolean) : Command
```

Add the field:

```kotlin
    private var quickSetupDefaultBrowserDialogShown = false
```

Extend `onContentInteraction` with the four new branches:

```kotlin
            ContentInteraction.EditAddressBarPosition -> {
                val screen = currentQuickSetup() ?: return
                viewModelScope.launch {
                    _commands.send(
                        Command.ShowQuickSetupAddressBarPositionBottomSheet(
                            initialSelection = screen.state.value.addressBarPosition,
                            showSplitOption = screen.content.showSplitOption,
                        ),
                    )
                }
            }

            ContentInteraction.EditSearchOptions -> {
                val screen = currentQuickSetup() ?: return
                viewModelScope.launch {
                    _commands.send(Command.ShowQuickSetupSearchOptionsBottomSheet(initialWithAi = screen.state.value.withAi))
                }
            }

            // The switch has already flipped itself, so the store has to record that before any side effect: a
            // later corrective write of the old value (declined system dialog, resume resync) would otherwise be
            // deduped as a no-change and never reach the binder.
            is ContentInteraction.SetDefaultBrowserToggled -> {
                currentQuickSetup()?.state?.update { it.copy(defaultBrowserChecked = interaction.checked) }
                if (interaction.checked) requestDefaultBrowser() else openDefaultBrowserSettings()
            }

            is ContentInteraction.AddWidgetToggled -> {
                currentQuickSetup()?.state?.update { it.copy(widgetChecked = interaction.checked) }
                viewModelScope.launch {
                    _commands.send(if (interaction.checked) Command.LaunchAddWidgetPrompt else Command.ShowRemoveWidgetBottomSheet)
                }
            }
```

Note: the `AddWidgetToggled(checked = true)` path reuses `Command.LaunchAddWidgetPrompt` but must not set
`addWidgetPromptFlowStarted` — that flag belongs to the standalone `AddWidget` step, which advances the
orchestrator when the prompt returns. The quick-setup toggle only refreshes its switch on resume.

Add the public entry points:

```kotlin
    fun onAddressBarBottomSheetResult(type: OmnibarType) {
        currentQuickSetup()?.state?.update { it.copy(addressBarPosition = type) }
    }

    fun onSearchOptionsBottomSheetResult(withAi: Boolean) {
        currentQuickSetup()?.state?.update { it.copy(withAi = withAi) }
    }

    /** Quick setup's own default-browser prompt: it never advances the step, which only moves on confirmation. */
    fun onQuickSetupDefaultBrowserSet() {
        recordDefaultBrowserDialogResult(isSet = true, fireTelemetry = false)
    }

    fun onQuickSetupDefaultBrowserNotSet() {
        recordDefaultBrowserDialogResult(isSet = false, fireTelemetry = false)
    }

    /**
     * Re-reads the OS state behind quick setup's two switches. Also called by the fragment when the system
     * settings intent cannot be launched, since no activity starts and no later [onResume] follows.
     */
    fun syncQuickSetupSwitches() {
        val screen = currentQuickSetup() ?: return
        viewModelScope.launch {
            val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
            }
            screen.state.update { it.copy(defaultBrowserChecked = isDefault, widgetChecked = hasWidget) }
        }
    }
```

Add the private helpers:

```kotlin
    private fun requestDefaultBrowser() {
        viewModelScope.launch {
            if (!quickSetupDefaultBrowserDialogShown) {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    quickSetupDefaultBrowserDialogShown = true
                    _commands.send(Command.ShowQuickSetupDefaultBrowserDialog(intent))
                    return@launch
                }
            }
            _commands.send(Command.OpenDefaultBrowserSystemSettings)
        }
    }

    private fun openDefaultBrowserSettings() {
        viewModelScope.launch { _commands.send(Command.OpenDefaultBrowserSystemSettings) }
    }

    private fun currentQuickSetup(): QuickSetupScreen? {
        val state = _viewState.value
        val stepId = state.stepId ?: return null
        val content = state.config?.content as? ContentConfig.QuickSetup ?: return null
        return QuickSetupScreen(content, contentValues.contentState(stepId, content))
    }

    private class QuickSetupScreen(
        val content: ContentConfig.QuickSetup,
        val state: MutableStateFlow<QuickSetupContentState>,
    )
```

Change `recordDefaultBrowserDialogResult` to take the telemetry flag:

```kotlin
    private fun recordDefaultBrowserDialogResult(
        isSet: Boolean,
        fireTelemetry: Boolean = true,
    ) {
        defaultRoleBrowserDialog.dialogShown()
        appInstallStore.defaultBrowser = isSet
        if (fireTelemetry) {
            val pixelName = if (isSet) AppPixelName.DEFAULT_BROWSER_SET else AppPixelName.DEFAULT_BROWSER_NOT_SET
            pixel.fire(pixelName, mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()))
        }
    }
```

And extend `onResume`:

```kotlin
    fun onResume() {
        syncQuickSetupSwitches()
        checkAddWidgetPromptResult()
    }
```

- [ ] **Step 6: Add the binder**

Create `QuickSetupBinder.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven.binders

import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeBrandDesignReinstallerQuickSetupBinding
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.QuickSetupContentState
import com.duckduckgo.app.onboarding.ui.page.configdriven.StatefulDialogBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The address-bar-position row is always shown: only the search-options row and the divider above it follow
 * `hideAddressBarRow`, matching the legacy screen.
 */
class QuickSetupBinder(
    private val binding: IncludeBrandDesignReinstallerQuickSetupBinding,
) : StatefulDialogBinder<ContentConfig.QuickSetup, QuickSetupContentState> {

    override val view: View = binding.root

    override fun bind(
        content: ContentConfig.QuickSetup,
        state: MutableStateFlow<QuickSetupContentState>,
        scope: BindScope,
    ): ContentHandle = with(binding) {
        val context = root.context

        setDefaultBrowserItem.isVisible = !content.hideSetDefaultBrowserRow
        setDefaultBrowserDivider.isVisible = !content.hideSetDefaultBrowserRow
        addWidgetItem.isVisible = !content.hideAddWidgetRow
        addWidgetDivider.isVisible = !content.hideAddWidgetRow
        addressBarSearchOptionsItem.isVisible = !content.hideAddressBarRow
        addressBarSearchOptionsDivider.isVisible = !content.hideAddressBarRow

        setDefaultBrowserItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.SetDefaultBrowserToggled(checked))
        }
        addWidgetItem.setOnCheckedChangeListener { checked ->
            scope.execute(ContentInteraction.AddWidgetToggled(checked))
        }
        addressBarPositionItem.setOnClickListener { scope.execute(ContentInteraction.EditAddressBarPosition) }
        addressBarSearchOptionsItem.setOnClickListener { scope.execute(ContentInteraction.EditSearchOptions) }

        scope.coroutineScope.launch {
            state.collect { render(it) }
        }

        quickSetupTitle.setTitle(content.title.resolve(context))

        ContentHandle(
            title = quickSetupTitle,
            fadeTargets = listOf(quickSetupOptionsContainer),
            result = { NewUserOnboardingEvent.QuickSetupConfirmed(state.value.addressBarPosition, state.value.withAi) },
        )
    }

    /** Switches render silently so re-rendering a collected value never re-fires the listener that produced it. */
    private fun render(state: QuickSetupContentState) = with(binding) {
        setDefaultBrowserItem.setCheckedSilently(state.defaultBrowserChecked)
        addWidgetItem.setCheckedSilently(state.widgetChecked)
        addressBarPositionItem.setIcon(addressBarPositionIconRes(state.addressBarPosition))
        addressBarPositionItem.setSecondaryText(addressBarPositionLabelRes(state.addressBarPosition))
        addressBarSearchOptionsItem.setIcon(searchOptionsIconRes(state.withAi))
        addressBarSearchOptionsItem.setSecondaryText(searchOptionsLabelRes(state.withAi))
    }

    private fun addressBarPositionIconRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.drawable.ic_address_bar_top_24
        OmnibarType.SINGLE_BOTTOM -> R.drawable.ic_address_bar_bottom_24
        OmnibarType.SPLIT -> R.drawable.ic_address_bar_split_24
    }

    private fun addressBarPositionLabelRes(type: OmnibarType): Int = when (type) {
        OmnibarType.SINGLE_TOP -> R.string.preOnboardingAddressBarPositionTop
        OmnibarType.SINGLE_BOTTOM -> R.string.preOnboardingAddressBarPositionBottom
        OmnibarType.SPLIT -> R.string.preOnboardingAddressBarPositionSplit
    }

    private fun searchOptionsIconRes(withAi: Boolean): Int = if (withAi) R.drawable.ic_ai_24 else R.drawable.ic_search_24

    private fun searchOptionsLabelRes(withAi: Boolean): Int =
        if (withAi) R.string.quickSetupInputScreenSearchAndDuckAi else R.string.quickSetupInputScreenSearchOnly
}
```

`state.collect` replays its current value, so the initial render comes from the collector — no separate seeding
call at bind time.

- [ ] **Step 7: Route the config to the binder**

```kotlin
    private val quickSetup = QuickSetupBinder(binding.reinstallerQuickSetupContent)
```

```kotlin
            is ContentConfig.QuickSetup -> {
                boundView = quickSetup.view
                quickSetup.bind(content, contentValues.contentState(stepId, content), scope)
            }
```

- [ ] **Step 8: Handle the new commands in the fragment**

In `ConfigDrivenWelcomePage.kt`, add the quick-setup default-browser launcher next to the existing one:

```kotlin
    private val quickSetupDefaultBrowserRoleManagerDialog = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onQuickSetupDefaultBrowserSet()
        } else {
            viewModel.onQuickSetupDefaultBrowserNotSet()
        }
    }
```

Register the bottom-sheet result listeners at the end of `onViewCreated`:

```kotlin
        registerQuickSetupBottomSheetResultListeners()
```

```kotlin
    private fun registerQuickSetupBottomSheetResultListeners() {
        childFragmentManager.setFragmentResultListener(
            QuickSetupAddressBarPositionBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val selectedName = bundle.getString(QuickSetupAddressBarPositionBottomSheet.RESULT_KEY_SELECTED_POSITION)
                ?: return@setFragmentResultListener
            viewModel.onAddressBarBottomSheetResult(OmnibarType.valueOf(selectedName))
        }
        childFragmentManager.setFragmentResultListener(
            QuickSetupSearchOptionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            viewModel.onSearchOptionsBottomSheetResult(withAi = bundle.getBoolean(QuickSetupSearchOptionsBottomSheet.RESULT_KEY_WITH_AI))
        }
        childFragmentManager.setFragmentResultListener(
            RemoveWidgetInstructionsBottomSheet.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, _ ->
            viewModel.syncQuickSetupSwitches()
        }
    }
```

Add the command branches in `handleCommand`:

```kotlin
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupDefaultBrowserDialog ->
                quickSetupDefaultBrowserRoleManagerDialog.launch(command.intent)
            ConfigDrivenOnboardingPageViewModel.Command.OpenDefaultBrowserSystemSettings -> openDefaultBrowserSystemSettings()
            ConfigDrivenOnboardingPageViewModel.Command.ShowRemoveWidgetBottomSheet ->
                RemoveWidgetInstructionsBottomSheet().show(childFragmentManager, RemoveWidgetInstructionsBottomSheet.TAG)
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupAddressBarPositionBottomSheet ->
                QuickSetupAddressBarPositionBottomSheet
                    .newInstance(initialSelection = command.initialSelection, showSplitOption = command.showSplitOption)
                    .show(childFragmentManager, QuickSetupAddressBarPositionBottomSheet.TAG)
            is ConfigDrivenOnboardingPageViewModel.Command.ShowQuickSetupSearchOptionsBottomSheet ->
                QuickSetupSearchOptionsBottomSheet
                    .newInstance(initialWithAi = command.initialWithAi)
                    .show(childFragmentManager, QuickSetupSearchOptionsBottomSheet.TAG)
```

and the settings fallback:

```kotlin
    private fun openDefaultBrowserSystemSettings() {
        try {
            startActivity(DefaultBrowserSystemSettings.intent())
        } catch (e: ActivityNotFoundException) {
            val errorMessage = getString(R.string.cannotLaunchDefaultAppSettings)
            logcat(WARN) { "$errorMessage: ${e.asLog()}" }
            Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_SHORT).show()
            // No activity launches, so the resume-driven resync never arrives for this attempt.
            viewModel.syncQuickSetupSwitches()
        }
    }
```

New imports: `android.content.ActivityNotFoundException`, `android.widget.Toast`,
`com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserSystemSettings`,
`com.duckduckgo.app.browser.omnibar.OmnibarType`,
`com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupAddressBarPositionBottomSheet`,
`com.duckduckgo.app.onboardingquicksetup.ui.QuickSetupSearchOptionsBottomSheet`,
`com.duckduckgo.app.onboardingquicksetup.ui.RemoveWidgetInstructionsBottomSheet`,
`logcat.LogPriority.WARN`, `logcat.asLog`, `logcat.logcat`.

- [ ] **Step 9: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 10: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ContentConfig.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogBinder.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolver.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenWelcomePage.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/engine/ContentController.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/binders/QuickSetupBinder.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/DialogConfigResolverTest.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt
git commit -m "add the quick setup binder and its config mapping"
```

---

### Task 7: Shown-pixel parity

The new arm fires none of the legacy once-ever "dialog shown" pixels. Port
`BrandDesignUpdatePageViewModel.fireDialogShownPixel` (`:208-222`) as a standalone collaborator keyed on
`NewUserOnboardingActivityDialog` instead of `PreOnboardingDialogType`, with an exhaustive `when` and no `else`.

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingDialogShownPixels.kt`
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingDialogShownPixelsTest.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt`

**Interfaces:**
- Produces: `OnboardingDialogShownPixels(pixel: Pixel)` with `fun fireFor(dialog: NewUserOnboardingActivityDialog)`.

- [ ] **Step 1: Write the failing tests**

Create `OnboardingDialogShownPixelsTest.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class OnboardingDialogShownPixelsTest {

    private val pixel: Pixel = mock()
    private val testee = OnboardingDialogShownPixels(pixel)

    @Test
    fun `fires the intro shown pixel once ever for the initial dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.Initial)

        verify(pixel).fire(AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the reinstall intro shown pixel for the reinstall dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.InitialReinstallUser)

        verify(pixel).fire(AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the sync restore shown pixel for the sync restore dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.SyncRestore)

        verify(pixel).fire(AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the comparison chart shown pixel for the comparison chart`() {
        testee.fireFor(NewUserOnboardingActivityDialog.ComparisonChart)

        verify(pixel).fire(AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the ai comparison shown pixel for the ai comparison chart`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AiComparisonChart)

        verify(pixel).fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
    }

    @Test
    fun `fires the address bar shown pixel for the address bar dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = false))

        verify(pixel).fire(AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the search experience shown pixel for the input screen`() {
        testee.fireFor(NewUserOnboardingActivityDialog.InputScreen)

        verify(pixel).fire(AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
    }

    @Test
    fun `fires nothing for the dialogs legacy had no shown pixel for`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AddToDock)
        testee.fireFor(NewUserOnboardingActivityDialog.WidgetPrompt)
        testee.fireFor(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = true))

        verifyNoInteractions(pixel)
    }
}
```

`Pixel.fire(pixel, parameters = emptyMap(), encodedParameters = emptyMap(), type = Count)` — the named-argument
form above matches the production call exactly, since Kotlin fills the same defaults on both sides.

Add to `ConfigDrivenOnboardingPageViewModelTest`, with a new `private val mockShownPixels: OnboardingDialogShownPixels = mock()`
passed into `createViewModel`:

```kotlin
    @Test
    fun `fires the shown pixel for a dialog it renders`() = runTest {
        startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        verify(mockShownPixels).fireFor(NewUserOnboardingActivityDialog.ComparisonChart)
    }

    @Test
    fun `fires no shown pixel for a command only dialog`() = runTest {
        startAt(NewUserOnboardingActivityDialog.NotificationPermission)
        advanceUntilIdle()

        verifyNoInteractions(mockShownPixels)
    }
```

Add the imports `org.mockito.kotlin.verify` and `org.mockito.kotlin.verifyNoInteractions` to the test file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: compile failure — `OnboardingDialogShownPixels` is unresolved.

- [ ] **Step 3: Add the mapper**

Create `OnboardingDialogShownPixels.kt`:

```kotlin
package com.duckduckgo.app.onboarding.ui.page.configdriven

import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import javax.inject.Inject

/**
 * The once-ever "dialog shown" pixels, keyed on the dialog being applied. Exhaustive with no `else`, so a new
 * dialog cannot compile until it has been given an explicit decision, even when that decision is to fire nothing.
 */
class OnboardingDialogShownPixels @Inject constructor(private val pixel: Pixel) {

    fun fireFor(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            NewUserOnboardingActivityDialog.SyncRestore -> pixel.fire(PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InitialReinstallUser ->
                pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.Initial -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.ComparisonChart -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.AiComparisonChart ->
                pixel.fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
            is NewUserOnboardingActivityDialog.AddressBarPosition ->
                pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InputScreen ->
                pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
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

- [ ] **Step 4: Wire it into the view model**

Add the constructor parameter after `dialogConfigResolver`:

```kotlin
    private val shownPixels: OnboardingDialogShownPixels,
```

and fire it in `applyStep`, immediately inside `if (config != null) {`:

```kotlin
            shownPixels.fireFor(dialog)
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.*"`
Expected: PASS.

- [ ] **Step 6: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingDialogShownPixels.kt \
        app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/OnboardingDialogShownPixelsTest.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt
git commit -m "fire the legacy dialog shown pixels from the config-driven view model"
```

---

### Task 8: Reduce the unrendered-dialog path to the command-only dialogs

After tasks 1-6, `DialogConfigResolver.resolve()` returns null for exactly four dialogs:
`IntroAnimation`, `NotificationPermission`, `DefaultBrowserPrompt`, `AddWidget`. Every other branch in
`advancePastUnrenderedDialog` is now dead and its "advance past without presenting" shortcut would be a bug if
reached — `SyncRestore` would skip onboarding instead of offering restore.

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt`
- Test: `app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt`

**Interfaces:**
- Consumes: everything from tasks 1-7. Produces no new public surface.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `renders the sync restore dialog instead of skipping onboarding`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.SyncRestore)
        advanceUntilIdle()

        val content = testee.viewState.value.config!!.content as ContentConfig.Welcome
        assertEquals(TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle), content.title)
        assertTrue(recordedEvents.isEmpty())
    }
```

Add the import `com.duckduckgo.app.browser.R`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.ui.page.configdriven.ConfigDrivenOnboardingPageViewModelTest"`
Expected: this test PASSES already if task 1 landed — `SyncRestore` now resolves to a config, so the null branch
is never reached. If it FAILS, task 1's resolver branch is missing or wrong; fix that before continuing.

- [ ] **Step 3: Rename and reduce the method**

Rename `advancePastUnrenderedDialog` to `handleCommandOnlyDialog`, update its call site in `applyStep`, and
replace its body:

```kotlin
    /**
     * The four dialogs [DialogConfigResolver] maps to null: they have no card to render, only a side effect.
     * Config-producing dialogs are listed exhaustively so the compiler keeps the two mappings in step.
     */
    private suspend fun handleCommandOnlyDialog(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            is NewUserOnboardingActivityDialog.IntroAnimation -> emit(NewUserOnboardingEvent.IntroAnimationFinished)

            NewUserOnboardingActivityDialog.NotificationPermission -> {
                if (!notificationPermissionFlowStarted) {
                    notificationPermissionFlowStarted = true
                    viewModelScope.launch {
                        delay(2.seconds)
                        _commands.send(Command.RequestNotificationPermissions)
                    }
                }
            }

            NewUserOnboardingActivityDialog.DefaultBrowserPrompt -> {
                val intent = defaultRoleBrowserDialog.createIntent(context)
                if (intent != null) {
                    _commands.send(Command.ShowDefaultBrowserDialog(intent))
                } else {
                    pixel.fire(AppPixelName.DEFAULT_BROWSER_DIALOG_NOT_SHOWN)
                    emit(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
                }
            }

            NewUserOnboardingActivityDialog.AddWidget -> {
                addWidgetPromptFlowStarted = true
                _commands.send(Command.LaunchAddWidgetPrompt)
            }

            NewUserOnboardingActivityDialog.SyncRestore,
            NewUserOnboardingActivityDialog.InitialReinstallUser,
            NewUserOnboardingActivityDialog.Initial,
            NewUserOnboardingActivityDialog.ComparisonChart,
            NewUserOnboardingActivityDialog.AiComparisonChart,
            NewUserOnboardingActivityDialog.AddToDock,
            NewUserOnboardingActivityDialog.WidgetPrompt,
            is NewUserOnboardingActivityDialog.AddressBarPosition,
            NewUserOnboardingActivityDialog.InputScreen,
            is NewUserOnboardingActivityDialog.InputScreenPreview,
            is NewUserOnboardingActivityDialog.QuickSetup,
            -> Unit
        }
    }
```

The `IntroAnimation` branch keeps emitting `IntroAnimationFinished` — the intro is implemented on another branch.

- [ ] **Step 4: Run the full module test suite**

Run: `./gradlew :app:testInternalDebugUnitTest --tests "com.duckduckgo.app.onboarding.*"`
Expected: PASS.

- [ ] **Step 5: Format and commit**

```bash
./gradlew spotlessApply
git add app/src/main/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModel.kt \
        app/src/test/java/com/duckduckgo/app/onboarding/ui/page/configdriven/ConfigDrivenOnboardingPageViewModelTest.kt
git commit -m "reduce the unrendered dialog path to the command only dialogs"
```

---

### Task 9: Whole-branch verification

**Files:** none modified unless a check fails.

- [ ] **Step 1: Run the full `:app` unit test suite**

Run: `./gradlew :app:testInternalDebugUnitTest`
Expected: PASS. `BrandDesignUpdatePageViewModel` and its tests were not touched, so the legacy arm's tests must
still pass untouched.

- [ ] **Step 2: Run lint and format checks**

Run: `./gradlew spotlessCheck lint_check`
Expected: PASS. `lint_check` enforces the ADS rules — no raw `Button`/`TextView`, no `@color/` references, no
`AlertDialog`. None of the new code adds layouts, so failures here point at an import or an API misuse.

- [ ] **Step 3: Build and install the internal build**

Run: `./gradlew installInternalRelease`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Manual device check with the flag on**

Enable `configDrivenDialogs` in the internal build's feature-flag dev settings, then walk each ported screen and
confirm against the flag-off arm:

- Welcome, reinstall welcome, sync restore: walking Dax, arrow at the start, body lines fading in after the
  title types, and the secondary CTA present on the reinstall and sync-restore variants only.
- Add to dock: the demo video loops, no arrow, and the video stops when the step advances.
- Widget prompt: left wing, arrow at the end, both CTAs.
- Input screen: picker paints its initial selection without a crossfade, the "with AI" flourish starts after the
  fade completes, and toggling crossfades.
- Input screen preview: tabs switch hint and action icon, suggestions stagger in and are only tappable once
  visible, keyboard appears on a tall screen, and submitting from a suggestion, the action icon and the IME
  action all advance.
- Quick setup: rows respect the plan's hide flags, both switches survive rotation mid-selection, both bottom
  sheets return into the rows, and the default-browser switch reverts when the system dialog is declined.

Rotate on each screen and confirm the render snaps rather than replaying its entrance.

- [ ] **Step 5: Report**

Report which screens were verified on device, and anything that behaved differently from the flag-off arm.
Do not enable the flag by default and do not open a PR as part of this plan.

---

## Self-review notes

- Spec coverage: screens (tasks 1-6), shown pixels (task 7), command-only dialogs (task 8), content interactions
  (tasks 5-6), quick-setup bottom sheets and re-sync (task 6), add-to-dock video (task 2), testing and
  verification (each task plus task 9). Intro animation is explicitly out of scope per the spec.
- Two deliberate divergences from the spec text, both noted at their task: the add-to-dock video starts from the
  surface-available callback rather than `onContentReady` (that is what gates playback, and it matches legacy),
  and `ContentConfig.QuickSetup` drops `isReinstallUser` because nothing in the view layer reads it.
- `ContentConfig.Welcome` carries an explicit `body1AsHtml` flag rather than inferring HTML from `body2 == null`,
  which is what the POC did.
- The quick-setup card arrow, left as "derived from legacy" in the spec, is resolved: `AtEnd`
  (`BrandDesignUpdateWelcomePage.kt:1044-1053` slides the arrow to fraction 1).
