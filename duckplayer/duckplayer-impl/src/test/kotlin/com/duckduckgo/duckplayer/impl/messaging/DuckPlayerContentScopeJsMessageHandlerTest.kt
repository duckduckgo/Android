package com.duckduckgo.duckplayer.impl.messaging

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DuckPlayerContentScopeJsMessageHandlerTest {
    private val handler = DuckPlayerContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "duckPlayer",
            id = "myId",
            method = "getUserValues",
            params = JSONObject(),
        )

        handler.process(message, "secret", callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `only allow duckduckgo and youtube domains`() {
        val domains = handler.allowedDomains
        assertTrue(domains.size == 3)
        assertTrue(domains[0] == "duckduckgo.com")
        assertTrue(domains[1] == "youtube.com")
        assertTrue(domains[2] == "m.youtube.com")
    }

    @Test
    fun `feature name is duck player`() {
        assertTrue(handler.featureName == "duckPlayer")
    }

    @Test
    fun `only contains valid methods`() {
        val methods = handler.methods
        assertTrue(methods.size == 8)
        assertTrue(methods[0] == "getUserValues")
        assertTrue(methods[1] == "sendDuckPlayerPixel")
        assertTrue(methods[2] == "setUserValues")
        assertTrue(methods[3] == "openDuckPlayer")
        assertTrue(methods[4] == "openInfo")
        assertTrue(methods[5] == "initialSetup")
        assertTrue(methods[6] == "reportPageException")
        assertTrue(methods[7] == "reportInitException")
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
