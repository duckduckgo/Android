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

import android.content.Context
import android.net.Uri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

interface CsvFileReader {
    suspend fun readCsvFile(fileUri: Uri): String
}

@ContributesBinding(AppScope::class)
class ContentResolverFileReader @Inject constructor(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : CsvFileReader {

    override suspend fun readCsvFile(fileUri: Uri): String {
        return withContext(dispatchers.io()) {
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    buildString {
                        reader.forEachLine { line ->
                            append(line).append("\n")
                        }
                    }
                }
            } ?: ""
        }
    }
}
