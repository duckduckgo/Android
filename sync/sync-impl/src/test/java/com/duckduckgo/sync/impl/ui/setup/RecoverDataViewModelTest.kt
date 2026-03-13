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

package com.duckduckgo.sync.impl.ui.setup

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.persistentstorage.api.PersistentStorage
import com.duckduckgo.persistentstorage.api.PersistentStorageAvailability
import com.duckduckgo.sync.TestSyncFixtures.accountCreatedFailInvalid
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.pdfFile
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.RecoveryCodePDF
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAccountRepository.AuthCode
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.autorestore.SyncAutoRestorePreferenceManager
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.Close
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.Next
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.Command.RecoveryCodePDFSuccess
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.ViewMode.CreatingAccount
import com.duckduckgo.sync.impl.ui.setup.RecoverDataViewModel.ViewMode.SignedIn
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class RecoverDataViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val recoveryPDF: RecoveryCodePDF = mock()
    private val syncAccountRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val syncPixels: SyncPixels = mock()
    private val syncAutoRestoreManager: SyncAutoRestorePreferenceManager = mock()
    private val persistentStorage: PersistentStorage = mock()

    @SuppressLint("DenyListedApi")
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
        syncAutoRestore().setRawStoredState(State(enable = true))
    }

    private val testee = RecoverDataViewModel(
        recoveryPDF,
        syncAccountRepository,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
        syncPixels,
        syncAutoRestoreManager,
        persistentStorage,
        syncFeature,
    )

    @Test
    fun whenUserIsNotSignedInThenAccountCreatedAndViewStateUpdated() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(Result.Success(true))
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            assertTrue(viewState.restoreOnReinstallEnabled)
            assertTrue(viewState.showRestoreOnReinstall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedInThenShowViewState() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPersistentStorageUnavailableThenShowRestoreOnReinstallFalse() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Unavailable)

        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            assertFalse(viewState.showRestoreOnReinstall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCreateAccountFailsThenEmitShowError() = runTest {
        whenever(syncAccountRepository.isSignedIn()).thenReturn(false)
        whenever(syncAccountRepository.createAccount()).thenReturn(accountCreatedFailInvalid)
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Unavailable)

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
    fun whenUserClicksNextThenSetsRestorePreferenceAndEmitsNext() = runTest {
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        testee.commands().test {
            testee.onNextClicked()
            val command = awaitItem()
            assertTrue(command is Next)
            verify(syncAutoRestoreManager).setRestoreOnReinstallEnabled(true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleChangedToFalseAndNextClickedThenSetsRestoreDisabled() = runTest {
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        testee.onToggleChanged(false)

        testee.commands().test {
            testee.onNextClicked()
            val command = awaitItem()
            assertTrue(command is Next)
            verify(syncAutoRestoreManager).setRestoreOnReinstallEnabled(false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenBackPressedThenPersistsToggleAndEmitsClose() = runTest {
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        testee.commands().test {
            testee.onBackPressed()
            val command = awaitItem()
            assertTrue(command is Close)
            verify(syncAutoRestoreManager).setRestoreOnReinstallEnabled(true)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleChangedToFalseAndBackPressedThenSetsRestoreDisabled() = runTest {
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)

        testee.onToggleChanged(false)

        testee.commands().test {
            testee.onBackPressed()
            val command = awaitItem()
            assertTrue(command is Close)
            verify(syncAutoRestoreManager).setRestoreOnReinstallEnabled(false)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnDownloadAsPdfThenEmitCheckPermissionCommand() = runTest {
        testee.commands().test {
            testee.onDownloadAsPdfClicked()
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

    @Test
    fun whenSyncAutoRestoreFFDisabledThenShowRestoreOnReinstallFalse() = runTest {
        val disabledFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
            syncAutoRestore().setRawStoredState(State(enable = false))
        }
        val vm = RecoverDataViewModel(
            recoveryPDF,
            syncAccountRepository,
            clipboard,
            coroutineTestRule.testDispatcherProvider,
            syncPixels,
            syncAutoRestoreManager,
            persistentStorage,
            disabledFeature,
        )
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))

        vm.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is SignedIn)
            assertFalse(viewState.showRestoreOnReinstall)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenToggleChangedThenViewStateUpdated() = runTest {
        whenever(syncAutoRestoreManager.isRestoreOnReinstallEnabled()).thenReturn(true)
        whenever(syncAccountRepository.isSignedIn()).thenReturn(true)
        val authCodeToUse = AuthCode(qrCode = jsonRecoveryKeyEncoded, rawCode = "something else")
        whenever(syncAccountRepository.getRecoveryCode()).thenReturn(Result.Success(authCodeToUse))
        whenever(persistentStorage.checkAvailability()).thenReturn(PersistentStorageAvailability.Available(isEndToEndEncryptionSupported = true))

        testee.viewState().test {
            val initialState = awaitItem()
            assertTrue(initialState.restoreOnReinstallEnabled)

            testee.onToggleChanged(false)
            val updatedState = awaitItem()
            assertFalse(updatedState.restoreOnReinstallEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
