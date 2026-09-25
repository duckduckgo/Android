/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.credentialtransfer

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.DomainNameNormalizer
import com.duckduckgo.autofill.impl.importing.ExistingCredentialMatchDetector
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.credentialexchange.api.CredentialExchange
import com.duckduckgo.credentialexchange.api.CredentialExchangeFailure
import com.duckduckgo.credentialexchange.api.CredentialExchangeResult
import com.duckduckgo.credentialexchange.api.ExchangedCredential
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Turns what the OS credential exchange hands back into passwords this app can save: the CXF types
 * become [LoginCredentials], domains are normalised, and anything already saved is dropped.
 */
interface CredentialExchangePasswordImporter {
    suspend fun isSupported(): Boolean
    suspend fun convertAndDeduplicate(result: CredentialExchangeResult): CredentialExchangeImportResult
}

sealed interface CredentialExchangeImportResult {

    /**
     * [credentials] is what is left after de-duplication; [originalCount] is what the exporting app
     * sent, so the result screen can say how many were skipped.
     */
    data class Success(
        val credentials: List<LoginCredentials>,
        val originalCount: Int,
    ) : CredentialExchangeImportResult

    data object Cancelled : CredentialExchangeImportResult
    data class Failure(val reason: CredentialExchangeFailure) : CredentialExchangeImportResult
}

@ContributesBinding(AppScope::class)
class RealCredentialExchangePasswordImporter @Inject constructor(
    private val credentialExchange: CredentialExchange,
    private val domainNameNormalizer: DomainNameNormalizer,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    private val dispatchers: DispatcherProvider,
) : CredentialExchangePasswordImporter {

    override suspend fun isSupported(): Boolean = credentialExchange.isImportSupported()

    override suspend fun convertAndDeduplicate(result: CredentialExchangeResult): CredentialExchangeImportResult {
        return when (result) {
            is CredentialExchangeResult.Success -> CredentialExchangeImportResult.Success(
                credentials = deduplicate(result.credentials.map { it.toLoginCredentials() }),
                originalCount = result.credentials.size,
            )
            is CredentialExchangeResult.Cancelled -> CredentialExchangeImportResult.Cancelled
            is CredentialExchangeResult.Failure -> CredentialExchangeImportResult.Failure(result.reason)
        }
    }

    private fun ExchangedCredential.toLoginCredentials() = LoginCredentials(
        domain = url,
        username = username,
        password = password,
        domainTitle = title,
        notes = note,
    )

    private suspend fun deduplicate(credentials: List<LoginCredentials>): List<LoginCredentials> = withContext(dispatchers.io()) {
        val normalised = credentials
            .distinct()
            .map { it.copy(domain = domainNameNormalizer.normalize(it.domain)) }
        existingCredentialMatchDetector.filterExistingCredentials(normalised)
    }
}
