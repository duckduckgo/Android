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
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command.LoginSuccess
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

@RunWith(AndroidJUnit4::class)
class SyncConnectViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val qrEncoder: QREncoder = mock()
    private val syncPixels: SyncPixels = mock()

    private val testee = SyncConnectViewModel(
        syncRepository,
        qrEncoder,
        clipboard,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))
        whenever(qrEncoder.encodeAsBitmap(eq(jsonConnectKeyEncoded), any(), any())).thenReturn(bitmap)
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState().test {
            val viewState = awaitItem()
            Assert.assertEquals(bitmap, viewState.qrCodeBitmap)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateConnectQRFailsThenFinishWithError() = runTest {
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Error(reason = "error"))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
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
    fun whenConnectionKeysSuccessThenLoginSuccess() = runTest {
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            verify(syncPixels).fireSignupConnectPixel()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedThenShowMessage() = runTest {
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))

        testee.onCopyCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnCopyCodeClickedThenCopyCodeToClipboard() = runTest {
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(jsonConnectKeyEncoded)
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
        whenever(syncRepository.processCode(jsonConnectKeyEncoded)).thenReturn(Result.Success(true))
        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
            val command = awaitItem()
            verify(syncPixels).fireLoginPixel()
            assertTrue(command is Command.LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansRecoveryCodeButSignedInThenCommandIsError() = runTest {
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
    fun whenUserScansConnectQRCodeAndConnectDeviceFailsThenCommandIsError() = runTest {
        whenever(syncRepository.processCode(jsonConnectKeyEncoded)).thenReturn(Result.Error(code = CONNECT_FAILED.code))
        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
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

    @Test
    fun whenPollingIfConnectFailsThenShowError() = runTest {
        whenever(syncRepository.pollConnectionKeys()).thenReturn(Result.Error(CONNECT_FAILED.code))
        testee.viewState().test {
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
        testee.viewState().test {
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
        whenever(syncRepository.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))
        whenever(syncRepository.pollConnectionKeys())
            .thenReturn(Result.Error())
            .thenReturn(Result.Success(true))

        testee.viewState().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
            verify(syncRepository, times(2)).pollConnectionKeys()
        }
    }
}
