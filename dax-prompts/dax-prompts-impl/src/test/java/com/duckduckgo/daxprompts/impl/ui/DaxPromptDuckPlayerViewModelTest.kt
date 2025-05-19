/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.daxprompts.impl.ui

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.impl.ReactivateUsersExperiment
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class DaxPromptDuckPlayerViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: DaxPromptDuckPlayerViewModel
    private val mockDaxPromptsRepository: DaxPromptsRepository = mock()
    private val mockReactivationExperiment: ReactivateUsersExperiment = mock()

    @Before
    fun setup() {
        testee = DaxPromptDuckPlayerViewModel(mockDaxPromptsRepository, mockReactivationExperiment)
    }

    @Test
    fun whenCloseButtonClickedThenEmitsCloseScreenCommand() = runTest {
        testee.onCloseButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptDuckPlayerViewModel.Command.CloseScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        verify(mockReactivationExperiment).fireCloseScreen()
    }

    @Test
    fun whenPrimaryButtonClickedThenEmitsTryDuckPlayerCommand() = runTest {
        testee.onPrimaryButtonClicked()

        testee.commands().test {
            val command = awaitItem() as DaxPromptDuckPlayerViewModel.Command.TryDuckPlayer
            assertEquals(DaxPromptDuckPlayerViewModel.DUCK_PLAYER_DEMO_URL, command.url)
            cancelAndIgnoreRemainingEvents()
        }
        verify(mockReactivationExperiment).fireDuckPlayerClick()
    }
}
