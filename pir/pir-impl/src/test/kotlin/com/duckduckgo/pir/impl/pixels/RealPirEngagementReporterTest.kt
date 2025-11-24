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

package com.duckduckgo.pir.impl.pixels

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.pir.impl.checker.PirWorkHandler
import com.duckduckgo.pir.impl.models.ProfileQuery
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime
import java.time.ZoneId

class RealPirEngagementReporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var testee: RealPirEngagementReporter

    private val mockDispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockPirWorkHandler: PirWorkHandler = mock()
    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirPixelSender: PirPixelSender = mock()

    // Real dates for testing
    // January 1, 2024 12:00:00 UTC
    private val baseDate = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
    private val baseDateMs = baseDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // January 2, 2024 12:00:00 UTC (1 day later for DAU)
    private val oneDayLater = baseDate.plusDays(1)
    private val oneDayLaterMs = oneDayLater.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // January 8, 2024 12:00:00 UTC (7 days later for WAU)
    private val sevenDaysLater = baseDate.plusDays(7)
    private val sevenDaysLaterMs = sevenDaysLater.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // January 29, 2024 12:00:00 UTC (28 days later for MAU)
    private val twentyEightDaysLater = baseDate.plusDays(28)
    private val twentyEightDaysLaterMs = twentyEightDaysLater.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val testProfileQuery = ProfileQuery(
        id = 1L,
        firstName = "John",
        lastName = "Doe",
        city = "New York",
        state = "NY",
        addresses = emptyList(),
        birthYear = 1990,
        fullName = "John Doe",
        age = 33,
        deprecated = false,
    )

    @Before
    fun setUp() {
        testee = RealPirEngagementReporter(
            dispatcherProvider = mockDispatcherProvider,
            pirWorkHandler = mockPirWorkHandler,
            pirRepository = mockPirRepository,
            currentTimeProvider = mockCurrentTimeProvider,
            pirPixelSender = mockPirPixelSender,
        )
    }

    // ========== DAU pixel tests ==========

    @Test
    fun whenNotActiveUserThenDoesNotFireDauPixel() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(false))

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
        verifyNoInteractions(mockPirRepository)
    }

    @Test
    fun whenActiveUserAndNoLastPixelForAnyThenFiresAllPixels() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(0L)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(0L)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(baseDateMs)
        verify(mockPirPixelSender).reportWAU()
        verify(mockPirRepository).setLastPirWauPixelTimeMs(baseDateMs)
        verify(mockPirPixelSender).reportMAU()
        verify(mockPirRepository).setLastPirMauPixelTimeMs(baseDateMs)
    }

    @Test
    fun whenActiveUserAndOneDayPassedThenFiresDauPixelOnly() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(oneDayLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(oneDayLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    @Test
    fun whenActiveUserAndSameDayThenDoesNotFireAnyPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    @Test
    fun whenActiveUserAndDateChangedButNot24hrsThenDoesFireDauPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        val almostOneDayLater = baseDate.plusHours(23).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(almostOneDayLater)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(almostOneDayLater)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    @Test
    fun whenActiveUserAndSevenDaysPassedThenFiresWauPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(sevenDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(sevenDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportWAU()
        verify(mockPirRepository).setLastPirWauPixelTimeMs(sevenDaysLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    @Test
    fun whenActiveUserAndLessThanSevenDaysPassedThenDoesNotFireWauPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(sevenDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        val sixDaysLater = baseDate.plusDays(6).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(sixDaysLater)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    // ========== MAU pixel tests ==========

    @Test
    fun whenActiveUserAndTwentyEightDaysPassedThenFiresMauPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(twentyEightDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(twentyEightDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twentyEightDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportMAU()
        verify(mockPirRepository).setLastPirMauPixelTimeMs(twentyEightDaysLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
    }

    @Test
    fun whenActiveUserAndLessThanTwentyEightDaysPassedThenDoesNotFireMauPixel() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        val twentySevenDaysLater = baseDate.plusDays(27).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(twentySevenDaysLater) // Emitted already
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(twentySevenDaysLater) // Emitted already
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twentySevenDaysLater)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    // ========== Combined tests ==========

    @Test
    fun whenActiveUserAndAllPixelsEligibleThenFiresAllPixels() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twentyEightDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirPixelSender).reportWAU()
        verify(mockPirPixelSender).reportMAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(twentyEightDaysLaterMs)
        verify(mockPirRepository).setLastPirWauPixelTimeMs(twentyEightDaysLaterMs)
        verify(mockPirRepository).setLastPirMauPixelTimeMs(twentyEightDaysLaterMs)
    }

    @Test
    fun whenActiveUserAndOnlyDauAndWauEligibleThenFiresBothPixels() = runTest {
        setupActiveUser()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(sevenDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirPixelSender).reportWAU()
        verify(mockPirPixelSender, never()).reportMAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(sevenDaysLaterMs)
        verify(mockPirRepository).setLastPirWauPixelTimeMs(sevenDaysLaterMs)
        verify(mockPirRepository, never()).setLastPirMauPixelTimeMs(any())
    }

    // ========== Edge cases ==========

    @Test
    fun whenActiveUserAndNoProfileQueriesThenDoesNotFirePixels() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(true))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(emptyList())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(oneDayLaterMs)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
    }

    @Test
    fun whenActiveUserAndPirWorkHandlerReturnsNullThenDoesNotFirePixels() = runTest {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(emptyFlow())
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(oneDayLaterMs)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
    }

    @Test
    fun whenActiveUserAndLastPixelInFutureThenDoesNotFirePixels() = runTest {
        setupActiveUser()
        val futureDate = baseDate.plusDays(1)
        val futureDateMs = futureDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender, never()).reportDAU()
    }

    @Test
    fun whenActiveUserAndCrossingMonthBoundaryThenFiresDauPixel() = runTest {
        setupActiveUser()
        // January 31, 2024
        val jan31 = LocalDateTime.of(2024, 1, 31, 12, 0, 0)
        val jan31Ms = jan31.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // February 1, 2024 (1 day later)
        val feb1 = LocalDateTime.of(2024, 2, 1, 12, 0, 0)
        val feb1Ms = feb1.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(jan31Ms)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(jan31Ms)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(jan31Ms)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(feb1Ms)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(feb1Ms)
    }

    @Test
    fun whenActiveUserAndCrossingYearBoundaryThenFiresDauPixel() = runTest {
        setupActiveUser()
        // December 31, 2023
        val dec31 = LocalDateTime.of(2023, 12, 31, 12, 0, 0)
        val dec31Ms = dec31.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // January 1, 2024 (1 day later)
        val jan1 = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
        val jan1Ms = jan1.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(dec31Ms)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(dec31Ms)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(dec31Ms)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(jan1Ms)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(jan1Ms)
    }

    @Test
    fun whenActiveUserAndMoreThanOneDayPassedThenFiresDauPixel() = runTest {
        setupActiveUser()
        val twoDaysLater = baseDate.plusDays(2).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockPirRepository.getLastPirDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twoDaysLater)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportDAU()
        verify(mockPirRepository).setLastPirDauPixelTimeMs(twoDaysLater)
    }

    private suspend fun setupActiveUser() {
        whenever(mockPirWorkHandler.canRunPir()).thenReturn(flowOf(true))
        whenever(mockPirRepository.getValidUserProfileQueries()).thenReturn(listOf(testProfileQuery))
    }
}
