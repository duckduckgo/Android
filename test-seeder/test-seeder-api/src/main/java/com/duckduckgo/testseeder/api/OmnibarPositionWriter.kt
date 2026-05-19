/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.testseeder.api

/**
 * Writes the omnibar position from a Maestro intent extra value.
 *
 * Implementations are only contributed in internal builds. The seeder calls this
 * exclusively from `seedIfNeeded`, which is itself a no-op in non-internal flavours,
 * so no fallback binding is required in release builds.
 */
interface OmnibarPositionWriter {
    fun setFromKey(positionKey: String)
}
