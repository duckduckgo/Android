/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pagetranslation.impl.messaging

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pagetranslation.impl.AttachedWebView
import com.duckduckgo.pagetranslation.impl.PageTranslationDeviceSupport
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.messaging.RealPageTranslationMessaging.PageTranslationJsInterface
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class RealPageTranslationMessagingTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val script = FakePageTranslationScript()
    private val attachedWebView = AttachedWebView()
    private val feature = FakeFeatureToggleFactory.create(PageTranslationFeature::class.java).apply {
        translation().setRawStoredState(State(enable = true))
    }
    private var isDeviceSupported = true
    private val deviceSupport = object : PageTranslationDeviceSupport {
        override fun isSupported() = isDeviceSupported
    }
    private val messaging = RealPageTranslationMessaging(attachedWebView, script, coroutineRule.testDispatcherProvider, feature, deviceSupport)
    private val webView: WebView = mock()
    private val page: PageTranslationJsInterface by lazy { getInterface(webView) }

    @Test
    fun registerTheInterfaceUnderTheScriptsName() {
        messaging.register(webView)

        verify(webView).addJavascriptInterface(any(), eq(INTERFACE_NAME))
    }

    @Test
    fun registerNothingWhilePageTranslationIsOff() {
        feature.translation().setRawStoredState(State(enable = false))

        messaging.register(webView)

        verify(webView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun registerNothingOnAnUnsupportedDevice() {
        isDeviceSupported = false

        messaging.register(webView)

        verify(webView, never()).addJavascriptInterface(any(), any())
    }

    @Test
    fun injectTheScriptForTheDocument() = runMessagingTest {
        attach(webView)

        verify(webView).evaluateJavascript("script for $TIME_ORIGIN with $secret", null)
    }

    @Test
    fun keepTheSecretWhenTheSameDocumentIsInjectedAgain() = runMessagingTest {
        attach(webView)

        messaging.inject(TIME_ORIGIN)

        assertEquals(2, script.secrets.size)
        assertEquals(1, script.secrets.toSet().size)
    }

    @Test
    fun useANewSecretForEachDocument() = runMessagingTest {
        attach(webView)

        messaging.inject(TIME_ORIGIN + 1)

        assertEquals(2, script.secrets.toSet().size)
    }

    @Test
    fun ignoreAReplyWithThePreviousDocumentsSecret() = runMessagingTest {
        attach(webView)
        val previousSecret = secret
        messaging.inject(TIME_ORIGIN + 1)

        val units = async { messaging.extract() }
        runCurrent()
        page.reply(EXTRACT_RESULT, getLastEvent(webView).requestId, """"units":[]""", secret = previousSecret)
        advanceTimeBy(11.seconds)

        assertNull(units.await())
    }

    @Test
    fun sendNothingBeforeTheBundleIsInjected() = runMessagingTest {
        attachedWebView.attach(webView)
        messaging.register(webView)

        val units = async(start = UNDISPATCHED) { messaging.extract() }
        runCurrent()

        assertNull(units.await())
        verify(webView, never()).evaluateJavascript(any(), anyOrNull())
    }

    @Test
    fun extractThePagesUnits() = runMessagingTest {
        attach(webView)

        val units = async { messaging.extract() }
        runCurrent()
        val event = getLastEvent(webView)
        page.reply(
            EXTRACT_RESULT,
            event.requestId,
            """"units":[{"id":0,"text":"Hola","html":false,"vp":1},{"id":1,"text":"<b>Adiós</b>","html":true,"vp":0}]""",
        )

        assertEquals("extract", event.getString("subscriptionName"))
        assertEquals(
            listOf(PageUnit(0, "Hola", isHtml = false, isInViewport = true), PageUnit(1, "<b>Adiós</b>", isHtml = true, isInViewport = false)),
            units.await(),
        )
    }

    @Test
    fun applyTranslationsAsTextOrTreeAndReturnTheCountWritten() = runMessagingTest {
        attach(webView)

        val count = async {
            messaging.apply(
                LanguagePair("es", "en"),
                listOf(UnitTranslation(0, "Hello", isHtml = false), UnitTranslation(1, "{\"tree\":1}", isHtml = true)),
            )
        }
        runCurrent()
        val event = getLastEvent(webView)
        page.reply(APPLY_RESULT, event.requestId, """"count":2""")

        val params = event.getJSONObject("params")
        assertEquals("apply", event.getString("subscriptionName"))
        assertEquals("es", params.getString("sourceLanguage"))
        assertEquals("en", params.getString("targetLanguage"))
        assertEquals("""[{"id":0,"text":"Hello"},{"id":1,"tree":"{\"tree\":1}"}]""", params.getJSONArray("updates").toString())
        assertEquals(2, count.await())
    }

    @Test
    fun sendEachRequestWithANewRequestId() = runMessagingTest {
        attach(webView)

        async { messaging.extract() }
        runCurrent()
        val first = getLastEvent(webView).requestId
        async { messaging.extract() }
        runCurrent()

        assertNotEquals(first, getLastEvent(webView).requestId)
    }

    @Test
    fun ignoreAReplyWithAnotherRequestId() = runMessagingTest {
        attach(webView)

        val units = async { messaging.extract() }
        runCurrent()
        page.reply(EXTRACT_RESULT, "other", """"units":[]""")
        advanceTimeBy(11.seconds)

        assertNull(units.await())
    }

    @Test
    fun ignoreAReplyWithoutTheSecret() = runMessagingTest {
        attach(webView)

        val units = async { messaging.extract() }
        runCurrent()
        page.reply(EXTRACT_RESULT, getLastEvent(webView).requestId, """"units":[]""", secret = "guessed")
        advanceTimeBy(11.seconds)

        assertNull(units.await())
    }

    @Test
    fun giveUpWhenThePageDoesNotReplyInTenSeconds() = runMessagingTest {
        attach(webView)

        val units = async { messaging.extract() }
        advanceTimeBy(9.seconds)
        assertTrue(units.isActive)

        advanceTimeBy(2.seconds)
        assertNull(units.await())
    }

    @Test
    fun dropARequestWhenANewOneStartsAndIgnoreItsLateReply() = runMessagingTest {
        attach(webView)

        val first = async { messaging.extract() }
        runCurrent()
        val firstRequestId = getLastEvent(webView).requestId
        val second = async { messaging.extract() }
        runCurrent()
        page.reply(EXTRACT_RESULT, firstRequestId, """"units":[]""")
        page.reply(EXTRACT_RESULT, getLastEvent(webView).requestId, """"units":[{"id":3,"text":"Hola","html":false,"vp":1}]""")

        assertNull(first.await())
        assertEquals(listOf(PageUnit(3, "Hola", isHtml = false, isInViewport = true)), second.await())
    }

    @Test
    fun dropTheRequestOnCancel() = runMessagingTest {
        attach(webView)

        val units = async { messaging.extract() }
        runCurrent()
        messaging.cancel()

        assertNull(units.await())
    }

    @Test
    fun sendNothingWithoutAnAttachedWebView() = runMessagingTest {
        messaging.register(webView)

        val units = async(start = UNDISPATCHED) { messaging.extract() }
        runCurrent()

        assertNull(units.await())
        verify(webView, never()).evaluateJavascript(any(), anyOrNull())
    }

    private fun runMessagingTest(block: suspend TestScope.() -> Unit) = coroutineRule.testScope.runTest { block() }

    private suspend fun attach(webView: WebView) {
        attachedWebView.attach(webView)
        messaging.register(webView)
        messaging.inject(TIME_ORIGIN)
    }

    private val secret: String
        get() = script.secrets.last()

    private fun getInterface(webView: WebView): PageTranslationJsInterface =
        argumentCaptor<Any>().also {
            verify(
                webView,
            ).addJavascriptInterface(it.capture(), eq(INTERFACE_NAME))
        }.lastValue as PageTranslationJsInterface

    private fun getLastEvent(webView: WebView): JSONObject {
        val source = argumentCaptor<String>().also { verify(webView, atLeastOnce()).evaluateJavascript(it.capture(), anyOrNull()) }.lastValue
        val prefix = "window['$CALLBACK_NAME']('$secret', "
        assertTrue(source.startsWith(prefix))

        return JSONObject(source.removePrefix(prefix).removeSuffix(");"))
    }

    private val JSONObject.requestId: String
        get() = getJSONObject("params").getString("requestId")

    private fun PageTranslationJsInterface.reply(
        method: String,
        requestId: String,
        params: String,
        secret: String = this@RealPageTranslationMessagingTest.secret,
    ) {
        process(
            """{"context":"contentScopeScripts","featureName":"pageTranslation","method":"$method","params":{"requestId":"$requestId",$params}}""",
            secret,
        )
    }

    private class FakePageTranslationScript : PageTranslationScript {
        override val interfaceName = INTERFACE_NAME
        override val callbackName = CALLBACK_NAME
        val secrets = mutableListOf<String>()

        override suspend fun get(timeOrigin: Double, secret: String): String {
            secrets += secret
            return "script for $timeOrigin with $secret"
        }
    }

    private companion object {
        const val INTERFACE_NAME = "pageTranslationInterface"
        const val CALLBACK_NAME = "pageTranslationCallback"
        const val EXTRACT_RESULT = "extractResult"
        const val APPLY_RESULT = "applyResult"
        const val TIME_ORIGIN = 1732634400123.456
    }
}
