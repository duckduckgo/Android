package com.duckduckgo.breakagereporting.impl

import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessageCallback
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.mock

class BreakageContentScopeJsMessageHandlerTest {
    private val handler = BreakageContentScopeJsMessageHandler().getJsMessageHandler()

    @Test
    fun `when message sent then callback called`() = runTest {
        val message = JsMessage(
            context = "contentScopeScripts",
            featureName = "breakageReporting",
            id = "myId",
            method = "breakageReportResult",
            params = JSONObject(),
        )

        handler.process(message, mock(), callback)

        assertEquals(1, callback.counter)
    }

    @Test
    fun `allow all domains`() = runTest {
        val domains = handler.allowedDomains
        assertTrue(domains.isEmpty())
    }

    @Test
    fun `feature name is breakage reporting`() = runTest {
        assertTrue(handler.featureName == "breakageReporting")
    }

    @Test
    fun `only contains valid methods`() = runTest {
        val methods = handler.methods
        assertTrue(methods.size == 1)
        assertTrue(methods.first() == "breakageReportResult")
    }

    private val callback = object : JsMessageCallback() {
        var counter = 0
        override fun process(featureName: String, method: String, id: String?, data: JSONObject?) {
            counter++
        }
    }
}
