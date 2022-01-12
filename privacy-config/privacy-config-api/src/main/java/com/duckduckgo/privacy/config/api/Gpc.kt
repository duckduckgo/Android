/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.api

/** Public interface for the Gpc feature */
interface Gpc {
    /**
     * This method returns a [String] which contains the JS code for GPC to be injected.
     * @return a [String] containing the JS code gor GPC.
     */
    fun getGpcJs(): String
    /**
     * This takes into account two different inputs.
     * 1. If the user enabled or not the GPC feature
     * 2. If the remote configuration has enabled or not the GPC feature. When disabled, the remote
     * configuration value prevails over the user choice.
     * @return `true` if the feature is enabled and `false` is is not.
     */
    fun isEnabled(): Boolean
    /**
     * This method returns a [Map] with the GPC headers IF the url passed allows for them to be
     * added.
     * @return a [Map] with the GPC headers or an empty [Map] if the above conditions are not met
     */
    fun getHeaders(url: String): Map<String, String>
    /**
     * This method takes a [url] and a map with its [existingHeaders] and it then returns `true` if
     * the given [url] can add the GPC headers
     * @return a `true` if the given [url] and [existingHeaders] permit the GPC headers to be added
     */
    fun canUrlAddHeaders(url: String, existingHeaders: Map<String, String>): Boolean
    /**
     * This method takes a [url] and returns `true` or `false` depending if the given [url] can use
     * the GPC feature
     * @return a `true` if the given [url] can use the GPC feature.
     */
    fun canGpcBeUsedByUrl(url: String): Boolean
    /**
     * This method is used to enable GPC. Note: The remote configuration will take precedence over
     * this value.
     */
    fun enableGpc()
    /**
     * This method is used to disable GPC. Note: The remote configuration will take precedence over
     * this value.
     */
    fun disableGpc()
}

/** Public data class for GPC Exceptions */
data class GpcException(val domain: String)

/** Public data class for GPC Header Enable Sites */
data class GpcHeaderEnabledSite(val domain: String)
