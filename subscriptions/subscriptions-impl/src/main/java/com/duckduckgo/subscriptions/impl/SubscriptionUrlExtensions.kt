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

package com.duckduckgo.subscriptions.impl

import androidx.core.net.toUri
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.FUNNEL_ORIGIN_ALLOWLIST
import com.duckduckgo.subscriptions.impl.SubscriptionsConstants.ORIGIN_QUERY_PARAM_KEY

internal fun String.appendFunnelOriginParam(origin: String?): String {
    val allowedOrigin = origin?.takeIf { it in FUNNEL_ORIGIN_ALLOWLIST } ?: return this
    return runCatching {
        val uri = toUri()
        if (!uri.isHierarchical || uri.getQueryParameter(ORIGIN_QUERY_PARAM_KEY) != null) {
            this
        } else {
            uri.buildUpon().appendQueryParameter(ORIGIN_QUERY_PARAM_KEY, allowedOrigin).build().toString()
        }
    }.getOrDefault(this)
}
