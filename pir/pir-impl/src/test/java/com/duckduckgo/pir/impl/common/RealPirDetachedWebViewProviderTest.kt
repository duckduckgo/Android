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

package com.duckduckgo.pir.impl.common

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RealPirDetachedWebViewProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockScanWebViewSizeToggle: Toggle = mock()

    private lateinit var testee: RealPirDetachedWebViewProvider

    @Before
    fun setUp() {
        whenever(mockPirRemoteFeatures.scanWebViewSize()).thenReturn(mockScanWebViewSizeToggle)
        testee = RealPirDetachedWebViewProvider(
            pirRequestInterceptor = mock(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            pirRemoteFeatures = mockPirRemoteFeatures,
        )
    }

    // Robolectric's WebView provider drops setFrame, so width and height stay 0 even after layout(). The measured size
    // and a completed layout pass are what a test can observe; on a device that layout pass applies the measured frame.
    @Test
    fun whenScanWebViewSizeEnabledThenCreatedWebViewIsLaidOut1024CssPxWideAtScreenAspectRatio() {
        whenever(mockScanWebViewSizeToggle.isEnabled()).thenReturn(true)
        val metrics = context.resources.displayMetrics
        val expectedWidthPx = (1024 * metrics.density).roundToInt()
        val expectedHeightPx = (expectedWidthPx * metrics.heightPixels.toDouble() / metrics.widthPixels).toInt()

        val webView = createWebView()

        assertEquals(expectedWidthPx, webView.measuredWidth)
        assertEquals(expectedHeightPx, webView.measuredHeight)
        assertFalse(webView.isLayoutRequested)
    }

    @Test
    fun whenScanWebViewSizeDisabledThenCreatedWebViewIsNotLaidOut() {
        whenever(mockScanWebViewSizeToggle.isEnabled()).thenReturn(false)

        val webView = createWebView()

        assertEquals(0, webView.measuredWidth)
        assertEquals(0, webView.measuredHeight)
        assertTrue(webView.isLayoutRequested)
    }

    private fun createWebView(): WebView = testee.createInstance(
        context = context,
        scriptToLoad = "",
        userAgent = PirWebViewUserAgent(userAgent = "test-user-agent"),
        onPageLoaded = {},
        onPageLoadFailed = {},
        onRendererGone = {},
    )
}
