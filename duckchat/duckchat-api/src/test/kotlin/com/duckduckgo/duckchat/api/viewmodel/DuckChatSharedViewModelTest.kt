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

package com.duckduckgo.duckchat.api.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuckChatSharedViewModelTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: DuckChatSharedViewModel

    @Before
    fun before() {
        testee = DuckChatSharedViewModel()
    }

    @Test
    fun `when onFireButtonClicked then emit LaunchFire Command`() =
        runTest {
            testee.onFireButtonClicked()
            testee.command.test {
                assertEquals(DuckChatSharedViewModel.Command.LaunchFire, awaitItem())
            }
        }

    @Test
    fun `when onTabSwitcherCLicked then emit LaunchTabSwitcher Command`() =
        runTest {
            testee.onTabSwitcherClicked()
            testee.command.test {
                assertEquals(DuckChatSharedViewModel.Command.LaunchTabSwitcher, awaitItem())
            }
        }

    @Test
    fun `when onSearchRequested then emit SearchRequested Command`() =
        runTest {
            val query = "example.com"
            testee.onSearchRequested(query)
            testee.command.test {
                assertEquals(DuckChatSharedViewModel.Command.SearchRequested(query), awaitItem())
            }
        }

    @Test
    fun `when openExistingTab then emit OpenTab Command`() =
        runTest {
            val tabId = "tabId"
            testee.openExistingTab(tabId)
            testee.command.test {
                assertEquals(DuckChatSharedViewModel.Command.OpenTab(tabId), awaitItem())
            }
        }
}
