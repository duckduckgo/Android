/*
 * Copyright (c) 2024 DuckDuckGo
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

import com.duckduckgo.app.browser.uriloaded.DuckDuckGoUriLoadedManager
import com.duckduckgo.app.browser.uriloaded.UriLoadedPixelFeature
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DuckDuckGoUriLoadedManagerTest {

    private lateinit var testee: DuckDuckGoUriLoadedManager

    private val mockUriLoadedPixelFeature: UriLoadedPixelFeature = mock()
    private val mockUriLoadedKillSwitch: Toggle = mock()
    private val mockPixel: Pixel = mock()

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun setup() {
        whenever(mockUriLoadedPixelFeature.self()).thenReturn(mockUriLoadedKillSwitch)
    }

    @Test
    fun whenShouldSendUriLoadedPixelEnabledThenSendPixel() {
        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(true)

        initialize()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = false)

        verify(mockPixel).fire(AppPixelName.URI_LOADED)
    }

    @Test
    fun whenShouldSendUriLoadedPixelDisabledThenDoNotSendPixel() {
        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(false)

        initialize()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = false)

        verify(mockPixel, never()).fire(AppPixelName.URI_LOADED)
    }

    @Test
    fun whenPrivacyConfigDownloadedThenUpdateState() {
        initialize()

        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(true)
        testee.onPrivacyConfigDownloaded()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = false)

        verify(mockPixel).fire(AppPixelName.URI_LOADED)

        reset(mockPixel)

        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(false)
        testee.onPrivacyConfigDownloaded()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = false)

        verify(mockPixel, never()).fire(AppPixelName.URI_LOADED)
    }

    @Test
    fun whenSendUriLoadedPixelsWithDuckDuckGoUrlThenSerpPixelsAreFired() {
        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(true)

        initialize()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = true)

        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED)
        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED_DAILY, type = Pixel.PixelType.Daily())
        verify(mockPixel, never()).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED)
        verify(mockPixel, never()).fire(
            pixel = eq(AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED_DAILY),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenSendUriLoadedPixelsWithNonDuckDuckGoUrlThenWebsitePixelsAreFired() {
        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(true)

        initialize()
        testee.sendUriLoadedPixels(isDuckDuckGoUrl = false)

        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED)
        verify(mockPixel).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED_DAILY, type = Pixel.PixelType.Daily())
        verify(mockPixel, never()).fire(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED)
        verify(mockPixel, never()).fire(
            pixel = eq(AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED_DAILY),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    private fun initialize() {
        testee = DuckDuckGoUriLoadedManager(
            mockPixel,
            mockUriLoadedPixelFeature,
            TestScope(),
            coroutineTestRule.testDispatcherProvider,
            isMainProcess = true,
        )
    }
}
