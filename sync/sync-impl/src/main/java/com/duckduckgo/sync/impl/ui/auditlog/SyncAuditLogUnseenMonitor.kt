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

package com.duckduckgo.sync.impl.ui.auditlog

import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry

/**
 * Activity the user has not seen, read when Device Activity opens: every entry newer in the log than [lastSeenEntryId],
 * and the logins that the check found ([pendingLoginIds]), which can be older than it.
 * The check only finds logins, so [lastSeenEntryId] is what makes a removed device show as unseen.
 *
 * Give it each page once, newest page first: it remembers when it passed [lastSeenEntryId].
 */
class SyncAuditLogUnseenMonitor(
    val pendingLoginIds: Set<String>,
    private val lastSeenEntryId: String?,
) {
    // No last seen entry means the screen never opened, and the whole log would look new.
    private var passedLastSeen = lastSeenEntryId == null

    /** The ids of the unseen rows in [items], the rows made from [newEntries]. */
    fun unseenIds(
        newEntries: List<DecodedAuditLogEntry>,
        items: List<SyncAuditLogItem>,
    ): Set<String> {
        val newerIds = HashSet<String>()
        for (entry in newEntries) {
            when {
                passedLastSeen -> break
                entry.id == lastSeenEntryId -> passedLastSeen = true
                else -> newerIds += entry.id
            }
        }
        return items
            .map { it.entryId }
            .filterTo(HashSet()) { it in pendingLoginIds || it in newerIds }
    }
}
