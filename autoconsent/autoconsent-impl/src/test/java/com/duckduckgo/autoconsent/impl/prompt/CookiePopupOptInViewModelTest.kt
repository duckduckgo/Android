/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.autoconsent.impl.prompt

import app.cash.turbine.test
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Choice
import com.duckduckgo.autoconsent.impl.prompt.CookiePopupOptInViewModel.Command
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class CookiePopupOptInViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val testee = CookiePopupOptInViewModel()

    @Test
    fun whenCreatedThenMaxOptionIsSelected() {
        assertEquals(Choice.MAX, testee.viewState.value.selected)
    }

    @Test
    fun whenKeepCurrentSelectedThenViewStateUpdated() {
        testee.onOptionSelected(Choice.KEEP_CURRENT)

        assertEquals(Choice.KEEP_CURRENT, testee.viewState.value.selected)
    }

    @Test
    fun whenConfirmClickedThenCloseCommandEmitted() = runTest {
        testee.commands().test {
            testee.onConfirmClicked()

            assertEquals(Command.Close, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
