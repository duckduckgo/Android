/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.*
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.global.isHttpsVersionOfUri
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.net.URI


class BrowserWebViewClient(
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null

    private var currentUrl: String? = null
    private var tempTimer = 0L

    /**
     * This is the new method of url overriding available from API 24 onwards
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        return shouldOverride(view, url)
    }

    /**
     * * This is the old, deprecated method of url overriding available until API 23
     */
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView, urlString: String): Boolean {
        val url = Uri.parse(urlString)
        return shouldOverride(view, url)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(webView: WebView, url: Uri): Boolean {
        Timber.v("shouldOverride $url")

        val urlType = specialUrlDetector.determineType(url)

        return when (urlType) {
            is SpecialUrlDetector.UrlType.Email -> consume { webViewClientListener?.sendEmailRequested(urlType.emailAddress) }
            is SpecialUrlDetector.UrlType.Telephone -> consume { webViewClientListener?.dialTelephoneNumberRequested(urlType.telephoneNumber) }
            is SpecialUrlDetector.UrlType.Sms -> consume { webViewClientListener?.sendSmsRequested(urlType.telephoneNumber) }
            is SpecialUrlDetector.UrlType.IntentType -> consume {
                Timber.i("Found intent type link for $urlType.url")
                launchExternalApp(urlType)
            }
            is SpecialUrlDetector.UrlType.Unknown -> {
                Timber.w("Unable to process link type for ${urlType.url}")
                webView.loadUrl(webView.originalUrl)
                return false
            }
            is SpecialUrlDetector.UrlType.SearchQuery -> return false
            is SpecialUrlDetector.UrlType.Web -> {
                if (requestRewriter.shouldRewriteRequest(url)) {
                    val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                    webView.loadUrl(newUri.toString())
                    return true
                }
                return false
            }
        }
    }

    private fun launchExternalApp(urlType: SpecialUrlDetector.UrlType.IntentType) {
        webViewClientListener?.externalAppLinkClicked(urlType)
    }

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        tempTimer = System.currentTimeMillis()
        Timber.d("\nonPageStarted {\nurl: $url\nwebView.url: ${webView.url}\n}\n")
        currentUrl = url

        webViewClientListener?.let {
            it.loadingStarted(url)
            it.navigationOptionsChanged(WebViewNavigationOptions(webView.copyBackForwardList()))
        }
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        currentUrl = url
        webViewClientListener?.let {
            it.loadingFinished(url)
            it.navigationOptionsChanged(WebViewNavigationOptions(webView.copyBackForwardList()))
        }

        Timber.i("Page Load Time: ${System.currentTimeMillis() - tempTimer}ms for $url")
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        Timber.v("Intercepting resource ${request.url} on page $currentUrl")
        return runBlocking { requestInterceptor.shouldIntercept(request, webView, currentUrl, webViewClientListener) }
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        Timber.v("onReceivedHttpAuthRequest ${view?.url} $realm, $host,  $currentUrl")

        if (handler != null) {
            Timber.v("onReceivedHttpAuthRequest - useHttpAuthUsernamePassword [${handler.useHttpAuthUsernamePassword()}]")
            if (handler.useHttpAuthUsernamePassword()) {
                val credentials = buildAuthenticationCredentials(host.orEmpty(), realm.orEmpty(), view)

                if (credentials != null) {
                    handler.proceed(credentials[0], credentials[1])
                } else {
                    showAuthenticationDialog(view, handler, host, realm)
                }
            } else {
                showAuthenticationDialog(view, handler, host, realm)
            }
        } else {
            super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }
    }

    private fun buildAuthenticationCredentials(
        host: String,
        realm: String,
        view: WebView?
    ): Array<out String>? {
        val webViewDatabase = WebViewDatabase.getInstance(view?.context)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webViewDatabase.getHttpAuthUsernamePassword(host, realm)
        } else {
            @Suppress("DEPRECATION")
            view?.getHttpAuthUsernamePassword(host, realm)
        }
    }

    private fun showAuthenticationDialog(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?
    ) {
        webViewClientListener?.let {
            Timber.v("showAuthenticationDialog - $host, $realm")

            val siteURL = if (view?.url != null) "${URI(view.url).scheme}://$host" else host.orEmpty()

            val request = BasicAuthenticationRequest(
                handler = handler,
                host = host.orEmpty(),
                realm = realm.orEmpty(),
                site = siteURL
            )

            it.requiresAuthentication(request)
        }
    }

    /**
     * Utility to function to execute a function, and then return true
     *
     * Useful to reduce clutter in repeatedly including `return true` after doing the real work.
     */
    private inline fun consume(function: () -> Unit): Boolean {
        function()
        return true
    }

    interface BrowserNavigationOptions {
        val stepsToPreviousPage: Int
        val canGoBack: Boolean
        val canGoForward: Boolean
        val hasNavigationHistory: Boolean
    }

    data class WebViewNavigationOptions(val stack: WebBackForwardList) : BrowserNavigationOptions {

        override val stepsToPreviousPage: Int = if (stack.isHttpsUpgrade) 2 else 1
        override val canGoBack: Boolean = stack.currentIndex >= stepsToPreviousPage
        override val canGoForward: Boolean = stack.currentIndex + 1 < stack.size
        override val hasNavigationHistory = stack.size != 0

        private val WebBackForwardList.isHttpsUpgrade: Boolean
            get() {
                if (currentIndex < 1) return false
                val current = currentItem?.originalUrl?.toUri() ?: return false
                val previous = getItemAtIndex(currentIndex - 1).originalUrl?.toUri() ?: return false
                return current.isHttpsVersionOfUri(previous)
            }
    }
}