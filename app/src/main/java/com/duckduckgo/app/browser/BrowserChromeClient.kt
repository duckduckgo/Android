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
import android.webkit.*
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName.*
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.config.api.Drm
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

class BrowserChromeClient @Inject constructor(
    private val crashLogger: CrashLogger,
    private val drm: Drm,
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val coroutineDispatcher: DispatcherProvider = DefaultDispatcherProvider(),
    private val sitePermissionsManager: SitePermissionsManager,
) : WebChromeClient() {

    var webViewClientListener: WebViewClientListener? = null

    private var customView: View? = null

    override fun onShowCustomView(
        view: View,
        callback: CustomViewCallback?,
    ) {
        try {
            Timber.d("on show custom view")
            if (customView != null) {
                callback?.onCustomViewHidden()
                return
            }

            customView = view
            webViewClientListener?.goFullScreen(view)
        } catch (e: Throwable) {
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                crashLogger.logCrash(CrashLogger.Crash(pixelName = APPLICATION_CRASH_WEBVIEW_SHOW_CUSTOM_VIEW.pixelName, t = e))
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
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                crashLogger.logCrash(CrashLogger.Crash(pixelName = APPLICATION_CRASH_WEBVIEW_HIDE_CUSTOM_VIEW.pixelName, t = e))
                throw e
            }
        }
    }

    override fun onProgressChanged(
        webView: WebView,
        newProgress: Int,
    ) {
        try {
            // We want to use webView.progress rather than newProgress because the former gives you the overall progress of the new site
            // and the latter gives you the progress of the current main request being loaded and one site could have several redirects.
            Timber.d("onProgressChanged ${webView.url}, ${webView.progress}")
            if (webView.progress == 0) return
            val navigationList = webView.safeCopyBackForwardList() ?: return
            webViewClientListener?.navigationStateChanged(WebViewNavigationState(navigationList, webView.progress))
            webViewClientListener?.progressChanged(webView.progress)
            webViewClientListener?.onCertificateReceived(webView.certificate)
        } catch (e: Throwable) {
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                crashLogger.logCrash(CrashLogger.Crash(pixelName = APPLICATION_CRASH_WEBVIEW_ON_PROGRESS_CHANGED.pixelName, t = e))
                throw e
            }
        }
    }

    override fun onReceivedIcon(
        webView: WebView,
        icon: Bitmap,
    ) {
        webView.url?.let {
            Timber.i("Favicon bitmap received: ${webView.url}")
            webViewClientListener?.iconReceived(it, icon)
        }
    }

    override fun onReceivedTouchIconUrl(
        view: WebView?,
        url: String?,
        precomposed: Boolean,
    ) {
        Timber.i("Favicon touch received: ${view?.url}, $url")
        val visitedUrl = view?.url ?: return
        val iconUrl = url ?: return
        webViewClientListener?.iconReceived(visitedUrl, iconUrl)
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    override fun onReceivedTitle(
        view: WebView,
        title: String,
    ) {
        try {
            webViewClientListener?.titleReceived(title)
        } catch (e: Throwable) {
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                crashLogger.logCrash(CrashLogger.Crash(pixelName = APPLICATION_CRASH_WEBVIEW_RECEIVED_PAGE_TITLE.pixelName, t = e))
                throw e
            }
        }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        return try {
            webViewClientListener?.showFileChooser(filePathCallback, fileChooserParams)
            true
        } catch (e: Throwable) {
            // cancel the request using the documented way
            filePathCallback.onReceiveValue(null)

            appCoroutineScope.launch(coroutineDispatcher.io()) {
                crashLogger.logCrash(CrashLogger.Crash(pixelName = APPLICATION_CRASH_WEBVIEW_SHOW_FILE_CHOOSER.pixelName, t = e))
                throw e
            }

            true
        }
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?,
    ): Boolean {
        val isGesture = if (appBuildConfig.isTest) true else isUserGesture
        if (isGesture && resultMsg?.obj is WebView.WebViewTransport) {
            webViewClientListener?.openMessageInNewTab(resultMsg)
            return true
        }
        return false
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val drmPermissions = drm.getDrmPermissionsForRequest(request.origin.toString(), request.resources)
        if (drmPermissions.isNotEmpty()) {
            request.grant(drmPermissions)
        }
        appCoroutineScope.launch(coroutineDispatcher.io()) {
            val permissionsAllowedToAsk = sitePermissionsManager.getSitePermissionsAllowedToAsk(request.origin.toString(), request.resources)
            if (permissionsAllowedToAsk.isNotEmpty()) {
                webViewClientListener?.onSitePermissionRequested(request, permissionsAllowedToAsk)
            }
        }
    }

    override fun onCloseWindow(window: WebView?) {
        webViewClientListener?.closeCurrentTab()
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        webViewClientListener?.onSiteLocationPermissionRequested(origin, callback)
    }
}
