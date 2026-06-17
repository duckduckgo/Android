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
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.SyncAccountFixtures.accountA
import com.duckduckgo.sync.SyncAccountFixtures.accountB
import com.duckduckgo.sync.SyncAccountFixtures.noAccount
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.TestSyncFixtures.encryptedRecoveryCode
import com.duckduckgo.sync.TestSyncFixtures.jsonExchangeKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.primaryDeviceKeyId
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_CANCELLED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.PAIRING_REJECTED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.InvitationCode
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.RealSyncCodeDispatcher
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncAuthCode.Exchange
import com.duckduckgo.sync.impl.SyncAuthCode.Recovery
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.encodeB64
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2CodeParseResult
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Message
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2QrCode
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Runner
import com.duckduckgo.sync.impl.exchange.v2.ExchangeV2State
import com.duckduckgo.sync.impl.exchange.v2.LocalTrigger
import com.duckduckgo.sync.impl.exchange.v2.PairingRole
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.pixels.SyncPixels.ScreenType.SYNC_EXCHANGE
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.AskHostConfirmation
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.ShowV2Error
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.SwitchAccountSuccess
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncWithAnotherDeviceViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val qrEncoder: QREncoder = mock()
    private val syncPixels: SyncPixels = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
        this.seamlessAccountSwitching().setRawStoredState(State(true))
        this.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(false))
    }
    private val qrCode: ExchangeV2QrCode = mock()

    private val runnerEventsFlow = kotlinx.coroutines.flow.MutableSharedFlow<com.duckduckgo.sync.impl.exchange.v2.ExchangeV2Event>(replay = 0)
    private val runner: ExchangeV2Runner = mock<ExchangeV2Runner>().also {
        whenever(it.events).thenReturn(runnerEventsFlow)
        whenever(it.eventsSince(any())).thenAnswer { invocation ->
            val sinceMs = invocation.getArgument<Long>(0)
            runnerEventsFlow.filter { event -> event.timestampMs >= sinceMs }
        }
    }
    private val codeDispatcher = RealSyncCodeDispatcher(
        syncFeature = syncFeature,
        syncAccountRepository = syncRepository,
        qrCode = qrCode,
        runner = runner,
    )

    private val testee = SyncWithAnotherActivityViewModel(
        syncRepository,
        qrEncoder,
        clipboard,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
        syncFeature,
        codeDispatcher,
    )

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.viewState().test {
            val viewState = awaitItem()
            Assert.assertEquals(bitmap, viewState.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenScreenStartedAndExchangingKeysEnabledThenExchangeKeysUsedInQrCode() = runTest {
        val expectedBitmap = configureExchangeKeysSupported().first

        testee.viewState().test {
            val viewState = awaitItem()
            Assert.assertEquals(expectedBitmap, viewState.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateRecoveryQRFailsThenFinishWithError() = runTest {
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "error"))
        testee.viewState().test {
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
    fun whenGenerateRecoveryQRFailsAndExchangingKeysEnabledThenFinishWithError() = runTest {
        configureExchangeKeysSupported()
        whenever(syncRepository.generateExchangeInvitationCode()).thenReturn(Result.Error(reason = "error"))
        testee.viewState().test {
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
    fun whenOnCopyCodeClickedThenShowMessage() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        // need to ensure view state is started
        testee.viewState().test { cancelAndConsumeRemainingEvents() }

        testee.onCopyCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedAndExchangingKeysEnabledThenShowMessage() = runTest {
        configureExchangeKeysSupported()

        // need to ensure view state is started
        testee.viewState().test { cancelAndConsumeRemainingEvents() }

        testee.onCopyCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedThenCopyCodeToClipboard() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        // need to ensure view state is started
        testee.viewState().test { cancelAndConsumeRemainingEvents() }

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(authCodeToUse.rawCode)
    }

    @Test
    fun whenOnCopyCodeClickedAndExchangingKeysEnabledThenCopyCodeToClipboard() = runTest {
        val expectedJson = configureExchangeKeysSupported().second.rawCode

        // need to ensure view state is started
        testee.viewState().test { cancelAndConsumeRemainingEvents() }

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(expectedJson)
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
    fun whenUserScansRecoveryCodeButSignedInThenCommandIsError() = runTest {
        syncFeature.seamlessAccountSwitching().setRawStoredState(State(false))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_EXCHANGE))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeAndExchangingKeysEnabledButSignedInThenCommandIsError() = runTest {
        configureExchangeKeysSupported()
        syncFeature.seamlessAccountSwitching().setRawStoredState(State(false))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_EXCHANGE))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeButSignedInThenCommandIsAskToSwitchAccount() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.AskToSwitchAccount)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_EXCHANGE))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeAndExchangingKeysEnabledButSignedInThenCommandIsAskToSwitchAccount() = runTest {
        configureExchangeKeysSupported()
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.AskToSwitchAccount)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_EXCHANGE))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansExchangeCodeAndExchangingKeysEnabledThenCommandIsLoginSuccess() = runTest {
        val exchangeJson = configureExchangeKeysSupported().second.qrCode

        // configure success response: logged in
        whenever(syncRepository.pollForRecoveryCodeAndLogin()).thenReturn(Success(LoggedIn))

        testee.commands().test {
            testee.onQRCodeScanned(exchangeJson.encodeB64())
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansExchangeCodeAndExchangingKeysEnabledButAccountSwitchingRequiredThenCommandIsAskToSwitchAccount() = runTest {
        val exchangeJson = configureExchangeKeysSupported().second.qrCode

        // configure success response: account switching required
        whenever(syncRepository.pollForRecoveryCodeAndLogin()).thenReturn(Success(AccountSwitchingRequired(encryptedRecoveryCode)))

        testee.commands().test {
            testee.onQRCodeScanned(exchangeJson.encodeB64())
            val command = awaitItem()
            assertTrue(command is AskToSwitchAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserAcceptsToSwitchAccountThenPerformAction() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.logoutAndJoinNewAccount(jsonRecoveryKeyEncoded)).thenAnswer {
            whenever(syncRepository.getAccountInfo()).thenReturn(accountB)
            Success(true)
        }

        testee.onUserAcceptedJoiningNewAccount(jsonRecoveryKeyEncoded)

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is SwitchAccountSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserAcceptsToSwitchAccountAndExchangingKeysEnabledThenPerformAction() = runTest {
        configureExchangeKeysSupported()
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.logoutAndJoinNewAccount(jsonRecoveryKeyEncoded)).thenAnswer {
            whenever(syncRepository.getAccountInfo()).thenReturn(accountB)
            Success(true)
        }

        testee.onUserAcceptedJoiningNewAccount(jsonRecoveryKeyEncoded)

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is SwitchAccountSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedInUserScansRecoveryCodeAndLoginSucceedsThenReturnSwitchAccount() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenAnswer {
            whenever(syncRepository.getAccountInfo()).thenReturn(accountB)
            Success(true)
        }

        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is SwitchAccountSuccess)
            verify(syncPixels, times(1)).fireLoginPixel()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedOutUserScansRecoveryCodeAndLoginSucceedsThenReturnLoginSuccess() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenAnswer {
            whenever(syncRepository.getAccountInfo()).thenReturn(accountB)
            Success(true)
        }

        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            verify(syncPixels, times(1)).fireLoginPixel()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryQRCodeAndConnectDeviceFailsThenCommandIsError() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(syncRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Result.Error(code = LOGIN_FAILED.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verify(syncPixels).fireBarcodeScannerParseSuccess(eq(SyncPixels.ScreenType.SYNC_EXCHANGE))
            verify(syncPixels, never()).fireLoginPixel()
            verify(syncPixels, never()).fireSyncSetupFinishedSuccessfully(any())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserCancelsThenAbandonedPixelFired() = runTest {
        testee.onUserCancelledWithoutSyncSetup()
        verify(syncPixels).fireSyncSetupAbandoned(eq(SYNC_EXCHANGE))
    }

    @Test
    fun whenBarcodeShownThenPixelFired() = runTest {
        testee.onBarcodeScreenShown()
        verify(syncPixels).fireSyncBarcodeScreenShown(eq(SYNC_EXCHANGE))
    }

    private fun configureExchangeKeysSupported(): Pair<Bitmap, AuthCode> {
        syncFeature.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(true))
        whenever(syncRepository.pollSecondDeviceExchangeAcknowledgement()).thenReturn(Success(true))
        whenever(syncRepository.parseSyncAuthCode(any())).thenReturn(Exchange(InvitationCode("", "")))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val bitmap = TestSyncFixtures.qrBitmap()
        val jsonExchangeKey = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey)
        val authCodeToUse = AuthCode(qrCode = jsonExchangeKey, rawCode = "something else")
        whenever(syncRepository.generateExchangeInvitationCode()).thenReturn(Success(authCodeToUse))
        whenever(qrEncoder.encodeAsBitmap(eq(jsonExchangeKey), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.processCode(any(), anyOrNull())).thenReturn(Success(true))
        return Pair(bitmap, authCodeToUse)
    }

    private fun enableV2(displayOn: Boolean) {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(displayOn))
    }

    private fun presenterSessionStarted(linkingCode: String = "https://duckduckgo.com/sync/pairing?code2=xyz") =
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
    ) = ExchangeV2Event.Transition(
        timestampMs = System.currentTimeMillis(),
        from = from,
        to = to,
        trigger = null,
        localTrigger = localTrigger,
    )

    @Test
    fun whenBothV2FlagsOnThenRunnerStartPresentInvokedAndV1RecoveryNotCalled() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(bitmap)

        testee.viewState().test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            val withQr = awaitItem()
            Assert.assertEquals(bitmap, withQr.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner).startPresent()
        verify(syncRepository, never()).generateExchangeInvitationCode()
        verify(syncRepository, never()).getRecoveryCode()
    }

    @Test
    fun whenMasterFlagOnButCanShowV2OffThenV1PathTaken() = runTest {
        enableV2(displayOn = false)
        val bitmap = TestSyncFixtures.qrBitmap()
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "raw")
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner, never()).startPresent()
        verify(syncRepository).getRecoveryCode()
    }

    @Test
    fun whenMasterFlagOffThenV1PathTakenRegardlessOfDisplayFlag() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(false))
        syncFeature.canShowV2ConnectCode().setRawStoredState(State(true))
        val bitmap = TestSyncFixtures.qrBitmap()
        val authCode = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "raw")
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner, never()).startPresent()
    }

    @Test
    fun whenLinkingCodeReadyThenBarcodeContentsPopulatedAndCopyEmitsUrl() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val url = "https://duckduckgo.com/sync/pairing?code2=copy-me"
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(qrEncoder.encodeAsBitmap(eq(url), any(), any())).thenReturn(bitmap)

        testee.viewState().test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted(linkingCode = url))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.onCopyCodeClicked()
        verify(clipboard).copyToClipboard(url)
    }

    @Test
    fun whenHostConfirmingDuringV2PresentThenAskHostConfirmationCommandEmitted() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(runner.peerName).thenReturn("Peer Phone")
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState().test {
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
    fun whenHostDoneDuringV2PresentThenLoginSuccessShowRecoveryFalse() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState().test {
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
            Assert.assertEquals(false, (command as LoginSuccess).showRecovery)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSameAccountAbortDuringV2PresentThenShowAlreadyPaired() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState().test {
            awaitItem()
            runnerEventsFlow.emit(presenterSessionStarted())
            awaitItem()
            runnerEventsFlow.emit(
                transition(from = ExchangeV2State.Negotiating, to = ExchangeV2State.SameAccountAbort),
            )
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue("expected ShowV2Error, got $command", command is ShowV2Error)
            assertEquals(v2AlreadyPairedError, (command as ShowV2Error).content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenHostAbortedUserDeniedDuringV2PresentThenShowErrorCommandEmitted() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState().test {
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
            assertTrue("expected ShowV2Error, got $command", command is ShowV2Error)
            assertEquals(PAIRING_CANCELLED.code.toV2PairingError(), (command as ShowV2Error).content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSessionErrorDuringV2PresentThenShowErrorCommandEmitted() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())

        testee.viewState().test {
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
            assertTrue("expected ShowV2Error, got $command", command is ShowV2Error)
            assertEquals(PAIRING_FAILED.code.toV2PairingError(), (command as ShowV2Error).content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenViewStateReCollectedThenExchangeInvitationGeneratedOnce() = runTest {
        configureExchangeKeysSupported()
        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(syncRepository, times(1)).generateExchangeInvitationCode()
    }

    @Test
    fun whenViewStateReCollectedThenV2PresentStartedOnce() = runTest {
        enableV2(displayOn = true)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(qrEncoder.encodeAsBitmap(any(), any(), any())).thenReturn(TestSyncFixtures.qrBitmap())
        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        verify(runner, times(1)).startPresent()
    }

    @Test
    fun whenV2PairingRejectedByPeerThenShowError() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val scannedCode = "https://duckduckgo.com/sync/pairing/#&code2=v2code"
        whenever(qrCode.parse(scannedCode)).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )

        testee.commands().test {
            testee.onQRCodeScanned(scannedCode)
            runnerEventsFlow.emit(
                ExchangeV2Event.Transition(
                    timestampMs = System.currentTimeMillis(),
                    from = ExchangeV2State.Joiner.Confirming,
                    to = ExchangeV2State.Joiner.AbortedByHost,
                    trigger = ExchangeV2Message.RecoveryCodeDenied(rawJson = "{}"),
                    localTrigger = null,
                ),
            )
            val command = awaitItem()
            assertTrue("expected ShowV2Error, got $command", command is ShowV2Error)
            assertEquals(PAIRING_REJECTED.code.toV2PairingError(), (command as ShowV2Error).content)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenV2UpgradeRequiredThenShowUpdateError() = runTest {
        syncFeature.canUseV2ConnectFlow().setRawStoredState(State(true))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val scannedCode = "https://duckduckgo.com/sync/pairing/#&code2=v2code"
        whenever(qrCode.parse(scannedCode)).thenReturn(
            ExchangeV2CodeParseResult.LinkingV2(channelId = "c", publicKey = "k", version = "2"),
        )
        whenever(runner.eventsSince(any())).thenReturn(
            flowOf(ExchangeV2Event.SessionError(timestampMs = 0L, message = "Peer requires protocol v3; please update this app")),
        )

        testee.commands().test {
            testee.onQRCodeScanned(scannedCode)
            val command = awaitItem()
            assertTrue("expected ShowV2Error, got $command", command is ShowV2Error)
            assertEquals(v2UpgradeRequiredError, (command as ShowV2Error).content)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
