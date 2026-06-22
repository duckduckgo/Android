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
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
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

    private val regularSelectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val fireSelectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val regularTabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(regularSelectedTabFlow)
    }
    private val fireTabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(fireSelectedTabFlow)
    }
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = when (mode) {
            BrowserMode.REGULAR -> regularTabRepository
            BrowserMode.FIRE -> fireTabRepository
        }
    }
    private val currentModeFlow = MutableStateFlow(BrowserMode.REGULAR)
    private val browserModeStateHolder: BrowserModeStateHolder = mock<BrowserModeStateHolder>().also {
        whenever(it.currentMode).thenReturn(currentModeFlow)
    }

    private val testee = RealNativeInputStateStore(
        dagger.Lazy { tabRepositoryProvider },
        browserModeStateHolder,
    )

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

        assertEquals(NativeInputState.zero(), state)
    }

    @Test
    fun whenPublishCalledThenStateForTabReturnsPublishedValue() {
        val published = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
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
            ),
        )

        testee.clearTab("tab-a")

        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-a").value)
    }

    @Test
    fun whenClearTabCalledThenOtherTabsAreUntouched() {
        val stateB = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
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
            ),
        )
        testee.publish(
            "tab-b",
            NativeInputState(
                inputMode = InputMode.SEARCH_ONLY,
                inputContext = InputContext.DUCK_AI,
            ),
        )

        testee.clearAll()

        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-a").value)
        assertEquals(NativeInputState.zero(), testee.stateForTab("tab-b").value)
    }

    @Test
    fun whenSelectedTabPublishedThenStateEmitsItsValue() = runTest {
        val published = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
        )
        testee.publish("tab-a", published)
        regularSelectedTabFlow.value = tabEntity("tab-a")

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
        )
        val stateB = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
        )
        testee.publish("tab-a", stateA)
        testee.publish("tab-b", stateB)
        regularSelectedTabFlow.value = tabEntity("tab-a")

        testee.state.test {
            assertEquals(stateA, awaitItem())

            regularSelectedTabFlow.value = tabEntity("tab-b")

            assertEquals(stateB, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabsStateUpdatedThenStateReEmits() = runTest {
        val initial = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
        )
        testee.publish("tab-a", initial)
        regularSelectedTabFlow.value = tabEntity("tab-a")

        testee.state.test {
            assertEquals(initial, awaitItem())

            testee.update("tab-a") { it.copy(inputMode = InputMode.SEARCH_ONLY) }

            assertEquals(initial.copy(inputMode = InputMode.SEARCH_ONLY), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSelectedTabIsNullThenStateDoesNotEmit() = runTest {
        regularSelectedTabFlow.value = null

        testee.state.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenModeIsFireThenStateFollowsFireModeSelectedTab() = runTest {
        val regularState = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
        )
        val fireState = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
        )
        testee.publish("regular-tab", regularState)
        testee.publish("fire-tab", fireState)
        regularSelectedTabFlow.value = tabEntity("regular-tab")
        fireSelectedTabFlow.value = tabEntity("fire-tab")
        currentModeFlow.value = BrowserMode.FIRE

        testee.state.test {
            assertEquals(fireState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenModeSwitchesToFireThenStateReEmitsForFireSelectedTab() = runTest {
        val regularState = NativeInputState(
            inputMode = InputMode.SEARCH_AND_DUCK_AI,
            inputContext = InputContext.BROWSER,
        )
        val fireState = NativeInputState(
            inputMode = InputMode.SEARCH_ONLY,
            inputContext = InputContext.DUCK_AI,
        )
        testee.publish("regular-tab", regularState)
        testee.publish("fire-tab", fireState)
        regularSelectedTabFlow.value = tabEntity("regular-tab")
        fireSelectedTabFlow.value = tabEntity("fire-tab")

        testee.state.test {
            assertEquals(regularState, awaitItem())

            currentModeFlow.value = BrowserMode.FIRE

            assertEquals(fireState, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun tabEntity(tabId: String): TabEntity = TabEntity(tabId = tabId, position = 0)
}
