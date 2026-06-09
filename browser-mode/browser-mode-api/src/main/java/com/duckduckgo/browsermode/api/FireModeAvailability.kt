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
 * Single facade for whether Fire Mode is available to the user right now.
 *
 * Combines two independent checks:
 *  1. The Fire mode feature flag
 *  2. Whether the installed WebView reports support for the `MultiProfile` capability
 */
interface FireModeAvailability {
    /** Returns true if the prerequisites are satisfied, false otherwise **/
    fun isAvailable(): Boolean
}
