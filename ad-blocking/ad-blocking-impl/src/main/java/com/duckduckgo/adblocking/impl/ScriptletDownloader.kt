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

package com.duckduckgo.adblocking.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

interface ScriptletDownloader {
    suspend fun download(url: String): Result<ByteArray>
}

@ContributesBinding(AppScope::class)
class RealScriptletDownloader @Inject constructor(
    @Named("nonCaching") private val okHttpClient: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
) : ScriptletDownloader {

    override suspend fun download(url: String): Result<ByteArray> = withContext(dispatchers.io()) {
        runCatching {
            val request = Request.Builder().url(url).build()
            okHttpClient.get().newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Scriptlet download failed for $url: HTTP ${response.code}")
                }
                response.body?.bytes() ?: throw IOException("Scriptlet download for $url returned empty body")
            }
        }
    }
}
