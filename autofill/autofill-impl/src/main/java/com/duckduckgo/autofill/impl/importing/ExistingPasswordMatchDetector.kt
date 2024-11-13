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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

interface ExistingPasswordMatchDetector {
    suspend fun alreadyExists(newCredentials: LoginCredentials): Boolean
}

@ContributesBinding(AppScope::class)
class DefaultExistingPasswordMatchDetector @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
    private val autofillStore: InternalAutofillStore,
) : ExistingPasswordMatchDetector {

    override suspend fun alreadyExists(newCredentials: LoginCredentials): Boolean {
        val credentials = autofillStore.getAllCredentials().firstOrNull() ?: return false

        return credentials.any { existing ->
            existing.domain == newCredentials.domain &&
                existing.username == newCredentials.username &&
                existing.password == newCredentials.password &&
                existing.domainTitle == newCredentials.domainTitle &&
                existing.notes == newCredentials.notes
        }
    }
}
