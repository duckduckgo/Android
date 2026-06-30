/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.cookies.api

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode

/** Public interface for CookieManagerProvider */
interface CookieManagerProvider {
    /**
     * Returns the [CookieManager] for the browser mode that is currently active.
     */
    fun forCurrentBrowserMode(): CookieManager?

    /**
     * Returns the [CookieManager] for the given [mode], regardless of which mode is currently active.
     */
    fun forMode(mode: BrowserMode): CookieManager? = forCurrentBrowserMode()
}

/**
 * Sets [cookieString] for [url] in every browser mode's cookie jar (the default profile plus any
 * non-default ones), flushing each.
 */
fun CookieManagerProvider.setCookieForAllModes(url: String, cookieString: String) {
    BrowserMode.entries
        .mapNotNull { forMode(it) }
        .distinct()
        .forEach { cookieManager ->
            cookieManager.setCookie(url, cookieString)
            cookieManager.flush()
        }
}
