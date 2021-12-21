/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.unprotectedtemporary

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers

@ExperimentalCoroutinesApi
class RealUnprotectedTemporaryRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealUnprotectedTemporaryRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockUnprotectedTemporaryDao: UnprotectedTemporaryDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.unprotectedTemporaryDao()).thenReturn(mockUnprotectedTemporaryDao)
        testee =
            RealUnprotectedTemporaryRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenUnprotectedTemporaryDaoContainsExceptions()

        testee =
            RealUnprotectedTemporaryRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        assertEquals(unprotectedTemporaryException, testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealUnprotectedTemporaryRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

            testee.updateAll(listOf())

            verify(mockUnprotectedTemporaryDao).updateAll(ArgumentMatchers.anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenUnprotectedTemporaryDaoContainsExceptions()
            testee =
                RealUnprotectedTemporaryRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
            assertEquals(1, testee.exceptions.size)
            reset(mockUnprotectedTemporaryDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    private fun givenUnprotectedTemporaryDaoContainsExceptions() {
        whenever(mockUnprotectedTemporaryDao.getAll())
            .thenReturn(listOf(unprotectedTemporaryException))
    }

    companion object {
        val unprotectedTemporaryException = UnprotectedTemporaryEntity("example.com", "reason")
    }
}
