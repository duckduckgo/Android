/*
 * Copyright (c) 2022 DuckDuckGo
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

/** Public interface for the Tracking Parameters feature */
interface TrackingParameters {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * tracking parameters exceptions list
     * @return `true` if the given [url] is in the tracking parameters exceptions list and `false`
     * otherwise.
     */
    fun isAnException(url: String): Boolean

    /**
     * This method takes a [url] and returns a [String] containing the cleaned URL with tracking parameters removed.
     * @return the URL [String] or `null` if the [url] does not contain tracking parameters.
     */
    fun cleanTrackingParameters(url: String): String?

    /** The last tracking parameter cleaned URL. */
    var lastCleanedUrl: String?
}

/** Public data class for Tracking Parameter Exceptions. */
data class TrackingParameterException(val domain: String, val reason: String)
