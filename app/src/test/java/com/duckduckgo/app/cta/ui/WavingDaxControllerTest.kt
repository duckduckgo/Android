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
import org.junit.Test
import org.mockito.kotlin.mock

class WavingDaxControllerTest {

    private val deviceInfo: DeviceInfo = mock()
    private val controller = WavingDaxController(showArrow = true, deviceInfo = deviceInfo)

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
}
