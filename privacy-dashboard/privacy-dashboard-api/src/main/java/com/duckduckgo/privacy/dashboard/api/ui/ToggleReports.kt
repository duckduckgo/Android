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

package com.duckduckgo.privacy.dashboard.api.ui

interface ToggleReports {
    /**
     * Returns true if all the conditions are met for prompting the user to submit a toggle-off-prompted simplified breakage report.
     */
    suspend fun shouldPrompt(): Boolean

    /**
     * Adds a record to the datastore that a toggle-off prompt for a report was dismissed.
     */
    suspend fun onPromptDismissed()

    /**
     * Adds a record to the datastore that a report was sent as the result of a toggle-off prompt.
     */
    suspend fun onReportSent()
}
