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

package com.duckduckgo.sync.impl.ui.v2

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.AccountInfo
import com.duckduckgo.sync.impl.ConnectCode
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.ExchangeResult
import com.duckduckgo.sync.impl.InvitationCode
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.RouteDecision
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncCodeType
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.autorestore.RestorePayload
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncPixelParameters
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CodeVersion
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.AskSwitchAccount
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.RunAcknowledgmentAnimation
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowPairingAcknowledgement
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.v2.ProcessSyncCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class ProcessSyncCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryAuthCode = SyncAuthCode.Recovery(RecoveryCode(primaryKey = "primary-key", userId = "user-id"))
    private val exchangeAuthCode = SyncAuthCode.Exchange(InvitationCode(keyId = "key-id", publicKey = "public-key"))

    private val thisDevice = ConnectedDevice(
        thisDevice = true,
        deviceName = "This Device",
        deviceId = "this-device-id",
        deviceType = DeviceType(),
    )
    private val thisParcelableDevice = ParcelableDevice.fromConnectedDevice(thisDevice)

    private val accountRepository = mock<SyncAccountRepository>()
    private val codeDispatcher = mock<SyncCodeDispatcher>()
    private val syncPixels = mock<SyncPixels>()
    private val syncAutoRestoreManager = mock<SyncAutoRestoreManager>()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)

    private var accountInfo = AccountInfo()

    private fun createTestee(
        source: SyncCodeSource = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT),
    ): ProcessSyncCodeViewModel {
        whenever(accountRepository.getAccountInfo()).thenReturn(accountInfo)
        return ProcessSyncCodeViewModel(
            source = source,
            accountRepository = accountRepository,
            codeDispatcher = codeDispatcher,
            syncFeature = syncFeature,
            syncPixels = syncPixels,
            syncAutoRestoreManager = syncAutoRestoreManager,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when a legacy code is routed then the acknowledgment animation runs and the code is processed`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            cancel()
        }
        verify(accountRepository).processCode(eq(recoveryAuthCode), anyOrNull())
    }

    @Test
    fun `when processing a legacy code succeeds then the screen does not close until the animation completes`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when processing a legacy recovery code succeeds then a recovery success result is set and the screen closes`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when processing a legacy connect code succeeds then a pairing success result is set and the screen closes`() = runTest {
        givenLegacyCode(SyncAuthCode.Connect(ConnectCode(deviceId = "device-id", secretKey = "secret-key")))
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(
                SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT),
                command.result,
            )
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when processing a legacy code fails then a v1 error is shown`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_login_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when processing a legacy exchange code succeeds then the recovery key is polled until login completes`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.Pending), Result.Success(ExchangeResult.LoggedIn))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(
                SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT),
                command.result,
            )
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the exchange login completes then the screen does not close until the animation completes`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()
        whenever(accountRepository.pollForRecoveryCodeAndLogin()).thenReturn(Result.Success(ExchangeResult.LoggedIn))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when polling for the recovery key fails then a v1 error is shown`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin()).thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_login_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when processing fails on an already signed in device and seamless switching is enabled then the user is asked to switch`() = runTest {
        givenSeamlessAccountSwitching(enabled = true)
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertEquals(AskSwitchAccount("sync-url"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when processing fails on an already signed in device and seamless switching is disabled then an error is shown`() = runTest {
        givenSeamlessAccountSwitching(enabled = false)
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_login_authenticated_device_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when polling fails on an already signed in device and seamless switching is enabled then the user is asked to switch`() = runTest {
        givenSeamlessAccountSwitching(enabled = true)
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertEquals(AskSwitchAccount("sync-url"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when polling requires account switching then the user is asked to switch accounts`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertEquals(AskSwitchAccount("encoded-code"), awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the user accepts switching accounts then the account is switched and a success result is set`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))
        whenever(accountRepository.logoutAndJoinNewAccount("encoded-code")).thenReturn(Result.Success(true))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())

            testee.onAnimationComplete()
            testee.onUserAcceptedSwitchingAccount("encoded-code")
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(
                SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT),
                command.result,
            )
            assertIs<Close>(awaitItem())

            cancel()
        }
        verify(accountRepository).logoutAndJoinNewAccount("encoded-code")
    }

    @Test
    fun `when switching accounts fails then a v1 error is shown`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))
        whenever(accountRepository.logoutAndJoinNewAccount("encoded-code"))
            .thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())

            testee.onUserAcceptedSwitchingAccount("encoded-code")
            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_login_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when the user cancels switching accounts then the failure result is set and the screen closes`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())

            testee.onUserCancelledSwitchingAccount()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the v2 linking code is ready then no commands are sent`() = runTest {
        givenV2Outcomes(DispatchOutcome.LinkingCodeReady("linking-code"))

        val testee = createTestee()

        testee.commands.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the host confirmation is requested then the user is asked to confirm the host`() = runTest {
        givenV2Outcomes(DispatchOutcome.HostConfirmationRequested(peerName = "Other Device"))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskHostConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the joiner confirmation is requested then the user is asked to confirm the joiner`() = runTest {
        givenV2Outcomes(DispatchOutcome.JoinerConfirmationRequested(peerName = "Other Device"))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskJoinerConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes then the logged in view state is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING))

        val testee = createTestee()

        assertTrue(testee.viewState.value.isLoggedIn)
    }

    @Test
    fun `when the login completes then the screen does not close until the animation completes`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING))

        val testee = createTestee()

        testee.commands.test {
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the login completes then after the animation the success result is set and the screen closes`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.JOINER))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes as the elected host then a host success result is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes via recovery then the screen does not close until the animation completes`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.RECOVERY))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the login completes via recovery then the animation runs and a recovery success result is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.RECOVERY))
        givenThisConnectedDevice()

        val testee = createTestee()

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes but this connected device is missing then a failure result is set`() = runTest {
        givenV2Outcomes(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST))

        val testee = createTestee()

        testee.commands.test {
            testee.onAnimationComplete()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the devices are already paired then the already paired error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.AlreadyConnected)

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(v2AlreadyPairedError, command.content)

            cancel()
        }
    }

    @Test
    fun `when the peer requires an app upgrade then the upgrade required error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.UpgradeRequired(codeMajor = 2))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(v2UpgradeRequiredError, command.content)

            cancel()
        }
    }

    @Test
    fun `when pairing fails then the pairing failed error is shown`() = runTest {
        givenV2Outcomes(DispatchOutcome.Failed(reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(R.string.sync_v2_error_pairing_failed, command.content.title)
            assertEquals(R.string.sync_v2_error_try_again, command.content.message)

            cancel()
        }
    }

    @Test
    fun `when the host is confirmed then the dispatcher is notified and the acknowledgement is shown`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onHostConfirmed()
            assertIs<ShowPairingAcknowledgement>(awaitItem())

            cancel()
        }
        verify(codeDispatcher).confirmHost()
    }

    @Test
    fun `when the host is denied then the dispatcher is notified`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.onHostDenied()

        verify(codeDispatcher).denyHost()
    }

    @Test
    fun `when the joiner is confirmed then the dispatcher is notified and the acknowledgement is shown`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onJoinerConfirmed()
            assertIs<ShowPairingAcknowledgement>(awaitItem())

            cancel()
        }
        verify(codeDispatcher).confirmJoiner()
    }

    @Test
    fun `when the joiner is denied then the dispatcher is notified`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.onJoinerDenied()

        verify(codeDispatcher).denyJoiner()
    }

    @Test
    fun `when the acknowledgement animation is requested then the acknowledgment animation runs`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.runAcknowledgementAnimation()
            assertIs<RunAcknowledgmentAnimation>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the error dialog is dismissed then the failure result is set and the screen closes`() = runTest {
        givenV2Outcomes()

        val testee = createTestee()

        testee.commands.test {
            testee.onErrorDialogDismissed()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when a scanned v2 code is routed then a v2 barcode parse success pixel is fired`() = runTest {
        givenV2Outcomes()

        createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        verify(syncPixels).fireBarcodeScannerParseSuccess(SYNC_CONNECT, CodeVersion.V2, SyncCodeType.LINKING)
    }

    @Test
    fun `when a pasted v2 code is routed then a v2 pasted parse success pixel is fired`() = runTest {
        givenV2Outcomes()

        createTestee(source = SyncCodeSource.Pasted("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        verify(syncPixels).fireSyncSetupCodePastedParseSuccess(SYNC_CONNECT, CodeVersion.V2, SyncCodeType.LINKING)
    }

    @Test
    fun `when a scanned legacy code is unrecognized then a barcode parse error pixel is fired`() = runTest {
        givenLegacyCode(SyncAuthCode.Unknown("garbage"))

        createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        verify(syncPixels).fireBarcodeScannerParseError(SYNC_CONNECT, SetupFailureReason.UNRECOGNIZED_CODE)
    }

    @Test
    fun `when a scanned legacy v1 code is routed then a v1 barcode parse success pixel is fired`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()

        createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        verify(syncPixels).fireBarcodeScannerParseSuccess(SYNC_CONNECT, CodeVersion.V1, null)
    }

    @Test
    fun `when a legacy recovery login completes then login and setup finished pixels are fired`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()

        val testee = createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            testee.onAnimationComplete()
            awaitItem()
            awaitItem()
            cancel()
        }
        verify(syncPixels).fireLoginPixel()
        verify(syncPixels).fireSyncSetupFinishedSuccessfully(SYNC_CONNECT, null, null, null)
    }

    @Test
    fun `when a deep link setup starts then the deep link started pixel is fired`() = runTest {
        givenV2Outcomes()

        createTestee(source = SyncCodeSource.DeepLink("sync-url", SyncEntryPoint.ADD_DEVICE))

        verify(syncPixels).fireSetupDeepLinkFlowStarted()
    }

    @Test
    fun `when a deep link setup completes then the deep link success pixel is fired instead of setup finished`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()

        val testee = createTestee(source = SyncCodeSource.DeepLink("sync-url", SyncEntryPoint.ADD_DEVICE))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            testee.onAnimationComplete()
            awaitItem()
            awaitItem()
            cancel()
        }
        verify(syncPixels).fireLoginPixel()
        verify(syncPixels).fireSetupDeepLinkFlowSuccess()
        verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when resuming a previous session then the preserved device id is used and the auto restore success pixel is fired`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()
        whenever(syncAutoRestoreManager.retrieveRecoveryPayload())
            .thenReturn(RestorePayload(recoveryCode = "recovery-code", deviceId = "preserved-device-id"))

        val testee = createTestee(source = SyncCodeSource.Restored("sync-url"))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            testee.onAnimationComplete()
            awaitItem()
            awaitItem()
            cancel()
        }
        verify(accountRepository).processCode(eq(recoveryAuthCode), eq("preserved-device-id"))
        verify(syncPixels).fireAutoRestoreSuccess(SyncPixelParameters.AUTO_RESTORE_SOURCE_SETTINGS)
        verify(syncPixels).fireLoginPixel()
        verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when resuming a previous session fails then the auto restore failure pixel is fired`() = runTest {
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee(source = SyncCodeSource.Restored("sync-url"))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<ShowV1Error>(awaitItem())
            cancel()
        }
        verify(syncPixels).fireAutoRestoreFailure(SyncPixelParameters.AUTO_RESTORE_SOURCE_SETTINGS, LOGIN_FAILED.code.toString(), "boom")
    }

    @Test
    fun `when the user cancels a scanned setup then no abandoned pixel is fired as the scanner owns it`() = runTest {
        givenV2Outcomes()

        val testee = createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        testee.onUserCanceled()

        verify(syncPixels, never()).fireSyncSetupAbandoned(any(), anyOrNull())
    }

    @Test
    fun `when the user cancels a deep link setup before it finishes then the deep link abandoned pixel is fired`() = runTest {
        givenV2Outcomes()

        val testee = createTestee(source = SyncCodeSource.DeepLink("sync-url", SyncEntryPoint.ADD_DEVICE))

        testee.onUserCanceled()

        verify(syncPixels).fireSetupDeepLinkFlowAbandoned()
    }

    @Test
    fun `when the user is asked to switch accounts then the ask to switch pixel is fired`() = runTest {
        givenSeamlessAccountSwitching(enabled = true)
        givenLegacyCode(recoveryAuthCode)
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code, reason = "boom"))

        val testee = createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())
            cancel()
        }
        verify(syncPixels).fireAskUserToSwitchAccount()
    }

    @Test
    fun `when the user accepts switching accounts then the accepted pixel is fired`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        givenThisConnectedDevice()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))
        whenever(accountRepository.logoutAndJoinNewAccount("encoded-code")).thenReturn(Result.Success(true))

        val testee = createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())
            testee.onAnimationComplete()
            testee.onUserAcceptedSwitchingAccount("encoded-code")
            awaitItem()
            awaitItem()
            cancel()
        }
        verify(syncPixels).fireUserAcceptedSwitchingAccount()
        verify(syncPixels).fireUserSwitchedAccount()
    }

    @Test
    fun `when the user cancels switching accounts then the cancelled pixel is fired`() = runTest {
        givenLegacyCode(exchangeAuthCode)
        givenProcessCodeSucceeds()
        whenever(accountRepository.pollForRecoveryCodeAndLogin())
            .thenReturn(Result.Success(ExchangeResult.AccountSwitchingRequired("encoded-code")))

        val testee = createTestee(source = SyncCodeSource.Scanned("sync-url", SyncEntryPoint.SYNC_NEW_ACCOUNT))

        testee.commands.test {
            assertIs<RunAcknowledgmentAnimation>(awaitItem())
            assertIs<AskSwitchAccount>(awaitItem())
            testee.onUserCancelledSwitchingAccount()
            awaitItem()
            awaitItem()
            cancel()
        }
        verify(syncPixels).fireUserCancelledSwitchingAccount()
    }

    private fun givenProcessCodeSucceeds() {
        whenever(accountRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))
    }

    private fun givenThisConnectedDevice() {
        whenever(accountRepository.getThisConnectedDevice()).thenReturn(thisDevice)
    }

    private fun givenLegacyCode(authCode: SyncAuthCode) {
        whenever(codeDispatcher.route(any())).thenReturn(RouteDecision.Legacy(authCode))
    }

    private fun givenSeamlessAccountSwitching(enabled: Boolean) {
        syncFeature.seamlessAccountSwitching().setRawStoredState(State(enabled))
    }

    private fun givenV2Outcomes(vararg outcomes: DispatchOutcome) {
        val flow = if (outcomes.isEmpty()) emptyFlow() else flowOf(*outcomes)
        whenever(codeDispatcher.route(any())).thenReturn(RouteDecision.V2InProgress(SyncCodeType.LINKING, flow))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
