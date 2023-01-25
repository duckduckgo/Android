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

package com.duckduckgo.autofill.impl.ui.credential.management.searching

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CredentialListFilter {
    suspend fun filter(
        originalList: List<LoginCredentials>,
        query: String,
    ): List<LoginCredentials>
}

@ContributesBinding(AppScope::class)
class ManagementScreenCredentialListFilter @Inject constructor(private val autofillCredentialMatcher: AutofillCredentialMatcher) :
    CredentialListFilter {

    override suspend fun filter(
        originalList: List<LoginCredentials>,
        query: String,
    ): List<LoginCredentials> {
        if (query.isBlank()) return originalList

        return originalList.filter {
            autofillCredentialMatcher.matches(it, query)
        }
    }
}

interface AutofillCredentialMatcher {
    fun matches(
        credential: LoginCredentials,
        query: String,
    ): Boolean
}

@ContributesBinding(AppScope::class)
class ManagementScreenAutofillCredentialMatcher @Inject constructor() : AutofillCredentialMatcher {
    override fun matches(
        credential: LoginCredentials,
        query: String,
    ): Boolean {
        if (query.isBlank()) return true
        var matches = false

        if (credential.username?.contains(query, true) == true) {
            matches = true
        } else if (credential.domainTitle?.contains(query, true) == true) {
            matches = true
        } else if (credential.notes?.contains(query, true) == true) {
            matches = true
        } else if (credential.domain?.contains(query, true) == true) {
            matches = true
        }

        return matches
    }
}
