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

/** Public interface for the AMP Links feature */
interface AmpLinks {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * AMP links exceptions list
     * @return `true` if the given [url] is in the AMP links exceptions list and `false`
     * otherwise.
     */
    fun isAnException(url: String): Boolean

    /**
     * This method takes a [url] and returns a [AmpLinkType] depending on the detected AMP
     * link.
     * @return the [AmpLinkType] or `null` if the [url] is not an AMP link.
     */
    fun extractCanonicalFromAmpLink(url: String): AmpLinkType?

    /** The last AMP link and its destination URL. */
    var lastAmpLinkInfo: AmpLinkInfo?
}

/** Public data class for AMP Link Info. */
data class AmpLinkInfo(val ampLink: String, var destinationUrl: String? = null)

/** Public data class for AMP Link Exceptions. */
data class AmpLinkException(val domain: String, val reason: String)

/** Public sealed class for AMP Link Type. */
sealed class AmpLinkType {
    class ExtractedAmpLink(val extractedUrl: String) : AmpLinkType()
    class CloakedAmpLink(val ampUrl: String) : AmpLinkType()
}
