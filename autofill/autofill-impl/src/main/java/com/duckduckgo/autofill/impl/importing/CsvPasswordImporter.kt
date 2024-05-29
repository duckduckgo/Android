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
import android.os.Parcelable
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.importing.CsvPasswordImporter.ParseResult
import com.duckduckgo.autofill.impl.importing.CsvPasswordImporter.ParseResult.Success
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

interface CsvPasswordImporter {
    suspend fun readCsv(blob: String): ParseResult
    suspend fun readCsv(fileUri: Uri): ParseResult

    sealed interface ParseResult : Parcelable {
        @Parcelize
        data class Success(val numberPasswordsInSource: Int, val loginCredentialsToImport: List<LoginCredentials>) : ParseResult

        @Parcelize
        data object Error : ParseResult
    }
}

@ContributesBinding(AppScope::class)
class GooglePasswordManagerCsvPasswordImporter @Inject constructor(
    private val parser: CsvPasswordParser,
    private val fileReader: CsvFileReader,
    private val credentialValidator: ImportedPasswordValidator,
    private val domainNameNormalizer: DomainNameNormalizer,
    private val dispatchers: DispatcherProvider,
    private val blobDecoder: GooglePasswordBlobDecoder,
) : CsvPasswordImporter {

    override suspend fun readCsv(blob: String): ParseResult {
        return kotlin.runCatching {
            withContext(dispatchers.io()) {
                val csv = blobDecoder.decode(blob)
                convertToLoginCredentials(csv)
            }
        }.getOrElse { ParseResult.Error }
    }

    override suspend fun readCsv(fileUri: Uri): ParseResult {
        return kotlin.runCatching {
            withContext(dispatchers.io()) {
                val csv = fileReader.readCsvFile(fileUri)
                convertToLoginCredentials(csv)
            }
        }.getOrElse { ParseResult.Error }
    }

    private suspend fun convertToLoginCredentials(csv: String): Success {
        val allPasswords = parser.parseCsv(csv)
        val dedupedPasswords = allPasswords.distinct()
        val validPasswords = filterValidPasswords(dedupedPasswords)
        val normalizedDomains = domainNameNormalizer.normalizeDomains(validPasswords)
        return Success(allPasswords.size, normalizedDomains)
    }

    private fun filterValidPasswords(passwords: List<LoginCredentials>): List<LoginCredentials> {
        return passwords.filter { it.isValid() }
    }

    private fun LoginCredentials.isValid(): Boolean = credentialValidator.isValid(this)
}
