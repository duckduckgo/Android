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

package com.duckduckgo.youtubeadblocking.api

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse

/**
 * Public API for YouTube ad blocking feature.
 *
 * This feature intercepts YouTube HTML document requests and injects scriptlets
 * before any page JavaScript executes, blocking ads before they initialise.
 */
interface YouTubeAdBlocking {

    /**
     * @return `true` when YouTube ad blocking is enabled
     */
    suspend fun isEnabled(): Boolean

    /**
     * Intercept a WebView resource request. If the request is for a YouTube HTML document,
     * fetches the response, injects the scriptlet bundle into `<head>`, strips CSP headers,
     * and returns the modified response.
     *
     * @return A modified [WebResourceResponse] with injected scriptlets, or `null` if this
     * request should not be intercepted (not YouTube, not HTML, feature disabled, etc.)
     */
    suspend fun intercept(
        request: WebResourceRequest,
        url: Uri,
    ): WebResourceResponse?
}
