/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.importing.takeout.zip

import android.webkit.CookieManager
import com.duckduckgo.common.utils.DispatcherProvider
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class TakeoutZipDownloader @Inject constructor(
    @Named("nonCaching") private val okHttpClient: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun downloadZip(
        url: String,
        userAgent: String? = null,
        referrerUrl: String? = null,
    ): ByteArray = withContext(dispatchers.io()) {
        logcat { "Starting bookmark zip download from: $url" }

        val requestBuilder = Request.Builder().url(url)
        userAgent?.let { requestBuilder.addHeader("User-Agent", it) }

        // Extract cookies from WebView's CookieManager for this URL
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)
        if (cookies != null) {
            requestBuilder.addHeader("Cookie", cookies)
            logcat { "Added cookies to request: ${cookies.substring(0, minOf(100, cookies.length))}..." }
        } else {
            logcat { "No cookies found for URL: $url" }
        }

        // Add referrer if available for proper authentication context
        referrerUrl?.let {
            requestBuilder.addHeader("Referer", it)
            logcat { "Added referrer: $it" }
        }

        // Add common headers that might be expected for authenticated requests
        requestBuilder.addHeader("Accept", "application/zip,application/octet-stream,*/*")
        requestBuilder.addHeader("Accept-Language", "en-US,en;q=0.9")
        requestBuilder.addHeader("Accept-Encoding", "gzip, deflate, br")
        requestBuilder.addHeader("Sec-Fetch-Dest", "document")
        requestBuilder.addHeader("Sec-Fetch-Mode", "navigate")
        requestBuilder.addHeader("Sec-Fetch-Site", "same-origin")
        requestBuilder.addHeader("Upgrade-Insecure-Requests", "1")

        val request = requestBuilder.build()

        okHttpClient.get().newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                processSuccessfulResponse(response)
            } else {
                throw IOException("Bookmark zip download failed: HTTP ${response.code}: ${response.message}")
            }
        }
    }

    private fun processSuccessfulResponse(response: Response): ByteArray {
        val zipData = response.body?.bytes()
        return if (zipData != null) {
            logcat { "Bookmark zip downloaded successfully: ${zipData.size} bytes" }
            zipData
        } else {
            throw IOException("Bookmark zip download failed: Empty response body")
        }
    }
}
