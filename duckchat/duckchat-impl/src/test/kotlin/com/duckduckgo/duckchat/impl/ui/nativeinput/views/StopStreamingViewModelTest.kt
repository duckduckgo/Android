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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class StopStreamingViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val providerStateFlow = MutableSharedFlow<NativeInputState>(replay = 1)

    private val nativeInputStateProvider: NativeInputStateProvider = mock<NativeInputStateProvider>().also {
        whenever(it.state).thenReturn(providerStateFlow)
    }

    private val testee = StopStreamingViewModel(nativeInputStateProvider)

    @Test
    fun whenStreamingAndDuckAiContextThenVisible() = runTest {
        providerStateFlow.emit(stateOf(InputContext.DUCK_AI, isChatStreaming = true))

        testee.isVisible.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStreamingAndDuckAiContextualContextThenVisible() = runTest {
        providerStateFlow.emit(stateOf(InputContext.DUCK_AI_CONTEXTUAL, isChatStreaming = true))

        testee.isVisible.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStreamingButBrowserContextThenNotVisible() = runTest {
        providerStateFlow.emit(stateOf(InputContext.BROWSER, isChatStreaming = true))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenNotStreamingThenNotVisible() = runTest {
        providerStateFlow.emit(stateOf(InputContext.DUCK_AI, isChatStreaming = false))

        testee.isVisible.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenContextChangesToBrowserWhileStreamingThenBecomesNotVisible() = runTest {
        providerStateFlow.emit(stateOf(InputContext.DUCK_AI, isChatStreaming = true))

        testee.isVisible.test {
            assertTrue(awaitItem())

            providerStateFlow.emit(stateOf(InputContext.BROWSER, isChatStreaming = true))

            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun stateOf(
        context: InputContext,
        isChatStreaming: Boolean,
    ): NativeInputState = NativeInputState(
        inputMode = InputMode.SEARCH_AND_DUCK_AI,
        inputContext = context,
        isChatStreaming = isChatStreaming,
    )
}
