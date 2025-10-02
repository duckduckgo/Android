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
import com.duckduckgo.autofill.impl.importing.CsvCredentialConverter.CsvCredentialImportResult
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

interface CsvCredentialConverter {
    suspend fun readCsv(encodedBlob: String): CsvCredentialImportResult
    suspend fun readCsv(fileUri: Uri): CsvCredentialImportResult

    sealed interface CsvCredentialImportResult : Parcelable {

        @Parcelize
        data class Success(
            val numberCredentialsInSource: Int,
            val loginCredentialsToImport: List<LoginCredentials>,
        ) : CsvCredentialImportResult

        @Parcelize
        data object Error : CsvCredentialImportResult
    }
}

@ContributesBinding(AppScope::class)
class GooglePasswordManagerCsvCredentialConverter @Inject constructor(
    private val parser: CsvCredentialParser,
    private val fileReader: CsvFileReader,
    private val credentialValidator: ImportedCredentialValidator,
    private val domainNameNormalizer: DomainNameNormalizer,
    private val dispatchers: DispatcherProvider,
    private val blobDecoder: GooglePasswordBlobDecoder,
    private val existingCredentialMatchDetector: ExistingCredentialMatchDetector,
) : CsvCredentialConverter {

    override suspend fun readCsv(encodedBlob: String): CsvCredentialImportResult {
        return kotlin.runCatching {
            withContext(dispatchers.io()) {
                val csv = blobDecoder.decode(encodedBlob)
                convertToLoginCredentials(csv)
            }
        }.getOrElse { CsvCredentialImportResult.Error }
    }

    override suspend fun readCsv(fileUri: Uri): CsvCredentialImportResult {
        return kotlin.runCatching {
            withContext(dispatchers.io()) {
                val csv = fileReader.readCsvFile(fileUri)
                convertToLoginCredentials(csv)
            }
        }.getOrElse { CsvCredentialImportResult.Error }
    }

    private suspend fun convertToLoginCredentials(csv: String): CsvCredentialImportResult {
        return when (val parseResult = parser.parseCsv(csv)) {
            is CsvCredentialParser.ParseResult.Success -> {
                val toImport = deduplicateAndCleanup(parseResult.credentials)
                CsvCredentialImportResult.Success(parseResult.credentials.size, toImport)
            }
            is CsvCredentialParser.ParseResult.Error -> CsvCredentialImportResult.Error
        }
    }

    private suspend fun deduplicateAndCleanup(allCredentials: List<GoogleCsvLoginCredential>): List<LoginCredentials> {
        return allCredentials
            .distinct()
            .filter { credentialValidator.isValid(it) }
            .toLoginCredentials()
            .filterNewCredentials()
    }

    private suspend fun List<GoogleCsvLoginCredential>.toLoginCredentials(): List<LoginCredentials> {
        return this.map {
            LoginCredentials(
                domainTitle = it.title,
                username = it.username,
                password = it.password,
                domain = domainNameNormalizer.normalize(it.url),
                notes = it.notes,
            )
        }
    }

    private suspend fun List<LoginCredentials>.filterNewCredentials(): List<LoginCredentials> {
        return existingCredentialMatchDetector.filterExistingCredentials(this)
    }
}
