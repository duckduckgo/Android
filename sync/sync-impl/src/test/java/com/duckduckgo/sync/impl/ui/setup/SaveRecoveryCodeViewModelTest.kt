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

package com.duckduckgo.sync.impl.ui.setup

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailInvalid
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.pdfFile
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.Next
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.SaveRecoveryCodeViewModel.ViewMode.SignedIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SaveRecoveryCodeViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryPDF: RecoveryCodePDF = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val syncPixels: SyncPixels = mock()

    private val testee = SaveRecoveryCodeViewModel(
        recoveryPDF,
        syncAccountRepository,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
        syncPixels,
    )

    @Test
    fun whenUserIsNotSignedInThenAccountCreatedAndViewStateUpdated() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowViewState() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateAccountFailsThenEmitFinishWithError() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(accountCreatedFailInvalid)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is CreatingAccount)
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksNextThenFinishFlow() = runTest {
        testee.commands().test {
            testee.onNextClicked()
            val command = awaitItem()
            assertTrue(command is Next)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnSaveRecoveryCodeThenEmitCheckIfUserHasPermissionCommand() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.commands().test {
            testee.onSaveRecoveryCodeClicked()
            val command = awaitItem()
            assertTrue(command is Command.CheckIfUserHasStoragePermission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateRecoveryCodeThenGenerateFileAndEmitSuccessCommand() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(recoveryPDF.generateAndStoreRecoveryCodePDF(any(), eq(authCodeToUse.rawCode))).thenReturn(pdfFile())

        testee.commands().test {
            testee.generateRecoveryCode(mock())
            val command = awaitItem()
            assertTrue(command is RecoveryCodePDFSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksCopyThenCopyToClipboard() = runTest {
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        testee.commands().test {
            testee.onCopyCodeClicked()
            val command = awaitItem()
            verify(clipboard).copyToClipboard(eq("something else"))
            assertTrue(command is Command.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
