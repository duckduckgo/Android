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

import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckResult.FailureReason
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckState
import com.duckduckgo.sync.impl.auditlog.AuditLogCheckStateStore
import com.duckduckgo.sync.impl.auditlog.AuditLogChecker
import com.duckduckgo.sync.impl.auditlog.LoginAuditLogFeature
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationPermission
import com.duckduckgo.sync.impl.auditlog.NewLoginNotificationSetting
import com.duckduckgo.sync.impl.auditlog.NewLoginNotifier
import com.duckduckgo.sync.impl.auditlog.PendingLogin
import com.duckduckgo.sync.store.SyncStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant

class AuditLogCheckDebugControlsTest {

    private val stateStore = FakeStateStore()
    private val checker = FakeChecker()
    private val syncStore: SyncStore = mock { on { isSignedIn() } doReturn true }

    private val loginAuditLogFeature: LoginAuditLogFeature = mock { onBlocking { isNotificationEnabled() } doReturn true }
    private val notificationSetting: NewLoginNotificationSetting = mock { onBlocking { isEnabled() } doReturn true }
    private val notificationPermission: NewLoginNotificationPermission = mock { onBlocking { canPost() } doReturn true }
    private val newLoginNotifier: NewLoginNotifier = mock()
    private val currentTimeProvider: CurrentTimeProvider = mock { on { currentTimeMillis() } doReturn NOW.toEpochMilli() }

    private val controls = AuditLogCheckDebugControls(
        checker,
        stateStore,
        syncStore,
        loginAuditLogFeature,
        notificationSetting,
        notificationPermission,
        newLoginNotifier,
        currentTimeProvider,
    )

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

    @Test
    fun whenShowTestNotificationThenAddsATestLoginAfterTheOthersAndPostsAsForANewLogin() = runTest {
        stateStore.state = AuditLogCheckState(cursor = "c1", pendingLogins = listOf(LAPTOP))

        val text = controls.showTestNotification()

        val pending = stateStore.state.pendingLogins
        assertEquals(2, pending.size)
        assertEquals(LAPTOP, pending.first())
        assertEquals("Test device", pending.last().deviceName)
        assertEquals(NOW, pending.last().occurredAt)
        assertEquals("c1", stateStore.state.cursor)
        verify(newLoginNotifier).onCheckFinished(AuditLogCheckResult.Checked(newLogins = 1, renamedLogins = 0))
        assertTrue(text, text.startsWith("Test notification posted."))
        assertTrue(text, text.contains("Unseen logins: 2"))
    }

    @Test
    fun whenShowTestNotificationTwiceThenEachTimeAddsItsOwnTestLogin() = runTest {
        controls.showTestNotification()
        controls.showTestNotification()

        val ids = stateStore.state.pendingLogins.map { it.entryId }
        assertEquals(2, ids.distinct().size)
    }

    @Test
    fun whenShowTestNotificationWhileSignedOutThenPostsNothingAndSaysWhy() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(false)

        val text = controls.showTestNotification()

        assertEquals("Not signed in: no notification posted.", text)
        assertEquals(AuditLogCheckState(), stateStore.state)
        verify(newLoginNotifier, never()).onCheckFinished(any())
    }

    @Test
    fun whenShowTestNotificationWithNotificationFlagOffThenPostsNothingAndNamesTheFlags() = runTest {
        whenever(loginAuditLogFeature.isNotificationEnabled()).thenReturn(false)

        val text = controls.showTestNotification()

        assertTrue(text, text.startsWith("No notification posted: turn on both flags, loginAuditLog and loginNotification."))
        assertEquals(AuditLogCheckState(), stateStore.state)
        verify(newLoginNotifier, never()).onCheckFinished(any())
    }

    @Test
    fun whenShowTestNotificationWithSettingOffThenPostsNothingAndSaysWhereToTurnItOn() = runTest {
        whenever(notificationSetting.isEnabled()).thenReturn(false)

        val text = controls.showTestNotification()

        assertTrue(text, text.startsWith("No notification posted: \"Notify on New Connections\" is off in Sync settings."))
        assertEquals(AuditLogCheckState(), stateStore.state)
        verify(newLoginNotifier, never()).onCheckFinished(any())
    }

    @Test
    fun whenShowTestNotificationWithoutNotificationPermissionThenPostsNothingAndSaysWhereToAllowIt() = runTest {
        whenever(notificationPermission.canPost()).thenReturn(false)

        val text = controls.showTestNotification()

        assertTrue(text, text.startsWith("No notification posted: DuckDuckGo can't post this notification."))
        assertTrue(text, text.contains("New synced devices"))
        assertEquals(AuditLogCheckState(), stateStore.state)
        verify(newLoginNotifier, never()).onCheckFinished(any())
    }

    private class FakeStateStore : AuditLogCheckStateStore {
        var state = AuditLogCheckState()

        override suspend fun read(): AuditLogCheckState = state

        override suspend fun update(transform: (AuditLogCheckState) -> AuditLogCheckState?) {
            transform(state)?.let { state = it }
        }

        override fun pendingLogins(): Flow<List<PendingLogin>> = flowOf(state.pendingLogins)
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
        val NOW: Instant = Instant.parse("2026-11-26T12:00:00Z")
        val LAPTOP_AT: Instant = Instant.parse("2026-11-11T10:00:00Z")
        val PHONE_AT: Instant = Instant.parse("2026-11-11T11:00:00Z")
        val LAPTOP = PendingLogin(entryId = "e1", deviceId = "d1", deviceName = "Work laptop", occurredAt = LAPTOP_AT)
        val PHONE = PendingLogin(entryId = "e2", deviceId = "d2", deviceName = "Phone", occurredAt = PHONE_AT)
    }
}
