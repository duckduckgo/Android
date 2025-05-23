/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat

interface SharedCredentialJsonReader {
    suspend fun read(): String?
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppleSharedCredentialsJsonReader @Inject constructor(
    private val moshi: Moshi,
    private val dispatchers: DispatcherProvider,
) : SharedCredentialJsonReader {

    override suspend fun read(): String? {
        return withContext(dispatchers.io()) {
            loadJson()
        }
    }

    private fun loadJson(): String? {
        val json = runCatching {
            val reader = javaClass.classLoader?.getResource(JSON_FILENAME)?.openStream()?.bufferedReader()
            reader.use { it?.readText() }
        }.getOrNull()

        if (json == null) {
            logcat(ERROR) { "Failed to load shared credentials json" }
        }

        return json
    }

    private companion object {
        private const val JSON_FILENAME = "shared-credentials.json"
    }
}
