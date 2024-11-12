/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.plugins.headers

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope

/**
 * A plugin point for custom headers that should be added to all requests.
 */
@ContributesPluginPoint(AppScope::class)
interface CustomHeadersPlugin {

    /**
     * Returns a [Map] of headers that should be added to the request IF the url passed allows for them to be
     * added.
     * @param url The url of the request.
     * @return A [Map] of headers.
     */
    fun getHeaders(url: String): Map<String, String>
}
