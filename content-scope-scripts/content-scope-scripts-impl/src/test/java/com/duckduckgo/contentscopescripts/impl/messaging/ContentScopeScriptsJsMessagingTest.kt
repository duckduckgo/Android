/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.contentscopescripts.impl.messaging

import android.webkit.WebView
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsMessageCallback
import com.duckduckgo.js.messaging.api.JsMessageHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class ContentScopeScriptsJsMessagingTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockWebView: WebView = mock()
    private val jsMessageHelper: JsMessageHelper = mock()
    private lateinit var contentScopeScriptsJsMessaging: ContentScopeScriptsJsMessaging

    @Before
    fun setUp() {
        contentScopeScriptsJsMessaging = ContentScopeScriptsJsMessaging(jsMessageHelper, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenRegisterInterfaceThenAddJsInterface() {
        contentScopeScriptsJsMessaging.register(mockWebView, callback)

        verify(mockWebView).addJavascriptInterface(any(), anyOrNull())
    }

    private val callback = object : JsMessageCallback(this) {
        var counter = 0
        override fun process(method: String) {
            counter++
        }
    }
}
