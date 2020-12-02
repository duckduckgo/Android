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
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class BrowserChromeClient @Inject constructor(private val uncaughtExceptionRepository: UncaughtExceptionRepository) : WebChromeClient() {

    var webViewClientListener: WebViewClientListener? = null

    private var customView: View? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback?) {
        try {
            Timber.d("on show custom view")
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }

            customView = view
            webViewClientListener?.goFullScreen(view)

        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOW_CUSTOM_VIEW)
                throw e
            }
        }
    }

    override fun onHideCustomView() {
        try {
            Timber.d("on hide custom view")
            webViewClientListener?.exitFullScreen()
            customView = null
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, HIDE_CUSTOM_VIEW)
                throw e
            }
        }
    }

    override fun onProgressChanged(webView: WebView, newProgress: Int) {
        try {
            Timber.d("onProgressChanged ${webView.url}, $newProgress")
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList, newProgress))
            webViewClientListener?.progressChanged(newProgress)
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, ON_PROGRESS_CHANGED)
                throw e
            }
        }
    }

    override fun onReceivedIcon(webView: WebView, icon: Bitmap) {
        webViewClientListener?.iconReceived(icon)
    }

    override fun onReceivedTitle(view: WebView, title: String) {
        try {
            webViewClientListener?.titleReceived(title)
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, RECEIVED_PAGE_TITLE)
                throw e
            }
        }
    }

    override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean {
        return try {
            webViewClientListener?.showFileChooser(filePathCallback, fileChooserParams)
            true
        } catch (e: Throwable) {
            GlobalScope.launch {
                uncaughtExceptionRepository.recordUncaughtException(e, SHOW_FILE_CHOOSER)
                throw e
            }

            // cancel the request using the documented way
            filePathCallback.onReceiveValue(null)
            true
        }
    }

    override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
        if (isUserGesture && resultMsg?.obj is WebView.WebViewTransport) {
            webViewClientListener?.openMessageInNewTab(resultMsg)
            return true
        }
        return false
    }

    override fun onCloseWindow(window: WebView?) {
        webViewClientListener?.closeCurrentTab()
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        webViewClientListener?.onSiteLocationPermissionRequested(origin, callback)
    }
}
