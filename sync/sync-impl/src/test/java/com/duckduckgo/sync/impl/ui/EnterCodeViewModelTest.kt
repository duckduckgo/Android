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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.TestSyncFixtures.jsonConnectKeyEncoded
import com.duckduckgo.sync.TestSyncFixtures.jsonRecoveryKeyEncoded
import com.duckduckgo.sync.impl.Clipboard
import com.duckduckgo.sync.impl.Result.Error
import com.duckduckgo.sync.impl.Result.Success
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.EnterCodeActivity.Companion.Code
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.EnterCodeViewModel.Command.LoginSucess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class EnterCodeViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncRepository = mock()
    private val clipboard: Clipboard = mock()

    private val testee = EnterCodeViewModel(
        syncRepostitory,
        clipboard,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUserClicksOnPasteCodeThenClipboardIsPasted() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)

        testee.onPasteCodeClicked(Code.RECOVERY_CODE)

        verify(clipboard).pasteFromClipboard()
    }

    @Test
    fun whenUserClicksOnPasteCodeWithRecoveryCodeThenLoginWithCode() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncRepostitory.login(jsonRecoveryKeyEncoded)).thenReturn(Success(true))

        testee.onPasteCodeClicked(Code.RECOVERY_CODE)

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnPasteCodeWithConnectCodeThenConnectWithCode() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonConnectKeyEncoded)
        whenever(syncRepostitory.connectDevice(jsonConnectKeyEncoded)).thenReturn(Success(true))

        testee.onPasteCodeClicked(Code.CONNECT_CODE)

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPastedCodeFailsThenEmitError() = runTest {
        whenever(clipboard.pasteFromClipboard()).thenReturn(jsonRecoveryKeyEncoded)
        whenever(syncRepostitory.login(jsonRecoveryKeyEncoded)).thenReturn(Error(reason = "error"))

        testee.onPasteCodeClicked(Code.RECOVERY_CODE)

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is Command.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
