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
import android.net.http.SslCertificate
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

interface WebViewClientListener {

    fun navigationStateChanged(newWebNavigationState: WebNavigationState)
    fun pageRefreshed(refreshedUrl: String)
    fun progressChanged(newProgress: Int)
    fun willOverrideUrl(newUrl: String)
    fun redirectTriggeredByGpc()

    fun onSitePermissionRequested(
        request: PermissionRequest,
        sitePermissionsAllowedToAsk: Array<String>,
    )

    fun onSitePermissionDenied(
        request: PermissionRequest,
    )

    fun onSiteLocationPermissionRequested(
        origin: String,
        callback: GeolocationPermissions.Callback,
    )

    fun titleReceived(newTitle: String)
    fun trackerDetected(event: TrackingEvent)
    fun pageHasHttpResources(page: String)
    fun onCertificateReceived(certificate: SslCertificate?)

    fun sendEmailRequested(emailAddress: String)
    fun sendSmsRequested(telephoneNumber: String)
    fun dialTelephoneNumberRequested(telephoneNumber: String)
    fun goFullScreen(view: View)
    fun exitFullScreen()
    fun showFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    )

    fun handleAppLink(
        appLink: SpecialUrlDetector.UrlType.AppLink,
        isForMainFrame: Boolean,
    ): Boolean

    fun handleNonHttpAppLink(nonHttpAppLink: SpecialUrlDetector.UrlType.NonHttpAppLink): Boolean
    fun handleCloakedAmpLink(initialUrl: String)
    fun startProcessingTrackingLink()
    fun openMessageInNewTab(message: Message)
    fun recoverFromRenderProcessGone()
    fun requiresAuthentication(request: BasicAuthenticationRequest)
    fun closeCurrentTab()
    fun closeAndSelectSourceTab()
    fun upgradedToHttps()
    fun surrogateDetected(surrogate: SurrogateResponse)
    fun isDesktopSiteEnabled(): Boolean

    fun loginDetected()
    fun dosAttackDetected()
    fun iconReceived(
        url: String,
        icon: Bitmap,
    )

    fun iconReceived(
        visitedUrl: String,
        iconUrl: String,
    )

    fun prefetchFavicon(url: String)
    fun linkOpenedInNewTab(): Boolean
    fun isActiveTab(): Boolean
    fun onReceivedError(errorType: WebViewErrorResponse, url: String)
    fun recordErrorCode(error: String, url: String)
    fun recordHttpErrorCode(statusCode: Int, url: String)
}
