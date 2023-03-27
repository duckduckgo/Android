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
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncRepository
import com.duckduckgo.sync.impl.ui.ShowQRCodeViewModel.Command
import com.duckduckgo.sync.impl.ui.ShowQRCodeViewModel.Command.LoginSucess
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ShowQRCodeViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncRepository = mock()

    private val testee = ShowQRCodeViewModel(
        syncRepostitory,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenScreenStartedThenShowQRCode() = runTest {
        whenever(syncRepostitory.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))
        whenever(syncRepostitory.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.qrCode == jsonConnectKeyEncoded)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenGenerateConnectQRFailsThenSendError() = runTest {
        whenever(syncRepostitory.getConnectQR()).thenReturn(Result.Error(reason = ""))
        whenever(syncRepostitory.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState().test{
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenConnectionKeysSuccessThenLoginSuccess() = runTest {
        whenever(syncRepostitory.getConnectQR()).thenReturn(Result.Success(jsonConnectKeyEncoded))
        whenever(syncRepostitory.pollConnectionKeys()).thenReturn(Result.Success(true))
        testee.viewState().test{
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
