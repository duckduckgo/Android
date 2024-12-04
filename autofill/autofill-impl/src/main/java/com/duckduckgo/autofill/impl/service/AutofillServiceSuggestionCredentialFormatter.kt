/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import android.content.Context
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AutofillServiceSuggestionCredentialFormatter {
    fun getSuggestionSpecs(credential: LoginCredentials): SuggestionUISpecs
    fun getOpenDuckDuckGoSuggestionSpecs(): SuggestionUISpecs
}

data class SuggestionUISpecs(
    val title: String,
    val subtitle: String,
    val icon: Int,
)

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAutofillCredentialFormatter @Inject constructor(
    private val context: Context,
) : AutofillServiceSuggestionCredentialFormatter {

    override fun getSuggestionSpecs(credential: LoginCredentials): SuggestionUISpecs {
        val userName = credential.nonEmptyUsername()
        val domain = credential.nonEmptyDomain()
        val domainTitle = credential.nonEmptyDomainTitle()

        val title = listOfNotNull(userName, domainTitle, domain).first()
        val subtitle = if (userName != null) {
            domainTitle ?: domain.orEmpty() // domain should exist, otherwise we wouldn't be here
        } else {
            "" // no subtitle if no username
        }
        return SuggestionUISpecs(
            title = title,
            subtitle = subtitle,
            icon = R.drawable.ic_dax_silhouette_primary_24,
        )
    }

    private fun LoginCredentials.nonEmptyUsername(): String? {
        return this.username.takeUnless { it.isNullOrBlank() }
    }

    private fun LoginCredentials.nonEmptyDomain(): String? {
        return this.domain.takeUnless { it.isNullOrBlank() }
    }

    private fun LoginCredentials.nonEmptyDomainTitle(): String? {
        return this.domainTitle.takeUnless { it.isNullOrBlank() }
    }

    override fun getOpenDuckDuckGoSuggestionSpecs(): SuggestionUISpecs {
        return SuggestionUISpecs(
            title = context.getString(R.string.autofill_service_suggestion_search_passwords),
            subtitle = "",
            icon = R.drawable.ic_dax_silhouette_primary_24,
        )
    }
}
