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

package com.duckduckgo.pir.impl.dashboard

import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.pir.impl.dashboard.messaging.PirDashboardWebViewClient
import com.duckduckgo.pir.impl.dashboard.purchase.PirFreemiumPurchaseUrlRouter
import com.duckduckgo.pir.impl.dashboard.purchase.PirPurchaseRoute
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirDashboardWebViewClientTest {

    private val router: PirFreemiumPurchaseUrlRouter = mock()
    private val listener: PirDashboardWebViewClient.Listener = mock()
    private val webView: WebView = mock()

    private val testee = PirDashboardWebViewClient(router).also { it.listener = listener }

    private val purchaseRoute = PirPurchaseRoute.NativePurchaseFlow(origin = "funnel_freescan_android", featurePage = "pir")

    private fun request(
        url: String,
        isForMainFrame: Boolean = true,
    ): WebResourceRequest = mock<WebResourceRequest>().also {
        whenever(it.url).thenReturn(url.toUri())
        whenever(it.isForMainFrame).thenReturn(isForMainFrame)
    }

    @Test
    fun `test cancels navigation and notifies listener for a purchase url`() {
        val request = request("https://duckduckgo.com/subscriptions")
        whenever(router.route(request.url)).thenReturn(purchaseRoute)

        assertTrue(testee.shouldOverrideUrlLoading(webView, request))
        verify(listener).onSubscriptionPurchaseRequested(purchaseRoute)
    }

    @Test
    fun `test allows navigation for a non purchase url`() {
        val request = request("https://duckduckgo.com/dbp")
        whenever(router.route(request.url)).thenReturn(PirPurchaseRoute.NotHandled)

        assertFalse(testee.shouldOverrideUrlLoading(webView, request))
        verify(listener, never()).onSubscriptionPurchaseRequested(any())
    }

    @Test
    fun `test does not intercept sub frame request`() {
        val request = request("https://duckduckgo.com/subscriptions", isForMainFrame = false)

        assertFalse(testee.shouldOverrideUrlLoading(webView, request))
        verify(router, never()).route(any())
        verify(listener, never()).onSubscriptionPurchaseRequested(any())
    }
}
