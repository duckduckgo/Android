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

package com.duckduckgo.site.preferences.api

/**
 * Bulk teardown of stored site preferences, consumed by the fire button and single-tab burn.
 * Site keys are eTLD+1 (or raw host when there is no registrable domain).
 */
interface SitePreferencesDataClearer {

    /** Forget preferences for all of [domains] — used by single-tab burn. */
    suspend fun forgetDesktopMode(domains: Set<String>)

    /** Forget every preference except those for [fireproofedDomains] — used by the fire button. */
    suspend fun clearAllButFireproofed(fireproofedDomains: Set<String>)
}
