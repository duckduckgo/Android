package com.duckduckgo.webcompat.impl.messaging

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WebCompatContentScopeJsMessageHandlerTest {
    private val handler = WebCompatContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "webCompat",
            id = "myId",
            method = "webShare",
            params = JSONObject(),
        )

        handler.process(message, "secret", callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `allow all domains`() {
        val domains = handler.allowedDomains
        assertTrue(domains.isEmpty())
    }

    @Test
    fun `feature name is web compat`() {
        assertTrue(handler.featureName == "webCompat")
    }

    @Test
    fun `only contains valid methods`() {
        val methods = handler.methods
        assertTrue(methods.size == 4)
        assertTrue(methods[0] == "webShare")
        assertTrue(methods[1] == "permissionsQuery")
        assertTrue(methods[2] == "screenLock")
        assertTrue(methods[3] == "screenUnlock")
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
