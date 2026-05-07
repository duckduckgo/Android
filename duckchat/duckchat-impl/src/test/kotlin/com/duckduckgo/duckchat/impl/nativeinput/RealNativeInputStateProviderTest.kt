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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.impl.ui.NativeInputState
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateDao
import com.duckduckgo.duckchat.store.impl.store.NativeInputTabStateEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealNativeInputStateProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val dao: NativeInputTabStateDao = mock()
    private lateinit var testee: RealNativeInputStateProvider

    @Before
    fun setUp() {
        whenever(dao.getTab(any())).thenReturn(null)
        testee = RealNativeInputStateProvider(
            dao = dao,
            appScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenDisplayedStateInitialThenZero() = runTest {
        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenStateForUnknownTabThenZero() = runTest {
        assertEquals(NativeInputState.zero(), testee.stateForTab("new-tab").value)
    }

    @Test
    fun whenSetActiveTabThenDisplayedStateUpdated() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)

        assertEquals(NativeInputState.InputMode.SEARCH_AND_DUCK_AI, testee.displayedState.value.inputMode)
        assertEquals(NativeInputState.InputContext.DUCK_AI, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenSetActiveTabWithPersistedModelThenStateMergesModelId() = runTest {
        whenever(dao.getTab("tab-1")).thenReturn(
            NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"),
        )
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        assertEquals("claude-3", testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenSetActiveTabWithNoPersistedDataThenSelectedModelIdIsNull() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        assertNull(testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateOnActiveTabThenDisplayedStateReflectsChange() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }

        assertEquals("gpt-4o", testee.displayedState.value.selectedModelId)
    }

    @Test
    fun whenUpdateOnActiveTabWithChangedModelIdThenDaoPersists() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        testee.update("tab-1") { copy(selectedModelId = "gpt-4o") }

        verify(dao).upsert(NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "gpt-4o"))
    }

    @Test
    fun whenUpdateWithUnchangedModelIdThenDaoNotCalled() = runTest {
        whenever(dao.getTab("tab-1")).thenReturn(
            NativeInputTabStateEntity(tabId = "tab-1", selectedModelId = "claude-3"),
        )
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)

        testee.update("tab-1") { copy(selectedModelId = "claude-3") }

        verify(dao, never()).upsert(any())
    }

    @Test
    fun whenUpdateOnUnknownTabThenNoOp() = runTest {
        testee.update("never-set-tab") { copy(selectedModelId = "model") }

        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearActiveTabThenDisplayedStateResetsToZero() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)
        testee.clearTab("tab-1")

        assertEquals(NativeInputState.zero(), testee.displayedState.value)
    }

    @Test
    fun whenClearActiveTabThenDaoDeletes() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)
        testee.clearTab("tab-1")

        verify(dao).delete("tab-1")
    }

    @Test
    fun whenClearNonActiveTabThenDisplayedStateUnchanged() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_AND_DUCK_AI,
            inputContext = NativeInputState.InputContext.DUCK_AI,
        )
        testee.setActiveTab("tab-1", structural)
        testee.setActiveTab("tab-2", structural.copy(inputContext = NativeInputState.InputContext.BROWSER))

        testee.clearTab("tab-1")

        assertEquals(NativeInputState.InputContext.BROWSER, testee.displayedState.value.inputContext)
    }

    @Test
    fun whenStateForTabAfterUpdateThenReturnsUpdatedState() = runTest {
        val structural = NativeInputState(
            inputMode = NativeInputState.InputMode.SEARCH_ONLY,
            inputContext = NativeInputState.InputContext.BROWSER,
        )
        testee.setActiveTab("tab-1", structural)
        testee.update("tab-1") { copy(selectedModelId = "llama-3") }

        assertEquals("llama-3", testee.stateForTab("tab-1").firstOrNull()?.selectedModelId)
    }
}
