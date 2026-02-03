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

package com.duckduckgo.contentscopescripts.impl.features.browseruilock

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class BrowserUiLockContentScopeJsMessageHandlerTest {
    private val handler = BrowserUiLockContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() = runTest {
        val params = JSONObject().apply {
            put("locked", true)
            put("signals", JSONObject().apply {
                put("overscrollBehavior", "none")
                put("overflow", "")
            })
        }

        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "browserUiLock",
            id = "myId",
            method = "uiLockChanged",
            params = params,
        )

        handler.process(message, mock(), callback)

        assertEquals(1, callback.counter)
        assertEquals("browserUiLock", callback.lastFeatureName)
        assertEquals("uiLockChanged", callback.lastMethod)
    }

    @Test
    fun `allow all domains`() = runTest {
        val domains = handler.allowedDomains
        assertTrue(domains.isEmpty())
    }

    @Test
    fun `feature name is browserUiLock`() = runTest {
        assertEquals("browserUiLock", handler.featureName)
    }

    @Test
    fun `only contains uiLockChanged method`() = runTest {
        val methods = handler.methods
        assertEquals(1, methods.size)
        assertEquals("uiLockChanged", methods.first())
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        var lastFeatureName: String? = null
        var lastMethod: String? = null

        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
            lastFeatureName = featureName
            lastMethod = method
        }
    }
}
