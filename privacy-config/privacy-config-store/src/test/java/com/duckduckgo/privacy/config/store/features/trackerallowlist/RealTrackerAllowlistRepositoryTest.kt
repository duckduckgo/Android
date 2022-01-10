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

package com.duckduckgo.privacy.config.store.features.trackerallowlist

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.AllowlistRuleEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList

@ExperimentalCoroutinesApi
class RealTrackerAllowlistRepositoryTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealTrackerAllowlistRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockTrackerAllowlistDao: TrackerAllowlistDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.trackerAllowlistDao()).thenReturn(mockTrackerAllowlistDao)
        testee =
            RealTrackerAllowlistRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenHttpsDaoContainsExceptions()

        testee =
            RealTrackerAllowlistRepository(
                mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

        assertEquals(trackerAllowlistEntity, testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealTrackerAllowlistRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)

            testee.updateAll(listOf())

            verify(mockTrackerAllowlistDao).updateAll(anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenHttpsDaoContainsExceptions()
            testee =
                RealTrackerAllowlistRepository(
                    mockDatabase, TestScope(), coroutineRule.testDispatcherProvider)
            assertEquals(1, testee.exceptions.size)
            reset(mockTrackerAllowlistDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    private fun givenHttpsDaoContainsExceptions() {
        whenever(mockTrackerAllowlistDao.getAll()).thenReturn(listOf(trackerAllowlistEntity))
    }

    companion object {
        val trackerAllowlistEntity =
            TrackerAllowlistEntity(
                domain = "domain",
                rules =
                    listOf(
                        AllowlistRuleEntity(
                            rule = "rule", domains = listOf("domain"), reason = "reason")))
    }
}
