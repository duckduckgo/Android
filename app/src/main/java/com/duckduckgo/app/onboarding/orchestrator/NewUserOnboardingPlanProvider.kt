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

import androidx.annotation.StringRes
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingResolver
import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.OnboardingInputScreenLaunchTarget
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.OnboardingPreferenceCatalog
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager.SegmentedOnboardingExperimentVariant
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.SegmentedOnboardingPath
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.onboarding.ui.page.configdriven.DownloadReasonSelection
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SEARCH_ONLY_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SPLIT_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.feature.toggles.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.ActivePluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingPlanId
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.AbortPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin
import com.duckduckgo.sync.api.SyncAutoRestore
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
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
    private val onboardingPixelSender: OnboardingPixelSender,
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val widgetCapabilities: WidgetCapabilities,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
    private val dismissedCtaDao: DismissedCtaDao,
    private val onboardingInputScreenLaunchTarget: OnboardingInputScreenLaunchTarget,
    private val customAiOnboardingResolver: CustomAiOnboardingResolver,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
    private val onboardingPromptsExperimentManager: OnboardingPromptsExperimentManager,
    private val segmentedOnboardingExperimentManager: SegmentedOnboardingExperimentManager,
    private val onboardingPreferenceCatalog: OnboardingPreferenceCatalog,
    private val singleChoiceDataPlugins: ActivePluginPoint<OnboardingSingleChoiceDataPlugin>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) {

    suspend fun buildRootPlan(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val ctx = NewUserOnboardingPlanContext()
        // Side-effecting (creates the DDG downloads dir, persists reinstall state) and must always run
        ctx.isReinstall = appBuildConfig.isAppReinstall()

        return if (customAiOnboardingResolver.resolve()) {
            // in custom AI onboarding path, the input toggle is enabled by default
            duckChat.setCosmeticInputScreenUserSetting(enabled = true)
            onboardingStore.storeInputScreenSelection(selected = true)

            // prepare in-context CTAs
            duckAiOnboardingDemo.arm()

            pixel.fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())

            buildCustomAiPlan(ctx, onCompleted, onSkipped)
        } else {
            val onboardingPromptExperimentVariant = if (ctx.isReinstall) {
                null
            } else {
                onboardingPromptsExperimentManager.enroll()
            }
            when {
                onboardingPromptExperimentVariant != null -> buildDefaultPlan(ctx, onCompleted, onSkipped, onboardingPromptExperimentVariant)
                segmentedOnboardingExperimentManager.enroll() == SegmentedOnboardingExperimentVariant.TREATMENT ->
                    buildSegmentedPlan(ctx, onCompleted, onSkipped)
                else -> buildDefaultPlan(ctx, onCompleted, onSkipped)
            }
        }
    }

    private suspend fun buildDefaultPlan(
        ctx: NewUserOnboardingPlanContext,
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
        onboardingPromptExperimentVariant: OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant? = null,
    ): LinearOnboardingPlan {
        // SuspendMemos evaluate the inner lambda lazily, on first access, and store the result in-memory for subsequent access
        val firstDialog = SuspendMemo { resolveFirstDialog(ctx.isReinstall) }
        val duckAiEnabled = SuspendMemo { duckAiOnboardingAvailability.isDuckAiOnboardingEnabled() }

        val quickSetupPlan = quickSetupPlan(ctx)

        val showDock = onboardingPromptExperimentVariant ==
            OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY ||
            onboardingPromptExperimentVariant == OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET
        val variantAllowsWidget = onboardingPromptExperimentVariant ==
            OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY ||
            onboardingPromptExperimentVariant == OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET
        val showWidget = variantAllowsWidget && withContext(dispatchers.io()) { !widgetCapabilities.hasInstalledWidgets }

        return rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = buildList {
                add(introAnimationStep())
                add(notificationPermissionStep())
                add(syncRestoreStep(firstDialog, quickSetupPlan))
                add(initialReinstallUserStep(firstDialog, quickSetupPlan))
                add(initialStep(firstDialog))
                add(comparisonChartStep())
                add(defaultBrowserPromptStep())
                if (showDock) {
                    add(addToDockStep())
                }
                if (showWidget) {
                    add(widgetPromptStep(ctx))
                    add(addWidgetStep(ctx))
                }
                add(addressBarPositionStep())
                add(inputScreenStep(ctx))
                add(
                    inputScreenPreviewStep(
                        ctx = ctx,
                        isSearchDefault = true,
                        showModeToggle = { ctx.inputModeWasAi && duckAiEnabled() },
                        shownOnlyWithModeToggle = true,
                    ),
                )
            },
        )
    }

    private fun buildCustomAiPlan(
        ctx: NewUserOnboardingPlanContext,
        rootOnCompleted: suspend () -> Unit,
        rootOnSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val firstDialog = SuspendMemo { resolveFirstDialog(ctx.isReinstall) }

        val quickSetupPlan = quickSetupPlan(ctx, forceWithAiInput = true)

        val dismissDuckAiFireCta = suspend {
            // End-of-plan dismissal for Duck AI Fire CTA — deferred to here (vs. on user interaction)
            // so the CTA survives an app kill and re-runs correctly on next launch, if linear onboarding wasn't finished yet.
            withContext(dispatchers.io()) {
                dismissedCtaDao.insert(DismissedCta(CtaId.DAX_DUCK_AI_FIRE_BUTTON))
            }
        }
        val markInputToLaunchOnChat = {
            // The custom-AI flow always finishes on the Duck.ai (chat) tab
            onboardingInputScreenLaunchTarget.setOpenOnDuckAi()
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
                initialReinstallUserStep(firstDialog, quickSetupPlan, isCustomAiPlan = true),
                initialStep(firstDialog),
                aiComparisonChartStep(),
                inputScreenPreviewStep(
                    ctx = ctx,
                    isSearchDefault = false,
                    showsStepIndicator = true,
                    handsPromptToDemoStep = true,
                ),
                duckAiDemoStep(ctx),
                comparisonChartStep(),
                defaultBrowserPromptStep(),
                addressBarPositionStep(),
            ),
        )
    }

    private suspend fun buildSegmentedPlan(
        ctx: NewUserOnboardingPlanContext,
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        val firstDialog = SuspendMemo { FirstDialog.INITIAL }
        val modelProviderChoice = singleChoiceDataPlugin(OnboardingSingleChoiceDataPlugin.Id.DuckAiModelProvider)
        val togglePositionChoice = singleChoiceDataPlugin(OnboardingSingleChoiceDataPlugin.Id.DuckAiNewTabTogglePosition)
        val duckAiStateChoice = singleChoiceDataPlugin(OnboardingSingleChoiceDataPlugin.Id.DuckAiState)

        // Warms the options up while the user is still several screens away from the step that renders them.
        appCoroutineScope.launch(dispatchers.io()) { modelProviderChoice?.prefetch() }
        appCoroutineScope.launch(dispatchers.io()) { togglePositionChoice?.prefetch() }

        return rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = buildList {
                add(introAnimationStep())
                add(notificationPermissionStep())
                add(initialStep(firstDialog))
                add(downloadReasonStep(ctx, modelProviderChoice, togglePositionChoice, duckAiStateChoice))
            },
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
            onCompleted = {
                ctx.runFinalizers()
                onCompleted()
            },
            onSkipped = {
                ctx.runFinalizers()
                onSkipped()
            },
            result = { ctx.completionResult },
        )

    /** A plan pushed on top of the root plan through [LinearOnboardingTransition.SwitchTo]. */
    private fun sidePlan(id: LinearOnboardingPlanId, steps: List<LinearOnboardingStep>): LinearOnboardingPlan =
        LinearOnboardingPlan(id = id, steps = steps.firingShownPixels().abortingOnDevSkip())

    private fun quickSetupPlan(ctx: NewUserOnboardingPlanContext, forceWithAiInput: Boolean = false): LinearOnboardingPlan =
        sidePlan(QUICK_SETUP_PLAN_ID, listOf(quickSetupStep(ctx, forceWithAiInput)))

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

    private suspend fun resolveFirstDialog(isReinstall: Boolean): FirstDialog =
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
                        SwitchTo(quickSetupPlan)
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun initialReinstallUserStep(
        firstDialog: SuspendMemo<FirstDialog>,
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
                        SwitchTo(quickSetupPlan)
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

    private fun downloadReasonStep(
        ctx: NewUserOnboardingPlanContext,
        modelProviderChoice: OnboardingSingleChoiceDataPlugin?,
        togglePositionChoice: OnboardingSingleChoiceDataPlugin?,
        duckAiStateChoice: OnboardingSingleChoiceDataPlugin?,
    ): NewUserOnboardingActivityStep {
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.DOWNLOAD_REASON,
            pixelName = null,
            resolveDialog = { NewUserOnboardingActivityDialog.DownloadReason },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.DownloadReasonConfirmed -> {
                        logcat { "Download reason confirmed: ${event.selection}" }

                        val selection = event.selection ?: run {
                            logcat(priority = LogPriority.ERROR) {
                                "Download reason confirmed with null selection, this should not happen. Falling back to 'SEARCH'"
                            }
                            DownloadReasonSelection.SEARCH
                        }

                        when (selection) {
                            DownloadReasonSelection.SEARCH -> SwitchTo(segmentedSearchPlan(ctx))
                            DownloadReasonSelection.AI_CHAT -> SwitchTo(segmentedAiPlan(ctx, modelProviderChoice, togglePositionChoice))
                            DownloadReasonSelection.NO_AI -> SwitchTo(segmentedNoAiPlan(ctx, duckAiStateChoice))
                            DownloadReasonSelection.BLOCK_ADS -> SwitchTo(segmentedBlockAdsPlan(ctx))
                        }
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun segmentedSearchPlan(ctx: NewUserOnboardingPlanContext): LinearOnboardingPlan {
        val duckAiEnabled = SuspendMemo { duckAiOnboardingAvailability.isDuckAiOnboardingEnabled() }
        onboardingStore.setSegmentedOnboardingPath(SegmentedOnboardingPath.SEARCH)
        return sidePlan(
            id = SEGMENTED_SEARCH_PLAN_ID,
            steps = listOf(
                comparisonChartStep(NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedSearchPath)),
                defaultBrowserPromptStep(),
                preferenceSelectorStep(
                    ctx,
                    titleRes = R.string.searchPathPreferenceSelectorTitle,
                    listOf(
                        OnboardingPreference.SEARCH_HISTORY,
                        OnboardingPreference.SAFE_SEARCH,
                    ),
                ),
                inputScreenStep(ctx),
                addressBarPositionStep(),
                inputScreenPreviewStep(
                    ctx = ctx,
                    isSearchDefault = true,
                    showModeToggle = { ctx.inputModeWasAi && duckAiEnabled() },
                ),
            ),
        )
    }

    private suspend fun segmentedAiPlan(
        ctx: NewUserOnboardingPlanContext,
        modelProviderChoice: OnboardingSingleChoiceDataPlugin?,
        togglePositionChoice: OnboardingSingleChoiceDataPlugin?,
    ): LinearOnboardingPlan {
        onboardingStore.setSegmentedOnboardingPath(SegmentedOnboardingPath.AI)
        applyInputModeSelection(ctx, withAi = true, fireTelemetry = false)
        ctx.onFinish { onboardingInputScreenLaunchTarget.setOpenOnDuckAi() }
        return sidePlan(
            id = SEGMENTED_AI_PLAN_ID,
            steps = listOf(
                comparisonChartStep(NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedAiPath)),
                defaultBrowserPromptStep(),
                modelProviderStep(modelProviderChoice),
                togglePositionStep(togglePositionChoice),
                addressBarPositionStep(),
                inputScreenPreviewStep(ctx = ctx, isSearchDefault = false),
            ),
        )
    }

    private suspend fun segmentedNoAiPlan(
        ctx: NewUserOnboardingPlanContext,
        duckAiStateChoice: OnboardingSingleChoiceDataPlugin?,
    ): LinearOnboardingPlan {
        onboardingStore.setSegmentedOnboardingPath(null)
        applyInputModeSelection(ctx, withAi = false, fireTelemetry = false)
        return sidePlan(
            id = SEGMENTED_NO_AI_PLAN_ID,
            steps = listOf(
                comparisonChartStep(NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedNoAiPath)),
                defaultBrowserPromptStep(),
                preferenceSelectorStep(
                    ctx,
                    titleRes = R.string.noAiPathPreferenceSelectorTitle,
                    listOf(
                        OnboardingPreference.SEARCH_ASSIST,
                        OnboardingPreference.HIDE_AI_GENERATED_IMAGES,
                    ),
                ),
                duckAiStateStep(ctx, duckAiStateChoice),
                addressBarPositionStep(),
                inputScreenPreviewStep(ctx = ctx, isSearchDefault = true),
            ),
        )
    }

    private fun segmentedBlockAdsPlan(ctx: NewUserOnboardingPlanContext): LinearOnboardingPlan {
        val duckAiEnabled = SuspendMemo { duckAiOnboardingAvailability.isDuckAiOnboardingEnabled() }
        onboardingStore.setSegmentedOnboardingPath(null)
        return sidePlan(
            id = SEGMENTED_BLOCK_ADS_PLAN_ID,
            steps = listOf(
                comparisonChartStep(NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedBlockAdsPath)),
                defaultBrowserPromptStep(),
                preferenceSelectorStep(
                    ctx,
                    titleRes = R.string.blockAdsPathPreferenceSelectorTitle,
                    listOf(
                        OnboardingPreference.BLOCK_ADS,
                        OnboardingPreference.REJECT_OPTIONAL_COOKIES,
                        OnboardingPreference.ACCEPT_NON_OPT_OUT_COOKIES,
                    ),
                    caption = R.string.preferenceChangeInSettingsCaption,
                ),
                inputScreenStep(ctx),
                addressBarPositionStep(),
                inputScreenPreviewStep(
                    ctx = ctx,
                    isSearchDefault = true,
                    showModeToggle = { ctx.inputModeWasAi && duckAiEnabled() },
                    shownOnlyWithModeToggle = false,
                ),
            ),
        )
    }

    private fun preferenceSelectorStep(
        ctx: NewUserOnboardingPlanContext,
        @StringRes titleRes: Int,
        offered: List<OnboardingPreference>,
        @StringRes caption: Int? = null,
    ): NewUserOnboardingActivityStep {
        // Resolved on first access, so a preference's availability is evaluated when the run reaches this
        // step and not when the plan holding it was built.
        val rows = SuspendMemo { onboardingPreferenceCatalog.offer(offered) }
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.PREFERENCE_SELECTOR,
            pixelName = null,
            showsStepIndicator = true,
            precondition = { rows().isNotEmpty() },
            resolveDialog = {
                NewUserOnboardingActivityDialog.PreferenceSelector(
                    titleRes = titleRes,
                    rows = rows(),
                    caption = caption,
                )
            },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.PreferenceSelectorConfirmed -> {
                        // Committed only once the run ends, so preferences a path seeds its own way don't
                        // survive a process death into the path a restarted onboarding takes.
                        ctx.onFinish { onboardingPreferenceCatalog.apply(event.selections) }
                        Advance
                    }

                    else -> Stay
                }
            },
        )
    }

    private suspend fun singleChoiceDataPlugin(id: OnboardingSingleChoiceDataPlugin.Id): OnboardingSingleChoiceDataPlugin? =
        singleChoiceDataPlugins.getPlugins().firstOrNull { it.id == id }

    private fun modelProviderStep(plugin: OnboardingSingleChoiceDataPlugin?): NewUserOnboardingActivityStep {
        val options = SuspendMemo { plugin?.options().orEmpty() }
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.MODEL_PROVIDER,
            pixelName = null,
            showsStepIndicator = true,
            precondition = { options().size > 1 },
            resolveDialog = {
                NewUserOnboardingActivityDialog.SingleChoice(
                    title = R.string.aiPathModelChoiceTitle,
                    body = R.string.aiPathModelChoiceBody,
                    options(),
                )
            },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.SingleChoiceConfirmed -> {
                        logcat { "Model provider confirmed: ${event.option.id}" }
                        plugin?.apply(event.option)
                        Advance
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun togglePositionStep(plugin: OnboardingSingleChoiceDataPlugin?): NewUserOnboardingActivityStep {
        val options = SuspendMemo { plugin?.options().orEmpty() }
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.TOGGLE_POSITION,
            pixelName = null,
            showsStepIndicator = true,
            precondition = { options().size > 1 },
            resolveDialog = { NewUserOnboardingActivityDialog.TogglePosition(options()) },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.SingleChoiceConfirmed -> {
                        logcat { "Toggle position confirmed: ${event.option.id}" }
                        plugin?.apply(event.option)
                        Advance
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun duckAiStateStep(
        ctx: NewUserOnboardingPlanContext,
        plugin: OnboardingSingleChoiceDataPlugin?,
    ): NewUserOnboardingActivityStep {
        val options = SuspendMemo { plugin?.options().orEmpty() }
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.DUCK_AI_STATE,
            pixelName = null,
            showsStepIndicator = true,
            precondition = { options().size > 1 },
            resolveDialog = { NewUserOnboardingActivityDialog.DuckAiState(options()) },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.SingleChoiceConfirmed -> {
                        logcat { "Duck.ai state confirmed: ${event.option.id}" }
                        // Committed only once the run ends, so a pick abandoned by a process death doesn't
                        // leak into the path a restarted onboarding takes.
                        ctx.onFinish { plugin?.apply(event.option) }
                        Advance
                    }

                    else -> Stay
                }
            },
        )
    }

    private fun comparisonChartStep(
        dialog: NewUserOnboardingActivityDialog = NewUserOnboardingActivityDialog.ComparisonChart,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SET_DEFAULT
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.COMPARISON_CHART,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { dialog },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.ContinueClicked -> {
                        val showDefaultBrowserDialog = defaultRoleBrowserDialog.shouldShowDialog()
                        pixel.fire(
                            PREONBOARDING_CHOOSE_BROWSER_PRESSED,
                            mapOf(PixelParameter.DEFAULT_BROWSER to (!showDefaultBrowserDialog).toString()),
                        )
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
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

    private fun addToDockStep(): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_ADD_TO_DOCK
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.ADD_TO_DOCK,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.AddToDock },
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

    private fun widgetPromptStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_WIDGET_PROMPT
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.WIDGET_PROMPT,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.WidgetPrompt },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.Presented -> {
                        onboardingStore.linearPlanWidgetPromptShown = true
                        Stay
                    }
                    is NewUserOnboardingEvent.AddWidgetRequested -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    is NewUserOnboardingEvent.WidgetPromptSkipped -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = false))
                        ctx.skipAddWidget = true
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun addWidgetStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        // No shown pixel of its own; the confirmed result belongs to the widget-prompt pixel
        // (shown on the widget_prompt page). Skipped when the user opted out or already has a widget.
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.ADD_WIDGET,
            pixelName = null,
            precondition = {
                !ctx.skipAddWidget && withContext(dispatchers.io()) { !widgetCapabilities.hasInstalledWidgets }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.AddWidget },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.AddWidgetFinished -> {
                        onboardingPixelSender.fire(
                            OnboardingPixelName.ONBOARDING_WIDGET_PROMPT,
                            OnboardingPixelAction.WidgetConfirmed(added = event.widgetAdded),
                        )
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

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

    /**
     * Preview of the input screen the user just configured. [isSearchDefault] picks the pre-selected mode and
     * [showModeToggle] resolves whether the search/Duck.ai toggle is offered; the title follows from the two.
     * [shownOnlyWithModeToggle] skips the step entirely when the toggle wouldn't show.
     */
    private fun inputScreenPreviewStep(
        ctx: NewUserOnboardingPlanContext,
        isSearchDefault: Boolean,
        showModeToggle: suspend () -> Boolean = { false },
        shownOnlyWithModeToggle: Boolean = false,
        showsStepIndicator: Boolean = false,
        // The custom-AI plan hands the prompt to its duck_ai_demo step. Paths with no demo step after the
        // preview finish onboarding on the prompt instead, so it isn't dropped.
        handsPromptToDemoStep: Boolean = false,
    ): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW,
            pixelName = pixelName,
            showsStepIndicator = showsStepIndicator,
            precondition = { !shownOnlyWithModeToggle || showModeToggle() },
            resolveDialog = {
                val modeToggleShown = showModeToggle()
                NewUserOnboardingActivityDialog.InputScreenPreview(
                    isSearchDefault = isSearchDefault,
                    showModeToggle = modeToggleShown,
                    titleRes = when {
                        modeToggleShown -> R.string.preOnboardingInputModeDemoTitle
                        !isSearchDefault -> R.string.preOnboardingInputModeDemoTitleCustomAi
                        else -> R.string.searchPathInputPreviewTitle
                    },
                )
            },
            transition = { event ->
                when (event) {
                    is NewUserOnboardingEvent.InputDemoQuerySubmitted -> {
                        if (event.isChat) {
                            onboardingPixelSender.chatBranchSelected()
                        } else {
                            onboardingPixelSender.searchBranchSelected()
                        }
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.TryInputClicked(fromSuggestion = event.fromSuggestion, isChat = event.isChat),
                        )
                        if (handsPromptToDemoStep) {
                            ctx.pendingDuckAiPrompt = event.query
                        } else {
                            ctx.completionResult = if (event.isChat) {
                                NewUserOnboardingResult.LaunchChat(prompt = event.query)
                            } else {
                                NewUserOnboardingResult.LaunchSearch(query = event.query)
                            }
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
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun duckAiDemoStep(ctx: NewUserOnboardingPlanContext): NewUserBrowserActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_FIRE_BUTTON
        return NewUserBrowserActivityStep(
            id = NewUserOnboardingStepIds.DUCK_AI_DEMO,
            pixelName = pixelName,
            resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo(prompt = ctx.pendingDuckAiPrompt.orEmpty()) },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.DuckAiFireCompleted -> {
                        onboardingPixelSender.fire(pixelName, OnboardingPixelAction.Clicked(engaged = true))
                        Advance
                    }
                    else -> Stay
                }
            },
        )
    }

    private fun quickSetupStep(ctx: NewUserOnboardingPlanContext, forceWithAiInput: Boolean): NewUserOnboardingActivityStep {
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
                    hideAddressBarRow = forceWithAiInput,
                    isReinstallUser = ctx.isReinstall,
                )
            },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.QuickSetupConfirmed -> {
                        val resolved = resolveOmnibarType(event.type)
                        settingsDataStore.omnibarType = resolved
                        if (forceWithAiInput) {
                            duckChat.setInputScreenUserSetting(true)
                        }
                        applyInputModeSelection(ctx, forceWithAiInput || event.withAi, fireTelemetry = false)
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
        const val QUICK_SETUP_PLAN_ID = "new-user_quick-setup"
        const val SEGMENTED_SEARCH_PLAN_ID = "new-user_segmented_search"
        const val SEGMENTED_AI_PLAN_ID = "new-user_segmented_ai"
        const val SEGMENTED_NO_AI_PLAN_ID = "new-user_segmented_no-ai"
        const val SEGMENTED_BLOCK_ADS_PLAN_ID = "new-user_segmented_block-ads"

        private const val BLOCK_STORE_TIMEOUT_MS = 3_000L
    }
}
