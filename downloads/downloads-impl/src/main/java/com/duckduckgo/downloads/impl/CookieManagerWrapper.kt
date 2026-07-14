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

package com.duckduckgo.downloads.impl

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

// This class is basically a convenience wrapper for easier testing
interface CookieManagerWrapper {
    /**
     * @return the cookie stored for the given [url] in [browserMode]'s cookie store, or null if none.
     */
    fun getCookie(url: String, browserMode: BrowserMode): String?
}

@ContributesBinding(AppScope::class)
class CookieManagerWrapperImpl @Inject constructor(
    private val cookieManagerProvider: CookieManagerProvider,
    private val dispatcherProvider: DispatcherProvider,
) : CookieManagerWrapper {
    override fun getCookie(url: String, browserMode: BrowserMode): String? {
        // Downloads run on a worker thread; the Fire manager (ProfileStore-backed, @UiThread) resolves
        // to null off-main until warmed, so fall back to the main thread only in that cold case.
        val cookieManager = cookieManagerProvider.forMode(browserMode)
            ?: runBlocking(dispatcherProvider.main()) { cookieManagerProvider.forMode(browserMode) }
        return cookieManager?.getCookie(url)
    }
}
