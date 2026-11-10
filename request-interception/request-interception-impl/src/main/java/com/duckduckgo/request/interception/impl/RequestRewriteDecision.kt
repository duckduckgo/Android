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

package com.duckduckgo.request.interception.impl

import android.webkit.WebResourceRequest
import com.duckduckgo.privacy.config.api.Gpc
import com.duckduckgo.user.agent.api.UserAgentProvider
import javax.inject.Inject

class RequestRewriteDecision @Inject constructor(
    private val gpc: Gpc,
    private val userAgentProvider: UserAgentProvider,
) {

    fun getHeaders(request: WebResourceRequest): Map<String, String> {
        return request.requestHeaders.apply {
            putAll(gpc.getHeaders(request.url.toString()))
        }
    }

    fun shouldAddGpcHeaders(request: WebResourceRequest): Boolean {
        val existingHeaders = request.requestHeaders
        return (request.isForMainFrame && request.method == "GET" && gpc.canUrlAddHeaders(request.url.toString(), existingHeaders))
    }

    fun userAgent(url: String, isDesktop: Boolean): String = userAgentProvider.userAgent(url, isDesktop)
}
