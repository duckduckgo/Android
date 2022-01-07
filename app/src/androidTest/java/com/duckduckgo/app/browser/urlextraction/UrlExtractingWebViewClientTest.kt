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
import androidx.test.annotation.UiThreadTest
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class UrlExtractingWebViewClientTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var testee: UrlExtractingWebViewClient

    private val requestRewriter: RequestRewriter = mock()
    private val specialUrlDetector: SpecialUrlDetector = mock()
    private val requestInterceptor: RequestInterceptor = mock()
    private val listener: WebViewClientListener = mock()
    private val cookieManager: CookieManager = mock()
    private val loginDetector: DOMLoginDetector = mock()
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore = mock()
    private val uncaughtExceptionRepository: UncaughtExceptionRepository = mock()
    private val dosDetector: DosDetector = DosDetector()
    private val gpc: Gpc = mock()
    private val trustedCertificateStore: TrustedCertificateStore = mock()
    private val webViewHttpAuthStore: WebViewHttpAuthStore = mock()
    private val thirdPartyCookieManager: ThirdPartyCookieManager = mock()
    private val emailInjector: EmailInjector = mock()
    private val accessibilityManager: AccessibilityManager = mock()
    private val urlExtractor: DOMUrlExtractor = mock()
    private val mockWebView: WebView = mock()

    @UiThreadTest
    @Before
    fun setup() {
        testee = UrlExtractingWebViewClient(
            webViewHttpAuthStore,
            trustedCertificateStore,
            requestRewriter,
            specialUrlDetector,
            requestInterceptor,
            offlinePixelCountDataStore,
            uncaughtExceptionRepository,
            cookieManager,
            loginDetector,
            dosDetector,
            gpc,
            thirdPartyCookieManager,
            TestScope(),
            coroutinesTestRule.testDispatcherProvider,
            emailInjector,
            accessibilityManager,
            urlExtractor
        )
    }

    @UiThreadTest
    @Test
    fun whenOnPageStartedCalledThenInjectUrlExtractionJS() {
        testee.onPageStarted(mockWebView, BrowserWebViewClientTest.EXAMPLE_URL, null)
        verify(urlExtractor).injectUrlExtractionJS(mockWebView)
    }
}
