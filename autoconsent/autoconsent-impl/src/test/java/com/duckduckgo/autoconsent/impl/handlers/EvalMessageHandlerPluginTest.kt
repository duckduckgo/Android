/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.handlers

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.handlers.EvalMessageHandlerPlugin.EvalResp
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class EvalMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockCallback: AutoconsentCallback = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)

    private val evalMessageHandlerPlugin = EvalMessageHandlerPlugin(TestScope(), coroutineRule.testDispatcherProvider)

    @Test
    fun whenProcessMessageIfTypeNotEvalDoNothing() {
        evalMessageHandlerPlugin.process("noMatching", "", webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessMessageIfDoesNotParseDoNothing() {
        val message = """
            {"type":"${evalMessageHandlerPlugin.supportedTypes.first()}", id: "myId", "code": "42==42"}
        """.trimIndent()

        evalMessageHandlerPlugin.process(evalMessageHandlerPlugin.supportedTypes.first(), message, webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessMessageThenCallEvaluateWithCorrectCode() {
        val expected = """
        javascript:(function() {
            try {
                return !!((42==42));
            } catch (e) {
              // ignore errors
              return;
            }
        })();
        """.trimIndent()
        evalMessageHandlerPlugin.process(evalMessageHandlerPlugin.supportedTypes.first(), message("42==42"), webView, mockCallback)

        assertEquals(expected, shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessMessageThenAndEvalTrueThenCorrectEvalRespSent() {
        evalMessageHandlerPlugin.process(evalMessageHandlerPlugin.supportedTypes.first(), message("42==42"), webView, mockCallback)

        val shadow = shadowOf(webView)
        shadow.lastEvaluatedJavascriptCallback.onReceiveValue("true")

        val result = shadow.lastEvaluatedJavascript
        val evalResp = jsonToEvalResp(result)

        assertTrue(evalResp!!.result)
        assertEquals("myId", evalResp.id)
        assertEquals("evalResp", evalResp.type)
    }

    @Test
    fun whenProcessMessageThenAndEvalFalseThenCorrectEvalRespSent() {
        evalMessageHandlerPlugin.process(evalMessageHandlerPlugin.supportedTypes.first(), message("41==42"), webView, mockCallback)

        val shadow = shadowOf(webView)
        shadow.lastEvaluatedJavascriptCallback.onReceiveValue("false")

        val result = shadow.lastEvaluatedJavascript
        val evalResp = jsonToEvalResp(result)

        assertFalse(evalResp!!.result)
        assertEquals("myId", evalResp.id)
        assertEquals("evalResp", evalResp.type)
    }

    private fun message(code: String): String {
        return """
            {"type":"${evalMessageHandlerPlugin.supportedTypes.first()}", "id": "myId", "code": "$code"}
        """.trimIndent()
    }

    private fun jsonToEvalResp(json: String): EvalResp? {
        val trimmedJson = json
            .removePrefix("javascript:(function() {window.autoconsentMessageCallback(")
            .removeSuffix(", window.origin);})();")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<EvalResp> = moshi.adapter(EvalResp::class.java)
        return jsonAdapter.fromJson(trimmedJson)
    }
}
