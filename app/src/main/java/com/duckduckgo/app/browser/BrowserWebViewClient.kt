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
import androidx.annotation.RequiresApi
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.statistics.store.OfflinePixelDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URI


class BrowserWebViewClient(
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val offlinePixelDataStore: OfflinePixelDataStore
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    private var lastPageStarted: String? = null


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
        webViewClientListener?.navigationStateChanged(WebViewNavigationState(webView.copyBackForwardList()))
        if (url != null && url == lastPageStarted) {
            webViewClientListener?.pageRefreshed(url)
        }
        lastPageStarted = url
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        webViewClientListener?.navigationStateChanged(WebViewNavigationState(webView.copyBackForwardList()))
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            val documentUrl = withContext(Dispatchers.Main) { webView.url }
            Timber.v("Intercepting resource ${request.url} on page $documentUrl")
            requestInterceptor.shouldIntercept(request, webView, documentUrl, webViewClientListener)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        if (detail?.didCrash() == true) {
            offlinePixelDataStore.webRendererGoneCrashCount += 1
        } else {
            offlinePixelDataStore.webRendererGoneKilledCount += 1
        }
        return super.onRenderProcessGone(view, detail)
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        Timber.v("onReceivedHttpAuthRequest ${view?.url} $realm, $host")

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
}