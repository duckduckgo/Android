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

package com.duckduckgo.pagetranslation.impl

import androidx.work.ListenableWorker.Result
import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.util.concurrent.TimeUnit

internal class ModelsManifestFetcher(
    private val store: ModelsManifestStore,
    private val dispatchers: DispatcherProvider,
) {
    suspend fun fetch(url: String = MANIFEST_URL): Result = withContext(dispatchers.io()) {
        try {
            val request = Request.Builder().url(url).header("User-Agent", "DuckDuckGo")
            store.etagForRequest()?.let { request.header("If-None-Match", it) }
            client.newCall(request.build()).execute().use { response ->
                when (response.code) {
                    HTTP_OK -> {
                        store.replaceIfSupported(response.body?.string().orEmpty(), response.header("ETag"))
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

    private companion object {
        const val MANIFEST_URL = "https://staticcdn.duckduckgo.com/translations/models/models.json"
        val client = OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .followRedirects(false)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
