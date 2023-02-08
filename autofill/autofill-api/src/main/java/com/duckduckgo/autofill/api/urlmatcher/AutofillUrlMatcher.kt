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

package com.duckduckgo.autofill.api.urlmatcher

interface AutofillUrlMatcher {
    fun extractUrlPartsForAutofill(originalUrl: String?): ExtractedUrlParts
    fun matchingForAutofill(
        visitedSite: ExtractedUrlParts,
        savedSite: ExtractedUrlParts,
    ): Boolean

    /**
     * This method tries to clean up a raw URL.
     * @return a `String` containing host:port for the given raw URL.
     */
    fun cleanRawUrl(rawUrl: String): String

    data class ExtractedUrlParts(
        val eTldPlus1: String?,
        val subdomain: String?,
        val port: Int? = null,
    )
}
