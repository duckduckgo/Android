/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.serviceworker

import android.os.Build
import android.webkit.ServiceWorkerClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.annotation.RequiresApi
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.app.global.exception.UncaughtExceptionRepository
import com.duckduckgo.app.global.exception.UncaughtExceptionSource
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.N)
class BrowserServiceWorkerClient(
    private val requestInterceptor: RequestInterceptor,
    private val uncaughtExceptionRepository: UncaughtExceptionRepository
) : ServiceWorkerClient() {

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            try {
                val documentUrl: String? = request.requestHeaders[HEADER_ORIGIN] ?: request.requestHeaders[HEADER_REFERER]
                Timber.v("Intercepting Service Worker resource ${request.url} type:${request.method} on page $documentUrl")
                requestInterceptor.shouldInterceptFromServiceWorker(request, documentUrl)
            } catch (e: Throwable) {
                uncaughtExceptionRepository.recordUncaughtException(e, UncaughtExceptionSource.SHOULD_INTERCEPT_REQUEST_FROM_SERVICE_WORKER)
                throw e
            }
        }
    }

    companion object {
        const val HEADER_ORIGIN = "Origin"
        const val HEADER_REFERER = "Referer"
    }
}
