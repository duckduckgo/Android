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

package com.duckduckgo.app.statistics.api

import org.json.JSONObject

internal object PixelRequestLimit {
    const val DEFAULT_MAX_REQUESTS_PER_HOST = 2
    private const val MIN_MAX_REQUESTS_PER_HOST = 1
    private const val SETTINGS_KEY = "maxRequestsPerHost"

    fun maxRequestsPerHost(settings: String?): Int = runCatching {
        val json = JSONObject(settings ?: return DEFAULT_MAX_REQUESTS_PER_HOST)
        if (!json.has(SETTINGS_KEY)) return DEFAULT_MAX_REQUESTS_PER_HOST
        json.getInt(SETTINGS_KEY).coerceAtLeast(MIN_MAX_REQUESTS_PER_HOST)
    }.getOrDefault(DEFAULT_MAX_REQUESTS_PER_HOST)
}
