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

import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


class BrowserChromeClient @Inject constructor() : WebChromeClient(), CoroutineScope by MainScope() {

    var webViewClientListener: WebViewClientListener? = null

    private var customView: View? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback?) {
        Timber.d("on show custom view")

        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        webViewClientListener?.goFullScreen(view)
    }

    override fun onHideCustomView() {
        Timber.d("on hide custom view")

        webViewClientListener?.exitFullScreen()
        customView = null
    }

    @Deprecated(DEPRECATED_METHOD_SUGGESTION, ReplaceWith("onProgressChangedAsync(webView, newProgress)"))
    override fun onProgressChanged(webView: WebView, newProgress: Int) {
        launch { onProgressChangedAsync(webView, newProgress) }
    }

    suspend fun onProgressChangedAsync(webView: WebView, newProgress: Int) {
        Timber.d("onProgressChanged - $newProgress - ${webView.url}")
        webViewClientListener?.progressChanged(webView.url, newProgress)
    }

    @Deprecated(DEPRECATED_METHOD_SUGGESTION, ReplaceWith("onReceivedTitle(view, title)"))
    override fun onReceivedTitle(view: WebView, title: String) {
        launch { onReceivedTitleAsync(view, title) }
    }

    suspend fun onReceivedTitleAsync(webView: WebView, title: String) {
        webViewClientListener?.titleReceived(title)
    }

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
        webViewClientListener?.showFileChooser(filePathCallback, fileChooserParams)
        return true
    }

    companion object {
        private const val DEPRECATED_METHOD_SUGGESTION = "This is a required callback, but anywhere we can, we should use suspendable version instead"
    }
}