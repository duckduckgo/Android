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

package com.duckduckgo.app.trackerdetection

/**
 * A rolling log of the trackers blocked on this device, kept for a limited retention window so the
 * tab switcher can show how many were blocked recently.
 */
interface WebTrackersBlockedHistory {
    /**
     * Records that a request to [trackerUrl], attributed to [trackerCompany], was blocked.
     */
    suspend fun onTrackerBlocked(trackerUrl: String, trackerCompany: String)

    /**
     * How many trackers were blocked in the last 7 days.
     */
    suspend fun trackerCountForLast7Days(): Int

    /**
     * Erases the whole log, for example when the user clears their browsing data.
     */
    suspend fun deleteAll()

    /**
     * Drops the entries that have fallen outside the retention window.
     */
    suspend fun deleteExpiredEntries()
}
