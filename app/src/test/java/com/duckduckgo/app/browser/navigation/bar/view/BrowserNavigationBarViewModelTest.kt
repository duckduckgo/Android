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

package com.duckduckgo.app.browser.navigation.bar.view

import app.cash.turbine.test
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class BrowserNavigationBarViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var tabRepositoryMock: TabRepository

    @Mock
    private lateinit var pixelMock: Pixel

    private lateinit var testee: BrowserNavigationBarViewModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        testee = BrowserNavigationBarViewModel(
            tabRepository = tabRepositoryMock,
            pixel = pixelMock,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when Fire button clicked, then send view command`() = runTest {
        testee.onFireButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyFireButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when Tabs button clicked, then send view command`() = runTest {
        testee.onTabsButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyTabsButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when Tabs button long clicked, then send view command`() = runTest {
        testee.onTabsButtonLongClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyTabsButtonLongClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when Menu button clicked, then send view command`() = runTest {
        testee.onMenuButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyMenuButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when NewTab button clicked, then send view command`() = runTest {
        testee.onNewTabButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyNewTabButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when Autofill button clicked, then send view command`() = runTest {
        testee.onAutofillButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyAutofillButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when Bookmarks button clicked, then send view command`() = runTest {
        testee.onBookmarksButtonClicked()

        testee.commands.test {
            val command = awaitItem()
            Assert.assertEquals(Command.NotifyBookmarksButtonClicked, command)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
