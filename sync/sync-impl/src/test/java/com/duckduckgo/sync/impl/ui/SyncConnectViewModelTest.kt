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
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.Command
import com.duckduckgo.sync.impl.ui.SyncConnectViewModel.ViewMode
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
class SyncConnectViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncAccountRepository = mock()

    private val testee = SyncConnectViewModel(
        syncRepostitory,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenUserNotSignedInThenViewModeIsUnAuthenticated() = runTest {
        whenever(syncRepostitory.isSignedIn()).thenReturn(false)
        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.UnAuthenticated)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserSignedIntThenViewModeIsSignedIn() = runTest {
        whenever(syncRepostitory.isSignedIn()).thenReturn(true)
        testee.viewState().test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.SignedIn)
            cancelAndIgnoreRemainingEvents()
        }
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
        whenever(syncRepostitory.processCode(jsonConnectKeyEncoded)).thenReturn(Result.Success(true))
        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserScansConnectQRCodeAndConnectDeviceFailsThenCommandIsError() = runTest {
        whenever(syncRepostitory.processCode(jsonConnectKeyEncoded)).thenReturn(Result.Error(reason = "error"))
        testee.commands().test {
            testee.onQRCodeScanned(jsonConnectKeyEncoded)
            val command = awaitItem()
            assertTrue(command is Command.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUseClicksOnShowQRCodeThenCommandIsShowQRCode() = runTest {
        testee.commands().test {
            testee.onShowQRCodeClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQRCode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenLoginSucceedsThenCommandIsLoginSuccess() = runTest {
        testee.commands().test {
            testee.onLoginSucess()
            val command = awaitItem()
            assertTrue(command is Command.LoginSucess)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
