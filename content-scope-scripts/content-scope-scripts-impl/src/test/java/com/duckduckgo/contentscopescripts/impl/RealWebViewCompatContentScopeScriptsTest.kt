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

import android.annotation.SuppressLint
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionManager
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealWebViewCompatContentScopeScriptsTest {

    private val mockPluginPoint: PluginPoint<ContentScopeConfigPlugin> = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockContentScopeJsReader: ContentScopeJSReader = mock()
    private val mockPlugin1: ContentScopeConfigPlugin = mock()
    private val mockPlugin2: ContentScopeConfigPlugin = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFingerprintProtectionManager: FingerprintProtectionManager = mock()
    private val contentScopeScriptsFeature = FakeFeatureToggleFactory.create(ContentScopeScriptsFeature::class.java)

    lateinit var testee: WebViewCompatContentScopeScripts

    @Before
    fun setup() {
        testee = RealWebViewCompatContentScopeScripts(
            mockPluginPoint,
            mockUserAllowListRepository,
            mockContentScopeJsReader,
            mockAppBuildConfig,
            mockUnprotectedTemporary,
            mockFingerprintProtectionManager,
            contentScopeScriptsFeature,
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
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        js = testee.getScript(listOf())

        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndAllowListChangedThenUseNewAllowListValue() {
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"foo\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        js = testee.getScript(listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(4)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndGpcChangedThenUseNewGpcValue() {
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":false,\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin2.preferences()).thenReturn("\"globalPrivacyControlValue\":false")
        js = testee.getScript(listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndConfigChangedThenUseNewConfigValue() {
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":true,\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\"\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin1.preferences()).thenReturn("\"globalPrivacyControlValue\":true")
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        js = testee.getScript(listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndUnprotectedTemporaryChangedThenUseNewUnprotectedTemporaryValue() {
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        js = testee.getScript(listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(4)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndCurrentCohortsChangedThenUseNewCurrentCohortsValue() = runTest {
        var js = testee.getScript(listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\{\"cohort\":\"control\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"test\"}]," +
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val activeExperiments = listOf(mockToggle)

        js = testee.getScript(activeExperiments)

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptWithMultipleActiveExperimentsThenFormatsCorrectly() = runTest {
        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[" +
                "\\{\"cohort\":\"treatment\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"test\"}," +
                "\\{\"cohort\":\"control\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"bloops\"}\\]," +
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        val mockToggle1 = mock<Toggle>()
        whenever(mockToggle1.getCohort()).thenReturn(Cohort("treatment", weight = 1))
        whenever(mockToggle1.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val mockToggle2 = mock<Toggle>()
        whenever(mockToggle2.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle2.featureName()).thenReturn(FeatureName("contentScopeExperiments", "bloops"))

        val activeExperiments = listOf(mockToggle1, mockToggle2)

        val js = testee.getScript(activeExperiments)

        verifyJsScript(js, newRegEx)
    }

    @Test
    fun whenGetScriptWithExperimentWithoutCohortThenFormatsCorrectly() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(null)
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val activeExperiments = listOf(mockToggle)

        val js = testee.getScript(activeExperiments)

        verifyJsScript(js)
    }

    @Test
    fun whenGetScriptWithNoActiveExperimentsThenFormatsCorrectly() = runTest {
        val js = testee.getScript(listOf())

        verifyJsScript(js)
    }

    @Test
    fun whenGetScriptWithNullSiteThenFormatsCorrectly() = runTest {
        val js = testee.getScript(listOf())

        verifyJsScript(js)
    }

    @Test
    fun whenContentScopeScriptsAndUseNewWebCompatApisAreEnabledThenReturnTrue() = runTest {
        contentScopeScriptsFeature.self().setRawStoredState(State(enable = true))
        contentScopeScriptsFeature.useNewWebCompatApis().setRawStoredState(State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenContentScopeScriptsIsDisabledThenReturnFalse() = runTest {
        contentScopeScriptsFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenseNewWebCompatApisIsDisabledThenReturnFalse() = runTest {
        contentScopeScriptsFeature.useNewWebCompatApis().setRawStoredState(State(enable = false))
        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenGetScriptThenPopulateMessagingParameters() = runTest {
        val js = testee.getScript(listOf())
        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
    }

    @Test
    fun whenGetScriptWithMixedValidAndNullCohortExperimentsThenFiltersOutNullCohorts() = runTest {
        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[" +
                "\\{\"cohort\":\"treatment\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"test\"}\\]," +
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        val validExperiment = mock<Toggle>()
        whenever(validExperiment.getCohort()).thenReturn(Cohort("treatment", weight = 1))
        whenever(validExperiment.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val nullCohortExperiment = mock<Toggle>()
        whenever(nullCohortExperiment.getCohort()).thenReturn(null)
        whenever(nullCohortExperiment.featureName()).thenReturn(FeatureName("contentScopeExperiments", "bloops"))

        val activeExperiments = listOf(validExperiment, nullCohortExperiment)

        val js = testee.getScript(activeExperiments)

        verifyJsScript(js, newRegEx)
    }

    @Test
    fun whenGetScriptWithExperimentWithoutParentNameThenFiltersOut() = runTest {
        val expectedRegEx = contentScopeRegex

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("treatment", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName(null, "test"))

        val activeExperiments = listOf(mockToggle)

        val js = testee.getScript(activeExperiments)

        verifyJsScript(js, expectedRegEx)
    }

    private fun verifyJsScript(js: String, regex: Regex = contentScopeRegex) {
        val matchResult = regex.find(js)
        val messageSecret = matchResult!!.groupValues[1]
        val messageCallback = matchResult.groupValues[2]
        val messageInterface = matchResult.groupValues[3]
        assertTrue(messageSecret != messageCallback && messageSecret != messageInterface && messageCallback != messageInterface)
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
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\"\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
    }
}
