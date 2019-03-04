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
import com.duckduckgo.app.browser.BrowserWebViewClient.BrowserNavigationOptions
import com.duckduckgo.app.trackerdetection.model.TrackingEvent

interface WebViewClientListener {

    val url: String?
    fun loadingStarted(url: String?)
    fun progressChanged(progressedUrl: String?, newProgress: Int)
    fun loadingFinished(url: String? = null)
    fun titleReceived(title: String)
    fun navigationOptionsChanged(navigationOptions: BrowserNavigationOptions)
    fun trackerDetected(event: TrackingEvent)
    fun pageHasHttpResources(page: String?)

    fun sendEmailRequested(emailAddress: String)
    fun sendSmsRequested(telephoneNumber: String)
    fun dialTelephoneNumberRequested(telephoneNumber: String)
    fun goFullScreen(view: View)
    fun exitFullScreen()
    fun showFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams)
    fun externalAppLinkClicked(appLink: SpecialUrlDetector.UrlType.IntentType)
}