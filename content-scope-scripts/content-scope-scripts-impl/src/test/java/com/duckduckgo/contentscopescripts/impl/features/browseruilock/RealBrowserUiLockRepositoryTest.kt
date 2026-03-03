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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.contentscopescripts.impl.features.browseruilock.store.BrowserUiLockStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealBrowserUiLockRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealBrowserUiLockRepository

    private val mockStore: BrowserUiLockStore = mock()

    @Before
    fun before() = runTest {
        whenever(mockStore.getJsonData()).thenReturn(null)
    }

    @Test
    fun whenInitializedAndDoesNotHaveStoredValueThenLoadEmptyJsonToMemory() =
        runTest {
            testee = RealBrowserUiLockRepository(
                mockStore,
                coroutineRule.testDispatcherProvider,
                TestScope(),
                isMainProcess = true,
            )

            verify(mockStore).getJsonData()
            assertEquals("{}", testee.getJsonData())
        }

    @Test
    fun whenInitializedAndHasStoredValueThenLoadStoredJsonToMemory() =
        runTest {
            whenever(mockStore.getJsonData()).thenReturn(JSON_STRING)
            testee = RealBrowserUiLockRepository(
                mockStore,
                coroutineRule.testDispatcherProvider,
                TestScope(),
                isMainProcess = true,
            )

            verify(mockStore).getJsonData()
            assertEquals(JSON_STRING, testee.getJsonData())
        }

    @Test
    fun whenInsertJsonDataThenStoreIsCalled() =
        runTest {
            whenever(mockStore.insertJsonData(JSON_STRING)).thenReturn(true)
            testee = RealBrowserUiLockRepository(
                mockStore,
                coroutineRule.testDispatcherProvider,
                TestScope(),
                isMainProcess = true,
            )

            testee.insertJsonData(JSON_STRING)

            verify(mockStore).insertJsonData(JSON_STRING)
        }

    @Test
    fun whenInsertJsonDataSucceedsThenMemoryIsUpdated() =
        runTest {
            whenever(mockStore.insertJsonData(JSON_STRING)).thenReturn(true)
            testee = RealBrowserUiLockRepository(
                mockStore,
                coroutineRule.testDispatcherProvider,
                TestScope(),
                isMainProcess = true,
            )

            testee.insertJsonData(JSON_STRING)

            assertEquals(JSON_STRING, testee.getJsonData())
        }

    @Test
    fun whenInsertJsonDataFailsThenMemoryIsNotUpdated() =
        runTest {
            whenever(mockStore.insertJsonData(JSON_STRING)).thenReturn(false)
            testee = RealBrowserUiLockRepository(
                mockStore,
                coroutineRule.testDispatcherProvider,
                TestScope(),
                isMainProcess = true,
            )

            testee.insertJsonData(JSON_STRING)

            assertEquals("{}", testee.getJsonData())
        }

    companion object {
        private const val JSON_STRING = "{\"key\":\"value\"}"
    }
}
