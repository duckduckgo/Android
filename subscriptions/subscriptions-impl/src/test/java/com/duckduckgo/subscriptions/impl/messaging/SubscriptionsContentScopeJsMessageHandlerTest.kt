package com.duckduckgo.subscriptions.impl.messaging

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SubscriptionsContentScopeJsMessageHandlerTest {
    private val handler = SubscriptionsContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() = runTest {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "subscriptions",
            id = "myId",
            method = "handshake",
            params = JSONObject(),
        )

        handler.process(message, "secret", callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `only allow duckduckgo dot com domains`() = runTest {
        val domains = handler.allowedDomains
        assertTrue(domains.size == 1)
        assertTrue(domains.first() == "duckduckgo.com")
    }

    @Test
    fun `feature name is subscriptions`() = runTest {
        assertTrue(handler.featureName == "subscriptions")
    }

    @Test
    fun `only contains valid methods`() = runTest {
        val methods = handler.methods
        assertTrue(methods.size == 7)
        assertTrue(methods.contains("handshake"))
        assertTrue(methods.contains("subscriptionDetails"))
        assertTrue(methods.contains("getAuthAccessToken"))
        assertTrue(methods.contains("getFeatureConfig"))
        assertTrue(methods.contains("backToSettings"))
        assertTrue(methods.contains("openSubscriptionActivation"))
        assertTrue(methods.contains("openSubscriptionPurchase"))
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
