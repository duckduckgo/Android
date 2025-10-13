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

package com.duckduckgo.settings.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.settings.impl.SettingsWebViewViewModel.Command
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SettingsWebViewViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var viewModel: SettingsWebViewViewModel

    @Before
    fun setup() {
        viewModel = SettingsWebViewViewModel()
    }

    @Test
    fun whenOnStartWithUrlThenLoadUrlCommandEmitted() = runTest {
        val testUrl = "https://example.com/settings"
        viewModel.commands.test {
            viewModel.onStart(testUrl)

            val command = awaitItem()
            assertTrue(command is Command.LoadUrl)
            assertEquals(testUrl, (command as Command.LoadUrl).url)
        }
    }

    @Test
    fun whenOnStartWithNullUrlThenExitCommandEmitted() = runTest {
        viewModel.commands.test {
            viewModel.onStart(null)

            val command = awaitItem()
            assertTrue(command is Command.Exit)
        }
    }
}
