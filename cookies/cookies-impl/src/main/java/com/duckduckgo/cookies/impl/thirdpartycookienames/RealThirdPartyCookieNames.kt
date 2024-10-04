/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.cookies.impl.thirdpartycookienames

import com.duckduckgo.cookies.api.ThirdPartyCookieNames
import com.duckduckgo.cookies.store.CookiesRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealThirdPartyCookieNames @Inject constructor(
    private val cookiesRepository: CookiesRepository,
) : ThirdPartyCookieNames {

    override fun hasExcludedCookieName(cookieString: String): Boolean {
        return cookiesRepository.cookieNames.any { cookieString.contains(it) }
    }
}
