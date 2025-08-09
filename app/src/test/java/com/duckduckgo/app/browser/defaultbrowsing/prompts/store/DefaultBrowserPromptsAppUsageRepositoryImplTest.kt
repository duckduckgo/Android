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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.store

import com.duckduckgo.common.test.CoroutineTestRule
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultBrowserPromptsAppUsageRepositoryImplTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    @Mock private lateinit var experimentAppUsageDaoMock: ExperimentAppUsageDao

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun `when record usage, then insert ET time`() = runTest {
        val expected = ExperimentAppUsageEntity(
            isoDateET = ZonedDateTime.now(ZoneId.of("America/New_York"))
                .truncatedTo(ChronoUnit.DAYS)
                .format(DateTimeFormatter.ISO_LOCAL_DATE),
        )
        val testee = DefaultBrowserPromptsAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        testee.recordAppUsedNow()

        verify(experimentAppUsageDaoMock).insert(expected)
    }

    @Test
    fun `when active days since enrollment queried and first day is null, return failure`() = runTest {
        whenever(experimentAppUsageDaoMock.getFirstDay()).thenReturn(null)
        val testee = DefaultBrowserPromptsAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val result = testee.getActiveDaysUsedSinceEnrollment()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `when active days since enrollment queried and first day is malformed date, return failure`() = runTest {
        val invalidDate = "not-a-valid-date"
        whenever(experimentAppUsageDaoMock.getFirstDay()).thenReturn(invalidDate)
        val testee = DefaultBrowserPromptsAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val result = testee.getActiveDaysUsedSinceEnrollment()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DateTimeParseException)
    }

    @Test
    fun `when active days since enrollment queried and first day is correct, return success`() = runTest {
        val validDate = "2023-01-01"
        val expectedDaysUsed = 2L
        whenever(experimentAppUsageDaoMock.getFirstDay()).thenReturn(validDate)
        whenever(experimentAppUsageDaoMock.getNumberOfDaysAppUsedSinceDateET(validDate)).thenReturn(expectedDaysUsed)

        val testee = DefaultBrowserPromptsAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val result = testee.getActiveDaysUsedSinceEnrollment()

        assertTrue(result.isSuccess)
        Assert.assertEquals(expectedDaysUsed, result.getOrThrow())
    }
}
