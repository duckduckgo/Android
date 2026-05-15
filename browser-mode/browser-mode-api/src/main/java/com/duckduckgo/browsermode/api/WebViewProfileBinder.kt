/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.browsermode.api

import android.webkit.WebView

/**
 * Binds a [WebView] to the WebView profile associated with a [BrowserMode], isolating cookies
 * and origin-keyed storage (LocalStorage, IndexedDB) between modes.
 *
 * Must be called once per [WebView] before any other WebView API call, including `loadUrl`. The
 * binding is permanent for the lifetime of the [WebView] — it cannot be changed once the
 * [WebView] has been used.
 *
 * No-op on devices that do not support the `MULTI_PROFILE` WebView feature; such callers
 * implicitly share the default profile across modes.
 */
interface WebViewProfileBinder {

    /**
     * Must be called on the main thread.
     *
     * @param webView a freshly-created [WebView] that has not yet been used.
     * @param mode the [BrowserMode] whose profile should back this [WebView].
     */
    fun bind(webView: WebView, mode: BrowserMode)
}
