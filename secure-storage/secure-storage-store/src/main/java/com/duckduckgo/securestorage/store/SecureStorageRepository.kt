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

import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsEntity
import com.duckduckgo.securestorage.store.db.WebsiteLoginCredentialsDao
import kotlinx.coroutines.flow.Flow

/**
 * This class is mainly responsible only for accessing and storing data into the DB.
 */
interface SecureStorageRepository {
    suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentialsEntity)

    suspend fun getWebsiteLoginCredentials(id: Int): WebsiteLoginCredentialsEntity

    suspend fun getWebsiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentialsEntity>>

    suspend fun getWebsiteLoginCredentialsForId(id: Int): WebsiteLoginCredentialsEntity

    suspend fun getAllWebsiteLoginCredentials(): Flow<List<WebsiteLoginCredentialsEntity>>

    suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentialsEntity)

    suspend fun deleteWebsiteLoginCredentials(id: Int)
}

class RealSecureStorageRepository constructor(
    private val websiteLoginCredentialsDao: WebsiteLoginCredentialsDao
) : SecureStorageRepository {
    override suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentialsEntity) {
        websiteLoginCredentialsDao.insert(websiteLoginCredentials)
    }

    override suspend fun getWebsiteLoginCredentials(id: Int): WebsiteLoginCredentialsEntity =
        websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(id)

    override suspend fun getWebsiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentialsEntity>> =
        websiteLoginCredentialsDao.getWebsiteLoginCredentialsByDomain(domain)

    override suspend fun getAllWebsiteLoginCredentials(): Flow<List<WebsiteLoginCredentialsEntity>> =
        websiteLoginCredentialsDao.getWebsiteLoginCredentials()

    override suspend fun getWebsiteLoginCredentialsForId(id: Int): WebsiteLoginCredentialsEntity =
        websiteLoginCredentialsDao.getWebsiteLoginCredentialsById(id)

    override suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentialsEntity) =
        websiteLoginCredentialsDao.update(websiteLoginCredentials)

    override suspend fun deleteWebsiteLoginCredentials(id: Int) {
        websiteLoginCredentialsDao.delete(id)
    }
}
