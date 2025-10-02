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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.net.http.SslError.SSL_DATE_INVALID
import android.net.http.SslError.SSL_EXPIRED
import android.net.http.SslError.SSL_IDMISMATCH
import android.net.http.SslError.SSL_UNTRUSTED
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.StringRes
import androidx.annotation.UiThread
import androidx.annotation.WorkerThread
import androidx.core.net.toUri
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.browser.SSLErrorType.EXPIRED
import com.duckduckgo.app.browser.SSLErrorType.GENERIC
import com.duckduckgo.app.browser.SSLErrorType.UNTRUSTED_HOST
import com.duckduckgo.app.browser.SSLErrorType.WRONG_HOST
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
import com.duckduckgo.app.browser.mediaplayback.MediaPlayback
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedHandler
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedHandler
import com.duckduckgo.app.browser.print.PrintInjector
import com.duckduckgo.app.browser.trafficquality.AndroidFeaturesHeaderPlugin
import com.duckduckgo.app.browser.uriloaded.UriLoadedManager
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.autoconsent.api.Autoconsent
import com.duckduckgo.autofill.api.BrowserAutofill
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.common.utils.AppUrl.ParamKey.QUERY
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.contentscopescripts.api.contentscopeExperiments.ContentScopeExperiments
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerOrigin.SERP_AUTO
import com.duckduckgo.duckplayer.api.DuckPlayer.DuckPlayerState.ENABLED
import com.duckduckgo.duckplayer.api.DuckPlayer.OpenDuckPlayerInNewTab.On
import com.duckduckgo.duckplayer.impl.DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.js.messaging.api.AddDocumentStartJavaScriptPlugin
import com.duckduckgo.js.messaging.api.PostMessageWrapperPlugin
import com.duckduckgo.js.messaging.api.SubscriptionEventData
import com.duckduckgo.js.messaging.api.WebMessagingPlugin
import com.duckduckgo.js.messaging.api.WebViewCompatMessageCallback
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.privacy.config.api.AmpLinks
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.user.agent.api.ClientBrandHintProvider
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.*
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

private const val ABOUT_BLANK = "about:blank"

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
    private val ampLinks: AmpLinks,
    private val printInjector: PrintInjector,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val adClickManager: AdClickManager,
    private val autoconsent: Autoconsent,
    private val pixel: Pixel,
    private val crashLogger: CrashLogger,
    private val jsPlugins: PluginPoint<JsInjectorPlugin>,
    private val currentTimeProvider: CurrentTimeProvider,
    private val pageLoadedHandler: PageLoadedHandler,
    private val shouldSendPagePaintedPixel: PagePaintedHandler,
    private val navigationHistory: NavigationHistory,
    private val mediaPlayback: MediaPlayback,
    private val subscriptions: Subscriptions,
    private val duckPlayer: DuckPlayer,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val uriLoadedManager: UriLoadedManager,
    private val androidFeaturesHeaderPlugin: AndroidFeaturesHeaderPlugin,
    private val duckChat: DuckChat,
    private val contentScopeExperiments: ContentScopeExperiments,
    private val addDocumentStartJavascriptPlugins: PluginPoint<AddDocumentStartJavaScriptPlugin>,
    private val webMessagingPlugins: PluginPoint<WebMessagingPlugin>,
    private val postMessageWrapperPlugins: PluginPoint<PostMessageWrapperPlugin>,
) : WebViewClient() {

    var webViewClientListener: WebViewClientListener? = null
    var clientProvider: ClientBrandHintProvider? = null
    private var lastPageStarted: String? = null
    private var start: Long? = null

    private var shouldOpenDuckPlayerInNewTab: Boolean = true

    private var currentLoadOperationId: String? = null
    private var parallelRequestsOnStart = 0

    init {
        appCoroutineScope.launch {
            duckPlayer.observeShouldOpenInNewTab().collect {
                shouldOpenDuckPlayerInNewTab = it is On
            }
        }
    }

    private fun incrementAndTrackLoad() {
        // a new load operation is starting for this WebView instance.
        val loadId = UUID.randomUUID().toString()
        this.currentLoadOperationId = loadId

        parallelRequestsOnStart = parallelRequestCounter.incrementAndGet() - 1

        val job = timeoutScope.launch {
            delay(REQUEST_TIMEOUT_MS)
            // attempt to remove the job - if successful, it means it hasn't been finished/errored/cancelled yet
            if (activeRequestTimeoutJobs.remove(loadId) != null) {
                parallelRequestCounter.decrementAndGet()
            }
        }
        activeRequestTimeoutJobs[loadId] = job
    }

    private fun decrementLoadCountAndGet(): Int {
        this.currentLoadOperationId?.let { loadId ->
            val job = activeRequestTimeoutJobs.remove(loadId)

            // if we successfully removed the job (it means it hadn't timed out yet)
            if (job != null) {
                job.cancel()
                parallelRequestCounter.decrementAndGet()
            }
        }
        this.currentLoadOperationId = null
        return parallelRequestCounter.get()
    }

    /**
     * This is the method of url overriding available from API 24 onwards
     */
    @UiThread
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val url = request.url
        return shouldOverride(view, url, request.isForMainFrame, request.isRedirect)
    }

    /**
     * API-agnostic implementation of deciding whether to override url or not
     */
    private fun shouldOverride(
        webView: WebView,
        url: Uri,
        isForMainFrame: Boolean,
        isRedirect: Boolean,
    ): Boolean {
        try {
            logcat(VERBOSE) { "shouldOverride webViewUrl: ${webView.url} URL: $url" }
            webViewClientListener?.onShouldOverride()
            if (requestInterceptor.shouldOverrideUrlLoading(webViewClientListener, url, webView.url?.toUri(), isForMainFrame)) {
                return true
            }

            if (isForMainFrame && dosDetector.isUrlGeneratingDos(url)) {
                webView.loadUrl(ABOUT_BLANK)
                webViewClientListener?.dosAttackDetected()
                return false
            }

            return when (val urlType = specialUrlDetector.determineType(initiatingUrl = webView.originalUrl, uri = url)) {
                is SpecialUrlDetector.UrlType.ShouldLaunchPrivacyProLink -> {
                    subscriptions.launchPrivacyPro(webView.context, url)
                    true
                }
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
                    logcat(INFO) { "Found app link for ${urlType.uriString}" }
                    webViewClientListener?.let { listener ->
                        return listener.handleAppLink(urlType, isForMainFrame)
                    }
                    false
                }
                is SpecialUrlDetector.UrlType.ShouldLaunchDuckChatLink -> {
                    runCatching {
                        val query = url.getQueryParameter(QUERY)
                        if (query != null) {
                            duckChat.openDuckChatWithPrefill(query)
                        } else {
                            duckChat.openDuckChat()
                        }
                    }.isSuccess
                }
                is SpecialUrlDetector.UrlType.ShouldLaunchDuckPlayerLink -> {
                    if (isRedirect && isForMainFrame) {
                        /*
                        This forces shouldInterceptRequest to be called with the YouTube URL, otherwise that method is never executed and
                        therefore the Duck Player page is never launched if YouTube comes from a redirect.
                         */
                        webViewClientListener?.let {
                            loadUrl(it, webView, url.toString())
                        }
                        return true
                    } else {
                        shouldOverrideWebRequest(
                            url,
                            webView,
                            isForMainFrame,
                            openInNewTab = shouldOpenDuckPlayerInNewTab && isForMainFrame && webView.url != url.toString(),
                            willOpenDuckPlayer = isForMainFrame,
                        )
                    }
                }
                is SpecialUrlDetector.UrlType.NonHttpAppLink -> {
                    logcat(INFO) { "Found non-http app link for ${urlType.uriString}" }
                    if (isForMainFrame) {
                        webViewClientListener?.let { listener ->
                            return listener.handleNonHttpAppLink(urlType)
                        }
                    }
                    true
                }

                is SpecialUrlDetector.UrlType.Unknown -> {
                    logcat(WARN) { "Unable to process link type for ${urlType.uriString}" }
                    webView.originalUrl?.let {
                        webView.loadUrl(it)
                    }
                    false
                }

                is SpecialUrlDetector.UrlType.SearchQuery -> false
                is SpecialUrlDetector.UrlType.Web -> {
                    shouldOverrideWebRequest(url, webView, isForMainFrame)
                }

                is SpecialUrlDetector.UrlType.ExtractedAmpLink -> {
                    if (isForMainFrame) {
                        webViewClientListener?.let { listener ->
                            listener.startProcessingTrackingLink()
                            logcat { "AMP link detection: Loading extracted URL: ${urlType.extractedUrl}" }
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
                            logcat { "Loading parameter cleaned URL: ${urlType.cleanedUrl}" }

                            return when (
                                val parameterStrippedType =
                                    specialUrlDetector.processUrl(initiatingUrl = webView.originalUrl, uriString = urlType.cleanedUrl)
                            ) {
                                is SpecialUrlDetector.UrlType.AppLink -> {
                                    loadUrl(listener, webView, urlType.cleanedUrl)
                                    listener.handleAppLink(parameterStrippedType, isForMainFrame)
                                }

                                is SpecialUrlDetector.UrlType.ExtractedAmpLink -> {
                                    logcat { "AMP link detection: Loading extracted URL: ${parameterStrippedType.extractedUrl}" }
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
                is SpecialUrlDetector.UrlType.DuckScheme -> {
                    webViewClientListener?.let { listener ->
                        if (
                            url.pathSegments?.firstOrNull()?.equals(DUCK_PLAYER_OPEN_IN_YOUTUBE_PATH, ignoreCase = true) == true ||
                            !shouldOpenDuckPlayerInNewTab
                        ) {
                            loadUrl(listener, webView, url.toString())
                        } else {
                            listener.openLinkInNewTab(url)
                        }
                        true
                    } ?: false
                }
            }
        } catch (e: Throwable) {
            appCoroutineScope.launch(dispatcherProvider.io()) {
                crashLogger.logCrash(CrashLogger.Crash(shortName = "m_webview_should_override", t = e))
            }
            return false
        }
    }

    private fun shouldOverrideWebRequest(
        url: Uri,
        webView: WebView,
        isForMainFrame: Boolean,
        openInNewTab: Boolean = false,
        willOpenDuckPlayer: Boolean = false,
    ): Boolean {
        if (requestRewriter.shouldRewriteRequest(url)) {
            webViewClientListener?.let { listener ->
                val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                loadUrl(listener, webView, newUri.toString())
                return true
            }
        }
        if (isForMainFrame) {
            webViewClientListener?.let { listener ->
                listener.willOverrideUrl(url.toString())
                clientProvider?.let { provider ->
                    if (provider.shouldChangeBranding(url.toString())) {
                        provider.setOn(webView.settings, url.toString())
                        if (openInNewTab) {
                            listener.openLinkInNewTab(url)
                        } else {
                            loadUrl(listener, webView, url.toString())
                        }
                        return true
                    } else if (willOpenDuckPlayer && webView.url?.let { duckDuckGoUrlDetector.isDuckDuckGoUrl(it) } == true) {
                        duckPlayer.setDuckPlayerOrigin(SERP_AUTO)
                        if (openInNewTab) {
                            listener.openLinkInNewTab(url)
                            return true
                        } else {
                            return false
                        }
                    } else if (openInNewTab) {
                        listener.openLinkInNewTab(url)
                        return true
                    } else {
                        val headers = androidFeaturesHeaderPlugin.getHeaders(url.toString())
                        if (headers.isNotEmpty()) {
                            loadUrl(webView, url.toString(), headers)
                            return true
                        }
                        return false
                    }
                }
                return false
            }
        } else if (openInNewTab) {
            webViewClientListener?.openLinkInNewTab(url)
            return true
        }
        return false
    }

    @UiThread
    override fun onPageCommitVisible(webView: WebView, url: String) {
        logcat(VERBOSE) { "onPageCommitVisible webViewUrl: ${webView.url} URL: $url progress: ${webView.progress}" }
        // Show only when the commit matches the tab state
        if (webView.url == url) {
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.onPageCommitVisible(WebViewNavigationState(navigationList), url)
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

    private fun loadUrl(
        webView: WebView,
        url: String,
        headers: Map<String, String>,
    ) {
        webView.loadUrl(url, headers)
    }

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        url?.let {
            // See https://app.asana.com/0/0/1206159443951489/f (WebView limitations)
            if (it != ABOUT_BLANK && start == null) {
                start = currentTimeProvider.elapsedRealtime()
                incrementAndTrackLoad() // increment the request counter
                requestInterceptor.onPageStarted(url)
            }
            handleMediaPlayback(webView, it)
            autoconsent.injectAutoconsent(webView, url)
            adClickManager.detectAdDomain(url)
            appCoroutineScope.launch(dispatcherProvider.io()) {
                thirdPartyCookieManager.processUriForThirdPartyCookies(webView, url.toUri())
            }
        }
        val navigationList = webView.safeCopyBackForwardList() ?: return

        appCoroutineScope.launch(dispatcherProvider.main()) {
            val activeExperiments = contentScopeExperiments.getActiveExperiments()
            webViewClientListener?.pageStarted(WebViewNavigationState(navigationList), activeExperiments)
            jsPlugins.getPlugins().forEach {
                it.onPageStarted(webView, url, webViewClientListener?.getSite()?.isDesktopMode, activeExperiments)
            }
        }
        if (url != null && url == lastPageStarted) {
            webViewClientListener?.pageRefreshed(url)
        }
        lastPageStarted = url
        browserAutofillConfigurator.configureAutofillForCurrentPage(webView, url)
        loginDetector.onEvent(WebNavigationEvent.OnPageStarted(webView))
    }

    private fun handleMediaPlayback(
        webView: WebView,
        url: String,
    ) {
        // The default value for this flag is `true`.
        webView.settings.mediaPlaybackRequiresUserGesture = mediaPlayback.doesMediaPlaybackRequireUserGestureForUrl(url)
    }

    fun configureWebView(webView: DuckDuckGoWebView, callback: WebViewCompatMessageCallback?) {
        addDocumentStartJavascriptPlugins.getPlugins().forEach { plugin ->
            plugin.addDocumentStartJavaScript(webView)
        }

        callback?.let {
            webMessagingPlugins.getPlugins().forEach { plugin ->
                plugin.register(callback, webView)
            }
        }
    }

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        logcat(VERBOSE) { "onPageFinished webViewUrl: ${webView.url} URL: $url progress: ${webView.progress}" }

        // See https://app.asana.com/0/0/1206159443951489/f (WebView limitations)
        if (webView.progress == 100) {
            jsPlugins.getPlugins().forEach {
                it.onPageFinished(
                    webView,
                    url,
                    webViewClientListener?.getSite(),
                )
            }
            addDocumentStartJavascriptPlugins.getPlugins().forEach {
                it.addDocumentStartJavaScript(
                    webView,
                )
            }

            url?.let {
                // We call this for any url but it will only be processed for an internal tester verification url
                internalTestUserChecker.verifyVerificationCompleted(it)
            }

            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.run {
                pageFinished(WebViewNavigationState(navigationList), url)
            }
            flushCookies()
            printInjector.injectPrint(webView)

            url?.let {
                val uri = url.toUri()
                if (url != ABOUT_BLANK) {
                    start?.let { safeStart ->
                        // TODO (cbarreiro - 22/05/2024): Extract to plugins
                        pageLoadedHandler.onPageLoaded(
                            url = it,
                            title = navigationList.currentItem?.title,
                            start = safeStart,
                            end = currentTimeProvider.elapsedRealtime(),
                            isTabInForegroundOnFinish = webViewClientListener?.isTabInForeground() ?: true,
                            activeRequestsOnLoadStart = parallelRequestsOnStart,
                            concurrentRequestsOnFinish = decrementLoadCountAndGet(),
                        )
                        shouldSendPagePaintedPixel(webView = webView, url = it)
                        appCoroutineScope.launch(dispatcherProvider.io()) {
                            if (duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isSimulatedYoutubeNoCookie(uri)) {
                                duckPlayer.createDuckPlayerUriFromYoutubeNoCookie(url.toUri())?.let {
                                    navigationHistory.saveToHistory(
                                        it,
                                        navigationList.currentItem?.title,
                                    )
                                }
                            } else {
                                if (duckPlayer.getDuckPlayerState() == ENABLED && duckPlayer.isYoutubeWatchUrl(uri)) {
                                    duckPlayer.duckPlayerNavigatedToYoutube()
                                }
                                navigationHistory.saveToHistory(url, navigationList.currentItem?.title)
                            }
                        }
                        uriLoadedManager.sendUriLoadedPixel()

                        start = null
                    }
                }
            }
        }
    }

    private fun flushCookies() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            cookieManagerProvider.get()?.flush()
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(
        webView: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        return runBlocking {
            val documentUrl = withContext(dispatcherProvider.main()) { webView.url }
            withContext(dispatcherProvider.main()) {
                loginDetector.onEvent(WebNavigationEvent.ShouldInterceptRequest(webView, request))
            }
            logcat(VERBOSE) { "Intercepting resource ${request.url} type:${request.method} on page $documentUrl" }
            requestInterceptor.shouldIntercept(
                request,
                webView,
                documentUrl?.toUri(),
                webViewClientListener,
            )
        }
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        logcat(WARN) { "onRenderProcessGone. Did it crash? ${detail?.didCrash()}" }
        if (detail?.didCrash() == true) {
            pixel.fire(WEB_RENDERER_GONE_CRASH)
        } else {
            pixel.fire(WEB_RENDERER_GONE_KILLED)
        }

        if (this.start != null) {
            decrementLoadCountAndGet()
            this.start = null
        }

        webViewClientListener?.recoverFromRenderProcessGone()
        return true
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
    }

    override fun onReceivedSslError(
        view: WebView?,
        handler: SslErrorHandler,
        error: SslError,
    ) {
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
            webViewClientListener?.onReceivedSslError(handler, parseSSlErrorResponse(error))
        }
    }

    private fun parseSSlErrorResponse(sslError: SslError): SslErrorResponse {
        logcat { "SSL Certificate: parseSSlErrorResponse ${sslError.primaryError}" }
        val sslErrorType = when (sslError.primaryError) {
            SSL_UNTRUSTED -> UNTRUSTED_HOST
            SSL_EXPIRED -> EXPIRED
            SSL_DATE_INVALID -> EXPIRED
            SSL_IDMISMATCH -> WRONG_HOST
            else -> GENERIC
        }
        return SslErrorResponse(sslError, sslErrorType, sslError.url)
    }

    private fun requestAuthentication(
        view: WebView?,
        handler: HttpAuthHandler,
        host: String?,
        realm: String?,
    ) {
        webViewClientListener?.let {
            logcat(VERBOSE) { "showAuthenticationDialog - $host, $realm" }

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
        error?.let { webResourceError ->
            val parsedError = parseErrorResponse(webResourceError)
            if (request?.isForMainFrame == true) {
                if (parsedError != OMITTED) {
                    if (this.start != null) {
                        decrementLoadCountAndGet()
                        this.start = null
                    }
                    webViewClientListener?.onReceivedError(parsedError, request.url.toString())
                }
                logcat { "recordErrorCode for ${request.url}" }
                webViewClientListener?.recordErrorCode(
                    "${webResourceError.errorCode.asStringErrorCode()} - ${webResourceError.description}",
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
        super.onReceivedHttpError(view, request, errorResponse)
        view?.url?.let {
            // We call this for any url but it will only be processed for an internal tester verification url
            internalTestUserChecker.verifyVerificationErrorReceived(it)
        }
        if (request?.isForMainFrame == true) {
            errorResponse?.let {
                logcat { "recordHttpErrorCode for ${request.url}" }
                webViewClientListener?.recordHttpErrorCode(it.statusCode, request.url.toString())
            }
        }
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

    fun addExemptedMaliciousSite(url: Uri, feed: Feed) {
        requestInterceptor.addExemptedMaliciousSite(url, feed)
    }

    fun destroy(webView: DuckDuckGoWebView) {
        webMessagingPlugins.getPlugins().forEach { plugin ->
            plugin.unregister(webView)
        }
    }

    fun postContentScopeMessage(
        eventData: SubscriptionEventData,
        webView: WebView,
    ) {
        postMessageWrapperPlugins.getPlugins()
            .firstOrNull { it.context == "contentScopeScripts" }
            ?.postMessage(eventData, webView)
    }

    companion object {
        val parallelRequestCounter = AtomicInteger(0)
        private val activeRequestTimeoutJobs = ConcurrentHashMap<String, Job>()
        private const val REQUEST_TIMEOUT_MS = 30000L // 30 seconds

        // dedicated scope for request count timeout jobs (static, to be shared across all instances)
        @SuppressLint("NoHardcodedCoroutineDispatcher")
        private val timeoutScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

enum class WebViewPixelName(override val pixelName: String) : Pixel.PixelName {
    WEB_RENDERER_GONE_CRASH("m_web_view_renderer_gone_crash"),
    WEB_RENDERER_GONE_KILLED("m_web_view_renderer_gone_killed"),
    WEB_PAGE_LOADED("m_web_view_page_loaded"),
    WEB_PAGE_PAINTED("m_web_view_page_painted"),
}

enum class WebViewErrorResponse(@StringRes val errorId: Int) {
    BAD_URL(R.string.webViewErrorBadUrl),
    CONNECTION(R.string.webViewErrorNoConnection),
    OMITTED(R.string.webViewErrorNoConnection),
    LOADING(R.string.webViewErrorNoConnection),
    SSL_PROTOCOL_ERROR(R.string.webViewErrorSslProtocol),
}

data class SslErrorResponse(val error: SslError, val errorType: SSLErrorType, val url: String)
enum class SSLErrorType(@StringRes val errorId: Int) {
    EXPIRED(R.string.sslErrorExpiredMessage),
    WRONG_HOST(R.string.sslErrorWrongHostMessage),
    UNTRUSTED_HOST(R.string.sslErrorUntrustedMessage),
    GENERIC(R.string.sslErrorUntrustedMessage),
    NONE(R.string.sslErrorUntrustedMessage),
}
