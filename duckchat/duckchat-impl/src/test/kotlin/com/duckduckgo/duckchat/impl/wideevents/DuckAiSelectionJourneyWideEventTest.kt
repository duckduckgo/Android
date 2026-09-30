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

package com.duckduckgo.duckchat.impl.wideevents

import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.contextual.DuckChatContextualTimeProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration

class DuckAiSelectionJourneyWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()
    private var now = 0L

    private lateinit var testee: RealDuckAiSelectionJourneyWideEvent

    @Before
    fun setup() = runTest {
        whenever(wideEventClient.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(Duration.ZERO))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        testee = RealDuckAiSelectionJourneyWideEvent(
            wideEventClient = wideEventClient,
            timeProvider = object : DuckChatContextualTimeProvider {
                override fun currentTimeMillis() = now
            },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private suspend fun attach(tabId: String, flowId: Long, count: Int = 1) {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(flowId))
        testee.onSelectionAttached(tabId, count)
        idle()
    }

    private suspend fun finishedWith(flowId: Long, status: FlowStatus, vararg expected: Pair<String, String>) {
        verify(wideEventClient).flowFinish(eq(flowId), eq(status), argThat { expected.all { (k, v) -> this[k] == v } })
    }

    @Test
    fun whenFirstSelectionAttachedThenFlowAndDurationIntervalStart() = runTest {
        attach("tab-A", flowId = 1L)

        verify(wideEventClient).flowStart(eq("duckai-selection-journey"), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient).intervalStart(eq(1L), eq("latency.journey_duration_ms_bucketed"), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenFurtherSelectionsAttachedThenSingleFlowTracksHighWaterMark() = runTest {
        attach("tab-A", flowId = 1L, count = 1)
        attach("tab-A", flowId = 99L, count = 3)
        testee.onSelectionAttached("tab-A", 2)
        idle()

        verify(wideEventClient, times(1)).flowStart(any(), anyOrNull(), any(), any(), any(), any())

        testee.onPromptSubmitted("tab-A")
        idle()

        finishedWith(1L, FlowStatus.Success, "selection.max_count_bucketed" to "3-5")
    }

    @Test
    fun whenSubmittedWithoutSuggestionThenActionIsPromptAndNoDismissal() = runTest {
        attach("tab-A", flowId = 1L)

        testee.onPromptSubmitted("tab-A")
        idle()

        verify(wideEventClient).intervalEnd(1L, "latency.journey_duration_ms_bucketed")
        finishedWith(
            1L,
            FlowStatus.Success,
            "outcome.terminal_reason" to "submitted",
            "submission.action" to "prompt",
            "interaction.dismissal_count_bucketed" to "0",
            "interaction.dismissed_before_submission" to "false",
            "interaction.saw_selection_suggestions" to "false",
        )
    }

    @Test
    fun whenSuggestionSelectedThenSubmissionActionIsReported() = runTest {
        attach("tab-A", flowId = 1L)
        testee.onSuggestionsViewed("tab-A")
        testee.onSuggestionSelected("tab-A", SelectionSubmissionAction.SUMMARIZE)

        testee.onPromptSubmitted("tab-A")
        idle()

        finishedWith(1L, FlowStatus.Success, "submission.action" to "summarize", "interaction.saw_selection_suggestions" to "true")
    }

    @Test
    fun whenSurfaceDismissedThenPendingSuggestionIsDroppedAndDismissalsCounted() = runTest {
        attach("tab-A", flowId = 1L)
        testee.onSuggestionSelected("tab-A", SelectionSubmissionAction.TRANSLATE)
        testee.onSurfaceDismissed("tab-A")
        testee.onSurfaceDismissed("tab-A")

        testee.onPromptSubmitted("tab-A")
        idle()

        finishedWith(
            1L,
            FlowStatus.Success,
            "submission.action" to "prompt",
            "interaction.dismissal_count_bucketed" to "2+",
            "interaction.dismissed_before_submission" to "true",
        )
    }

    @Test
    fun whenSelectionsRemainAfterRemovalThenJourneyContinues() = runTest {
        attach("tab-A", flowId = 1L, count = 2)

        testee.onSelectionRemoved("tab-A", 1)
        idle()

        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenAttachingAfterMaxDurationThenStaleJourneyExpiresAndNewOneStarts() = runTest {
        attach("tab-A", flowId = 1L)
        now = 300_001L

        attach("tab-A", flowId = 2L)

        finishedWith(1L, FlowStatus.Cancelled, "outcome.terminal_reason" to "session_expired")
        verify(wideEventClient).intervalStart(eq(2L), any(), anyOrNull(), anyOrNull())
    }

    @Test
    fun whenNoJourneyActiveThenEventsAreIgnored() = runTest {
        testee.onSurfaceDismissed("tab-A")
        testee.onSuggestionsViewed("tab-A")
        testee.onPromptSubmitted("tab-A")
        testee.onJourneyEnded("tab-A", SelectionTerminalReason.NEW_CHAT)
        testee.onSelectionRemoved("tab-A", 0)
        idle()

        verify(wideEventClient, never()).flowStart(any(), anyOrNull(), any(), any(), any(), any())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenFlowStartFailsThenNothingElseIsSent() = runTest {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.failure(RuntimeException()))
        testee.onSelectionAttached("tab-A", 1)
        testee.onPromptSubmitted("tab-A")
        idle()

        verify(wideEventClient, never()).intervalStart(any(), any(), anyOrNull(), anyOrNull())
        verify(wideEventClient, never()).flowFinish(any(), any(), any())
    }

    @Test
    fun whenSelectionsRemovedInOneTabThenOnlyThatTabsJourneyIsCancelled() = runTest {
        attach("tab-A", flowId = 1L)
        attach("tab-B", flowId = 2L)

        testee.onSelectionRemoved("tab-B", 0)
        idle()

        verify(wideEventClient).flowFinish(eq(2L), eq(FlowStatus.Cancelled), argThat { this["outcome.terminal_reason"] == "selections_removed" })
        verify(wideEventClient, never()).flowFinish(eq(1L), any(), any())

        testee.onPromptSubmitted("tab-A")
        idle()

        verify(wideEventClient).flowFinish(eq(1L), eq(FlowStatus.Success), argThat { this["outcome.terminal_reason"] == "submitted" })
    }

    @Test
    fun whenTabClosedThenJourneyEndsWithTabClosed() = runTest {
        attach("tab-A", flowId = 1L)

        testee.onJourneyEnded("tab-A", SelectionTerminalReason.TAB_CLOSED)
        idle()

        verify(wideEventClient).flowFinish(eq(1L), eq(FlowStatus.Cancelled), argThat { this["outcome.terminal_reason"] == "tab_closed" })
    }
}
