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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ExistingCredentialMatchDetector {
    suspend fun filterExistingCredentials(newCredentials: List<LoginCredentials>): List<LoginCredentials>
}

@ContributesBinding(AppScope::class)
class DefaultExistingCredentialMatchDetector @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
) : ExistingCredentialMatchDetector {

    override suspend fun filterExistingCredentials(newCredentials: List<LoginCredentials>): List<LoginCredentials> {
        return withContext(dispatchers.io()) {
            val existingCredentials = autofillStore.getAllCredentials().firstOrNull() ?: return@withContext newCredentials

            // Filter new credentials to exclude those already in the database
            newCredentials.filter { newCredential ->

                existingCredentials.none { existingCredential ->
                    existingCredential.domain == newCredential.domain &&
                        existingCredential.username == newCredential.username &&
                        existingCredential.password == newCredential.password &&
                        existingCredential.domainTitle == newCredential.domainTitle &&
                        existingCredential.notes == newCredential.notes
                }
            }
        }
    }
}
