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

package com.duckduckgo.cookies.impl.clearing

import android.webkit.CookieManager
import android.webkit.ValueCallback
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class CookiesDataClearingPluginTest {
    @get:Rule val coroutineRule = CoroutineTestRule()

    private val fireCookieManager: CookieManager = mock {
        on { removeAllCookies(any()) } doAnswer { (it.getArgument<ValueCallback<Boolean>>(0)).onReceiveValue(true) }
    }
    private val provider: BrowserModeDataProvider<CookieManager> = mock {
        on { forMode(BrowserMode.FIRE) } doReturn fireCookieManager
    }
    private val testee = CookiesDataClearingPlugin(provider, coroutineRule.testDispatcherProvider)

    @Test fun whenFireBrowserDataThenRemovesAllFireCookiesAndFlushes() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.FIRE)))
        verify(fireCookieManager).removeAllCookies(any())
        verify(fireCookieManager).flush()
    }

    @Test fun whenBrowserDataAllThenRemovesFireCookies() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.All))
        verify(fireCookieManager).removeAllCookies(any())
    }

    @Test fun whenRegularBrowserDataThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.BrowserData.AllForMode(BrowserMode.REGULAR)))
        verify(provider, never()).forMode(any())
    }

    @Test fun whenUnrelatedTypeThenDoesNothing() = runTest {
        testee.onClearData(setOf(ClearableData.Tabs.AllForMode(BrowserMode.FIRE)))
        verify(provider, never()).forMode(any())
    }
}
