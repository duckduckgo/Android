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

package com.duckduckgo.clicktoload.impl

import android.webkit.WebView
import com.duckduckgo.clicktoload.api.ClickToLoad
import com.duckduckgo.clicktoload.impl.handlers.PlaceholderHandler
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class RealClickToLoadTest {
    lateinit var testee: ClickToLoad

    private val mockContentScopeScripts: ContentScopeScripts = mock()
    private val mockWebView: WebView = mock()

    @Before
    fun before() {
        testee = RealClickToLoad(mockContentScopeScripts, PlaceholderHandler())
    }

    @Test
    fun whenDisplayPlaceHoldersThenSendMessageToContentScopeScripts() {
        testee.displayClickToLoadPlaceholders(mockWebView, "block-ctl-fb")
        verify(mockContentScopeScripts).sendMessage(
            "{\"feature\":\"clickToLoad\",\"messageType\":\"displayClickToLoadPlaceholders\",\"options\":{\"action\":\"block-ctl-fb\"}," +
                "\"type\":\"update\"}",
            mockWebView,
        )
    }
}
