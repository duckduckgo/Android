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

import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.ExperimentAppUsageRepository.UserNotEnrolledException
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ExperimentAppUsageRepositoryImplTest {

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
        val testee = ExperimentAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        testee.recordAppUsedNow()

        verify(experimentAppUsageDaoMock).insert(expected)
    }

    @Test
    fun `when active days since enrollment queried and no cohort assigned, return failure`() = runTest {
        val toggleMock = mock<Toggle>()
        whenever(toggleMock.getCohort()).thenReturn(null)
        val testee = ExperimentAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val actual = testee.getActiveDaysUsedSinceEnrollment(toggleMock)

        Assert.assertTrue(actual.exceptionOrNull() is UserNotEnrolledException)
    }

    @Test
    fun `when active days since enrollment queried and malformed date, return failure`() = runTest {
        val fakeCohort = Cohort(
            name = "fakeCohort",
            weight = 1,
            enrollmentDateET = "2025-01-16",
        )
        val toggleMock = mock<Toggle>()
        whenever(toggleMock.getCohort()).thenReturn(fakeCohort)
        val testee = ExperimentAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val actual = testee.getActiveDaysUsedSinceEnrollment(toggleMock)

        Assert.assertTrue(actual.exceptionOrNull() is DateTimeParseException)
    }

    @Test
    fun `when active days since enrollment queried and user is enrolled, return success`() = runTest {
        val zonedDateTimeString = "2025-01-16T00:00-05:00[America/New_York]"
        val fakeCohort = Cohort(
            name = "fakeCohort",
            weight = 1,
            enrollmentDateET = zonedDateTimeString,
        )
        val toggleMock = mock<Toggle>()
        whenever(toggleMock.getCohort()).thenReturn(fakeCohort)
        val expectedIsoDateString = "2025-01-16"
        val expectedValue = 2L
        whenever(experimentAppUsageDaoMock.getNumberOfDaysAppUsedSinceDateET(expectedIsoDateString)).thenReturn(expectedValue)
        val testee = ExperimentAppUsageRepositoryImpl(
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            experimentAppUsageDao = experimentAppUsageDaoMock,
        )

        val actual = testee.getActiveDaysUsedSinceEnrollment(toggleMock)

        Assert.assertEquals(Result.success(expectedValue), actual)
    }
}
