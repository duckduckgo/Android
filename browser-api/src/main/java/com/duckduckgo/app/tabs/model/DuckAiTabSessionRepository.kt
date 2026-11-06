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

package com.duckduckgo.app.tabs.model

/**
 * Stores session related attributes for Duck.ai tabs. Storage is keyed by tab and cleaned up automatically when the tab is closed.
 * Currently only holds the source entry point for the Duck.ai tab.
 */
interface DuckAiTabSessionRepository {

    /**
     * Records [source] as the entry point of the next tab created or navigated to a Duck.ai URL.
     * Call this before triggering the navigation. Consumed at most once .
     */
    fun setPendingEntryPointSource(source: String)

    /**
     * Claims the pending entry point set via [setPendingEntryPointSource] for [tabId], if [url] is a
     * Duck.ai URL. No-ops if nothing is pending. Safe to call for every tab creation/navigation,
     * the pending value is consumed at most once, so unrelated tabs never claim it.
     */
    fun tryClaimEntryPointSource(tabId: String, url: String?)

    /** The entry point recorded for [tabId], or null if none was ever attributed. */
    suspend fun getEntryPointSource(tabId: String): String?
}
