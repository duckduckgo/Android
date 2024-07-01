/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.remoteconfig

import com.duckduckgo.autoconsent.impl.store.AutoconsentDao
import com.duckduckgo.autoconsent.impl.store.AutoconsentDatabase
import com.duckduckgo.autoconsent.impl.store.AutoconsentExceptionEntity
import com.duckduckgo.autoconsent.impl.store.toFeatureException
import com.duckduckgo.common.test.CoroutineTestRule
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

class RealAutoconsentExceptionsRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDatabase: AutoconsentDatabase = mock()
    private val mockDao: AutoconsentDao = mock()

    lateinit var repository: AutoconsentExceptionsRepository

    @Before
    fun before() {
        whenever(mockDatabase.autoconsentDao()).thenReturn(mockDao)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenDaoContainsExceptions()

        repository = RealAutoconsentExceptionsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDatabase, isMainProcess = true)

        assertEquals(exception.toFeatureException(), repository.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() = runTest {
        repository = RealAutoconsentExceptionsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDatabase, isMainProcess = true)

        repository.insertAllExceptions(listOf())

        verify(mockDao).updateAllExceptions(anyList())
    }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() = runTest {
        givenDaoContainsExceptions()
        repository = RealAutoconsentExceptionsRepository(TestScope(), coroutineRule.testDispatcherProvider, mockDatabase, isMainProcess = true)

        assertEquals(1, repository.exceptions.size)
        reset(mockDao)

        repository.insertAllExceptions(listOf())

        assertEquals(0, repository.exceptions.size)
    }

    private fun givenDaoContainsExceptions() {
        whenever(mockDao.getExceptions()).thenReturn(listOf(exception))
    }

    companion object {
        val exception = AutoconsentExceptionEntity("example.com", "reason")
    }
}
