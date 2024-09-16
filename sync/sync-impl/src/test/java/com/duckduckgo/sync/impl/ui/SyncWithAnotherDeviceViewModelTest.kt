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
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.QREncoder
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncWithAnotherActivityViewModel.Command.LoginSuccess
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
class SyncWithAnotherDeviceViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val qrEncoder: QREncoder = mock()
    private val syncPixels: SyncPixels = mock()

    private val testee = SyncWithAnotherActivityViewModel(
        syncRepository,
        qrEncoder,
        clipboard,
        syncPixels,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        val bitmap = TestSyncFixtures.qrBitmap()
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(jsonRecoveryKeyEncoded))
        whenever(qrEncoder.encodeAsBitmap(eq(jsonRecoveryKeyEncoded), any(), any())).thenReturn(bitmap)
        testee.viewState().test {
            val viewState = awaitItem()
            Assert.assertEquals(bitmap, viewState.qrCodeBitmap)
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
    fun whenOnCopyCodeClickedThenShowMessage() = runTest {
        whenever(syncRepository.getRecoveryCode()).thenReturn(Result.Success(jsonRecoveryKeyEncoded))

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

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard(jsonRecoveryKeyEncoded)
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
    fun whenUserScansRecoveryQRCodeAndConnectDeviceFailsThenCommandIsError() = runTest {
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
}
