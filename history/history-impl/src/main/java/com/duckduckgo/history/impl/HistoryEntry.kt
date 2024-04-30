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

package com.duckduckgo.history.impl

import androidx.core.net.toUri
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.HistoryEntry.VisitedPage
import com.duckduckgo.history.api.HistoryEntry.VisitedSERP
import com.duckduckgo.history.impl.store.HistoryEntryWithVisits
import java.time.LocalDateTime

fun HistoryEntryWithVisits.toHistoryEntry(): HistoryEntry? {
    if (historyEntry.url.isBlank()) return null
    return if (historyEntry.isSerp && !historyEntry.query.isNullOrBlank()) {
        VisitedSERP(historyEntry.url.toUri(), historyEntry.title, historyEntry.query, visits = visits.map { LocalDateTime.parse(it.timestamp) })
    } else {
        VisitedPage(historyEntry.url.toUri(), historyEntry.title, visits.map { LocalDateTime.parse(it.timestamp) })
    }
}
