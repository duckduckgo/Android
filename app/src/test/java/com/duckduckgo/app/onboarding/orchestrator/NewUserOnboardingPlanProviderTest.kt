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
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant
import com.duckduckgo.app.onboarding.SegmentedOnboardingExperimentManager.SegmentedOnboardingExperimentVariant
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Routing tests: which flow's plan [NewUserOnboardingPlanProvider.buildRootPlan] resolves to, and the
 * enrolment ordering between the experiments. Behaviour within each flow is covered by the per-builder
 * tests ([DefaultOnboardingPlanBuilderTest], [CustomAiOnboardingPlanBuilderTest],
 * [SegmentedOnboardingPlanBuilderTest]).
 */
class NewUserOnboardingPlanProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val h = NewUserOnboardingPlanHarness(coroutineRule.testDispatcherProvider)

    private val syncAutoRestore = h.syncAutoRestore
    private val appBuildConfig = h.appBuildConfig
    private val pixel = h.pixel
    private val customAiOnboardingStore = h.customAiOnboardingStore
    private val customAiOnboardingResolver = h.customAiOnboardingResolver
    private val duckAiOnboardingDemo = h.duckAiOnboardingDemo
    private val homeScreenPromptsExperiment = h.homeScreenPromptsExperiment
    private val segmentedOnboardingExperiment = h.segmentedOnboardingExperiment

    private val provider = h.provider()
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    private suspend fun start() {
        orchestrator.startPlan(provider.buildRootPlan(onCompleted = {}, onSkipped = {}))
    }

    private suspend fun builtStepIds(): List<String> =
        provider.buildRootPlan(onCompleted = {}, onSkipped = {}).steps.map { it.id }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
    }

    @Test
    fun `when custom ai path wins then builds the custom ai plan and the segmented experiment is never enrolled`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)

        val ids = builtStepIds()

        assertTrue(ids.contains(NewUserOnboardingStepIds.AI_COMPARISON_CHART))
        verify(segmentedOnboardingExperiment, never()).enroll()
    }

    @Test
    fun `when enrolled in the home screen prompts experiment then the segmented experiment is never enrolled`() = runTest {
        whenever(homeScreenPromptsExperiment.enroll()).thenReturn(OnboardingPromptExperimentVariant.CONTROL)
        whenever(segmentedOnboardingExperiment.enroll()).thenReturn(SegmentedOnboardingExperimentVariant.TREATMENT)

        val ids = builtStepIds()

        assertTrue(ids.contains(NewUserOnboardingStepIds.COMPARISON_CHART))
        assertFalse(ids.contains(NewUserOnboardingStepIds.DOWNLOAD_REASON))
        verify(segmentedOnboardingExperiment, never()).enroll()
    }

    @Test
    fun `when enrolled in the segmented treatment then builds the segmented plan`() = runTest {
        whenever(homeScreenPromptsExperiment.enroll()).thenReturn(null)
        whenever(segmentedOnboardingExperiment.enroll()).thenReturn(SegmentedOnboardingExperimentVariant.TREATMENT)

        val ids = builtStepIds()

        assertTrue(ids.contains(NewUserOnboardingStepIds.DOWNLOAD_REASON))
        assertFalse(ids.contains(NewUserOnboardingStepIds.COMPARISON_CHART))
    }

    @Test
    fun `when the segmented control variant then builds the default plan`() = runTest {
        whenever(homeScreenPromptsExperiment.enroll()).thenReturn(null)
        whenever(segmentedOnboardingExperiment.enroll()).thenReturn(SegmentedOnboardingExperimentVariant.CONTROL)

        val ids = builtStepIds()

        assertTrue(ids.contains(NewUserOnboardingStepIds.COMPARISON_CHART))
        assertFalse(ids.contains(NewUserOnboardingStepIds.DOWNLOAD_REASON))
    }

    @Test
    fun whenReinstallThenNotEnrolledInHomeScreenPromptsExperiment() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(homeScreenPromptsExperiment.enroll()).thenReturn(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY)

        val ids = builtStepIds()

        verify(homeScreenPromptsExperiment, never()).enroll()
        assertFalse(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_WIDGET))
    }

    @Test
    fun `when default path then does not fire plan started pixel`() = runTest {
        start()
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())
    }

    @Test
    fun `when default onboarding completed then does not arm open input on duck ai tab`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = false))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore, never()).setOpenInputOnDuckAiTab()
        verify(duckAiOnboardingDemo, never()).arm()
    }

    @Test
    fun `when can restore then sync restore shown and reinstall still evaluated for side effect`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.SYNC_RESTORE)
        // isAppReinstall must run even when Sync Restore wins (side-effecting): once eagerly to gate
        // enrollment in the home-screen-prompts experiment, once via the first-dialog memo.
        verify(appBuildConfig, times(2)).isAppReinstall()
        orchestrator.onEvent(NewUserOnboardingEvent.RestoreRequested)
        verify(pixel).fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
        verify(syncAutoRestore).restoreSyncAccount()
        // Advances past the mutually-exclusive reinstall/initial steps to comparison chart.
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        // The custom-AI "returning sync user ignored" pixel must not leak into the default plan
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
    }
}
