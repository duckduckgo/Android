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

package com.duckduckgo.app.history

import android.net.Uri
import androidx.core.net.toUri
import com.duckduckgo.app.history.HistoryEntry.VisitedPage
import com.duckduckgo.app.history.HistoryEntry.VisitedSERP
import com.duckduckgo.app.history.store.HistoryEntryWithVisits
import java.util.Date

sealed class HistoryEntry(
    open val url: Uri,
    open val title: String,
    open val visits: List<Date>,
) {

    data class VisitedPage(
        override val url: Uri,
        override val title: String,
        override val visits: List<Date>,
    ) : HistoryEntry(url = url, title = title, visits = visits)

    data class VisitedSERP(
        override val url: Uri,
        override val title: String,
        val query: String,
        val queryTokens: List<String>? = null,
        override val visits: List<Date>,
    ) : HistoryEntry(url = url, title, visits = visits)
}

fun HistoryEntryWithVisits.toHistoryEntry(): HistoryEntry {
    return when (historyEntry.isSerp) {
        true -> VisitedSERP(historyEntry.url.toUri(), historyEntry.title, historyEntry.query ?: "", visits = visits.map { Date(it.date) })
        false -> VisitedPage(historyEntry.url.toUri(), historyEntry.title, visits.map { Date(it.date) })
    }
}
