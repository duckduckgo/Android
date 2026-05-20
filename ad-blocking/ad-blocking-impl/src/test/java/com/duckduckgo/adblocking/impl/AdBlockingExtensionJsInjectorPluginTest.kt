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

import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
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
    private val userEnabledFlow = MutableStateFlow(true)
    private val settingsRepository: AdBlockingSettingsRepository = object : AdBlockingSettingsRepository {
        override fun isEnabledFlow(): Flow<Boolean> = userEnabledFlow
        override suspend fun setEnabled(enabled: Boolean) {
            userEnabledFlow.value = enabled
        }
    }
    private val webView: WebView = mock()
    private val testScope = CoroutineScope(UnconfinedTestDispatcher())

    private val singleScriptlet = listOf(Scriptlet(name = "a.js", content = "console.log('a')"))
    private val twoScriptlets = listOf(
        Scriptlet(name = "b.js", content = "console.log('b')"),
        Scriptlet(name = "a.js", content = "console.log('a')"),
    )

    private val plugin by lazy {
        AdBlockingExtensionJsInjectorPlugin(
            statusChecker = statusChecker,
            settingsRepository = settingsRepository,
            repository = repository,
            appScope = testScope,
        )
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
    fun whenUserHasDisabledThenScriptIsNotInjected() {
        userEnabledFlow.value = false
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

        plugin.onPageStarted(webView, url = "data:text/html,<p>hi</p>", isDesktopMode = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }

    @Test
    fun whenScriptletDataIsUpdatedThenNextInjectionUsesNewPayload() {
        scriptletsFlow.value = singleScriptlet
        plugin.onPageStarted(webView, url = "https://youtube.com/page", isDesktopMode = null)

        scriptletsFlow.value = listOf(Scriptlet(name = "a.js", content = "console.log('updated')"))
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
    fun whenOnPageFinishedCalledThenNoInjection() {
        scriptletsFlow.value = singleScriptlet

        plugin.onPageFinished(webView, url = "https://youtube.com/page", site = null)

        verify(webView, never()).evaluateJavascript(any(), isNull())
    }
}
