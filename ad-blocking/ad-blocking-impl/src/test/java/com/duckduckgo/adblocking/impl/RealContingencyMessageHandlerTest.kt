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

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ContingencyMessageStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("DenyListedApi") // setRawStoredState
@RunWith(AndroidJUnit4::class)
class RealContingencyMessageHandlerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val feature = FakeFeatureToggleFactory.create(AdBlockingExtensionFeature::class.java)

    private val shownFlow = MutableStateFlow(false)
    private val store = object : ContingencyMessageStore {
        override val shown: StateFlow<Boolean> = shownFlow
        override suspend fun setShown() {
            shownFlow.value = true
        }

        override suspend fun reset() {
            shownFlow.value = false
        }
    }

    private val view = FakeContingencyMessageView()

    private val mockDomainMatcher: AdBlockingExtensionDomainMatcher = mock() {
        on { matches(any<Uri>()) } doReturn true
        on { matches(any<String>()) } doReturn true
    }

    private val handler = RealContingencyMessageHandler(
        feature = feature,
        store = store,
        view = view,
        domainMatcher = mockDomainMatcher,
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private fun setToggles(uxImprovements: Boolean, contingency: Boolean) {
        feature.adBlockingUXImprovements().setRawStoredState(Toggle.State(remoteEnableState = uxImprovements))
        feature.enableContingencyMode().setRawStoredState(Toggle.State(remoteEnableState = contingency))
    }

    @Test
    fun whenAllGatesOpenAndYouTubeAndNotShownThenShouldShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertTrue(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenYouTubeSubdomainThenShouldShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertTrue(handler.shouldShow("https://m.youtube.com/watch?v=abc"))
    }

    @Test
    fun whenYouTubeNoCookieThenShouldShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertTrue(handler.shouldShow("https://youtube-nocookie.com/watch?v=abc"))
    }

    @Test
    fun whenUxImprovementsDisabledThenShouldNotShow() {
        setToggles(uxImprovements = false, contingency = true)

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenContingencyModeDisabledThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = false)

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenUrlIsNotYouTubeThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)
        whenever(mockDomainMatcher.matches(any<String>())).thenReturn(false)

        assertFalse(handler.shouldShow("https://example.com/watch?v=abc"))
    }

    @Test
    fun whenUrlIsNullThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)

        assertFalse(handler.shouldShow(null))
    }

    @Test
    fun whenAlreadyShownThenShouldNotShow() {
        setToggles(uxImprovements = true, contingency = true)
        shownFlow.value = true

        assertFalse(handler.shouldShow("https://youtube.com/watch?v=abc"))
    }

    @Test
    fun whenContingencyModeDisabledThenShownIsReset() = runTest {
        shownFlow.value = true

        handler.onContingencyModeChanged(contingencyEnabled = false)

        assertFalse(shownFlow.value)
    }

    @Test
    fun whenContingencyModeEnabledThenShownIsNotReset() = runTest {
        shownFlow.value = true

        handler.onContingencyModeChanged(contingencyEnabled = true)

        assertTrue(shownFlow.value)
    }

    @Test
    fun whenPageLoadedRepeatedlyBeforeStoreWriteReflectsThenShowsOnlyOnce() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        // Model DataStore write latency: setShown() never flips the observable value during the
        // test, so only the in-memory guard can prevent a repeat show.
        val handler = handlerWith(laggingStore())
        val webView = foregroundWebView()

        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.onPageLoaded(webView, YOUTUBE_URL)

        assertEquals(1, view.shownCount)
    }

    @Test
    fun whenContingencyDisabledThenReenabledThenShowsAgain() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())
        val webView = foregroundWebView()

        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.onContingencyModeChanged(contingencyEnabled = false)
        handler.onPageLoaded(webView, YOUTUBE_URL)

        assertEquals(2, view.shownCount)
    }

    @Test
    fun whenWebViewNotShownThenDoesNotShow() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())

        handler.onPageLoaded(backgroundWebView(), YOUTUBE_URL)

        assertEquals(0, view.shownCount)
    }

    @Test
    fun whenBackgroundLoadThenOneOffNotConsumedAndForegroundStillShows() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val store = laggingStore()
        val handler = handlerWith(store)

        handler.onPageLoaded(backgroundWebView(), YOUTUBE_URL)
        handler.onPageLoaded(foregroundWebView(), YOUTUBE_URL)

        assertEquals(1, view.shownCount)
    }

    @Test
    fun whenViewDoesNotPresentThenOneOffNotConsumedAndNextLoadRetries() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())
        val webView = foregroundWebView()
        view.presents = false

        handler.onPageLoaded(webView, YOUTUBE_URL)
        view.presents = true
        handler.onPageLoaded(webView, YOUTUBE_URL)

        assertEquals(2, view.invokedCount)
        assertEquals(1, view.shownCount)
    }

    @Test
    fun whenPendingShowCancelledBeforeFocusThenNotShownAndOneOffNotConsumed() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())
        view.deferralScope = this
        val webView = foregroundWebView()

        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.cancelPendingShow()
        view.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, view.shownCount)
    }

    @Test
    fun whenGatesCloseWhileDeferredThenNotShownAndOneOffNotConsumed() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())
        view.deferralScope = this
        val webView = foregroundWebView()

        handler.onPageLoaded(webView, YOUTUBE_URL)
        setToggles(uxImprovements = true, contingency = false)
        view.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(0, view.shownCount)
    }

    @Test
    fun whenPageLoadedRepeatedlyWhileDeferredThenLaunchesAndShowsOnce() = runTest {
        setToggles(uxImprovements = true, contingency = true)
        val handler = handlerWith(laggingStore())
        view.deferralScope = this
        val webView = foregroundWebView()

        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.onPageLoaded(webView, YOUTUBE_URL)
        handler.onPageLoaded(webView, YOUTUBE_URL)
        view.gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, view.invokedCount)
        assertEquals(1, view.shownCount)
    }

    private fun foregroundWebView() = mock<WebView> {
        on { isShown } doReturn true
        on { url } doReturn YOUTUBE_URL
    }

    private fun backgroundWebView() = mock<WebView> { on { isShown } doReturn false }

    private fun laggingStore() = object : ContingencyMessageStore {
        private val value = MutableStateFlow(false)
        override val shown: StateFlow<Boolean> = value
        override suspend fun setShown() { /* write is async; observable value lags */ }
        override suspend fun reset() { value.value = false }
    }

    private fun handlerWith(store: ContingencyMessageStore) = RealContingencyMessageHandler(
        feature = feature,
        store = store,
        view = view,
        domainMatcher = mockDomainMatcher,
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private class FakeContingencyMessageView : ContingencyMessageView {
        var shownCount = 0
        var invokedCount = 0

        var presents = true

        // When set, presentation is deferred on this scope until [gate] completes, modelling awaitWindowFocus
        // suspending until the WebView's window regains focus. Left null for synchronous presentation.
        var deferralScope: CoroutineScope? = null
        val gate = CompletableDeferred<Unit>()

        override fun launchWhenFocused(webView: WebView, block: suspend () -> Unit): Job? {
            invokedCount++
            val scope = deferralScope ?: run {
                // Synchronous focus: the block has no real suspension points, so an unconfined
                // dispatcher runs it to completion before returning.
                CoroutineScope(UnconfinedTestDispatcher()).launch { block() }
                return null
            }
            return scope.launch {
                gate.await()
                block()
            }
        }

        override fun show(webView: WebView): Boolean {
            if (!presents) return false
            shownCount++
            return true
        }
    }

    private companion object {
        private const val YOUTUBE_URL = "https://youtube.com/watch?v=abc"
    }
}
