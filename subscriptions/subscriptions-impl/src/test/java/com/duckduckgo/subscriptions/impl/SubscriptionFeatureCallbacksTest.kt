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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.subscriptions.impl.wideevents.FreeTrialConversionWideEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionFeatureCallbacksTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val freeTrialConversionWideEvent: FreeTrialConversionWideEvent = mock()

    private val testee = SubscriptionFeatureCallbacks(
        freeTrialConversionWideEvent = freeTrialConversionWideEvent,
        coroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun `onVpnStarted calls onVpnActivatedSuccessfully`() = runTest {
        testee.onVpnStarted(coroutineRule.testScope)
        advanceUntilIdle()

        verify(freeTrialConversionWideEvent).onVpnActivatedSuccessfully()
    }

    @Test
    fun `onDuckAiRetentionAtbRefreshed when modelTier is paid tier then calls onDuckAiPaidPromptSubmitted`() = runTest {
        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v123-1",
            newAtb = "v123-2",
            metadata = mapOf("modelTier" to "plus"),
        )
        advanceUntilIdle()

        verify(freeTrialConversionWideEvent).onDuckAiPaidPromptSubmitted()
    }

    @Test
    fun `onDuckAiRetentionAtbRefreshed when modelTier is missing then does not call onDuckAiPaidPromptSubmitted`() = runTest {
        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v123-1",
            newAtb = "v123-2",
            metadata = emptyMap(),
        )
        advanceUntilIdle()

        verify(freeTrialConversionWideEvent, never()).onDuckAiPaidPromptSubmitted()
    }

    @Test
    fun `onDuckAiRetentionAtbRefreshed when modelTier is not a paid tier then does not call onDuckAiPaidPromptSubmitted`() = runTest {
        testee.onDuckAiRetentionAtbRefreshed(
            oldAtb = "v123-1",
            newAtb = "v123-2",
            metadata = mapOf("modelTier" to "free"),
        )
        advanceUntilIdle()

        verify(freeTrialConversionWideEvent, never()).onDuckAiPaidPromptSubmitted()
    }
}
