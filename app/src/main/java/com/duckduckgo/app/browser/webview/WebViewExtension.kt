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

package com.duckduckgo.app.browser.webview

import android.annotation.SuppressLint
import android.webkit.WebSettings
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

fun WebSettings.enableLightMode() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_OFF)
    }
}

@SuppressLint("RequiresFeature")
fun WebSettings.enableDarkMode() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
        // requires WebView v76
        WebSettingsCompat.setForceDark(this, WebSettingsCompat.FORCE_DARK_ON)
        // requires WebView v83
        WebSettingsCompat.setForceDarkStrategy(
            this, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
    }
}
