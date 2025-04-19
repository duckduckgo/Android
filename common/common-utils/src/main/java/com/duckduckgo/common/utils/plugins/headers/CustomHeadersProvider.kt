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
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface CustomHeadersProvider {

    /**
     * Returns a [Map] of custom headers that should be added to the request.
     * @param url The url of the request.
     * @return A [Map] of headers.
     */
    fun getCustomHeaders(url: String): Map<String, String>

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
}

@ContributesBinding(AppScope::class)
class RealCustomHeadersProvider @Inject constructor(
    private val customHeadersPluginPoint: PluginPoint<CustomHeadersProvider.CustomHeadersPlugin>,
) : CustomHeadersProvider {

    override fun getCustomHeaders(url: String): Map<String, String> {
        val customHeaders = mutableMapOf<String, String>()
        customHeadersPluginPoint.getPlugins().forEach {
            customHeaders.putAll(it.getHeaders(url))
        }
        return customHeaders.toMap()
    }
}
