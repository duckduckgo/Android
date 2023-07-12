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
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.Command
import com.duckduckgo.sync.impl.ui.setup.SetupAccountViewModel.ViewMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@ExperimentalCoroutinesApi
class SetupAccountViewModelTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncAccountRepository = mock()

    private val testee = SetupAccountViewModel(
        syncRepostitory,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenOnBackPressedAndViewModeSyncAnotherDeviceThenViewModeEnableSync() = runTest {
        testee.viewState(Screen.SETUP).test {
            var viewState = awaitItem()
            testee.onAskSyncAnotherDevice()
            viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.AskSyncAnotherDevice)
            testee.onBackPressed()
            viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
        }
    }

    @Test
    fun whenOnBackPressedAndViewModeEnableSyncThenClose() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.onBackPressed()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.Close)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnBackPressedAndViewModeSaveRecoveryCodeThenClose() = runTest {
        testee.viewState(Screen.SETUP).test {
            var viewState = awaitItem()
            testee.onAskSyncAnotherDevice()
            viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.AskSyncAnotherDevice)
            testee.finishSetupFlow()
            viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.AskSaveRecoveryCode)
            testee.onBackPressed()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.Close)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnAskSyncAnotherDeviceThenViewModeSyncAnotherDevice() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.onAskSyncAnotherDevice()
            val viewState2 = awaitItem()
            assertTrue(viewState2.viewMode is ViewMode.AskSyncAnotherDevice)
        }
    }

    @Test
    fun whenFinishSetupFlowThenViewModeAskRecoveryCode() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.finishSetupFlow()
            val viewState2 = awaitItem()
            assertTrue(viewState2.viewMode is ViewMode.AskSaveRecoveryCode)
        }
    }

    @Test
    fun whenFlowStartedFromDeviceConnectedThenViewModeDeviceConnected() = runTest {
        testee.viewState(Screen.DEVICE_CONNECTED).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.DeviceConnected)
        }
    }

    @Test
    fun whenOnBackPressedAndViewModeDeviceConnectedThenClose() = runTest {
        testee.viewState(Screen.DEVICE_CONNECTED).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.DeviceConnected)
            testee.onBackPressed()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.Close)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnRecoverYourSyncDataClickedThenCommandRecoverSyncData() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.onRecoverYourSyncedData()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.RecoverSyncData)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnLoginSucessClickecThenViewModeDeviceConnected() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.onLoginSucess()
            val viewState2 = awaitItem()
            assertTrue(viewState2.viewMode is ViewMode.DeviceConnected)
        }
    }

    @Test
    fun whenUserClicksOnSyncAnotherDeviceThenCommandSyncAnotherDevice() = runTest {
        testee.viewState(Screen.SETUP).test {
            val viewState = awaitItem()
            assertTrue(viewState.viewMode is ViewMode.TurnOnSync)
            testee.onAskSyncAnotherDevice()
            val viewState2 = awaitItem()
            assertTrue(viewState2.viewMode is ViewMode.AskSyncAnotherDevice)
            testee.onSyncAnotherDevice()
        }
        testee.commands().test {
            val command = awaitItem()
            assertTrue(command is Command.SyncAnotherDevice)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
