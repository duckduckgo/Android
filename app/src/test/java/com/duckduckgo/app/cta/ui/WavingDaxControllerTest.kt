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

package com.duckduckgo.app.cta.ui

import com.duckduckgo.common.utils.device.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock

class WavingDaxControllerTest {

    private val deviceInfo: DeviceInfo = mock()
    private val controller = WavingDaxController(
        showArrow = true,
        deviceInfo = deviceInfo,
        wavingDaxSpec = DaxBubbleCta.WavingDaxSpec(
            rotationDegrees = 0f,
            translationXDp = 0f,
            translationYDp = 0f,
            minHeightDp = 178f,
            maxHeightDp = 178f,
            anchorToCardOnTablet = false,
        ),
    )

    @Test
    fun daxFitHeight_clampsToMax_whenRoomy() {
        assertEquals(800, controller.daxFitHeight(usableBottom = 2000, cardBottom = 1000, marginPx = 0, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxFitHeight_returnsAvailable_betweenMinAndMax() {
        assertEquals(500, controller.daxFitHeight(usableBottom = 1600, cardBottom = 1000, marginPx = 100, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxFitHeight_hidesBelowFloor() {
        assertNull(controller.daxFitHeight(usableBottom = 1300, cardBottom = 1000, marginPx = 0, minHeightPx = 400, maxHeightPx = 800))
    }

    @Test
    fun daxHorizontalScale_isOne_atFullHeight() {
        assertEquals(1f, controller.daxHorizontalScale(heightPx = 800, maxHeightPx = 800), 0f)
    }

    @Test
    fun daxHorizontalScale_shrinksProportionally_belowFullHeight() {
        assertEquals(0.5f, controller.daxHorizontalScale(heightPx = 400, maxHeightPx = 800), 0f)
    }

    @Test
    fun daxHorizontalScale_isOne_whenMaxHeightNonPositive() {
        assertEquals(1f, controller.daxHorizontalScale(heightPx = 400, maxHeightPx = 0), 0f)
    }
}
