/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.urlextraction

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class UrlExtractingWebViewClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: UrlExtractingWebViewClient

    private val requestInterceptor: RequestInterceptor = mock()
    private val cookieManager: CookieManager = mock()
    private val gpc: Gpc = mock()
    private val trustedCertificateStore: TrustedCertificateStore = mock()
    private val webViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val thirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val urlExtractor: DOMUrlExtractor = mock()
    private val mockWebView: WebView = mock()

    @UiThreadTest
    @Before
    fun setup() {
        testee = UrlExtractingWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestInterceptor,
            cookieManager,
            gpc,
            thirdPartyCookieManager,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            urlExtractor
        )
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectUrlExtractionJS() {
        testee.onPageStarted(mockWebView, BrowserWebViewClientTest.EXAMPLE_URL, null)
        verify(urlExtractor).injectUrlExtractionJS(mockWebView)
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsNullThenDoNotInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(mockWebView, null, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsValidThenInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(true)

        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        verify(gpc).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledIfUrlIsNotAndValidThenDoNotInjectGpcToDom() = runTest {
        whenever(gpc.canGpcBeUsedByUrl(any())).thenReturn(false)

        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        verify(gpc, never()).getGpcJs()
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenProcessUriForThirdPartyCookiesCalled() = runTest {
        testee.onPageStarted(mockWebView, EXAMPLE_URL, null)
        verify(thirdPartyCookieManager).processUriForThirdPartyCookies(mockWebView, EXAMPLE_URL.toUri())
    }

    @UiThreadTest
    @Test
    fun whenOnPageFinishedCalledThenFlushCookies() {
        testee.onPageFinished(mockWebView, null)
        verify(cookieManager).flush()
    }

    companion object {
        const val EXAMPLE_URL = "example.com"
    }
}
