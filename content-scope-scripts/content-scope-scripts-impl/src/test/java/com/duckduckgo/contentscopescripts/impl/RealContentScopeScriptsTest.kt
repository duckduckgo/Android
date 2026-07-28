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
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureException
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.FeatureName
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import com.duckduckgo.fingerprintprotection.api.FingerprintProtectionManager
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.duckduckgo.privacy.config.api.UnprotectedTemporary
import junit.framework.TestCase.assertEquals
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
class RealContentScopeScriptsTest {

    private val mockPluginPoint: PluginPoint<ContentScopeConfigPlugin> = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()
    private val mockContentScopeJsReader: ContentScopeJSReader = mock()
    private val mockPlugin1: ContentScopeConfigPlugin = mock()
    private val mockPlugin2: ContentScopeConfigPlugin = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockUnprotectedTemporary: UnprotectedTemporary = mock()
    private val mockFingerprintProtectionManager: FingerprintProtectionManager = mock()
    private val contentScopeScriptsFeature = FakeFeatureToggleFactory.create(ContentScopeScriptsFeature::class.java)

    lateinit var testee: CoreContentScopeScripts

    @Before
    fun setup() {
        testee = createTestee()
        whenever(mockPlugin1.config()).thenReturn(config1)
        whenever(mockPlugin2.config()).thenReturn(config2)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1, mockPlugin2))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl))
        whenever(mockContentScopeJsReader.getContentScopeJS()).thenReturn(contentScopeJS)
        whenever(mockAppBuildConfig.versionCode).thenReturn(versionCode)
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions)
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        whenever(mockFingerprintProtectionManager.getSeed()).thenReturn(sessionKey)
    }

    @Test
    fun whenGetScriptWhenVariablesAreCachedAndNoChangesThenUseCachedVariables() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        js = testee.getScript(null, listOf())

        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndAllowListChangedThenUseNewAllowListValue() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"foo\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\",\"internal\":true\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        js = testee.getScript(null, listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(4)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndGpcChangedThenUseNewGpcValue() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":false,\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin2.preferences()).thenReturn("\"globalPrivacyControlValue\":false")
        js = testee.getScript(null, listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndConfigChangedThenUseNewConfigValue() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"globalPrivacyControlValue\":true,\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\",\"internal\":true\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockPlugin1.preferences()).thenReturn("\"globalPrivacyControlValue\":true")
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        js = testee.getScript(null, listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(3)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenOptimizeEnabledAndPluginConfigChangesAfterStabilisingThenNotReflectedUntilPrivacyConfigPersisted() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        // Two calls to let the plugin config stabilise (repos "settled").
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        // Plugin config changes with no persist signal: must keep serving the cached config.
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        val staleJs = testee.getScript(null, listOf())
        assertTrue(staleJs.contains("\"features\":{$config1,$config2}"))

        // Persist signal invalidates the cached plugin config; next call rebuilds it.
        (testee as PrivacyConfigCallbackPlugin).onPrivacyConfigPersisted()
        val freshJs = testee.getScript(null, listOf())
        assertTrue(freshJs.contains("\"features\":{$config1}"))
        assertFalse("stale config2 must be gone after invalidation", freshJs.contains(config2))
    }

    @Test
    fun whenOptimizeEnabledAndPluginConfigChangesBeforeStabilisingThenReflectedWithoutPersistSignal() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        // First call: the config has not stabilised yet, so the next call must still rebuild it. This is the
        // cold-start window where the feature repos are still loading their persisted config into memory.
        testee.getScript(null, listOf())

        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        val js = testee.getScript(null, listOf())

        assertTrue(js.contains("\"features\":{$config1}"))
        assertFalse("config2 must be gone while the plugin config is still unsettled", js.contains(config2))
    }

    @Test
    fun whenOptimizeEnabledAndPluginConfigIsEmptyThenNeverLatchesAndPicksUpTheLoadedConfig() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        // The feature repos have not finished loading their persisted config yet, so every plugin
        // reports an empty config for the first two sweeps and the real config on the third.
        whenever(mockPlugin1.config()).thenReturn("", "", config1)
        whenever(mockPlugin2.config()).thenReturn("")

        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{}"))
        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{}"))

        // Two identical empty sweeps must not settle the latch, otherwise the loaded config would
        // never be picked up. No persist signal is sent here on purpose.
        val js = testee.getScript(null, listOf())
        assertTrue("the loaded config must be picked up without a persist signal", js.contains("\"features\":{$config1}"))
    }

    @Test
    fun whenOptimizeEnabledAndOnlyUnprotectedTemporaryChangesAfterSettlingThenContentScopeJsonIsRebuilt() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        // Two calls to settle the plugin config, so the rebuild below can only be driven by the
        // unprotected temporary change and not by the plugin config branch.
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        val js = testee.getScript(null, listOf())

        assertTrue(js.contains("\"unprotectedTemporary\":[{\"domain\":\"example.com\",\"reason\":\"reason\"}]"))
        assertTrue("the settled plugin config must survive the rebuild", js.contains("\"features\":{$config1,$config2}"))
    }

    @Test
    fun whenOptimizeEnabledAndPrivacyConfigDownloadedThenCachedPluginConfigIsNotInvalidated() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        // Invalidation is deliberately driven by onPrivacyConfigPersisted(), which is a superset of
        // this callback, so a download on its own must not rebuild the cached plugin config.
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        (testee as PrivacyConfigCallbackPlugin).onPrivacyConfigDownloaded()

        val js = testee.getScript(null, listOf())
        assertTrue(js.contains("\"features\":{$config1,$config2}"))
    }

    @Test
    fun whenOptimizeEnabledThenPluginConfigIsOnlyRebuiltUntilSettledAndOnPersist() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        // Calls 1 and 2 both sweep the plugins (call 2 is what settles the latch); calls 3 and 4 must not.
        repeat(4) { testee.getScript(null, listOf()) }
        verify(mockPlugin1, times(2)).config()

        // A persist signal re-arms the rebuild for exactly one sweep: the config comes back unchanged, so
        // the latch settles again immediately and the following calls skip the sweep.
        (testee as PrivacyConfigCallbackPlugin).onPrivacyConfigPersisted()
        repeat(3) { testee.getScript(null, listOf()) }
        verify(mockPlugin1, times(3)).config()
    }

    @Test
    fun whenOptimizeEnabledAndNothingChangesThenScriptIsAssembledOnceAndInputsReadOncePerCall() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        repeat(4) { verifyJsScript(testee.getScript(null, listOf())) }

        // No input changed, so the script is assembled once and each input is read once per call
        // (the legacy path reads these twice on the call that changes them).
        verify(mockContentScopeJsReader).getContentScopeJS()
        verify(mockUnprotectedTemporary, times(4)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(4)).domainsInUserAllowList()
    }

    @Test
    fun whenOptimizeDisabledThenPluginConfigChangesAreReflectedImmediately() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = false))
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        // Legacy path recomputes config every call, so a change is picked up with no persist signal.
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        val js = testee.getScript(null, listOf())
        assertTrue(js.contains("\"features\":{$config1}"))
        assertFalse("config2 must be gone immediately on the legacy path", js.contains(config2))
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndUnprotectedTemporaryChangedThenUseNewUnprotectedTemporaryValue() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        js = testee.getScript(null, listOf())

        verifyJsScript(js, newRegEx)
        verify(mockUnprotectedTemporary, times(4)).unprotectedTemporaryExceptions
        verify(mockUserAllowListRepository, times(3)).domainsInUserAllowList()
        verify(mockContentScopeJsReader, times(2)).getContentScopeJS()
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndDesktopModeChangedThenUseNewDesktopModeValue() {
        var js = testee.getScript(null, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\",\"internal\":true\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":true," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        js = testee.getScript(true, listOf())

        verifyJsScript(js, newRegEx)
    }

    @Test
    fun whenGetScriptAndVariablesAreCachedAndCurrentCohortsChangedThenUseNewCurrentCohortsValue() = runTest {
        var js = testee.getScript(false, listOf())
        verifyJsScript(js)

        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\{\"cohort\":\"control\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"test\"}]," +
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val activeExperiments = listOf(mockToggle)

        js = testee.getScript(false, activeExperiments)

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
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
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

        val js = testee.getScript(false, activeExperiments)

        verifyJsScript(js, newRegEx)
    }

    @Test
    fun whenGetScriptWithExperimentWithoutCohortThenFormatsCorrectly() = runTest {
        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(null)
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        val activeExperiments = listOf(mockToggle)

        val js = testee.getScript(false, activeExperiments)

        verifyJsScript(js)
    }

    @Test
    fun whenGetScriptWithNoActiveExperimentsThenFormatsCorrectly() = runTest {
        val js = testee.getScript(null, listOf())

        verifyJsScript(js)
    }

    @Test
    fun whenGetScriptWithNullSiteThenFormatsCorrectly() = runTest {
        val js = testee.getScript(null, listOf())

        verifyJsScript(js)
    }

    @Test
    fun whenContentScopeScriptsIsEnabledThenReturnTrue() {
        contentScopeScriptsFeature.self().setRawStoredState(State(enable = true))
        assertTrue(testee.isEnabled())
    }

    @Test
    fun whenContentScopeScriptsIsDisabledThenReturnFalse() {
        contentScopeScriptsFeature.self().setRawStoredState(State(enable = false))
        assertFalse(testee.isEnabled())
    }

    @Test
    fun whenGetScriptThenPopulateMessagingParameters() {
        val js = testee.getScript(null, listOf())
        verifyJsScript(js)
        verify(mockContentScopeJsReader).getContentScopeJS()
    }

    @Test
    fun whenOptimizeInjectionEnabledThenOutputIsByteIdenticalToFallbackPath() {
        // getScript memoizes cachedContentScopeJS, so each path uses its own fresh instance to force a real build.
        // Fallback path (chained String.replace).
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = false))
        val fallbackTestee = createTestee()
        val fallbackJs = fallbackTestee.getScript(null, listOf())

        // Optimized path (single-pass StringBuilder).
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        val optimizedTestee = createTestee()
        val optimizedJs = optimizedTestee.getScript(null, listOf())

        // The three messaging secrets are random per instance; normalise them before the byte comparison.
        val secret = Regex("[\\da-f]{32}")
        assertEquals(secret.replace(fallbackJs, "SECRET"), secret.replace(optimizedJs, "SECRET"))
    }

    @Test
    fun whenOptimizeInjectionEnabledAndUnprotectedTemporaryChangesThenReusedAdapterSerializesNewValue() {
        // Build the testee with the flag on so the list adapters are created once, up front, and then reused.
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        val optimizedTestee = createTestee()

        // First call serialises both exceptions through the cached adapter.
        verifyJsScript(optimizedTestee.getScript(null, listOf()))

        // Change the exceptions: the reused adapter must serialise the new list, not a stale cached result.
        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        verifyJsScript(optimizedTestee.getScript(null, listOf()), newRegEx)
    }

    @Test
    fun whenOptimizeInjectionEnabledAndCohortsChangeThenReusedExperimentsAdapterSerializesNewValue() = runTest {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        val optimizedTestee = createTestee()

        // First call with no experiments builds and caches the experiments adapter (empty currentCohorts).
        verifyJsScript(optimizedTestee.getScript(false, listOf()))

        // A later call with cohorts must serialise them through the same reused adapter.
        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"example\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\{\"cohort\":\"control\",\"feature\":\"contentScopeExperiments\",\"subfeature\":\"test\"}]," +
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
                "\"locale\":\"en\",\"sessionKey\":\"5678\"," +
                "\"desktopModeEnabled\":false,\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        verifyJsScript(optimizedTestee.getScript(false, listOf(mockToggle)), newRegEx)
    }

    @Test
    fun whenOptimizeInjectionEnabledAndAllowListChangesThenReusedAdapterSerializesNewValue() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        val optimizedTestee = createTestee()

        // First call serialises the initial allow-list through the cached domains adapter.
        verifyJsScript(optimizedTestee.getScript(null, listOf()))

        // Change the allow-list: the reused adapter must serialise the new domain.
        val newRegEx = Regex(
            "^processConfig\\(\\{\"features\":\\{" +
                "\"config1\":\\{\"state\":\"enabled\"\\}," +
                "\"config2\":\\{\"state\":\"disabled\"\\}\\}," +
                "\"unprotectedTemporary\":\\[" +
                "\\{\"domain\":\"example\\.com\",\"reason\":\"reason\"\\}," +
                "\\{\"domain\":\"foo\\.com\",\"reason\":\"reason2\"\\}\\]\\}, \\[\"foo\\.com\"\\], " +
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234," +
                "\"platform\":\\{\"name\":\"android\",\"internal\":true\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        verifyJsScript(optimizedTestee.getScript(null, listOf()), newRegEx)
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
                "\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\}," +
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

        val js = testee.getScript(null, activeExperiments)

        verifyJsScript(js, newRegEx)
    }

    @Test
    fun whenGetScriptWithExperimentWithoutParentNameThenFiltersOut() = runTest {
        val expectedRegEx = contentScopeRegex

        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("treatment", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName(null, "test"))

        val activeExperiments = listOf(mockToggle)

        val js = testee.getScript(null, activeExperiments)

        verifyJsScript(js, expectedRegEx)
    }

    @Test
    fun whenPluginReturnsEmptyConfigBetweenOthersThenNoDanglingCommaInFeatures() {
        val firstNonEmpty: ContentScopeConfigPlugin = mock()
        val emptyConfig: ContentScopeConfigPlugin = mock()
        val secondNonEmpty: ContentScopeConfigPlugin = mock()
        whenever(firstNonEmpty.config()).thenReturn(config1)
        whenever(emptyConfig.config()).thenReturn("")
        whenever(secondNonEmpty.config()).thenReturn(config2)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(firstNonEmpty, emptyConfig, secondNonEmpty))

        val js = testee.getScript(null, listOf())

        assertFalse("features must not contain a double comma", js.contains(",,"))
        assertTrue(js.contains("\"features\":{$config1,$config2}"))
    }

    @Test
    fun whenFirstPluginReturnsEmptyConfigThenNoLeadingCommaInFeatures() {
        val emptyConfig: ContentScopeConfigPlugin = mock()
        val firstNonEmpty: ContentScopeConfigPlugin = mock()
        val secondNonEmpty: ContentScopeConfigPlugin = mock()
        whenever(emptyConfig.config()).thenReturn("")
        whenever(firstNonEmpty.config()).thenReturn(config1)
        whenever(secondNonEmpty.config()).thenReturn(config2)
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(emptyConfig, firstNonEmpty, secondNonEmpty))

        val js = testee.getScript(null, listOf())

        assertFalse("features must not contain a double comma", js.contains(",,"))
        assertTrue(js.contains("\"features\":{$config1,$config2}"))
    }

    @Test
    fun whenPluginReturnsEmptyPreferencesThenNoDanglingCommaInPreferences() {
        whenever(mockPlugin1.preferences()).thenReturn("\"globalPrivacyControlValue\":true")
        whenever(mockPlugin2.preferences()).thenReturn("")

        val js = testee.getScript(null, listOf())

        assertFalse("preferences must not contain a double comma", js.contains(",,"))
        assertTrue(js.contains("\"globalPrivacyControlValue\":true"))
    }

    private fun createTestee(): CoreContentScopeScripts = RealContentScopeScripts(
        mockPluginPoint,
        mockUserAllowListRepository,
        mockContentScopeJsReader,
        mockAppBuildConfig,
        mockUnprotectedTemporary,
        mockFingerprintProtectionManager,
        contentScopeScriptsFeature,
    )

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
                "\\{\"currentCohorts\":\\[\\],\"versionNumber\":1234,\"platform\":\\{\"name\":\"android\",\"internal\":true\\},\"locale\":\"en\"," +
                "\"sessionKey\":\"5678\",\"desktopModeEnabled\":false," +
                "\"messageSecret\":\"([\\da-f]{32})\"," +
                "\"messageCallback\":\"([\\da-f]{32})\"," +
                "\"javascriptInterface\":\"([\\da-f]{32})\"\\}\\)$",
        )
    }
}
