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

package com.duckduckgo.securestorage.api

import kotlinx.coroutines.flow.Flow

interface SecureStorage {
    fun canAccessSecureStorage(): Boolean

    suspend fun authenticateUser(): Result

    suspend fun authenticateUser(password: String): Result

    @Throws(UserNotAuthenticatedException::class)
    suspend fun addWebsiteLoginCredential(websiteLoginCredentials: WebsiteLoginCredentials)

    suspend fun getWebsiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>>

    suspend fun getAllWebsiteLoginDetails(): Flow<List<WebsiteLoginDetails>>

    @Throws(UserNotAuthenticatedException::class)
    suspend fun getWebsiteLoginCredentials(id: Int): WebsiteLoginCredentials

    @Throws(UserNotAuthenticatedException::class)
    suspend fun getWebsiteLoginCredentialsForDomain(domain: String): Flow<List<WebsiteLoginCredentials>>

    @Throws(UserNotAuthenticatedException::class)
    suspend fun getAllWebsiteLoginCredentials(): Flow<List<WebsiteLoginCredentials>>

    @Throws(UserNotAuthenticatedException::class)
    suspend fun updateWebsiteLoginCredentials(websiteLoginCredentials: WebsiteLoginCredentials)

    suspend fun deleteWebsiteLoginCredentials(id: Int)
}
