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

import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.pixels.OnboardingPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Advance
import com.duckduckgo.onboarding.api.LinearOnboardingTransition.Stay
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Composes the custom AI onboarding plan, the flow resolved by
 * [com.duckduckgo.app.onboarding.CustomAiOnboardingResolver].
 *
 * Each [build] creates a fresh [NewUserOnboardingPlanContext] and per-run [SuspendMemo]s, so a new
 * onboarding run never reads stale state.
 */
internal class CustomAiOnboardingPlanBuilder @Inject constructor(
    private val steps: NewUserOnboardingSteps,
    private val plans: NewUserOnboardingPlans,
    private val duckChat: DuckChat,
    private val onboardingStore: OnboardingStore,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
    private val customAiOnboardingStore: CustomAiOnboardingStore,
    private val dismissedCtaDao: DismissedCtaDao,
    private val onboardingPixelSender: OnboardingPixelSender,
    private val pixel: Pixel,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun build(
        rootOnCompleted: suspend () -> Unit,
        rootOnSkipped: suspend () -> Unit,
    ): LinearOnboardingPlan {
        // in custom AI onboarding path, the input toggle is enabled by default
        duckChat.setCosmeticInputScreenUserSetting(enabled = true)
        onboardingStore.storeInputScreenSelection(selected = true)

        // prepare in-context CTAs
        duckAiOnboardingDemo.arm()

        pixel.fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())

        val ctx = NewUserOnboardingPlanContext()
        val firstDialog = SuspendMemo { steps.resolveFirstDialog(ctx) }

        val quickSetupPlan = plans.quickSetupPlan(ctx, forceWithAiInput = true)

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

        return plans.rootPlan(
            ctx = ctx,
            onCompleted = onCompleted,
            onSkipped = onSkipped,
            steps = listOf(
                steps.introAnimationStep(withDuckAi = true),
                steps.notificationPermissionStep(),
                steps.initialReinstallUserStep(firstDialog, quickSetupPlan, isCustomAiPlan = true),
                steps.initialStep(firstDialog),
                aiComparisonChartStep(),
                customAiInputScreenPreviewStep(ctx),
                duckAiDemoStep(ctx),
                steps.comparisonChartStep(),
                steps.defaultBrowserPromptStep(),
                steps.addressBarPositionStep(),
            ),
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

    // Chat-only preview: the toggle is hidden and the demo defaults to chat. Captures the prompt for the
    // duck_ai_demo step.
    private fun customAiInputScreenPreviewStep(ctx: NewUserOnboardingPlanContext): NewUserOnboardingActivityStep {
        val pixelName = OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
        return NewUserOnboardingActivityStep(
            id = NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW,
            pixelName = pixelName,
            showsStepIndicator = true,
            resolveDialog = { NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false) },
            transition = { event ->
                when {
                    event is NewUserOnboardingEvent.InputDemoQuerySubmitted -> {
                        onboardingPixelSender.chatBranchSelected()
                        onboardingPixelSender.fire(
                            pixelName,
                            OnboardingPixelAction.TryInputClicked(fromSuggestion = event.fromSuggestion, isChat = event.isChat),
                        )
                        ctx.pendingDuckAiPrompt = event.query
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
}
