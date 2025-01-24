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

package com.duckduckgo.autofill.impl.service.mapper

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

interface AppCredentialProvider {
    /**
     * Provide a list of unique credentials that can be associated with the given [appPackage]
     * You DO NOT need to set any dispatcher to call this suspend function
     */
    suspend fun getCredentials(appPackage: String): List<LoginCredentials>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAppCredentialProvider @Inject constructor(
    private val appToDomainMapper: AppToDomainMapper,
    private val dispatcherProvider: DispatcherProvider,
    private val autofillStore: AutofillStore,
) : AppCredentialProvider {
    override suspend fun getCredentials(appPackage: String): List<LoginCredentials> = withContext(dispatcherProvider.io()) {
        Timber.d("Autofill-mapping: Getting credentials for $appPackage")
        return@withContext appToDomainMapper.getAssociatedDomains(appPackage).map {
            getAllCredentialsFromDomain(it)
        }.flatten().distinct().also {
            Timber.d("Autofill-mapping: Total credentials for $appPackage : ${it.size}")
        }
    }

    private suspend fun getAllCredentialsFromDomain(domain: String): List<LoginCredentials> {
        return autofillStore.getCredentials(domain)
    }
}
