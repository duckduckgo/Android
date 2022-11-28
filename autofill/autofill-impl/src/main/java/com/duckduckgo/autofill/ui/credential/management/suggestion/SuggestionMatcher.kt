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

package com.duckduckgo.autofill.ui.credential.management.suggestion

import com.duckduckgo.app.global.extractSchemeAndDomain
import com.duckduckgo.autofill.domain.app.LoginCredentials
import javax.inject.Inject

class SuggestionMatcher @Inject constructor() {

    fun getSuggestions(
        currentUrl: String?,
        credentials: List<LoginCredentials>,
    ): List<LoginCredentials> {
        if (currentUrl == null) return emptyList()

        return credentials.filter {
            it.domain?.extractSchemeAndDomain() == currentUrl.extractSchemeAndDomain()
        }
    }
}
