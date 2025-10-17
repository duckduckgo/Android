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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AutoconsentPixelManagerTest {

    private val mockPixel: Pixel = mock()
    private lateinit var pixelManager: RealAutoconsentPixelManager

    @Before
    fun setup() {
        pixelManager = RealAutoconsentPixelManager(mockPixel)
    }

    @Test
    fun whenFireDailyPixelThenFirePixelWithDailyType() {
        val pixelName = AutoConsentPixel.AUTOCONSENT_DETECTED_BY_PATTERNS_DAILY

        pixelManager.fireDailyPixel(pixelName)

        verify(mockPixel).fire(pixelName, type = Daily())
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
}
