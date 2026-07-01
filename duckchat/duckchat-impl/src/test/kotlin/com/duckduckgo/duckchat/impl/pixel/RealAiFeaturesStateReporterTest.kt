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

package com.duckduckgo.duckchat.impl.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.duckchat.impl.DuckChatInternal
import com.duckduckgo.duckchat.impl.store.HideAiGeneratedImages
import com.duckduckgo.duckchat.impl.store.SearchAssistVisibility
import com.duckduckgo.settings.api.SerpSettingsDataProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealAiFeaturesStateReporterTest {

    private val duckChat: DuckChatInternal = mock()
    private val serpSettingsDataProvider: SerpSettingsDataProvider = mock()
    private val pixel: Pixel = mock()

    private val testee = RealAiFeaturesStateReporter(
        duckChat = duckChat,
        serpSettingsDataProvider = serpSettingsDataProvider,
        pixel = pixel,
    )

    private fun stubSettings(duckAiOn: Boolean, kbe: String?, kbj: String?) {
        whenever(duckChat.observeEnableDuckChatUserSetting()).thenReturn(flowOf(duckAiOn))
        whenever(serpSettingsDataProvider.observeSetting(SearchAssistVisibility.SERP_SETTINGS_KEY)).thenReturn(flowOf(kbe))
        whenever(serpSettingsDataProvider.observeSetting(HideAiGeneratedImages.SERP_SETTINGS_KEY)).thenReturn(flowOf(kbj))
    }

    @Test
    fun `when fully without ai then state pixel reports no_ai true`() = runTest {
        stubSettings(duckAiOn = false, kbe = "0", kbj = "1")

        testee.reportDailyState()

        verify(pixel).fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to "false",
                DuckChatPixelParameters.SEARCH_ASSIST to "0",
                DuckChatPixelParameters.HIDE_AI_IMAGES to "on",
                DuckChatPixelParameters.NO_AI to "true",
            ),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun `when duck ai on then no_ai false`() = runTest {
        stubSettings(duckAiOn = true, kbe = "0", kbj = "1")

        testee.reportDailyState()

        verify(pixel).fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to "true",
                DuckChatPixelParameters.SEARCH_ASSIST to "0",
                DuckChatPixelParameters.HIDE_AI_IMAGES to "on",
                DuckChatPixelParameters.NO_AI to "false",
            ),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun `when search assist not synced then defaults to 2 and not no_ai`() = runTest {
        stubSettings(duckAiOn = false, kbe = null, kbj = "1")

        testee.reportDailyState()

        verify(pixel).fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to "false",
                DuckChatPixelParameters.SEARCH_ASSIST to "2",
                DuckChatPixelParameters.HIDE_AI_IMAGES to "on",
                DuckChatPixelParameters.NO_AI to "false",
            ),
            type = Pixel.PixelType.Daily(),
        )
        verify(pixel, never()).fire(eq(DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE), any(), any(), any())
    }

    @Test
    fun `when stored kbe value is unrecognized then unrecognized pixel fired and default used`() = runTest {
        stubSettings(duckAiOn = false, kbe = "9", kbj = "1")

        testee.reportDailyState()

        verify(pixel).fire(DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE, type = Pixel.PixelType.Daily())
        verify(pixel).fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to "false",
                DuckChatPixelParameters.SEARCH_ASSIST to "2",
                DuckChatPixelParameters.HIDE_AI_IMAGES to "on",
                DuckChatPixelParameters.NO_AI to "false",
            ),
            type = Pixel.PixelType.Daily(),
        )
    }

    @Test
    fun `when stored kbj value is unrecognized then unrecognized pixel fired and treated as off`() = runTest {
        stubSettings(duckAiOn = false, kbe = "0", kbj = "7")

        testee.reportDailyState()

        verify(pixel).fire(DuckChatPixelName.SERP_SETTINGS_UNRECOGNIZED_VALUE, type = Pixel.PixelType.Daily())
        verify(pixel).fire(
            DuckChatPixelName.AI_FEATURES_STATE_DAILY,
            mapOf(
                DuckChatPixelParameters.DUCK_AI to "false",
                DuckChatPixelParameters.SEARCH_ASSIST to "0",
                DuckChatPixelParameters.HIDE_AI_IMAGES to "off",
                DuckChatPixelParameters.NO_AI to "false",
            ),
            type = Pixel.PixelType.Daily(),
        )
    }
}
