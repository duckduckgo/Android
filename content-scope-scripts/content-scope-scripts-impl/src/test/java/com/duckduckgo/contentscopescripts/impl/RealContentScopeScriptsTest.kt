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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.feature.toggles.api.FeatureExceptions.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionManager
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealContentScopeScriptsTest {

    private val mockPluginPoint: PluginPoint<ContentScopeConfigPlugin> = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockContentScopeJsReader: ContentScopeJSReader = mock()
    private val mockPlugin1: ContentScopeConfigPlugin = mock()
    private val mockPlugin2: ContentScopeConfigPlugin = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFingerprintProtectionManager: FingerprintProtectionManager = mock()
    private val mockContentScopeScriptsFeature: ContentScopeScriptsFeature = mock()

    lateinit var testee: CoreContentScopeScripts

    @Before
    fun setup() {
        testee = RealContentScopeScripts(
            mockPluginPoint,
            mockUserAllowListRepository,
            mockContentScopeJsReader,
            mockAppBuildConfig,
            mockUnprotectedTemporary,
            mockFingerprintProtectionManager,
            mockContentScopeScriptsFeature,
        )
        whenever(mockPlugin1.config()).thenReturn(config1)
        whenever(mockPlugin2.config()).thenReturn(config2)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl))
        whenever(mockContentScopeJsReader.getContentScopeJS()).thenReturn(contentScopeJS)
        whenever(mockAppBuildConfig.versionCode).thenReturn(versionCode)
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions)
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        whenever(mockFingerprintProtectionManager.getSeed()).thenReturn(sessionKey)
    }

    @Test
    fun whenGetScriptWhenVariablesAreCachedAndNoChangesThenUseCachedVariables() {
        var js = testee.getScript()
        verifyJsScript(js)

        js = testee.getScript()

        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndAllowListChangedThenUseNewAllowListValue() {
        var js = testee.getScript()
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"foo\\.com\"\\], " +
                "\\{\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        js = testee.getScript()

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(4)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndGpcChangedThenUseNewGpcValue() {
        var js = testee.getScript()
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":false,\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin2.preferences()).thenReturn("\"globalPrivacyControlValue\":false")
        js = testee.getScript()

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndConfigChangedThenUseNewConfigValue() {
        var js = testee.getScript()
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":true,\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin1.preferences()).thenReturn("\"globalPrivacyControlValue\":true")
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        js = testee.getScript()

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndUnprotectedTemporaryChangedThenUseNewUnprotectedTemporaryValue() {
        var js = testee.getScript()
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        js = testee.getScript()

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(4)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenContentScopeScriptsIsEnabledThenReturnTrue() {
        whenever(mockContentScopeScriptsFeature.self()).thenReturn(EnabledToggle())
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenContentScopeScriptsIsDisabledThenReturnFalse() {
        whenever(mockContentScopeScriptsFeature.self()).thenReturn(DisabledToggle())
        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenGetScriptThenPopulateMessagingParameters() {
        val js = testee.getScript()
        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
    }

    private fun verifyJsScript(js: String, regex: Regex = contentScopeRegex) {
        val matchResult = regex.find(js)
        val messageSecret = matchResult!!.groupValues[1]
        val messageCallback = matchResult.groupValues[2]
        val messageInterface = matchResult.groupValues[3]
        assertTrue(messageSecret != messageCallback && messageSecret != messageInterface && messageCallback != messageInterface)
    }

    class EnabledToggle : Toggle {
        override fun isEnabled(): Boolean {
            return true
        }

        override fun setEnabled(state: State) {
            // not implemented
        }

        override fun getRawStoredState(): State? = null
    }

    class DisabledToggle : Toggle {
        override fun isEnabled(): Boolean {
            return false
        }

        override fun setEnabled(state: State) {
            // not implemented
        }

        override fun getRawStoredState(): State? = null
    }

    companion object {
        const val contentScopeJS = "processConfig(\$CONTENT_SCOPE\$, \$USER_UNPROTECTED_DOMAINS\$, \$USER_PREFERENCES\$)"
        const val config1 = "\"config1\":{\"state\":\"enabled\"}"
        const val config2 = "\"config2\":{\"state\":\"disabled\"}"
        const val exampleUrl = "example.com"
        const val exampleUrl2 = "foo.com"
        const val versionCode = 1234
        const val sessionKey = "5678"
        val unprotectedTemporaryException = FeatureException(domain = "example.com", reason = "reason")
        val unprotectedTemporaryException2 = FeatureException(domain = "foo.com", reason = "reason2")
        val contentScopeRegex = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"sessionKey\":\"5678\"," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
    }
}
