/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingResolver
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager.QuickSetupExperimentVariant
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.ReturnAndAdvance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import com.duckduckgo.sync.api.SyncAutoRestore
import dagger.SingleInstanceIn
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Composes the linear-onboarding plan for the new user.
 *
 * Each call to [buildRootPlan] creates a fresh [NewUserOnboardingPlanContext] and per-run [SuspendMemo]s, so
 * a new onboarding run never reads stale state.
 */
@SingleInstanceIn(AppScope::class)
class NewUserOnboardingPlanProvider @Inject constructor(
    private val syncAutoRestore: SyncAutoRestore,
    private val appBuildConfig: AppBuildConfig,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val settingsDataStore: SettingsDataStore,
    private val onboardingStore: OnboardingStore,
    private val duckChat: DuckChat,
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val duckAiOnboardingAvailability: DuckAiOnboardingAvailability,
    private val onboardingQuickSetupExperimentManager: OnboardingQuickSetupExperimentManager,
    private val onboardingPixelSender: OnboardingPixelSender,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val dismissedCtaDao: DismissedCtaDao,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val customAiOnboardingResolver: CustomAiOnboardingResolver,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
) {

    suspend fun buildRootPlan(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan =
        if (customAiOnboardingResolver.resolve()) {
            // in custom AI onboarding path, the input toggle is enabled by default
            duckChat.setCosmeticInputScreenUserSetting(enabled = true)
            onboardingStore.storeInputScreenSelection(selected = true)

            // prepare in-context CTAs
            duckAiOnboardingDemo.arm()

            pixel.fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())

            buildCustomAiPlan(onCompleted, onSkipped)
        } else {
            buildDefaultPlan(onCompleted, onSkipped)
        }

    private fun buildDefaultPlan(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val ctx = NewUserOnboardingPlanContext()

        // SuspendMemos evaluate the inner lambda lazily, on first access, and store the result in-memory for subsequent access
        val firstDialog = SuspendMemo { resolveFirstDialog(ctx) }
        val duckAiEnabled = SuspendMemo { duckAiOnboardingAvailability.isDuckAiOnboardingEnabled() }

        val skipPlan = skipPlan()
        val quickSetupPlan = quickSetupPlan(ctx)

        return rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = listOf(
                introAnimationStep(),
                notificationPermissionStep(),
                syncRestoreStep(firstDialog, skipPlan, quickSetupPlan),
                initialReinstallUserStep(firstDialog, skipPlan, quickSetupPlan),
                initialStep(firstDialog),
                comparisonChartStep(),
                defaultBrowserPromptStep(),
                addressBarPositionStep(),
                inputScreenStep(ctx),
                inputScreenPreviewStep(ctx, duckAiEnabled),
            ),
        )
    }

    private fun buildCustomAiPlan(
        rootOnCompleted: suspend () -> Unit,
        rootOnSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val ctx = NewUserOnboardingPlanContext()
        val firstDialog = SuspendMemo { resolveFirstDialog(ctx) }

        val skipPlan = skipPlan()
        val quickSetupPlan = quickSetupPlan(ctx)

        val dismissDuckAiFireCta = suspend {
            // End-of-plan dismissal for Duck AI Fire CTA — deferred to here (vs. on user interaction)
            // so the CTA survives an app kill and re-runs correctly on next launch, if linear onboarding wasn't finished yet.
            withContext(dispatchers.io()) {
                dismissedCtaDao.insert(DismissedCta(CtaId.DAX_DUCK_AI_FIRE_BUTTON))
            }
        }
        val markInputToLaunchOnChat = {
            // The custom-AI flow always finishes on the Duck.ai (chat) tab
            customAiOnboardingStore.setOpenInputOnDuckAiTab()
        }
        val onCompleted = suspend {
            dismissDuckAiFireCta()
            markInputToLaunchOnChat()
            rootOnCompleted()
        }
        val onSkipped = suspend {
            dismissDuckAiFireCta()
            markInputToLaunchOnChat()
            rootOnSkipped()
        }

        return rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = listOf(
                introAnimationStep(withDuckAi = true),
                notificationPermissionStep(),
                initialReinstallUserStep(firstDialog, skipPlan, quickSetupPlan, isCustomAiPlan = true),
                initialStep(firstDialog),
                aiComparisonChartStep(),
                customAiInputScreenPreviewStep(ctx),
                duckAiDemoStep(ctx),
                comparisonChartStep(),
                defaultBrowserPromptStep(),
                addressBarPositionStep(),
            ),
        )
    }

    private fun rootPlan(
        ctx: NewUserOnboardingPlanContext,
        steps: List<LinearOnboardingStep>,
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan =
        LinearOnboardingPlan(
            id = ROOT_PLAN_ID,
            steps = steps.firingShownPixels().abortingOnDevSkip(),
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            result = { ctx.completionResult },
        )

    private fun skipPlan(): LinearOnboardingPlan =
        LinearOnboardingPlan(id = SKIP_PLAN_ID, steps = listOf(skipOnboardingOptionStep()).firingShownPixels().abortingOnDevSkip())

    private fun quickSetupPlan(ctx: NewUserOnboardingPlanContext): LinearOnboardingPlan =
        LinearOnboardingPlan(id = QUICK_SETUP_PLAN_ID, steps = listOf(quickSetupStep(ctx)).firingShownPixels().abortingOnDevSkip())

    /**
     * Wraps each step so the internal dev "skip all onboarding" shortcut aborts the run from wherever
     * we are. The orchestrator still only routes [NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked] to
     * the current step's transition; this keeps that cross-cutting handling in one place instead of in
     * every step factory.
     */
    private fun List<LinearOnboardingStep>.abortingOnDevSkip(): List<LinearOnboardingStep> =
        map { step ->
            val original = step.transition
            val wrapped: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
                if (event is NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked) AbortPlan else original(event)
            }
            when (step) {
                is NewUserOnboardingActivityStep -> step.copy(transition = wrapped)
                is NewUserBrowserActivityStep -> step.copy(transition = wrapped)
                else -> step
            }
        }

    /**
     * Wraps each step so that a [NewUserOnboardingEvent.Presented] event fires the step's shown pixel
     * (its [NewUserOnboardingActivityStep.pixelName], if any) and then delegates to the original transition.
     * This is the inner wrap, applied before [abortingOnDevSkip].
     */
    private fun List<LinearOnboardingStep>.firingShownPixels(): List<LinearOnboardingStep> =
        map { step ->
            val pixelName = (step as? NewUserOnboardingActivityStep)?.pixelName
                ?: (step as? NewUserBrowserActivityStep)?.pixelName
            val original = step.transition
            val wrapped: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
                if (event is NewUserOnboardingEvent.Presented && pixelName != null) {
                    onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Shown)
                }
                original(event)
            }
            when (step) {
                is NewUserOnboardingActivityStep -> step.copy(transition = wrapped)
                is NewUserBrowserActivityStep -> step.copy(transition = wrapped)
                else -> step
            }
        }

    private suspend fun resolveFirstDialog(ctx: NewUserOnboardingPlanContext): FirstDialog =
        withContext(dispatchers.io()) {
            val canRestore = withTimeoutOrNull(BLOCK_STORE_TIMEOUT_MS) {
                try {
                    logcat { "Sync-AutoRestore: checking canRestore..." }
                    val result = syncAutoRestore.canRestore()
                    logcat(LogPriority.INFO) { "Sync-AutoRestore: canRestore=$result" }
                    result
                } catch (t: Throwable) {
                    coroutineContext.ensureActive()
                    logcat(LogPriority.WARN) { "Sync-AutoRestore: canRestore check failed - ${t.message}" }
                    false
                }
            } ?: false
            // Side-effecting (creates the DDG downloads dir, persists reinstall state) and must always run
            val isReinstall = appBuildConfig.isAppReinstall()
            ctx.isReinstall = isReinstall
            when {
                canRestore -> FirstDialog.SYNC_RESTORE
                isReinstall -> FirstDialog.REINSTALL
                else -> FirstDialog.INITIAL
            }
        }

    private fun introAnimationStep(withDuckAi: Boolean = false) = NewUserOnboardingActivityStep(
        id = NewUserOnboardingStepIds.INTRO_ANIMATION,
        pixelName = null,
        resolveDialog = {
            NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi)
        },
        transition = { event ->
            when {
                event is NewUserOnboardingEvent.IntroAnimationFinished -> Advance
                else -> Stay
            }
        },
    )

    private fun notificationPermissionStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_NOTIFICATIONS
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.NOTIFICATION_PERMISSION,
            pixelName = pixelName,
            resolveDialog = { NewUserOnboardingActivityDialog.NotificationPermission },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.NotificationPermissionFinished -> {
                        if (event.granted != null) {
                            onboardingPixelSender.fire(pixelName, OnboardingPixelAction.NotificationsConfirmed(granted = event.granted))
                        }
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun syncRestoreStep(
        firstDialog: SuspendMemo<FirstDialog>,
        skipPlan: LinearOnboardingPlan,
        quickSetupPlan: LinearOnboardingPlan,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WELCOME
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.SYNC_RESTORE,
            pixelName = pixelName,
            precondition = { firstDialog() == FirstDialog.SYNC_RESTORE },
            resolveDialog = { NewUserOnboardingActivityDialog.SyncRestore },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.RestoreRequested -> {
                        pixel.fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        syncAutoRestore.restoreSyncAccount()
                        Advance
                    }

                    is NewUserOnboardingEvent.SkipRequested -> {
                        pixel.fire(PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE, type = Unique())
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = false))
                        skipFork(skipPlan, quickSetupPlan)
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun initialReinstallUserStep(
        firstDialog: SuspendMemo<FirstDialog>,
        skipPlan: LinearOnboardingPlan,
        quickSetupPlan: LinearOnboardingPlan,
        isCustomAiPlan: Boolean = false,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WELCOME
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INITIAL_REINSTALL_USER,
            pixelName = pixelName,
            precondition = {
                when (firstDialog()) {
                    FirstDialog.SYNC_RESTORE -> {
                        if (isCustomAiPlan) {
                            pixel.fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
                            true
                        } else {
                            false
                        }
                    }
                    FirstDialog.REINSTALL -> true
                    FirstDialog.INITIAL -> false
                }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.InitialReinstallUser },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.ContinueClicked -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    is NewUserOnboardingEvent.SkipRequested -> {
                        pixel.fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = false))
                        skipFork(skipPlan, quickSetupPlan)
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun initialStep(
        firstDialog: SuspendMemo<FirstDialog>,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WELCOME
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INITIAL,
            pixelName = pixelName,
            precondition = { firstDialog() == FirstDialog.INITIAL },
            resolveDialog = { NewUserOnboardingActivityDialog.Initial },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun comparisonChartStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SET_DEFAULT
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.COMPARISON_CHART,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.ComparisonChart },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        val showDefaultBrowserDialog = defaultRoleBrowserDialog.shouldShowDialog()
                        pixel.fire(
                            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
                            mapOf(PixelParameter.DEFAULT_BROWSER to (!showDefaultBrowserDialog).toString()),
                        )
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked())
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun defaultBrowserPromptStep() = NewUserOnboardingActivityStep(
        id = NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT,
        // No shown pixel of its own; the confirmed result belongs to the set-default pixel (started on the comparison-chart step).
        pixelName = null,
        precondition = { defaultRoleBrowserDialog.shouldShowDialog() },
        resolveDialog = { NewUserOnboardingActivityDialog.DefaultBrowserPrompt },
        transition = { event ->
            when {
                event is NewUserOnboardingEvent.DefaultBrowserPromptFinished -> {
                    onboardingPixelSender.fire(
                        OnboardingPixelName.ONBOARDING_SET_DEFAULT,
                        OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = event.isDefaultBrowser),
                    )
                    Advance
                }
                else -> Stay
            }
        },
    )

    private fun addressBarPositionStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.ADDRESS_BAR_POSITION,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = isSplitOmnibarEnabled()) },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.AddressBarConfirmed -> {
                        val resolved = resolveOmnibarType(event.type)
                        settingsDataStore.omnibarType = resolved
                        fireAddressBarPositionPixel(resolved)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.AddressBarClicked(position = resolved))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun inputScreenStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SEARCH_EXPERIENCE
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INPUT_SCREEN,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.InputScreen },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.InputModeConfirmed -> {
                        applyInputModeSelection(ctx, event.withAi, fireTelemetry = true)
                        ctx.inputModeWasAi = event.withAi
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.SearchExperienceClicked(withAi = event.withAi))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun inputScreenPreviewStep(
        ctx: NewUserOnboardingPlanContext,
        duckAiEnabled: SuspendMemo<Boolean>,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW,
            pixelName = pixelName,
            precondition = {
                ctx.inputModeWasAi && duckAiEnabled()
            },
            resolveDialog = {
                NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = true)
            },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.InputDemoQuerySubmitted -> {
                        if (event.isChat) {
                            onboardingStore.setChatOnboardingVariant()
                        } else {
                            onboardingStore.setSearchOnboardingVariant()
                        }
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.TryASearchClicked(fromSuggestion = event.fromSuggestion, isChat = event.isChat),
                        )
                        ctx.completionResult = if (event.isChat) {
                            NewUserOnboardingResult.LaunchChat(prompt = event.query)
                        } else {
                            NewUserOnboardingResult.LaunchSearch(query = event.query)
                        }
                        Advance
                    }

                    is NewUserOnboardingEvent.ContinueClicked -> Advance
                    else -> Stay
                }
            },
        )
    }

    private fun aiComparisonChartStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_AI_INTRO
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.AI_COMPARISON_CHART,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.AiComparisonChart },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked())
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    // Chat-only preview: the toggle is hidden and the demo defaults to chat. Captures the prompt for the
    // duck_ai_demo step.
    private fun customAiInputScreenPreviewStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW,
            pixelName = pixelName,
            precondition = {
                withContext(dispatchers.io()) {
                    androidBrowserConfigFeature.singleTabFireDialog().isEnabled()
                }
            },
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false) },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.InputDemoQuerySubmitted -> {
                        if (event.isChat) {
                            onboardingStore.setChatOnboardingVariant()
                        } else {
                            onboardingStore.setSearchOnboardingVariant()
                        }
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.TryASearchClicked(fromSuggestion = event.fromSuggestion, isChat = event.isChat),
                        )
                        ctx.pendingDuckAiPrompt = event.query
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun duckAiDemoStep(ctx: NewUserOnboardingPlanContext) = NewUserBrowserActivityStep(
        id = NewUserOnboardingStepIds.DUCK_AI_DEMO,
        pixelName = null,
        precondition = {
            withContext(dispatchers.io()) {
                androidBrowserConfigFeature.singleTabFireDialog().isEnabled()
            }
        },
        resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo(prompt = ctx.pendingDuckAiPrompt.orEmpty()) },
        transition = { event ->
            when {
                event is NewUserOnboardingEvent.DuckAiFireCompleted -> Advance
                else -> Stay
            }
        },
    )

    private fun skipOnboardingOptionStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SKIP_ONBOARDING
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.SKIP_ONBOARDING_OPTION,
            pixelName = pixelName,
            resolveDialog = { NewUserOnboardingActivityDialog.SkipNewUserOnboardingOption },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.SkipConfirmed -> {
                        pixel.fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        duckChat.setInputScreenUserSetting(true)
                        AbortPlan
                    }

                    is NewUserOnboardingEvent.ResumeRequested -> {
                        pixel.fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = false))
                        ReturnAndAdvance
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun quickSetupStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_QUICK_SETUP
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.QUICK_SETUP,
            pixelName = pixelName,
            resolveDialog = {
                val (isDefault, hasWidget) = withContext(dispatchers.io()) {
                    defaultBrowserDetector.isDefaultBrowser() to widgetCapabilities.hasInstalledWidgets
                }
                NewUserOnboardingActivityDialog.QuickSetup(
                    showSplitOption = isSplitOmnibarEnabled(),
                    hideSetDefaultBrowserRow = isDefault,
                    hideAddWidgetRow = hasWidget,
                    isReinstallUser = ctx.isReinstall,
                )
            },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.QuickSetupConfirmed -> {
                        val resolved = resolveOmnibarType(event.type)
                        settingsDataStore.omnibarType = resolved
                        applyInputModeSelection(ctx, event.withAi, fireTelemetry = false)
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.QuickSetupClicked(
                                addressBarPosition = resolved,
                                inputScreenSelected = event.withAi,
                            ),
                        )
                        AbortPlan
                    }
                    else -> Stay
                }
            },
        )
    }

    private suspend fun skipFork(
        skipPlan: LinearOnboardingPlan,
        quickSetupPlan: LinearOnboardingPlan,
    ): LinearOnboardingTransition =
        if (onboardingQuickSetupExperimentManager.enroll() == QuickSetupExperimentVariant.TREATMENT) {
            SwitchTo(quickSetupPlan)
        } else {
            SwitchTo(skipPlan)
        }

    private suspend fun applyInputModeSelection(
        ctx: NewUserOnboardingPlanContext,
        withAi: Boolean,
        fireTelemetry: Boolean,
    ) {
        if (fireTelemetry) {
            if (withAi) {
                pixel.fire(PREONBOARDING_AICHAT_SELECTED)
                inputScreenOnboardingWideEvent.onInputScreenEnabledDuringOnboarding(reinstallUser = ctx.isReinstall)
            } else {
                pixel.fire(PREONBOARDING_SEARCH_ONLY_SELECTED)
            }
        }
        duckChat.setCosmeticInputScreenUserSetting(withAi)
        onboardingStore.storeInputScreenSelection(withAi)
    }

    private fun fireAddressBarPositionPixel(resolved: OmnibarType) {
        when (resolved) {
            OmnibarType.SINGLE_BOTTOM -> pixel.fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SPLIT -> pixel.fire(PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE)
            OmnibarType.SINGLE_TOP -> Unit
        }
    }

    private suspend fun resolveOmnibarType(selected: OmnibarType): OmnibarType =
        if (selected == OmnibarType.SPLIT && !isSplitOmnibarEnabled()) OmnibarType.SINGLE_TOP else selected

    private suspend fun isSplitOmnibarEnabled(): Boolean =
        withContext(dispatchers.io()) {
            androidBrowserConfigFeature.splitOmnibar().isEnabled() &&
                androidBrowserConfigFeature.splitOmnibarWelcomePage().isEnabled()
        }

    private enum class FirstDialog { SYNC_RESTORE, REINSTALL, INITIAL }

    companion object {

        const val ROOT_PLAN_ID = "new-user_onboarding"
        const val SKIP_PLAN_ID = "new-user_skip"
        const val QUICK_SETUP_PLAN_ID = "new-user_quick-setup"

        private const val BLOCK_STORE_TIMEOUT_MS = 3_000L
    }
}
