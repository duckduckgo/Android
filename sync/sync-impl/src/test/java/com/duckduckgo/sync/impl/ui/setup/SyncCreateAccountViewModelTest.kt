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
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.AbortFlow
import com.duckduckgo.sync.impl.ui.setup.SyncCreateAccountViewModel.Command.FinishSetupFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock

@ExperimentalCoroutinesApi
class SyncCreateAccountViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val syncRepostitory: SyncAccountRepository = mock()

    private val testee = SyncCreateAccountViewModel(
        syncRepostitory,
        coroutineTestRule.testDispatcherProvider,
    )

    @Test
    fun whenNextClickedThenEmitFinishSetupCommand() = runTest {
        testee.onNextClicked()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is FinishSetupFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCloseClickedThenEmitFinishSetupCommand() = runTest {
        testee.onCloseClicked()

        testee.commands().test {
            val command = awaitItem()
            Assert.assertTrue(command is AbortFlow)
            cancelAndIgnoreRemainingEvents()
        }
    }

}
