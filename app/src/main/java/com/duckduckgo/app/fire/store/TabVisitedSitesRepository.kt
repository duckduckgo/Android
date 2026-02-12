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

package com.duckduckgo.app.fire.store

/**
 * Tracks which eTLD+1 domains each browser tab has visited.
 *
 * Used during single-tab burning to determine which sites' data
 * (cookies, web storage, etc.) should be cleared for a specific tab.
 */
interface TabVisitedSitesRepository {

    /** Records that [domain] (eTLD+1) was visited in the given [tabId]. Duplicate entries are ignored. */
    suspend fun recordVisitedSite(tabId: String, domain: String)

    /** Returns the set of eTLD+1 domains visited by the given [tabId]. */
    suspend fun getVisitedSites(tabId: String): Set<String>

    /** Removes all visited-site records for the given [tabId]. */
    suspend fun clearTab(tabId: String)

    /** Removes all visited-site records across all tabs. Called during full fire (clear all data). */
    suspend fun clearAll()
}
