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

package com.duckduckgo.history.api

import android.net.Uri
import java.time.LocalDateTime

/**
 * A sealed class representing a history entry.
 * @property url The URL of the history entry.
 * @property title The title of the history entry.
 * @property visits List of [LocalDateTime] objects representing the visit times.
 */
sealed class HistoryEntry {
    abstract val url: Uri
    abstract val title: String
    abstract val visits: List<LocalDateTime>

    /**
     * Data class representing a visited page history entry.
     * @property url The URL of the visited page.
     * @property title The title of the visited page.
     * @property visits List of [LocalDateTime] objects representing the visit times.
     */
    data class VisitedPage(
        override val url: Uri,
        override val title: String,
        override val visits: List<LocalDateTime>,
    ) : HistoryEntry()

    /**
     * Data class representing a visited search engine results page (SERP) subtype of history entry.
     * @property url The URL of the SERP.
     * @property title The title of the SERP.
     * @property query The search query used for the SERP.
     * @property visits List of [LocalDateTime] objects representing the visit times.
     */
    data class VisitedSERP(
        override val url: Uri,
        override val title: String,
        val query: String,
        override val visits: List<LocalDateTime>,
    ) : HistoryEntry()
}
