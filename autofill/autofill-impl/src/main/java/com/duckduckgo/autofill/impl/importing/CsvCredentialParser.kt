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

import com.duckduckgo.autofill.impl.importing.CsvCredentialParser.ParseResult
import com.duckduckgo.autofill.impl.importing.CsvCredentialParser.ParseResult.Error
import com.duckduckgo.autofill.impl.importing.CsvCredentialParser.ParseResult.Success
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import de.siegmar.fastcsv.reader.CsvReader
import de.siegmar.fastcsv.reader.CsvRow
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

interface CsvCredentialParser {
    suspend fun parseCsv(csv: String): ParseResult

    sealed interface ParseResult {
        data class Success(val credentials: List<GoogleCsvLoginCredential>) : ParseResult
        data object Error : ParseResult
    }
}

@ContributesBinding(AppScope::class)
class GooglePasswordManagerCsvCredentialParser @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : CsvCredentialParser {

    override suspend fun parseCsv(csv: String): ParseResult {
        return kotlin.runCatching {
            val credentials = convertToCredentials(csv).also {
                logcat(INFO) { "Parsed CSV. Found ${it.size} credentials" }
            }
            Success(credentials)
        }.onFailure {
            logcat(ERROR) { "Failed to parse CSV: ${it.asLog()}" }
            Error
        }.getOrElse {
            Error
        }
    }

    /**
     * Format of the Google Password Manager CSV is:
     * name | url | username | password | note
     */
    private suspend fun convertToCredentials(csv: String): List<GoogleCsvLoginCredential> {
        return withContext(dispatchers.io()) {
            val lines = mutableListOf<CsvRow>()
            val iter = CsvReader.builder().build(csv).spliterator()
            iter.forEachRemaining { lines.add(it) }
            logcat { "Found ${lines.size} lines in the CSV" }

            lines.firstOrNull().verifyExpectedFormat()

            // drop the header row
            val credentialLines = lines.drop(1)

            return@withContext credentialLines
                .mapNotNull {
                    if (it.fields.size != EXPECTED_HEADERS_ORDERED.size) {
                        logcat(WARN) { "Line is unexpected format. Expected ${EXPECTED_HEADERS_ORDERED.size} parts, found ${it.fields.size}" }
                        return@mapNotNull null
                    }

                    parseToCredential(
                        title = it.getField(0).blanksToNull(),
                        url = it.getField(1).blanksToNull(),
                        username = it.getField(2).blanksToNull(),
                        password = it.getField(3).blanksToNull(),
                        notes = it.getField(4).blanksToNull(),
                    )
                }
        }
    }

    private fun parseToCredential(
        title: String?,
        url: String?,
        username: String?,
        password: String?,
        notes: String?,
    ): GoogleCsvLoginCredential {
        return GoogleCsvLoginCredential(
            title = title,
            url = url,
            username = username,
            password = password,
            notes = notes,
        )
    }

    private fun String?.blanksToNull(): String? {
        return if (isNullOrBlank()) null else this
    }

    private fun CsvRow?.verifyExpectedFormat() {
        if (this == null) {
            throw IllegalArgumentException("File not recognised as a CSV")
        }

        val headers = this.fields

        if (headers.size != EXPECTED_HEADERS_ORDERED.size) {
            throw IllegalArgumentException(
                "CSV header size does not match expected amount. Expected: ${EXPECTED_HEADERS_ORDERED.size}, found: ${headers.size}",
            )
        }

        headers.forEachIndexed { index, value ->
            if (value != EXPECTED_HEADERS_ORDERED[index]) {
                throw IllegalArgumentException(
                    "CSV header does not match expected format. Expected: ${EXPECTED_HEADERS_ORDERED[index]}, found: $value",
                )
            }
        }
    }

    companion object {
        val EXPECTED_HEADERS_ORDERED = listOf(
            "name",
            "url",
            "username",
            "password",
            "note",
        )
    }
}
