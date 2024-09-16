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

package com.duckduckgo.autofill.impl.ui.credential.selecting

import android.content.Context
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.common.utils.extractDomain

class CredentialTextExtractor(private val applicationContext: Context) {

    fun usernameOrPlaceholder(credentials: LoginCredentials): String {
        credentials.username.let { username ->
            return if (username.isNullOrBlank()) {
                buildPlaceholderForSite(credentials.domain)
            } else {
                username
            }
        }
    }

    private fun buildPlaceholderForSite(domain: String?): String {
        return if (domain.isNullOrBlank()) {
            missingUsernameAndSitePlaceholder()
        } else {
            val domainName = domain.extractDomain() ?: return missingUsernameAndSitePlaceholder()
            applicationContext.getString(R.string.useSavedLoginDialogMissingUsernameButtonText, domainName)
        }
    }

    private fun missingUsernameAndSitePlaceholder(): String {
        return applicationContext.getString(R.string.useSavedLoginDialogMissingUsernameMissingDomainButtonText)
    }
}
