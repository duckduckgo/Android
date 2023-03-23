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

/**
 * This interface is used to clean and match URLs for autofill purposes. It can offer support for:
 *    - When we have a full URL and might have to clean this up before we can save it.
 *    - When we have two URLs and need to determine if they should be treated as matching for autofill purposes.
 */
interface AutofillUrlMatcher {

    /**
     * This method tries to extract the parts of a URL that are relevant for autofill.
     */
    fun extractUrlPartsForAutofill(originalUrl: String?): ExtractedUrlParts

    /**
     * This method tries to determine if two URLs are matching for autofill purposes.
     * @return true if the two URLs are matching for autofill purposes, false otherwise.
     */
    fun matchingForAutofill(
        visitedSite: ExtractedUrlParts,
        savedSite: ExtractedUrlParts,
    ): Boolean

    /**
     * This method tries to clean up a raw URL.
     * @return a `String` containing host:port for the given raw URL.
     */
    fun cleanRawUrl(rawUrl: String): String

    /**
     * Data class to hold the extracted parts of a URL.
     * @param eTldPlus1 the eTldPlus1 of the URL, an important component when considering whether two URLs are matching for autofill or not. IDNA-encoded if domain contains non-ascii.
     * @param userFacingETldPlus1 the user-facing eTldPlus1. eTldPlus1 might be IDNA-encoded whereas this will be Unicode-encoded, and therefore might contain non-ascii characters.
     * @param subdomain the subdomain of the URL or null if there was no subdomain.
     * @param port the port of the URL or null if there was no explicit port.
     */
    data class ExtractedUrlParts(
        val eTldPlus1: String?,
        val userFacingETldPlus1: String?,
        val subdomain: String?,
        val port: Int? = null,
    )
}
