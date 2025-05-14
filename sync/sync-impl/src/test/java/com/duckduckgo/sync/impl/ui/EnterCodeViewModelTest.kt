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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.sync.SyncAccountFixtures.accountA
import com.duckduckgo.sync.SyncAccountFixtures.accountB
import com.duckduckgo.sync.SyncAccountFixtures.noAccount
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKey
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKey
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.primaryKey
import com.duckduckgo.sync.impl.AccountErrorCodes.ALREADY_SIGNED_IN
import com.duckduckgo.sync.impl.AccountErrorCodes.CONNECT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.CREATE_ACCOUNT_FAILED
import com.duckduckgo.sync.impl.AccountErrorCodes.GENERIC_ERROR
import com.duckduckgo.sync.impl.AccountErrorCodes.INVALID_CODE
import com.duckduckgo.sync.impl.AccountErrorCodes.LOGIN_FAILED
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.RecoveryCode
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.SyncAuthCode.Recovery
import com.duckduckgo.sync.impl.SyncAuthCode.Unknown
import com.duckduckgo.sync.impl.SyncFeature
import com.duckduckgo.sync.impl.pixels.SyncPixels
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Idle
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.AskToSwitchAccount
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSuccess
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.ShowError
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.SwitchAccountSuccess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class EnterCodeViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncAccountRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()
    private val syncFeature = FakeFeatureToggleFactory.create(SyncFeature::class.java).apply {
        this.seamlessAccountSwitching().setRawStoredState(State(true))
    }
    private val syncPixels: SyncPixels = mock()

    private val testee = EnterCodeViewModel(
        syncAccountRepository,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
        syncFeature = syncFeature,
        syncPixels = syncPixels,
    )

    @Test
    fun whenUIStartsThenViewStateIsIdle() = runTest {
        testee.viewState().test {
            val item = awaitItem()
            assertTrue(item.authState is Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnPasteCodeThenClipboardIsPasted() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)

        testee.onPasteCodeClicked()

        verify(clipboard).pasteFromClipboard()
    }

    @Test
    fun whenUserClicksOnPasteCodeWithRecoveryCodeThenProcessCode() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenAnswer {
            whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
            Success(true)
        }

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnPasteCodeWithConnectCodeThenProcessCode() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonConnectKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonConnectKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonConnectKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenAnswer {
            whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
            Success(true)
        }

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPastedInvalidCodeThenAuthStateError() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn("invalid code")
        whenever(syncAccountRepository.parseSyncAuthCode("invalid code")).thenReturn(Unknown("invalid code"))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = INVALID_CODE.code))

        testee.onPasteCodeClicked()

        testee.viewState().test {
            val item = awaitItem()
            assertTrue(item.authState is AuthState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeButUserSignedInThenShowError() = runTest {
        syncFeature.seamlessAccountSwitching().setRawStoredState(State(false))
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = ALREADY_SIGNED_IN.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeButUserSignedInThenOfferToSwitchAccount() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = ALREADY_SIGNED_IN.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is AskToSwitchAccount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserAcceptsToSwitchAccountThenPerformAction() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncAccountRepository.logoutAndJoinNewAccount(jsonRecoveryKeyEncoded)).thenAnswer {
            whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountB)
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
    fun whenSignedInUserProcessCodeSucceedsAndAccountChangedThenReturnSwitchAccount() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenAnswer {
            whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountB)
            Success(true)
        }
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)

        testee.commands().test {
            testee.onPasteCodeClicked()
            val command = awaitItem()
            assertTrue(command is SwitchAccountSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSignedOutUserScansRecoveryCodeAndLoginSucceedsThenReturnLoginSuccess() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenAnswer {
            whenever(syncAccountRepository.getAccountInfo()).thenReturn(accountA)
            Success(true)
        }
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)

        testee.commands().test {
            testee.onPasteCodeClicked()
            val command = awaitItem()
            assertTrue(command is LoginSuccess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeAndLoginFailsThenShowErrorAndUpdateState() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = LOGIN_FAILED.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeAndLoginFailsThenUpdateStateToIdle() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = LOGIN_FAILED.code))

        testee.onPasteCodeClicked()

        testee.viewState().test {
            assertTrue(awaitItem().authState == Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeAndConnectFailsThenShowError() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonConnectKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonConnectKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonConnectKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = CONNECT_FAILED.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeAndCreateAccountFailsThenShowError() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = CREATE_ACCOUNT_FAILED.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is ShowError)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenProcessCodeAndGenericErrorThenDoNothing() = runTest {
        whenever(syncAccountRepository.getAccountInfo()).thenReturn(noAccount)
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.parseSyncAuthCode(jsonRecoveryKeyEncoded)).thenReturn(Recovery(RecoveryCode(jsonRecoveryKey, primaryKey)))
        whenever(syncAccountRepository.processCode(any())).thenReturn(Error(code = GENERIC_ERROR.code))

        testee.onPasteCodeClicked()

        testee.commands().test {
            cancelAndIgnoreRemainingEvents()
        }
    }
}
