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

package com.duckduckgo.autofill.impl.ui.credential.management

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface LoginCredentialTitleExtractor {
    fun extract(credential: LoginCredentials): String
}

@ContributesBinding(FragmentScope::class)
class TitleOrDomainExtractor @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
) : LoginCredentialTitleExtractor {

    override fun extract(credential: LoginCredentials): String {
        val title = credential.domainTitle
        if (!title.isNullOrBlank()) {
            return title
        }

        return urlMatcher.extractUrlPartsForAutofill(credential.domain).format()
    }

    private fun ExtractedUrlParts.format(): String {
        return if (subdomain.isNullOrBlank()) {
            eTldPlus1 ?: ""
        } else {
            "$subdomain.$eTldPlus1"
        }
    }
}
