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

package com.duckduckgo.privacy.config.store.features.useragent

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UserAgentExceptionEntity
import com.duckduckgo.privacy.config.store.toUserAgentException
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

@ExperimentalCoroutinesApi
class RealUserAgentRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealUserAgentRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockUserAgentDao: UserAgentDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.userAgentDao()).thenReturn(mockUserAgentDao)
        testee =
            RealUserAgentRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider
            )
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenUserAgentDaoContainsExceptions()
        val actual = userAgentException.toUserAgentException()
        testee =
            RealUserAgentRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider
            )

        assertEquals(testee.omitApplicationExceptions.first(), actual)
        assertEquals(testee.omitVersionExceptions.first(), actual)
        assertEquals(testee.defaultExceptions.first(), actual)
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealUserAgentRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider
                )

            testee.updateAll(listOf())

            verify(mockUserAgentDao).updateAll(anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenUserAgentDaoContainsExceptions()
            testee =
                RealUserAgentRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider
                )
            assertEquals(1, testee.defaultExceptions.size)
            assertEquals(1, testee.omitApplicationExceptions.size)
            assertEquals(1, testee.omitVersionExceptions.size)
            reset(mockUserAgentDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.defaultExceptions.size)
            assertEquals(0, testee.omitApplicationExceptions.size)
            assertEquals(0, testee.omitVersionExceptions.size)
        }

    private fun givenUserAgentDaoContainsExceptions() {
        whenever(mockUserAgentDao.getApplicationExceptions()).thenReturn(listOf(userAgentException))
        whenever(mockUserAgentDao.getDefaultExceptions()).thenReturn(listOf(userAgentException))
        whenever(mockUserAgentDao.getVersionExceptions()).thenReturn(listOf(userAgentException))
    }

    companion object {
        val userAgentException = UserAgentExceptionEntity("example.com", "reason", omitApplication = false, omitVersion = false)
    }
}
