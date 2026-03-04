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

package com.duckduckgo.app.browser.pir

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.duckduckgo.app.surrogates.ResourceSurrogates
import com.duckduckgo.app.trackerdetection.TrackerDetector
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.api.PirTrackerBlockingInterceptor
import com.squareup.anvil.annotations.ContributesBinding
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealPirTrackerBlockingInterceptor @Inject constructor(
    private val trackerDetector: TrackerDetector,
    private val resourceSurrogates: ResourceSurrogates,
) : PirTrackerBlockingInterceptor {

    val allowList: Set<String> = setOf(
        "static.cloudflareinsights.com/beacon.min.js",
    )

    override fun shouldIntercept(
        request: WebResourceRequest,
        documentUrl: Uri,
    ): WebResourceResponse? {
        logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 1: $documentUrl" }
        if (request.isForMainFrame) return null

        logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 2: $documentUrl" }

        val trackingEvent = trackerDetector.evaluate(
            request.url,
            documentUrl,
            checkFirstParty = true,
            requestHeaders = request.requestHeaders,
        ) ?: return null

        logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 3: $documentUrl $trackingEvent" }
        if (trackingEvent.status != TrackerStatus.BLOCKED) return null

        logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 4: $documentUrl $trackingEvent" }
        trackingEvent.surrogateId?.let { surrogateId ->
            val surrogate = resourceSurrogates.get(surrogateId)
            if (surrogate.responseAvailable) {
                logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 5: $documentUrl ${request.url}" }
                logcat { "PIR-TRACKER: Surrogate found for ${request.url}" }
                // return WebResourceResponse(
                //     surrogate.mimeType,
                //     "UTF-8",
                //     surrogate.jsFunction.byteInputStream(),
                // )
            }
        }
        logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 6: $documentUrl ${request.url}" }

        if (allowList.any { request.url.toString().contains(it) }) {
            logcat(LogPriority.ERROR) { "PIR-TRACKER shouldIntercept 7 (allowlist): $documentUrl ${request.url}" }
            return null
        }

        logcat { "PIR-TRACKER: Blocking tracker ${request.url}" }
        return WebResourceResponse(null, null, null)
    }
}
