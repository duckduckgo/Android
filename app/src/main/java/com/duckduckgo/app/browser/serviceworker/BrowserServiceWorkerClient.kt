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

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.net.toUri
import androidx.webkit.ServiceWorkerClientCompat
import com.duckduckgo.app.browser.RequestInterceptor
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.VERBOSE
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class BrowserServiceWorkerClient @Inject constructor(
    private val requestInterceptor: RequestInterceptor,
) : ServiceWorkerClientCompat() {

    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
        return runBlocking {
            val documentUrl: Uri? = (request.requestHeaders[HEADER_ORIGIN] ?: request.requestHeaders[HEADER_REFERER])?.toUri()
            logcat(VERBOSE) { "Intercepting Service Worker resource ${request.url} type:${request.method} on page $documentUrl" }
            requestInterceptor.shouldInterceptFromServiceWorker(request, documentUrl)
        }
    }

    companion object {
        private const val HEADER_ORIGIN = "Origin"
        private const val HEADER_REFERER = "Referer"
    }
}
