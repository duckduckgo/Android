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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class WavingDaxControllerTest {

    private val deviceInfo: DeviceInfo = mock()
    private val testSpec = DaxBubbleCta.WavingDaxSpec(0f, 0f, 0f, 178f, 178f, false)
    private val controller = WavingDaxController(
        showArrow = true,
        deviceInfo = deviceInfo,
        wavingDaxSpec = testSpec,
        improvementsV2Enabled = true,
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

    // develop / V2-off daxFits tests

    @Test
    fun daxFits_false_whenHeadIntrudesIntoCardBody() {
        assertEquals(
            false,
            controller.daxFits(daxTop = 90, daxLeft = 0, daxRight = 40, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
    }

    @Test
    fun daxFits_false_whenInFinBandAndOverlapsFinHorizontally() {
        assertEquals(
            false,
            controller.daxFits(daxTop = 110, daxLeft = 60, daxRight = 90, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
    }

    @Test
    fun daxFits_true_whenInFinBandButHorizontallyClearOfFin() {
        assertEquals(
            true,
            controller.daxFits(daxTop = 110, daxLeft = 0, daxRight = 40, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
    }

    @Test
    fun daxFits_true_whenEntirelyBelowFinTip() {
        assertEquals(
            true,
            controller.daxFits(daxTop = 140, daxLeft = 60, daxRight = 90, cardBodyBottom = 100, finBottom = 130, finLeft = 50, finRight = 100),
        )
    }

    @Test
    fun whenV2DisabledThenDaxFitsRotatedRectLogicUsed() {
        val controller = WavingDaxController(
            showArrow = true,
            deviceInfo = deviceInfo,
            wavingDaxSpec = testSpec,
            improvementsV2Enabled = false,
        )
        // daxTop below cardBodyBottom and clear of the fin → fits
        assertTrue(
            controller.daxFits(
                daxTop = 100,
                daxLeft = 0,
                daxRight = 10,
                cardBodyBottom = 50,
                finBottom = 40,
                finLeft = 500,
                finRight = 600,
            ),
        )
        // daxTop intruding into the card body → does not fit
        assertFalse(
            controller.daxFits(
                daxTop = 30,
                daxLeft = 0,
                daxRight = 10,
                cardBodyBottom = 50,
                finBottom = 40,
                finLeft = 500,
                finRight = 600,
            ),
        )
    }
}
