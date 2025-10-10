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

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autoconsent.api.AutoconsentCallback
import com.duckduckgo.autoconsent.impl.FakeSettingsRepository
import com.duckduckgo.autoconsent.impl.adapters.JSONObjectAdapter
import com.duckduckgo.autoconsent.impl.cache.RealAutoconsentSettingsCache
import com.duckduckgo.autoconsent.impl.handlers.InitMessageHandlerPlugin.InitResp
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class InitMessageHandlerPluginTest {
    @get:Rule var coroutineRule = CoroutineTestRule()

    private val mockCallback: AutoconsentCallback = mock()
    private val webView: WebView = WebView(InstrumentationRegistry.getInstrumentation().targetContext)
    private val settingsRepository = FakeSettingsRepository()
    private var settingsCache = RealAutoconsentSettingsCache()

    private val initHandlerPlugin = InitMessageHandlerPlugin(
        TestScope(),
        coroutineRule.testDispatcherProvider,
        settingsRepository,
        settingsCache,
    )

    @Test
    fun whenProcessIfMessageTypeIsNotInitThenDoNothing() {
        initHandlerPlugin.process("noMatching", "", webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessIfCannotParseMessageThenDoNothing() {
        val message = """
            {"type":"${initHandlerPlugin.supportedTypes.first()}", url: "http://www.example.com"}
        """.trimIndent()

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message, webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessIfNotUrlSchemaThenDoNothing() {
        val message = """
            {"type":"${initHandlerPlugin.supportedTypes.first()}", "url": "ftp://www.example.com"}
        """.trimIndent()

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message, webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    @Ignore("Only valid when firstPopupHandled is being used")
    fun whenProcessIfAutoconsentIsDisabledAndAlreadyHandledThenDoNothing() {
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")
        settingsRepository.userSetting = false
        settingsRepository.firstPopupHandled = true

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    @Ignore("Only valid when firstPopupHandled is being used")
    fun whenProcessIfAutoconsentIsDisabledAndNotHandledThenDoNotCallEvaluate() {
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")
        settingsRepository.userSetting = false
        settingsRepository.firstPopupHandled = false

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    @Ignore("Only valid when firstPopupHandled is being used")
    fun whenProcessMessageForFirstTimeThenDoNotCallEvaluate() {
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")
        settingsRepository.userSetting = true
        settingsRepository.firstPopupHandled = false

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        assertNull(result)
    }

    @Test
    fun whenProcessIfAutoconsentIsDisabledThenDoNothing() {
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")
        settingsRepository.userSetting = false

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        assertNull(shadowOf(webView).lastEvaluatedJavascript)
    }

    @Test
    fun whenProcessMessageIfNoSettingsThenDoNotCallEvaluate() {
        settingsCache = RealAutoconsentSettingsCache()
        settingsRepository.userSetting = true

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        assertNull(result)
    }

    @Test
    fun whenProcessMessageIfCanNotParseSettingsThenDoNotCallEvaluate() {
        settingsCache.updateSettings("{\"random\": []}")
        settingsRepository.userSetting = true

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        assertNull(result)
    }

    @Test
    fun whenProcessMessageWithEmptyObjectsInSettingsResponseSentIsCorrect() {
        settingsRepository.userSetting = true
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {}}")

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        val initResp = jsonToInitResp(result)
        assertEquals("optOut", initResp!!.config.autoAction)
        assertTrue(initResp.config.enablePrehide)
        assertTrue(initResp.config.enabled)
        assertNotNull(initResp.rules.compact)
        assertEquals(20, initResp.config.detectRetries)
        assertEquals("initResp", initResp.type)
    }

    @Test
    fun whenProcessMessageResponseSentIsCorrect() {
        settingsRepository.userSetting = true
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        val initResp = jsonToInitResp(result)
        assertEquals("optOut", initResp!!.config.autoAction)
        assertTrue(initResp.config.enablePrehide)
        assertTrue(initResp.config.enabled)
        assertNotNull(initResp.rules.compact)
        assertEquals(20, initResp.config.detectRetries)
        assertEquals("initResp", initResp.type)
    }

    @Test
    @Ignore("Only valid when firstPopupHandled is being used")
    fun whenProcessMessageAndPopupHandledResponseSentIsCorrect() {
        settingsRepository.userSetting = true
        settingsRepository.firstPopupHandled = true
        settingsCache.updateSettings("{\"disabledCMPs\": [], \"compactRuleList\": {\"v\": 1, \"s\": [], \"r\": []}}")

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        val shadow = shadowOf(webView)
        val result = shadow.lastEvaluatedJavascript
        val initResp = jsonToInitResp(result)
        assertEquals("optOut", initResp!!.config.autoAction)
        assertTrue(initResp.config.enablePrehide)
        assertTrue(initResp.config.enabled)
        assertNotNull(initResp.rules.compact)
        assertEquals(20, initResp.config.detectRetries)
        assertEquals("initResp", initResp.type)
    }

    @Test
    fun whenProcessMessageThenOnResultReceivedCalled() {
        settingsRepository.userSetting = true

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = false, optOutFailed = false, selfTestFailed = false, isCosmetic = false)
    }

    @Test
    @Ignore("Only valid when firstPopupHandled is being used")
    fun whenProcessMessageAndFirstPopupHandledThenOnResultReceivedCalled() {
        settingsRepository.userSetting = true
        settingsRepository.firstPopupHandled = true

        initHandlerPlugin.process(initHandlerPlugin.supportedTypes.first(), message(), webView, mockCallback)

        verify(mockCallback).onResultReceived(consentManaged = false, optOutFailed = false, selfTestFailed = false, isCosmetic = false)
    }

    private fun message(): String {
        return """
            {"type":"${initHandlerPlugin.supportedTypes.first()}", "url": "http://www.example.com"}
        """.trimIndent()
    }

    private fun jsonToInitResp(json: String): InitResp? {
        val trimmedJson = json
            .removePrefix("javascript:(function() {window.autoconsentMessageCallback(")
            .removeSuffix(", window.origin);})();")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter: JsonAdapter<InitResp> = moshi.adapter(InitResp::class.java)
        return jsonAdapter.fromJson(trimmedJson)
    }
}
