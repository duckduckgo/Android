/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.browser.api

import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.feature.toggles.api.Toggle

/** Public interface to inject JS code to a website */
interface JsInjectorPlugin {
    /**
     * This method is called during onPageStarted and receives a [webView] instance, the [url] of the website and the [site]
     */
    fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle> = listOf(),
    )

    /**
     * This method is called during onPageFinished and receives a [webView] instance, the [url] of the website and the [site]
     */
    fun onPageFinished(webView: WebView, url: String?, site: Site?)
}
