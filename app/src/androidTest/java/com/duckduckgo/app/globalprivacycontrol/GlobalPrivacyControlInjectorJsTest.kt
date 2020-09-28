/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.globalprivacycontrol

import android.webkit.WebView
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test

class GlobalPrivacyControlInjectorJsTest {

    private val mockSettingsStore: SettingsDataStore = mock()
    lateinit var testee: GlobalPrivacyControlInjectorJs

    @Before
    fun setup() {
        testee = GlobalPrivacyControlInjectorJs(mockSettingsStore)
    }

    @UiThreadTest
    @Test
    fun whenInjectDoNotSellToDomAndGcpIsEnabledThenInjectToDom() {
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(true)

        testee.injectDoNotSellToDom(webView)

        verify(webView).evaluateJavascript(any(), anyOrNull())
    }

    @UiThreadTest
    @Test
    fun whenInjectDoNotSellToDomAndGcpIsNotEnabledThenDoNotInjectToDom() {
        val webView = spy(WebView(InstrumentationRegistry.getInstrumentation().targetContext))
        whenever(mockSettingsStore.globalPrivacyControlEnabled).thenReturn(false)

        testee.injectDoNotSellToDom(webView)

        verifyZeroInteractions(webView)
    }
}
