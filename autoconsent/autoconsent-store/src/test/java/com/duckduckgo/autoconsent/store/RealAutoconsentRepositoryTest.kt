/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.store

import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealAutoconsentRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDatabase: AutoconsentDatabase = mock()
    private val mockDao: AutoconsentDao = mock()

    lateinit var repository: AutoconsentRepository

    @Before
    fun before() {
        whenever(mockDatabase.autoconsentDao()).thenReturn(mockDao)

        repository = RealAutoconsentRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenDaoContainsExceptionsAndDisabledCmps()

        repository = RealAutoconsentRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        assertEquals(exception.toFeatureException(), repository.exceptions.first())
        assertEquals(disabledCmp, repository.disabledCmps.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() = runTest {
        repository = RealAutoconsentRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        repository.updateAll(listOf(), listOf())

        verify(mockDao).updateAll(anyList(), anyList())
    }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() = runTest {
        givenDaoContainsExceptionsAndDisabledCmps()
        repository = RealAutoconsentRepository(mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        assertEquals(1, repository.exceptions.size)
        assertEquals(1, repository.disabledCmps.size)
        reset(mockDao)

        repository.updateAll(listOf(), listOf())

        assertEquals(0, repository.exceptions.size)
        assertEquals(0, repository.disabledCmps.size)
    }

    private fun givenDaoContainsExceptionsAndDisabledCmps() {
        whenever(mockDao.getExceptions()).thenReturn(listOf(exception))
        whenever(mockDao.getDisabledCmps()).thenReturn(listOf(disabledCmp))
    }

    companion object {
        val exception = AutoconsentExceptionEntity("example.com", "reason")
        val disabledCmp = DisabledCmpsEntity("disabledcmp")
    }
}
