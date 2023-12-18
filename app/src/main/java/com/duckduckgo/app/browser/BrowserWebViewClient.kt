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
import android.net.http.SslError
import android.net.http.SslError.*
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.*
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import androidx.tracing.Trace
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.accessibility.AccessibilityManager
import com.duckduckgo.app.browser.WebViewErrorResponse.BAD_URL
import com.duckduckgo.app.browser.WebViewErrorResponse.CONNECTION
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_KILLED
import com.duckduckgo.app.browser.certificates.rootstore.CertificateValidationState
import com.duckduckgo.app.browser.certificates.rootstore.TrustedCertificateStore
import com.duckduckgo.app.browser.cookies.ThirdPartyCookieManager
import com.duckduckgo.app.browser.httpauth.WebViewHttpAuthStore
import com.duckduckgo.app.browser.logindetection.DOMLoginDetector
import com.duckduckgo.app.browser.logindetection.WebNavigationEvent
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.contentscopescripts.api.ContentScopeScripts
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.privacy.config.api.AmpLinks
import com.google.android.material.snackbar.Snackbar
import java.lang.Exception
import java.net.InetAddress
import java.net.URI
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.URL

private const val LATENCY_THRESHOLD = 150


class BrowserWebViewClient @Inject constructor(
    private val webViewHttpAuthStore: WebViewHttpAuthStore,
    private val trustedCertificateStore: TrustedCertificateStore,
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val cookieManagerProvider: CookieManagerProvider,
    private val loginDetector: DOMLoginDetector,
    private val dosDetector: DosDetector,
    private val thirdPartyCookieManager: ThirdPartyCookieManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val browserAutofillConfigurator: BrowserAutofill.Configurator,
    private val accessibilityManager: AccessibilityManager,
    private val ampLinks: AmpLinks,
    private val printInjector: PrintInjector,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val adClickManager: AdClickManager,
    private val autoconsent: Autoconsent,
    private val contentScopeScripts: ContentScopeScripts,
    private val pixel: Pixel,
    private val crashLogger: CrashLogger,
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    private var lastPageStarted: String? = null
    private var start: Long? = null
    private var end: Long? = null

    /**
     * This is the new method of url overriding available from API 24 onwards
     */
    @RequiresApi(Build.VERSION_CODES.N)
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_SHOULD_OVERRIDE_URL_LOADING", traceCookie)
        val url = request.url
        val result = shouldOverride(view, url, request.isForMainFrame)
        Trace.endAsyncSection("LOAD_PAGE_SHOULD_OVERRIDE_URL_LOADING", traceCookie)
        return result
    }

    /**
     * * This is the old, deprecated method of url overriding available until API 23
     */
    @Suppress("OverridingDeprecatedMember")
    override fun shouldOverrideUrlLoading(
        view: WebView,
        urlString: String,
    ): Boolean {
        val url = Uri.parse(urlString)
        return shouldOverride(view, url, isForMainFrame = true)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(
        webView: WebView,
        url: Uri,
        isForMainFrame: Boolean,
    ): Boolean {
        Timber.v("shouldOverride $url")
        try {
            if (isForMainFrame && dosDetector.isUrlGeneratingDos(url)) {
                webView.loadUrl("about:blank")
                webViewClientListener?.dosAttackDetected()
                return false
            }

            return when (val urlType = specialUrlDetector.determineType(initiatingUrl = webView.originalUrl, uri = url)) {
                is SpecialUrlDetector.UrlType.Email -> {
                    webViewClientListener?.sendEmailRequested(urlType.emailAddress)
                    true
                }

                is SpecialUrlDetector.UrlType.Telephone -> {
                    webViewClientListener?.dialTelephoneNumberRequested(urlType.telephoneNumber)
                    true
                }

                is SpecialUrlDetector.UrlType.Sms -> {
                    webViewClientListener?.sendSmsRequested(urlType.telephoneNumber)
                    true
                }

                is SpecialUrlDetector.UrlType.AppLink -> {
                    Timber.i("Found app link for ${urlType.uriString}")
                    webViewClientListener?.let { listener ->
                        return listener.handleAppLink(urlType, isForMainFrame)
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.NonHttpAppLink -> {
                    Timber.i("Found non-http app link for ${urlType.uriString}")
                    if (isForMainFrame) {
                        webViewClientListener?.let { listener ->
                            return listener.handleNonHttpAppLink(urlType)
                        }
                    }
                    true
                }

                is SpecialUrlDetector.UrlType.Unknown -> {
                    Timber.w("Unable to process link type for ${urlType.uriString}")
                    webView.originalUrl?.let {
                        webView.loadUrl(it)
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.SearchQuery -> false
                is SpecialUrlDetector.UrlType.Web -> {
                    if (requestRewriter.shouldRewriteRequest(url)) {
                        val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                        webView.loadUrl(newUri.toString())
                        return true
                    }
                    if (isForMainFrame) {
                        webViewClientListener?.willOverrideUrl(url.toString())
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.ExtractedAmpLink -> {
                    if (isForMainFrame) {
                        webViewClientListener?.let { listener ->
                            listener.startProcessingTrackingLink()
                            Timber.d("AMP link detection: Loading extracted URL: ${urlType.extractedUrl}")
                            loadUrl(listener, webView, urlType.extractedUrl)
                            return true
                        }
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.CloakedAmpLink -> {
                    val lastAmpLinkInfo = ampLinks.lastAmpLinkInfo
                    if (isForMainFrame && (lastAmpLinkInfo == null || lastPageStarted != lastAmpLinkInfo.destinationUrl)) {
                        webViewClientListener?.let { listener ->
                            listener.handleCloakedAmpLink(urlType.ampUrl)
                            return true
                        }
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.TrackingParameterLink -> {
                    if (isForMainFrame) {
                        webViewClientListener?.let { listener ->
                            listener.startProcessingTrackingLink()
                            Timber.d("Loading parameter cleaned URL: ${urlType.cleanedUrl}")

                            return when (
                                val parameterStrippedType =
                                    specialUrlDetector.processUrl(initiatingUrl = webView.originalUrl, uriString = urlType.cleanedUrl)
                            ) {
                                is SpecialUrlDetector.UrlType.AppLink -> {
                                    loadUrl(listener, webView, urlType.cleanedUrl)
                                    listener.handleAppLink(parameterStrippedType, isForMainFrame)
                                }

                                is SpecialUrlDetector.UrlType.ExtractedAmpLink -> {
                                    Timber.d("AMP link detection: Loading extracted URL: ${parameterStrippedType.extractedUrl}")
                                    loadUrl(listener, webView, parameterStrippedType.extractedUrl)
                                    true
                                }

                                else -> {
                                    loadUrl(listener, webView, urlType.cleanedUrl)
                                    true
                                }
                            }
                        }
                    }
                    false
                }
            }
        } catch (e: Throwable) {
            crashLogger.logCrash(CrashLogger.Crash(shortName = "m_webview_should_override", t = e))
            return false
        }
    }

    private fun loadUrl(
        listener: WebViewClientListener,
        webView: WebView,
        url: String,
    ) {
        if (listener.linkOpenedInNewTab()) {
            webView.post {
                webView.loadUrl(url)
            }
        } else {
            webView.loadUrl(url)
        }
    }

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {

        // [CRIS] TEMP: Throw exception on high latency to avoid polluting results
        appCoroutineScope.launch {
            getPing().let { if (it >= LATENCY_THRESHOLD) throw Exception("Bad network: $it ms") }
        }

        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        beginTrace(url!!, "LOAD_PAGE_START_TO_FINISH", 0)
        beginTrace(url,"LOAD_PAGE_ON_PAGE_STARTED", traceCookie)

        Timber.v("onPageStarted webViewUrl: ${webView.url} URL: $url")

        url?.let {
            autoconsent.injectAutoconsent(webView, url)
            adClickManager.detectAdDomain(url)
            requestInterceptor.onPageStarted(url)
            appCoroutineScope.launch(dispatcherProvider.default()) {
                thirdPartyCookieManager.processUriForThirdPartyCookies(webView, url.toUri())
            }
        }
        val navigationList = webView.safeCopyBackForwardList() ?: return
        webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
        if (url != null && url == lastPageStarted) {
            webViewClientListener?.pageRefreshed(url)
        }
        lastPageStarted = url
        browserAutofillConfigurator.configureAutofillForCurrentPage(webView, url)
        contentScopeScripts.injectContentScopeScripts(webView)
        loginDetector.onEvent(WebNavigationEvent.OnPageStarted(webView))

        endTrace(url, webView, "LOAD_PAGE_ON_PAGE_STARTED", traceCookie)
    }

    @UiThread
    override fun onPageFinished(
        webView: WebView,
        url: String?,
    ) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_ON_PAGE_FINISHED", traceCookie)

        accessibilityManager.onPageFinished(webView, url)
        url?.let {
            // We call this for any url but it will only be processed for an internal tester verification url
            internalTestUserChecker.verifyVerificationCompleted(it)
        }
        Timber.v("onPageFinished webViewUrl: ${webView.url} URL: $url")
        val navigationList = webView.safeCopyBackForwardList() ?: return
        webViewClientListener?.run {
            navigationStateChanged(WebViewNavigationState(navigationList))
            url?.let { prefetchFavicon(url) }
        }
        flushCookies()
        printInjector.injectPrint(webView)

        // [CRIS] TEMP: Throw exception on high latency to avoid polluting results
        appCoroutineScope.launch {
            getPing().let { if (it >= LATENCY_THRESHOLD) throw Exception("Bad network: $it ms") }
        }

        endTrace(url!!, webView, "LOAD_PAGE_START_TO_FINISH", 0)
        Trace.endAsyncSection("LOAD_PAGE_ON_PAGE_FINISHED", traceCookie)
    }

    private fun flushCookies() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            cookieManagerProvider.get().flush()
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(
        webView: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_SHOULD_INTERCEPT_REQUEST", traceCookie)

        val result = runBlocking {
            val documentUrl = withContext(dispatcherProvider.main()) { webView.url }
            withContext(dispatcherProvider.main()) {
                loginDetector.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, request))
            }
            Timber.v("Intercepting resource ${request.url} type:${request.method} on page $documentUrl")
            requestInterceptor.shouldIntercept(request, webView, documentUrl, webViewClientListener)
        }
        // [CRIS] TEMP: This will log only one execution of the tens or hundreds that happen within a page load
        // See https://app.asana.com/0/0/1206159443951489/f (Macrobenchmark can't handle webview methods well)

        Trace.endAsyncSection("LOAD_PAGE_SHOULD_INTERCEPT_REQUEST", traceCookie)
        return result
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_ON_RENDER_PROCESS_GONE", traceCookie)
        Timber.w("onRenderProcessGone. Did it crash? ${detail?.didCrash()}")
        if (detail?.didCrash() == true) {
            pixel.fire(WEB_RENDERER_GONE_CRASH)
        } else {
            pixel.fire(WEB_RENDERER_GONE_KILLED)
        }
        webViewClientListener?.recoverFromRenderProcessGone()
        Trace.endAsyncSection("LOAD_PAGE_ON_RENDER_PROCESS_GONE", traceCookie)
        return true
    }

    @UiThread
    override fun onReceivedHttpAuthRequest(
        view: WebView?,
        handler: HttpAuthHandler?,
        host: String?,
        realm: String?,
    ) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_ON_RECEIVED_HTTP_AUTH_REQUEST", traceCookie)
        Timber.v("onReceivedHttpAuthRequest ${view?.url} $realm, $host")
        if (handler != null) {
            Timber.v("onReceivedHttpAuthRequest - useHttpAuthUsernamePassword [${handler.useHttpAuthUsernamePassword()}]")
            if (handler.useHttpAuthUsernamePassword()) {
                val credentials = view?.let {
                    webViewHttpAuthStore.getHttpAuthUsernamePassword(it, host.orEmpty(), realm.orEmpty())
                }

                if (credentials != null) {
                    handler.proceed(credentials.username, credentials.password)
                } else {
                    requestAuthentication(view, handler, host, realm)
                }
            } else {
                requestAuthentication(view, handler, host, realm)
            }
        } else {
            super.onReceivedHttpAuthRequest(view, handler, host, realm)
        }
        Trace.endAsyncSection("LOAD_PAGE_ON_RECEIVED_HTTP_AUTH_REQUEST", traceCookie)
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError,
    ) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_ON_RECEIVED_SSL_ERROR", traceCookie)
        var trusted: CertificateValidationState = CertificateValidationState.UntrustedChain
        when (error.primaryError) {
            SSL_UNTRUSTED -> {
                Timber.d("The certificate authority ${error.certificate.issuedBy.dName} is not trusted")
                trusted = trustedCertificateStore.validateSslCertificateChain(error.certificate)
            }

            else -> Timber.d("SSL error ${error.primaryError}")
        }

        Timber.d("The certificate authority validation result is $trusted")
        if (trusted is CertificateValidationState.TrustedChain) handler.proceed() else super.onReceivedSslError(view, handler, error)
        Trace.endAsyncSection("LOAD_PAGE_ON_RECEIVED_SSL_ERROR", traceCookie)
    }

    private fun requestAuthentication(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?,
    ) {
        webViewClientListener?.let {
            Timber.v("showAuthenticationDialog - $host, $realm")

            val siteURL = if (view?.url != null) "${URI(view.url).scheme}://$host" else host.orEmpty()

            val request = BasicAuthenticationRequest(
                handler = handler,
                host = host.orEmpty(),
                realm = realm.orEmpty(),
                site = siteURL,
            )

            it.requiresAuthentication(request)
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        error?.let {
            val parsedError = parseErrorResponse(it)
            if (parsedError != OMITTED && request?.isForMainFrame == true) {
                webViewClientListener?.onReceivedError(parsedError, request.url.toString())
            }
            if (request?.isForMainFrame == true) {
                Timber.d("recordErrorCode for ${request.url}")
                webViewClientListener?.recordErrorCode(
                    "${it.errorCode.asStringErrorCode()} - ${it.description}",
                    request.url.toString(),
                )
            }
        }
        super.onReceivedError(view, request, error)
    }

    private fun parseErrorResponse(error: WebResourceError): WebViewErrorResponse {
        return if (error.errorCode == ERROR_HOST_LOOKUP) {
            when (error.description) {
                "net::ERR_NAME_NOT_RESOLVED" -> BAD_URL
                "net::ERR_INTERNET_DISCONNECTED" -> CONNECTION
                else -> OMITTED
            }
        } else if (error.errorCode == ERROR_FAILED_SSL_HANDSHAKE && error.description == "net::ERR_SSL_PROTOCOL_ERROR") {
            WebViewErrorResponse.SSL_PROTOCOL_ERROR
        } else {
            OMITTED
        }
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?,
    ) {
        val traceCookie = Random(System.currentTimeMillis()).nextInt()
        Trace.beginAsyncSection("LOAD_PAGE_ON_RECEIVED_HTTP_ERROR", traceCookie)
        super.onReceivedHttpError(view, request, errorResponse)
        view?.url?.let {
            // We call this for any url but it will only be processed for an internal tester verification url
            internalTestUserChecker.verifyVerificationErrorReceived(it)
        }
        if (request?.isForMainFrame == true) {
            errorResponse?.let {
                Timber.d("recordHttpErrorCode for ${request.url}")
                webViewClientListener?.recordHttpErrorCode(it.statusCode, request.url.toString())
            }
        }
        Trace.endAsyncSection("LOAD_PAGE_ON_RECEIVED_HTTP_ERROR", traceCookie)
    }

    private fun Int.asStringErrorCode(): String {
        return when (this) {
            ERROR_AUTHENTICATION -> "ERROR_AUTHENTICATION"
            ERROR_BAD_URL -> "ERROR_BAD_URL"
            ERROR_CONNECT -> "ERROR_CONNECT"
            ERROR_FAILED_SSL_HANDSHAKE -> "ERROR_FAILED_SSL_HANDSHAKE"
            ERROR_FILE -> "ERROR_FILE"
            ERROR_FILE_NOT_FOUND -> "ERROR_FILE_NOT_FOUND"
            ERROR_HOST_LOOKUP -> "ERROR_HOST_LOOKUP"
            ERROR_IO -> "ERROR_IO"
            ERROR_PROXY_AUTHENTICATION -> "ERROR_PROXY_AUTHENTICATION"
            ERROR_REDIRECT_LOOP -> "ERROR_REDIRECT_LOOP"
            ERROR_TIMEOUT -> "ERROR_TIMEOUT"
            ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
            ERROR_UNKNOWN -> "ERROR_UNKNOWN"
            ERROR_UNSAFE_RESOURCE -> "ERROR_UNSAFE_RESOURCE"
            ERROR_UNSUPPORTED_AUTH_SCHEME -> "ERROR_UNSUPPORTED_AUTH_SCHEME"
            ERROR_UNSUPPORTED_SCHEME -> "ERROR_UNSUPPORTED_SCHEME"
            SAFE_BROWSING_THREAT_BILLING -> "SAFE_BROWSING_THREAT_BILLING"
            SAFE_BROWSING_THREAT_MALWARE -> "SAFE_BROWSING_THREAT_MALWARE"
            SAFE_BROWSING_THREAT_PHISHING -> "SAFE_BROWSING_THREAT_PHISHING"
            SAFE_BROWSING_THREAT_UNKNOWN -> "SAFE_BROWSING_THREAT_UNKNOWN"
            SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE -> "SAFE_BROWSING_THREAT_UNWANTED_SOFTWARE"
            else -> "ERROR_OTHER"
        }
    }

    private suspend fun getPing(): Long {
        val inetAddress = InetAddress.getByName("1.1.1.1")
        val startTime = SystemClock.elapsedRealtime()
        if (inetAddress.isReachable(5000)) { // Timeout set to 5 seconds
            val endTime = SystemClock.elapsedRealtime()
            return endTime - startTime
        } else throw Exception("Network unreachable")
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun beginTrace(url: String, trace: String, cookie: Int) {
        // [CRIS] TEMP: Ignore about:blank and store start time for LOAD_PAGE_ON_PAGE_STARTED
        // See https://app.asana.com/0/0/1206159443951489/f (Macrobenchmark limitations and Webview limitations)
        if (url != "about:blank") {
            if (trace == "LOAD_PAGE_ON_PAGE_STARTED") {
                start = SystemClock.elapsedRealtime()
                Log.v("BrowserWebViewClient", "LOAD_PAGE_ON_PAGE_STARTED ${URL(url).host}")
            }
            android.os.Trace.beginAsyncSection(trace, cookie)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun endTrace(url: String, webView: WebView, trace: String, cookie: Int) {
        if (url != "about:blank") {
            if (trace == "LOAD_PAGE_START_TO_FINISH") {
                if (start != null && webView.progress == 100) {
                    end = SystemClock.elapsedRealtime()
                    // [CRIS] TEMP: Only log finish when
                    //    1. Not about:blank
                    //    2. Already started
                    //    3. Webview progress == 100
                    // See https://app.asana.com/0/0/1206159443951489/f (Macrobenchmark limitations and Webview limitations)
                    Log.v("BrowserWebViewClient", "LOAD_PAGE_START_TO_FINISH ${URL(url).host}: ${end!!-start!!}ms")
                    android.os.Trace.endAsyncSection(trace, cookie)
                    Snackbar.make(webView, "Load finished", Snackbar.LENGTH_LONG).show()
                }
            } else {
                android.os.Trace.endAsyncSection(trace, cookie)
            }
        }
    }
}

enum class WebViewPixelName(override val pixelName: String) : Pixel.PixelName {
    WEB_RENDERER_GONE_CRASH("m_web_view_renderer_gone_crash"),
    WEB_RENDERER_GONE_KILLED("m_web_view_renderer_gone_killed"),
}

enum class WebViewErrorResponse(@StringRes val errorId: Int) {
    BAD_URL(R.string.webViewErrorBadUrl),
    CONNECTION(R.string.webViewErrorNoConnection),
    OMITTED(R.string.webViewErrorNoConnection),
    SSL_PROTOCOL_ERROR(R.string.webViewErrorSslProtocol),
}
