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

package com.duckduckgo.sync.internal.debug

import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.engine.RealSyncScheduler
import com.duckduckgo.sync.impl.engine.SyncStateRepository
import com.duckduckgo.sync.impl.internal.SyncInternalEnvDataStore
import com.duckduckgo.sync.impl.triggers.SyncBackgroundWorker
import com.duckduckgo.sync.internal.ui.AuditLogCheckDebugControls
import com.duckduckgo.sync.internal.ui.AuditLogDebugReport
import com.duckduckgo.sync.internal.ui.OtherDevices
import com.duckduckgo.sync.internal.ui.OtherDevicesDebugTool
import com.duckduckgo.sync.store.SyncStore
import com.duckduckgo.sync.store.model.SyncAttempt
import com.duckduckgo.sync.store.model.SyncAttemptState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Duration
import java.time.OffsetDateTime

class SyncDebugCommandsTest {

    private val syncStore: SyncStore = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val envDataStore = FakeEnvDataStore()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val otherDevicesDebugTool: OtherDevicesDebugTool = mock()
    private val auditLogDebugReport: AuditLogDebugReport = mock()
    private val auditLogCheckDebugControls: AuditLogCheckDebugControls = mock()
    private val workManager: WorkManager = mock()
    private val syncStateRepository: SyncStateRepository = mock()

    private val commands = SyncDebugCommands(
        syncStore,
        syncFeature,
        envDataStore,
        syncAccountRepository,
        otherDevicesDebugTool,
        auditLogDebugReport,
        auditLogCheckDebugControls,
        workManager,
        syncStateRepository,
    )

    @Test
    fun whenCommandUnknownThenListsKnownCommands() = runTest {
        val result = commands.run("nope", on = true)

        assertTrue(result.startsWith("unknown command \"nope\""))
        SyncDebugCommands.COMMANDS.forEach { assertTrue(result.contains(it)) }
    }

    @Test
    fun whenStatusThenReportsSignInEnvironmentFlagsAndUnseenCount() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(auditLogCheckDebugControls.unseenCount()).thenReturn("Unseen logins: 2")
        commands.run(SyncDebugCommands.SET_FLAGS, on = true)

        val result = commands.run(SyncDebugCommands.STATUS, on = true)

        assertEquals(
            "signedIn=true environment=$PROD loginAuditLog=true loginNotification=true Unseen logins: 2",
            result,
        )
    }

    @Test
    fun whenUseStagingOnThenSwitchesToStaging() = runTest {
        val result = commands.run(SyncDebugCommands.USE_STAGING, on = true)

        assertTrue(envDataStore.useSyncDevEnvironment)
        assertEquals("environment=$STAGING", result)
    }

    @Test
    fun whenUseStagingOffThenSwitchesToProduction() = runTest {
        envDataStore.useSyncDevEnvironment = true

        val result = commands.run(SyncDebugCommands.USE_STAGING, on = false)

        assertFalse(envDataStore.useSyncDevEnvironment)
        assertEquals("environment=$PROD", result)
    }

    @Test
    fun whenSetFlagsOnThenBothAuditLogFlagsAreOn() = runTest {
        commands.run(SyncDebugCommands.SET_FLAGS, on = true)

        assertTrue(syncFeature.loginAuditLog().isEnabled())
        assertTrue(syncFeature.loginNotification().isEnabled())
    }

    @Test
    fun whenSetFlagsOffThenBothAuditLogFlagsAreOff() = runTest {
        commands.run(SyncDebugCommands.SET_FLAGS, on = true)

        commands.run(SyncDebugCommands.SET_FLAGS, on = false)

        assertFalse(syncFeature.loginAuditLog().isEnabled())
        assertFalse(syncFeature.loginNotification().isEnabled())
    }

    @Test
    fun whenSimulateLoginThenReturnsToolResult() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(otherDevicesDebugTool.simulateLogin()).thenReturn("Logged in \"Fake device\"")

        assertEquals("Logged in \"Fake device\"", commands.run(SyncDebugCommands.SIMULATE_LOGIN, on = true))
    }

    @Test
    fun whenLogOutOthersWithNoOtherDevicesThenSaysSo() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(otherDevicesDebugTool.otherDevices()).thenReturn(OtherDevices(devices = emptyList()))

        assertEquals("no other devices", commands.run(SyncDebugCommands.LOG_OUT_OTHERS, on = true))
    }

    @Test
    fun whenLogOutOthersThenLogsOutEachOtherDevice() = runTest {
        envDataStore.useSyncDevEnvironment = true
        val first = device("a")
        val second = device("b")
        whenever(otherDevicesDebugTool.otherDevices()).thenReturn(OtherDevices(devices = listOf(first, second)))
        whenever(otherDevicesDebugTool.logOut(first)).thenReturn("Logged out \"a\"")
        whenever(otherDevicesDebugTool.logOut(second)).thenReturn("Logged out \"b\"")

        assertEquals("Logged out \"a\"\nLogged out \"b\"", commands.run(SyncDebugCommands.LOG_OUT_OTHERS, on = true))
    }

    @Test
    fun whenLogOutOthersCannotListDevicesThenReturnsTheError() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(otherDevicesDebugTool.otherDevices()).thenReturn(OtherDevices(devices = emptyList(), error = "Could not get the device list"))

        assertEquals("Could not get the device list", commands.run(SyncDebugCommands.LOG_OUT_OTHERS, on = true))
        verify(otherDevicesDebugTool, never()).logOut(any())
    }

    @Test
    fun whenReadLogSignedOutThenNotSignedIn() = runTest {
        whenever(syncStore.token).thenReturn(null)

        assertEquals(SyncDebugCommands.NOT_SIGNED_IN, commands.run(SyncDebugCommands.READ_LOG, on = true))
    }

    @Test
    fun whenReadLogSignedInThenReturnsReport() = runTest {
        whenever(syncStore.token).thenReturn("token")
        whenever(syncStore.deviceId).thenReturn("device")
        whenever(auditLogDebugReport.fetchAndDescribe("token", "device")).thenReturn("2 entries")

        assertEquals("2 entries", commands.run(SyncDebugCommands.READ_LOG, on = true))
    }

    @Test
    fun whenDeleteAccountSignedOutThenDoesNotCallRepository() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(syncStore.isSignedIn()).thenReturn(false)

        assertEquals(SyncDebugCommands.NOT_SIGNED_IN, commands.run(SyncDebugCommands.DELETE_ACCOUNT, on = true))
        verify(syncAccountRepository, never()).deleteAccount()
    }

    @Test
    fun whenDeleteAccountSucceedsThenSaysDeleted() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Success(true))

        assertEquals("account deleted", commands.run(SyncDebugCommands.DELETE_ACCOUNT, on = true))
    }

    @Test
    fun whenDeleteAccountFailsThenReportsReasonAndCode() = runTest {
        envDataStore.useSyncDevEnvironment = true
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Error(code = 500, reason = "server"))

        assertEquals("delete failed: server (code 500)", commands.run(SyncDebugCommands.DELETE_ACCOUNT, on = true))
    }

    @Test
    fun whenRunBackgroundSyncSignedOutThenEnqueuesNothing() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(false)

        assertEquals(SyncDebugCommands.NOT_SIGNED_IN, commands.run(SyncDebugCommands.RUN_BACKGROUND_SYNC, on = true))
        verify(workManager, never()).enqueue(any<WorkRequest>())
    }

    @Test
    fun whenRunBackgroundSyncSignedInThenEnqueuesTheBackgroundWorker() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(true)

        val result = commands.run(SyncDebugCommands.RUN_BACKGROUND_SYNC, on = true)

        val request = argumentCaptor<WorkRequest>()
        verify(workManager).enqueue(request.capture())
        assertEquals(SyncBackgroundWorker::class.java.name, request.firstValue.workSpec.workerClassName)
        assertEquals("enqueued SyncBackgroundWorker", result)
    }

    @Test
    fun whenDestructiveCommandOnProductionThenRefusedAndNothingRuns() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(true)

        SyncDebugCommands.STAGING_ONLY.forEach { command ->
            assertEquals(
                "refused: $command only runs on staging; send ${SyncDebugCommands.USE_STAGING} first",
                commands.run(command, on = true),
            )
        }
        verify(syncAccountRepository, never()).deleteAccount()
        verify(otherDevicesDebugTool, never()).simulateLogin()
        verify(otherDevicesDebugTool, never()).otherDevices()
    }

    @Test
    fun whenOnProductionThenReadOnlyAndSetupCommandsStillRun() = runTest {
        whenever(syncStore.isSignedIn()).thenReturn(true)
        whenever(auditLogCheckDebugControls.unseenCount()).thenReturn("Unseen logins: 0")

        assertTrue(commands.run(SyncDebugCommands.STATUS, on = true).startsWith("signedIn=true"))
        assertEquals("enqueued SyncBackgroundWorker", commands.run(SyncDebugCommands.RUN_BACKGROUND_SYNC, on = true))
    }

    @Test
    fun whenExpireSyncDebounceWithNoAttemptThenStoresNothing() = runTest {
        whenever(syncStateRepository.current()).thenReturn(null)

        assertEquals("no sync attempt stored", commands.run(SyncDebugCommands.EXPIRE_SYNC_DEBOUNCE, on = true))
        verify(syncStateRepository, never()).store(any())
    }

    @Test
    fun whenExpireSyncDebounceThenSameAttemptMovesPastTheDebouncePeriod() = runTest {
        val last = SyncAttempt(id = 7, state = SyncAttemptState.SUCCESS, meta = "meta")
        whenever(syncStateRepository.current()).thenReturn(last)

        commands.run(SyncDebugCommands.EXPIRE_SYNC_DEBOUNCE, on = true)

        val stored = argumentCaptor<SyncAttempt>()
        verify(syncStateRepository).store(stored.capture())
        assertEquals(last.copy(timestamp = stored.firstValue.timestamp), stored.firstValue)
        val minutesAgo = Duration.between(OffsetDateTime.parse(stored.firstValue.timestamp), OffsetDateTime.now()).toMinutes()
        assertTrue(minutesAgo > RealSyncScheduler.DEBOUNCE_PERIOD_IN_MINUTES)
    }

    private fun device(name: String) = ConnectedDevice(deviceName = name, deviceId = name, deviceType = DeviceType())

    private class FakeEnvDataStore : SyncInternalEnvDataStore {
        override var syncEnvironmentUrl: String = PROD
        override var useSyncDevEnvironment: Boolean = false
            set(value) {
                field = value
                syncEnvironmentUrl = if (value) STAGING else PROD
            }
    }

    private companion object {
        const val PROD = "https://sync.duckduckgo.com"
        const val STAGING = "https://sync-staging.duckduckgo.com"
    }
}
