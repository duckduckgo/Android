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

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.common.utils.DispatcherProvider
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

class ShowOnAppLaunchUrlFetcher @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    @Named("api") private val apiClient: OkHttpClient,
) : UrlFetcher {

    override suspend fun fetchUrl(url: String): String? = withContext(dispatcherProvider.io()) {
        val request = Request.Builder()
            .head()
            .url(url)
            .build()

        try {
            val result = apiClient.newBuilder()
                .followRedirects(true)
                .build()
                .newCall(request)
                .execute()

            val resolvedUrl = result.request.url.toString()
            Timber.d("Successfully fetched resolved url. Result is: $resolvedUrl")
            resolvedUrl
        } catch (e: Exception) {
            Timber.d(e, "Failed to fetch resolved url for $url")
            null
        }
    }
}
