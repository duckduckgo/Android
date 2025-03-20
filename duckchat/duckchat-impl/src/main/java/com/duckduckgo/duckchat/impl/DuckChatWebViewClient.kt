/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.duckchat.impl

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.UiThread
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.common.utils.plugins.PluginPoint
import javax.inject.Inject

class DuckChatWebViewClient @Inject constructor(
    private val jsPlugins: PluginPoint<JsInjectorPlugin>,
) : WebViewClient() {

    @UiThread
    override fun onPageStarted(
        webView: WebView,
        url: String?,
        favicon: Bitmap?,
    ) {
        jsPlugins.getPlugins().forEach {
            it.onPageStarted(webView, url, null)
        }
    }
}
