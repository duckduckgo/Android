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

package com.duckduckgo.contentscopescripts.impl.features.pagecontext

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.impl.features.pagecontext.store.PageContextStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPageContextRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockStore: PageContextStore = mock()
    private val testScope = TestScope(coroutineRule.testDispatcher)

    private lateinit var testee: RealPageContextRepository

    @Before
    fun setup() = runTest {
        whenever(mockStore.getJsonData()).thenReturn(null)
        whenever(mockStore.insertJsonData(any())).thenReturn(true)
    }

    @Test
    fun whenMainProcessInitLoadsStoredValueIntoMemory() = runTest {
        val storedJson = """{"key":"value"}"""
        whenever(mockStore.getJsonData()).thenReturn(storedJson)

        testee = RealPageContextRepository(
            pageContextStore = mockStore,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = testScope,
            isMainProcess = true,
        )
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockStore).getJsonData()
        assertEquals(storedJson, testee.getJsonData())
    }

    @Test
    fun whenNotMainProcessInitKeepsEmptyJson() = runTest {
        whenever(mockStore.getJsonData()).thenReturn("""{"key":"value"}""")

        testee = RealPageContextRepository(
            pageContextStore = mockStore,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = testScope,
            isMainProcess = false,
        )
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RealPageContextRepository.EMPTY_JSON, testee.getJsonData())
    }

    @Test
    fun whenInsertJsonDataSucceedsThenMemoryAndStoreUpdated() = runTest {
        val newJson = """{"new":"json"}"""
        testee = RealPageContextRepository(
            pageContextStore = mockStore,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = testScope,
            isMainProcess = false,
        )

        testee.insertJsonData(newJson)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockStore).insertJsonData(newJson)
        assertEquals(newJson, testee.getJsonData())
    }

    @Test
    fun whenInsertJsonDataFailsThenMemoryNotUpdated() = runTest {
        val initialJson = """{"initial":"json"}"""
        whenever(mockStore.getJsonData()).thenReturn(initialJson)
        whenever(mockStore.insertJsonData(any())).thenReturn(false)

        testee = RealPageContextRepository(
            pageContextStore = mockStore,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            coroutineScope = testScope,
            isMainProcess = true,
        )
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(initialJson, testee.getJsonData())

        val failedJson = """{"failed":"json"}"""
        testee.insertJsonData(failedJson)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockStore).insertJsonData(failedJson)
        assertEquals(initialJson, testee.getJsonData())
    }
}
