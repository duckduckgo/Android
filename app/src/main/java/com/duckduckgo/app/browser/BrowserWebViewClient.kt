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

    private val loginDetector = LoginDetector()

    @UiThread
    override fun onPageFinished(webView: WebView, url: String?) {
        try {
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList))
            flushCookies()
            loginDetector.injectJS(webView)
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
                    webViewClientListener?.loginDetected()
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
            if (request.url?.path?.contains(Regex("login|sign-in|sessions")) == true) {
                Timber.i("LoginDetectionInterface post login DETECTED")
                return true
            }
        }
        return false
    }

    fun injectJS(webView: WebView) {
        val javascript = "(function() {\n" +
                "  LoginDetection.showToast(\"installing loginDetection.js - IN\");\n" +
                "\n" +
                "  function loginFormDetected() {\n" +
                "    try {\n" +
                "      LoginDetection.loginDetected(\"login detected\");\n" +
                "    } catch (error) {\n" +
                "    }\n" +
                "  }\n" +
                "\n" +
                "  function inputVisible(input) {\n" +
                "    return !(input.offsetWidth === 0 && input.offsetHeight === 0) && !input.ariaHidden && !input.hidden;\n" +
                "  }\n" +
                "\n" +
                "  function checkIsLoginForm(form) {\n" +
                "    LoginDetection.showToast(\"checking form \" + form);\n" +
                "\n" +
                "    var inputs = form.getElementsByTagName(\"input\");\n" +
                "    if (!inputs) {\n" +
                "      return;\n" +
                "    }\n" +
                "\n" +
                "    for (var i = 0; i < inputs.length; i++) {\n" +
                "      var input = inputs.item(i);\n" +
                "      if (input.type == \"password\" && inputVisible(input)) {\n" +
                "        LoginDetection.showToast(\"found password in form \" + form);\n" +
                "        loginFormDetected();\n" +
                "        return true;\n" +
                "      }\n" +
                "    }\n" +
                "\n" +
                "    LoginDetection.showToast(\"no password field in form \" + form);\n" +
                "    return false;\n" +
                "  }\n" +
                "\n" +
                "  function submitHandler(event) {\n" +
                "    checkIsLoginForm(event.target);\n" +
                "  }\n" +
                "\n" +
                "  function scanForForms() {\n" +
                "    LoginDetection.showToast(\"Scanning for forms\");\n" +
                "\n" +
                "    var forms = document.forms;\n" +
                "    if (!forms || forms.length == 0) {\n" +
                "      LoginDetection.showToast(\"No forms found\");\n" +
                "      return;\n" +
                "    }\n" +
                "\n" +
                "    for (var i = 0; i < forms.length; i++) {\n" +
                "      var form = forms[i];\n" +
                "      form.removeEventListener(\"submit\", submitHandler);\n" +
                "      form.addEventListener(\"submit\", submitHandler);\n" +
                "      LoginDetection.showToast(\"adding form handler \" + i);\n" +
                "    }\n" +
                "\n" +
                "  }\n" +
                "\n" +
                "  window.addEventListener(\"DOMContentLoaded\", function(event) {\n" +
                "    LoginDetection.showToast(\"Adding to DOM\");\n" +
                "    setTimeout(scanForForms, 1000);\n" +
                "  });\n" +
                "\n" +
                "  window.addEventListener(\"click\", scanForForms);\n" +
                "  window.addEventListener(\"beforeunload\", scanForForms);\n" +
                "\n" +
                "  window.addEventListener(\"submit\", submitHandler);\n" +
                "\n" +
                "  try {\n" +
                "    const observer = new PerformanceObserver((list, observer) => {\n" +
                "      LoginDetection.showToast(\"XHR: Observer callback - IN\");\n" +
                "      const entries = list.getEntries().filter((entry) => {\n" +
                "        var found = entry.initiatorType == \"xmlhttprequest\" && entry.name.split(\"?\")[0].match(/login|sign-in/);\n" +
                "        if (found) {\n" +
                "          LoginDetection.showToast(\"XHR: observed login - \" + entry.name.split(\"?\")[0]);\n" +
                "          LoginDetection.loginDetected(\"XHR: observed login - \" + entry.name.split(\"?\")[0]);\n" +
                "        }\n" +
                "        return found;\n" +
                "      });\n" +
                "\n" +
                "      if (entries.length == 0) {\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      LoginDetection.showToast(\"XHR: checking forms - IN\");\n" +
                "      var forms = document.forms;\n" +
                "      if (!forms || forms.length == 0) {\n" +
                "        LoginDetection.showToast(\"XHR: No forms found\");\n" +
                "        return;\n" +
                "      }\n" +
                "\n" +
                "      for (var i = 0; i < forms.length; i++) {\n" +
                "        if (checkIsLoginForm(forms[i])) {\n" +
                "          LoginDetection.showToast(\"XHR: found login form\");\n" +
                "          break;\n" +
                "        }\n" +
                "      }\n" +
                "      LoginDetection.showToast(\"XHR: checking forms - OUT\");\n" +
                "\n" +
                "    });\n" +
                "    observer.observe({\n" +
                "      entryTypes: [\"resource\"]\n" +
                "    });\n" +
                "  } catch (error) {\n" +
                "  }\n" +
                "\n" +
                "  setTimeout(scanForForms, 1000);" +
                "  LoginDetection.showToast(\"installing loginDetection.js - OUT\");\n" +
                "\n" +
                "})();"
        webView.evaluateJavascript("javascript:$javascript", null)
    }
}