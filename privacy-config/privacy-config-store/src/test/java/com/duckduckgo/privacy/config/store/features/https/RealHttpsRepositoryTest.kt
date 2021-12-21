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

package com.duckduckgo.privacy.config.store.features.https

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.HttpsExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toHttpsException
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
class RealHttpsRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealHttpsRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockHttpsDao: HttpsDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.httpsDao()).thenReturn(mockHttpsDao)
        testee =
            RealHttpsRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenHttpsDaoContainsExceptions()

        testee =
            RealHttpsRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        assertEquals(httpException.toHttpsException(), testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealHttpsRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

            testee.updateAll(listOf())

            verify(mockHttpsDao).updateAll(ArgumentMatchers.anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenHttpsDaoContainsExceptions()
            testee =
                RealHttpsRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
            assertEquals(1, testee.exceptions.size)
            reset(mockHttpsDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    private fun givenHttpsDaoContainsExceptions() {
        whenever(mockHttpsDao.getAll()).thenReturn(listOf(httpException))
    }

    companion object {
        val httpException = HttpsExceptionEntity("example.com", "reason")
    }
}
