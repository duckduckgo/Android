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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.FakeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PopUpFoundMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockCallback: AutoconsentCallback = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)
    private val repository = FakeRepository()

    private val popupFoundHandler = PopUpFoundMessageHandlerPlugin(coroutineRule.testScope, coroutineRule.testDispatcherProvider, repository)

    @Test
    fun whenProcessIfMessageTypeIsNotPopUpFoundThenDoNothing() {
        popupFoundHandler.process("noMatching", "", webView, mockCallback)

        assertNull(Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessIfSettingEnabledThenDoNothing() {
        repository.userSetting = true

        popupFoundHandler.process(popupFoundHandler.type, "", webView, mockCallback)

        assertNull(Shadows.shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessIfSettingDisabledThenCallCallback() {
        repository.userSetting = false

        popupFoundHandler.process(popupFoundHandler.type, "", webView, mockCallback)

        verify(mockCallback).onFirstPopUpHandled(any(), any())
    }

    @Test
    fun whenProcessIfSettingDisabledThenFistPopupHandledSetToTrue() {
        repository.userSetting = false

        popupFoundHandler.process(popupFoundHandler.type, "", webView, mockCallback)

        assertTrue(repository.firstPopupHandled)
    }

}
