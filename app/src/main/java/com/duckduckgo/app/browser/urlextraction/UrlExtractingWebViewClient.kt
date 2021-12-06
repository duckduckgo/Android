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

import android.graphics.Bitmap
import android.webkit.*
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.*
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.email.EmailInjector
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber

class UrlExtractingWebViewClient(
    webViewHttpAuthStore: WebViewHttpAuthStore,
    trustedCertificateStore: TrustedCertificateStore,
    requestRewriter: RequestRewriter,
    specialUrlDetector: SpecialUrlDetector,
    requestInterceptor: RequestInterceptor,
    offlinePixelCountDataStore: OfflinePixelCountDataStore,
    uncaughtExceptionRepository: UncaughtExceptionRepository,
    cookieManager: CookieManager,
    loginDetector: DOMLoginDetector,
    dosDetector: DosDetector,
    gpc: Gpc,
    thirdPartyCookieManager: ThirdPartyCookieManager,
    appCoroutineScope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    emailInjector: EmailInjector,
    accessibilityManager: AccessibilityManager,
    private val urlExtractor: DOMUrlExtractor
) : BrowserWebViewClient(
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
    appCoroutineScope,
    dispatcherProvider,
    emailInjector,
    accessibilityManager
) {
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(webView, url, favicon)
        Timber.d("Tracking link detection: Injecting JS for URL extraction")
        urlExtractor.injectUrlExtractionJS(webView)
    }
}
