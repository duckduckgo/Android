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

import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.auditlog.AuditLogEntrySkipReason
import com.duckduckgo.sync.impl.auditlog.DecodedAuditLogEntry.Skipped
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.REMOVED
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogAction.SYNCED
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class SyncAuditLogUnseenTest {

    @Test
    fun whenPendingLoginIsLoadedThenItIsUnseen() {
        val unseen = SyncAuditLogUnseen(pendingLoginIds = setOf("e2"))

        assertEquals(setOf("e2"), unseen.unseenIds(loaded(item("e1"), item("e2"), item("e3"))))
    }

    @Test
    fun whenNothingWasSeenBeforeThenOnlyPendingLoginsAreUnseen() {
        val unseen = SyncAuditLogUnseen(pendingLoginIds = setOf("e3"), lastSeenEntryId = null)

        assertEquals(setOf("e3"), unseen.unseenIds(loaded(item("e1", REMOVED), item("e2"), item("e3"))))
    }

    @Test
    fun whenEntriesAreAboveTheLastSeenEntryThenTheyAreUnseen() {
        val unseen = SyncAuditLogUnseen(lastSeenEntryId = "e3")

        assertEquals(setOf("e1", "e2"), unseen.unseenIds(loaded(item("e1", REMOVED), item("e2"), item("e3"), item("e4"))))
    }

    @Test
    fun whenPendingLoginIsOlderThanTheLastSeenEntryThenItIsStillUnseen() {
        val unseen = SyncAuditLogUnseen(pendingLoginIds = setOf("e3"), lastSeenEntryId = "e2")

        assertEquals(setOf("e1", "e3"), unseen.unseenIds(loaded(item("e1", REMOVED), item("e2"), item("e3"))))
    }

    @Test
    fun whenLastSeenEntryIsHiddenThenEntriesAboveItAreStillUnseen() {
        val unseen = SyncAuditLogUnseen(lastSeenEntryId = "hidden")
        val loaded = SyncAuditLogLoadResult.Loaded(
            items = listOf(item("e1"), item("e3")),
            pages = SyncAuditLogPages(
                entries = listOf(skipped("e1"), skipped("hidden"), skipped("e3")),
                before = null,
            ),
        )

        assertEquals(setOf("e1"), unseen.unseenIds(loaded))
    }

    @Test
    fun whenLastSeenEntryIsNotLoadedYetThenEveryLoadedEntryIsUnseen() {
        val unseen = SyncAuditLogUnseen(lastSeenEntryId = "older")

        assertEquals(setOf("e1", "e2"), unseen.unseenIds(loaded(item("e1"), item("e2"))))
    }

    @Test
    fun whenOlderPageLoadsThenOnlyItsOwnRowsAreReturned() {
        val unseen = SyncAuditLogUnseen(lastSeenEntryId = "e4")
        val older = SyncAuditLogLoadResult.Loaded(
            items = listOf(item("e3"), item("e4")),
            pages = SyncAuditLogPages(entries = listOf("e1", "e2", "e3", "e4").map(::skipped), before = null),
        )

        assertEquals(setOf("e3"), unseen.unseenIds(older))
    }

    private fun loaded(vararg items: SyncAuditLogItem) = SyncAuditLogLoadResult.Loaded(
        items = items.toList(),
        pages = SyncAuditLogPages(entries = items.map { skipped(it.entryId) }, before = null),
    )

    private fun skipped(id: String) = Skipped(id, AuditLogEntrySkipReason.UNKNOWN_TYPE)

    private fun item(
        id: String,
        action: SyncAuditLogAction = SYNCED,
    ) = SyncAuditLogItem(id, action, Instant.EPOCH, "Name", DeviceType())
}
