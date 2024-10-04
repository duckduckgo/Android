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

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import de.siegmar.fastcsv.reader.CsvReader
import de.siegmar.fastcsv.reader.CsvRow
import javax.inject.Inject
import kotlinx.coroutines.withContext
import timber.log.Timber

interface CsvPasswordParser {
    suspend fun parseCsv(csv: String): List<LoginCredentials>
}

@ContributesBinding(AppScope::class)
class GooglePasswordManagerCsvPasswordParser @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : CsvPasswordParser {

//    private val csvFormat by lazy {
//        CSVFormat.Builder.create(CSVFormat.DEFAULT).build()
//    }

    override suspend fun parseCsv(csv: String): List<LoginCredentials> {
        return kotlin.runCatching {
            convertToPasswordList(csv).also {
                Timber.i("Parsed CSV. Found %d passwords", it.size)
            }
        }.onFailure {
            Timber.e("Failed to parse CSV: %s", it.message)
        }.getOrElse {
            emptyList()
        }
    }

    /**
     * Format of the Google Password Manager CSV is:
     * name | url | username | password | note
     */
    private suspend fun convertToPasswordList(csv: String): List<LoginCredentials> {
        return withContext(dispatchers.io()) {
            val lines = mutableListOf<CsvRow>()
            val iter = CsvReader.builder().build(csv).spliterator()
            iter.forEachRemaining { lines.add(it) }
            Timber.d("Found %d lines in the CSV", lines.size)

            lines.firstOrNull().verifyExpectedFormat()

            // drop the header row
            val passwordsLines = lines.drop(1)

            Timber.v("About to parse %d passwords", passwordsLines.size)
            return@withContext passwordsLines
                .mapNotNull {
                    if (it.fields.size != EXPECTED_HEADERS.size) {
                        Timber.w("CSV line does not match expected format. Expected ${EXPECTED_HEADERS.size} parts, found ${it.fields.size}")
                        return@mapNotNull null
                    }

                    parseToCredential(
                        domainTitle = it.getField(0).blanksToNull(),
                        domain = it.getField(1).blanksToNull(),
                        username = it.getField(2).blanksToNull(),
                        password = it.getField(3).blanksToNull(),
                        notes = it.getField(4).blanksToNull(),
                    )
                }
        }
    }

    private fun parseToCredential(
        domainTitle: String?,
        domain: String?,
        username: String?,
        password: String?,
        notes: String?,
    ): LoginCredentials {
        return LoginCredentials(
            domainTitle = domainTitle,
            domain = domain,
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

        if (headers.size != EXPECTED_HEADERS.size) {
            throw IllegalArgumentException(
                "CSV header size does not match expected amount. Expected: ${EXPECTED_HEADERS.size}, found: ${headers.size}",
            )
        }

        headers.forEachIndexed { index, value ->
            if (value != EXPECTED_HEADERS[index]) {
                throw IllegalArgumentException("CSV header does not match expected format. Expected: ${EXPECTED_HEADERS[index]}, found: $value")
            }
        }
    }

    companion object {
        val EXPECTED_HEADERS = listOf(
            "name",
            "url",
            "username",
            "password",
            "note",
        )
    }
}
