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
    fun whenGetDaxPromptsShowDuckPlayerAndNoCachedValueThenReturnFromDataStore() = runTest {
        whenever(mockDataStore.getDaxPromptsShowDuckPlayer()).thenReturn(true)

        val result = testee.getDaxPromptsShowDuckPlayer()

        assertEquals(true, result)
        verify(mockDataStore, times(1)).getDaxPromptsShowDuckPlayer()
    }

    @Test
    fun whenGetDaxPromptsShowDuckPlayerAndCachedValueExistsThenReturnCachedValue() = runTest {
        whenever(mockDataStore.getDaxPromptsShowDuckPlayer()).thenReturn(true)
        testee.setDaxPromptsShowDuckPlayer(false)
        whenever(mockDataStore.getDaxPromptsShowDuckPlayer()).thenReturn(true) // This shouldn't be called

        val result = testee.getDaxPromptsShowDuckPlayer()

        assertEquals(false, result)
        verify(mockDataStore, never()).getDaxPromptsShowDuckPlayer()
    }

    @Test
    fun whenSetDaxPromptsShowDuckPlayerThenUpdateBothCacheAndDataStore() = runTest {
        testee.setDaxPromptsShowDuckPlayer(true)

        verify(mockDataStore, times(1)).setDaxPromptsShowDuckPlayer(true)
        assertEquals(true, testee.getDaxPromptsShowDuckPlayer())
    }

    @Test
    fun whenSetDaxPromptsShowDuckPlayerThenSubsequentGetReturnsNewValue() = runTest {
        whenever(mockDataStore.getDaxPromptsShowDuckPlayer()).thenReturn(false)

        val initialResult = testee.getDaxPromptsShowDuckPlayer()
        testee.setDaxPromptsShowDuckPlayer(true)
        val finalResult = testee.getDaxPromptsShowDuckPlayer()

        assertEquals(false, initialResult)
        assertEquals(true, finalResult)
    }

    @Test
    fun whenSetDaxPromptsShowDuckPlayerWithSameValueThenStillUpdateDataStore() = runTest {
        testee.setDaxPromptsShowDuckPlayer(true)

        testee.setDaxPromptsShowDuckPlayer(true)

        verify(mockDataStore, times(2)).setDaxPromptsShowDuckPlayer(true)
    }
}
