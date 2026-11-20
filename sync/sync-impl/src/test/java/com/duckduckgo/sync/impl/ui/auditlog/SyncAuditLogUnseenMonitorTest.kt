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

class SyncAuditLogUnseenMonitorTest {

    @Test
    fun whenPendingLoginIsLoadedThenItIsUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = setOf("e2"), lastSeenEntryId = null)

        assertEquals(setOf("e2"), monitor.unseenIdsOf(item("e1"), item("e2"), item("e3")))
    }

    @Test
    fun whenNothingWasSeenBeforeThenOnlyPendingLoginsAreUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = setOf("e3"), lastSeenEntryId = null)

        assertEquals(setOf("e3"), monitor.unseenIdsOf(item("e1", REMOVED), item("e2"), item("e3")))
    }

    @Test
    fun whenEntriesAreAboveTheLastSeenEntryThenTheyAreUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = emptySet(), lastSeenEntryId = "e3")

        assertEquals(setOf("e1", "e2"), monitor.unseenIdsOf(item("e1", REMOVED), item("e2"), item("e3"), item("e4")))
    }

    @Test
    fun whenPendingLoginIsOlderThanTheLastSeenEntryThenItIsStillUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = setOf("e3"), lastSeenEntryId = "e2")

        assertEquals(setOf("e1", "e3"), monitor.unseenIdsOf(item("e1", REMOVED), item("e2"), item("e3")))
    }

    @Test
    fun whenLastSeenEntryIsHiddenThenEntriesAboveItAreStillUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = emptySet(), lastSeenEntryId = "hidden")

        val unseenIds = monitor.unseenIds(
            newEntries = listOf(skipped("e1"), skipped("hidden"), skipped("e3")),
            items = listOf(item("e1"), item("e3")),
        )

        assertEquals(setOf("e1"), unseenIds)
    }

    @Test
    fun whenLastSeenEntryIsNotLoadedYetThenEveryLoadedEntryIsUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = emptySet(), lastSeenEntryId = "older")

        assertEquals(setOf("e1", "e2"), monitor.unseenIdsOf(item("e1"), item("e2")))
    }

    @Test
    fun whenLastSeenEntryIsOnAnOlderPageThenEntriesAboveItOnThatPageAreUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = emptySet(), lastSeenEntryId = "e4")
        monitor.unseenIdsOf(item("e1"), item("e2"))

        assertEquals(setOf("e3"), monitor.unseenIdsOf(item("e3"), item("e4")))
    }

    @Test
    fun whenPageAfterTheLastSeenEntryLoadsThenOnlyPendingLoginsAreUnseen() {
        val monitor = SyncAuditLogUnseenMonitor(pendingLoginIds = setOf("e5"), lastSeenEntryId = "e2")
        monitor.unseenIdsOf(item("e1"), item("e2"))

        assertEquals(setOf("e5"), monitor.unseenIdsOf(item("e3"), item("e4"), item("e5")))
    }

    private fun SyncAuditLogUnseenMonitor.unseenIdsOf(vararg items: SyncAuditLogItem) =
        unseenIds(newEntries = items.map { skipped(it.entryId) }, items = items.toList())

    private fun skipped(id: String) = Skipped(id, AuditLogEntrySkipReason.UNKNOWN_TYPE)

    private fun item(
        id: String,
        action: SyncAuditLogAction = SYNCED,
    ) = SyncAuditLogItem(id, action, Instant.EPOCH, "Name", DeviceType())
}
