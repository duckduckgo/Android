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
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.AskTurnOffSync
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.CheckIfUserHasStoragePermission
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroCreateAccount
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.IntroRecoverSyncData
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchLearnMore
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.LaunchSyncGetOnOtherPlatforms
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.Command.RequestSetupAuthentication
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.CreateAccountFlow
import com.duckduckgo.sync.impl.ui.SyncActivityViewModel.SetupFlows.SignInFlow
import com.duckduckgo.sync.impl.ui.SyncDeviceListItem.SyncedDevice
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
            appCoroutineScope = coroutineTestRule.testScope,
        )
        whenever(deviceAuthenticator.isAuthenticationRequired()).thenReturn(true)
        whenever(syncStateMonitor.syncState()).thenReturn(emptyFlow())
        whenever(syncAccountRepository.isSyncSupported()).thenReturn(true)
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
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
