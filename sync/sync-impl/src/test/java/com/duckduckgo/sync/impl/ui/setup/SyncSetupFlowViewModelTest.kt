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

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.sync.impl.SyncAccountRepository
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.Command.*
import com.duckduckgo.sync.impl.ui.setup.SyncSetupFlowViewModel.ViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@ExperimentalCoroutinesApi
class SyncSetupFlowViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncAccountRepository = mock()

    private val testee = SyncSetupFlowViewModel(
        syncRepostitory,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenInitialSetupScreenThenViewStateIsEnableSync() = runTest {
        testee.viewState(ViewMode.InitialSetupScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.InitialSetupScreen)
        }
    }

    @Test
    fun whenSyncAnotherDeviceScreenThenViewStateIsSyncAnotherDevice() = runTest {
        testee.viewState(ViewMode.SyncAnotherDeviceScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.SyncAnotherDeviceScreen)
        }
    }

    @Test
    fun whenUserClicksOnTurnOnSyncThenCommandIsAskSyncAnotherDevice() = runTest {
        testee.viewState(ViewMode.InitialSetupScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.InitialSetupScreen)
            testee.onTurnOnSyncClicked()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is AskSyncAnotherDevice)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnRecoverYourSyncDataThenCommandIsRecoverSyncData() = runTest {
        testee.viewState(ViewMode.InitialSetupScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.InitialSetupScreen)
            testee.onRecoverYourSyncDataClicked()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is RecoverSyncData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClicksOnNotNowThenFinishSetupFlow() = runTest {
        testee.viewState(ViewMode.SyncAnotherDeviceScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.SyncAnotherDeviceScreen)
            testee.onNotNowClicked()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is FinishSetupFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUserClickOnCloseThenCommandIsAbortFlow() = runTest {
        testee.viewState(ViewMode.SyncAnotherDeviceScreen).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.SyncAnotherDeviceScreen)
            testee.onCloseClicked()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is AbortFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
