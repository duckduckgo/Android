/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.accessibility

import android.webkit.WebView
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore

interface AccessibilityManager {
    fun onPageFinished(webView: WebView, url: String?)
}

class AppAccessibilityManager(val accessibilitySettingsDataStore: AccessibilitySettingsDataStore) :
    AccessibilityManager {
    override fun onPageFinished(webView: WebView, url: String?) {
        if (accessibilitySettingsDataStore.forceZoom) {
            webView.loadUrl(
                "javascript:document.getElementsByName('viewport')[0].setAttribute('content', 'width=device-width,initial-scale=1.0,maximum-scale=10.0,user-scalable=yes');")
        }
    }
}
