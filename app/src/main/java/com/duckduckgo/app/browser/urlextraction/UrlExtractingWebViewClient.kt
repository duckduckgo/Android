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
import android.net.http.SslError
import android.net.http.SslError.*
import android.webkit.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.app.browser.certificates.rootstore.CertificateValidationState
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import kotlinx.coroutines.*
import logcat.LogPriority.VERBOSE
import logcat.logcat

class UrlExtractingWebViewClient(
    private val webViewHttpAuthStore: WebViewHttpAuthStore,
    private val trustedCertificateStore: TrustedCertificateStore,
    private val requestInterceptor: RequestInterceptor,
    private val cookieManagerProvider: CookieManagerProvider,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val urlExtractor: DOMUrlExtractor,
) : WebViewClient() {

    var urlExtractionListener: UrlExtractionListener? = null

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        logcat(VERBOSE) { "onPageStarted webViewUrl: ${webView.url} URL: $url" }
        url?.let {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                thirdPartyCookieManager.processUriForThirdPartyCookies(webView, url.toUri())
            }
        }
        logcat { "AMP link detection: Injecting JS for URL extraction" }
        urlExtractor.injectUrlExtractionJS(webView)
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        logcat(VERBOSE) { "onPageFinished webViewUrl: ${webView.url} URL: $url" }
        flushCookies()
    }

    private fun flushCookies() {
        appCoroutineScope.launch(dispatcherProvider.io()) { cookieManagerProvider.get()?.flush() }
    }

    @WorkerThread
    override fun shouldInterceptRequest(
        webView: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return runBlocking {
            val documentUrl = withContext(dispatcherProvider.main()) { webView.url?.toUri() }
            logcat(VERBOSE) { "Intercepting resource ${request.url} type:${request.method} on page $documentUrl" }
            requestInterceptor.shouldIntercept(
                request,
                webView,
                documentUrl,
                null,
            )
        }
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?,
    ) {
        logcat(VERBOSE) { "onReceivedHttpAuthRequest ${view?.url} $realm, $host" }
        if (handler != null) {
            logcat(VERBOSE) { "onReceivedHttpAuthRequest - useHttpAuthUsernamePassword [${handler.useHttpAuthUsernamePassword()}]" }
            if (handler.useHttpAuthUsernamePassword()) {
                val credentials =
                    view?.let {
                        webViewHttpAuthStore.getHttpAuthUsernamePassword(
                            it,
                            host.orEmpty(),
                            realm.orEmpty(),
                        )
                    }

                if (credentials != null) {
                    handler.proceed(credentials.username, credentials.password)
                }
            }
        } else {
            super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: SslError) {
        var trusted: CertificateValidationState = CertificateValidationState.UntrustedChain
        when (error.primaryError) {
            SSL_UNTRUSTED -> {
                logcat { "The certificate authority ${error.certificate.issuedBy.dName} is not trusted" }
                trusted = trustedCertificateStore.validateSslCertificateChain(error.certificate)
            }
            else -> logcat { "SSL error ${error.primaryError}" }
        }

        logcat { "The certificate authority validation result is $trusted" }
        if (trusted is CertificateValidationState.TrustedChain) {
            handler.proceed()
        } else {
            super.onReceivedSslError(view, handler, error)
        }
    }

    override fun onReceivedError(
        webView: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (webView != null) {
            val initialUrl = (webView as UrlExtractingWebView).initialUrl

            if (error?.errorCode == ERROR_CONNECT) {
                urlExtractionListener?.onUrlExtractionError(initialUrl)
            }
        }
        super.onReceivedError(webView, request, error)
    }
}
