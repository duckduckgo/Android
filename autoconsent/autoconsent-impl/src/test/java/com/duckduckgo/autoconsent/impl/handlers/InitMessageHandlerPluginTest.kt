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
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeature
import com.duckduckgo.autoconsent.impl.remoteconfig.AutoconsentFeatureModels.CompactRules
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
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
    private val feature = FakeFeatureToggleFactory.create(AutoconsentFeature::class.java)

    @Suppress("ktlint:standard:max-line-length")
    private val mockRulesetJson = "{\"disabledCMPs\":[],\"compactRuleList\":{\"v\":1,\"s\":[\".cc-type-categories[aria-describedby=\\\"cookieconsent:desc\\\"]\",\".cc-type-categories[aria-describedby=\\\"cookieconsent:desc\\\"] .cc-dismiss\",\".cc-dismiss\",\".cc-type-categories input[type=checkbox]:not([disabled]):checked\",\".cc-save\",\"#gdpr-cookie-consent-bar\",\"#gdpr-cookie-consent-bar #cookie_action_reject\",\"wpl_viewed_cookie=no\",\".cookie-alert-extended\",\".cookie-alert-extended-modal\",\"a[data-controller='cookie-alert/extended/detail-link']\",\".cookie-alert-configuration-input:checked\",\"button[data-controller='cookie-alert/extended/button/configuration']\",\"body > div#root > div#ccpa-iframe-theme-provider[data-testid=\\\"ccpa-iframe-theme-provider\\\"] > div#ccpa-iframe[data-testid=\\\"ccpa-iframe\\\"] > div#ccpa_consent_banner[data-testid=\\\"ccpa_consent_banner\\\"] > div:not([id]) > div:nth-child(3):not([id]) > span:not([id]) > span:not([id]) > div:nth-child(2):not([id]) > span:nth-child(1):not([id]) > button#decline_cookies_button[data-testid=\\\"decline_cookies_button\\\"]\",\"body:not([id]) > div#didomi-host > div:not([id]) > div#didomi-popup > div:nth-child(2):not([id]) > div:not([id]) > div:nth-child(2):not([id]) > span:nth-child(1):not([id])\"],\"r\":[[1,\"Complianz categories\",2,\"\",22,[0],[{\"e\":0}],[{\"v\":0}],[{\"if\":{\"e\":1},\"then\":[{\"k\":2}],\"else\":[{\"all\":true,\"optional\":true,\"k\":3},{\"k\":4}]}],[],{}],[1,\"WP Cookie Notice for GDPR\",2,\"\",22,[5],[{\"e\":5}],[{\"v\":5}],[{\"c\":6}],[{\"cc\":7}],{}],[1,\"cookiealert\",2,\"\",11,[],[{\"e\":8}],[{\"v\":9}],[{\"k\":10},{\"all\":true,\"optional\":true,\"k\":11},{\"k\":12},{\"eval\":\"EVAL_COOKIEALERT_0\"}],[{\"eval\":\"EVAL_COOKIEALERT_2\"}],{\"intermediate\":false}],[1,\"auto_AU_help.dropbox.com_4ad\",0,\"^https?://(www\\\\.)?dropbox\\\\.com/\",1,[],[{\"e\":13}],[{\"v\":13}],[{\"wait\":500},{\"c\":13}],[{\"timeout\":1000,\"check\":\"none\",\"wv\":13}],{}],[1,\"auto_AU_24h-lemans.com_2ab\",0,\"^https?://(www\\\\.)?24h-lemans\\\\.com/\",10,[],[{\"e\":14}],[{\"v\":14}],[{\"c\":14}],[],{}]],\"index\":{\"genericRuleRange\":[0,3],\"frameRuleRange\":[2,4],\"specificRuleRange\":[3,5],\"genericStringEnd\":13,\"frameStringEnd\":14}}}"

    private val initHandlerPlugin = InitMessageHandlerPlugin(
        TestScope(),
        coroutineRule.testDispatcherProvider,
        settingsRepository,
        settingsCache,
        feature,
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

    @Test
    fun filterCompactRulesWhenUnsupportedVersionRturnsEmptyRuleset() {
        settingsCache.updateSettings("{\"compactRuleList\": {\"v\": 2, \"r\": [], \"s\": [] }}")
        val rules = settingsCache.getSettings()!!.compactRuleList
        val result = invokeFilterCompactRules(rules, "https://www.example.com")
        val compact = result.compact as CompactRules

        assertEquals(1, compact.v)
        assertTrue(compact.r.isEmpty())
        assertTrue(compact.s.isEmpty())
        assertNull(compact.index)
    }

    @Test
    fun filterCompactRulesNoSpecificMatchReturnsGenericAndStringsUpToGenericEnd() {
        settingsCache.updateSettings(mockRulesetJson)
        val rules = settingsCache.getSettings()!!.compactRuleList

        val result = invokeFilterCompactRules(rules, "https://www.example.com")
        val compact = result.compact as CompactRules

        assertEquals(1, compact.v)
        assertEquals(3, compact.r.size)
        assertEquals(13, compact.s.size)
    }

    @Test
    fun filterCompactRulesWithSpecificMatchMergesRulesAndFiltersStrings() {
        settingsCache.updateSettings(mockRulesetJson)
        val rules = settingsCache.getSettings()!!.compactRuleList

        val result = invokeFilterCompactRules(rules, "https://www.24h-lemans.com/")
        val compact = result.compact as CompactRules

        assertEquals(1, compact.v)
        assertEquals(4, compact.r.size)
        assertEquals(15, compact.s.size)
    }

    @Test
    fun filterCompactRulesNoIndexAppliesPredicateAndFiltersStrings() {
        settingsCache.updateSettings(mockRulesetJson)
        val originalRules = settingsCache.getSettings()!!.compactRuleList
        val rules = CompactRules(1, originalRules.r, originalRules.s, index = null)
        val result = invokeFilterCompactRules(rules, "https://example.com/")
        val compact = result.compact as CompactRules

        assertEquals(1, compact.v)
        assertEquals(3, compact.r.size)
        assertEquals(13, compact.s.size)
    }

    @Test
    fun filterCompactRulesAlwaysFiltersFrameOnlyRule() {
        settingsCache.updateSettings(mockRulesetJson)
        val rules = settingsCache.getSettings()!!.compactRuleList

        val result = invokeFilterCompactRules(rules, "http://help.dropbox.com/")
        val compact = result.compact as CompactRules

        assertEquals(1, compact.v)
        assertEquals(3, compact.r.size)
        assertEquals(13, compact.s.size)
    }

    private fun invokeFilterCompactRules(
        rules: CompactRules,
        url: String,
    ): InitMessageHandlerPlugin.AutoconsentRuleset {
        val method = InitMessageHandlerPlugin::class.java.getDeclaredMethod(
            "filterCompactRules",
            CompactRules::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(initHandlerPlugin, rules, url) as InitMessageHandlerPlugin.AutoconsentRuleset
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
