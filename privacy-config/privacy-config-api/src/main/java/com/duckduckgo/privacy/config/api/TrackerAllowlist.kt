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

/** Public interface for the Tracker Allowlist feature */
interface TrackerAllowlist {
    /**
     * This method takes a [documentURL] and a [url] and returns `true` or `false` depending if the
     * [url] and [documentURL] match the rules in the allowlist.
     * @return `true` if the given [url] and [documentURL] match the rules in the allowlist
     */
    fun isAnException(documentURL: String, url: String): Boolean
}
