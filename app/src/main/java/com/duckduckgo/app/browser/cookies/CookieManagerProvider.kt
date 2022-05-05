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

package com.duckduckgo.app.browser.cookies

import android.webkit.CookieManager

interface CookieManagerProvider {
    fun get(): CookieManager
}

class DefaultCookieManagerProvider : CookieManagerProvider {

    @Volatile
    private var instance: CookieManager? = null

    override fun get(): CookieManager =
        instance ?: synchronized(this) {
            instance ?: CookieManager.getInstance().also { instance = it }
        }
}
