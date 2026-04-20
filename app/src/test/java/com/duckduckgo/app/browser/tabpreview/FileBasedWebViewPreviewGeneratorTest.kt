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

package com.duckduckgo.app.browser.tabpreview

import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
class FileBasedWebViewPreviewGeneratorTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val trackingDispatcherProvider = TrackingDispatcherProvider(coroutineTestRule.testDispatcher)
    private val testee = FileBasedWebViewPreviewGenerator(trackingDispatcherProvider)

    @Test
    fun whenGeneratePreviewInvokedThenComputationDispatcherIsNeverUsed() = runTest {
        val webView: WebView = mock()

        runCatching { testee.generatePreview(webView) }

        assertEquals(0, trackingDispatcherProvider.computationInvocations)
        assertTrue("io() dispatcher should be used for bitmap work", trackingDispatcherProvider.ioInvocations >= 1)
    }

    @Test
    fun whenGeneratePreviewInvokedThenScrollbarsAreDisabledThenReEnabled() = runTest {
        val webView: WebView = mock()

        runCatching { testee.generatePreview(webView) }

        val inOrder = inOrder(webView)
        inOrder.verify(webView).isVerticalScrollBarEnabled = false
        inOrder.verify(webView).isHorizontalScrollBarEnabled = false
        inOrder.verify(webView).isVerticalScrollBarEnabled = true
        inOrder.verify(webView).isHorizontalScrollBarEnabled = true
    }

    private class TrackingDispatcherProvider(
        private val testDispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        var ioInvocations = 0
        var computationInvocations = 0

        override fun io(): CoroutineDispatcher {
            ioInvocations++
            return testDispatcher
        }

        override fun computation(): CoroutineDispatcher {
            computationInvocations++
            return testDispatcher
        }

        override fun main(): CoroutineDispatcher = testDispatcher
        override fun unconfined(): CoroutineDispatcher = testDispatcher
    }
}
