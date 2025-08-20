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

package com.duckduckgo.networkprotection.impl.pixels

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_EXCELLENT_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_GOOD_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_MODERATE_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_POOR_LATENCY
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixelNames.NETP_REPORT_TERRIBLE_LATENCY
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository
import com.duckduckgo.networkprotection.store.NetPGeoswitchingRepository.UserPreferredLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class VpnLatencyPixelInterceptorTest {

    @Mock
    private lateinit var netPGeoswitchingRepository: NetPGeoswitchingRepository

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private lateinit var testee: VpnLatencyPixelInterceptor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = VpnLatencyPixelInterceptor(netPGeoswitchingRepository, appBuildConfig)
    }

    @Test
    fun whenLatencyPixelWithCustomLocationAndOsAbove15ThenAddBothParameters() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(countryCode = "US"),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(35) // API 35 (Android 15)
        val pixelUrl = String.format(PIXEL_TEMPLATE, NETP_REPORT_TERRIBLE_LATENCY.pixelName)

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals("custom", resultUrl.queryParameter("location"))
        assertEquals("true", resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun whenLatencyPixelWithNearestLocationAndOsBelow15ThenAddBothParameters() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(34) // API 34 (Android 14)
        val pixelUrl = String.format(PIXEL_TEMPLATE, NETP_REPORT_POOR_LATENCY.pixelName)

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals("nearest", resultUrl.queryParameter("location"))
        assertEquals("false", resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun whenLatencyPixelWithNullCountryCodeThenLocationIsNearest() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(countryCode = null),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(35) // API 35 (Android 15)
        val pixelUrl = String.format(PIXEL_TEMPLATE, NETP_REPORT_MODERATE_LATENCY.pixelName)

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals("nearest", resultUrl.queryParameter("location"))
        assertEquals("true", resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun whenGoodLatencyPixelThenParametersAreAdded() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(countryCode = "CA"),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(35) // API 35 (Android 15)
        val pixelUrl = String.format(PIXEL_TEMPLATE, NETP_REPORT_GOOD_LATENCY.pixelName)

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals("custom", resultUrl.queryParameter("location"))
        assertEquals("true", resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun whenExcellentLatencyPixelThenParametersAreAdded() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(countryCode = "FR"),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(34) // API 34 (Android 14)
        val pixelUrl = String.format(PIXEL_TEMPLATE, NETP_REPORT_EXCELLENT_LATENCY.pixelName)

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals("custom", resultUrl.queryParameter("location"))
        assertEquals("false", resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun whenNonLatencyPixelThenNoParametersAdded() = runTest {
        val pixelUrl = String.format(PIXEL_TEMPLATE, "m_netp_ev_enabled_d")

        val result = testee.intercept(FakeChain(pixelUrl))

        val resultUrl = result.request.url
        assertEquals(null, resultUrl.queryParameter("location"))
        assertEquals(null, resultUrl.queryParameter("os15Above"))
    }

    @Test
    fun verifyOriginalUrlIsPreservedWithAdditionalParameters() = runTest {
        whenever(netPGeoswitchingRepository.getUserPreferredLocation()).thenReturn(
            UserPreferredLocation(),
        )
        whenever(appBuildConfig.sdkInt).thenReturn(35) // API 35 (Android 15)
        val originalUrl = "https://improving.duckduckgo.com/t/${NETP_REPORT_TERRIBLE_LATENCY.pixelName}_android_phone?appVersion=5.135.0&test=1"

        val result = testee.intercept(FakeChain(originalUrl))

        val resultUrl = result.request.url
        // Check that original parameters are preserved
        assertEquals("5.135.0", resultUrl.queryParameter("appVersion"))
        assertEquals("1", resultUrl.queryParameter("test"))
        // Check that new parameters are added
        assertEquals("nearest", resultUrl.queryParameter("location"))
        assertEquals("true", resultUrl.queryParameter("os15Above"))
        // Check that the base URL is preserved
        assertTrue(resultUrl.toString().contains("improving.duckduckgo.com"))
        assertTrue(resultUrl.toString().contains(NETP_REPORT_TERRIBLE_LATENCY.pixelName))
    }

    companion object {
        private const val PIXEL_TEMPLATE = "https://improving.duckduckgo.com/t/%s_android_phone?appVersion=5.135.0&test=1"
    }
}
