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
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.duckplayer.api.PrivatePlayerMode
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
    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockDaxPromptsRepository: DaxPromptsRepository = mock()

    @Before
    fun setup() {
        testee = DaxPromptDuckPlayerViewModel(mockDuckPlayer, mockDaxPromptsRepository)
    }

    @Test
    fun whenCloseButtonClickedThenEmitsCloseScreenCommand() = runTest {
        testee.onCloseButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptDuckPlayerViewModel.Command.CloseScreen, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPrimaryButtonClickedThenEmitsTryDuckPlayerCommand() = runTest {
        testee.onPrimaryButtonClicked()

        testee.commands().test {
            val command = awaitItem() as DaxPromptDuckPlayerViewModel.Command.TryDuckPlayer
            assertEquals(DaxPromptDuckPlayerViewModel.DUCK_PLAYER_DEMO_URL, command.url)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenSecondaryButtonClickedThenEmitsDismissCommand() = runTest {
        testee.onSecondaryButtonClicked()

        testee.commands().test {
            assertEquals(DaxPromptDuckPlayerViewModel.Command.Dismiss, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenUpdateDuckPlayerSettingsCalledThenSetUserPreferencesWithCorrectParameters() = runTest {
        testee.updateDuckPlayerSettings()

        verify(mockDuckPlayer).setUserPreferences(
            overlayInteracted = false,
            privatePlayerMode = PrivatePlayerMode.AlwaysAsk.value,
        )
    }
}
