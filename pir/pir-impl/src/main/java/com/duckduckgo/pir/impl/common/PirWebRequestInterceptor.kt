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

package com.duckduckgo.pir.impl.common

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.tracker.detection.api.TrackerDetector
import com.squareup.anvil.annotations.ContributesBinding
import logcat.logcat
import javax.inject.Inject

interface PirRequestInterceptor {
    /**
     * Determines whether a given web request should be intercepted.
     *
     * @param request the web resource request to evaluate.
     * @param documentUrlProvider lazily provides the document URL. Only invoked when the interceptor
     *   has passed all cheap precondition checks (feature flag, main-frame filter) and actually needs
     *   the URL for tracker evaluation. This avoids expensive main-thread hops when interception is disabled.
     * @return [WebResourceResponse] if the request should be blocked, or null to allow the request to proceed.
     */
    fun shouldInterceptRequest(
        request: WebResourceRequest,
        documentUrlProvider: () -> Uri?,
    ): WebResourceResponse?
}

@ContributesBinding(AppScope::class)
class PirWebRequestInterceptor @Inject constructor(
    private val trackerDetector: TrackerDetector,
    private val pirRemoteFeatures: PirRemoteFeatures,
) : PirRequestInterceptor {

    override fun shouldInterceptRequest(
        request: WebResourceRequest,
        documentUrlProvider: () -> Uri?,
    ): WebResourceResponse? {
        if (!pirRemoteFeatures.trackerBlocking().isEnabled()) {
            return null
        }

        if (request.isForMainFrame) {
            return null
        }

        val documentUrl = documentUrlProvider() ?: return null

        val trackingEvent = trackerDetector.evaluate(
            url = request.url,
            documentUrl = documentUrl,
            requestHeaders = request.requestHeaders.orEmpty(),
        ) ?: return null

        if (trackingEvent.status == TrackerStatus.BLOCKED) {
            logcat { "PIR-INTERCEPTOR: Blocking tracker request ${request.url}" }
            return WebResourceResponse(null, null, null)
        }

        return null
    }
}
