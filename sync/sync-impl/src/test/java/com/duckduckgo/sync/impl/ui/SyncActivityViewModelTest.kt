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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.qrBitmap
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchDeviceSetupFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import java.lang.String.format
import kotlin.reflect.KClass
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SyncActivityViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val qrEncoder: QREncoder = mock()
    private val recoveryPDF: RecoveryCodePDF = mock()
    private val syncRepository: SyncRepository = mock()
    lateinit var isSignedInFlow: MutableStateFlow<Boolean>

    private val testee = SyncActivityViewModel(
        qrEncoder = qrEncoder,
        syncRepository = syncRepository,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        recoveryCodePDF = recoveryPDF,
    )

    @Test
    fun whenUserSignedInThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowAccount() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenLoginQRCodeIsNotNull() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.loginQRCode != null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserHasMultipleConnectedDevicesThenShowDevices() = runTest {
        givenAuthenticatedUser()
        val connectedDevices = listOf(connectedDevice, connectedDevice)
        whenever(syncRepository.getConnectedDevices()).thenReturn(Result.Success(connectedDevices))

        testee.viewState().test {
            val initialState = awaitItem()
            assertEquals(1, initialState.syncedDevices.size)
            val fetchViewState = awaitItem()
            assertEquals(connectedDevices.size, fetchViewState.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleDisabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(false)

        testee.viewState().test {
            val viewState = awaitItem()
            assertFalse(viewState.syncToggleState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleEnabledThenLaunchSetupFlow() = runTest {
        testee.onToggleClicked(true)

        testee.commands().test {
            awaitItem().assertCommandType(LaunchDeviceSetupFlow::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleDisabledThenAskTurnOffSync() = runTest {
        givenAuthenticatedUser()

        testee.onToggleClicked(false)

        testee.commands().test {
            awaitItem().assertCommandType(Command.AskTurnOffSync::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncConfirmedThenLogoutLocalDevice() = runTest {
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        verify(syncRepository).logout(deviceId)
    }

    @Test
    fun whenLogoutSuccessThenUpdateViewState() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true)).also {
            isSignedInFlow.emit(false)
        }

        testee.viewState().test {
            var viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
            testee.onTurnOffSyncConfirmed(connectedDevice)
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            assertFalse(viewState.syncToggleState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLogoutErrorThenUpdateViewState() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.onTurnOffSyncConfirmed(connectedDevice)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTurnOffSyncCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onTurnOffSyncCancelled()
            val viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
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
        whenever(syncRepository.deleteAccount()).thenReturn(Result.Success(true)).also {
            isSignedInFlow.emit(false)
        }

        testee.viewState().test {
            var viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
            testee.onDeleteAccountConfirmed()
            viewState = awaitItem()
            assertFalse(viewState.showAccount)
            assertFalse(viewState.syncToggleState)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountErrorThenUpdateViewState() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.deleteAccount()).thenReturn(Result.Error(reason = "error"))

        testee.onDeleteAccountConfirmed()

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
            assertTrue(viewState.showAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenDeleteAccountConfirmedThenDeleteAccount() = runTest {
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onDeleteAccountConfirmed()

        verify(syncRepository).deleteAccount()
    }

    @Test
    fun whenDeleteAccountCancelledThenDeviceSyncViewStateIsEnabled() = runTest {
        givenAuthenticatedUser()

        testee.viewState().test {
            testee.onDeleteAccountCancelled()
            val viewState = awaitItem()
            assertTrue(viewState.syncToggleState)
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
    fun whenOnRemoveDeviceConfirmedThenRemoveDevice() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncRepository).logout(deviceId)
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenFetchRemoteDevices() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncRepository).getConnectedDevices()
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenReturnUpdateDevices() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = awaitItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            whenever(syncRepository.getConnectedDevices()).thenReturn(Result.Success(listOf()))
            testee.onRemoveDeviceConfirmed(connectedDevice)
            awaitItem = awaitItem()
            assertEquals(0, awaitItem.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceFailsThenRestorePreviousList() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onRemoveDeviceConfirmed(connectedDevice)
            val awaitItem = awaitItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnDeviceEditedThenUpdateDevice() = runTest {
        givenAuthenticatedUser()
        whenever(syncRepository.renameDevice(any())).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = awaitItem()
            val newDevice = connectedDevice.copy(deviceName = "newDevice")
            whenever(syncRepository.getConnectedDevices()).thenReturn(Result.Success(listOf(newDevice)))
            testee.onDeviceEdited(newDevice)
            awaitItem = awaitItem()
            assertNotNull(awaitItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == newDevice.deviceName })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeThenEmitCheckIfUserHasPermissionCommand() = runTest {
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is CheckIfUserHasStoragePermission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateRecoveryCodeThenGenerateFileAndEmitSuccessCommand() = runTest {
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(recoveryPDF.generateAndStoreRecoveryCodePDF(any(), eq(jsonRecoveryKeyEncoded))).thenReturn(TestSyncFixtures.pdfFile())

        testee.commands().test {
            testee.generateRecoveryCode(mock())
            val command = awaitItem()
            assertTrue(command is RecoveryCodePDFSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnScanQRCodeClickedThenEmitCommandScanQRCode() = runTest {
        testee.onScanQRCodeClicked()

        testee.commands().test {
            awaitItem().assertCommandType(Command.ScanQRCode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnShowTextCodeClickedThenEmitCommandShowTextCode() = runTest {
        testee.onShowTextCodeClicked()

        testee.commands().test {
            awaitItem().assertCommandType(Command.ShowTextCode::class)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun Command.assertCommandType(expectedType: KClass<out Command>) {
        assertTrue(format("Unexpected command type: %s", this::class.simpleName), this::class == expectedType)
    }

    private fun givenAuthenticatedUser() {
        whenever(syncRepository.isSignedIn()).thenReturn(true)
        isSignedInFlow = MutableStateFlow(true)
        whenever(syncRepository.getRecoveryCode()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncRepository.getThisConnectedDevice()).thenReturn(connectedDevice)
        whenever(syncRepository.getConnectedDevices()).thenReturn(Result.Success(listOf(connectedDevice)))
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(qrBitmap())
    }
}
