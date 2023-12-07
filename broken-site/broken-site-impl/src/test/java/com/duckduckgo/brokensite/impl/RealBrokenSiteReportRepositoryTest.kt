/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.brokensite.impl

import com.duckduckgo.brokensite.store.BrokenSiteDao
import com.duckduckgo.brokensite.store.BrokenSiteDatabase
import com.duckduckgo.brokensite.store.BrokenSiteLastSentReportEntity
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealBrokenSiteReportRepositoryTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockDatabase: BrokenSiteDatabase = mock()
    private val mockBrokenSiteDao: BrokenSiteDao = mock()
    lateinit var testee: RealBrokenSiteReportRepository

    @Before
    fun before() {
        whenever(mockDatabase.brokenSiteDao()).thenReturn(mockBrokenSiteDao)

        testee = RealBrokenSiteReportRepository(
            database = mockDatabase,
            coroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenGetLastSentDayCalledWithEmptyHostnameThenReturnNull() = runTest {
        val hostname = ""

        val result = testee.getLastSentDay(hostname)

        assertNull(result)
    }

    @Test
    fun whenGetLastSentDayCalledWithNewHostnameThenReturnNull() = runTest {
        val hostname = "www.example.com"
        val hostnameHashPrefix = "80fc0f"
        val brokenSiteReportEntity = null
        whenever(mockDatabase.brokenSiteDao().getBrokenSiteReport(hostnameHashPrefix)).thenReturn(brokenSiteReportEntity)

        val result = testee.getLastSentDay(hostname)

        assertNull(result)
    }

    @Test
    fun whenGetLastSentDayCalledWithExistingHostnameThenReturnLastSeenDay() = runTest {
        val hostname = "www.example.com"
        val hostnameHashPrefix = "80fc0f"
        val lastSentDay = "2023-11-01T15:30:54.401Z"
        val brokenSiteReportEntity = BrokenSiteLastSentReportEntity(hostnameHashPrefix, lastSentDay)
        whenever(mockDatabase.brokenSiteDao().getBrokenSiteReport(hostnameHashPrefix)).thenReturn(brokenSiteReportEntity)

        val result = testee.getLastSentDay(hostname)

        assertEquals("2023-11-01", result)
    }

    @Test
    fun whenSetLastSentDayCalledWithEmptyHostnameThenUpsertBrokenSiteReportIsNeverCalled() = runTest {
        val hostname = ""

        testee.setLastSentDay(hostname)

        verify(mockDatabase.brokenSiteDao(), never()).insertBrokenSiteReport(any())
    }

    @Test
    fun whenSetLastSentDayCalledWithNonEmptyHostnameThenUpsertBrokenSiteReportIsCalled() = runTest {
        val hostname = "www.example.com"

        testee.setLastSentDay(hostname)

        verify(mockDatabase.brokenSiteDao()).insertBrokenSiteReport(any())
    }

    @Test
    fun whenCleanupOldEntriesCalledThenCleanupBrokenSiteReportIsCalled() = runTest {
        testee.cleanupOldEntries()

        verify(mockDatabase.brokenSiteDao()).deleteAllExpiredReports(any())
    }
}
