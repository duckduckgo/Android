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
     * @return `true` if the given [url] is in the tracking links exceptions list and `false` otherwise.
     */
    fun isAnException(url: String): Boolean

    /**
     * This method takes a [url] and returns a [TrackingLinkType] depending on the detected tracking link.
     * @return the [TrackingLinkType] or `null` if the [url] is not a tracking link.
     */
    fun extractCanonicalFromTrackingLink(url: String): TrackingLinkType?

    /**
     * The last tracking link and its destination URL.
     */
    var lastTrackingLinkInfo: TrackingLinkInfo?
}

/**
 * Public data class for Tracking Link Info.
 */
data class TrackingLinkInfo(val trackingLink: String, var destinationUrl: String? = null)

/**
 * Public data class for Tracking Link Exceptions.
 */
data class TrackingLinkException(val domain: String, val reason: String)

/**
 * Public sealed class for Tracking Link Type.
 */
sealed class TrackingLinkType {
    class ExtractedTrackingLink(val extractedUrl: String) : TrackingLinkType()
    class CloakedTrackingLink(val trackingUrl: String) : TrackingLinkType()
}
