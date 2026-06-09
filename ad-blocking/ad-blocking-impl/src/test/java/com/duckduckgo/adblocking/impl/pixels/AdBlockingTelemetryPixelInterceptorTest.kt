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

import com.duckduckgo.adblocking.impl.domain.AdBlockingState
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.common.test.api.FakeChain
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdBlockingTelemetryPixelInterceptorTest {

    private val statusChecker = mock<AdBlockingStatusChecker>()
    private val interceptor = AdBlockingTelemetryPixelInterceptor(statusChecker)

    @Test
    fun whenUserEnabledAndPixelMatchesThenPixelProceeds() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.UserEnabled)

        val url = "$PIXEL_BASE/webTelemetry_youtubeBuffering_test_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(url, response.request.url.toString())
    }

    @Test
    fun whenEnabledByDefaultAndPixelMatchesThenPixelIsDropped() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.Default)

        val url = "$PIXEL_BASE/webTelemetry_youtubeBuffering_test_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(200, response.code)
        assertEquals("Dropped ad blocking telemetry pixel", response.message)
        assertEquals("Ad blocking telemetry pixel dropped", response.body?.string())
    }

    @Test
    fun whenDisabledAndPixelMatchesThenPixelIsDropped() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Disabled)

        val url = "$PIXEL_BASE/webTelemetry_youtubeBuffering_test_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(200, response.code)
        assertEquals("Dropped ad blocking telemetry pixel", response.message)
    }

    @Test
    fun whenNotConsentedAndPixelMatchesWithCamelCasePrefixThenPixelIsDropped() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.Default)

        val url = "$PIXEL_BASE/webTelemetry_youTubeBuffering_test_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(200, response.code)
        assertEquals("Dropped ad blocking telemetry pixel", response.message)
    }

    @Test
    fun whenNotConsentedAndPixelDoesNotMatchThenPixelProceeds() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Disabled)

        val url = "$PIXEL_BASE/m_unrelated_pixel_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(url, response.request.url.toString())
    }

    @Test
    fun whenUserEnabledAndPixelDoesNotMatchThenPixelProceeds() {
        whenever(statusChecker.currentState()).thenReturn(AdBlockingState.Enabled.UserEnabled)

        val url = "$PIXEL_BASE/m_unrelated_pixel_android_phone"
        val response = interceptor.intercept(FakeChain(url))

        assertEquals(url, response.request.url.toString())
    }

    companion object {
        private const val PIXEL_BASE = "https://improving.duckduckgo.com/t"
    }
}
