/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.dataclearing.api.plugin

/**
 * Triggers data clearing by invoking all registered [DataClearingPlugin] instances.
 *
 * Any module that depends on `:data-clearing-api` can inject this interface
 * to trigger data clearing.
 */
interface DataClearingTrigger {
    /** Execute all registered data clearing plugins with the given [params]. */
    suspend fun clearData(params: DataClearingParams)
}
