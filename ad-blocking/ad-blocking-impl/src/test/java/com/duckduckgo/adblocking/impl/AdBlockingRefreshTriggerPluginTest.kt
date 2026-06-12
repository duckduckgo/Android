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

package com.duckduckgo.adblocking.impl

import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AdBlockingRefreshTriggerPluginTest {

    private val stateFlow = MutableStateFlow<AdBlockingState>(AdBlockingState.Enabled.UserEnabled)
    private val statusChecker: AdBlockingStatusChecker = mock {
        on { observeState() } doReturn stateFlow
    }
    private val plugin = AdBlockingRefreshTriggerPlugin(statusChecker)

    @Test
    fun whenSubscribedAndNoChangeThenDoesNotEmitForCurrentState() = runTest {
        plugin.observeRefreshRequests().test {
            expectNoEvents()
            cancel()
        }
    }

    @Test
    fun whenStateChangesThenEmits() = runTest {
        plugin.observeRefreshRequests().test {
            stateFlow.value = AdBlockingState.Disabled

            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStateChangesMultipleTimesThenEmitsForEachChange() = runTest {
        plugin.observeRefreshRequests().test {
            stateFlow.value = AdBlockingState.Disabled
            awaitItem()

            stateFlow.value = AdBlockingState.Enabled.UserEnabled
            awaitItem()

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenStateRepeatsSameValueThenDoesNotEmit() = runTest {
        plugin.observeRefreshRequests().test {
            stateFlow.value = AdBlockingState.Enabled.UserEnabled

            expectNoEvents()
            cancel()
        }
    }
}
