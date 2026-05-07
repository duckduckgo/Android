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

package com.duckduckgo.firemode.api

/**
 * Single facade for whether Fire Mode is available to the user right now.
 *
 * Combines two independent checks:
 *  - the `fireTabs` RemoteFeature flag (server-side rollout / kill-switch), and
 *  - whether the installed WebView reports support for the `MultiProfile` capability,
 *    which is the hard prerequisite for keeping Fire data isolated.
 *
 * On older WebView versions the capability check fails and Fire Mode is silently
 * unavailable. Call sites — entry points, settings menu, RMF banner, etc. —
 * should consume this facade rather than re-checking the underlying flags.
 */
interface FireModeAvailability {
    suspend fun isAvailable(): Boolean
}
