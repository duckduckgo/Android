/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.promptscoordinator.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import com.duckduckgo.promptscoordinator.api.PromptType
import com.duckduckgo.promptscoordinator.api.PromptsCoordinator
import com.duckduckgo.promptscoordinator.impl.store.ModalEvaluatorCompletionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ModalEvaluatorCoordinatorTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockCompletionStore: ModalEvaluatorCompletionStore = mock()
    private val mockPluginPoint: PluginPoint<ModalEvaluator> = mock()
    private val mockLifecycleOwner: LifecycleOwner = mock()
    private val mockPromptsCoordinator: PromptsCoordinator = mock()

    private lateinit var testee: ModalEvaluatorCoordinator

    @Before
    fun setUp() = runTest {
        // Default: prompts-coordinator kill-switch disabled → legacy 24h window behaviour.
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(false)
        testee = ModalEvaluatorCoordinator(
            appCoroutineScope = coroutinesTestRule.testScope,
            modalEvaluatorPluginPoint = mockPluginPoint,
            completionStore = mockCompletionStore,
            promptsCoordinator = mockPromptsCoordinator,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenOnResumeCalledAndBlockedBy24HourWindowThenNoEvaluatorsAreCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(true)
        val mockEvaluator = createMockEvaluator("test", 1)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockEvaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockEvaluator, never()).evaluate()
        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenOnResumeCalledAndNotBlockedThenEvaluatorsAreCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val mockEvaluator = createMockEvaluator("test", 1, ModalEvaluator.EvaluationResult.Skipped)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockEvaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockEvaluator).evaluate()
    }

    @Test
    fun whenEvaluatorShowsThenCompletionIsRecordedAndNoMoreEvaluatorsCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, wantsToShow())
        val evaluator2 = createMockEvaluator("second", 2, ModalEvaluator.EvaluationResult.Skipped)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2, never()).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenEvaluatorIsSkippedThenNextEvaluatorIsCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, ModalEvaluator.EvaluationResult.Skipped)
        val evaluator2 = createMockEvaluator("second", 2, wantsToShow())
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenShowFallsThroughThenNextEvaluatorIsCalledAndNoCompletionIsRecordedForIt() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, wantsToShow(shown = false))
        val evaluator2 = createMockEvaluator("second", 2, wantsToShow())
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenAllEvaluatorsAreSkippedThenNoCompletionIsRecorded() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, ModalEvaluator.EvaluationResult.Skipped)
        val evaluator2 = createMockEvaluator("second", 2, ModalEvaluator.EvaluationResult.Skipped)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2).evaluate()
        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenEvaluatorsHaveDifferentPrioritiesThenTheyAreEvaluatedInPriorityOrder() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val lowPriorityEvaluator = createMockEvaluator("low", 10, ModalEvaluator.EvaluationResult.Skipped)
        val highPriorityEvaluator = createMockEvaluator("high", 1, ModalEvaluator.EvaluationResult.Skipped)
        val midPriorityEvaluator = createMockEvaluator("mid", 5, wantsToShow())

        // Return in unsorted order
        whenever(mockPluginPoint.getPlugins()).thenReturn(
            listOf(lowPriorityEvaluator, midPriorityEvaluator, highPriorityEvaluator),
        )

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // High priority (1) should be evaluated first
        verify(highPriorityEvaluator).evaluate()
        // Mid priority (5) should be evaluated second and complete
        verify(midPriorityEvaluator).evaluate()
        // Low priority (10) should not be evaluated because mid completed
        verify(lowPriorityEvaluator, never()).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenNoEvaluatorsAvailableThenNoErrorOccurs() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        whenever(mockPluginPoint.getPlugins()).thenReturn(emptyList())

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenMultipleEvaluatorsHaveSamePriorityThenBothAreEvaluatedInOrder() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, ModalEvaluator.EvaluationResult.Skipped)
        val evaluator2 = createMockEvaluator("second", 1, wantsToShow())
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenOnResumeThenOnlyAppResumeEvaluatorsAreCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val appResumeEvaluator = createMockEvaluator("resume", 1, ModalEvaluator.EvaluationResult.Skipped, ModalTrigger.APP_RESUME)
        val ntpEvaluator = createMockEvaluator("ntp", 2, ModalEvaluator.EvaluationResult.Skipped, ModalTrigger.NTP_RENDER)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(appResumeEvaluator, ntpEvaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(appResumeEvaluator).evaluate()
        verify(ntpEvaluator, never()).evaluate()
    }

    @Test
    fun whenNewTabPageShownThenOnlyNtpRenderEvaluatorsAreCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val appResumeEvaluator = createMockEvaluator("resume", 1, ModalEvaluator.EvaluationResult.Skipped, ModalTrigger.APP_RESUME)
        val ntpEvaluator = createMockEvaluator("ntp", 2, ModalEvaluator.EvaluationResult.Skipped, ModalTrigger.NTP_RENDER)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(appResumeEvaluator, ntpEvaluator))

        testee.onNewTabPageShown()
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(ntpEvaluator).evaluate()
        verify(appResumeEvaluator, never()).evaluate()
    }

    @Test
    fun whenNewTabPageShownAndBlockedBy24HourWindowThenNoEvaluatorsAreCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(true)
        val ntpEvaluator = createMockEvaluator("ntp", 1, wantsToShow(), ModalTrigger.NTP_RENDER)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(ntpEvaluator))

        testee.onNewTabPageShown()
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(ntpEvaluator, never()).evaluate()
        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenPromptsCoordinatorEnabledAndClaimRefusedThenShowIsNotInvokedAndThePassStops() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(true)
        whenever(mockPromptsCoordinator.tryClaim(PromptType.MODAL)).thenReturn(false)
        var firstShown = false
        val evaluator1 = createMockEvaluator("first", 1, wantsToShow { firstShown = true })
        val evaluator2 = createMockEvaluator("second", 2, wantsToShow())
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // Deciding needs no claim: the evaluator runs, but nothing shows on a refused surface.
        verify(evaluator1).evaluate()
        assertFalse(firstShown)
        // A refusal refuses the whole pass: lower priority evaluators cannot show either.
        verify(evaluator2, never()).evaluate()
        verify(mockCompletionStore, never()).recordCompletion()
        // The internal 24h window must not be consulted while the prompts-coordinator owns gating.
        verify(mockCompletionStore, never()).isBlockedBy24HourWindow()
    }

    @Test
    fun whenPromptsCoordinatorEnabledAndClaimGrantedThenShowRunsAndClaimIsDone() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(true)
        whenever(mockPromptsCoordinator.tryClaim(PromptType.MODAL)).thenReturn(true)
        var shown = false
        val mockEvaluator = createMockEvaluator("test", 1, wantsToShow { shown = true })
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockEvaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockCompletionStore, never()).isBlockedBy24HourWindow()
        assertTrue(shown)
        // Timestamps keep recording regardless of the flag so kill-switch flips stay seamless.
        verify(mockCompletionStore).recordCompletion()
        // Showing is terminal, so the gap is stamped here rather than at dismissal.
        verify(mockPromptsCoordinator).onClaimDone(PromptType.MODAL)
        verify(mockPromptsCoordinator, never()).onClaimCancelled(PromptType.MODAL)
    }

    @Test
    fun whenAllEvaluatorsSkipThenNoClaimIsEverAttempted() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(true)
        val evaluator = createMockEvaluator("test", 1, ModalEvaluator.EvaluationResult.Skipped)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator).evaluate()
        // Nothing wanted to show, so the surface was never touched.
        verify(mockPromptsCoordinator, never()).tryClaim(PromptType.MODAL)
        verify(mockPromptsCoordinator, never()).onClaimCancelled(PromptType.MODAL)
        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenShowFallsThroughThenClaimIsCancelledAndNotDone() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(true)
        whenever(mockPromptsCoordinator.tryClaim(PromptType.MODAL)).thenReturn(true)
        val evaluator = createMockEvaluator("test", 1, wantsToShow(shown = false))
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPromptsCoordinator).onClaimCancelled(PromptType.MODAL)
        verify(mockPromptsCoordinator, never()).onClaimDone(PromptType.MODAL)
        verify(mockCompletionStore, never()).recordCompletion()
    }

    @Test
    fun whenPromptsCoordinatorDisabledThenClaimIsNeverAttempted() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(false)
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator = createMockEvaluator("test", 1, wantsToShow())
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(mockPromptsCoordinator, never()).tryClaim(PromptType.MODAL)
        verify(mockCompletionStore).isBlockedBy24HourWindow()
        verify(mockCompletionStore).recordCompletion()
    }

    @Test
    fun whenShowThrowsThenTheModalClaimIsStillReleased() = runTest {
        whenever(mockPromptsCoordinator.isEnabled()).thenReturn(true)
        whenever(mockPromptsCoordinator.tryClaim(PromptType.MODAL)).thenReturn(true)
        val throwing = createMockEvaluator(
            "throwing",
            1,
            ModalEvaluator.EvaluationResult.WantsToShow { throw IllegalStateException("plugin blew up") },
        )
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(throwing))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        // Show actions are third-party plugin code: a throw must not strand the surface, which would
        // silently block every prompt for the rest of the process.
        verify(mockPromptsCoordinator).onClaimCancelled(PromptType.MODAL)
        verify(mockPromptsCoordinator, never()).onClaimDone(PromptType.MODAL)
    }

    private fun wantsToShow(
        shown: Boolean = true,
        onShow: () -> Unit = {},
    ): ModalEvaluator.EvaluationResult.WantsToShow =
        ModalEvaluator.EvaluationResult.WantsToShow {
            onShow()
            shown
        }

    private suspend fun createMockEvaluator(
        id: String,
        priority: Int,
        result: ModalEvaluator.EvaluationResult = ModalEvaluator.EvaluationResult.Skipped,
        trigger: ModalTrigger = ModalTrigger.APP_RESUME,
    ): ModalEvaluator {
        return mock<ModalEvaluator>().apply {
            whenever(this.evaluatorId).thenReturn(id)
            whenever(this.priority).thenReturn(priority)
            whenever(this.trigger).thenReturn(trigger)
            whenever(this.evaluate()).thenReturn(result)
        }
    }
}
