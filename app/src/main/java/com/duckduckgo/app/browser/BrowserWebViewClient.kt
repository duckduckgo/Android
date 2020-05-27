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
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource.*
import com.duckduckgo.app.statistics.store.OfflinePixelCountDataStore
import kotlinx.coroutines.*
import timber.log.Timber
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class BrowserWebViewClient(
    private val requestRewriter: RequestRewriter,
    private val specialUrlDetector: SpecialUrlDetector,
    private val requestInterceptor: RequestInterceptor,
    private val offlinePixelCountDataStore: OfflinePixelCountDataStore,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository,
    private val cookieManager: CookieManager
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
        try {
            Timber.v("shouldOverride $url")

            return when (val urlType = specialUrlDetector.determineType(url)) {
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
                is SpecialUrlDetector.UrlType.IntentType -> {
                    Timber.i("Found intent type link for $urlType.url")
                    launchExternalApp(urlType)
                    true
                }
                is SpecialUrlDetector.UrlType.Unknown -> {
                    Timber.w("Unable to process link type for ${urlType.url}")
                    webView.loadUrl(webView.originalUrl)
                    false
                }
                is SpecialUrlDetector.UrlType.SearchQuery -> false
                is SpecialUrlDetector.UrlType.Web -> {
                    if (requestRewriter.shouldRewriteRequest(url)) {
                        val newUri = requestRewriter.rewriteRequestWithCustomQueryParams(url)
                        webView.loadUrl(newUri.toString())
                        return true
                    }
                    false
                }
            }
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOULD_OVERRIDE_REQUEST)
                throw e
            }
            return false
        }
    }

    private fun launchExternalApp(urlType: SpecialUrlDetector.UrlType.IntentType) {
        webViewClientListener?.externalAppLinkClicked(urlType)
    }

    @UiThread
    override fun onPageStarted(webView: WebView, url: String?, favicon: Bitmap?) {
        try {
            Timber.v("onPageStarted webViewUrl: ${webView.url} URL: $url")
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            if (url != null && url == lastPageStarted) {
                webViewClientListener?.pageRefreshed(url)
            }
            lastPageStarted = url
            loginDetector.injectJSWithoutXHR(webView)
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_STARTED)
                throw e
            }
        }
    }

    private val loginDetector = LoginDetector()

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        try {
            Timber.v("onPageFinished webViewUrl: ${webView.url} URL: $url")
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            flushCookies()
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PAGE_FINISHED)
                throw e
            }
        }
    }

    private fun flushCookies() {
        GlobalScope.launch(Dispatchers.IO) {
            cookieManager.flush()
        }
    }

    @WorkerThread
    override fun shouldInterceptRequest(webView: WebView, request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            try {
                val documentUrl = withContext(Dispatchers.Main) { webView.url }
                if (loginDetector.interceptPost(request)) {
                    //webViewClientListener?.loginDetected()
                    withContext(Dispatchers.Main) { loginDetector.injectOnlyFormsJS(webView) }
                }
                Timber.v("Intercepting resource ${request.url} type:${request.method} on page $documentUrl")
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

        webViewClientListener?.recoverFromRenderProcessGone()
        return true
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
}

class LoginDetector {

    fun interceptPost(request: WebResourceRequest): Boolean {
        if (request.method == "POST") {
            Timber.i("LoginDetectionInterface evaluate ${request.url}")
            if (request.url?.path?.contains(Regex("login|sign-in|signin|sessions")) == true) {
                Timber.v("LoginDetectionInterface post login DETECTED")
                return true
            }
        }
        return false
    }

    suspend fun injectOnlyFormsJS(webView: WebView): Boolean {
        val javascript = "(function() {\n" +
                "\tLoginDetection.showToast(\"installing loginDetection.js - IN\");\n" +
                "\n" +
                "\tfunction loginFormDetected() {\n" +
                "\t\ttry {\n" +
                "\t\t\tLoginDetection.loginDetected(\"login detected\");\n" +
                "\t\t} catch (error) {}\n" +
                "\t}\n" +
                "\n" +
                "\tfunction inputVisible(input) {\n" +
                "\t\treturn !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.ariaHidden && !input.hidden && input.value != \"\";\n" +
                "\t}\n" +
                "\n" +
                "\tfunction checkIsLoginForm(form) {\n" +
                "\t\tLoginDetection.showToast(\"checking form \" + form);\n" +
                "\n" +
                "\t\tvar inputs = form.getElementsByTagName(\"input\");\n" +
                "\t\tif (!inputs) {\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < inputs.length; i++) {\n" +
                "\t\t\tvar input = inputs.item(i);\n" +
                "\t\t\tif (input.type == \"password\" && inputVisible(input)) {\n" +
                "\t\t\t\tLoginDetection.showToast(\"found password in form \" + form);\n" +
                "\t\t\t\tloginFormDetected();\n" +
                "\t\t\t\treturn true;\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tLoginDetection.showToast(\"no password field in form \" + form);\n" +
                "\t\treturn false;\n" +
                "\t}\n" +
                "\n" +
                "\tfunction submitHandler(event) {\n" +
                "\t\tcheckIsLoginForm(event.target);\n" +
                "\t}\n" +
                "\n" +
                "\tfunction scanForForms() {\n" +
                "\t\tLoginDetection.showToast(\"Scanning for forms\");\n" +
                "\n" +
                "\t\tvar forms = document.forms;\n" +
                "\t\tif (!forms || forms.length == 0) {\n" +
                "\t\t\tLoginDetection.showToast(\"No forms found\");\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < forms.length; i++) {\n" +
                "\t\t\tvar form = forms[i];\n" +
                "\t\t\tvar found = checkIsLoginForm(form);\n" +
                "\t\t\tif (found) {\n" +
                "\t\t\t    return found;\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t}\n" +
                "\n" +
                "return scanForForms();\n" +
                "})();"

        return suspendCoroutine { continuation ->
            webView.evaluateJavascript("javascript:$javascript") { result ->
                Timber.v("LoginDetectionInterface Result: $result")
                continuation.resume(result?.toBoolean() ?: false)
            }
        }
    }

    fun injectJS(webView: WebView) {
        val javascript = "(function() {\n" +
                "\tLoginDetection.showToast(\"installing loginDetection.js - IN\");\n" +
                "\n" +
                "\tfunction loginFormDetected() {\n" +
                "\t\ttry {\n" +
                "\t\t\tLoginDetection.loginDetected(\"login detected\");\n" +
                "\t\t} catch (error) {}\n" +
                "\t}\n" +
                "\n" +
                "\tfunction inputVisible(input) {\n" +
                "\t\treturn !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.ariaHidden && !input.hidden;\n" +
                "\t}\n" +
                "\n" +
                "\tfunction checkIsLoginForm(form) {\n" +
                "\t\tLoginDetection.showToast(\"checking form \" + form);\n" +
                "\n" +
                "\t\tvar inputs = form.getElementsByTagName(\"input\");\n" +
                "\t\tif (!inputs) {\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < inputs.length; i++) {\n" +
                "\t\t\tvar input = inputs.item(i);\n" +
                "\t\t\tif (input.type == \"password\" && inputVisible(input)) {\n" +
                "\t\t\t\tLoginDetection.showToast(\"found password in form \" + form);\n" +
                "\t\t\t\tloginFormDetected();\n" +
                "\t\t\t\treturn true;\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tLoginDetection.showToast(\"no password field in form \" + form);\n" +
                "\t\treturn false;\n" +
                "\t}\n" +
                "\n" +
                "\tfunction submitHandler(event) {\n" +
                "\t\tcheckIsLoginForm(event.target);\n" +
                "\t}\n" +
                "\n" +
                "\tfunction scanForForms() {\n" +
                "\t\tLoginDetection.showToast(\"Scanning for forms\");\n" +
                "\n" +
                "\t\tvar forms = document.forms;\n" +
                "\t\tif (!forms || forms.length == 0) {\n" +
                "\t\t\tLoginDetection.showToast(\"No forms found\");\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < forms.length; i++) {\n" +
                "\t\t\tvar form = forms[i];\n" +
                "\t\t\tform.removeEventListener(\"submit\", submitHandler);\n" +
                "\t\t\tform.addEventListener(\"submit\", submitHandler);\n" +
                "\t\t\tLoginDetection.showToast(\"adding form handler \" + i);\n" +
                "\t\t}\n" +
                "\n" +
                "\t}\n" +
                "\n" +
                "\twindow.addEventListener(\"DOMContentLoaded\", function(event) {\n" +
                "\t\tLoginDetection.showToast(\"Adding to DOM\");\n" +
                "\t\tsetTimeout(scanForForms, 1000);\n" +
                "\t});\n" +
                "\n" +
                "\twindow.addEventListener(\"click\", scanForForms);\n" +
                "\twindow.addEventListener(\"beforeunload\", scanForForms);\n" +
                "\n" +
                "\twindow.addEventListener(\"submit\", submitHandler);\n" +
                "\n" +
                "\ttry {\n" +
                "\t\tconst observer = new PerformanceObserver((list, observer) => {\n" +
                "\t\t\tLoginDetection.showToast(\"XHR: Observer callback - IN\");\n" +
                "\t\t\tconst entries = list.getEntries().filter((entry) => {\n" +
                "\t\t\t\tLoginDetection.showToast(\"XHR: analising\" + entry.name);\n" +
                "\t\t\t\tvar found = entry.initiatorType == \"xmlhttprequest\" && entry.name.split(\"?\")[0].match(/login|sign-in|signin|sessions/);\n" +
                "\t\t\t\tif (found) {\n" +
                "\t\t\t\t\tLoginDetection.showToast(\"XHR: observed login - \" + entry.name.split(\"?\")[0]);\n" +
                "\t\t\t\t}\n" +
                "\t\t\t\treturn found;\n" +
                "\t\t\t});\n" +
                "\n" +
                "\t\t\tif (entries.length == 0) {\n" +
                "\t\t\t\treturn;\n" +
                "\t\t\t}\n" +
                "\n" +
                "\t\t\tLoginDetection.showToast(\"XHR: checking forms - IN\");\n" +
                "\t\t\tvar forms = document.forms;\n" +
                "\t\t\tif (!forms || forms.length == 0) {\n" +
                "\t\t\t\tLoginDetection.showToast(\"XHR: No forms found\");\n" +
                "\t\t\t\treturn;\n" +
                "\t\t\t}\n" +
                "\n" +
                "\t\t\tfor (var i = 0; i < forms.length; i++) {\n" +
                "\t\t\t\tif (checkIsLoginForm(forms[i])) {\n" +
                "\t\t\t\t\tLoginDetection.showToast(\"XHR: found login form\");\n" +
                "\t\t\t\t\tbreak;\n" +
                "\t\t\t\t}\n" +
                "\t\t\t}\n" +
                "\t\t\tLoginDetection.showToast(\"XHR: checking forms - OUT\");\n" +
                "\n" +
                "\t\t});\n" +
                "\t\tobserver.observe({\n" +
                "\t\t\tentryTypes: [\"resource\"]\n" +
                "\t\t});\n" +
                "\t} catch (error) {}\n" +
                "\n" +
                "\tLoginDetection.showToast(\"installing loginDetection.js - OUT\");\n" +
                "})();"
        webView.evaluateJavascript("javascript:$javascript", null)
    }

    fun injectJSWithoutXHR(webView: WebView) {
        val javascript = "(function() {\n" +
                "\tLoginDetection.showToast(\"installing loginDetection.js - IN\");\n" +
                "\n" +
                "\tfunction loginFormDetected() {\n" +
                "\t\ttry {\n" +
                "\t\t\tLoginDetection.loginDetected(\"login detected\");\n" +
                "\t\t} catch (error) {}\n" +
                "\t}\n" +
                "\n" +
                "\tfunction inputVisible(input) {\n" +
                "\t\treturn !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.ariaHidden && !input.hidden && input.value != \"\";\n" +
                "\t}\n" +
                "\n" +
                "\tfunction checkIsLoginForm(form) {\n" +
                "\t\tLoginDetection.showToast(\"checking form \" + form);\n" +
                "\n" +
                "\t\tvar inputs = form.getElementsByTagName(\"input\");\n" +
                "\t\tif (!inputs) {\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < inputs.length; i++) {\n" +
                "\t\t\tvar input = inputs.item(i);\n" +
                "\t\t\tif (input.type == \"password\" && inputVisible(input)) {\n" +
                "\t\t\t\tLoginDetection.showToast(\"found password in form \" + form);\n" +
                "\t\t\t\tloginFormDetected();\n" +
                "\t\t\t\treturn true;\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tLoginDetection.showToast(\"no password field in form \" + form);\n" +
                "\t\treturn false;\n" +
                "\t}\n" +
                "\n" +
                "\tfunction submitHandler(event) {\n" +
                "\t\tcheckIsLoginForm(event.target);\n" +
                "\t}\n" +
                "\n" +
                "\tfunction scanForForms() {\n" +
                "\t\tLoginDetection.showToast(\"Scanning for forms\");\n" +
                "\n" +
                "\t\tvar forms = document.forms;\n" +
                "\t\tif (!forms || forms.length == 0) {\n" +
                "\t\t\tLoginDetection.showToast(\"No forms found\");\n" +
                "\t\t\treturn;\n" +
                "\t\t}\n" +
                "\n" +
                "\t\tfor (var i = 0; i < forms.length; i++) {\n" +
                "\t\t\tvar form = forms[i];\n" +
                "\t\t\tform.removeEventListener(\"submit\", submitHandler);\n" +
                "\t\t\tform.addEventListener(\"submit\", submitHandler);\n" +
                "\t\t\tLoginDetection.showToast(\"adding form handler \" + i);\n" +
                "\t\t}\n" +
                "\n" +
                "\t}\n" +
                "\n" +
                "\twindow.addEventListener(\"DOMContentLoaded\", function(event) {\n" +
                "\t\tLoginDetection.showToast(\"Adding to DOM\");\n" +
                "\t\tsetTimeout(scanForForms, 1000);\n" +
                "\t});\n" +
                "\n" +
                "\twindow.addEventListener(\"click\", scanForForms);\n" +
                "\twindow.addEventListener(\"beforeunload\", scanForForms);\n" +
                "\n" +
                "\twindow.addEventListener(\"submit\", submitHandler);\n" +
                "\n" +
                "\tLoginDetection.showToast(\"installing loginDetection.js - OUT\");\n" +
                "})();"
        webView.evaluateJavascript("javascript:$javascript", null)
    }
}