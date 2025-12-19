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

package com.duckduckgo.app.attributed.metrics.pixels

import com.duckduckgo.common.test.api.FakeChain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AttributedMetricPixelInterceptorTest {

    private lateinit var interceptor: AttributedMetricPixelInterceptor

    @Before
    fun setup() {
        interceptor = AttributedMetricPixelInterceptor()
    }

    @Test
    fun `when pixel is attributed metric with phone form factor then form factor is removed from url`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/attributed_metric_test_android_phone"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/attributed_metric_test_android",
            result.request.url.toString(),
        )
    }

    @Test
    fun `when pixel is attributed metric with tablet form factor then form factor is removed from url`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/attributed_metric_test_android_tablet"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/attributed_metric_test_android",
            result.request.url.toString(),
        )
    }

    @Test
    fun `when pixel is attributed metric with query params then form factor is removed and params preserved`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/attributed_metric_test_android_phone?param1=value1&param2=value2"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/attributed_metric_test_android?param1=value1&param2=value2",
            result.request.url.toString(),
        )
    }

    @Test
    fun `when pixel is attributed metric with tablet and query params then form factor is removed and params preserved`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/attributed_metric_test_android_tablet?param1=value1"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/attributed_metric_test_android?param1=value1",
            result.request.url.toString(),
        )
    }

    @Test
    fun `when pixel is not an attributed metric then url is unchanged`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/other_pixel_android_phone"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/other_pixel_android_phone",
            result.request.url.toString(),
        )
    }

    @Test
    fun `when pixel already has android without form factor then url is unchanged`() {
        val pixelUrl = "https://improving.duckduckgo.com/t/attributed_metric_test_android"

        val result = interceptor.intercept(FakeChain(pixelUrl))

        assertEquals(
            "https://improving.duckduckgo.com/t/attributed_metric_test_android",
            result.request.url.toString(),
        )
    }
}
