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

package com.duckduckgo.app.pixels.surface

import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MobileBrowserSurfacePixelInterceptorTest {

    private val surfacePixelPlugin = mock<SurfacePixelPlugin>()
    private val fakePluginPoint = FakePluginPoint(listOf(surfacePixelPlugin))

    private val productSurfaceTelemetryFeature = FakeFeatureToggleFactory.create(ProductSurfaceTelemetryFeature::class.java)

    private val interceptor = MobileBrowserSurfacePixelInterceptor(
        fakePluginPoint,
        productSurfaceTelemetryFeature,
    )

    @Test
    fun whenFeatureIsDisabledAndPixelMatchesThenPixelIsDropped() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = false))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
                "m_product_telemetry_surface_usage_website",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_serp_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(500, response.code)
        assertEquals("Dropped mobile surfaces pixel", response.message)
        assertEquals("Mobile surfaces pixel dropped", response.body?.string())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelMatchesThenPixelProceeds() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = true))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
                "m_product_telemetry_surface_usage_website",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_serp_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    @Test
    fun whenFeatureIsEnabledAndPixelDoesNotMatchThenPixelProceeds() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = true))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
                "m_product_telemetry_surface_usage_website",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_some_other_pixel_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    @Test
    fun whenFeatureIsDisabledAndPixelDoesNotMatchThenPixelProceeds() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = false))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
                "m_product_telemetry_surface_usage_website",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_some_other_pixel_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    @Test
    fun whenNoPluginsRegisteredThenPixelAlwaysProceeds() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = false))

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_serp_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    @Test
    fun whenPixelStartsWithRegisteredNameThenItMatches() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = false))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
            ),
        )

        // Pixel with form factor suffix should still match
        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_serp_phone"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(500, response.code)
        assertEquals("Dropped mobile surfaces pixel", response.message)
    }

    @Test
    fun whenPixelNameIsExactMatchThenItMatches() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = true))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_dau",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_dau"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    @Test
    fun whenFeatureIsDisabledAndPixelHasQueryParamsThenPixelIsDropped() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = false))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_serp",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_serp_phone?appVersion=5.123.0"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(500, response.code)
        assertEquals("Dropped mobile surfaces pixel", response.message)
    }

    @Test
    fun whenFeatureIsEnabledAndPixelHasQueryParamsThenPixelProceeds() {
        productSurfaceTelemetryFeature.self().setRawStoredState(Toggle.State(enable = true))
        whenever(surfacePixelPlugin.names()).thenReturn(
            listOf(
                "m_product_telemetry_surface_usage_website",
            ),
        )

        val startUrl = "$URL_PIXEL_BASE/m_product_telemetry_surface_usage_website_tablet?appVersion=5.123.0"
        val response = interceptor.intercept(FakeChain(startUrl))

        assertEquals(startUrl, response.request.url.toString())
    }

    private class FakePluginPoint(val plugins: List<SurfacePixelPlugin>) : PluginPoint<SurfacePixelPlugin> {
        override fun getPlugins(): Collection<SurfacePixelPlugin> = plugins
    }

    companion object {
        private const val URL_PIXEL_BASE = "https://improving.duckduckgo.com/t"
    }
}
