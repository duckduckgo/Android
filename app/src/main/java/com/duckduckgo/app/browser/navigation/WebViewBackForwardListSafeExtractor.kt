/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.browser.navigation

import android.webkit.WebBackForwardList
import android.webkit.WebView
import timber.log.Timber

/**
 * There is a bug in WebView whereby `webView.copyBackForwardList()` can internally throw a NPE
 *
 * This extension function can be used as a direct replacement of `copyBackForwardList()`
 * It will catch the NullPointerException and return `null` when it happens.
 *
 * https://bugs.chromium.org/p/chromium/issues/detail?id=498796
 */
fun WebView.safeCopyBackForwardList(): WebBackForwardList? {
    return try {
        copyBackForwardList()
    } catch (e: NullPointerException) {
        Timber.e(e, "Failed to extract WebView back forward list")
        null
    }
}
