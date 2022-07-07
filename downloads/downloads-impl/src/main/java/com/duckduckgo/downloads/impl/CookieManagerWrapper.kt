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

import android.webkit.CookieManager
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

// This class is basically a convenience wrapper for easier testing
interface CookieManagerWrapper {
    /**
     * @return the cooke stored for the given [url] if any, null otherwise
     */
    fun getCookie(url: String): String?
}

@ContributesBinding(AppScope::class)
class CookieManagerWrapperImpl @Inject constructor() : CookieManagerWrapper {
    override fun getCookie(url: String): String? {
        return CookieManager.getInstance().getCookie(url)
    }
}
