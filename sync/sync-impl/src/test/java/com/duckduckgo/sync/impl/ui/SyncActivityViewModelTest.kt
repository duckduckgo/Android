/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.settings.api.SettingsPageFeature
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.api.SyncAutoRestore
import com.duckduckgo.sync.api.SyncState
import com.duckduckgo.sync.api.SyncState.IN_PROGRESS
import com.duckduckgo.sync.api.SyncState.OFF
import com.duckduckgo.sync.api.SyncState.READY
import com.duckduckgo.sync.api.SyncStateMonitor
import com.duckduckgo.sync.api.engine.SyncEngine
import com.duckduckgo.sync.api.engine.SyncEngine.SyncTrigger.FEATURE_READ
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskEditDevice
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchLearnMore
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.ShowPreviousSessionReady
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.OriginalFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.CreateAccountFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.SignInFlow
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.lang.String.format
import kotlin.reflect.KClass

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncActivityViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryPDF: RecoveryCodePDF = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val syncStateMonitor: SyncStateMonitor = mock()
    private val syncEngine: SyncEngine = mock()
    private val syncFeatureToggle: SyncFeatureToggle = mock()
    private val syncPixels: SyncPixels = mock()
    private val deviceAuthenticator: DeviceAuthenticator = mock()
    private val syncSetupWideEvent: SyncSetupWideEvent = mock()
    private val syncAutoRestoreManager: SyncAutoRestoreManager = mock()
    private val syncAutoRestore: SyncAutoRestore = mock()

    private val fakeSettingsPageFeature = FakeFeatureToggleFactory.create(SettingsPageFeature::class.java)

    private val stateFlow = MutableStateFlow(SyncState.READY)

    private lateinit var testee: SyncActivityViewModel

    @Before
    fun before() = runTest {
        testee = SyncActivityViewModel(
            syncAccountRepository = syncAccountRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            syncStateMonitor = syncStateMonitor,
            syncEngine = syncEngine,
            recoveryCodePDF = recoveryPDF,
            syncFeatureToggle = syncFeatureToggle,
            settingsPageFeature = fakeSettingsPageFeature,
            syncPixels = syncPixels,
            syncSetupWideEvent = syncSetupWideEvent,
            deviceAuthenticator = deviceAuthenticator,
            syncAutoRestoreManager = syncAutoRestoreManager,
            syncAutoRestore = syncAutoRestore,
            appCoroutineScope = coroutineTestRule.testScope,
        )
        whenever(deviceAuthenticator.isAuthenticationRequired()).thenReturn(true)
        whenever(syncStateMonitor.syncState()).thenReturn(emptyFlow())
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(true)
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(false)
    }

    @Test
    fun whenUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowAccount() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)

            verify(syncEngine).triggerSync(FEATURE_READ)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenLoginQRCodeIsNotNull() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)

            verify(syncEngine).triggerSync(FEATURE_READ)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasMultipleConnectedDevicesThenShowDevices() = runTest {
        givenAuthenticatedUser()

        val connectedDevices = listOf(connectedDevice, connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(connectedDevices.size, initialState.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceThenEmitCommandSyncWithAnotherDevice() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.onSyncWithAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.SyncWithAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceWithoutDeviceAuthenticationThenEmitCommandRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)
        testee.onSyncWithAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.RequestSetupAuthentication::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncWithAnotherDeviceAndCanRestoreThenShowPreviousSessionReady() = runTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.onSyncWithAnotherDevice()

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(OriginalFlow.SYNC_WITH_ANOTHER, (command as ShowPreviousSessionReady).originalFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScanAnotherDeviceQRCodeThenEmitCommandAddAnotherDevice() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.onAddAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.AddAnotherDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScanAnotherDeviceQRCodeWithoutDeviceAuthenticationThenEmitCommandRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)
        testee.onAddAnotherDevice()

        testee.commands().test {
            awaitItem().assertCommandType(Command.RequestSetupAuthentication::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceThenLaunchCreateAccountFlow() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.commands().test {
            testee.onSyncThisDevice()
            awaitItem().assertCommandType(IntroCreateAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceWithoutDeviceAuthenticationThenEmitCommandRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)
        testee.commands().test {
            testee.onSyncThisDevice()
            val command = awaitItem()
            command.assertCommandType(RequestSetupAuthentication::class)
            assertTrue((command as RequestSetupAuthentication).forSyncThisDevice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceAndCanRestoreThenShowPreviousSessionReady() = runTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.commands().test {
            testee.onSyncThisDevice()
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(OriginalFlow.SYNC_THIS_DEVICE, (command as ShowPreviousSessionReady).originalFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceThenOnFlowStartedCalled() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.commands().test {
            testee.onSyncThisDevice()
            awaitItem()
            verify(syncSetupWideEvent).onFlowStarted(source = null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceWithSourceThenOnFlowStartedCalledWithSource() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.commands().test {
            testee.onSyncThisDevice(source = "settings")
            awaitItem()
            verify(syncSetupWideEvent).onFlowStarted(source = "settings")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceWithoutDeviceAuthThenOnDeviceAuthNotEnrolledCalled() = runTest {
        givenUserHasDeviceAuthentication(false)
        testee.commands().test {
            testee.onSyncThisDevice()
            awaitItem()
            verify(syncSetupWideEvent).onFlowStarted(source = null)
            verify(syncSetupWideEvent).onDeviceAuthNotEnrolled()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConnectionCancelledThenOnFlowCancelledCalled() = runTest {
        testee.onConnectionCancelled()

        verify(syncSetupWideEvent).onFlowCancelled()
    }

    @Test
    fun whenSyncThisDeviceThenThisDeviceSyncInProgress() = runTest {
        testee.viewState().test {
            testee.onSyncThisDevice()
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncThisDeviceCanceledThenThisDeviceSyncIdle() = runTest {
        testee.viewState().test {
            testee.onSyncThisDevice()
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)

            testee.onSyncThisDeviceCanceled()
            assertFalse(expectMostRecentItem().isThisDeviceSyncing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverDataThenRecoverDataCommandSent() = runTest {
        givenUserHasDeviceAuthentication(true)
        testee.onRecoverYourSyncedData()

        testee.commands().test {
            awaitItem().assertCommandType(IntroRecoverSyncData::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverDataWithoutDeviceAuthenticationThenEmitCommandRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)
        testee.onRecoverYourSyncedData()

        testee.commands().test {
            awaitItem().assertCommandType(RequestSetupAuthentication::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverSyncedDataAndCanRestoreThenShowPreviousSessionReady() = runTest {
        givenUserHasDeviceAuthentication(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        testee.onRecoverYourSyncedData()

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(ShowPreviousSessionReady::class)
            assertEquals(OriginalFlow.RECOVER_SYNCED_DATA, (command as ShowPreviousSessionReady).originalFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffClickedThenAskTurnOffCommandShown() = runTest {
        givenAuthenticatedUser()

        testee.onTurnOffClicked()

        testee.commands().test {
            awaitItem().assertCommandType(AskTurnOffSync::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncConfirmedThenLogoutLocalDevice() = runTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        verify(syncAccountRepository).logout(deviceId)
    }

    @Test
    fun whenTurnOffSyncConfirmedThenPixelFired() = runTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        verify(syncPixels).fireUserConfirmedToTurnOffSync()
    }

    @Test
    fun whenTurnOffSyncConfirmedFailsThenErrorShown() = runTest {
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.commands().test {
            testee.onTurnOffSyncConfirmed(connectedDevice)

            awaitItem().assertCommandType(Command.ShowError::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutSuccessThenUpdateViewState() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            testee.onTurnOffSyncConfirmed(connectedDevice)
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutErrorThenUpdateViewState() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onTurnOffSyncCancelled()
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountClickedThenAskDeleteAccount() = runTest {
        testee.onDeleteAccountClicked()

        testee.commands().test {
            awaitItem().assertCommandType(Command.AskDeleteAccount::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountSuccessThenUpdateViewState() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Success(true))

        testee.viewState().test {
            var viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            testee.onDeleteAccountConfirmed()
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountErrorThenUpdateViewState() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Error(reason = "error"))

        testee.onDeleteAccountConfirmed()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountConfirmedThenDeleteAccount() = runTest {
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))
        whenever(syncAccountRepository.deleteAccount()).thenReturn(Result.Success(true))

        testee.onDeleteAccountConfirmed()

        verify(syncAccountRepository).deleteAccount()
    }

    @Test
    fun whenDeleteAccountCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onDeleteAccountCancelled()
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceClickedThenAskRemoveDevice() = runTest {
        testee.onRemoveDeviceClicked(connectedDevice)

        testee.commands().test {
            awaitItem().assertCommandType(Command.AskRemoveDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditDeviceClickedWithoutRequireAuthThenAskEditDeviceWithoutAuthentication() = runTest {
        testee.onEditDeviceClicked(connectedDevice, requireAuth = false)

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(AskEditDevice::class)
            assertEquals(connectedDevice, (command as AskEditDevice).device)
            assertFalse(command.requireAuthentication)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditDeviceClickedWithoutRequireAuthThenNoAuthenticationRequested() = runTest {
        givenUserHasDeviceAuthentication(false)

        testee.onEditDeviceClicked(connectedDevice, requireAuth = false)

        testee.commands().test {
            awaitItem().assertCommandType(AskEditDevice::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditDeviceClickedWithRequireAuthAndDeviceAuthenticationThenAskEditDeviceWithAuthentication() = runTest {
        givenUserHasDeviceAuthentication(true)

        testee.onEditDeviceClicked(connectedDevice, requireAuth = true)

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(AskEditDevice::class)
            assertEquals(connectedDevice, (command as AskEditDevice).device)
            assertTrue(command.requireAuthentication)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnEditDeviceClickedWithRequireAuthWithoutDeviceAuthenticationThenRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)

        testee.onEditDeviceClicked(connectedDevice, requireAuth = true)

        testee.commands().test {
            val command = awaitItem()
            command.assertCommandType(RequestSetupAuthentication::class)
            assertFalse((command as RequestSetupAuthentication).forSyncThisDevice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceConfirmedThenRemoveDevice() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository).logout(deviceId)
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenDeviceIsRemovedWithoutRefetch() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository, never()).getConnectedDevices()
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenReturnUpdateDevices() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            testee.onRemoveDeviceConfirmed(connectedDevice)
            awaitItem = expectMostRecentItem()
            assertEquals(0, awaitItem.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenStaleDeviceFetchCompletesAfterRemovalThenRemovedDeviceDoesNotReappear() = runTest {
        givenAuthenticatedUser()

        val otherDevice = connectedDevice.copy(thisDevice = false, deviceId = "otherDeviceId")
        whenever(syncAccountRepository.logout(otherDevice.deviceId)).thenReturn(Result.Success(true))
        var fetchCount = 0
        whenever(syncAccountRepository.getConnectedDevices()).thenAnswer {
            fetchCount++
            if (fetchCount == 2) {
                // this fetch read the server before the removal: the user confirms the removal
                // while it is in flight, so its stale result lands afterwards
                testee.onRemoveDeviceConfirmed(otherDevice)
            }
            Result.Success(listOf(connectedDevice, otherDevice))
        }

        testee.viewState().test {
            assertEquals(2, expectMostRecentItem().syncedDevices.size)

            testee.onDevicesUpdated()

            val devices = expectMostRecentItem().syncedDevices.filterIsInstance<SyncedDevice>()
            assertEquals(listOf(deviceId), devices.map { it.device.deviceId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceFailsThenDeviceRemainsInList() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onRemoveDeviceConfirmed(connectedDevice)
            val awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            assertFalse((awaitItem.syncedDevices.first() as SyncedDevice).loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceEditedThenUpdateDevice() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = expectMostRecentItem()
            val newDevice = connectedDevice.copy(deviceName = "newDevice")
            whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(listOf(newDevice)))
            testee.onDeviceEdited(newDevice)
            awaitItem = expectMostRecentItem()
            assertNotNull(awaitItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == newDevice.deviceName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceEditedFailsThenDeviceRemainsInList() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.renameDevice(any())).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onDeviceEdited(connectedDevice.copy(deviceName = "newDevice"))
            val awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            assertFalse((awaitItem.syncedDevices.first() as SyncedDevice).loading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeThenEmitCheckIfUserHasPermissionCommand() = runTest {
        givenUserHasDeviceAuthentication(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is CheckIfUserHasStoragePermission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeWithoutDeviceAuthenticationThenEmitCommandRequestSetupAuthentication() = runTest {
        givenUserHasDeviceAuthentication(false)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is RequestSetupAuthentication)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateRecoveryCodeThenGenerateFileAndEmitSuccessCommand() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(recoveryPDF.generateAndStoreRecoveryCodePDF(any(), eq(authCodeToUse.rawCode))).thenReturn(TestSyncFixtures.pdfFile())

        testee.commands().test {
            testee.generateRecoveryCode(mock())
            val command = awaitItem()
            assertTrue(command is RecoveryCodePDFSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceConnectedThenFetchRemoteDevices() = runTest {
        givenAuthenticatedUser()

        val connectedDevices = listOf(connectedDevice, connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        testee.viewState().test {
            testee.onSyncThisDevice()
            assertTrue(expectMostRecentItem().isThisDeviceSyncing)

            testee.onDeviceConnected()

            val initialState = expectMostRecentItem()
            assertEquals(connectedDevices.size, initialState.syncedDevices.size)
            assertFalse(initialState.isThisDeviceSyncing)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDevicesUpdatedThenViewStateReflectsRefreshedDevices() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()

            val updatedDevice = connectedDevice.copy(deviceName = "updatedDevice")
            whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Success(listOf(updatedDevice)))
            testee.onDevicesUpdated()
            val refreshedItem = expectMostRecentItem()
            assertNotNull(refreshedItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == updatedDevice.deviceName })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsDisabledThenViewStateChanges() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, true)
            stateFlow.value = OFF
            val updatedState = awaitItem()
            assertEquals(updatedState.showAccount, false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsEnabledThenViewStateChanges() = runTest {
        givenAuthenticatedUser()
        stateFlow.value = SyncState.OFF

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, false)
            stateFlow.value = READY
            val updatedState = awaitItem()
            assertEquals(updatedState.showAccount, true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateIsInProgressEnabledThenViewStateDoesNotChange() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val initialState = expectMostRecentItem()
            assertEquals(initialState.showAccount, true)
            stateFlow.value = IN_PROGRESS
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedAndSetupFlowsDisabledThenAllSetupFlowsDisabledViewState() = runTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedAndCreateAccountDisabledThenOnlySignInFlowDisabledViewState() = runTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSetupFlowsDisabledThenAllSetupFlowsDisabledViewState() = runTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(false)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(true)

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateAccountDisabledThenOnlySignInFlowDisabledViewState() = runTest {
        whenever(syncFeatureToggle.allowSetupFlows()).thenReturn(true)
        whenever(syncFeatureToggle.allowCreateAccount()).thenReturn(false)

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.disabledSetupFlows.contains(SignInFlow))
            assertTrue(viewState.disabledSetupFlows.contains(CreateAccountFlow))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncNotSupportedThenEmitCommandShowDeviceUnsupported() = runTest {
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(false)

        testee.commands().test {
            awaitItem().assertCommandType(Command.ShowDeviceUnsupported::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLearnMoreClickedThenEmitLaunchLearnMoreCommand() = runTest {
        testee.onLearnMoreClicked()

        testee.commands().test {
            awaitItem().assertCommandType(LaunchLearnMore::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLearnMoreClickedThenCommandContainsCorrectUrl() = runTest {
        testee.onLearnMoreClicked()

        testee.commands().test {
            val command = awaitItem() as LaunchLearnMore
            assertEquals(
                "https://duckduckgo.com/duckduckgo-help-pages/sync-and-backup/recovery-codes-and-troubleshooting#data-expiration",
                command.url,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenClickedToGetAppOnOtherPlatformsClickedInEnabledStateThenEmitCommand() = runTest {
        testee.onGetOnOtherPlatformsClickedWhenSyncEnabled()
        testee.commands().test {
            awaitItem().also {
                assertEquals("activated", (it as LaunchSyncGetOnOtherPlatforms).source.value)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenClickedToGetAppOnOtherPlatformsClickedInDisabledStateThenEmitCommand() = runTest {
        testee.onGetOnOtherPlatformsClickedWhenSyncDisabled()
        testee.commands().test {
            awaitItem().also {
                assertEquals("not_activated", (it as LaunchSyncGetOnOtherPlatforms).source.value)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureEnabledAndSignedOutThenViewStateShowsNewDesktopBrowserSetting() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureDisabledAndSignedOutThenViewStateDoesNotShowNewDesktopBrowserSetting() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureEnabledAndSignedInThenViewStateShowsNewDesktopBrowserSetting() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(true))
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDesktopBrowserFeatureDisabledAndSignedInThenViewStateDoesNotShowNewDesktopBrowserSetting() = runTest {
        fakeSettingsPageFeature.newDesktopBrowserSettingEnabled().setRawStoredState(State(false))
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.newDesktopBrowserSettingEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedOutThenAutoRestoreToggleIsHidden() = runTest {
        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreAvailableThenViewStateShowsToggle() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreNotAvailableThenViewStateHidesToggle() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.showAutoRestoreToggle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreEnabledThenViewStateReflectsEnabled() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertTrue(viewState.autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreDisabledThenViewStateReflectsDisabled() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = expectMostRecentItem()
            assertFalse(viewState.autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestoreToggleChangedThenUpdatesViewStateOnly() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            val updatedState = awaitItem()
            assertFalse(updatedState.autoRestoreEnabled)
            verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
            verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSyncStateReEmitsAfterToggleChangedThenPendingToggleStatePreserved() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            assertFalse(awaitItem().autoRestoreEnabled)

            // Sync state re-emits (e.g. a background sync completes) — toggle should not revert.
            // MutableStateFlow only emits when value changes, so if toggle is correctly preserved
            // (state unchanged) we expect no new emission.
            stateFlow.value = IN_PROGRESS
            stateFlow.value = READY
            advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenExitsWithAutoRestoreEnabledThenSavesPayloadAndSetsPreference() = runTest {
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "rawCode")
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager).saveAutoRestoreData(eq("rawCode"), anyOrNull())
    }

    @Test
    fun whenScreenExitsWithAutoRestoreEnabledButGetRecoveryCodeFailsThenPreferenceNotWritten() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
    }

    @Test
    fun whenScreenExitsWithAutoRestoreDisabledThenClearsPayloadAndSetsPreference() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsTwiceWithAutoRestoreEnabledThenSavesPayloadOnlyOnce() = runTest {
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "rawCode")
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()
        testee.onScreenExit()

        verify(syncAutoRestoreManager, times(1)).saveAutoRestoreData(eq("rawCode"), anyOrNull())
    }

    @Test
    fun whenScreenExitsTwiceWithAutoRestoreDisabledThenClearsPayloadOnlyOnce() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()
        testee.onScreenExit()

        verify(syncAutoRestoreManager, times(1)).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsAndFeatureWasUnavailableThenNoStorageOperations() = runTest {
        // Feature unavailable at load time — autoRestoreAvailable captured as false
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
        verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
    }

    @Test
    fun whenScreenExitsWithNoNetChangeToToggleThenNoStorageOperations() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            expectMostRecentItem()
            testee.onAutoRestoreToggleChanged(true)
            awaitItem()
            testee.onAutoRestoreToggleChanged(false)
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onScreenExit()

        verify(syncAutoRestoreManager, never()).saveAutoRestoreData(any(), anyOrNull())
        verify(syncAutoRestoreManager, never()).clearAutoRestoreData()
    }

    @Test
    fun whenSignedOutThenSignedBackInThenAutoRestoreToggleIsVisible() = runTest {
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        givenAuthenticatedUser()

        testee.viewState().test {
            val signedInState = expectMostRecentItem()
            assertTrue(signedInState.showAutoRestoreToggle)

            stateFlow.value = OFF
            val signedOutState = awaitItem()
            assertFalse(signedOutState.showAutoRestoreToggle)

            stateFlow.value = READY
            val reSignedInState = awaitItem()
            assertTrue(reSignedInState.showAutoRestoreToggle)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenAutoRestorePreferenceWrittenDuringSetupThenResubscribingToViewStateShowsCorrectToggleState() = runTest {
        // Simulate the race: account created (signed-in state fires) before the setup screen writes
        // the preference. The first viewState() collection reads 'false' from DataStore.
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(false)
        givenAuthenticatedUser()

        testee.viewState().test {
            assertFalse(expectMostRecentItem().autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }

        // Setup flow now writes 'true' to DataStore (user allowed auto-restore).
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        // SyncActivity returns to foreground — viewState() is re-subscribed.
        testee.viewState().test {
            assertTrue(expectMostRecentItem().autoRestoreEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV1ExchangeUrlThenAskSetupSyncDeepLinkIsSent() = runTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "code").asUrl()
        whenever(syncAccountRepository.parseSyncAuthCode(url)).thenReturn(SyncAuthCode.Exchange(mock()))

        testee.commands().test {
            testee.processSetupDeepLink(url)
            awaitItem().assertCommandType(Command.AskSetupSyncDeepLink::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV1ConnectUrlThenNoCommandIsSent() = runTest {
        val url = SyncBarcodeUrl(webSafeB64EncodedCode = "code").asUrl()
        whenever(syncAccountRepository.parseSyncAuthCode(url)).thenReturn(SyncAuthCode.Unknown("code"))

        testee.commands().test {
            testee.processSetupDeepLink(url)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithV2UrlAndDeviceAuthEnrolledThenDeepLinkIntoSetupIsSent() = runTest {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(true)
        val url = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            protocolVersion = SyncBarcodeUrl.ProtocolVersion.V2,
        ).asUrl()

        testee.commands().test {
            testee.processSetupDeepLink(url)
            awaitItem().assertCommandType(Command.DeepLinkIntoSetup::class)
            cancelAndIgnoreRemainingEvents()
        }

        verify(syncAccountRepository, never()).parseSyncAuthCode(any())
    }

    @Test
    fun whenProcessSetupDeepLinkWithV2UrlAndDeviceAuthNotEnrolledThenRequestSetupAuthenticationIsSent() = runTest {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(false)
        val url = SyncBarcodeUrl(
            webSafeB64EncodedCode = "code",
            protocolVersion = SyncBarcodeUrl.ProtocolVersion.V2,
        ).asUrl()

        testee.commands().test {
            testee.processSetupDeepLink(url)
            awaitItem().assertCommandType(RequestSetupAuthentication::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessSetupDeepLinkWithMalformedUrlThenNoCommandIsSent() = runTest {
        testee.commands().test {
            testee.processSetupDeepLink("not-a-sync-url")
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }

    private fun givenAuthenticatedUser() {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncStateMonitor.syncState()).thenReturn(stateFlow.asStateFlow())
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAccountRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Success(listOf(connectedDevice)))
    }

    private fun givenUserHasDeviceAuthentication(hasDeviceAuthentication: Boolean) {
        whenever(deviceAuthenticator.hasValidDeviceAuthentication()).thenReturn(hasDeviceAuthentication)
    }
}
