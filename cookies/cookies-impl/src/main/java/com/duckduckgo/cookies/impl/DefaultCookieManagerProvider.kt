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

package com.duckduckgo.cookies.impl

import android.webkit.CookieManager
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class DefaultCookieManagerProvider @Inject constructor() : CookieManagerProvider {

    @Volatile
    private var instance: CookieManager? = null

    override fun get(): CookieManager? {
        return runCatching {
            instance ?: synchronized(this) {
                instance ?: CookieManager.getInstance().also { instance = it }
            }
        }.getOrNull()
    }
}
