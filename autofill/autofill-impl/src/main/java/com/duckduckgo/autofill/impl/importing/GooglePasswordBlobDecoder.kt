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

import android.util.Base64
import android.util.Base64.DEFAULT
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface GooglePasswordBlobDecoder {
    suspend fun decode(data: String): String
}

@ContributesBinding(AppScope::class)
class GooglePasswordBlobDecoderImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
) : GooglePasswordBlobDecoder {

    override suspend fun decode(data: String): String {
        return withContext(dispatchers.io()) {
            kotlin.runCatching {
                val base64Data = removeDataTypePrefix(data)
                val decodedBytes = Base64.decode(base64Data, DEFAULT)
                String(decodedBytes, Charsets.UTF_8)
            }.getOrElse { rootCause ->
                throw IllegalArgumentException("Unrecognized format", rootCause)
            }
        }
    }

    /**
     * String will start with data type.
     * e.g., data:text/csv;charset=utf-8;;base64,
     */
    private fun removeDataTypePrefix(data: String) = data.split(",")[1]
}
