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
import com.duckduckgo.app.browser.BrowserWebViewClient.RequestOrigin.MainFrame
import com.duckduckgo.app.browser.BrowserWebViewClient.RequestOrigin.SubFrame
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource.*
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.URI


class BrowserWebViewClient(
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val mainFrameUrlHandler: SpecialUrlHandler,
    private val subFrameUrlHandler: SpecialUrlHandler
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    private var lastPageStarted: String? = null


    /**
     * This is the new method of url overriding available from API 24 onwards
     */
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url
        val requestOrigin = if (request.isForMainFrame) MainFrame else SubFrame
        return shouldOverride(view, url, requestOrigin)
    }

    /**
     * * This is the old, deprecated method of url overriding available until API 23
     */
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(view: WebView, urlString: String): Boolean {
        val url = Uri.parse(urlString)
        return shouldOverride(view, url, RequestOrigin.Unknown)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(webView: WebView, url: Uri, requestOrigin: RequestOrigin): Boolean {
        return try {
            Timber.d("Request origin: ${requestOrigin.javaClass.simpleName}, shouldOverride $url")

            val urlType = specialUrlDetector.determineType(url)
            val urlHandler = getUrlHandler(requestOrigin)
            urlHandler.handleUrl(webView, urlType, webViewClientListener)

        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOULD_OVERRIDE_REQUEST)
                throw e
            }
            false
        }
    }

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        try {
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            if (url != null && url == lastPageStarted) {
                webViewClientListener?.pageRefreshed(url)
            }
            lastPageStarted = url
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_STARTED)
                throw e
            }
        }
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        try {
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_FINISHED)
                throw e
            }
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            try {
                val documentUrl = withContext(Dispatchers.Main) { webView.url }
                Timber.v("Intercepting resource ${request.url} on page $documentUrl")
                requestInterceptor.shouldIntercept(request, webView, documentUrl, webViewClientListener)
            } catch (e: Throwable) {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOULD_INTERCEPT_REQUEST)
                throw e
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        Timber.w("onRenderProcessGone. Did it crash? ${detail?.didCrash()}")
        if (detail?.didCrash() == true) {
            offlinePixelCountDataStore.webRendererGoneCrashCount += 1
        } else {
            offlinePixelCountDataStore.webRendererGoneKilledCount += 1
        }
        return super.onRenderProcessGone(view, detail)
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(view: WebView?, handler: HttpAuthHandler?, host: String?, realm: String?) {
        try {
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
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_HTTP_AUTH_REQUEST)
                throw e
            }
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

    private fun getUrlHandler(requestOrigin: RequestOrigin): SpecialUrlHandler {
        return when (requestOrigin) {
            is MainFrame -> mainFrameUrlHandler
            is SubFrame -> subFrameUrlHandler

            // ideally we wouldn't have an unknown case, but for now, default to main frame handling for compatibility with existing behaviour
            is RequestOrigin.Unknown -> mainFrameUrlHandler
        }
    }

    sealed class RequestOrigin {
        object MainFrame : RequestOrigin()
        object SubFrame : RequestOrigin()
        object Unknown : RequestOrigin()
    }
}