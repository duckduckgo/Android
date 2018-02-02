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

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import timber.log.Timber
import javax.inject.Inject


class BrowserChromeClient @Inject constructor() : WebChromeClient() {

    var webViewClientListener: WebViewClientListener? = null
    var customViewCallback: CustomViewCallback? = null

    private var customView: View? = null

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        webViewClientListener?.progressChanged(newProgress)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback?) {
        Timber.i("on show custom view")

        if(customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        webViewClientListener?.goFullScreen(view)
    }

    override fun onHideCustomView() {
        Timber.i("hide custom view")

        webViewClientListener?.exitFullScreen()
        customViewCallback = null
        customView = null
    }

}