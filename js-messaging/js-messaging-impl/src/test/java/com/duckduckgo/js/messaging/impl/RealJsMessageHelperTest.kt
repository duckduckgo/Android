package com.duckduckgo.js.messaging.impl

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.js.messaging.api.JsRequestResponse
import com.duckduckgo.js.messaging.api.SubscriptionEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows

@RunWith(AndroidJUnit4::class)
class RealJsMessageHelperTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)
    private val jsMessageHelper = RealJsMessageHelper(TestScope(), coroutineRule.testDispatcherProvider)

    @Test
    fun whenSendSubscriptionEventThenResponseMatches() = runTest {
        val expected = """
            javascript:(function() {
                window['callbackName']('secret', {"context":"myContext","featureName":"myFeature","params":{"test":"this is a test"},"subscriptionName":"mySubscription"});
            })();
        """.trimIndent()
        val subscriptionEvent = SubscriptionEvent(
            context = "myContext",
            featureName = "myFeature",
            subscriptionName = "mySubscription",
            params = JSONObject("""{"test":"this is a test"}"""),
        )

        jsMessageHelper.sendSubscriptionEvent(subscriptionEvent, "callbackName", "secret", webView)

        val shadow = Shadows.shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript

        assertEquals(expected, result)
    }

    @Test
    fun whenSendJsResponseAsSuccessThenResponseMatches() = runTest {
        val expected = """
            javascript:(function() {
                window['callbackName']('secret', {"context":"myContext","featureName":"myFeature","id":"myId","method":"myMethod","result":{"test":"this is a test"}});
            })();
        """.trimIndent()
        val response = JsRequestResponse.Success(
            context = "myContext",
            featureName = "myFeature",
            method = "myMethod",
            id = "myId",
            result = JSONObject("""{"test":"this is a test"}"""),
        )

        jsMessageHelper.sendJsResponse(response, "callbackName", "secret", webView)

        val shadow = Shadows.shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript

        assertEquals(expected, result)
    }

    @Test
    fun whenSendJsResponseAsErrorThenResponseMatches() = runTest {
        val expected = """
            javascript:(function() {
                window['callbackName']('secret', {"context":"myContext","error":"this is an error","featureName":"myFeature","id":"myId","method":"myMethod"});
            })();
        """.trimIndent()
        val response = JsRequestResponse.Error(
            context = "myContext",
            featureName = "myFeature",
            method = "myMethod",
            id = "myId",
            error = "this is an error",
        )

        jsMessageHelper.sendJsResponse(response, "callbackName", "secret", webView)

        val shadow = Shadows.shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript

        assertEquals(expected, result)
    }
}
