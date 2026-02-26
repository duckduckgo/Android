package com.duckduckgo.subscriptions.impl.messaging

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class UseSubscriptionContentScopeJsMessageHandlerTest {
    private val handler = UseSubscriptionContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() = runTest {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "useSubscription",
            id = "myId",
            method = "getSubscription",
            params = JSONObject(),
        )

        handler.process(message, mock(), callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `only allow duckduckgo dot com or duck dot ai domains`() = runTest {
        val domains = handler.allowedDomains
        assertEquals(2, domains.size)
        assertEquals("duckduckgo.com", domains[0])
        assertEquals("duck.ai", domains[1])
    }

    @Test
    fun `feature name is useSubscription`() = runTest {
        assertEquals("useSubscription", handler.featureName)
    }

    @Test
    fun `contains expected methods`() = runTest {
        val methods = handler.methods
        assertTrue(methods.contains("getSubscription"))
        assertTrue(methods.contains("setSubscription"))
        assertTrue(methods.contains("getSubscriptionOptions"))
        assertTrue(methods.contains("getSubscriptionTierOptions"))
        assertTrue(methods.contains("setAuthTokens"))
        assertTrue(methods.contains("getAuthAccessToken"))
        assertTrue(methods.contains("getFeatureConfig"))
        assertTrue(methods.contains("subscriptionSelected"))
        assertTrue(methods.contains("activateSubscription"))
        assertTrue(methods.contains("backToSettings"))
        assertTrue(methods.contains("getAccessToken"))
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
