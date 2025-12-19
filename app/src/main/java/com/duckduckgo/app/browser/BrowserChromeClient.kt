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
import android.graphics.Bitmap.Config.ARGB_8888
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.site.permissions.api.SitePermissionsManager
import com.duckduckgo.site.permissions.api.SitePermissionsManager.LocationPermissionRequest
import com.duckduckgo.site.permissions.api.SitePermissionsManager.SitePermissionQueryResponse.Granted
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.VERBOSE
import logcat.logcat
import java.lang.ref.WeakReference
import javax.inject.Inject

class BrowserChromeClient @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val coroutineDispatcher: DispatcherProvider = DefaultDispatcherProvider(),
    private val sitePermissionsManager: SitePermissionsManager,
) : WebChromeClient() {

    var webViewClientListener: WebViewClientListener? = null

    private var webViewRef: WeakReference<WebView>? = null

    private var customView: View? = null

    override fun onShowCustomView(
        view: View,
        callback: CustomViewCallback?,
    ) {
        logcat { "on show custom view" }
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        webViewClientListener?.goFullScreen(view)
    }

    override fun onHideCustomView() {
        logcat { "on hide custom view" }
        webViewClientListener?.exitFullScreen()
        customView = null
    }

    override fun onProgressChanged(
        webView: WebView,
        newProgress: Int,
    ) {
        webViewRef = WeakReference(webView)
        // We want to use webView.progress rather than newProgress because the former gives you the overall progress of the new site
        // and the latter gives you the progress of the current main request being loaded and one site could have several redirects.
        logcat { "onProgressChanged ${webView.url}, ${webView.progress}" }
        if (webView.progress == 0) return
        val navigationList = webView.safeCopyBackForwardList() ?: return
        webViewClientListener?.progressChanged(webView.progress, WebViewNavigationState(navigationList, webView.progress))
        webViewClientListener?.onCertificateReceived(webView.certificate)
    }

    override fun onReceivedIcon(
        webView: WebView,
        icon: Bitmap,
    ) {
        webView.url?.let {
            logcat(INFO) { "Favicon bitmap received: ${webView.url}" }
            webViewClientListener?.iconReceived(it, icon)
        }
    }

    override fun onReceivedTouchIconUrl(
        view: WebView?,
        url: String?,
        precomposed: Boolean,
    ) {
        logcat(INFO) { "Favicon touch received: ${view?.url}, $url" }
        val visitedUrl = view?.url ?: return
        val iconUrl = url ?: return
        webViewClientListener?.iconReceived(visitedUrl, iconUrl)
        super.onReceivedTouchIconUrl(view, url, precomposed)
    }

    override fun onReceivedTitle(
        view: WebView,
        title: String,
    ) {
        webViewClientListener?.titleReceived(title)
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
            throw e
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
        logcat { "Permissions: permission requested ${request.resources.asList()}" }
        webViewClientListener?.getCurrentTabId()?.let { tabId ->
            appCoroutineScope.launch(coroutineDispatcher.io()) {
                val origin = request.origin.toString()

                // If media capture has already been granted for this tab/site, the SitePermissionsManager may auto-accept the WebView
                // request with no user interaction. In that case, we must lift the WebView autoplay-gesture requirement before the
                // permission is granted so camera previews that rely on implicit play()/autoplay don't render blank.
                val alreadyGrantedMediaCapture = isMediaCaptureAlreadyGranted(origin, tabId, request.resources)
                if (alreadyGrantedMediaCapture) {
                    withContext(coroutineDispatcher.main()) {
                        allowMediaPlaybackWithoutGestureForOrigin(origin)
                    }
                }

                val permissionsAllowedToAsk = sitePermissionsManager.getSitePermissions(tabId, request)
                if (permissionsAllowedToAsk.userHandled.isNotEmpty()) {
                    logcat { "Permissions: permission requested not user handled" }
                    webViewClientListener?.onSitePermissionRequested(request, permissionsAllowedToAsk)
                }
            }
        }
    }

    private suspend fun isMediaCaptureAlreadyGranted(
        origin: String,
        tabId: String,
        resources: Array<String>,
    ): Boolean {
        if (resources.none { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE || it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }) return false

        val cameraGranted =
            if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                sitePermissionsManager.getPermissionsQueryResponse(origin, tabId, "camera") == Granted
            } else {
                false
            }

        val microphoneGranted =
            if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                sitePermissionsManager.getPermissionsQueryResponse(origin, tabId, "microphone") == Granted
            } else {
                false
            }

        return cameraGranted || microphoneGranted
    }

    private fun allowMediaPlaybackWithoutGestureForOrigin(origin: String) {
        val webView = webViewRef?.get() ?: return

        val currentHost = webView.url?.toUri()?.host
        val originHost = origin.toUri().host
        if (currentHost != null && originHost != null && currentHost != originHost) return

        // Setting is per-WebView; BrowserWebViewClient will restore the default on next navigation.
        webView.settings.mediaPlaybackRequiresUserGesture = false
    }

    override fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback,
    ) {
        logcat { "Permissions: location permission requested $origin" }
        onPermissionRequest(LocationPermissionRequest(origin, callback))
    }

    override fun onCloseWindow(window: WebView?) {
        webViewClientListener?.closeCurrentTab()
    }

    /**
     * Called when a site's javascript tries to create a javascript alert dialog
     * @return false to allow it to happen as normal; return true to suppress it from being shown
     */
    override fun onJsAlert(
        view: WebView?,
        url: String,
        message: String,
        result: JsResult,
    ): Boolean = shouldSuppressJavascriptDialog(result)

    /**
     * Called when a site's javascript tries to create a javascript prompt dialog
     * @return false to allow it to happen as normal; return true to suppress it from being shown
     */
    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult,
    ): Boolean = shouldSuppressJavascriptDialog(result)

    /**
     * Called when a site's javascript tries to create a javascript confirmation dialog
     * @return false to allow it to happen as normal; return true to suppress it from being shown
     */
    override fun onJsConfirm(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult,
    ): Boolean = shouldSuppressJavascriptDialog(result)

    /**
     * Determines if we should allow or suppress a javascript dialog from being shown
     *
     * If suppressing it, we also cancel the pending javascript result so JS execution can continue
     * @return false to allow it to happen as normal; return true to suppress it from being shown
     */
    private fun shouldSuppressJavascriptDialog(result: JsResult): Boolean {
        if (webViewClientListener?.isActiveTab() == true) {
            return false
        }

        logcat(VERBOSE) { "javascript dialog attempting to show but is not the active tab; suppressing dialog" }
        result.cancel()
        return true
    }

    override fun getDefaultVideoPoster(): Bitmap {
        return Bitmap.createBitmap(intArrayOf(Color.TRANSPARENT), 1, 1, ARGB_8888)
    }
}
