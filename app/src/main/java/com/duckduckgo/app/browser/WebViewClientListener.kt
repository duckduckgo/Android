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
import com.duckduckgo.app.browser.model.BasicAuthenticationRequest
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

interface WebViewClientListener {

    fun navigationStateChanged(newWebNavigationState: WebNavigationState)
    fun pageRefreshed(refreshedUrl: String)
    fun progressChanged(newProgress: Int)

    fun onSiteLocationPermissionRequested(origin: String, callback: GeolocationPermissions.Callback)

    fun titleReceived(newTitle: String)
    fun trackerDetected(event: TrackingEvent)
    fun pageHasHttpResources(page: String)

    fun sendEmailRequested(emailAddress: String)
    fun sendSmsRequested(telephoneNumber: String)
    fun dialTelephoneNumberRequested(telephoneNumber: String)
    fun goFullScreen(view: View)
    fun exitFullScreen()
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams)
    fun externalAppLinkClicked(appLink: SpecialUrlDetector.UrlType.IntentType)
    fun openMessageInNewTab(message: Message)
    fun recoverFromRenderProcessGone()
    fun requiresAuthentication(request: BasicAuthenticationRequest)
    fun closeCurrentTab()
    fun closeAndSelectSourceTab()
    fun upgradedToHttps()
    fun surrogateDetected(surrogate: SurrogateResponse)

    fun loginDetected()
    fun dosAttackDetected()
    fun iconReceived(icon: Bitmap)
    fun prefetchFavicon(url: String)
}
