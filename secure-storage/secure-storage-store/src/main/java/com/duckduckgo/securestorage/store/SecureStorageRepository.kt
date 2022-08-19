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

package com.duckduckgo.securestorage.store

import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsDao
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import kotlinx.coroutines.flow.Flow

/**
 * This class is mainly responsible only for accessing and storing data into the DB.
 */
interface SecureStorageRepository {
    interface Factory {
        fun get(): SecureStorageRepository?
    }

    suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentialsEntity): WebsiteLoginCredentialsEntity?

    suspend fun websiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentialsEntity>>

    suspend fun getWebsiteLoginCredentialsForId(id: Long): WebsiteLoginCredentialsEntity?

    suspend fun websiteLoginCredentials(): Flow<List<WebsiteLoginCredentialsEntity>>

    suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentialsEntity): WebsiteLoginCredentialsEntity?

    suspend fun deleteWebsiteLoginCredentials(id: Long)
}

class RealSecureStorageRepository constructor(
    private val websiteLoginCredentialsDao: WebsiteLoginCredentialsDao
) : SecureStorageRepository {
    override suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentialsEntity): WebsiteLoginCredentialsEntity? {
        val newCredentialId = websiteLoginCredentialsDao.insert(websiteLoginCredentials)
        return websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(newCredentialId)
    }

    override suspend fun websiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentialsEntity>> =
        websiteLoginCredentialsDao.websiteLoginCredentialsByDomain(domain)

    override suspend fun websiteLoginCredentials(): Flow<List<WebsiteLoginCredentialsEntity>> =
        websiteLoginCredentialsDao.websiteLoginCredentials()

    override suspend fun getWebsiteLoginCredentialsForId(id: Long): WebsiteLoginCredentialsEntity? =
        websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(id)

    override suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentialsEntity): WebsiteLoginCredentialsEntity? {
        val credentialId = websiteLoginCredentials.id
        websiteLoginCredentialsDao.update(websiteLoginCredentials)
        return websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(credentialId)
    }

    override suspend fun deleteWebsiteLoginCredentials(id: Long) {
        websiteLoginCredentialsDao.delete(id)
    }
}
