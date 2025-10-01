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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategorySingleChoiceViewModel.Command
import com.duckduckgo.mobile.android.vpn.ui.AppBreakageCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalTime
class ReportBreakageCategorySingleChoiceViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var viewModel: ReportBreakageCategorySingleChoiceViewModel

    private val viewState: ReportBreakageCategorySingleChoiceViewModel.ViewState
        get() = viewModel.viewState.value

    @Before
    fun setup() {
        viewModel = ReportBreakageCategorySingleChoiceViewModel()
        viewModel.setCategories(
            listOf(
                AppBreakageCategory("zero", "0"),
                AppBreakageCategory("one", "1"),
                AppBreakageCategory("two", "2"),
                AppBreakageCategory("three", "3"),
                AppBreakageCategory("four", "4"),
                AppBreakageCategory("five", "5"),
            ),
        )
    }

    @Test
    fun whenInitializedThenCannotSubmit() {
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCategorySelectedThenCanSubmit() {
        selectAndAcceptCategory()
        assertTrue(viewState.submitAllowed)
    }

    @Test
    fun whenCategoryChangedButNotSelectedThenCannotSubmit() {
        viewModel.onCategoryIndexChanged(0)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenNoCategorySelectedThenCannotSubmit() {
        selectAndAcceptCategory(-1)
        assertFalse(viewState.submitAllowed)
    }

    @Test
    fun whenCategorySelectedButNotChangedThenReturnOldCategory() {
        viewModel.onCategoryIndexChanged(0)
        viewModel.onCategoryAccepted()
        viewModel.onCategoryIndexChanged(1)
        assertEquals(0, viewState.indexSelected)
    }

    @Test
    fun whenCategoryAcceptedAndIncorrectIndexThenReturnNullCategory() {
        selectAndAcceptCategory(-1)
        assertNull(viewState.categorySelected)
    }

    @Test
    fun whenCategoryAcceptedAndCorrectIndexThenReturnCategory() {
        val indexSelected = 0
        selectAndAcceptCategory(indexSelected)

        assertEquals(AppBreakageCategory("zero", "0"), viewState.categorySelected)
    }

    @Test
    fun whenCancelSelectionThenAssignOldIndexValue() {
        selectAndAcceptCategory(0)
        viewModel.onCategoryIndexChanged(1)
        viewModel.onCategorySelectionCancelled()

        assertEquals(0, viewModel.indexSelected)
    }

    @Test
    fun whenCancelSelectionAndNoPreviousValueThenAssignMinusOne() {
        viewModel.onCategoryIndexChanged(1)
        viewModel.onCategorySelectionCancelled()

        assertEquals(-1, viewModel.indexSelected)
    }

    @Test
    fun whenOnSubmitPressedAndCategorySelectedThenEmitConfirmAndFinishCommand() = runTest {
        selectAndAcceptCategory()

        viewModel.commands().test {
            viewModel.onSubmitPressed()

            val expectedCommand = Command.ConfirmAndFinish

            assertEquals(expectedCommand, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    private fun selectAndAcceptCategory(indexSelected: Int = 0) {
        viewModel.onCategoryIndexChanged(indexSelected)
        viewModel.onCategoryAccepted()
    }
}
