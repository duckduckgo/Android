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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.app.browser.WebViewPixelName
import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Phase
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class PageLoadTimeoutPixelsTest {

    private val pixel: Pixel = mock()
    private val testee = RealPageLoadTimeoutPixels(pixel)

    @Test
    fun whenTimeoutShownThenFirePixelWithPhase() {
        testee.fireTimeoutShown(Phase.COMMITTED_NO_CONTENT)

        verify(pixel).fire(WebViewPixelName.WEB_PAGE_LOAD_TIMEOUT_SHOWN, mapOf("phase" to "committed_no_content"))
    }

    @Test
    fun whenTimeoutShownNotCommittedThenPhaseIsNotCommitted() {
        testee.fireTimeoutShown(Phase.NOT_COMMITTED)

        verify(pixel).fire(WebViewPixelName.WEB_PAGE_LOAD_TIMEOUT_SHOWN, mapOf("phase" to "not_committed"))
    }

    @Test
    fun whenRecoveredThenElapsedTimeIsBucketed() {
        listOf(
            0L to "0-5s",
            4_999L to "0-5s",
            5_000L to "5-15s",
            14_999L to "5-15s",
            15_000L to "15-30s",
            29_999L to "15-30s",
            30_000L to "30-60s",
            59_999L to "30-60s",
            60_000L to "60s+",
            600_000L to "60s+",
        ).forEach { (elapsedMs, bucket) ->
            testee.fireTimeoutRecovered(elapsedMs)

            verify(pixel).fire(
                WebViewPixelName.WEB_PAGE_LOAD_TIMEOUT_RECOVERED,
                mapOf("recovered_after" to bucket),
            )
            clearInvocations(pixel)
        }
    }
}
