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
 * Public interface for the Https (Smart Encryption) feature
 */
interface Https {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url]
     * is in the https exceptions list
     * @return a `true` if the given [url] if the url is in the https exceptions list and `false` otherwise.
     */
    fun isAnException(url: String): Boolean
}

/**
 * Public data class for Https Exceptions
 */
data class HttpsException(val domain: String, val reason: String)
