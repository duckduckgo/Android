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
package com.duckduckgo.nextsteps.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.RemoteMessageModel
import com.duckduckgo.remote.messaging.api.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class NextStepsAppObserverTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val nextStepsA = aNextStepsMessage("a")
    private val nextStepsB = aNextStepsMessage("b")
    private val otherMessage = RemoteMessage(
        id = "survey",
        content = Content.Small(titleText = "title", descriptionText = "description"),
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = listOf(Surface.NEW_TAB_PAGE),
    )
    private val activeMessage = MutableStateFlow<RemoteMessage?>(nextStepsA)
    private val model: RemoteMessageModel = mock {
        whenever(it.observeActiveMessages()).thenReturn(activeMessage)
        whenever(it.getActiveMessage()).thenAnswer { activeMessage.value }
    }
    private val telemetry: NextStepsTelemetry = mock()

    private fun anObserver() = NextStepsAppObserver(
        model,
        telemetry,
        coroutineRule.testScope.backgroundScope,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `the message leaving the slot reports null`() = runTest {
        anObserver().onCreate(mock())
        advanceUntilIdle()

        activeMessage.value = null
        advanceUntilIdle()

        verify(telemetry).onActiveMessageChanged("a")
        verify(telemetry).onActiveMessageChanged(null)
    }

    @Test
    fun `another message taking the slot reports null`() = runTest {
        anObserver().onCreate(mock())
        advanceUntilIdle()

        activeMessage.value = otherMessage
        advanceUntilIdle()

        verify(telemetry).onActiveMessageChanged(null)
    }

    @Test
    fun `a different next steps message taking the slot reports its id`() = runTest {
        anObserver().onCreate(mock())
        advanceUntilIdle()

        activeMessage.value = nextStepsB
        advanceUntilIdle()

        verify(telemetry).onActiveMessageChanged("b")
    }

    @Test
    fun `changes between other messages report nothing`() = runTest {
        activeMessage.value = otherMessage
        anObserver().onCreate(mock())
        advanceUntilIdle()

        activeMessage.value = null
        advanceUntilIdle()
        activeMessage.value = otherMessage.copy(id = "promo")
        advanceUntilIdle()

        verify(telemetry, times(1)).onActiveMessageChanged(null)
    }

    @Test
    fun `the decision uses the live slot not the emission`() = runTest {
        anObserver().onCreate(mock())
        advanceUntilIdle()
        whenever(model.getActiveMessage()).thenReturn(nextStepsB)

        activeMessage.value = null
        advanceUntilIdle()

        verify(telemetry).onActiveMessageChanged("b")
    }

    @Test
    fun `an empty slot at start reports null once`() = runTest {
        activeMessage.value = null
        anObserver().onCreate(mock())
        advanceUntilIdle()

        verify(telemetry, times(1)).onActiveMessageChanged(null)
        verifyNoMoreInteractions(telemetry)
    }

    @Test
    fun `app resume is forwarded to telemetry`() = runTest {
        anObserver().onResume(mock())
        advanceUntilIdle()

        verify(telemetry).onAppResumed()
    }

    private fun aNextStepsMessage(id: String) = RemoteMessage(
        id = id,
        content = Content.ActionableItems(titleText = "Setup", listItems = emptyList()),
        matchingRules = emptyList(),
        exclusionRules = emptyList(),
        surfaces = listOf(Surface.NEW_TAB_PAGE),
    )
}
