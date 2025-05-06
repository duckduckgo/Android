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
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.primaryDeviceKeyId
import com.duckduckgo.sync.TestSyncFixtures.validLoginKeys
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.CodeType.EXCHANGE
import com.duckduckgo.sync.impl.ExchangeResult.AccountSwitchingRequired
import com.duckduckgo.sync.impl.ExchangeResult.LoggedIn
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.encodeB64
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.SwitchAccountSuccess
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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

    private val testee = SyncWithAnotherActivityViewModel(
        syncRepository,
        qrEncoder,
        clipboard,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
        syncFeature,
    )

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(jsonRecoveryKeyEncoded))
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
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(jsonRecoveryKeyEncoded))

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
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(jsonRecoveryKeyEncoded))

        // need to ensure view state is started
        testee.viewState().test { cancelAndConsumeRemainingEvents() }

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(jsonRecoveryKeyEncoded)
    }

    @Test
    fun whenOnCopyCodeClickedAndExchangingKeysEnabledThenCopyCodeToClipboard() = runTest {
        val expectedJson = configureExchangeKeysSupported().second

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
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verifyNoInteractions(syncPixels)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeAndExchangingKeysEnabledButSignedInThenCommandIsError() = runTest {
        configureExchangeKeysSupported()
        syncFeature.seamlessAccountSwitching().setRawStoredState(State(false))
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verifyNoInteractions(syncPixels)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeButSignedInThenCommandIsAskToSwitchAccount() = runTest {
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.AskToSwitchAccount)
            verifyNoInteractions(syncPixels)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeAndExchangingKeysEnabledButSignedInThenCommandIsAskToSwitchAccount() = runTest {
        configureExchangeKeysSupported()
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Result.Error(code = ALREADY_SIGNED_IN.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.AskToSwitchAccount)
            verifyNoInteractions(syncPixels)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansExchangeCodeAndExchangingKeysEnabledThenCommandIsLoginSuccess() = runTest {
        val exchangeJson = configureExchangeKeysSupported().second

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
        val exchangeJson = configureExchangeKeysSupported().second

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
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenAnswer {
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
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenAnswer {
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
        whenever(syncRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Result.Error(code = LOGIN_FAILED.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonRecoveryKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            verifyNoInteractions(syncPixels)
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
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun configureExchangeKeysSupported(): Pair<Bitmap, String> {
        syncFeature.exchangeKeysToSyncWithAnotherDevice().setRawStoredState(State(true))
        whenever(syncRepository.pollSecondDeviceExchangeAcknowledgement()).thenReturn(Success(true))
        whenever(syncRepository.getCodeType(any())).thenReturn(EXCHANGE)
        whenever(syncRepository.getAccountInfo()).thenReturn(accountA)
        val bitmap = TestSyncFixtures.qrBitmap()
        val jsonExchangeKey = jsonExchangeKey(primaryDeviceKeyId, validLoginKeys.primaryKey).also {
            whenever(syncRepository.generateExchangeInvitationCode()).thenReturn(Success(it))
            whenever(qrEncoder.encodeAsBitmap(eq(it), any(), any())).thenReturn(bitmap)
        }
        whenever(syncRepository.processCode(any())).thenReturn(Success(true))
        return Pair(bitmap, jsonExchangeKey)
    }
}
