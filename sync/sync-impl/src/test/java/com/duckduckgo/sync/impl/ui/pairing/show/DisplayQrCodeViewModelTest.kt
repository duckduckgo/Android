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

package com.duckduckgo.sync.impl.ui.pairing.show

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.clipboard.ClipboardInteractor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountInfo
import com.duckduckgo.sync.impl.ConnectedDevice
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.DispatchOutcome
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncCodeDispatcher
import com.duckduckgo.sync.impl.exchange.ExchangeProtocolVersion
import com.duckduckgo.sync.impl.pixels.SwitchedAccountDetector
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.CancellationReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_EXCHANGE
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupFailureReason
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupPath
import com.duckduckgo.sync.impl.pixels.SyncPixels.SetupRole
import com.duckduckgo.sync.impl.ui.SyncEntryPoint
import com.duckduckgo.sync.impl.ui.devices.ParcelableDevice
import com.duckduckgo.sync.impl.ui.pairing.SyncPairingResult
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2AlreadyPairedError
import com.duckduckgo.sync.impl.ui.pairing.exchangeV2UpgradeRequiredError
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.BitmapWithCode
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.SetPairingResult
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShareCode
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.pairing.show.DisplayQrCodeViewModel.DialogType
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl
import com.duckduckgo.sync.impl.ui.qrcode.SyncBarcodeUrl.ProtocolVersion.V2
import kotlinx.coroutines.flow.MutableSharedFlow
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
import org.mockito.kotlin.verifyNoMoreInteractions
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
    private val qrEncoder = mock<QREncoder>()
    private val clipboard = mock<ClipboardInteractor>()
    private val switchedAccountDetector = mock<SwitchedAccountDetector>()

    @Before
    fun setup() {
        whenever(codeDispatcher.presentV2()).thenReturn(emptyFlow())
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(bitmap)
        whenever(accountRepository.getAccountInfo()).thenReturn(AccountInfo(isSignedIn = false))
    }

    private fun createTestee(source: String = "other") = DisplayQrCodeViewModel(
        setupEvent = mock(),
        syncEntryPoint = SyncEntryPoint.SYNC_NEW_ACCOUNT,
        launchSource = source,
        accountRepository = accountRepository,
        codeDispatcher = codeDispatcher,
        pixels = pixels,
        qrEncoder = qrEncoder,
        clipboard = clipboard,
        switchedAccountDetector = switchedAccountDetector,
        dispatchers = coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun `when this device is signed in then v2 pixels report the exchange screen`() = runTest {
        givenSignedIn()
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING)))

        createTestee()

        verify(pixels).fireSyncSetupFinishedSuccessfully(SYNC_EXCHANGE, "other", SetupPath.PAIRING, null, null)
    }

    @Test
    fun `when the linking code is ready then it is shown as a QR code`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(BitmapWithCode(bitmap, linkingUrl, "encoded-code"), awaitItem().bitmap)
            verify(qrEncoder).encodeAsBitmap(eq(linkingUrl), any(), any())

            cancel()
        }
    }

    @Test
    fun `when the host confirmation is requested then the host confirmation dialog is shown`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(
            flowOf(DispatchOutcome.HostConfirmationRequested(peerName = "Other Device", protocolVersion = ExchangeProtocolVersion.V2_0)),
        )

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(DialogType.HostConfirmation(peerName = "Other Device"), awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the joiner confirmation is requested then the joiner confirmation dialog is shown`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(
            flowOf(DispatchOutcome.JoinerConfirmationRequested(peerName = "Other Device", protocolVersion = ExchangeProtocolVersion.V2_0)),
        )

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(DialogType.JoinerConfirmation(peerName = "Other Device"), awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the host is confirmed then the dispatcher is notified`() = runTest {
        val testee = createTestee()
        testee.onHostConfirmed()

        verify(codeDispatcher).confirmHost()
    }

    @Test
    fun `when the host is denied then the dispatcher is notified`() = runTest {
        val testee = createTestee()
        testee.onHostDenied()

        verify(codeDispatcher).denyHost()
    }

    @Test
    fun `when the joiner is confirmed then the dispatcher is notified`() = runTest {
        val testee = createTestee()
        testee.onJoinerConfirmed()

        verify(codeDispatcher).confirmJoiner()
    }

    @Test
    fun `when the joiner is denied then the dispatcher is notified`() = runTest {
        val testee = createTestee()
        testee.onJoinerDenied()

        verify(codeDispatcher).denyJoiner()
    }

    @Test
    fun `when the host is confirmed then the connecting dialog is shown`() = runTest {
        val testee = createTestee()
        testee.onHostConfirmed()

        testee.viewState.test {
            assertEquals(DialogType.Connecting, awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the joiner is confirmed then the connecting dialog is shown`() = runTest {
        val testee = createTestee()
        testee.onJoinerConfirmed()

        testee.viewState.test {
            assertEquals(DialogType.Connecting, awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the host is denied then no dialog is shown`() = runTest {
        val testee = createTestee()
        testee.onHostConfirmed()
        testee.onHostDenied()

        testee.viewState.test {
            assertEquals(null, awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the login completes after confirming then the connecting dialog is dismissed`() = runTest {
        givenThisConnectedDevice()
        val outcomes = MutableSharedFlow<DispatchOutcome>()
        whenever(codeDispatcher.presentV2()).thenReturn(outcomes)

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(null, awaitItem().dialog)

            testee.onHostConfirmed()
            assertEquals(DialogType.Connecting, awaitItem().dialog)

            outcomes.emit(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING))
            assertEquals(null, awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the join outcome is unknown after confirming then the check other device dialog is shown`() = runTest {
        val outcomes = MutableSharedFlow<DispatchOutcome>()
        whenever(codeDispatcher.presentV2()).thenReturn(outcomes)

        val testee = createTestee()

        testee.viewState.test {
            assertEquals(null, awaitItem().dialog)

            testee.onJoinerConfirmed()
            assertEquals(DialogType.Connecting, awaitItem().dialog)

            outcomes.emit(DispatchOutcome.JoinOutcomeUnknown())
            assertEquals(DialogType.CheckOtherDevice, awaitItem().dialog)

            cancel()
        }
    }

    @Test
    fun `when the connecting dialog is cancelled then the failure result is set and the screen closes`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onConnectingCancelled()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the check other device dialog is cancelled then the failure result is set and the screen closes`() = runTest {
        val testee = createTestee()

        testee.commands.test {
            testee.onCheckOtherDeviceCancelled()
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Failure, command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes then the success result is set and the screen closes`() = runTest {
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(
                SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT),
                command.result,
            )
            assertIs<Close>(awaitItem())

            cancel()
        }
        verify(pixels).fireLoginPixel()
        verify(pixels).fireSyncSetupFinishedSuccessfully(SYNC_CONNECT, "other", SetupPath.PAIRING, null, null)
    }

    @Test
    fun `when the screen opens then the account before pairing is recorded`() = runTest {
        createTestee()

        verify(switchedAccountDetector).onPairingStarted()
    }

    @Test
    fun `when the v2 login completes then the switched account tracker is notified`() = runTest {
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING)))

        createTestee()

        verify(switchedAccountDetector).onPaired()
    }

    @Test
    fun `when v2 pairing fails then the switched account tracker is not notified`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed("boom")))

        createTestee()

        verify(switchedAccountDetector, never()).onPaired()
    }

    @Test
    fun `when the login completes as the elected host then a host success result is set`() = runTest {
        givenThisConnectedDevice()
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LoggedIn(path = SetupPath.PAIRING, myRole = SetupRole.HOST)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<SetPairingResult>(command)
            assertEquals(SyncPairingResult.Success(thisParcelableDevice, SyncEntryPoint.SYNC_NEW_ACCOUNT), command.result)
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the login completes but this connected device is missing then a failure result is set`() = runTest {
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
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.AlreadyConnected))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(exchangeV2AlreadyPairedError, command.content)

            cancel()
        }
    }

    @Test
    fun `when the peer requires an app upgrade then the upgrade required error is shown`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.UpgradeRequired(codeMajor = 2)))

        val testee = createTestee()

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowV2Error>(command)
            assertEquals(exchangeV2UpgradeRequiredError, command.content)

            cancel()
        }
    }

    @Test
    fun `when pairing fails then the pairing failed error is shown`() = runTest {
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
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "boom", path = SetupPath.PAIRING)))

        createTestee()

        verify(pixels).fireSyncSetupFailed(
            SYNC_CONNECT,
            "other",
            SetupFailureReason.UNEXPECTED_FAILURE,
            SetupPath.PAIRING,
            null,
            null,
            null,
        )
    }

    @Test
    fun `when pairing is denied then the setup abandoned pixel is fired`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels).fireSyncSetupAbandoned(SYNC_CONNECT, "other", CancellationReason.CONFIRMATION_DENIED)
    }

    @Test
    fun `when pairing is denied then the setup failed pixel is not fired`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.Failed(reason = "denied", code = PAIRING_CANCELLED.code)))

        createTestee()

        verify(pixels, never()).fireSyncSetupFailed(any(), anyOrNull(), any(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `when the linking code is ready then no setup pixels are fired`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        createTestee()

        verify(pixels).fireSyncBarcodeScreenShown(SYNC_CONNECT)
        verifyNoMoreInteractions(pixels)
    }

    @Test
    fun `when the copy button is clicked then the linking code is copied to the clipboard`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))

        val testee = createTestee()
        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(linkingUrl, isSensitive = true)
        verify(pixels).fireSyncSetupCodeCopiedToClipboard(SYNC_CONNECT)
    }

    @Test
    fun `when the copied code notification is not shown by the system then a message is shown`() = runTest {
        whenever(codeDispatcher.presentV2()).thenReturn(flowOf(DispatchOutcome.LinkingCodeReady(linkingUrl)))
        whenever(clipboard.copyToClipboard(any(), any())).thenReturn(false)

        val testee = createTestee()

        testee.commands.test {
            testee.onCopyCodeClicked()
            assertEquals(ShowMessage(R.string.sync_simplified_qr_code_copied_message), awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the copied code notification is shown by the system then no message is shown`() = runTest {
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
        val testee = createTestee()
        testee.onCopyCodeClicked()

        verifyNoInteractions(clipboard)
        verify(pixels).fireSyncBarcodeScreenShown(SYNC_CONNECT)
        verifyNoMoreInteractions(pixels)
    }

    @Test
    fun `when the share button is clicked then the share code command is sent`() = runTest {
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
        val testee = createTestee()

        testee.commands.test {
            testee.onShareCodeClicked()
            expectNoEvents()

            cancel()
        }
    }

    @Test
    fun `when the error dialog is dismissed then the failure result is set and the screen closes`() = runTest {
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

    private fun givenSignedIn() {
        whenever(accountRepository.getAccountInfo()).thenReturn(AccountInfo(isSignedIn = true))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
