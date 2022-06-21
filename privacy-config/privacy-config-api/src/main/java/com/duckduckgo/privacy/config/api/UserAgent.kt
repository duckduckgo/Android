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

/** Public interface for the User Agent feature */
interface UserAgent {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * application exceptions list
     * @return a `true` if the given [url] if the url is in the application exceptions list and `false`
     * otherwise.
     */
    fun isAnApplicationException(url: String): Boolean
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * version exceptions list
     * @return a `true` if the given [url] if the url is in the version exceptions list and `false`
     * otherwise.
     */
    fun isAVersionException(url: String): Boolean
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * default exceptions list
     * @return a `true` if the given [url] if the url is in the default exceptions list and `false`
     * otherwise.
     */
    fun isADefaultException(url: String): Boolean
}

/** Public data class for User Agent Exceptions */
data class UserAgentException(
    val domain: String,
    val reason: String
)
