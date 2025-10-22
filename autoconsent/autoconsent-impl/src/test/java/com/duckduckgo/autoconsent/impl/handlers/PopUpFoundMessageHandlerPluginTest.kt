/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.FakeSettingsRepository
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class PopUpFoundMessageHandlerPluginTest {

    private val mockCallback: AutoconsentCallback = mock()
    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)
    private val repository = FakeSettingsRepository()

    private val popupFoundHandler = PopUpFoundMessageHandlerPlugin(repository, mockPixelManager)

    @Test
    fun whenProcessIfMessageTypeIsNotPopUpFoundThenDoNothing() {
        popupFoundHandler.process("noMatching", "", webView, mockCallback)

        verify(mockCallback, never()).onFirstPopUpHandled()
    }

    @Test
    fun whenProcessIfSettingEnabledThenDoNothing() {
        repository.userSetting = true

        popupFoundHandler.process(popupFoundHandler.supportedTypes.first(), "", webView, mockCallback)

        verify(mockCallback, never()).onFirstPopUpHandled()
    }

    @Test
    fun whenProcessIfSettingDisabledAndCmpIsNotTopThenCallCallback() {
        repository.userSetting = false

        popupFoundHandler.process(popupFoundHandler.supportedTypes.first(), message("test"), webView, mockCallback)

        verify(mockCallback).onFirstPopUpHandled()
    }

    @Test
    fun whenProcessIfSettingDisabledAndCmpIsTopThenDoNothing() {
        repository.userSetting = false

        popupFoundHandler.process(popupFoundHandler.supportedTypes.first(), message("test-top"), webView, mockCallback)

        verify(mockCallback, never()).onFirstPopUpHandled()
    }

    @Test
    fun whenProcessAndMessageTypeIsPopUpFoundThenFirePopupFoundPixel() {
        popupFoundHandler.process(popupFoundHandler.supportedTypes.first(), message("test"), webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_POPUP_FOUND_DAILY)
    }

    private fun message(cmp: String): String {
        return """
            {"type":"${popupFoundHandler.supportedTypes.first()}", "cmp": "$cmp", "url": "http://example.com"}
        """.trimIndent()
    }
}
