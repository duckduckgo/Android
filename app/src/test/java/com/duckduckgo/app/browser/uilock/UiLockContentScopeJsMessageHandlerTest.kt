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

package com.duckduckgo.app.browser.uilock

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

class UiLockContentScopeJsMessageHandlerTest {
    private val handler = UiLockContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `feature name is browserUiLock`() {
        assertEquals(BROWSER_UI_LOCK_FEATURE_NAME, handler.featureName)
    }

    @Test
    fun `allowed domains is empty`() {
        assertTrue(handler.allowedDomains.isEmpty())
    }

    @Test
    fun `methods contains only uiLockChanged`() {
        val methods = handler.methods
        assertEquals(1, methods.size)
        assertEquals("uiLockChanged", methods[0])
    }

    @Test
    fun `when uiLockChanged message sent then callback is called`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = BROWSER_UI_LOCK_FEATURE_NAME,
            id = "myId",
            method = "uiLockChanged",
            params = JSONObject("""{ "locked": true }"""),
        )

        handler.process(message, mock(), callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `when uiLockChanged message sent then callback receives correct feature name`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = BROWSER_UI_LOCK_FEATURE_NAME,
            id = "testId",
            method = "uiLockChanged",
            params = JSONObject("""{ "locked": true }"""),
        )

        handler.process(message, mock(), callback)

        assertEquals(BROWSER_UI_LOCK_FEATURE_NAME, callback.lastFeatureName)
    }

    @Test
    fun `when uiLockChanged message sent then callback receives correct method`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = BROWSER_UI_LOCK_FEATURE_NAME,
            id = "testId",
            method = "uiLockChanged",
            params = JSONObject("""{ "locked": true }"""),
        )

        handler.process(message, mock(), callback)

        assertEquals("uiLockChanged", callback.lastMethod)
    }

    @Test
    fun `when uiLockChanged message sent then callback receives correct id`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = BROWSER_UI_LOCK_FEATURE_NAME,
            id = "testId",
            method = "uiLockChanged",
            params = JSONObject("""{ "locked": true }"""),
        )

        handler.process(message, mock(), callback)

        assertEquals("testId", callback.lastId)
    }

    @Test
    fun `when message sent with null callback then no exception`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = BROWSER_UI_LOCK_FEATURE_NAME,
            id = "testId",
            method = "uiLockChanged",
            params = JSONObject("""{ "locked": true }"""),
        )

        handler.process(message, mock(), null)
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        var lastFeatureName: String? = null
        var lastMethod: String? = null
        var lastId: String? = null

        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
            lastFeatureName = featureName
            lastMethod = method
            lastId = id
        }
    }
}
