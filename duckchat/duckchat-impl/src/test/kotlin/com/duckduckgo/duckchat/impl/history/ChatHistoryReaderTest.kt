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

package com.duckduckgo.duckchat.impl.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.WebViewModeInitializer
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.duckchat.impl.helper.DuckChatJSHelper
import com.duckduckgo.duckchat.impl.ui.DuckChatWebViewClient
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessaging
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.json.JSONObject
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class ChatHistoryReaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contentScopeScripts: JsMessaging = mock()
    private val duckChatWebViewClient: DuckChatWebViewClient = mock()
    private val duckChatJSHelper: DuckChatJSHelper = mock()
    private val cookieManagerProvider: CookieManagerProvider = mock()
    private val webViewModeInitializer: WebViewModeInitializer = mock()

    private fun createTestee(browserMode: BrowserMode) = RealChatHistoryReader(
        context,
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
        contentScopeScripts,
        duckChatWebViewClient,
        duckChatJSHelper,
        cookieManagerProvider,
        webViewModeInitializer,
        browserMode,
    )

    @Before
    fun setup() {
        whenever(webViewModeInitializer.bind(any(), any())).thenReturn(Result.success(Unit))
    }

    @Test
    fun whenInFireModeThenForwardsFireBrowserModeToTheJsHelper() {
        verifyReaderForwardsItsBrowserMode(BrowserMode.FIRE)
    }

    @Test
    fun whenInRegularModeThenForwardsRegularBrowserModeToTheJsHelper() {
        verifyReaderForwardsItsBrowserMode(BrowserMode.REGULAR)
    }

    // refresh() builds the headless WebView and registers the content-scope-scripts bridge callback. Drive
    // everything from plain test code via the rule's scheduler (so advanceUntilIdle can drain the reader's
    // launched forwarding coroutine), then assert the reader forwards its own browser mode to the JS helper.
    private fun verifyReaderForwardsItsBrowserMode(mode: BrowserMode) {
        val testee = createTestee(mode)

        coroutineRule.testScope.launch { testee.refresh() }
        coroutineRule.testScope.advanceUntilIdle()

        val callbackCaptor = argumentCaptor<JsMessageCallback>()
        verify(contentScopeScripts).register(any(), callbackCaptor.capture())
        callbackCaptor.firstValue!!.process(FEATURE, METHOD, ID, JSONObject())
        coroutineRule.testScope.advanceUntilIdle()

        verifyBlocking(duckChatJSHelper) {
            processJsCallbackMessage(eq(FEATURE), eq(METHOD), eq(ID), anyOrNull(), any(), any(), any(), eq(mode))
        }
    }

    companion object {
        private const val FEATURE = "aiChat"
        private const val METHOD = "someMethod"
        private const val ID = "someId"
    }
}
