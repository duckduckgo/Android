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

package com.duckduckgo.duckchat.localserver.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class OriginValidator @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
) {

    private val allowedOrigins = setOf("https://duckduckgo.com", "https://duck.ai")

    /**
     * Returns true if the request headers contain an allowed origin.
     *
     * @param headers the lowercase request headers map from NanoHTTPD (never contains null values).
     *   NanoHTTPD normalises header names to lowercase and trims surrounding whitespace from values,
     *   so no additional trimming is applied here. A value that is blank after NanoHTTPD's own
     *   processing is treated as absent.
     */
    fun isAllowed(headers: Map<String, String>): Boolean {
        val origin = headers["origin"]?.takeIf { it.isNotBlank() } ?: return false
        if (origin in allowedOrigins) return true
        if (appBuildConfig.isInternalBuild() && origin.endsWith(".duck.ai")) return true
        return false
    }
}
