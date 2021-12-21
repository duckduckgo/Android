/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageSingleChoiceFormViewModel.Companion.CHOICES
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalTime
@ExperimentalCoroutinesApi
class ReportBreakageSingleChoiceFormViewModelTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    private lateinit var viewModel: ReportBreakageSingleChoiceFormViewModel

    @Before
    fun setup() {
        viewModel = ReportBreakageSingleChoiceFormViewModel()
    }

    @Test
    fun whenGetChoicesAndNoSelectedChoiceThenReturnChoicesState() =
        coroutineRule.runBlocking {
            viewModel.getChoices().test {
                val expectedChoices = ReportBreakageSingleChoiceFormView.State(CHOICES, false)

                assertEquals(expectedChoices, awaitItem())

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenGetChoicesAndChoiceSelectedThenReturnChoicesState() =
        coroutineRule.runBlocking {
            val selectedChoice = CHOICES.first().copy(isSelected = true)
            viewModel.onChoiceSelected(selectedChoice)
            viewModel.getChoices().test {
                val expectedChoices =
                    ReportBreakageSingleChoiceFormView.State(
                        CHOICES.map {
                            if (it.questionStringRes == selectedChoice.questionStringRes)
                                selectedChoice
                            else it
                        },
                        true)

                assertEquals(expectedChoices, awaitItem())

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenOnChoiceSelectedThenEmitUpdatedChoices() =
        coroutineRule.runBlocking {
            viewModel.getChoices().test {
                val selectedChoice = CHOICES.first().copy(isSelected = true)
                viewModel.onChoiceSelected(selectedChoice)

                var expectedChoices = ReportBreakageSingleChoiceFormView.State(CHOICES, false)

                assertEquals(expectedChoices, awaitItem())

                expectedChoices =
                    ReportBreakageSingleChoiceFormView.State(
                        CHOICES.map {
                            if (it.questionStringRes == selectedChoice.questionStringRes)
                                selectedChoice
                            else it
                        },
                        true)

                assertEquals(expectedChoices, awaitItem())

                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun whenOnSubmitChoicesAndNoChoiceSelectedThenEmitNoEvent() =
        coroutineRule.runBlocking {
            viewModel.commands().test {
                viewModel.onSubmitChoices()

                expectNoEvents()
            }
        }

    @Test
    fun whenOnSubmitChoicesAndChoiceSelectedThenEmitSubmitChoiceCommand() =
        coroutineRule.runBlocking {
            val selectedChoice = CHOICES.first().copy(isSelected = true)
            viewModel.onChoiceSelected(selectedChoice)

            viewModel.commands().test {
                viewModel.onSubmitChoices()

                val expectedCommand =
                    ReportBreakageSingleChoiceFormView.Command.SubmitChoice(selectedChoice)

                assertEquals(expectedCommand, awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }
}
