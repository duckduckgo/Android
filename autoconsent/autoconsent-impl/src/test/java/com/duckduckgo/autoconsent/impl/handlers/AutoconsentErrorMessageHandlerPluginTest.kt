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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

@RunWith(AndroidJUnit4::class)
class AutoconsentErrorMessageHandlerPluginTest {

    private val mockCallback: AutoconsentCallback = mock()
    private val mockPixelManager: AutoconsentPixelManager = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val errorHandler = AutoconsentErrorMessageHandlerPlugin(mockPixelManager)

    @Test
    fun whenProcessAndMessageTypeIsNotAutoconsentErrorThenDoNothing() {
        errorHandler.process("noMatching", "", webView, mockCallback)

        verifyNoInteractions(mockCallback)
        verifyNoInteractions(mockPixelManager)
    }

    @Test
    fun whenProcessAndMessageTypeIsAutoconsentErrorThenFireErrorMultiplePopupsPixel() {
        errorHandler.process("autoconsentError", "", webView, mockCallback)

        verify(mockPixelManager).fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_MULTIPLE_POPUPS_DAILY)
    }
}
