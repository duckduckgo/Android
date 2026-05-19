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

package com.duckduckgo.duckchat.impl.nativeinput

import app.cash.turbine.test
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputContext
import com.duckduckgo.duckchat.api.nativeinput.NativeInputState.InputMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealNativeInputStateStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val selectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val tabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(selectedTabFlow)
    }
    private val lazyTabRepository: dagger.Lazy<TabRepository> = dagger.Lazy { tabRepository }

    private val testee = RealNativeInputStateStore(lazyTabRepository)

    @Test
    fun whenStateForTabCalledTwiceForSameTabThenSameFlowReturned() {
        val first = testee.stateForTab("tab-a")
        val second = testee.stateForTab("tab-a")

        assertSame(first, second)
    }

    @Test
    fun whenStateForTabCalledForDifferentTabsThenDifferentFlowsReturned() {
        val a = testee.stateForTab("tab-a")
        val b = testee.stateForTab("tab-b")

        assertNotSame(a, b)
    }

    @Test
    fun whenStateForTabCalledWithoutPriorPublishThenZeroStateReturned() {
        val state = testee.stateForTab("tab-a").value

        assertEquals(NativeInputState.zero("tab-a"), state)
    }

    @Test
    fun whenPublishCalledThenStateForTabReturnsPublishedValue() {
        val published = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
            tabId = "tab-a",
        )

        testee.publish("tab-a", published)

        assertEquals(published, testee.stateForTab("tab-a").value)
    }

    @Test
    fun whenPublishCalledForOneTabThenOtherTabStateIsUnchanged() {
        val initialB = testee.stateForTab("tab-b").value

        testee.publish(
            "tab-a",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.DUCK_AI,
                tabId = "tab-a",
            ),
        )

        assertEquals(initialB, testee.stateForTab("tab-b").value)
    }

    @Test
    fun whenUpdateCalledThenTransformAppliedToExistingState() {
        testee.publish(
            "tab-a",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.BROWSER,
                tabId = "tab-a",
            ),
        )

        testee.update("tab-a") { it.copy(inputContext = InputContext.DUCK_AI) }

        assertEquals(InputContext.DUCK_AI, testee.stateForTab("tab-a").value.inputContext)
        assertEquals(InputMode.SEARCH_ONLY, testee.stateForTab("tab-a").value.inputMode)
    }

    @Test
    fun whenUpdateCalledOnUnpublishedTabThenTransformAppliedToZeroState() {
        testee.update("tab-a") { it.copy(inputMode = InputMode.SEARCH_ONLY) }

        assertEquals(InputMode.SEARCH_ONLY, testee.stateForTab("tab-a").value.inputMode)
    }

    @Test
    fun whenClearTabCalledThenSubsequentStateForTabReturnsZero() {
        testee.publish(
            "tab-a",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.DUCK_AI,
                tabId = "tab-a",
            ),
        )

        testee.clearTab("tab-a")

        assertEquals(NativeInputState.zero("tab-a"), testee.stateForTab("tab-a").value)
    }

    @Test
    fun whenClearTabCalledThenOtherTabsAreUntouched() {
        val stateB = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
            tabId = "tab-b",
        )
        testee.publish("tab-b", stateB)

        testee.clearTab("tab-a")

        assertEquals(stateB, testee.stateForTab("tab-b").value)
    }

    @Test
    fun whenClearAllCalledThenAllTabsAreEvicted() {
        testee.publish(
            "tab-a",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.DUCK_AI,
                tabId = "tab-a",
            ),
        )
        testee.publish(
            "tab-b",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.DUCK_AI,
                tabId = "tab-b",
            ),
        )

        testee.clearAll()

        assertEquals(NativeInputState.zero("tab-a"), testee.stateForTab("tab-a").value)
        assertEquals(NativeInputState.zero("tab-b"), testee.stateForTab("tab-b").value)
    }

    @Test
    fun whenSelectedTabPublishedThenStateEmitsItsValue() = runTest {
        val published = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
            tabId = "tab-a",
        )
        testee.publish("tab-a", published)
        selectedTabFlow.value = tabEntity("tab-a")

        testee.state.test {
            assertEquals(published, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabChangesThenStateReEmitsForNewTab() = runTest {
        val stateA = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
            tabId = "tab-a",
        )
        val stateB = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
            tabId = "tab-b",
        )
        testee.publish("tab-a", stateA)
        testee.publish("tab-b", stateB)
        selectedTabFlow.value = tabEntity("tab-a")

        testee.state.test {
            assertEquals(stateA, awaitItem())

            selectedTabFlow.value = tabEntity("tab-b")

            assertEquals(stateB, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabsStateUpdatedThenStateReEmits() = runTest {
        val initial = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
            tabId = "tab-a",
        )
        testee.publish("tab-a", initial)
        selectedTabFlow.value = tabEntity("tab-a")

        testee.state.test {
            assertEquals(initial, awaitItem())

            testee.update("tab-a") { it.copy(inputMode = InputMode.SEARCH_ONLY) }

            assertEquals(initial.copy(inputMode = InputMode.SEARCH_ONLY), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabIsNullThenStateDoesNotEmit() = runTest {
        selectedTabFlow.value = null

        testee.state.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun tabEntity(tabId: String): TabEntity = TabEntity(tabId = tabId, position = 0)
}
