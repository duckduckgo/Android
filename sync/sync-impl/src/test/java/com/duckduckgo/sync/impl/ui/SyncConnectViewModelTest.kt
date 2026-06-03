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
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKey
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.AccountInfo
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.ConnectCode
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncAuthCode.Connect
import com.duckduckgo.sync.impl.SyncAuthCode.Recovery
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_CONNECT
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.AskJoinerConfirmation
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.ShowError
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.runTest
import org.junit.Assert
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

@RunWith(AndroidJUnit4::class)
class SyncConnectViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val qrEncoder: QREncoder = mock()
    private val syncPixels: SyncPixels = mock()

    private val syncFeature = com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory.create(SyncFeature::class.java)

    // Backing flow that the mocked runner exposes via events/eventsSince. Mirrors the
    // pattern used in RealSyncCodeDispatcherTest and SyncWithAnotherDeviceViewModelTest.
    private val runnerEventsFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 0)
    private val qrCode: com.duckduckgo.sync.impl.exchange.v2.ExchangeV2QrCode = mock()
    private val runner: com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner = mock<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner>().also {
        whenever(it.events).thenReturn(runnerEventsFlow)
        whenever(it.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            runnerEventsFlow.filter { event -> event.timestampMs >= sinceMs }
        }
    }
    private val codeDispatcher = com.duckduckgo.sync.impl.RealSyncCodeDispatcher(
        syncFeature = syncFeature,
        syncAccountRepository = syncRepository,
        qrCode = qrCode,
        runner = runner,
    )

    private val testee = SyncConnectViewModel(
        syncRepository,
        qrEncoder,
        clipboard,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
        syncFeature,
        codeDispatcher,
    )

    @Before
    fun setup() {
        // SyncConnect VM short-circuits pollConnectionKeys() if the user is already signed in
        // (returning from a successful EnterCode pair). Default to "no account" so existing
        // tests that exercise the QR/v2-present flow still run their setup path.
        whenever(syncRepository.getAccountInfo()).thenReturn(AccountInfo())
    }

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        val bitmap = TestSyncFixtures.qrBitmap()
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))
        whenever(qrEncoder.encodeAsBitmap(eq(jsonConnectKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState(source = null).test {
            val viewState = awaitItem()
            Assert.assertEquals(bitmap, viewState.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateConnectQRFailsThenFinishWithError() = runTest {
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Error(reason = "error"))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.FinishWithError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConnectionKeysSuccessThenLoginSuccess() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            verify(syncPixels).fireSignupConnectPixel(source = null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConnectionKeysSuccessWithSourceThenPixelContainsSource() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState(source = "foo").test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem()
            verify(syncPixels).fireSignupConnectPixel(source = "foo")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedThenShowMessage() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))

        testee.onCopyCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedThenCopyCodeToClipboard() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(authCodeToUse.rawCode)
    }

    @Test
    fun whenUserClicksOnReadTextCodeThenCommandIsReadTextCode() = runTest {
        testee.commands().test {
            testee.onReadTextCodeClicked()
            val command = awaitItem()
            assertTrue(command is Command.ReadTextCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansConnectQRCodeAndConnectDeviceSucceedsThenCommandIsLoginSuccess() = runTest {
        whenever(syncRepository.parseSyncAuthCode(jsonConnectKeyEncoded)).thenReturn(Connect(ConnectCode(jsonConnectKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))
        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.LoginSuccess)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_CONNECT))
            verify(syncPixels).fireLoginPixel()
            verify(syncPixels).fireSyncSetupFinishedSuccessfully(eq(SyncPixels.ScreenType.SYNC_CONNECT))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeButSignedInThenCommandIsError() = runTest {
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_CONNECT))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansConnectQRCodeAndConnectDeviceFailsThenCommandIsError() = runTest {
        whenever(syncRepository.parseSyncAuthCode(jsonConnectKeyEncoded)).thenReturn(Connect(ConnectCode(jsonConnectKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = CONNECT_FAILED.code))

        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_CONNECT))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLoginSucceedsThenCommandIsLoginSuccess() = runTest {
        testee.commands().test {
            testee.onLoginSuccess()
            val command = awaitItem()
            assertTrue(command is Command.LoginSuccess)
            verify(syncPixels).fireLoginPixel()
            verify(syncPixels).fireSyncSetupFinishedSuccessfully(eq(SyncPixels.ScreenType.SYNC_CONNECT))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCancelsThenAbandonedPixelFired() = runTest {
        testee.onUserCancelledWithoutSyncSetup()
        verify(syncPixels).fireSyncSetupAbandoned(eq(SYNC_CONNECT))
    }

    @Test
    fun whenBarcodeShownThenPixelFired() = runTest {
        testee.onBarcodeScreenShown()
        verify(syncPixels).fireSyncBarcodeScreenShown(eq(SYNC_CONNECT))
    }

    @Test
    fun whenPollingIfConnectFailsThenShowError() = runTest {
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Error(CONNECT_FAILED.code))
        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPollingIfLoginFailsThenShowError() = runTest {
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Error(LOGIN_FAILED.code))
        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPollingIfGenericErrorThenDoNothing() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncRepository.pollConnectionKeys())
            .thenReturn(Result.Error())
            .thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
            verify(syncRepository, times(2)).pollConnectionKeys()
        }
    }

    // ---- M1.5: signed-out v2 Presenter path ----

    private fun enableV2(displayOn: Boolean) {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(displayOn))
    }

    private fun presenterSessionStarted(linkingCode: String = "https://duckduckgo.com/sync/pairing/#&code2=xyz") =
        ExchangeV2Event.SessionStarted(
            timestampMs = System.currentTimeMillis(),
            pairingRole = PairingRole.Presenter,
            ownChannelId = "own-channel",
            linkingCode = linkingCode,
        )

    private fun transition(
        from: ExchangeV2State,
        to: ExchangeV2State,
        localTrigger: LocalTrigger? = null,
        trigger: ExchangeV2Message? = null,
    ) = ExchangeV2Event.Transition(
        timestampMs = System.currentTimeMillis(),
        from = from,
        to = to,
        trigger = trigger,
        localTrigger = localTrigger,
    )

    @Test
    fun whenBothV2FlagsOnThenRunnerStartPresentInvokedAndV1ConnectQRNotCalled() = runTest {
        enableV2(displayOn = true)
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(bitmap)

        testee.viewState(source = null).test {
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem() // initial empty
            val withQr = awaitItem()
            Assert.assertEquals(bitmap, withQr.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner).startPresent()
        verify(syncRepository, never()).getConnectQR()
    }

    @Test
    fun whenMasterFlagOnButCanShowV2OffThenV1PathTaken() = runTest {
        enableV2(displayOn = false)
        val bitmap = TestSyncFixtures.qrBitmap()
        val authCode = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "raw")
        whenever(qrEncoder.encodeAsBitmap(eq(jsonConnectKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCode))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner, never()).startPresent()
        verify(syncRepository).getConnectQR()
    }

    @Test
    fun whenMasterFlagOffThenV1PathTakenRegardlessOfDisplayFlag() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(true))
        val bitmap = TestSyncFixtures.qrBitmap()
        val authCode = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "raw")
        whenever(qrEncoder.encodeAsBitmap(eq(jsonConnectKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCode))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner, never()).startPresent()
    }

    @Test
    fun whenLinkingCodeReadyThenCopyEmitsUrl() = runTest {
        enableV2(displayOn = true)
        val url = "https://duckduckgo.com/sync/pairing/#&code2=copy-me"
        whenever(qrEncoder.encodeAsBitmap(eq(url), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            runnerEventsFlow.emit(presenterSessionStarted(linkingCode = url))
            awaitItem()
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onCopyCodeClicked()
        verify(clipboard).copyToClipboard(url)
        verify(syncRepository, never()).getConnectQR() // v2 path bypasses v1 fetch
    }

    @Test
    fun whenJoinerConfirmingDuringV2PresentThenAskJoinerConfirmationCommandEmitted() = runTest {
        enableV2(displayOn = true)
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Joiner.Confirming),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected AskJoinerConfirmation, got $command", command is AskJoinerConfirmation)
            Assert.assertEquals("Peer Phone", (command as AskJoinerConfirmation).peerName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenHostConfirmingDuringV2PresentThenAskHostConfirmationCommandEmitted() = runTest {
        enableV2(displayOn = true)
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.Host.Confirming),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected AskHostConfirmation, got $command", command is AskHostConfirmation)
            Assert.assertEquals("Peer Phone", (command as AskHostConfirmation).peerName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoinerDoneWithCidDdgRecoveryCodeThenLoginSuccess() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())
        val recoveryJson = org.json.JSONObject().apply {
            put(
                "recovery",
                org.json.JSONObject().apply {
                    put("user_id", "u-1")
                    put("secret", "s-1")
                    put("cid", "ddg")
                    put("v", "2.0")
                },
            )
        }.toString()
        val b64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = ExchangeV2Message.RecoveryCodeResponse(rawJson = "{}", recoveryCode = b64),
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected LoginSuccess, got $command", command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncRepository).processCode(any(), anyOrNull())
    }

    @Test
    fun whenJoinerDoneViaCid3partyThenLoginSuccessAndUpgradeInvoked() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())
        val recoveryJson = org.json.JSONObject().apply {
            put(
                "recovery",
                org.json.JSONObject().apply {
                    put("user_id", "u-3p")
                    put("secret", "s-3p")
                    put("cid", "3party")
                    put("v", "2.0")
                },
            )
        }.toString()
        val b64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        whenever(syncRepository.joinAccountFromThirdPartyRecoveryCode(any())).thenReturn(Result.Success(true))

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = ExchangeV2Message.RecoveryCodeResponse(rawJson = "{}", recoveryCode = b64),
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected LoginSuccess, got $command", command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncRepository).joinAccountFromThirdPartyRecoveryCode(any())
    }

    @Test
    fun whenV2LinkingCodeScannedThenRoutedThroughDispatcherAndLoginSuccess() = runTest {
        // Regression: signed-out Connect camera scan must route v2 codes through the dispatcher
        // (BUG-A / SURF-3). Previously onQRCodeScanned parsed v1-only and rejected v2 as invalid.
        enableV2(displayOn = true)
        val scannedUrl = "https://duckduckgo.com/sync/pairing/#&code2=scan-me"
        whenever(qrCode.parse(scannedUrl)).thenReturn(
            com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult.LinkingV2(
                channelId = "chan",
                publicKey = "pk",
                version = "2",
            ),
        )
        val recoveryJson = org.json.JSONObject().apply {
            put(
                "recovery",
                org.json.JSONObject().apply {
                    put("user_id", "u-1")
                    put("secret", "s-1")
                    put("cid", "ddg")
                    put("v", "2.0")
                },
            )
        }.toString()
        val recoveryB64 = android.util.Base64.encodeToString(
            recoveryJson.toByteArray(Charsets.UTF_8),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP,
        )
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Success(true))

        testee.commands().test {
            testee.onQRCodeScanned(scannedUrl)
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Waiting,
                    to = ExchangeV2State.Joiner.Done,
                    trigger = ExchangeV2Message.RecoveryCodeResponse(rawJson = "{}", recoveryCode = recoveryB64),
                ),
            )
            val command = awaitItem()
            assertTrue("expected LoginSuccess, got $command", command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner).startScan(scannedUrl)
    }

    @Test
    fun whenHostDoneDuringV2PresentThenLoginSuccess() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Host.Sending, to = ExchangeV2State.Host.Done),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected LoginSuccess, got $command", command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoinerAbortedByHostThenShowError() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Joiner.Waiting, to = ExchangeV2State.Joiner.AbortedByHost),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected ShowError, got $command", command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenJoinerAbortedLocalThenShowError() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedLocal,
                    localTrigger = LocalTrigger.UserDeniedJoiner,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected ShowError, got $command", command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenHostAbortedUserDeniedThenShowError() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(
                    from = ExchangeV2State.Host.Confirming,
                    to = ExchangeV2State.Host.Aborted,
                    localTrigger = LocalTrigger.UserDeniedHost,
                ),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected ShowError, got $command", command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSessionErrorDuringV2PresentThenShowError() = runTest {
        enableV2(displayOn = true)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState(source = null).test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                ExchangeV2Event.SessionError(timestampMs = System.currentTimeMillis(), message = "channel 5xx"),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected ShowError, got $command", command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewStateReCollectedThenConnectQRGeneratedOnce() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonConnectKeyEncoded, rawCode = "raw")
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(authCodeToUse))
        whenever(qrEncoder.encodeAsBitmap(eq(jsonConnectKeyEncoded), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState(source = null).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        testee.viewState(source = null).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        verify(syncRepository, times(1)).getConnectQR()
    }

    @Test
    fun whenViewStateReCollectedThenV2PresentStartedOnce() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(true))
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())
        testee.viewState(source = null).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        testee.viewState(source = null).test { awaitItem(); cancelAndIgnoreRemainingEvents() }
        verify(runner, times(1)).startPresent()
    }
}
