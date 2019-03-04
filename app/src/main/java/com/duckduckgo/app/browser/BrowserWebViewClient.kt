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

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import androidx.annotation.AnyThread
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import android.webkit.*
import androidx.core.net.toUri
import com.duckduckgo.app.global.isHttps
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.HTTPS_UPGRADE_SITE_ERROR
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.APP_VERSION
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.ERROR_CODE
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.URL
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import timber.log.Timber
import javax.inject.Inject
import kotlin.concurrent.thread


class BrowserWebViewClient @Inject constructor(
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val httpsUpgrader: HttpsUpgrader,
    private val statisticsDataStore: StatisticsDataStore,
    private val pixel: Pixel
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null

    private var currentUrl: String? = null

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

    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        Timber.d("\nonPageStarted {\nurl: $url\nwebView.url: ${webView.url}\n}\n")
        currentUrl = url

        webViewClientListener?.let {
            it.loadingStarted(url)
            it.navigationOptionsChanged(determineNavigationOptions(webView))
        }

        val uri = if (currentUrl != null) Uri.parse(currentUrl) else null
        if (uri != null) {
            reportHttpsIfInUpgradeList(uri)
        }
    }

    override fun onPageFinished(webView: WebView, url: String?) {
        Timber.d("onPageFinished $url")

        currentUrl = url
        webViewClientListener?.let {
            it.loadingFinished(url)
            it.navigationOptionsChanged(determineNavigationOptions(webView))
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        Timber.v("Intercepting resource ${request.url} on page $currentUrl")
        return requestInterceptor.shouldIntercept(request, webView, currentUrl, webViewClientListener)
    }

    @UiThread
    @Suppress("OverridingDeprecatedMember")
    override fun onReceivedError(view: WebView, errorCode: Int, description: String, failingUrl: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            val url = failingUrl.toUri()
            reportHttpsErrorIfInUpgradeList(url, error = "WEB_RESOURCE_ERROR_$errorCode")
        }
        super.onReceivedError(view, errorCode, description, failingUrl)
    }

    @UiThread
    @TargetApi(Build.VERSION_CODES.M)
    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) {
            reportHttpsErrorIfInUpgradeList(request.url, error = "WEB_RESOURCE_ERROR_${error.errorCode}")
        }
        super.onReceivedError(view, request, error)
    }

    @UiThread
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val uri = error.url.toUri()
        val isMainFrameRequest = currentUrl == uri.toString()
        if (isMainFrameRequest) {
            reportHttpsErrorIfInUpgradeList(uri, "SSL_ERROR_${error.primaryError}")
        }
        super.onReceivedSslError(view, handler, error)
    }

    @AnyThread
    private fun reportHttpsErrorIfInUpgradeList(url: Uri, error: String?) {
        if (!url.isHttps) return
        thread {
            if (httpsUpgrader.isInUpgradeList(url)) {
                reportHttpsUpgradeSiteError(url, error)
                statisticsDataStore.httpsUpgradesFailures += 1
            }
        }
    }

    @AnyThread
    private fun reportHttpsIfInUpgradeList(url: Uri) {
        if (!url.isHttps) return
        thread {
            if (httpsUpgrader.isInUpgradeList(url)) {
                statisticsDataStore.httpsUpgradesTotal += 1
            }
        }
    }

    private fun reportHttpsUpgradeSiteError(url: Uri, error: String?) {
        val host = url.host ?: return
        val params = mapOf(
            APP_VERSION to BuildConfig.VERSION_NAME,
            URL to "https://$host",
            ERROR_CODE to error
        )
        pixel.fire(HTTPS_UPGRADE_SITE_ERROR, params)
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

    private fun determineNavigationOptions(webView: WebView): BrowserNavigationOptions {
        val canGoBack = webView.canGoBack()
        val canGoForward = webView.canGoForward()
        return BrowserNavigationOptions(canGoBack, canGoForward)
    }

    data class BrowserNavigationOptions(val canGoBack: Boolean, val canGoForward: Boolean)

}
