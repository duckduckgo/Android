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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.Duration

class DuckAiSelectionJourneyWideEventTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()

    private lateinit var testee: RealDuckAiSelectionJourneyWideEvent

    @Before
    fun setup() = runTest {
        whenever(wideEventClient.intervalStart(any(), any(), anyOrNull(), anyOrNull())).thenReturn(Result.success(Unit))
        whenever(wideEventClient.intervalEnd(any(), any())).thenReturn(Result.success(Duration.ZERO))
        whenever(wideEventClient.flowFinish(any(), any(), any())).thenReturn(Result.success(Unit))
        testee = RealDuckAiSelectionJourneyWideEvent(
            wideEventClient = wideEventClient,
            timeProvider = object : DuckChatContextualTimeProvider {
                override fun currentTimeMillis() = 0L
            },
            dispatchers = coroutineRule.testDispatcherProvider,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    private suspend fun attach(tabId: String, flowId: Long) {
        whenever(wideEventClient.flowStart(any(), anyOrNull(), any(), any(), any(), any())).thenReturn(Result.success(flowId))
        testee.onSelectionAttached(tabId, 1)
        idle()
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
