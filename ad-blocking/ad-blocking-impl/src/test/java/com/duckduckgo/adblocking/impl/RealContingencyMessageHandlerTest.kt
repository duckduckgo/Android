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
import android.webkit.WebView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adblocking.impl.remoteconfig.AdBlockingExtensionFeature
import com.duckduckgo.adblocking.impl.remoteconfig.ContingencyMessageStore
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

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

    private val handler = RealContingencyMessageHandler(
        feature = feature,
        store = store,
        view = view,
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

    private fun foregroundWebView() = mock<WebView> { on { isShown } doReturn true }

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
        appScope = coroutineRule.testScope,
        dispatchers = coroutineRule.testDispatcherProvider,
    )

    private class FakeContingencyMessageView : ContingencyMessageView {
        var shownCount = 0
        override fun show(webView: WebView) {
            shownCount++
        }
    }

    private companion object {
        private const val YOUTUBE_URL = "https://youtube.com/watch?v=abc"
    }
}
