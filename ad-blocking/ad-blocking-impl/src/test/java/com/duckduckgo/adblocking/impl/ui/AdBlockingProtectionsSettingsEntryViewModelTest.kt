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

package com.duckduckgo.adblocking.impl.ui

import androidx.lifecycle.LifecycleOwner
import app.cash.turbine.test
import com.duckduckgo.adblocking.impl.domain.AdBlockingStatusChecker
import com.duckduckgo.adblocking.impl.domain.SettingsPlacement
import com.duckduckgo.adblocking.impl.ui.AdBlockingProtectionsSettingsEntryViewModel.Command.OpenSettings
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AdBlockingProtectionsSettingsEntryViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val statusChecker: AdBlockingStatusChecker = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private fun createViewModel() = AdBlockingProtectionsSettingsEntryViewModel(
        statusChecker,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenPlacementIsProtectionsThenVisible() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Protections))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertTrue(awaitItem().isVisible)
        }
    }

    @Test
    fun whenPlacementIsOtherThenNotVisible() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Other))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertFalse(awaitItem().isVisible)
        }
    }

    @Test
    fun whenPlacementIsHiddenThenNotVisible() = runTest {
        whenever(statusChecker.settingsPlacementFlow()).thenReturn(flowOf(SettingsPlacement.Hidden))

        val viewModel = createViewModel()
        viewModel.onStart(lifecycleOwner)

        viewModel.viewState.test {
            assertFalse(awaitItem().isVisible)
        }
    }

    @Test
    fun whenSettingClickedThenSendsOpenSettingsCommand() = runTest {
        val viewModel = createViewModel()

        viewModel.commands().test {
            viewModel.onSettingClicked()

            assertTrue(awaitItem() is OpenSettings)
        }
    }
}
