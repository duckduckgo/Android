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
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
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
        val exporterPackageName: String?,
    ) : CredentialExchangeImportResult

    data object Cancelled : CredentialExchangeImportResult
    data class Failure(
        val reason: CredentialExchangeFailure,
        val exporterPackageName: String? = null,
    ) : CredentialExchangeImportResult
}

@ContributesBinding(AppScope::class)
class RealCredentialExchangePasswordImporter @Inject constructor(
    private val credentialExchange: CredentialExchange,
    private val domainNameNormalizer: DomainNameNormalizer,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
    private val urlMatcher: AutofillUrlMatcher,
    private val dispatchers: DispatcherProvider,
) : CredentialExchangePasswordImporter {

    override suspend fun isSupported(): Boolean = credentialExchange.isImportSupported()

    override suspend fun convertAndDeduplicate(result: CredentialExchangeResult): CredentialExchangeImportResult {
        return when (result) {
            is CredentialExchangeResult.Success -> CredentialExchangeImportResult.Success(
                credentials = convertAndDeduplicate(result.credentials),
                originalCount = result.credentials.size,
                exporterPackageName = result.exporterPackageName,
            )
            is CredentialExchangeResult.Cancelled -> CredentialExchangeImportResult.Cancelled
            is CredentialExchangeResult.Failure -> CredentialExchangeImportResult.Failure(result.reason, result.exporterPackageName)
        }
    }

    private suspend fun convertAndDeduplicate(credentials: List<ExchangedCredential>): List<LoginCredentials> = withContext(dispatchers.io()) {
        val converted = credentials
            .map { it.toLoginCredentials() }
            .filterNot { it.allFieldsEmpty() }
            .distinct()
        existingCredentialMatchDetector.filterExistingCredentials(converted)
    }

    private suspend fun ExchangedCredential.toLoginCredentials() = LoginCredentials(
        domain = preferredDomain(urls),
        username = username,
        password = password,
        domainTitle = title,
        notes = note,
    )

    /**
     * A saved password can have more than one web address. The code picks one address with these rules, in this order:
     * 1. The main domain, with nothing in front of it. Example: example.com
     * 2. If there is no main domain, the first address that starts with www.
     * 3. If neither is there, the address with the fewest dot-separated parts. e.g., a.example.com has fewer parts than a.b.example.com
     */
    private suspend fun preferredDomain(urls: List<String>): String? {
        val domains = urls
            .filterNot { it.startsWith(ANDROID_APP_PREFIX) }
            .mapNotNull { domainNameNormalizer.normalize(it)?.takeIf(String::isNotBlank) }
            .distinct()
        if (domains.size <= 1) return domains.firstOrNull()

        // same order as iOS, so one export gives the same domain on both platforms
        return domains.firstOrNull { it.isBaseDomain() }
            ?: domains.firstOrNull { it.startsWith(WWW_PREFIX) }
            ?: domains.withFewestParts()
    }

    private fun List<String>.withFewestParts(): String =
        minWith(compareBy<String> { it.split(".").size }.thenBy { it })

    private fun String.isBaseDomain(): Boolean {
        val parts = urlMatcher.extractUrlPartsForAutofill(this)
        return parts.eTldPlus1 != null && parts.subdomain == null
    }

    private fun LoginCredentials.allFieldsEmpty(): Boolean =
        domain.isNullOrBlank() && username.isNullOrBlank() && password.isNullOrBlank() && domainTitle.isNullOrBlank() && notes.isNullOrBlank()

    companion object {
        private const val ANDROID_APP_PREFIX = "android://"
        private const val WWW_PREFIX = "www."
    }
}
