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

package com.duckduckgo.autoconsent.impl.pixels

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AutoconsentPixelManagerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val mockPixel: Pixel = mock()
    private val mockAutoconsentFeature: AutoconsentFeature = mock()
    private val mockToggle: Toggle = mock()
    private lateinit var pixelManager: RealAutoconsentPixelManager

    @Before
    fun setup() {
        whenever(mockAutoconsentFeature.cpmPixels()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)
        pixelManager = RealAutoconsentPixelManager(
            mockPixel,
            coroutineTestRule.testScope,
            mockAutoconsentFeature,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenFireDailyPixelThenFirePixelWithDailyType() = runTest {
        val pixelName = AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY

        pixelManager.fireDailyPixel(pixelName)
        advanceUntilIdle()

        verify(mockPixel).fire(eq(pixelName), eq(emptyMap()), eq(emptyMap()), eq(Daily()))
    }

    @Test
    fun whenCpmPixelsDisabledThenNoPixelsFired() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(false)
        val pixelName = AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY

        pixelManager.fireDailyPixel(pixelName)
        advanceUntilIdle()

        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedByPatternsProcessedWithNewInstanceIdThenReturnFalse() {
        val instanceId = "id-123-abc"

        val result = pixelManager.isDetectedByPatternsProcessed(instanceId)

        assertFalse(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedByPatternsProcessedWithProcessedInstanceIdThenReturnTrue() {
        val instanceId = "id-123-abc"
        pixelManager.markDetectedByPatternsProcessed(instanceId)

        val result = pixelManager.isDetectedByPatternsProcessed(instanceId)

        assertTrue(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedByBothProcessedWithNewInstanceIdThenReturnFalse() {
        val instanceId = "id-123-abc"

        val result = pixelManager.isDetectedByBothProcessed(instanceId)

        assertFalse(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedByBothProcessedWithProcessedInstanceIdThenReturnTrue() {
        val instanceId = "id-123-abc"
        pixelManager.markDetectedByBothProcessed(instanceId)

        val result = pixelManager.isDetectedByBothProcessed(instanceId)

        assertTrue(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedOnlyRulesProcessedWithNewInstanceIdThenReturnFalse() {
        val instanceId = "id-123-abc"

        val result = pixelManager.isDetectedOnlyRulesProcessed(instanceId)

        assertFalse(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenIsDetectedOnlyRulesProcessedWithProcessedInstanceIdThenReturnTrue() {
        val instanceId = "id-123-abc"
        pixelManager.markDetectedOnlyRulesProcessed(instanceId)

        val result = pixelManager.isDetectedOnlyRulesProcessed(instanceId)

        assertTrue(result)
        verifyNoMoreInteractions(mockPixel)
    }

    @Test
    fun whenFireDailyPixelsThenSummaryPixelContainsAllCounts() = runTest {
        val pixel1 = AutoConsentPixel.AUTOCONSENT_INIT_DAILY
        val pixel2 = AutoConsentPixel.AUTOCONSENT_POPUP_FOUND_DAILY

        pixelManager.fireDailyPixel(pixel1)
        pixelManager.fireDailyPixel(pixel2)
        pixelManager.fireDailyPixel(pixel1)

        verify(mockPixel, times(2)).fire(eq(pixel1), eq(emptyMap()), eq(emptyMap()), eq(Daily()))
        verify(mockPixel).fire(eq(pixel2), eq(emptyMap()), eq(emptyMap()), eq(Daily()))

        advanceTimeBy(120000L)
        advanceUntilIdle()

        verify(mockPixel).enqueueFire(
            eq(AutoConsentPixel.AUTOCONSENT_SUMMARY),
            argThat { parameters ->
                parameters[pixel1.pixelName] == "2" && parameters[pixel2.pixelName] == "1"
            },
            eq(emptyMap()),
        )
    }

    @Test
    fun whenFireDailyPixelAfterSummaryThenScheduleNewSummary() = runTest {
        val pixel1 = AutoConsentPixel.AUTOCONSENT_INIT_DAILY
        val pixel2 = AutoConsentPixel.AUTOCONSENT_POPUP_FOUND_DAILY

        pixelManager.fireDailyPixel(pixel1)
        advanceTimeBy(120000L)
        advanceUntilIdle()

        pixelManager.fireDailyPixel(pixel2)
        advanceTimeBy(120000L)
        advanceUntilIdle()

        verify(mockPixel).enqueueFire(
            eq(AutoConsentPixel.AUTOCONSENT_SUMMARY),
            argThat { parameters -> parameters[pixel1.pixelName] == "1" },
            eq(emptyMap()),
        )
        verify(mockPixel).enqueueFire(
            eq(AutoConsentPixel.AUTOCONSENT_SUMMARY),
            argThat { parameters -> parameters[pixel2.pixelName] == "1" },
            eq(emptyMap()),
        )
    }
}
