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

package com.duckduckgo.sync.impl.auditlog

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.time.Instant

class RealAuditLogCheckStateStoreTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    private val syncStore = RecordingSyncStore()

    private val testee = RealAuditLogCheckStateStore(syncStore, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenNothingStoredThenReadReturnsEmptyState() = runTest {
        assertEquals(AuditLogCheckState(cursor = null, pendingLogins = emptyList()), testee.read())
    }

    @Test
    fun whenUpdateWritesStateThenReadReturnsIt() = runTest {
        val state = AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1), login(2), login(3)))

        testee.update { state }

        assertEquals(state, testee.read())
        assertEquals("after-1", syncStore.auditLogCursor)
    }

    @Test
    fun whenUpdateReturnsNullThenNothingIsWritten() = runTest {
        testee.update { AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1))) }
        syncStore.writes.clear()

        testee.update { null }

        assertTrue(syncStore.writes.isEmpty())
        assertEquals(AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1))), testee.read())
    }

    @Test
    fun whenSignedOutThenUpdateWritesNothing() = runTest {
        syncStore.signedIn = false

        testee.update { AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1))) }

        assertTrue(syncStore.writes.isEmpty())
        assertNull(syncStore.auditLogCursor)
        assertNull(syncStore.auditLogPending)
    }

    @Test
    fun whenUpdateThenTransformGetsCurrentState() = runTest {
        testee.update { AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1))) }

        testee.update { it.copy(cursor = "after-2", pendingLogins = it.pendingLogins + login(2)) }

        assertEquals(AuditLogCheckState(cursor = "after-2", pendingLogins = listOf(login(1), login(2))), testee.read())
    }

    @Test
    fun whenUpdateThenPendingListIsWrittenBeforeCursor() = runTest {
        testee.update { AuditLogCheckState(cursor = "after-1", pendingLogins = listOf(login(1))) }

        assertEquals(listOf(PENDING, CURSOR), syncStore.writes)
    }

    @Test
    fun whenCursorClearedThenItIsNull() = runTest {
        testee.update { AuditLogCheckState(cursor = "after-1", pendingLogins = emptyList()) }

        testee.update { it.copy(cursor = null) }

        assertNull(testee.read().cursor)
        assertNull(syncStore.auditLogCursor)
    }

    @Test
    fun whenPendingLoginsWrittenThenStoredJsonUsesStableKeys() = runTest {
        testee.update { it.copy(pendingLogins = listOf(login(1))) }

        val json = syncStore.auditLogPending!!
        assertTrue(json.contains(""""entry_id":"entry-1""""))
        assertTrue(json.contains(""""device_id":"device-1""""))
        assertTrue(json.contains(""""device_name":"Device 1""""))
        assertTrue(json.contains(""""occurred_at":"2026-11-10T10:00:01Z""""))
    }

    @Test
    fun whenEmptyListWrittenThenStoredValueIsCleared() = runTest {
        testee.update { it.copy(pendingLogins = listOf(login(1))) }

        testee.update { it.copy(pendingLogins = emptyList()) }

        assertNull(syncStore.auditLogPending)
        assertTrue(testee.read().pendingLogins.isEmpty())
    }

    @Test
    fun whenMoreThanCapWrittenThenOnlyNewestAreKept() = runTest {
        testee.update { it.copy(pendingLogins = (1..105).map { n -> login(n) }) }

        val stored = testee.read().pendingLogins
        assertEquals(100, stored.size)
        assertEquals(login(6), stored.first())
        assertEquals(login(105), stored.last())
    }

    @Test
    fun whenUpdatesRunConcurrentlyThenNoneIsLost() = runBlocking {
        val store = RealAuditLogCheckStateStore(syncStore, object : DispatcherProvider {})

        (1..50).map { n -> async { store.update { it.copy(pendingLogins = it.pendingLogins + login(n)) } } }.awaitAll()

        assertEquals((1..50).map { login(it) }.toSet(), store.read().pendingLogins.toSet())
    }

    @Test
    fun whenStoredValueIsEmptyStringThenPendingLoginsIsEmpty() = runTest {
        syncStore.auditLogPending = ""

        assertTrue(testee.read().pendingLogins.isEmpty())
    }

    @Test
    fun whenStoredValueIsBadJsonThenPendingLoginsIsEmpty() = runTest {
        syncStore.auditLogPending = "{not json"

        assertTrue(testee.read().pendingLogins.isEmpty())
    }

    @Test
    fun whenStoredValueIsNotAListThenPendingLoginsIsEmpty() = runTest {
        syncStore.auditLogPending = """{"entry_id":"entry-1"}"""

        assertTrue(testee.read().pendingLogins.isEmpty())
    }

    @Test
    fun whenStoredRowHasMissingFieldThenOnlyThatRowIsDropped() = runTest {
        syncStore.auditLogPending =
            """[{"entry_id":"entry-1","device_id":"device-1","occurred_at":"2026-11-10T10:00:01Z"},${row(2)}]"""

        assertEquals(listOf(login(2)), testee.read().pendingLogins)
    }

    @Test
    fun whenStoredRowIsNullThenOnlyThatRowIsDropped() = runTest {
        syncStore.auditLogPending = """[null,${row(2)}]"""

        assertEquals(listOf(login(2)), testee.read().pendingLogins)
    }

    @Test
    fun whenStoredRowHasBadOccurredAtThenOnlyThatRowIsDropped() = runTest {
        syncStore.auditLogPending =
            """[{"entry_id":"entry-1","device_id":"device-1","device_name":"Device 1","occurred_at":"yesterday"},${row(2)}]"""

        assertEquals(listOf(login(2)), testee.read().pendingLogins)
    }

    @Test
    fun whenStoredRowHasUnknownFieldThenItIsIgnored() = runTest {
        syncStore.auditLogPending =
            """[{"entry_id":"entry-1","device_id":"device-1","device_name":"Device 1","occurred_at":"2026-11-10T10:00:01Z","extra":true}]"""

        assertEquals(listOf(login(1)), testee.read().pendingLogins)
    }

    private fun login(n: Int) = PendingLogin(
        entryId = "entry-$n",
        deviceId = "device-$n",
        deviceName = "Device $n",
        occurredAt = Instant.parse("2026-11-10T10:00:00Z").plusSeconds(n.toLong()),
    )

    private fun row(n: Int) =
        """{"entry_id":"entry-$n","device_id":"device-$n","device_name":"Device $n","occurred_at":"${login(n).occurredAt}"}"""

    private class RecordingSyncStore : SyncStore by mock<SyncStore>() {
        val writes: MutableList<String> = mutableListOf()

        @Volatile
        var signedIn = true

        override fun isSignedIn(): Boolean = signedIn

        @Volatile
        override var auditLogCursor: String? = null
            set(value) {
                writes.add(CURSOR)
                field = value
            }

        @Volatile
        override var auditLogPending: String? = null
            set(value) {
                writes.add(PENDING)
                field = value
            }
    }

    private companion object {
        const val CURSOR = "cursor"
        const val PENDING = "pending"
    }
}
