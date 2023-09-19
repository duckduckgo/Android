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

package com.duckduckgo.windows.impl.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.windows.impl.ui.WindowsViewModel.Command.GoToMacClientSettings
import com.duckduckgo.windows.impl.ui.WindowsViewModel.Command.ShareLink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
internal class WindowsViewModelTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private var mockPixel: Pixel = mock()

    private lateinit var testee: WindowsViewModel

    @Before
    fun before() {
        testee = WindowsViewModel(mockPixel, coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenOnShareClickedThenEmitShareLinkCommand() = runTest {
        testee.commands.test {
            testee.onShareClicked()
            assertEquals(ShareLink, awaitItem())
        }
    }

    @Test
    fun whenOnGoToMacClickedThenEmitGoToMacClientSettingsCommand() = runTest {
        testee.commands.test {
            testee.onGoToMacClicked()
            assertEquals(GoToMacClientSettings, awaitItem())
        }
    }
}
