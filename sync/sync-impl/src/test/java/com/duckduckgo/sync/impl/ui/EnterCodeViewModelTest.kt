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
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.AuthState.Idle
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSucess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
internal class EnterCodeViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncAccountRepository: SyncAccountRepository = mock()
    private val clipboard: Clipboard = mock()

    private val testee = EnterCodeViewModel(
        syncAccountRepository,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
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
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)

        testee.onPasteCodeClicked()

        verify(clipboard).pasteFromClipboard()
    }

    @Test
    fun whenUserClicksOnPasteCodeWithRecoveryCodeThenProcessCode() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Success(true))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnPasteCodeWithConnectCodeThenProcessCode() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonConnectKeyEncoded)
        whenever(syncAccountRepository.processCode(jsonConnectKeyEncoded)).thenReturn(Success(true))

        testee.onPasteCodeClicked()

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPastedCodeFailsThenEmitError() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncAccountRepository.processCode(jsonRecoveryKeyEncoded)).thenReturn(Error(reason = "error"))

        testee.onPasteCodeClicked()

        testee.viewState().test {
            val item = awaitItem()
            assertTrue(item.authState is AuthState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
