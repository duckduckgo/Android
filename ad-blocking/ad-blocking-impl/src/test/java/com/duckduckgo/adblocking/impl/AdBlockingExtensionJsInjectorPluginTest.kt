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

package com.duckduckgo.adblocking.impl

import android.net.Uri
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State.Cohort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AdBlockingExtensionJsInjectorPluginTest {

    private var canInject = true
    private val scriptletsFlow = MutableStateFlow<List<Scriptlet>>(emptyList())

    private val statusChecker: AdBlockingStatusChecker = mock {
        on { canInject() } doAnswer { canInject }
    }
    private val repository: AdBlockingExtensionRepository = mock {
        on { scriptletsFlow() } doReturn scriptletsFlow
    }
    private val webView: WebView = mock()
    private val contingencyMessageHandler: ContingencyMessageHandler = mock()
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val isolatedName = "scriptlets/isolated/ublock-filters.js"
    private val mainName = "scriptlets/main/ublock-filters.js"
    private val singleScriptlet = listOf(Scriptlet(name = isolatedName, content = "console.log('a')"))
    private val twoScriptlets = listOf(
        Scriptlet(name = mainName, content = "console.log('b')"),
        Scriptlet(name = isolatedName, content = "console.log('a')"),
    )

    private val mockDomainMatcher: AdBlockingExtensionDomainMatcher = mock() {
        on {
            matches(any<Uri>())
        } doReturn true
        on { matches(any<String>()) } doReturn true
    }

    private var killSwitchOn = true
    private var contingencyModeOn = false
    private val feature: AdBlockingExtensionFeature = mock {
        on { self() } doAnswer { toggle(killSwitchOn) }
        on { enableContingencyMode() } doAnswer { toggle(contingencyModeOn) }
    }

    private val experimentalScriptlets = listOf(
        Scriptlet(name = "scriptlets/main/ublock-experimental.js", content = "console.log('e')"),
        Scriptlet(name = isolatedName, content = "console.log('a')"),
    )

    private val plugin by lazy {
        AdBlockingExtensionJsInjectorPlugin(
            statusChecker = statusChecker,
            repository = repository,
            domainMatcher = mockDomainMatcher,
            contingencyMessageHandler = contingencyMessageHandler,
            feature = feature,
            appScope = testScope,
        )
    }

    private fun toggle(enabled: Boolean): Toggle = mock { on { isEnabled() } doReturn enabled }

    private fun experiment(name: String = "contentScopeExperiment12", cohort: String?): Toggle = mock {
        on { featureName() } doReturn Toggle.FeatureName(parentName = "contentScopeExperiments", name = name)
        on { getRawStoredState() } doReturn Toggle.State(assignedCohort = cohort?.let { Cohort(name = it, weight = 1) })
    }

    @After
    fun tearDown() {
        testScope.cancel()
    }

    @Test
    fun whenAllGatesAreOpenThenScriptIsInjected() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(
            eq("javascript:console.log('a')"),
            isNull(),
        )
    }

    @Test
    fun whenMultipleScriptletsThenAllAreInjectedInSortedOrder() {
        scriptletsFlow.value = twoScriptlets

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(
            eq("javascript:console.log('a')\nconsole.log('b')"),
            isNull(),
        )
    }

    @Test
    fun whenUrlIsSubdomainOfConfiguredDomainThenScriptIsInjected() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = "https://m.youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenUrlHasWwwPrefixThenScriptIsInjected() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = "https://www.youtube.com", isDesktopMode = null)

        verify(webView).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenUrlMatchesSecondConfiguredDomainThenScriptIsInjected() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = "https://youtube-nocookie.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenUrlHostNotInConfiguredDomainsThenScriptIsNotInjected() {
        scriptletsFlow.value = singleScriptlet
        whenever(mockDomainMatcher.matches(any<String>())).thenReturn(false)

        plugin.onPageStarted(webView, url = "https://example.com/page", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenStatusCheckerRejectsThenScriptIsNotInjected() {
        canInject = false
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenUrlIsNullThenScriptIsNotInjected() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageStarted(webView, url = null, isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenScriptletsListIsEmptyThenScriptIsNotInjected() {
        scriptletsFlow.value = emptyList()

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenUrlHasNoHostThenScriptIsNotInjected() {
        scriptletsFlow.value = singleScriptlet
        whenever(mockDomainMatcher.matches(any<String>())).thenReturn(false)

        plugin.onPageStarted(webView, url = "data:text/html,<p>hi</p>", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenScriptletDataIsUpdatedThenNextInjectionUsesNewPayload() {
        scriptletsFlow.value = singleScriptlet
        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        scriptletsFlow.value = listOf(Scriptlet(name = isolatedName, content = "console.log('updated')"))
        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(
            eq("javascript:console.log('updated')"),
            isNull(),
        )
    }

    @Test
    fun whenStatusCheckerStartsRejectingMidSessionThenNextInjectionNoOps() {
        scriptletsFlow.value = singleScriptlet
        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        canInject = false
        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenScriptletsIncludeNamesNotInAllowlistThenOnlyAllowlistedScriptletsAreInjected() {
        scriptletsFlow.value = listOf(
            Scriptlet(name = isolatedName, content = "console.log('a')"),
            Scriptlet(name = "rules/youtube.json", content = "{\"some\":\"json\"}"),
            Scriptlet(name = "scriptlets/other/something.js", content = "console.log('other')"),
            Scriptlet(name = mainName, content = "console.log('b')"),
        )

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView).evaluateJavascript(
            eq("javascript:console.log('a')\nconsole.log('b')"),
            isNull(),
        )
    }

    @Test
    fun whenAllScriptletNamesAreNotInAllowlistThenNoInjection() {
        scriptletsFlow.value = listOf(
            Scriptlet(name = "rules/youtube.json", content = "{\"some\":\"json\"}"),
            Scriptlet(name = "scriptlets/other/something.js", content = "console.log('other')"),
        )

        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenOnPageFinishedCalledThenNoInjection() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageFinished(webView, url = "https://youtube.com/page", site = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenOnPageFinishedCalledThenContingencyMessageHandlerIsNotified() {
        val url = "https://youtube.com/page"

        plugin.onPageFinished(webView, url = url, site = null)

        verify(contingencyMessageHandler).onPageLoaded(webView, url)
    }

    @Test
    fun whenTreatmentOnExperimentSiteThenOnlyExperimentalScriptletsAreInjectedEvenIfSettingIsOff() {
        canInject = false
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://www.xvideos.com/video", null, listOf(experiment(cohort = "treatment")))

        verify(webView).evaluateJavascript(eq("javascript:console.log('e')"), isNull())
    }

    @Test
    fun whenControlOnExperimentSiteThenNoInjection() {
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://pornhub.com/", null, listOf(experiment(cohort = "control")))

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenNotEnrolledOnExperimentSiteThenNoInjection() {
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://pornhub.com/", null, emptyList())

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenTreatmentOfAnotherExperimentOnExperimentSiteThenNoInjection() {
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://pornhub.com/", null, listOf(experiment(name = "contentScopeExperiment3", cohort = "treatment")))

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenKillSwitchOffThenNoExperimentInjection() {
        killSwitchOn = false
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://xhamster.com/", null, listOf(experiment(cohort = "treatment")))

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenContingencyModeOnThenNoExperimentInjection() {
        contingencyModeOn = true
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://xnxx.com/", null, listOf(experiment(cohort = "treatment")))

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenTreatmentOnYouTubeThenOnlyFilterScriptletsAreInjected() {
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://youtube.com/page", null, listOf(experiment(cohort = "treatment")))

        verify(webView).evaluateJavascript(eq("javascript:console.log('a')"), isNull())
    }

    @Test
    fun whenTreatmentOnYouTubeWithSettingOffThenNoInjection() {
        canInject = false
        scriptletsFlow.value = experimentalScriptlets

        plugin.onPageStarted(webView, "https://youtube.com/page", null, listOf(experiment(cohort = "treatment")))

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }
}
