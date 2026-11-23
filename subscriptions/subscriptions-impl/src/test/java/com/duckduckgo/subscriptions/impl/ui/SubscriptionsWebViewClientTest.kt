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

package com.duckduckgo.subscriptions.impl.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.subscriptions.impl.pixels.CancellationFlowLoadFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SubscriptionsWebViewClientTest {

    private var reportedError: Pair<CancellationFlowLoadFailureReason, Int>? = null

    private val testee = SubscriptionsWebViewClient(
        specialUrlDetector = mock(),
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        onRenderProcessCrash = { false },
        subscriptionRestoreWideEvent = mock(),
        onMainFrameLoadError = { reason, errorCode -> reportedError = reason to errorCode },
    )

    @Test
    fun whenMainFrameHttpErrorThenErrorReported() {
        testee.onReceivedHttpError(null, request(isForMainFrame = true), response(statusCode = 404))

        assertEquals(CancellationFlowLoadFailureReason.HTTP to 404, reportedError)
    }

    @Test
    fun whenMainFrameServerErrorThenErrorReported() {
        testee.onReceivedHttpError(null, request(isForMainFrame = true), response(statusCode = 503))

        assertEquals(CancellationFlowLoadFailureReason.HTTP to 503, reportedError)
    }

    @Test
    fun whenSubresourceHttpErrorThenNothingReported() {
        testee.onReceivedHttpError(null, request(isForMainFrame = false), response(statusCode = 500))

        assertNull(reportedError)
    }

    @Test
    fun whenMainFrameNetworkErrorThenErrorReported() {
        val error: WebResourceError = mock()
        whenever(error.errorCode).thenReturn(WebViewClient.ERROR_HOST_LOOKUP)

        testee.onReceivedError(null, request(isForMainFrame = true), error)

        assertEquals(CancellationFlowLoadFailureReason.NETWORK to WebViewClient.ERROR_HOST_LOOKUP, reportedError)
    }

    @Test
    fun whenSubresourceNetworkErrorThenNothingReported() {
        testee.onReceivedError(null, request(isForMainFrame = false), mock())

        assertNull(reportedError)
    }

    private fun request(isForMainFrame: Boolean): WebResourceRequest = mock<WebResourceRequest>().also {
        whenever(it.isForMainFrame).thenReturn(isForMainFrame)
    }

    private fun response(statusCode: Int): WebResourceResponse = mock<WebResourceResponse>().also {
        whenever(it.statusCode).thenReturn(statusCode)
    }
}
