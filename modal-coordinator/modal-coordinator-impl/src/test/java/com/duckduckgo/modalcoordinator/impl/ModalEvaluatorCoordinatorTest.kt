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

package com.duckduckgo.modalcoordinator.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.modalcoordinator.impl.store.ModalEvaluatorCompletionStore
import kotlinx.coroutines.test.runTest
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

    private lateinit var testee: ModalEvaluatorCoordinator

    @Before
    fun setUp() {
        testee = ModalEvaluatorCoordinator(
            appCoroutineScope = coroutinesTestRule.testScope,
            modalEvaluatorPluginPoint = mockPluginPoint,
            completionStore = mockCompletionStore,
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
    fun whenEvaluatorCompletesWithActionThenCompletionIsRecordedAndNoMoreEvaluatorsCalled() = runTest {
        whenever(mockCompletionStore.isBlockedBy24HourWindow()).thenReturn(false)
        val evaluator1 = createMockEvaluator("first", 1, ModalEvaluator.EvaluationResult.ModalShown)
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
        val evaluator2 = createMockEvaluator("second", 2, ModalEvaluator.EvaluationResult.ModalShown)
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
        val midPriorityEvaluator = createMockEvaluator("mid", 5, ModalEvaluator.EvaluationResult.ModalShown)

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
        val evaluator2 = createMockEvaluator("second", 1, ModalEvaluator.EvaluationResult.ModalShown)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(evaluator1, evaluator2))

        testee.onResume(mockLifecycleOwner)
        coroutinesTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(evaluator1).evaluate()
        verify(evaluator2).evaluate()
        verify(mockCompletionStore).recordCompletion()
    }

    private suspend fun createMockEvaluator(
        id: String,
        priority: Int,
        result: ModalEvaluator.EvaluationResult = ModalEvaluator.EvaluationResult.Skipped,
    ): ModalEvaluator {
        return mock<ModalEvaluator>().apply {
            whenever(this.evaluatorId).thenReturn(id)
            whenever(this.priority).thenReturn(priority)
            whenever(this.evaluate()).thenReturn(result)
        }
    }
}
