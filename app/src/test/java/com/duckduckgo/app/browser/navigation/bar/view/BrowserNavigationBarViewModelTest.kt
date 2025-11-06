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
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class BrowserNavigationBarViewModelTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val tabRepositoryMock: TabRepository = mock()

    private val pixelMock: Pixel = mock()

    private val additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts = mock()

    private val highlightPopupMenuFlow = MutableStateFlow(false)

    private lateinit var testee: BrowserNavigationBarViewModel

    @Before
    fun setUp() {
        whenever(additionalDefaultBrowserPrompts.highlightPopupMenu).thenReturn(highlightPopupMenuFlow)
        whenever(tabRepositoryMock.flowTabs).thenReturn(flowOf(listOf(TabEntity("abc"))))

        testee = BrowserNavigationBarViewModel(
            pixel = pixelMock,
            tabRepository = tabRepositoryMock,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
            additionalDefaultBrowserPrompts = additionalDefaultBrowserPrompts,
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

    @Test
    fun `when highlightPopupMenu flow emits true, viewState shows browser menu highlight`() = runTest {
        testee.viewState.test {
            val initial = awaitItem()
            Assert.assertEquals(false, initial.showBrowserMenuHighlight)

            highlightPopupMenuFlow.value = true

            val updated = awaitItem()
            Assert.assertEquals(true, updated.showBrowserMenuHighlight)

            highlightPopupMenuFlow.value = false
            val updatedFalse = awaitItem()
            Assert.assertEquals(false, updatedFalse.showBrowserMenuHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when setViewMode NewTab then viewState reflects NewTab configuration`() = runTest {
        testee.viewState.test {
            val initial = awaitItem()
            // sanity check defaults
            Assert.assertTrue(initial.newTabButtonVisible)
            Assert.assertFalse(initial.autofillButtonVisible)

            testee.setViewMode(BrowserNavigationBarView.ViewMode.NewTab)

            val updated = awaitItem()
            Assert.assertFalse(updated.newTabButtonVisible)
            Assert.assertTrue(updated.autofillButtonVisible)
            // unchanged flags
            Assert.assertTrue(updated.tabsButtonVisible)
            Assert.assertTrue(updated.bookmarksButtonVisible)
            Assert.assertTrue(updated.showShadow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when setViewMode TabManager then viewState reflects TabManager configuration`() = runTest {
        testee.viewState.test {
            awaitItem() // initial
            testee.setViewMode(BrowserNavigationBarView.ViewMode.TabManager)
            val updated = awaitItem()
            Assert.assertTrue(updated.newTabButtonVisible)
            Assert.assertFalse(updated.autofillButtonVisible)
            Assert.assertFalse(updated.tabsButtonVisible)
            Assert.assertFalse(updated.bookmarksButtonVisible)
            Assert.assertFalse(updated.showShadow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when switching NewTab then Browser then viewState reflects Browser configuration`() = runTest {
        testee.viewState.test {
            awaitItem() // initial
            testee.setViewMode(BrowserNavigationBarView.ViewMode.NewTab)
            awaitItem() // NewTab state
            testee.setViewMode(BrowserNavigationBarView.ViewMode.Browser)
            val browserState = awaitItem()
            Assert.assertTrue(browserState.newTabButtonVisible)
            Assert.assertFalse(browserState.autofillButtonVisible)
            Assert.assertTrue(browserState.tabsButtonVisible)
            Assert.assertTrue(browserState.bookmarksButtonVisible)
            Assert.assertTrue(browserState.showShadow)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when setViewMode CustomTab then navigation bar becomes hidden`() = runTest {
        testee.viewState.test {
            val initial = awaitItem()
            Assert.assertTrue(initial.isVisible)

            testee.setViewMode(BrowserNavigationBarView.ViewMode.CustomTab)
            val hidden = awaitItem()
            Assert.assertFalse(hidden.isVisible)
        }
    }
}
