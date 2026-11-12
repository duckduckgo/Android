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

package com.duckduckgo.sync.internal.ui

import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult.FailureReason
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckState
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.AuditLogChecker
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant

class AuditLogCheckDebugControlsTest {

    private val stateStore = FakeStateStore()
    private val checker = FakeChecker()
    private val syncStore: SyncStore = mock { on { isSignedIn() } doReturn true }

    private val controls = AuditLogCheckDebugControls(checker, stateStore, syncStore)

    @Test
    fun whenNoUnseenLoginsThenCountIsZero() = runTest {
        assertEquals("Unseen logins: 0", controls.unseenCount())
    }

    @Test
    fun whenUnseenLoginsStoredThenCountShowsHowMany() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP, PHONE))

        assertEquals("Unseen logins: 2", controls.unseenCount())
    }

    @Test
    fun whenFindFindsANewLoginThenSaysSoAndListsAllUnseenWithTheNewOneMarked() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP))
        checker.onCheck = {
            stateStore.state = AuditLogCheckState(cursor = "c2", pendingLogins = listOf(LAPTOP, PHONE))
            AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0)
        }

        val text = controls.findUnseen()

        assertEquals(1, checker.checkCount)
        assertTrue(text, text.startsWith("Found 1 new login from other devices."))
        assertTrue(text, text.contains("Unseen logins: 2"))
        assertTrue(text, text.contains("$LAPTOP_AT\n  name: Work laptop\n  entry: e1\n  device: d1"))
        assertTrue(text, text.contains("$PHONE_AT  NEW\n  name: Phone"))
        assertFalse(text, text.contains("$LAPTOP_AT  NEW"))
        assertTrue(text, text.indexOf("Phone") < text.indexOf("Work laptop"))
    }

    @Test
    fun whenFindSeesLogoutsThenSaysHowManyAndWhereToSeeThem() = runTest {
        checker.onCheck = { AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0, logouts = 2) }

        val text = controls.findUnseen()

        assertTrue(text, text.startsWith("Found 0 new logins from other devices. Also saw 2 logouts: tap Read server log to see them."))
    }

    @Test
    fun whenFindSeesNoLogoutsThenDoesNotMentionThem() = runTest {
        checker.onCheck = { AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0, logouts = 0) }

        val text = controls.findUnseen()

        assertFalse(text, text.contains("logout"))
    }

    @Test
    fun whenFindRenamesALoginThenSaysSo() = runTest {
        checker.onCheck = { AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 1) }

        val text = controls.findUnseen()

        assertTrue(text, text.startsWith("Found 0 new logins from other devices. Updated the name of 1."))
    }

    @Test
    fun whenFirstCheckForTheAccountThenExplainsThatOlderEntriesAreNotCounted() = runTest {
        checker.onCheck = { AuditLogCheckResult.Initialised }

        val text = controls.findUnseen()

        assertTrue(text, text.startsWith("First check for this account"))
        assertTrue(text, text.contains("Unseen logins: 0"))
    }

    @Test
    fun whenFlagsOffThenSaysWhichFlagsToTurnOn() = runTest {
        checker.onCheck = { AuditLogCheckResult.Disabled }

        val text = controls.findUnseen()

        assertTrue(text, text.contains("loginAuditLog"))
        assertTrue(text, text.contains("loginNotification"))
    }

    @Test
    fun whenACheckIsAlreadyRunningThenSaysSo() = runTest {
        checker.onCheck = { AuditLogCheckResult.AlreadyRunning }

        val text = controls.findUnseen()

        assertTrue(text, text.startsWith("A check is already running"))
    }

    @Test
    fun whenCheckFailsThenShowsTheReason() = runTest {
        checker.onCheck = { AuditLogCheckResult.Failed(FailureReason.NETWORK) }

        val text = controls.findUnseen()

        assertTrue(text, text.startsWith("Check failed: NETWORK"))
    }

    @Test
    fun whenMarkAllAsSeenThenEmptiesUnseenListAndKeepsTheCursor() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP, PHONE))

        val text = controls.markAllAsSeen()

        assertEquals(AuditLogCheckState(cursor = "c1"), stateStore.state)
        assertTrue(text, text.startsWith("Marked 2 as seen"))
        assertTrue(text, text.contains("Unseen logins: 0"))
    }

    @Test
    fun whenMarkAllAsSeenWhileSignedOutThenChangesNothing() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(false)
        stateStore.state = AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP))

        val text = controls.markAllAsSeen()

        assertTrue(text, text.startsWith("Not signed in"))
        assertEquals(AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP)), stateStore.state)
    }

    private class FakeStateStore : AuditLogCheckStateStore {
        var state = AuditLogCheckState()

        override suspend fun read(): AuditLogCheckState = state

        override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) {
            transform(state)?.let { state = it }
        }
    }

    private class FakeChecker : AuditLogChecker {
        var checkCount = 0
            private set
        var onCheck: () -> AuditLogCheckResult = { AuditLogCheckResult.Checked(newLogins = 0, renamedLogins = 0) }

        override suspend fun check(): AuditLogCheckResult {
            checkCount++
            return onCheck()
        }
    }

    private companion object {
        val LAPTOP_AT: Instant = Instant.parse("2026-11-11T10:00:00Z")
        val PHONE_AT: Instant = Instant.parse("2026-11-11T11:00:00Z")
        val LAPTOP = PendingLogin(entryId = "e1", deviceId = "d1", deviceName = "Work laptop", occurredAt = LAPTOP_AT)
        val PHONE = PendingLogin(entryId = "e2", deviceId = "d2", deviceName = "Phone", occurredAt = PHONE_AT)
    }
}
