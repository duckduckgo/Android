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

package com.duckduckgo.securestorage.impl

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.Result
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginCredentials
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.store.SecureStorageRepository
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealSecureStorage @Inject constructor(
    private val secureStorageRepository: SecureStorageRepository,
    private val dispatchers: DispatcherProvider
) : SecureStorage {

    override fun canAccessSecureStorage(): Boolean = true

    override suspend fun authenticateUser(): Result {
        // TODO (karl) Implement authentication. This is only relevant for L2. Note the expiry here will change once implemented.
        return withContext(dispatchers.io()) {
            Result.Success(expiryInMillis = System.currentTimeMillis() + DEFAULT_EXPIRY_IN_MILLIS)
        }
    }

    override suspend fun authenticateUser(password: String): Result {
        // TODO (karl) Implement authentication. This is only relevant for L2. Note the expiry here will change once implemented.
        return withContext(dispatchers.io()) {
            Result.Success(expiryInMillis = System.currentTimeMillis() + DEFAULT_EXPIRY_IN_MILLIS)
        }
    }

    override suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentials) {
        // TODO (karl) Integrate L2 encryption
        withContext(dispatchers.io()) {
            secureStorageRepository.addWebsiteLoginCredential(websiteLoginCredentials.toDataEntity())
        }
    }

    override suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>> =
        withContext(dispatchers.io()) {
            secureStorageRepository.websiteLoginCredentialsForDomain(domain).map { list ->
                list.map {
                    it.toDetails()
                }
            }
        }

    override suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>> =
        withContext(dispatchers.io()) {
            secureStorageRepository.websiteLoginCredentials().map { list ->
                list.map {
                    it.toDetails()
                }
            }
        }

    override suspend fun getWebsiteLoginCredentials(id: Int): WebsiteLoginCredentials =
        withContext(dispatchers.io()) {
            secureStorageRepository.getWebsiteLoginCredentialsForId(id).toCredentials()
        }

    override suspend fun websiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentials>> =
        // TODO (karl) Integrate L2 encryption
        withContext(dispatchers.io()) {
            secureStorageRepository.websiteLoginCredentialsForDomain(domain).map { list ->
                list.map {
                    it.toCredentials()
                }
            }
        }

    override suspend fun websiteLoginCredentials(): Flow<List<WebsiteLoginCredentials>> =
        // TODO (karl) Integrate L2 encryption
        withContext(dispatchers.io()) {
            secureStorageRepository.websiteLoginCredentials().map { list ->
                list.map {
                    it.toCredentials()
                }
            }
        }

    override suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentials) =
        // TODO (karl) Integrate L2 encryption
        withContext(dispatchers.io()) {
            secureStorageRepository.updateWebsiteLoginCredentials(websiteLoginCredentials.toDataEntity())
        }

    override suspend fun deleteWebsiteLoginCredentials(id: Int) =
        withContext(dispatchers.io()) {
            secureStorageRepository.deleteWebsiteLoginCredentials(id)
        }

    private fun WebsiteLoginCredentials.toDataEntity(): WebsiteLoginCredentialsEntity =
        WebsiteLoginCredentialsEntity(
            id = details.id ?: 0,
            domain = details.domain,
            username = details.username,
            password = password
        )

    private fun WebsiteLoginCredentialsEntity.toCredentials(): WebsiteLoginCredentials =
        WebsiteLoginCredentials(
            details = toDetails(),
            password = password
        )

    private fun WebsiteLoginCredentialsEntity.toDetails(): WebsiteLoginDetails =
        WebsiteLoginDetails(
            domain = domain,
            username = username,
            id = id
        )

    companion object {
        private const val DEFAULT_EXPIRY_IN_MILLIS = 30 * 60 * 1000
    }
}
