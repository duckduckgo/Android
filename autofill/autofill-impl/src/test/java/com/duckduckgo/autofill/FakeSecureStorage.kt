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

package com.duckduckgo.autofill

import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow

class FakeSecureStore(
    private val canAccessSecureStorage: Boolean,
    private val urlMatcher: AutofillUrlMatcher,
) : SecureStorage {

    private val credentials = mutableMapOf<Long, WebsiteLoginDetailsWithCredentials>()

    override suspend fun addWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials {
        val id = websiteLoginDetailsWithCredentials.details.id ?: (credentials.size.toLong() + 1)
        val credentialWithId: WebsiteLoginDetailsWithCredentials = websiteLoginDetailsWithCredentials.copy(
            details = websiteLoginDetailsWithCredentials.details.copy(id = id),
        )
        credentials[id] = credentialWithId
        return credentialWithId
    }

    override suspend fun addWebsiteLoginDetailsWithCredentials(credentials: List<WebsiteLoginDetailsWithCredentials>): List<Long> {
        credentials.forEach { addWebsiteLoginDetailsWithCredentials(it) }
        return credentials.map { it.details.id!! }
    }

    override suspend fun websiteLoginDetailsForDomain(domain: String): Flow<List<WebsiteLoginDetails>> {
        return flow {
            emit(
                domainLookup(domain).map { it.details },
            )
        }
    }

    override suspend fun websiteLoginDetails(): Flow<List<WebsiteLoginDetails>> {
        return flow {
            emit(credentials.values.map { it.details })
        }
    }

    override suspend fun getWebsiteLoginDetailsWithCredentials(id: Long): WebsiteLoginDetailsWithCredentials? {
        return credentials[id]
    }

    override suspend fun websiteLoginDetailsWithCredentialsForDomain(domain: String): Flow<List<WebsiteLoginDetailsWithCredentials>> {
        return flow {
            emit(
                domainLookup(domain),
            )
        }
    }

    private fun domainLookup(domain: String) = credentials.values
        .filter { it.details.domain?.contains(domain) == true }
        .filter {
            val visitedSite = urlMatcher.extractUrlPartsForAutofill(domain)
            val savedSite = urlMatcher.extractUrlPartsForAutofill(it.details.domain)
            urlMatcher.matchingForAutofill(visitedSite, savedSite)
        }

    override suspend fun websiteLoginDetailsWithCredentials(): Flow<List<WebsiteLoginDetailsWithCredentials>> {
        return flow {
            emit(credentials.values.toList())
        }
    }

    override suspend fun updateWebsiteLoginDetailsWithCredentials(
        websiteLoginDetailsWithCredentials: WebsiteLoginDetailsWithCredentials,
    ): WebsiteLoginDetailsWithCredentials {
        val id = websiteLoginDetailsWithCredentials.details.id ?: return websiteLoginDetailsWithCredentials
        credentials[id] = websiteLoginDetailsWithCredentials
        return websiteLoginDetailsWithCredentials
    }

    override suspend fun deleteWebsiteLoginDetailsWithCredentials(id: Long) {
        credentials.remove(id)
    }

    override suspend fun deleteWebSiteLoginDetailsWithCredentials(ids: List<Long>) {
        ids.forEach {
            credentials.remove(it)
        }
    }

    override suspend fun addToNeverSaveList(domain: String) {
    }

    override suspend fun clearNeverSaveList() {
    }

    override suspend fun neverSaveListCount(): Flow<Int> = emptyFlow()
    override suspend fun isInNeverSaveList(domain: String): Boolean = false

    override suspend fun canAccessSecureStorage(): Boolean = canAccessSecureStorage
}
