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

    /**
     * This method returns the default user agent policy [DefaultPolicy]
     * @return the default policy [DefaultPolicy]
     */
    fun defaultPolicy(): DefaultPolicy

    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * DDG default sites list
     * @return a `true` if the given [url] is in the DDG default sites list and `false` otherwise.
     */
    fun isADdgDefaultSite(url: String): Boolean

    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * DDG fixed sites list
     * @return a `true` if the given [url] is in the DDG fixed sites list and `false` otherwise.
     */
    fun isADdgFixedSite(url: String): Boolean

    /**
     * This method returns `true` or `false` depending if the closest user agent policy is enabled or not
     * @return a `true` if the closest user agent is enabled and `false` otherwise.
     */
    fun closestUserAgentEnabled(): Boolean

    /**
     * This method returns `true` or `false` depending if the DDG fixed user agent policy is enabled or not
     * @return a `true` if the DDG fixed user agent is enabled and `false` otherwise.
     */
    fun ddgFixedUserAgentEnabled(): Boolean

    /**
     * This method takes a [version] and returns `true` or `false` depending if the [version] is in the
     * closest user agent versions list
     * @return a `true` if the given [version] is in the closest user agent versions and `false` otherwise.
     */
    fun isClosestUserAgentVersion(version: String): Boolean

    /**
     * This method takes a [version] and returns `true` or `false` depending if the [version] is in the
     * ddg fixed user agent versions list
     * @return a `true` if the given [version] is in the ddg fixed user agent versions and `false` otherwise.
     */
    fun isDdgFixedUserAgentVersion(version: String): Boolean
}

/** Public enum class for the default policy */
enum class DefaultPolicy {
    DDG, DDG_FIXED, CLOSEST
}
