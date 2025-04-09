/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface PirDetachedWebViewProvider {
    /**
     * This method returns an instance of WebView created using the given [context] with every necessary
     * configuration setup for pir to run.
     *
     * @param context in which the webview should run - could be service/activity
     * @param scriptToLoad the JS script that is needed for PIR to run.
     * @param onPageLoaded callback to receive whenever a url has finished loading.
     */
    fun createInstance(
        context: Context,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
    ): WebView

    /**
     * This method configures the [WebView] passed to be able to run pir scripts.
     *
     * @param webView in which PIR should run
     * @param scriptToLoad the JS script that is needed for PIR to run.
     * @param onPageLoaded callback to receive whenever a url has finished loading.
     */
    fun setupWebView(
        webView: WebView,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
    ): WebView
}

@ContributesBinding(AppScope::class)
class RealPirDetachedWebViewProvider @Inject constructor() : PirDetachedWebViewProvider {
    @SuppressLint("SetJavaScriptEnabled")
    override fun createInstance(
        context: Context,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
    ): WebView {
        return setupWebView(WebView(context), scriptToLoad, onPageLoaded)
    }

    override fun setupWebView(
        webView: WebView,
        scriptToLoad: String,
        onPageLoaded: (String?) -> Unit,
    ): WebView {
        return webView.apply {
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    message: Message,
                ): Boolean {
                    val transport = message.obj as WebView.WebViewTransport
                    transport.webView = this@apply
                    message.sendToTarget()
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                @SuppressLint("RequiresFeature")
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    return false
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                ) {
                    super.onPageStarted(view, url, favicon)
                    logcat { "PIR-SCAN: webview loading $url" }
                    view?.evaluateJavascript("javascript:$scriptToLoad", null)
                }

                override fun onPageFinished(
                    view: WebView?,
                    url: String?,
                ) {
                    super.onPageFinished(view, url)
                    onPageLoaded(url)
                }
            }
            settings.apply {
                userAgentString = CUSTOM_UA
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                setSupportMultipleWindows(false)
                databaseEnabled = false
                setSupportZoom(true)
            }
        }
    }

    companion object {
        private const val CUSTOM_UA =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.0.0 Mobile DuckDuckGo/5 Safari/537.36"
    }
}
