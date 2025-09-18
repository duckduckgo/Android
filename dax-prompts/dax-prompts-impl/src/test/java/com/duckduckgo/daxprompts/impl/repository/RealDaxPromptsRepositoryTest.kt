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

package com.duckduckgo.daxprompts.impl.repository

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.impl.store.DaxPromptsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class RealDaxPromptsRepositoryTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: RealDaxPromptsRepository

    private val mockDataStore: DaxPromptsDataStore = mock()

    @Before
    fun setup() {
        testee = RealDaxPromptsRepository(mockDataStore, coroutineTestRule.testDispatcherProvider)
    }

    @Test
    fun whenGetDaxPromptsShowBrowserComparisonAndNoCachedValueThenReturnFromDataStore() = runTest {
        whenever(mockDataStore.getDaxPromptsShowBrowserComparison()).thenReturn(true)

        val result = testee.getDaxPromptsShowBrowserComparison()

        assertEquals(true, result)
        verify(mockDataStore, times(1)).getDaxPromptsShowBrowserComparison()
    }

    @Test
    fun whenGetDaxPromptsShowBrowserComparisonAndCachedValueExistsThenReturnCachedValue() = runTest {
        whenever(mockDataStore.getDaxPromptsShowBrowserComparison()).thenReturn(true)
        testee.setDaxPromptsShowBrowserComparison(false)
        whenever(mockDataStore.getDaxPromptsShowBrowserComparison()).thenReturn(true) // This shouldn't be called

        val result = testee.getDaxPromptsShowBrowserComparison()

        assertEquals(false, result)
        verify(mockDataStore, never()).getDaxPromptsShowBrowserComparison()
    }

    @Test
    fun whenSetDaxPromptsShowBrowserComparisonThenUpdateBothCacheAndDataStore() = runTest {
        testee.setDaxPromptsShowBrowserComparison(true)

        verify(mockDataStore, times(1)).setDaxPromptsShowBrowserComparison(true)
        assertEquals(true, testee.getDaxPromptsShowBrowserComparison())
    }

    @Test
    fun whenSetDaxPromptsShowBrowserComparisonThenSubsequentGetReturnsNewValue() = runTest {
        whenever(mockDataStore.getDaxPromptsShowBrowserComparison()).thenReturn(false)

        val initialResult = testee.getDaxPromptsShowBrowserComparison()
        testee.setDaxPromptsShowBrowserComparison(true)
        val finalResult = testee.getDaxPromptsShowBrowserComparison()

        assertEquals(false, initialResult)
        assertEquals(true, finalResult)
    }

    @Test
    fun whenSetDaxPromptsShowBrowserComparisonWithSameValueThenStillUpdateDataStore() = runTest {
        testee.setDaxPromptsShowBrowserComparison(true)

        testee.setDaxPromptsShowBrowserComparison(true)

        verify(mockDataStore, times(2)).setDaxPromptsShowBrowserComparison(true)
    }
}
