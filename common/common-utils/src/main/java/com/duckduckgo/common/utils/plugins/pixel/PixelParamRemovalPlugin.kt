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

package com.duckduckgo.common.utils.plugins.pixel

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope

/**
 * A plugin point for pixels that require data cleaning.
 * Pixels specified through this mechanism will have their ATB and App version information removed before pixel is sent.
 */
@ContributesPluginPoint(AppScope::class)
interface PixelParamRemovalPlugin {
    /**
     * List of pixels (pixel name or prefix) for which the provided set of parameters will be removed.
     */
    fun names(): List<Pair<String, Set<PixelParameter>>>

    enum class PixelParameter {
        ATB, APP_VERSION;

        companion object {
            fun removeAll() = setOf(ATB, APP_VERSION)
            fun removeAtb() = setOf(ATB)
            fun removeVersion() = setOf(APP_VERSION)
        }
    }
}
