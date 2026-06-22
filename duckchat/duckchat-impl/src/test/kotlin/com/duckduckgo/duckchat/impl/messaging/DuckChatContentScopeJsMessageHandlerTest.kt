package com.duckduckgo.duckchat.impl.messaging

import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class DuckChatContentScopeJsMessageHandlerTest {
    private val duckAiHostProvider = object : DuckAiHostProvider {}
    private val handler = DuckChatContentScopeJsMessageHandler(duckAiHostProvider).getJsMessageHandler()

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
        assertTrue(domains.size == 2)
        assertTrue(domains[0] == "duckduckgo.com")
        assertTrue(domains[1] == "duck.ai")
    }

    @Test
    fun `feature name is ai chat`() {
        assertTrue(handler.featureName == "aiChat")
    }

    @Test
    fun `only contains valid methods`() {
        val expected = listOf(
            "getAIChatNativeHandoffData",
            "getAIChatNativeConfigValues",
            "openAIChat",
            "closeAIChat",
            "openAIChatSettings",
            "responseState",
            "hideChatInput",
            "showChatInput",
            "reportMetric",
            "openKeyboard",
            "getAIChatPageContext",
            "togglePageContextTelemetry",
            "submitAIChatPageContext",
            "userDidAcceptTermsAndConditions",
            "getAIChatNativePrompt",
            "voiceSessionStarted",
            "voiceSessionEnded",
            "responseReceived",
            "showModelPicker",
            "disableChatInput",
            "enableChatInput",
            "focusChatInput",
        )
        // assert exact membership (size guards against accidental add/remove).
        assertEquals(expected.size, handler.methods.size)
        assertTrue(handler.methods.containsAll(expected))
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
