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
import android.os.Build
import android.webkit.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.app.browser.certificates.rootstore.CertificateValidationState
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.privacy.config.api.Gpc
import kotlinx.coroutines.*
import timber.log.Timber

class UrlExtractingWebViewClient(
    private val webViewHttpAuthStore: WebViewHttpAuthStore,
    private val trustedCertificateStore: TrustedCertificateStore,
    private val requestInterceptor: RequestInterceptor,
    private val cookieManager: CookieManager,
    private val gpc: Gpc,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val urlExtractor: DOMUrlExtractor
) : WebViewClient() {

    var urlExtractionListener: UrlExtractionListener? = null

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        Timber.v("onPageStarted webViewUrl: ${webView.url} URL: $url")
        url?.let {
            appCoroutineScope.launch(dispatcherProvider.default()) {
                thirdPartyCookieManager.processUriForThirdPartyCookies(webView, url.toUri())
            }
        }
        injectGpcToDom(webView, url)
        Timber.d("Tracking link detection: Injecting JS for URL extraction")
        urlExtractor.injectUrlExtractionJS(webView)
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        Timber.v("onPageFinished webViewUrl: ${webView.url} URL: $url")
        flushCookies()
    }

    private fun injectGpcToDom(webView: WebView, url: String?) {
        url?.let {
            if (gpc.canGpcBeUsedByUrl(url)) {
                webView.evaluateJavascript("javascript:${gpc.getGpcJs()}", null)
            }
        }
    }

    private fun flushCookies() {
        appCoroutineScope.launch(dispatcherProvider.io()) { cookieManager.flush() }
    }

    @WorkerThread
    override fun shouldInterceptRequest(
        webView: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        return runBlocking {
            val documentUrl = withContext(Dispatchers.Main) { webView.url }
            Timber.v(
                "Intercepting resource ${request.url} type:${request.method} on page $documentUrl"
            )
            requestInterceptor.shouldIntercept(
                request, webView, documentUrl, null
            )
        }
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?
    ) {
        Timber.v("onReceivedHttpAuthRequest ${view?.url} $realm, $host")
        if (handler != null) {
            Timber.v(
                "onReceivedHttpAuthRequest - useHttpAuthUsernamePassword [${handler.useHttpAuthUsernamePassword()}]"
            )
            if (handler.useHttpAuthUsernamePassword()) {
                val credentials =
                    view?.let {
                        webViewHttpAuthStore.getHttpAuthUsernamePassword(
                            it, host.orEmpty(), realm.orEmpty()
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
                Timber.d(
                    "The certificate authority ${error.certificate.issuedBy.dName} is not trusted"
                )
                trusted = trustedCertificateStore.validateSslCertificateChain(error.certificate)
            }
            else -> Timber.d("SSL error ${error.primaryError}")
        }

        Timber.d("The certificate authority validation result is $trusted")
        if (trusted is CertificateValidationState.TrustedChain) handler.proceed()
        else super.onReceivedSslError(view, handler, error)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (error?.errorCode == ERROR_CONNECT) {
                urlExtractionListener?.onUrlExtractionError()
            }
        } else {
            urlExtractionListener?.onUrlExtractionError()
        }
        super.onReceivedError(view, request, error)
    }
}
