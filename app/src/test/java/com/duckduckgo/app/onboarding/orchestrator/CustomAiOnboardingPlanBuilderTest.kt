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

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_AI_INTRO
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_FIRE_BUTTON
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_QUICK_SETUP
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CustomAiOnboardingPlanBuilderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val h = NewUserOnboardingPlanHarness(coroutineRule.testDispatcherProvider)

    private val syncAutoRestore = h.syncAutoRestore
    private val appBuildConfig = h.appBuildConfig
    private val duckChat = h.duckChat
    private val onboardingPixelSender = h.onboardingPixelSender
    private val pixel = h.pixel
    private val customAiOnboardingStore = h.customAiOnboardingStore
    private val duckAiOnboardingDemo = h.duckAiOnboardingDemo

    private val builder = h.customAiPlanBuilder()
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    private suspend fun start() {
        orchestrator.startPlan(builder.build(rootOnCompleted = {}, rootOnSkipped = {}))
    }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
    }

    private fun assertStepProgress(current: Int, total: Int) {
        assertEquals(StepProgress(current = current, total = total), (orchestrator.state.value as InProgress).stepIndicatorProgress())
    }

    @Test
    fun `when onboarding path then custom ai plan walks to completed`() = runTest {
        start()

        // Custom-AI plan arms the in-context Duck.ai demo up front (in build).
        verify(duckAiOnboardingDemo).arm()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        assertStepProgress(current = 1, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        assertStepProgress(current = 2, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)

        val step = (orchestrator.state.value as InProgress).currentStep as NewUserBrowserActivityStep
        assertEquals(NewUserBrowserActivityAction.RunDuckAiOnboardingDemo("best privacy tips"), step.resolveAction())

        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        assertStepProgress(current = 3, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT)
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        assertStep(NewUserOnboardingStepIds.ADDRESS_BAR_POSITION)
        assertStepProgress(current = 4, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when onboarding path and reinstall then reinstall dialog replaces initial`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()

        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
    }

    @Test
    fun `when onboarding path then input screen preview is chat only`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)

        val state = orchestrator.state.value as InProgress
        val dialog = (state.currentStep as NewUserOnboardingActivityStep).resolveDialog() as NewUserOnboardingActivityDialog.InputScreenPreview
        // Step number is derived from the step's position in the plan, not carried on the dialog.
        assertEquals(StepProgress(current = 2, total = 4), state.stepIndicatorProgress())
        assertFalse(dialog.isSearchDefault)
    }

    @Test
    fun `when custom ai path and quick setup confirmed with ai then forces input screen user setting on`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))
        // Skipping Custom AI onboarding via quick setup must force the input screen user setting on.
        verify(duckChat).setInputScreenUserSetting(true)
        verify(onboardingPixelSender).fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SINGLE_TOP,
                inputScreenSelected = true,
            ),
        )
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when custom ai onboarding completed then arms open input on duck ai tab`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true, fromSuggestion = false))
        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when custom ai onboarding skipped then arms open input on duck ai tab`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))

        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when custom ai path then fires plan started pixel`() = runTest {
        start()
        verify(pixel).fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())
    }

    @Test
    fun `when custom ai path and can restore then shows reinstall dialog and fires returning sync user ignored pixel`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        // The custom-AI plan has no sync-restore step, so a restore-capable user gets the reinstall dialog instead.
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        verify(pixel).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
        // No restore is offered: the sync-restore step does not exist on this plan.
        verify(syncAutoRestore, never()).restoreSyncAccount()
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
    }

    @Test
    fun `when custom ai path and initial user then does not fire returning sync user ignored pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
    }

    @Test
    fun `when dev skip then aborts to skipped`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
    }

    // region Shown pixel tests

    @Test
    fun `when ai comparison chart step presented then fires AiIntroShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when custom ai input screen preview step presented then fires SearchChatToggleShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_SEARCH_CHAT_TOGGLE, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when duck ai demo step presented then fires AiChatShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Shown)
    }

    // endregion

    // region Clicked/confirmed pixel tests

    @Test
    fun `when ai comparison chart continue clicked then fires AiComparisonClicked`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        verify(onboardingPixelSender).fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Clicked(engaged = true))
    }

    @Test
    fun `when custom ai input screen preview demo submitted then sets chat variant and fires TryInputClicked`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        // Chat-only preview always records the chat branch, regardless of the submitted mode.
        verify(onboardingPixelSender).chatBranchSelected()
        verify(onboardingPixelSender).fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            OnboardingPixelAction.TryInputClicked(fromSuggestion = false, isChat = true),
        )
    }

    @Test
    fun `when duck ai demo fire completed then fires AiChatClicked`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)
        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        verify(onboardingPixelSender).fire(ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Clicked(engaged = true))
    }

    // endregion
}
