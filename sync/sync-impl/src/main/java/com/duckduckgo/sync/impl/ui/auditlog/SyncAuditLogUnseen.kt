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

/**
 * Activity the user has not seen, read when Device Activity opens: every entry newer in the log than [lastSeenEntryId],
 * and the logins that the check found ([pendingLoginIds]), which can be older than it.
 * The check only finds logins, so [lastSeenEntryId] is what makes a removed device show as unseen.
 */
data class SyncAuditLogUnseen(
    val pendingLoginIds: Set<String> = emptySet(),
    val lastSeenEntryId: String? = null,
) {
    /** The ids of the unseen rows in [loaded]'s items. */
    fun unseenIds(loaded: SyncAuditLogLoadResult.Loaded): Set<String> {
        // No last seen entry means the screen never opened, and the whole log would look new.
        val newerIds = if (lastSeenEntryId == null) {
            emptySet()
        } else {
            loaded.pages.entries.asSequence().map { it.id }.takeWhile { it != lastSeenEntryId }.toHashSet()
        }
        return loaded.items
            .filter { it.entryId in pendingLoginIds || it.entryId in newerIds }
            .mapTo(HashSet()) { it.entryId }
    }
}
