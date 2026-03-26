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

import android.content.Context
import android.net.Uri
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.Lazy
import kotlinx.coroutines.withContext
import logcat.logcat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named

interface TakeoutZipDownloader {
    suspend fun downloadZip(
        url: String,
        userAgent: String,
    ): Uri
}

@ContributesBinding(AppScope::class)
class RealTakeoutZipDownloader @Inject constructor(
    @Named("nonCaching") private val okHttpClient: Lazy<OkHttpClient>,
    private val dispatchers: DispatcherProvider,
    private val context: Context,
    private val cookieManagerProvider: CookieManagerProvider,
) : TakeoutZipDownloader {
    override suspend fun downloadZip(
        url: String,
        userAgent: String,
    ): Uri =
        withContext(dispatchers.io()) {
            logcat { "Bookmark-import: Starting Google Takeout zip download from: $url" }

            val tempFile = createTempZipFile()
            val request = constructRequestBuilder(url, userAgent).build()

            okHttpClient.get().newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    downloadToFile(response, tempFile)
                    Uri.fromFile(tempFile)
                } else {
                    tempFile.delete()
                    throw IOException("Bookmark-import: Google Takeout zip download failed: HTTP ${response.code}: ${response.message}")
                }
            }
        }

    private fun constructRequestBuilder(
        url: String,
        userAgent: String,
    ): Request.Builder {
        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .addHeader(HEADER_USER_AGENT, userAgent)

        // Extract cookies from WebView's CookieManager for this URL
        val cookieManager = cookieManagerProvider.get()
        val cookies = cookieManager?.getCookie(url)
        if (cookies != null) {
            requestBuilder.addHeader(HEADER_COOKIE, cookies)
        }

        return requestBuilder
    }

    private fun createTempZipFile(): File = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, context.cacheDir)

    private fun downloadToFile(
        response: Response,
        tempFile: File,
    ) {
        val responseBody = response.body ?: throw IOException("Google Takeout zip download failed: Empty response body")

        tempFile.outputStream().buffered().use { fileOutput ->
            responseBody.byteStream().use { inputStream ->
                inputStream.copyTo(fileOutput)
            }
        }

        logcat { "Bookmark-import: Downloaded zip to temp file: ${tempFile.absolutePath}, size: ${tempFile.length()} bytes" }
    }

    companion object {
        private const val FILE_PREFIX = "takeout_download_"
        private const val FILE_SUFFIX = ".zip"
        private const val HEADER_USER_AGENT = "User-Agent"
        private const val HEADER_COOKIE = "Cookie"
    }
}
