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

import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_SKIP_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.SwitchTo
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Composes the default new-user onboarding plan, covering the control experience and the
 * onboarding-prompts experiment arms.
 *
 * Each [build] creates a fresh [NewUserOnboardingPlanContext] and per-run [SuspendMemo]s, so a new
 * onboarding run never reads stale state.
 */
internal class DefaultOnboardingPlanBuilder @Inject constructor(
    private val steps: NewUserOnboardingSteps,
    private val plans: NewUserOnboardingPlans,
    private val syncAutoRestore: SyncAutoRestore,
    private val duckAiOnboardingAvailability: DuckAiOnboardingAvailability,
    private val onboardingStore: OnboardingStore,
    private val onboardingPixelSender: OnboardingPixelSender,
    private val widgetCapabilities: WidgetCapabilities,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun build(
        onCompleted: suspend () -> Unit,
        onSkipped: suspend () -> Unit,
        onboardingPromptExperimentVariant: OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant? = null,
    ): LinearOnboardingPlan {
        val ctx = NewUserOnboardingPlanContext()

        // SuspendMemos evaluate the inner lambda lazily, on first access, and store the result in-memory for subsequent access
        val firstDialog = SuspendMemo { steps.resolveFirstDialog(ctx) }
        val duckAiEnabled = SuspendMemo { duckAiOnboardingAvailability.isDuckAiOnboardingEnabled() }

        val quickSetupPlan = plans.quickSetupPlan(ctx)

        val showDock = onboardingPromptExperimentVariant ==
            OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY ||
            onboardingPromptExperimentVariant == OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET
        val variantAllowsWidget = onboardingPromptExperimentVariant ==
            OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY ||
            onboardingPromptExperimentVariant == OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET
        val showWidget = variantAllowsWidget && withContext(dispatchers.io()) { !widgetCapabilities.hasInstalledWidgets }

        return plans.rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = buildList {
                add(steps.introAnimationStep())
                add(steps.notificationPermissionStep())
                add(syncRestoreStep(firstDialog, quickSetupPlan))
                add(steps.initialReinstallUserStep(firstDialog, quickSetupPlan))
                add(steps.initialStep(firstDialog))
                add(steps.comparisonChartStep())
                add(steps.defaultBrowserPromptStep())
                if (showDock) {
                    add(addToDockStep())
                }
                if (showWidget) {
                    add(widgetPromptStep(ctx))
                    add(addWidgetStep(ctx))
                }
                add(steps.addressBarPositionStep())
                add(inputScreenStep(ctx))
                add(inputScreenPreviewStep(ctx, duckAiEnabled))
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
                        steps.applyInputModeSelection(ctx, event.withAi, fireTelemetry = true)
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
                            onboardingPixelSender.chatBranchSelected()
                        } else {
                            onboardingPixelSender.searchBranchSelected()
                        }
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.TryInputClicked(fromSuggestion = event.fromSuggestion, isChat = event.isChat),
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
}
