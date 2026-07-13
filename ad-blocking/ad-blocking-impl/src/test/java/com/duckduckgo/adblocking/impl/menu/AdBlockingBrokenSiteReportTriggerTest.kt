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

package com.duckduckgo.adblocking.impl.menu

import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_BREAKAGE_REPORT_ENTERED_COUNT
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames.AD_BLOCKING_BREAKAGE_REPORT_ENTERED_DAILY
import com.duckduckgo.app.statistics.pixels.Pixel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class AdBlockingBrokenSiteReportTriggerTest {

    private val pixel: Pixel = mock()

    private val trigger = AdBlockingBrokenSiteReportTrigger(pixel)

    @Test
    fun whenReportRequestedThenFiresBreakageReportEnteredPixels() {
        trigger.requestReport()

        verify(pixel).fire(AD_BLOCKING_BREAKAGE_REPORT_ENTERED_DAILY, type = Pixel.PixelType.Daily())
        verify(pixel).fire(AD_BLOCKING_BREAKAGE_REPORT_ENTERED_COUNT)
    }

    @Test
    fun whenReportRequestedThenEmitsReportRequest() = runTest {
        trigger.observeReportRequests().test {
            trigger.requestReport()

            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }
}
