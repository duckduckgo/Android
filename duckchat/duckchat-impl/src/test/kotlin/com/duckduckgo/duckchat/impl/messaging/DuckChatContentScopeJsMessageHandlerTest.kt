package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class DuckChatContentScopeJsMessageHandlerTest {
    private val handler = DuckChatContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "aiChat",
            id = "myId",
            method = "getAIChatNativeHandoffData",
            params = JSONObject(),
        )

        handler.process(message, mock(), callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `only allow duckduckgo dot com domains`() {
        val domains = handler.allowedDomains
        assertTrue(domains.size == 1)
        assertTrue(domains.first() == "duckduckgo.com")
    }

    @Test
    fun `feature name is ai chat`() {
        assertTrue(handler.featureName == "aiChat")
    }

    @Test
    fun `only contains valid methods`() {
        val methods = handler.methods
        assertTrue(methods.size == 10)
        assertTrue(methods[0] == "getAIChatNativeHandoffData")
        assertTrue(methods[1] == "getAIChatNativeConfigValues")
        assertTrue(methods[2] == "openAIChat")
        assertTrue(methods[3] == "closeAIChat")
        assertTrue(methods[4] == "openAIChatSettings")
        assertTrue(methods[5] == "responseState")
        assertTrue(methods[6] == "hideChatInput")
        assertTrue(methods[7] == "showChatInput")
        assertTrue(methods[8] == "reportMetric")
        assertTrue(methods[9] == "openKeyboard")
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
