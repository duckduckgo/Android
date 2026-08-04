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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion.V2
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.BitmapWithCode
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.ShareCode
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.ShowV1Error
import com.duckduckgo.sync.impl.ui.v2.DisplayQrCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.PairingMethod
import com.duckduckgo.sync.impl.ui.v2.SyncPairingResult.Role
import com.duckduckgo.sync.impl.ui.v2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.v2UpgradeRequiredError
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

@RunWith(AndroidJUnit4::class)
class DisplayQrCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val bitmap = TestSyncFixtures.qrBitmap()
    private val linkingUrl = SyncBarcodeUrl(
        webSafeB64EncodedCode = "encoded-code",
        deviceName = "Device Name",
        protocolVersion = V2,
    ).asUrl()

    private val thisDevice = ConnectedDevice(
        thisDevice = true,
        deviceName = "This Device",
        deviceId = "this-device-id",
        deviceType = DeviceType(),
    )
    private val thisParcelableDevice = ParcelableDevice.fromConnectedDevice(thisDevice)

    private val accountRepository = mock<SyncAccountRepository>()
    private val codeDispatcher = mock<SyncCodeDispatcher>()
    private val pixels = mock<SyncPixels>()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java)
    private val qrEncoder = mock<QREncoder>()
    private val clipboard = mock<ClipboardInteractor>()

    @Before
    fun setup() {
        whenever(codeDispatcher.presentV2()).thenReturn(emptyFlow())
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(bitmap)
        whenever(accountRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
    }

    private fun createTestee(source: String? = null) = DisplayQrCodeViewModel(
        source = source,
        accountRepository = accountRepository,
        codeDispatcher = codeDispatcher,
        pixels = pixels,
        syncFeature = syncFeature,
        qrEncoder = qrEncoder,
        clipboard = clipboard,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when v2 is disabled then the v1 connect code is shown as a QR code`() = runTest {
        givenV2Disabled()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(BitmapWithCode(bitmap, "raw-code", "raw-code"), awaitItem().bitmap)
            verify(qrEncoder).encodeAsBitmap(eq("raw-code"), any(), any())
            verify(codeDispatcher, never()).presentV2()

            cancel()
        }
    }

    @Test
    fun `when v2 is disabled and the connect code cannot be generated then an error is shown`() = runTest {
        givenV2Disabled()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Error(reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_generic_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
        verify(accountRepository, never()).pollConnectionKeys()
    }

    @Test
    fun `when v2 is disabled and the connection keys are synced then a success result with no role is set and the screen closes`() = runTest {
        givenV2Disabled()
        givenThisConnectedDevice()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))
        whenever(accountRepository.pollConnectionKeys()).thenReturn(Result.Success(true))

        val testee = createTestee(source = "foo")

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, role = null, method = PairingMethod.DisplayedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
        verify(pixels).fireSignupConnectPixel("foo")
        verify(pixels).fireSyncSetupFinishedSuccessfully(SYNC_CONNECT, null, null, null)
    }

    @Test
    fun `when v2 is disabled and polling fails with a login error then an error is shown`() = runTest {
        givenV2Disabled()
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))
        whenever(accountRepository.pollConnectionKeys()).thenReturn(Result.Error(code = LOGIN_FAILED.code, reason = "boom"))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV1Error>(command)
            assertEquals(R.string.sync_connect_login_error, command.content.message)
            assertEquals("boom", command.content.reason)

            cancel()
        }
    }

    @Test
    fun `when only the v2 connect flow flag is enabled then the v1 connect code is shown`() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(false))
        whenever(accountRepository.getConnectQR()).thenReturn(Result.Success(AuthCode(qrCode = "raw-code", rawCode = "b64-code")))

        createTestee()

        verify(accountRepository).getConnectQR()
        verify(codeDispatcher, never()).presentV2()
    }

    @Test
    fun `when v2 is enabled then the v2 linking code is shown as a QR code`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(BitmapWithCode(bitmap, linkingUrl, "encoded-code"), awaitItem().bitmap)
            verify(qrEncoder).encodeAsBitmap(eq(linkingUrl), any(), any())
            verify(accountRepository, never()).getConnectQR()

            cancel()
        }
    }

    @Test
    fun `when the host confirmation is requested then the user is asked to confirm the host`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.HostConfirmationRequested(peerName = "Other Device")))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskHostConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the joiner confirmation is requested then the user is asked to confirm the joiner`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.JoinerConfirmationRequested(peerName = "Other Device")))

        val testee = createTestee()

        testee.commands.test {
            assertEquals(AskJoinerConfirmation(peerName = "Other Device"), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the host is confirmed then the dispatcher is notified`() = runTest {
        givenV2Enabled()

        val testee = createTestee()
        testee.onHostConfirmed()

        verify(codeDispatcher).confirmHost()
    }

    @Test
    fun `when the host is denied then the dispatcher is notified`() = runTest {
        givenV2Enabled()

        val testee = createTestee()
        testee.onHostDenied()

        verify(codeDispatcher).denyHost()
    }

    @Test
    fun `when the joiner is confirmed then the dispatcher is notified`() = runTest {
        givenV2Enabled()

        val testee = createTestee()
        testee.onJoinerConfirmed()

        verify(codeDispatcher).confirmJoiner()
    }

    @Test
    fun `when the joiner is denied then the dispatcher is notified`() = runTest {
        givenV2Enabled()

        val testee = createTestee()
        testee.onJoinerDenied()

        verify(codeDispatcher).denyJoiner()
    }

    @Test
    fun `when the login completes then the success result is set and the screen closes`() = runTest {
        givenV2Enabled()
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, role = null, method = PairingMethod.DisplayedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
        verify(pixels).fireLoginPixel()
        verify(pixels).fireSyncSetupFinishedSuccessfully(SYNC_CONNECT, SetupPath.PAIRING, null, null)
    }

    @Test
    fun `when the login completes as the elected host then a host success result is set`() = runTest {
        givenV2Enabled()
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, Role.Host, PairingMethod.DisplayedCode), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes but this connected device is missing then a failure result is set`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the devices are already paired then the already paired error is shown`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.AlreadyConnected))

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
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.UpgradeRequired(codeMajor = 2)))

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
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "boom")))

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
    fun `when pairing fails then the setup failed pixel is fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "boom", path = SetupPath.PAIRING)))

        createTestee()

        verify(pixels).fireSyncSetupFailed(SYNC_CONNECT, SetupFailureReason.UNEXPECTED_FAILURE, SetupPath.PAIRING, null, null, null)
    }

    @Test
    fun `when pairing is denied then the setup abandoned pixel is fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels).fireSyncSetupAbandoned(SYNC_CONNECT, CancellationReason.CONFIRMATION_DENIED)
    }

    @Test
    fun `when pairing is denied then the setup failed pixel is not fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels, never()).fireSyncSetupFailed(any(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when the linking code is ready then no setup pixels are fired`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        createTestee()

        verifyNoInteractions(pixels)
    }

    @Test
    fun `when the copy button is clicked then the linking code is copied to the clipboard`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()
        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(linkingUrl, isSensitive = true)
        verify(pixels).fireSyncSetupCodeCopiedToClipboard(SYNC_CONNECT)
    }

    @Test
    fun `when the copied code notification is not shown by the system then a message is shown`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))
        whenever(clipboard.copyToClipboard(any(), any())).thenReturn(false)

        val testee = createTestee()

        testee.commands.test {
            testee.onCopyCodeClicked()
            assertEquals(ShowMessage(R.string.sync_code_copied_message), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the copied code notification is shown by the system then no message is shown`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))
        whenever(clipboard.copyToClipboard(any(), any())).thenReturn(true)

        val testee = createTestee()

        testee.commands.test {
            testee.onCopyCodeClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the copy button is clicked before the code is ready then nothing is copied`() = runTest {
        givenV2Enabled()

        val testee = createTestee()
        testee.onCopyCodeClicked()

        verifyNoInteractions(clipboard)
        verifyNoInteractions(pixels)
    }

    @Test
    fun `when the share button is clicked then the share code command is sent`() = runTest {
        givenV2Enabled()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()

        testee.commands.test {
            testee.onShareCodeClicked()
            assertEquals(ShareCode(linkingUrl), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the share button is clicked before the code is ready then no command is sent`() = runTest {
        givenV2Enabled()

        val testee = createTestee()

        testee.commands.test {
            testee.onShareCodeClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the error dialog is dismissed then the failure result is set and the screen closes`() = runTest {
        givenV2Enabled()

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

    private fun givenThisConnectedDevice() {
        whenever(accountRepository.getThisConnectedDevice()).thenReturn(thisDevice)
    }

    private fun givenV2Enabled() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(true))
    }

    private fun givenV2Disabled() {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(false))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
