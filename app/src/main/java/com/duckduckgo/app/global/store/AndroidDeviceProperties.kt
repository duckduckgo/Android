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

package com.duckduckgo.app.global.store

import android.content.Context
import android.os.Build
import androidx.webkit.WebViewCompat
import com.duckduckgo.browser.api.DeviceProperties
import java.util.*

class AndroidDeviceProperties(
    val appContext: Context
) : DeviceProperties {
    override fun deviceLocale(): Locale = Locale.getDefault()

    override fun osApiLevel(): Int = Build.VERSION.SDK_INT

    override fun webView(): String = WebViewCompat.getCurrentWebViewPackage(appContext)?.versionName ?: WEBVIEW_UNKNOWN_VERSION

    companion object {
        const val WEBVIEW_UNKNOWN_VERSION = "unknown"
    }
}
