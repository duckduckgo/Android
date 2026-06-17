/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.pir.impl.store.PirRepository
import kotlinx.coroutines.test.runTest
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

class RealPirInteractionReporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockDispatcherProvider = coroutineRule.testDispatcherProvider
    private val mockPirRepository: PirRepository = mock()
    private val mockCurrentTimeProvider: CurrentTimeProvider = mock()
    private val mockPirPixelSender: PirPixelSender = mock()

    private val testee = RealPirInteractionReporter(
        dispatcherProvider = mockDispatcherProvider,
        pirRepository = mockPirRepository,
        currentTimeProvider = mockCurrentTimeProvider,
        pirPixelSender = mockPirPixelSender,
    )

    // January 1, 2024 12:00:00
    private val baseDate = LocalDateTime.of(2024, 1, 1, 12, 0, 0)
    private val baseDateMs = baseDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val oneDayLaterMs = baseDate.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val sevenDaysLaterMs = baseDate.plusDays(7).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val twentyEightDaysLaterMs = baseDate.plusDays(28).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun whenNoLastPixelForAnyThenFiresAllPixels() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(0L)
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(0L)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(0L)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportInteractionDAU()
        verify(mockPirRepository).setLastPirInteractionDauPixelTimeMs(baseDateMs)
        verify(mockPirPixelSender).reportInteractionWAU()
        verify(mockPirRepository).setLastPirInteractionWauPixelTimeMs(baseDateMs)
        verify(mockPirPixelSender).reportInteractionMAU()
        verify(mockPirRepository).setLastPirInteractionMauPixelTimeMs(baseDateMs)
    }

    @Test
    fun whenOneDayPassedThenFiresDauPixelOnly() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(oneDayLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportInteractionDAU()
        verify(mockPirRepository).setLastPirInteractionDauPixelTimeMs(oneDayLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirInteractionWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirInteractionMauPixelTimeMs(any())
    }

    @Test
    fun whenSameDayThenDoesNotFireAnyPixel() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirInteractionDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirInteractionWauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirInteractionMauPixelTimeMs(any())
    }

    @Test
    fun whenSevenDaysPassedThenFiresWauPixel() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(sevenDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(sevenDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportInteractionWAU()
        verify(mockPirRepository).setLastPirInteractionWauPixelTimeMs(sevenDaysLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirInteractionDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirInteractionMauPixelTimeMs(any())
    }

    @Test
    fun whenTwentyEightDaysPassedThenFiresMauPixel() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(twentyEightDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(twentyEightDaysLaterMs) // Emitted already
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twentyEightDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportInteractionMAU()
        verify(mockPirRepository).setLastPirInteractionMauPixelTimeMs(twentyEightDaysLaterMs)
        verifyNoMoreInteractions(mockPirPixelSender)
        verify(mockPirRepository, never()).setLastPirInteractionDauPixelTimeMs(any())
        verify(mockPirRepository, never()).setLastPirInteractionWauPixelTimeMs(any())
    }

    @Test
    fun whenAllPixelsEligibleThenFiresAllPixels() = runTest {
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(baseDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(twentyEightDaysLaterMs)

        testee.attemptFirePixel()

        verify(mockPirPixelSender).reportInteractionDAU()
        verify(mockPirPixelSender).reportInteractionWAU()
        verify(mockPirPixelSender).reportInteractionMAU()
    }

    @Test
    fun whenLastPixelInFutureThenDoesNotFirePixels() = runTest {
        val futureDateMs = baseDate.plusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        whenever(mockPirRepository.getLastPirInteractionDauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockPirRepository.getLastPirInteractionWauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockPirRepository.getLastPirInteractionMauPixelTimeMs()).thenReturn(futureDateMs)
        whenever(mockCurrentTimeProvider.currentTimeMillis()).thenReturn(baseDateMs)

        testee.attemptFirePixel()

        verifyNoInteractions(mockPirPixelSender)
    }
}
