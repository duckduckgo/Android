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
    fun whenOptimizeEnabledAndPluginConfigChangesThenReflectedImmediately() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        val js = testee.getScript(null, listOf())

        assertTrue(js.contains("\"features\":{$config1}"))
        assertFalse("config2 must be gone on the very next call", js.contains(config2))
    }

    @Test
    fun whenOptimizeEnabledAndPluginConfigLoadsAfterEarlyNavigationsThenItIsStillPickedUp() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        // A feature repo that has not finished its async load reports the entity's default JSON, which is
        // non-empty ("elementHiding":{} and friends). Repeated identical sweeps therefore say nothing about
        // whether the repos are done, and must not be taken as a signal to stop sweeping.
        val configDefault = "\"config1\":{}"
        whenever(mockPlugin1.config()).thenReturn(configDefault, configDefault, config1)

        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{$configDefault,$config2}"))
        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{$configDefault,$config2}"))

        val js = testee.getScript(null, listOf())
        assertTrue("a late repo load must be picked up", js.contains("\"features\":{$config1,$config2}"))
        assertFalse("the default config must not survive the load", js.contains(configDefault))
    }

    @Test
    fun whenOptimizeEnabledAndOnlyUnprotectedTemporaryChangesThenContentScopeJsonIsRebuilt() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        val js = testee.getScript(null, listOf())

        assertTrue(js.contains("\"unprotectedTemporary\":[{\"domain\":\"example.com\",\"reason\":\"reason\"}]"))
        assertTrue("the unchanged plugin config must survive the rebuild", js.contains("\"features\":{$config1,$config2}"))
    }

    @Test
    fun whenOptimizeEnabledAndUnprotectedTemporaryIsANewInstanceWithEqualContentsThenScriptIsNotReassembled() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())

        // The repository publishes a new list instance on every reload, even when the contents are unchanged,
        // so the identity fast path misses and the equals fallback has to stop a pointless reassembly.
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions)
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        testee.getScript(null, listOf())

        verify(mockContentScopeJsReader).getContentScopeJS()
    }

    @Test
    fun whenOptimizeEnabledAndAllowListIsANewInstanceWithEqualContentsThenScriptIsNotReassembled() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())

        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl))
        testee.getScript(null, listOf())

        verify(mockContentScopeJsReader).getContentScopeJS()
    }

    @Test
    fun whenOptimizeEnabledThenPluginConfigIsSweptOnEveryCall() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        repeat(4) { testee.getScript(null, listOf()) }

        // Deliberately not cached, see the cachedPluginConfig field comment. The saving is downstream: an
        // unchanged config means no JSON rebuild and no template reassembly.
        verify(mockPlugin1, times(4)).config()
        verify(mockContentScopeJsReader).getContentScopeJS()
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
    fun whenFlagFlipsBackToOptimizedThenPluginConfigDroppedOnTheLegacyPathIsNotResurrected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        // Record the plugin config on the full plugin set.
        optimizeFlag.setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())
        testee.getScript(null, listOf())

        // The legacy path takes over and the plugin config shrinks. Legacy serves the new config but does
        // not maintain the comparison baseline the optimized path keeps.
        optimizeFlag.setRawStoredState(State(enable = false))
        whenever(mockPluginPoint.getPlugins()).thenReturn(listOf(mockPlugin1))
        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{$config1}"))

        // Back on the optimized path, with something else also forcing a rebuild of the content scope
        // JSON. It must not be rebuilt from the config recorded before the flip.
        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        val js = testee.getScript(null, listOf())

        assertFalse("config dropped while on the legacy path must not be resurrected", js.contains(config2))
        assertTrue(js.contains("\"features\":{$config1}"))
    }

    @Test
    fun whenFlagFlipsBackToOptimizedThenExceptionsRestoredToTheirPreFlipValueAreStillReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        // Record the full exception list on the optimized path.
        optimizeFlag.setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())

        // The legacy path takes over, the list shrinks, and legacy rewrites the shared exceptions JSON.
        optimizeFlag.setRawStoredState(State(enable = false))
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(listOf(unprotectedTemporaryException))
        assertTrue(testee.getScript(null, listOf()).contains("\"unprotectedTemporary\":[{\"domain\":\"example.com\",\"reason\":\"reason\"}]"))

        // Back on the optimized path with the list restored to its pre-flip value. Comparing against a baseline
        // captured before the flip would find no change and keep serving the JSON legacy wrote.
        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions)
            .thenReturn(listOf(unprotectedTemporaryException, unprotectedTemporaryException2))
        val js = testee.getScript(null, listOf())

        assertTrue(
            "the restored exception list must be reflected",
            js.contains(
                "\"unprotectedTemporary\":[{\"domain\":\"example.com\",\"reason\":\"reason\"}," +
                    "{\"domain\":\"foo.com\",\"reason\":\"reason2\"}]",
            ),
        )
    }

    @Test
    fun whenFlagFlipsBackToOptimizedThenAnAllowListRestoredToItsPreFlipValueIsStillReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = true))
        testee.getScript(null, listOf())

        optimizeFlag.setRawStoredState(State(enable = false))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        assertTrue(testee.getScript(null, listOf()).contains("[\"$exampleUrl2\"]"))

        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl))
        val js = testee.getScript(null, listOf())

        assertTrue("the restored allow list must be reflected", js.contains("[\"$exampleUrl\"]"))
    }

    @Test
    fun whenFlagFlipsToOptimizedAndTheAllowListEmptiedWhileOnLegacyThenTheEmptyListIsReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = false))
        assertTrue(testee.getScript(null, listOf()).contains("[\"$exampleUrl\"]"))

        // Emptying is the one value a baseline reset cannot be told apart from, so the JSON legacy wrote
        // must not survive on the strength of the baseline alone.
        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(emptyList())
        val js = testee.getScript(null, listOf())

        assertTrue("the emptied allow list must be reflected", js.contains(", [], "))
    }

    @Test
    fun whenFlagFlipsToOptimizedAndExceptionsEmptiedWhileOnLegacyThenTheEmptyListIsReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = false))
        assertTrue(testee.getScript(null, listOf()).contains("\"domain\":\"example.com\""))

        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(emptyList())
        val js = testee.getScript(null, listOf())

        assertTrue("the emptied exception list must be reflected", js.contains("\"unprotectedTemporary\":[]"))
    }

    @Test
    fun whenFlagFlipsOnOffOnWithUnchangedInputsThenEveryScriptIsIdentical() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = true))
        val optimizedJs = testee.getScript(null, listOf())

        optimizeFlag.setRawStoredState(State(enable = false))
        val legacyJs = testee.getScript(null, listOf())

        optimizeFlag.setRawStoredState(State(enable = true))
        val optimizedAgainJs = testee.getScript(null, listOf())

        verifyJsScript(optimizedJs)
        verifyJsScript(legacyJs)
        verifyJsScript(optimizedAgainJs)
        assertEquals(optimizedJs, legacyJs)
        assertEquals(optimizedJs, optimizedAgainJs)
    }

    @Test
    fun whenFlagFlipsAndEveryInputIsEmptiedThenTheEmptyStateIsReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = false))
        assertTrue(testee.getScript(null, listOf()).contains("\"features\":{$config1,$config2}"))

        // Every input now coincides with the value the caches are dropped to, so nothing downstream is
        // recomputed and the dropped state has to be coherent on its own.
        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockPluginPoint.getPlugins()).thenReturn(emptyList())
        whenever(mockUnprotectedTemporary.unprotectedTemporaryExceptions).thenReturn(emptyList())
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(emptyList())
        val js = testee.getScript(null, listOf())

        assertTrue("the emptied content scope must be reflected", js.contains("{\"features\":{},\"unprotectedTemporary\":[]}"))
        assertTrue("the emptied allow list must be reflected", js.contains(", [], "))
    }

    @Test
    fun whenFlagFlipsBackToLegacyThenAnAllowListRestoredToItsPreFlipValueIsStillReflected() {
        val optimizeFlag = contentScopeScriptsFeature.optimizeContentScopeInjection()

        optimizeFlag.setRawStoredState(State(enable = false))
        assertTrue(testee.getScript(null, listOf()).contains("[\"$exampleUrl\"]"))

        // The optimized path rewrites the shared allow list JSON, so legacy cannot rely on a baseline
        // recorded before the flip either.
        optimizeFlag.setRawStoredState(State(enable = true))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl2))
        assertTrue(testee.getScript(null, listOf()).contains("[\"$exampleUrl2\"]"))

        optimizeFlag.setRawStoredState(State(enable = false))
        whenever(mockUserAllowListRepository.domainsInUserAllowList()).thenReturn(listOf(exampleUrl))
        val js = testee.getScript(null, listOf())

        assertTrue("the restored allow list must be reflected", js.contains("[\"$exampleUrl\"]"))
    }

    @Test
    fun whenOptimizeEnabledThenEachToggleCohortIsReadOnce() = runTest {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))
        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        testee.getScript(null, listOf(mockToggle))

        // getCohort() can enrol to self-heal a stale cohort, so the optimized path must not call it twice.
        verify(mockToggle).getCohort()
    }

    @Test
    fun whenOptimizeDisabledThenEachToggleCohortIsReadTwice() = runTest {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = false))
        val mockToggle = mock<Toggle>()
        whenever(mockToggle.getCohort()).thenReturn(Cohort("control", weight = 1))
        whenever(mockToggle.featureName()).thenReturn(FeatureName("contentScopeExperiments", "test"))

        testee.getScript(null, listOf(mockToggle))

        // The legacy path filters then maps, so it reads each toggle twice. Frozen deliberately.
        verify(mockToggle, times(2)).getCohort()
    }

    @Test
    fun whenOptimizeEnabledThenBuildConstantsAreReadOnce() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = true))

        repeat(3) { testee.getScript(null, listOf()) }

        // The version and platform parameters cannot change while the process is alive.
        verify(mockAppBuildConfig).versionCode
    }

    @Test
    fun whenOptimizeDisabledThenBuildConstantsAreReadOnEveryCall() {
        contentScopeScriptsFeature.optimizeContentScopeInjection().setRawStoredState(State(enable = false))

        repeat(3) { testee.getScript(null, listOf()) }

        // The legacy path rebuilds them per call. Frozen deliberately.
        verify(mockAppBuildConfig, times(3)).versionCode
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
        // Two preferences contributors as well as two config ones, so the comparison covers both accumulators
        // in getLegacyPluginParameters and its hand-maintained twin getOptimizedPluginParameters.
        whenever(mockPlugin1.preferences()).thenReturn("\"pref1\":true")
        whenever(mockPlugin2.preferences()).thenReturn("\"pref2\":false")

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
