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

package com.duckduckgo.pir.api

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * Evaluates whether a subresource request in a PIR WebView should be blocked as a tracker.
 * Implemented in the app module where TrackerDetector and ResourceSurrogates are available.
 */
interface PirTrackerBlockingInterceptor {
    /**
     * Returns a [WebResourceResponse] (empty or surrogate) to block the request,
     * or null to allow it through.
     */
    fun shouldIntercept(
        request: WebResourceRequest,
        documentUrl: Uri,
    ): WebResourceResponse?
}
