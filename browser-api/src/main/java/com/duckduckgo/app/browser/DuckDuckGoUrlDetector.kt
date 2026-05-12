/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.browser

/** Public interface for DuckDuckGoUrlDetector */
interface DuckDuckGoUrlDetector {

    /**
     * This method takes a [url] and returns `true` or `false`.
     * @return `true` if the given [url] belongs to the DuckDuckGo email domain and `false`
     * otherwise.
     */
    fun isDuckDuckGoEmailUrl(url: String): Boolean

    /**
     * This method takes a [url] and returns `true` or `false`.
     * @return `true` if the given [url] belongs to the DuckDuckGo domain and `false`
     * otherwise.
     */
    fun isDuckDuckGoUrl(url: String): Boolean

    /**
     * This method takes a [uri] and returns `true` or `false`.
     * @return `true` if the given [uri] is a DuckDuckGo query and `false`
     * otherwise.
     */
    fun isDuckDuckGoQueryUrl(uri: String): Boolean

    /**
     * This method takes a [uri] and returns `true` or `false`.
     * @return `true` if the given [uri] is a DuckDuckGo static URL and `false`
     * otherwise.
     */
    fun isDuckDuckGoStaticUrl(uri: String): Boolean

    /**
     * This method takes a [uriString] and returns a [String?]
     * @return the query of a given uri and null if it cannot find it.
     */
    fun extractQuery(uriString: String): String?

    /**
     * This method takes a [uri] and returns `true` or `false`.
     * @return `true` if the given [uri] is a DuckDuckGo vertical URL and `false`
     * otherwise.
     */
    fun isDuckDuckGoVerticalUrl(uri: String): Boolean

    /**
     * This method takes a [uriString] and returns a [String?]
     * @return the vertical of a given uri and null if it cannot find it.
     */
    fun extractVertical(uriString: String): String?
}
