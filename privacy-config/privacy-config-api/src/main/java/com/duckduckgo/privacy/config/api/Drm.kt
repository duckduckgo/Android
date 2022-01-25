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

/** Public interface for the DRM feature */
interface Drm {
    /**
     * This method takes a [url] and an [Array<String>] returns an `Array<String>` depending on the
     * [url]
     * @return an `Array<String>` if the given [url] is in the eme list and an empty array
     * otherwise.
     */
    fun getDrmPermissionsForRequest(
        url: String,
        resources: Array<String>
    ): Array<String>
}

/** Public data class for Drm Exceptions */
data class DrmException(
    val domain: String,
    val reason: String
)
