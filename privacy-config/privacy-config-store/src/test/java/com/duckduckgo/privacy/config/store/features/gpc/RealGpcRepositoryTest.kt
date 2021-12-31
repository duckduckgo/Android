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

package com.duckduckgo.privacy.config.store.features.gpc

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.privacy.config.store.GpcExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.toGpcException
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
class RealGpcRepositoryTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealGpcRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockGpcDao: GpcDao = mock()
    private val mockGpcDataStore: GpcDataStore = mock()

    @Before
    fun before() {
        whenever(mockDatabase.gpcDao()).thenReturn(mockGpcDao)
        testee =
            RealGpcRepository(
                mockGpcDataStore,
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenGpcDaoContainsExceptions()

        testee =
            RealGpcRepository(
                mockGpcDataStore,
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider)

        assertEquals(gpcException.toGpcException(), testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealGpcRepository(
                    mockGpcDataStore,
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider)

            testee.updateAll(listOf())

            verify(mockGpcDao).updateAll(anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenGpcDaoContainsExceptions()
            testee =
                RealGpcRepository(
                    mockGpcDataStore,
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider)
            assertEquals(1, testee.exceptions.size)
            reset(mockGpcDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    @Test
    fun whenEnableGpcThenSetGpcEnabledToTrue() {
        testee.enableGpc()

        verify(mockGpcDataStore).gpcEnabled = true
    }

    @Test
    fun whenDisableGpcThenSetGpcEnabledToFalse() {
        testee.disableGpc()

        verify(mockGpcDataStore).gpcEnabled = false
    }

    @Test
    fun whenIsGpcEnabledThenReturnGpcEnabledValue() {
        whenever(mockGpcDataStore.gpcEnabled).thenReturn(true)
        assertTrue(testee.isGpcEnabled())

        whenever(mockGpcDataStore.gpcEnabled).thenReturn(false)
        assertFalse(testee.isGpcEnabled())
    }

    private fun givenGpcDaoContainsExceptions() {
        whenever(mockGpcDao.getAll()).thenReturn(listOf(gpcException))
    }

    companion object {
        val gpcException = GpcExceptionEntity("example.com")
    }
}
