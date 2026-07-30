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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.duckduckgo.privacy.config.store.toFeatureException
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
                isMainProcess = true,
            )
    }

    @Test
    fun whenRepositoryIsCreatedThenExceptionsLoadedIntoMemory() {
        givenUnprotectedTemporaryDaoContainsExceptions()

        testee =
            RealUnprotectedTemporaryRepository(
                mockDatabase,
                TestScope(),
                coroutineRule.testDispatcherProvider,
                isMainProcess = true,
            )

        assertEquals(unprotectedTemporaryException.toFeatureException(), testee.exceptions.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() =
        runTest {
            testee =
                RealUnprotectedTemporaryRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )

            testee.updateAll(listOf())

            verify(mockUnprotectedTemporaryDao).updateAll(ArgumentMatchers.anyList())
        }

    @Test
    fun whenUpdateAllThenPreviousExceptionsAreCleared() =
        runTest {
            givenUnprotectedTemporaryDaoContainsExceptions()
            testee =
                RealUnprotectedTemporaryRepository(
                    mockDatabase,
                    TestScope(),
                    coroutineRule.testDispatcherProvider,
                    isMainProcess = true,
                )
            assertEquals(1, testee.exceptions.size)
            reset(mockUnprotectedTemporaryDao)

            testee.updateAll(listOf())

            assertEquals(0, testee.exceptions.size)
        }

    @Test
    fun whenReloadingThenAReaderNeverObservesAPartialList() {
        whenever(mockUnprotectedTemporaryDao.getAll())
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        testee = createTestee()
        assertEquals(2, testee.exceptions.size)

        // Read `exceptions` from inside the reload, at the point where a refill-in-place implementation has
        // already dropped the previous entries and not yet added the new ones.
        val sizesObservedMidReload = mutableListOf<Int>()
        whenever(mockUnprotectedTemporaryDao.getAll()).thenAnswer {
            sizesObservedMidReload.add(testee.exceptions.size)
            listOf(unprotectedTemporaryException)
        }

        testee.updateAll(listOf())

        assertEquals(listOf(2), sizesObservedMidReload)
        assertEquals(1, testee.exceptions.size)
    }

    @Test
    fun whenReloadingThenAPreviouslyReturnedListIsUnaffected() {
        whenever(mockUnprotectedTemporaryDao.getAll()).thenReturn(listOf(unprotectedTemporaryException))
        testee = createTestee()
        val held = testee.exceptions

        whenever(mockUnprotectedTemporaryDao.getAll())
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        testee.updateAll(listOf())

        // Callers may cache the reference and detect a change by comparing it, so a published list must never
        // be mutated afterwards.
        assertEquals(1, held.size)
        assertEquals(2, testee.exceptions.size)
        assertNotSame(held, testee.exceptions)
    }

    @Test
    fun whenReloadFailsThenThePreviouslyLoadedExceptionsAreRetained() {
        whenever(mockUnprotectedTemporaryDao.getAll()).thenReturn(listOf(unprotectedTemporaryException))
        testee = createTestee()
        assertEquals(1, testee.exceptions.size)

        whenever(mockUnprotectedTemporaryDao.getAll()).thenThrow(RuntimeException("database unavailable"))

        // The new list is only published once the read succeeds, so a failed reload keeps serving the previous
        // exceptions rather than dropping every exemption until the next successful one.
        assertThrows(RuntimeException::class.java) { testee.updateAll(listOf()) }

        assertEquals(1, testee.exceptions.size)
        assertEquals(unprotectedTemporaryException.toFeatureException(), testee.exceptions.first())
    }

    private fun createTestee() =
        RealUnprotectedTemporaryRepository(
            mockDatabase,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            isMainProcess = true,
        )

    private fun givenUnprotectedTemporaryDaoContainsExceptions() {
        whenever(mockUnprotectedTemporaryDao.getAll())
            .thenReturn(listOf(unprotectedTemporaryException))
    }

    companion object {
        val unprotectedTemporaryException = UnprotectedTemporaryEntity("example.com", "reason")
        val unprotectedTemporaryException2 = UnprotectedTemporaryEntity("foo.com", "reason2")
    }
}
