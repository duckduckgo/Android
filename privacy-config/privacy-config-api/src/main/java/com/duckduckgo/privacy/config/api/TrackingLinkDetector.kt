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

/**
 * Public interface for the Tracking Link Detection feature
 */
interface TrackingLinkDetector {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url]
     * is in the tracking links exceptions list
     * @return a `true` if the given [url] if the url is in the tracking links exceptions list and `false` otherwise.
     */
    fun isAnException(url: String): Boolean

    /**
     * This method takes a [url] and returns the extracted destination [url] from a tracking link.
     * @return the extracted destination [url] or `null` if the [url] is not a tracking link.
     */
    fun extractCanonicalFromTrackingLink(url: String): String?
}

/**
 * Public data class for Tracking Links Exceptions
 */
data class TrackingLinkException(val domain: String, val reason: String)
