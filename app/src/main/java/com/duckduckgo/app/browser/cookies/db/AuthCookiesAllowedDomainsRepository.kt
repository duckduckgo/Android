/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.cookies.db

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.UriString
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AuthCookiesAllowedDomainsRepository @Inject constructor(
    private val authCookiesAllowedDomainsDao: AuthCookiesAllowedDomainsDao,
    private val dispatcherProvider: DispatcherProvider
) {

    suspend fun addDomain(domain: String): Long? {
        if (!UriString.isValidDomain(domain)) return null

        val authCookieAllowedDomainEntity = AuthCookieAllowedDomainEntity(domain = domain)

        val id = withContext(dispatcherProvider.io()) {
            authCookiesAllowedDomainsDao.insert(authCookieAllowedDomainEntity)
        }

        return if (id >= 0) {
            id
        } else {
            null
        }
    }

    suspend fun getDomain(domain: String): AuthCookieAllowedDomainEntity? {
        return withContext(dispatcherProvider.io()) {
            authCookiesAllowedDomainsDao.getDomain(domain)
        }
    }

    suspend fun removeDomain(authCookieAllowedDomainEntity: AuthCookieAllowedDomainEntity) {
        withContext(dispatcherProvider.io()) {
            authCookiesAllowedDomainsDao.delete(authCookieAllowedDomainEntity)
        }
    }

    suspend fun deleteAll(exceptionList: List<String> = emptyList()) {
        withContext(dispatcherProvider.io()) {
            authCookiesAllowedDomainsDao.deleteAll(exceptionList.joinToString(","))
        }
    }
}
