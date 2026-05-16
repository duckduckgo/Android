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

package com.duckduckgo.browsermode.api

/**
 * Resolves a mode-specific instance of [T] given a [BrowserMode].
 *
 * Use to expose a resource that has independent [REGULAR][BrowserMode.REGULAR] and
 * [FIRE][BrowserMode.FIRE] variants without referencing either backing implementation directly.
 */
interface BrowserModeDataProvider<T> {
    /** Returns the [T] bound to [mode]. */
    fun forMode(mode: BrowserMode): T
}
