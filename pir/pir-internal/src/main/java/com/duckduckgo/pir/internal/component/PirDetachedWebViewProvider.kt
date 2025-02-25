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

package com.duckduckgo.pir.internal.component

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Message
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.duckduckgo.di.scopes.ServiceScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import logcat.logcat

interface PirDetachedWebViewProvider {
    fun getInstance(context: Context, scriptToLoad: String, onPageLoaded: (String?) -> Unit): WebView
}

@ContributesBinding(ServiceScope::class)
class RealPirDetachedWebViewProvider @Inject constructor() : PirDetachedWebViewProvider {
    @SuppressLint("SetJavaScriptEnabled")
    override fun getInstance(context: Context, scriptToLoad: String, onPageLoaded: (String?) -> Unit): WebView {
        return WebView(context).apply {
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
                /** override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                 logcat {"PIR-SCAN: webview Redirect detected: ${request?.url}"}
                 return false // Allow WebView to load the URL
                 }**/

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?,
                ) {
                    super.onPageStarted(view, url, favicon)
                    logcat { "PIR-SCAN: webview loading $url" }
                    view?.evaluateJavascript("javascript:$scriptToLoad", null)
                    /**if (view != null) {
                     WebViewCompat.addDocumentStartJavaScript(view, scriptToLoad, setOf("*"))
                     }**/
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
