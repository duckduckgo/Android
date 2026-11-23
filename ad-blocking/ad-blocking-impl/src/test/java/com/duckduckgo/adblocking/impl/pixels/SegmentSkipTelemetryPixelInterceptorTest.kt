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

package com.duckduckgo.adblocking.impl.pixels

import com.duckduckgo.adblocking.impl.segmentskip.SegmentSkipUserPreferences
import com.duckduckgo.common.test.api.FakeChain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SegmentSkipTelemetryPixelInterceptorTest {
    private val preferences = mock<SegmentSkipUserPreferences>()
    private val interceptor = SegmentSkipTelemetryPixelInterceptor(preferences)

    @Test
    fun whenConsentAbsentThenNewTelemetryIsDroppedSuccessfully() {
        for (event in newTelemetryNames) {
            val response = interceptor.intercept(FakeChain("$BASE/$event", expectedResponseCode = 418))
            assertEquals(200, response.code)
            assertEquals("Dropped Segment Skip telemetry", response.message)
        }
    }

    @Test
    fun whenConsentPresentThenNewTelemetryProceeds() {
        whenever(preferences.hasConsent()).thenReturn(true)
        for (event in newTelemetryNames) {
            assertEquals(418, interceptor.intercept(FakeChain("$BASE/$event", expectedResponseCode = 418)).code)
        }
    }

    @Test
    fun whenNamesOnlyResembleEventsThenTheyProceedRegardlessOfConsent() {
        for (consent in listOf(false, true)) {
            whenever(preferences.hasConsent()).thenReturn(consent)
            for (event in listOf(
                "segmentSkip_errors_hour_android_phone",
                "segmentSkip_errors_hour_android_tablet",
                "segmentSkip_errors_day_android_phone",
                "segmentSkip_errors_day_android_tablet",
                "segmentSkip_errors_hourly_android_phone",
                "segmentSkip_errors_day_extra_android_phone",
                "webTelemetry_youtubeInterference_day.buffering_android_phone",
                "other_android_phone",
            )) {
                val url = "$BASE/$event"
                assertEquals(418, interceptor.intercept(FakeChain(url, expectedResponseCode = 418)).code)
            }
        }
    }

    private companion object {
        const val BASE = "https://improving.duckduckgo.com/t"
        val newTelemetryNames = listOf(
            "webTelemetry_segmentSkip_errors_hour_android_phone",
            "webTelemetry_segmentSkip_errors_day_android_tablet",
            "WEBTELEMETRY_SEGMENTSKIP_other_android_tablet",
        )
    }
}
