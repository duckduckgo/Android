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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.connectedDevice
import com.duckduckgo.sync.TestSyncFixtures.deviceId
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
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
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.auth.DeviceAuthenticator
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.CreateAccountFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.SignInFlow
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import java.lang.String.format
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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

    private val stateFlow = MutableStateFlow(SyncState.READY)

    private lateinit var testee: SyncActivityViewModel

    @Before
    fun before() {
        testee = SyncActivityViewModel(
            syncAccountRepository = syncAccountRepository,
            dispatchers = coroutineTestRule.testDispatcherProvider,
            syncStateMonitor = syncStateMonitor,
            syncEngine = syncEngine,
            recoveryCodePDF = recoveryPDF,
            syncFeatureToggle = syncFeatureToggle,
            syncPixels = syncPixels,
            deviceAuthenticator = deviceAuthenticator,
        )
        whenever(deviceAuthenticator.isAuthenticationRequired()).thenReturn(true)
        whenever(syncStateMonitor.syncState()).thenReturn(emptyFlow())
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(true)
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
            awaitItem().assertCommandType(RequestSetupAuthentication::class)
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
    fun whenOnRemoveDeviceConfirmedThenRemoveDevice() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository).logout(deviceId)
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenFetchRemoteDevices() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.onRemoveDeviceConfirmed(connectedDevice)

        verify(syncAccountRepository).getConnectedDevices()
    }

    @Test
    fun whenOnRemoveDeviceSucceedsThenReturnUpdateDevices() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Success(true))

        testee.viewState().test {
            var awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
            whenever(syncAccountRepository.getConnectedDevices()).thenReturn(Result.Success(listOf()))
            testee.onRemoveDeviceConfirmed(connectedDevice)
            awaitItem = awaitItem()
            assertEquals(0, awaitItem.syncedDevices.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRemoveDeviceFailsThenRestorePreviousList() = runTest {
        givenAuthenticatedUser()

        whenever(syncAccountRepository.logout(deviceId)).thenReturn(Result.Error(reason = "error"))

        testee.viewState().test {
            testee.onRemoveDeviceConfirmed(connectedDevice)
            val awaitItem = expectMostRecentItem()
            assertEquals(1, awaitItem.syncedDevices.size)
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
            awaitItem = awaitItem()
            assertNotNull(awaitItem.syncedDevices.filterIsInstance<SyncedDevice>().first { it.device.deviceName == newDevice.deviceName })
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
            testee.onDeviceConnected()

            val initialState = expectMostRecentItem()
            assertEquals(connectedDevices.size, initialState.syncedDevices.size)
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
