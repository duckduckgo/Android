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

import com.duckduckgo.feature.toggles.api.FeatureException

/** Public interface for the Unprotected Temporary feature */
interface UnprotectedTemporary {
    /**
     * This method takes a [url] and returns `true` or `false` depending if the [url] is in the
     * unprotected temporary exceptions list
     * @return a `true` if the given [url] if the url is in the unprotected temporary exceptions list and `false`
     * otherwise.
     */
    fun isAnException(url: String): Boolean

    /** The unprotected temporary exceptions list */
    val unprotectedTemporaryExceptions: List<FeatureException>
}
