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

import android.content.Context
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestoreManager
import com.duckduckgo.sync.impl.pixels.SyncAccountOperation
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.CheckStoragePermission
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShareRecoveryCodeFile
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.v2.RecoveryCodeActivityViewModel.Command.ShowMessage
import com.duckduckgo.sync.impl.wideevents.SyncSetupWideEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

class RecoveryCodeActivityViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val authCode = AuthCode(qrCode = "qr-code", rawCode = "raw-code")

    private val syncAccountRepository = mock<SyncAccountRepository>()

    private val syncAutoRestoreManager = mock<SyncAutoRestoreManager> {
        onBlocking { isAutoRestoreAvailable() } doReturn false
    }
    private val recoveryCodePDF = mock<RecoveryCodePDF>()
    private val clipboard = mock<Clipboard>()
    private val syncPixels = mock<SyncPixels>()
    private val syncSetupWideEvent = mock<SyncSetupWideEvent>()
    private val context = mock<Context>()

    // Lazy becasue it creates the account on init. CoroutineTestRule must replace the main dispatcher,
    // and mocks must be configured before creation.
    private val testee by lazy {
        RecoveryCodeActivityViewModel(
            syncAccountRepository = syncAccountRepository,
            syncAutoRestoreManager = syncAutoRestoreManager,
            recoveryCodePDF = recoveryCodePDF,
            clipboard = clipboard,
            syncPixels = syncPixels,
            syncSetupWideEvent = syncSetupWideEvent,
            dispatchers = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when signed in then the recovery code is shown in the view state`() = runTest {
        givenRecoveryCodeAvailable()

        testee.viewState.test {
            assertEquals("raw-code", awaitItem().recoveryCode)

            cancel()
        }
    }

    @Test
    fun `when the recovery code is shown then the wide event records it`() = runTest {
        givenRecoveryCodeAvailable()

        testee.viewState.test {
            verify(syncSetupWideEvent).onRecoveryCodeShown()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when fetching the recovery code fails then an error is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "boom"))

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowError>(command)
            assertEquals(R.string.sync_device_v2_recovery_code_get_code_error, command.message)
            assertEquals("boom", command.reason)

            cancel()
        }
    }

    @Test
    fun `when fetching the recovery code fails then the wide event records the failure`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Error(reason = "boom"))

        testee.viewState.test {
            verify(syncSetupWideEvent).onRecoveryCodeGenerationFailed()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when not signed in then an account is created and the recovery code is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))

        testee.viewState.test {
            assertEquals("raw-code", awaitItem().recoveryCode)

            cancel()
        }

        verify(syncAccountRepository).createAccount()
    }

    @Test
    fun `when creating the account fails then an error is shown`() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Error(reason = "boom"))

        testee.commands.test {
            val command = awaitItem()
            assertIs<ShowError>(command)
            assertEquals(R.string.sync_create_account_generic_error, command.message)

            cancel()
        }
    }

    @Test
    fun `when auto restore is available then the view state reflects it`() = runTest {
        givenRecoveryCodeAvailable()
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(true)

        testee.viewState.test {
            assertTrue(awaitItem().isAutoRestoreAvailable)

            cancel()
        }
    }

    @Test
    fun `when auto restore is not available then the view state reflects it`() = runTest {
        givenRecoveryCodeAvailable()
        whenever(syncAutoRestoreManager.isAutoRestoreAvailable()).thenReturn(false)

        testee.viewState.test {
            assertFalse(awaitItem().isAutoRestoreAvailable)

            cancel()
        }
    }

    @Test
    fun `when the recovery code sheet is generated then the pdf is created from the recovery code`() = runTest {
        givenRecoveryCodeAvailable()
        whenever(recoveryCodePDF.generateAndStoreRecoveryCodePDF(any(), any())).thenReturn(File("recovery-code.pdf"))

        testee.generateRecoveryCodeSheet(context)

        verify(recoveryCodePDF).generateAndStoreRecoveryCodePDF(context, "raw-code")
    }

    @Test
    fun `when the recovery code sheet is generated then the pdf file is shared`() = runTest {
        givenRecoveryCodeAvailable()
        val pdfFile = File("recovery-code.pdf")
        whenever(recoveryCodePDF.generateAndStoreRecoveryCodePDF(any(), any())).thenReturn(pdfFile)

        testee.commands.test {
            testee.generateRecoveryCodeSheet(context)

            val command = awaitItem()
            assertIs<ShareRecoveryCodeFile>(command)
            assertEquals(pdfFile, command.pdfFile)

            cancel()
        }
    }

    @Test
    fun `when generating the recovery code pdf fails then an error is shown`() = runTest {
        givenRecoveryCodeAvailable()
        whenever(recoveryCodePDF.generateAndStoreRecoveryCodePDF(any(), any())).thenThrow(RuntimeException("boom"))

        testee.commands.test {
            testee.generateRecoveryCodeSheet(context)

            val command = awaitItem()
            assertIs<ShowError>(command)
            assertEquals(R.string.sync_recovery_pdf_error, command.message)

            cancel()
        }
    }

    @Test
    fun `when generating the recovery code pdf fails then an error pixel is fired`() = runTest {
        givenRecoveryCodeAvailable()
        whenever(recoveryCodePDF.generateAndStoreRecoveryCodePDF(any(), any())).thenThrow(RuntimeException("boom"))

        testee.generateRecoveryCodeSheet(context)

        verify(syncPixels).fireSyncAccountErrorPixel(any(), eq(SyncAccountOperation.CREATE_PDF))
    }

    @Test
    fun `when restore on reinstall is changed then the view state reflects it`() = runTest {
        givenRecoveryCodeAvailable()

        testee.changeRestoreOnReinstall(false)
        assertFalse(testee.viewState.value.isAutoRestoreEnabled)

        testee.changeRestoreOnReinstall(true)
        assertTrue(testee.viewState.value.isAutoRestoreEnabled)
    }

    @Test
    fun `when a message is requested then the show message command is sent`() = runTest {
        givenRecoveryCodeAvailable()

        testee.commands.test {
            testee.showMessage(R.string.sync_code_copied_message)

            val command = awaitItem()
            assertIs<ShowMessage>(command)
            assertEquals(R.string.sync_code_copied_message, command.message)

            cancel()
        }
    }

    @Test
    fun `when the user copies the code then it is copied to the clipboard`() = runTest {
        givenRecoveryCodeAvailable()

        testee.onCopyCodeClicked()

        verify(clipboard).copyToClipboard("raw-code")
    }

    @Test
    fun `when the user copies the code then a confirmation message is shown`() = runTest {
        givenRecoveryCodeAvailable()

        testee.commands.test {
            testee.onCopyCodeClicked()

            val command = awaitItem()
            assertIs<ShowMessage>(command)
            assertEquals(R.string.sync_code_copied_message, command.message)

            cancel()
        }
    }

    @Test
    fun `when the user downloads the code then the storage permission check is requested`() = runTest {
        givenRecoveryCodeAvailable()

        testee.commands.test {
            testee.onDownloadCodeClicked()
            assertIs<CheckStoragePermission>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the user clicks done then the screen closes`() = runTest {
        givenRecoveryCodeAvailable()

        testee.commands.test {
            testee.onDoneClicked()
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    @Test
    fun `when the error dialog is dismissed then the screen closes`() = runTest {
        givenRecoveryCodeAvailable()

        testee.commands.test {
            testee.onErrorDialogDismissed()
            assertIs<Close>(awaitItem())

            cancel()
        }
    }

    private fun givenRecoveryCodeAvailable() {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCode))
    }
}

@OptIn(ExperimentalContracts::class)
private inline fun <reified T> assertIs(value: Any?) {
    contract {
        returns() implies (value is T)
    }
    assertTrue("Expected ${T::class.simpleName} but was ${value?.let { it::class.simpleName }}", value is T)
}
