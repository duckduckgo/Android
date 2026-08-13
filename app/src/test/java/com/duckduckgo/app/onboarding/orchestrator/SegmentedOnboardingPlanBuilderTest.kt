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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SegmentedOnboardingPlanBuilderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val h = NewUserOnboardingPlanHarness(coroutineRule.testDispatcherProvider)

    private val builder = h.segmentedPlanBuilder()
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    private suspend fun start() {
        orchestrator.startPlan(builder.build(onCompleted = {}, onSkipped = {}))
    }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
    }

    @Test
    fun `when segmented plan then reaches the download reason step`() = runTest {
        start()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.DOWNLOAD_REASON)
    }

    @Test
    fun `when dev skip then aborts to skipped`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
    }
}
