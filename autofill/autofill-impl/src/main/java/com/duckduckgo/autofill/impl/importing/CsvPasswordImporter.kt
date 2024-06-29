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

import android.net.Uri
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface CsvPasswordImporter {
    suspend fun importCsv(fileUri: Uri): List<Long>
}

@ContributesBinding(AppScope::class)
class GooglePasswordManagerCsvPasswordImporter @Inject constructor(
    private val parser: CsvPasswordParser,
    private val fileReader: CsvFileReader,
    private val credentialValidator: ImportedPasswordValidator,
    private val existingPasswordMatchDetector: ExistingPasswordMatchDetector,
    private val domainNameNormalizer: DomainNameNormalizer,
    private val dispatchers: DispatcherProvider,
    private val autofillStore: InternalAutofillStore,
) : CsvPasswordImporter {

    override suspend fun importCsv(fileUri: Uri): List<Long> {
        return kotlin.runCatching {
            withContext(dispatchers.io()) {
                val csv = fileReader.readCsvFile(fileUri)
                val allPasswords = parser.parseCsv(csv)
                val dedupedPasswords = allPasswords.distinct()
                val validPasswords = filterValidPasswords(dedupedPasswords)
                val normalizedDomains = domainNameNormalizer.normalizeDomains(validPasswords)
                savePasswords(normalizedDomains)
            }
        }.getOrElse { emptyList() }
    }

    private suspend fun savePasswords(passwords: List<LoginCredentials>): List<Long> {
        val savedCredentialIds = mutableListOf<Long>()
        passwords.forEach {
            if (!existingPasswordMatchDetector.alreadyExists(it)) {
                val insertedId = autofillStore.saveCredentials(it.domain!!, it)?.id

                if (insertedId != null) {
                    savedCredentialIds.add(insertedId)
                }
            }
        }
        return savedCredentialIds
    }

    private fun filterValidPasswords(passwords: List<LoginCredentials>): List<LoginCredentials> {
        return passwords.filter { it.isValid() }
    }

    private fun LoginCredentials.isValid(): Boolean = credentialValidator.isValid(this)
}
