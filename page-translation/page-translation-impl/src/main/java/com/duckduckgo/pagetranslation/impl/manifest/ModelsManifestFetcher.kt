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

package com.duckduckgo.pagetranslation.impl.manifest

import androidx.work.ListenableWorker.Result
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.ModelsHttpClient
import dev.zacsweers.metro.ContributesBinding
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import javax.inject.Inject

interface ModelsManifestFetcher {
    /** Revalidates the stored manifest, and asks for a retry when the server can't be reached or fails. */
    suspend fun fetch(): Result
}

@ContributesBinding(AppScope::class)
class RealModelsManifestFetcher @Inject constructor(
    private val store: ModelsManifestStore,
    @ModelsHttpClient private val client: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
) : ModelsManifestFetcher {
    override suspend fun fetch(): Result = withContext(dispatchers.io()) {
        try {
            val request = Request.Builder().url(MANIFEST_URL)
            store.getEtagForRequest()?.let { request.header(IF_NONE_MATCH_HEADER, it) }

            client.value.newCall(request.build()).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> {
                        store.replaceIfSupported(response.body?.string().orEmpty(), response.header(ETAG_HEADER))

                        Result.success()
                    }

                    HTTP_NOT_MODIFIED -> Result.success()

                    else -> {
                        logcat(ERROR) { "Page translation models manifest request failed" }
                        Result.retry()
                    }
                }
            }
        } catch (_: IOException) {
            logcat(ERROR) { "Could not refresh page translation models manifest" }
            Result.retry()
        }
    }

    companion object {
        const val MANIFEST_URL = "https://staticcdn.duckduckgo.com/translations/models/models.json"

        const val IF_NONE_MATCH_HEADER = "If-None-Match"
        const val ETAG_HEADER = "ETag"
    }
}
