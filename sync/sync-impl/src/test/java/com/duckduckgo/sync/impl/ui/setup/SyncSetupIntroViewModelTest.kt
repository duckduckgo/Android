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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.sync.impl.SyncFeatureToggle
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.RECOVERY_INTRO
import com.duckduckgo.sync.impl.ui.setup.SetupAccountActivity.Companion.Screen.SYNC_INTRO
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.RecoverDataFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.Command.StartSetupFlow
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.CreateAccountIntro
import com.duckduckgo.sync.impl.ui.setup.SyncSetupIntroViewModel.ViewMode.RecoverAccountIntro
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

class SyncSetupIntroViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncFeatureToggle: SyncFeatureToggle = mock()

    private val testee = SyncSetupIntroViewModel(syncFeatureToggle, coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenSyncIntroArgumentThenIntroCreateAccountScreenShown() = runTest {
        testee.viewState(SYNC_INTRO).test {
            val viewState = awaitItem()
            Assert.assertTrue(viewState.viewMode is CreateAccountIntro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRecoverIntroArgumentThenIntroRecoveryScreenShown() = runTest {
        testee.viewState(RECOVERY_INTRO).test {
            val viewState = awaitItem()
            Assert.assertTrue(viewState.viewMode is RecoverAccountIntro)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnTurnSyncOnClickedThenStartSetupFlowCommandSent() = runTest {
        testee.onTurnSyncOnClicked()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is StartSetupFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnStartRecoveryDataClickedThenRecoverDataFlowCommandSent() = runTest {
        testee.onStartRecoverDataClicked()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is RecoverDataFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenOnAbortCLickedThenAbortFlowCommandSent() = runTest {
        testee.onAbortClicked()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is AbortFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
