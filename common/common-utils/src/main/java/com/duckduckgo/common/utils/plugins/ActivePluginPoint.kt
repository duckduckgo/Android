/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.common.utils.plugins

/** A PluginPoint provides a list of plugins of a particular type T */
interface ActivePluginPoint<out T : @JvmSuppressWildcards ActivePluginPoint.ActivePlugin> {
    /** @return the list of plugins of type <T> */
    suspend fun getPlugins(): Collection<T>

    interface ActivePlugin {
        suspend fun isActive(): Boolean = true
    }
}
