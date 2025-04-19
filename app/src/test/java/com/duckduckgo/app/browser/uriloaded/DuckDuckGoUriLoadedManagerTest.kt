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
import org.mockito.kotlin.*

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
        testee.sendUriLoadedPixel()

        verify(mockPixel).fire(AppPixelName.URI_LOADED)
    }

    @Test
    fun whenShouldSendUriLoadedPixelDisabledThenDoNotSendPixel() {
        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(false)

        initialize()
        testee.sendUriLoadedPixel()

        verify(mockPixel, never()).fire(AppPixelName.URI_LOADED)
    }

    @Test
    fun whenPrivacyConfigDownloadedThenUpdateState() {
        initialize()

        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(true)
        testee.onPrivacyConfigDownloaded()
        testee.sendUriLoadedPixel()

        verify(mockPixel).fire(AppPixelName.URI_LOADED)

        reset(mockPixel)

        whenever(mockUriLoadedKillSwitch.isEnabled()).thenReturn(false)
        testee.onPrivacyConfigDownloaded()
        testee.sendUriLoadedPixel()

        verify(mockPixel, never()).fire(AppPixelName.URI_LOADED)
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
